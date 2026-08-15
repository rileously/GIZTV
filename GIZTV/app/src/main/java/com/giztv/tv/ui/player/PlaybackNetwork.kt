@file:Suppress("UnsafeOptInUsageError")

package com.giztv.tv.ui.player

import android.content.Context
import android.net.ConnectivityManager
import android.net.NetworkCapabilities
import android.net.http.HttpEngine
import android.os.Build
import android.os.ext.SdkExtensions
import androidx.annotation.ChecksSdkIntAtLeast
import androidx.annotation.RequiresExtension
import androidx.core.content.edit
import androidx.media3.common.C
import androidx.media3.datasource.DataSink
import androidx.media3.datasource.DataSource
import androidx.media3.datasource.DataSpec
import androidx.media3.datasource.DefaultDataSource
import androidx.media3.datasource.DefaultHttpDataSource
import androidx.media3.datasource.HttpEngineDataSource
import androidx.media3.datasource.TransferListener
import androidx.media3.datasource.cache.Cache
import androidx.media3.datasource.cache.CacheDataSink
import androidx.media3.datasource.cache.CacheDataSource
import androidx.media3.datasource.cache.LeastRecentlyUsedCacheEvictor
import androidx.media3.datasource.cache.SimpleCache
import androidx.media3.datasource.okhttp.OkHttpDataSource
import androidx.media3.database.StandaloneDatabaseProvider
import androidx.media3.exoplayer.upstream.DefaultBandwidthMeter
import java.io.File
import java.util.concurrent.Executors
import java.util.concurrent.TimeUnit
import okhttp3.OkHttpClient

private const val BANDWIDTH_PREFERENCES = "giztv_link_speed"

/** Below this an estimate is more likely a stalled fetch than a link; above it, a fluke. */
private const val MINIMUM_CREDIBLE_BITRATE = 400_000L
private const val MAXIMUM_CREDIBLE_BITRATE = 100_000_000L

/** Enough for seeking back through what was just watched and for reloading after a stall. */
private const val MEDIA_CACHE_BYTES = 384L * 1024L * 1024L

private const val MEDIA_CACHE_DIRECTORY = "media"

/** The S extension that carries HttpEngine, which is not the same question as an API level. */
private const val HTTP_ENGINE_EXTENSION_VERSION = 7

/**
 * What the last session measured of the link, so the next one does not begin blind.
 *
 * Media3's meter starts every process with a table of guesses by country and network type, which is
 * why a fast connection used to spend the opening of a film proving itself. What this device
 * actually managed last time is a far better opening bid, and it is kept per network type because a
 * phone's Wi-Fi and its cellular link are not the same connection in any respect.
 */
internal class BandwidthEstimateStore(context: Context) {
  private val preferences =
    context.applicationContext.getSharedPreferences(BANDWIDTH_PREFERENCES, Context.MODE_PRIVATE)

  fun load(networkKey: String): Long? =
    preferences.getLong(networkKey, 0L).takeIf { it >= MINIMUM_CREDIBLE_BITRATE }

  fun save(networkKey: String, bitrate: Long) {
    val credible = credibleBitrateEstimate(bitrate) ?: return
    preferences.edit { putLong(networkKey, credible) }
  }
}

/**
 * Whether a measurement is worth carrying into the next session.
 *
 * A player that spent its whole life waiting on a dead edge reports a handful of kilobits, and a
 * cached replay off local disk reports a gigabit. Neither describes the link, and either one
 * remembered would misjudge the first film of the next session.
 */
internal fun credibleBitrateEstimate(bitrate: Long): Long? =
  bitrate.takeIf { it in MINIMUM_CREDIBLE_BITRATE..MAXIMUM_CREDIBLE_BITRATE }

/** Wi-Fi and cellular are measured and remembered apart. */
internal fun networkKindKey(context: Context): String {
  val connectivity =
    context.getSystemService(Context.CONNECTIVITY_SERVICE) as? ConnectivityManager
      ?: return "unknown"
  val capabilities =
    runCatching { connectivity.getNetworkCapabilities(connectivity.activeNetwork) }.getOrNull()
      ?: return "unknown"
  return when {
    capabilities.hasTransport(NetworkCapabilities.TRANSPORT_WIFI) -> "wifi"
    capabilities.hasTransport(NetworkCapabilities.TRANSPORT_ETHERNET) -> "ethernet"
    capabilities.hasTransport(NetworkCapabilities.TRANSPORT_CELLULAR) -> "cellular"
    else -> "unknown"
  }
}

