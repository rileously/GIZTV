package com.example.auroratv.ui.dlhd

import com.example.auroratv.ui.iptv.DLHD_IPTV_GROUP
import com.example.auroratv.ui.iptv.IptvChannel
import java.io.IOException
import java.net.HttpURLConnection
import java.net.URL
import java.util.Locale
import java.util.concurrent.TimeUnit
import java.util.regex.Pattern
import kotlinx.coroutines.Dispatchers
import kotlinx.coroutines.sync.Mutex
import kotlinx.coroutines.sync.withLock
import kotlinx.coroutines.withContext

internal const val DLHD_24X7_CHANNELS_URL = "$DLHD_ORIGIN/24-7-channels.php"

/** How long the 24/7 channel grid stays good before IPTV asks again. */
private const val CHANNELS_TTL_MS = 15 * 60_000L

/**
 * DaddyLive's always-on channel grid.
 *
 * Each card is only a watch page, so these channels resolve through the browser the same way a
 * soccer fixture does — they are not bare HLS addresses an IPTV playlist can hand straight to
 * Media3.
 */
internal object DlhdChannelsRepository {
  private val cache = DlhdChannelsCache(CHANNELS_TTL_MS)

  suspend fun channels(): List<IptvChannel> = cache.get { requestChannels() }

  suspend fun refresh(): List<IptvChannel> {
    cache.clear()
    return channels()
  }
}

private suspend fun requestChannels(): List<IptvChannel> =
  withContext(Dispatchers.IO) {
    val connection = (URL(DLHD_24X7_CHANNELS_URL).openConnection() as HttpURLConnection)
    try {
      connection.requestMethod = "GET"
      connection.setRequestProperty("Accept", "text/html,application/xhtml+xml")
      connection.setRequestProperty("Referer", "$DLHD_ORIGIN/")
      connection.setRequestProperty("User-Agent", dlhdUserAgent())
      connection.connectTimeout = 12_000
      connection.readTimeout = 20_000
      val status = connection.responseCode
      if (status == HttpURLConnection.HTTP_UNAVAILABLE || status == 429) {
        throw IOException("DaddyLive channels are busy right now. Try again in a moment.")
      }
      if (status !in 200..299) throw IOException("DaddyLive channels returned HTTP $status")
      parseDlhd24x7Channels(connection.inputStream.bufferedReader().use { it.readText() })
    } finally {
      connection.disconnect()
    }
  }

private val CHANNEL_CARD =
  Pattern.compile(
    """<a\s+class="card"\s+href="(/watch\.php\?id=(\d+))"[^>]*>[\s\S]*?<div class="card__title">([\s\S]*?)</div>""",
    Pattern.CASE_INSENSITIVE,
  )

/**
 * Reads the 24/7 channel grid HTML into IPTV rows that open via the watch page.
 *
 * The page is a card grid rather than an M3U, so this is the only step that turns a name and id
 * into something the IPTV screen can list.
 */
internal fun parseDlhd24x7Channels(html: String): List<IptvChannel> {
  val channels = ArrayList<IptvChannel>()
  val seen = HashSet<String>()
  val matcher = CHANNEL_CARD.matcher(html)
  while (matcher.find()) {
    val path = matcher.group(1)?.trim().orEmpty()
    val channelId = matcher.group(2)?.trim().orEmpty()
    val name =
      decodeBasicHtml(stripHtmlTags(matcher.group(3).orEmpty()))
        .replace(Regex("\\s+"), " ")
        .trim()
    if (path.isBlank() || channelId.isBlank() || name.isBlank()) continue
    if (!seen.add(channelId)) continue
    channels.add(
      IptvChannel(
        id = "dlhd-$channelId",
        name = name,
        group = DLHD_IPTV_GROUP,
        logoUrl = null,
        tvgId = channelId,
        url = DLHD_ORIGIN + path,
        headers = emptyMap(),
        mimeType = null,
        drm = null,
        resolveViaBrowser = true,
      ),
    )
  }
  return channels.sortedBy { it.name.lowercase(Locale.US) }
}

private fun stripHtmlTags(value: String): String = value.replace(Regex("<[^>]+>"), " ")

private fun decodeBasicHtml(value: String): String =
  value
    .replace("&amp;", "&")
    .replace("&lt;", "<")
    .replace("&gt;", ">")
    .replace("&quot;", "\"")
    .replace("&#39;", "'")
    .replace("&nbsp;", " ")

internal class DlhdChannelsCache(
  private val ttlMs: Long,
  private val now: () -> Long = System::currentTimeMillis,
) {
  private val guard = Mutex()
  private var storedAtMs = 0L
  private var channels: List<IptvChannel>? = null

  suspend fun get(load: suspend () -> List<IptvChannel>): List<IptvChannel> {
    fresh()?.let { return it }
    return guard.withLock {
      freshLocked()
        ?: load().also { loaded ->
          channels = loaded
          storedAtMs = now()
        }
    }
  }

  suspend fun clear() {
    guard.withLock {
      channels = null
      storedAtMs = 0L
    }
  }

  private suspend fun fresh(): List<IptvChannel>? = guard.withLock { freshLocked() }

  private fun freshLocked(): List<IptvChannel>? =
    channels?.takeIf { storedAtMs != 0L && now() - storedAtMs < ttlMs }
}

internal val DLHD_CHANNELS_REFRESH_INTERVAL_MS = TimeUnit.MINUTES.toMillis(15)