/**
 * The one meter every part of playback reports to and reads from.
 *
 * It replaces Media3's own singleton so that it can be opened with what this device measured last
 * time. Everything that used to ask for the singleton asks here instead, because an estimate is
 * only worth anything if the player choosing renditions and the sources fetching them share it.
 */
internal object PlaybackBandwidth {
  private var meter: DefaultBandwidthMeter? = null

  /**
   * Whether a film has already been played over this link, in this session.
   *
   * A measurement from a moment ago on the connection actually in use is worth acting on. A number
   * carried over from the last time the app ran is worth much less: the viewer may have moved from
   * their home Wi-Fi to a train, and it says nothing about the edge serving the next title. Only
   * the first is allowed to decide where a film opens.
   */
  var measuredThisSession: Boolean = false
    private set

  @Synchronized
  fun meter(context: Context): DefaultBandwidthMeter =
    meter
      ?: run {
        val appContext = context.applicationContext
        val remembered = BandwidthEstimateStore(appContext).load(networkKindKey(appContext))
        DefaultBandwidthMeter.Builder(appContext)
          .apply {
            // Only for the kind of connection it was measured on. Seeding every network type with
            // one number is how a speed learned on home Wi-Fi became what the app assumed of a
            // cellular link — and an opening rendition chosen for a link that was never there.
            remembered?.let { setInitialBitrateEstimate(networkTypeOf(appContext), openingBid(it)) }
          }
          .build()
          .also { meter = it }
      }

  /** Called when a playback ends, which is the only point at which a measurement means anything. */
  fun remember(context: Context) {
    val measured = meter?.bitrateEstimate ?: return
    measuredThisSession = true
    BandwidthEstimateStore(context).save(networkKindKey(context.applicationContext), measured)
  }
}

/**
 * What a remembered measurement is worth as an opening assumption.
 *
 * Deliberately less than what was measured. The selector spends a fraction of whatever it is told,
 * so an optimistic memory does not merely start high — it starts high and then has to be corrected
 * by a rebuffer, which is the exact experience this was supposed to prevent.
 */
internal fun openingBid(rememberedBitrate: Long): Long =
  (rememberedBitrate * REMEMBERED_ESTIMATE_FRACTION).toLong()

private const val REMEMBERED_ESTIMATE_FRACTION = 0.6

/** Media3 keeps an estimate per kind of connection; this says which one is in use. */
private fun networkTypeOf(context: Context): Int =
  when (networkKindKey(context)) {
    "wifi" -> C.NETWORK_TYPE_WIFI
    "ethernet" -> C.NETWORK_TYPE_ETHERNET
    "cellular" -> C.NETWORK_TYPE_CELLULAR_UNKNOWN
    else -> C.NETWORK_TYPE_OTHER
  }

/**
 * Where fetched media is kept.
 *
 * One instance per process: SimpleCache locks its folder and a second one over the same directory
 * throws. It lives in the cache directory, so a device short of space may take it back.
 */
internal object PlaybackCache {
  @Volatile private var cache: Cache? = null
  private var opening = false

  /**
   * The cache if it is ready, and nothing at all if it is not.
   *
   * Opening one reads an index off disk and opens a database, and this is asked for while a film is
   * being built — on the main thread, in the middle of the composition that puts the player on
   * screen. Doing that work there is a stall in front of the viewer at the exact moment they are
   * waiting for a picture, so it happens on a background thread and the film that asked first
   * simply goes to the network, as it always did.
   */
  fun get(context: Context): Cache? {
    cache?.let { return it }
    open(context)
    return null
  }

  @Synchronized
  private fun open(context: Context) {
    if (cache != null || opening) return
    opening = true
    val appContext = context.applicationContext
    Thread(
        {
          runCatching {
              SimpleCache(
                File(appContext.cacheDir, MEDIA_CACHE_DIRECTORY),
                LeastRecentlyUsedCacheEvictor(MEDIA_CACHE_BYTES),
                StandaloneDatabaseProvider(appContext),
              )
            }
            .onFailure { android.util.Log.w("GizHls", "Media cache unavailable", it) }
            .onSuccess { cache = it }
          synchronized(this) { opening = false }
        },
        "GizMediaCache",
      )
      .apply { isDaemon = true }
      .start()
  }
}

/**
 * What a fetched piece of media is filed under.
 *
 * These addresses are signed, and the signature changes every time a title is resolved — so the URL
 * itself names nothing that survives until the next play. The host and path do: they are the same
 * segment of the same film whoever signed for it, which is what makes the cache worth having at
 * all. Anything with no path to speak of falls back to the whole address rather than colliding.
 */
internal fun stableCacheKey(uri: String): String {
  val withoutFragment = uri.substringBefore('#')
  val withoutQuery = withoutFragment.substringBefore('?')
  val schemeEnd = withoutQuery.indexOf("://")
  if (schemeEnd < 0) return withoutQuery.ifBlank { uri }
  val hostAndPath = withoutQuery.substring(schemeEnd + 3)
  return hostAndPath.ifBlank { uri }
}

/**
 * The stack every segment travels over.
 *
 * Android's built-in stack speaks HTTP/1.1 and nothing else, which on a lossy link means one lost
 * packet holds up everything behind it. Both stacks used here instead speak HTTP/2, and HttpEngine
 * speaks HTTP/3 over QUIC, where loss is recovered without stalling the connection.
 *
 * HttpEngine is part of the platform and costs nothing to carry, wherever the S extension that
 * holds it has reached; everywhere else OkHttp, which is already in this app for artwork. Anything
 * unexpected on the way in falls back to the stack that has always worked, because a slower fetch
 * is worth more than a film that will not open.
 */
internal fun playbackHttpDataSourceFactory(
  context: Context,
  userAgent: String,
  requestProperties: Map<String, String>,
  connectTimeoutMs: Int,
  readTimeoutMs: Int,
  transferListener: TransferListener,
): DataSource.Factory {
  if (httpEngineSupported()) {
    runCatching {
        httpEngineDataSourceFactory(
          context = context,
          userAgent = userAgent,
          requestProperties = requestProperties,
          connectTimeoutMs = connectTimeoutMs,
          readTimeoutMs = readTimeoutMs,
          transferListener = transferListener,
        )
      }
      .onFailure { android.util.Log.w("GizHls", "HttpEngine unavailable; using OkHttp", it) }
      .getOrNull()
      ?.let { return it }
  }
  return runCatching {
      OkHttpDataSource.Factory(playbackHttpClient(connectTimeoutMs, readTimeoutMs))
        .setUserAgent(userAgent)
        .setDefaultRequestProperties(requestProperties)
        .setTransferListener(transferListener)
    }
    .onFailure { android.util.Log.w("GizHls", "OkHttp unavailable; using the platform stack", it) }
    .getOrElse {
      DefaultHttpDataSource.Factory()
        .setUserAgent(userAgent)
        .setDefaultRequestProperties(requestProperties)
        .setAllowCrossProtocolRedirects(true)
        .setConnectTimeoutMs(connectTimeoutMs)
        .setReadTimeoutMs(readTimeoutMs)
        .setTransferListener(transferListener)
    }
}

/**
 * Whether this device carries the platform's HTTP stack.
 *
 * Not a plain API level: HttpEngine ships in the S extensions, so it is present on some devices
 * below API 34 and its absence is possible above. The extension is the honest question to ask.
 */
@ChecksSdkIntAtLeast(extension = Build.VERSION_CODES.S, api = HTTP_ENGINE_EXTENSION_VERSION)
private fun httpEngineSupported(): Boolean =
  Build.VERSION.SDK_INT >= Build.VERSION_CODES.R &&
    SdkExtensions.getExtensionVersion(Build.VERSION_CODES.S) >= HTTP_ENGINE_EXTENSION_VERSION

/** The platform's own stack, which is where HTTP/3 over QUIC comes from. */
@RequiresExtension(extension = Build.VERSION_CODES.S, version = HTTP_ENGINE_EXTENSION_VERSION)
private fun httpEngineDataSourceFactory(
  context: Context,
  userAgent: String,
  requestProperties: Map<String, String>,
  connectTimeoutMs: Int,
  readTimeoutMs: Int,
  transferListener: TransferListener,
): DataSource.Factory =
  HttpEngineDataSource.Factory(sharedHttpEngine(context), httpEngineExecutor)
    .setUserAgent(userAgent)
    .setDefaultRequestProperties(requestProperties)
    .setConnectionTimeoutMs(connectTimeoutMs)
    .setReadTimeoutMs(readTimeoutMs)
    .setTransferListener(transferListener)

/**
 * One engine for the whole app, built once.
 *
 * A stack of this kind carries everything worth carrying between requests: open connections, the
 * knowledge of which hosts speak QUIC, the session tickets that let a repeat handshake be skipped.
 * Building a new one per film threw all of that away every time and made the first request of every
 * film pay for it again — which is the opposite of the reason for using this stack at all.
 */
@RequiresExtension(extension = Build.VERSION_CODES.S, version = HTTP_ENGINE_EXTENSION_VERSION)
@Synchronized
private fun sharedHttpEngine(context: Context): HttpEngine =
  httpEngine
    ?: HttpEngine.Builder(context.applicationContext)
      .setEnableBrotli(true)
      .setEnableHttp2(true)
      .setEnableQuic(true)
      .build()
      .also { httpEngine = it }

private var httpEngine: HttpEngine? = null

/** HttpEngine hands its callbacks to this; segments are read on it, so it is not the main thread. */
private val httpEngineExecutor by lazy { Executors.newCachedThreadPool() }

/**
 * One client, so segment after segment reuses a connection that is already open and warm rather
 * than paying for a handshake it has already paid for.
 */
private var httpClient: OkHttpClient? = null

@Synchronized
private fun playbackHttpClient(connectTimeoutMs: Int, readTimeoutMs: Int): OkHttpClient =
  httpClient
    ?: OkHttpClient.Builder()
      .connectTimeout(connectTimeoutMs.toLong(), TimeUnit.MILLISECONDS)
      .readTimeout(readTimeoutMs.toLong(), TimeUnit.MILLISECONDS)
      // These providers redirect between hosts and, occasionally, between schemes.
      .followRedirects(true)
      .followSslRedirects(true)
      .retryOnConnectionFailure(true)
      .build()
      .also { httpClient = it }

/**
 * Wraps [upstream] so that what has been fetched once need not be fetched again.
 *
 * Only for a title with an end. A live playlist is a moving window whose segments are never asked
 * for twice, so caching one fills the disk with video nobody can return to.
 */
internal fun playbackDataSourceFactory(
  context: Context,
  upstream: DataSource.Factory,
  cacheable: Boolean,
): DataSource.Factory {
  val local = DefaultDataSource.Factory(context, upstream)
  if (!cacheable) return local
  val cache = PlaybackCache.get(context) ?: return local
  return CacheDataSource.Factory()
    .setCache(cache)
    .setUpstreamDataSourceFactory(local)
    .setCacheKeyFactory { dataSpec -> dataSpec.key ?: stableCacheKey(dataSpec.uri.toString()) }
    // Video, and only video. See [SegmentOnlyCacheDataSink].
    .setCacheWriteDataSinkFactory {
      SegmentOnlyCacheDataSink(CacheDataSink(cache, CACHE_FRAGMENT_BYTES))
    }
    // A cache that cannot be read from or written to must never be the reason a film stops.
    .setFlags(CacheDataSource.FLAG_IGNORE_CACHE_ON_ERROR)
}

/** Fragment size for cache writes; Media3's own default, named here because the sink is ours. */
private const val CACHE_FRAGMENT_BYTES = 5L * 1024L * 1024L

/**
 * Keeps video, and refuses to keep the list of where the video is.
 *
 * A playlist is not the same kind of thing as a segment. It is a list of signed addresses, each of
 * which stops working within hours, so a playlist read back off disk on a later play hands the
 * player a set of links that have all expired — a title that used to play, failing on the second
 * viewing for a reason no server could explain. Playlists are small and worth re-fetching; segments
 * are large and worth keeping, and only they are written here.
 */
private class SegmentOnlyCacheDataSink(private val delegate: DataSink) : DataSink {
  private var keeping = false

  override fun open(dataSpec: DataSpec) {
    keeping = !looksLikePlaylist(dataSpec.uri.toString())
    if (keeping) delegate.open(dataSpec)
  }

  override fun write(buffer: ByteArray, offset: Int, length: Int) {
    if (keeping) delegate.write(buffer, offset, length)
  }

  override fun close() {
    if (keeping) delegate.close()
    keeping = false
  }
}

/** An HLS or DASH manifest, by the only part of the address that is stable enough to ask. */
internal fun looksLikePlaylist(uri: String): Boolean {
  val path = uri.substringBefore('#').substringBefore('?').lowercase()
  return path.endsWith(".m3u8") || path.endsWith(".m3u") || path.endsWith(".mpd")
}
