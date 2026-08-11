package com.giztv.tv.ui.iptv

import android.content.Context
import androidx.media3.common.MimeTypes
import com.giztv.tv.ui.dlhd.DlhdChannelsRepository
import com.giztv.tv.ui.player.HlsStreamRequest
import com.giztv.tv.ui.player.StreamDrmConfiguration
import com.giztv.tv.ui.player.StreamDrmScheme
import java.io.File
import java.io.Reader
import java.io.StringReader
import java.net.HttpURLConnection
import java.net.URL
import java.net.URLDecoder
import java.nio.charset.StandardCharsets
import java.util.Locale
import kotlinx.coroutines.Dispatchers
import kotlinx.coroutines.withContext
import org.json.JSONObject
import android.util.Log

internal const val ALL_IPTV_CHANNELS = "All channels"
internal const val ALL_IPTV_GROUPS = "All providers"
/**
 * Pinned categories. They are held by the viewer rather than by the playlist, so the strip prepends
 * them and they never take part in [IPTV_CATEGORY_ORDER].
 */
internal const val IPTV_CATEGORY_FAVORITES = "Favorites"
internal const val IPTV_CATEGORY_RECENT = "Recent"
internal const val IPTV_CATEGORY_DADDYLIVE = "DaddyLive"
internal const val IPTV_CATEGORY_SPORTS = "Sports"
internal const val IPTV_CATEGORY_NEWS = "News"
internal const val IPTV_CATEGORY_MOVIES = "Movies"
internal const val IPTV_CATEGORY_ENTERTAINMENT = "Entertainment"
internal const val IPTV_CATEGORY_KIDS = "Kids & family"
internal const val IPTV_CATEGORY_MUSIC = "Music & radio"
internal const val IPTV_CATEGORY_KNOWLEDGE = "Knowledge"
internal const val IPTV_CATEGORY_FAITH = "Faith"
internal const val IPTV_CATEGORY_REGIONAL = "Regional"
internal const val IPTV_CATEGORY_OTHER = "Other"
/** Provider group stamped on every channel scraped from DaddyLive's 24/7 grid. */
internal const val DLHD_IPTV_GROUP = "DaddyLive 24/7"
private const val DEFAULT_GROUP = "Other"
private const val BUNDLED_PLAYLIST = "iptv/play.m3u"
private const val REMOTE_PLAYLIST = "https://iptv-org.github.io/iptv/index.category.m3u"
private const val PLAYLIST_CACHE_DIRECTORY = "iptv"
private const val PLAYLIST_CACHE_FILE = "public-playlist.m3u"
private const val PLAYLIST_CACHE_MAX_AGE_MS = 6 * 60 * 60 * 1_000L
private const val REMOTE_CONNECT_TIMEOUT_MS = 8_000
private const val REMOTE_READ_TIMEOUT_MS = 20_000
private const val MAX_REMOTE_PLAYLIST_CHARS = 8 * 1024 * 1024
private const val MIN_REMOTE_CHANNELS = 1_000
private const val MAX_PLAYBACK_SOURCES = 6

private val IPTV_CATEGORY_ORDER =
  listOf(
    IPTV_CATEGORY_DADDYLIVE,
    IPTV_CATEGORY_SPORTS,
    IPTV_CATEGORY_NEWS,
    IPTV_CATEGORY_MOVIES,
    IPTV_CATEGORY_ENTERTAINMENT,
    IPTV_CATEGORY_KIDS,
    IPTV_CATEGORY_MUSIC,
    IPTV_CATEGORY_KNOWLEDGE,
    IPTV_CATEGORY_FAITH,
    IPTV_CATEGORY_REGIONAL,
    IPTV_CATEGORY_OTHER,
  )

/** Hoisted: the key is read several times per channel, and the playlist runs to five figures. */
private val whitespaceRun = Regex("\\s+")

internal data class IptvPlaylist(
  val channels: List<IptvChannel>,
  val epgUrl: String? = null,
) {
  /**
   * Browsing keys worked out once.
   *
   * The public playlist runs to five figures, and deriving a category plus four lowercase forms for
   * every channel on every keystroke left search lagging a line behind the typing. The repository
   * warms this on its background thread before the screen ever reads it.
   */
  val browseIndex: IptvBrowseIndex by lazy { IptvBrowseIndex.of(channels) }
}

internal data class IptvCategory(
  val label: String,
  val channelCount: Int,
)

internal data class IptvStreamSource(
  val url: String,
  val headers: Map<String, String>,
  val mimeType: String?,
  val drm: StreamDrmConfiguration?,
)

internal data class IptvChannel(
  val id: String,
  val name: String,
  val group: String,
  val logoUrl: String?,
  val tvgId: String?,
  val url: String,
  val headers: Map<String, String>,
  val mimeType: String?,
  val drm: StreamDrmConfiguration?,
  val backupSources: List<IptvStreamSource> = emptyList(),
  /**
   * The address is a watch page rather than a stream.
   *
   * DaddyLive's 24/7 grid only publishes player pages, so IPTV has to send those through the
   * browser resolver instead of handing them straight to Media3.
   */
  val resolveViaBrowser: Boolean = false,
) {
  /**
   * What identifies this channel across playlist reloads.
   *
   * The generated [id] carries the position in the file, which moves whenever the upstream playlist
   * is re-cut, so it cannot anchor a favourite. A guide id, or failing that the name within its
   * group, survives that. It doubles as the key the repeated listings of a channel are pooled and
   * collapsed on in [IptvBrowseIndex].
   */
  val key: String
    get() {
      val guideId = tvgId?.trim()?.substringBefore('@')?.lowercase(Locale.ENGLISH)
      if (!guideId.isNullOrBlank()) return "guide:$guideId"
      val normalizedName = name.trim().lowercase(Locale.ENGLISH).replace(whitespaceRun, " ")
      if (normalizedName.length >= 5) return "name:${group.lowercase(Locale.ENGLISH)}:$normalizedName"
      return "url:$url"
    }

  val formatLabel: String
    get() =
      when {
        resolveViaBrowser -> "DLHD"
        mimeType == MimeTypes.APPLICATION_M3U8 -> "HLS"
        mimeType == MimeTypes.APPLICATION_MPD -> "DASH"
        mimeType == MimeTypes.VIDEO_MP2T -> "MPEG-TS"
        mimeType == MimeTypes.VIDEO_MP4 -> "MP4"
        else -> "LIVE"
      }

  val playbackSources: List<IptvStreamSource>
    get() =
      (listOf(IptvStreamSource(url, headers, mimeType, drm)) + backupSources)
        .distinctBy { source -> source.url to source.headers }
        .take(MAX_PLAYBACK_SOURCES)

  fun toPlaybackRequest(): HlsStreamRequest = toPlaybackRequests().first()

  fun toPlaybackRequests(): List<HlsStreamRequest> {
    val sources = playbackSources
    return sources.mapIndexed { index, source ->
      HlsStreamRequest(
        url = source.url,
        headers = source.headers,
        sourcePageUrl = source.url,
        title = name,
        subtitle = group,
        mimeType = source.mimeType,
        drm = source.drm,
        isLive = true,
        sourceIndex = index,
        sourceCount = sources.size,
      )
    }
  }
}

internal object IptvRepository {
  @Volatile private var cached: IptvPlaylist? = null
  @Volatile private var cachedAtEpochMs: Long = 0L

  suspend fun playlist(context: Context, refresh: Boolean = false): IptvPlaylist =
    withContext(Dispatchers.IO) {
      val now = System.currentTimeMillis()
      if (!refresh && now - cachedAtEpochMs < PLAYLIST_CACHE_MAX_AGE_MS) {
        cached?.let { return@withContext it }
      }

      val cacheFile = File(File(context.filesDir, PLAYLIST_CACHE_DIRECTORY), PLAYLIST_CACHE_FILE)
      val freshDiskPlaylist =
        cacheFile
          .takeIf { !refresh && it.isFile && now - it.lastModified() < PLAYLIST_CACHE_MAX_AGE_MS }
          ?.let(::parsePlaylistFile)
      val remotePlaylist = freshDiskPlaylist ?: fetchRemotePlaylist()?.also { (_, source) -> cachePlaylist(cacheFile, source) }?.first
      val staleDiskPlaylist = cacheFile.takeIf(File::isFile)?.let(::parsePlaylistFile)
      val bundledPlaylist =
        lazy {
          context.assets.open(BUNDLED_PLAYLIST).bufferedReader().use(::parseIptvPlaylist)
        }
      val parsed = remotePlaylist ?: staleDiskPlaylist ?: bundledPlaylist.value
      // DaddyLive's grid is optional: a failure there must not blank the rest of IPTV.
      val daddyLive =
        runCatching {
            if (refresh) DlhdChannelsRepository.refresh() else DlhdChannelsRepository.channels()
          }
          .onFailure { Log.w("GizTvIptv", "DaddyLive 24/7 channels unavailable", it) }
          .getOrDefault(emptyList())
      val merged =
        if (daddyLive.isEmpty()) {
          parsed
        } else {
          parsed.copy(channels = daddyLive + parsed.channels)
        }
      // Built here rather than on first use: this is the background thread, and the screen reads it
      // from the frame that shows the guide.
      merged.browseIndex
      cached = merged
      cachedAtEpochMs = now
      merged
    }
}

private fun fetchRemotePlaylist(): Pair<IptvPlaylist, String>? {
  val connection =
    runCatching { URL(REMOTE_PLAYLIST).openConnection() as HttpURLConnection }.getOrNull()
      ?: return null
  return try {
    connection.instanceFollowRedirects = true
    connection.connectTimeout = REMOTE_CONNECT_TIMEOUT_MS
    connection.readTimeout = REMOTE_READ_TIMEOUT_MS
    connection.setRequestProperty("Accept", "application/vnd.apple.mpegurl, audio/x-mpegurl, text/plain")
    connection.setRequestProperty("User-Agent", "GIZTV/1.13 IPTV updater")
    if (connection.responseCode !in 200..299) return null
    val source =
      connection.inputStream.bufferedReader(StandardCharsets.UTF_8).use(::readLimitedPlaylist)
        ?: return null
    val playlist = parseRemoteIptvPlaylist(source) ?: return null
    playlist to source
  } catch (_: Exception) {
    null
  } finally {
    connection.disconnect()
  }
}

private fun readLimitedPlaylist(reader: Reader): String? {
  val result = StringBuilder()
  val buffer = CharArray(16 * 1024)
  while (true) {
    val read = reader.read(buffer)
    if (read < 0) break
    if (result.length + read > MAX_REMOTE_PLAYLIST_CHARS) return null
    result.append(buffer, 0, read)
  }
  return result.toString()
}

internal fun parseRemoteIptvPlaylist(
  source: String,
  minimumChannels: Int = MIN_REMOTE_CHANNELS,
): IptvPlaylist? {
  val start = source.trimStart().take(256)
  if (!start.startsWith("#EXTM3U", ignoreCase = true) || start.contains("<html", ignoreCase = true)) {
    return null
  }
  return runCatching { parseIptvPlaylist(StringReader(source)) }
    .getOrNull()
    ?.takeIf { it.channels.size >= minimumChannels }
}

private fun parsePlaylistFile(file: File): IptvPlaylist? =
  runCatching {
      file.bufferedReader(StandardCharsets.UTF_8).use(::readLimitedPlaylist)
        ?.let(::parseRemoteIptvPlaylist)
    }
    .getOrNull()

private fun cachePlaylist(file: File, source: String) {
  runCatching {
    file.parentFile?.mkdirs()
    val temporary = File(file.parentFile, "${file.name}.new")
    temporary.writeText(source, StandardCharsets.UTF_8)
    if (!temporary.renameTo(file)) {
      temporary.copyTo(file, overwrite = true)
      temporary.delete()
    }
  }
}

internal fun parseIptvPlaylist(reader: Reader): IptvPlaylist {
  val channels = mutableListOf<IptvChannel>()
  val canonicalGroups = linkedMapOf<String, String>()
  var epgUrl: String? = null
  var pending: PendingChannel? = null

  reader.buffered().forEachLine { sourceLine ->
    val line = sourceLine.trim().removePrefix("\uFEFF")
    when {
      line.isBlank() -> Unit
      line.startsWith("#EXTM3U", ignoreCase = true) -> {
        val attributes = parseAttributes(line)
        epgUrl =
          attributes["url-tvg"]?.takeIf(String::isNotBlank)
            ?: attributes["x-tvg-url"]?.takeIf(String::isNotBlank)
      }
      line.startsWith("#EXTINF:", ignoreCase = true) -> {
        val comma = metadataCommaIndex(line)
        if (comma < 0) {
          pending = null
        } else {
          val attributes = parseAttributes(line.substring(0, comma))
          val name =
            line.substring(comma + 1).trim().takeIf(String::isNotBlank)
              ?: attributes["tvg-name"]?.trim().orEmpty()
          pending =
            PendingChannel(
              name = name,
              group = attributes["group-title"]?.trim().orEmpty(),
              logoUrl = attributes["tvg-logo"]?.trim()?.takeIf(String::isNotBlank),
              tvgId = attributes["tvg-id"]?.trim()?.takeIf(String::isNotBlank),
            )
        }
      }
      line.startsWith("#EXTVLCOPT:", ignoreCase = true) -> {
        val option = line.substringAfter(':').splitOnce('=')
        if (option != null) pending?.options?.set(option.first.lowercase(Locale.ENGLISH), option.second)
      }
      line.startsWith("#KODIPROP:", ignoreCase = true) -> {
        val property = line.substringAfter(':').splitOnce('=')
        if (property != null) pending?.properties?.set(property.first.lowercase(Locale.ENGLISH), property.second)
      }
      line.startsWith("#EXTHTTP:", ignoreCase = true) -> {
        pending?.headers?.putAll(parseJsonHeaders(line.substringAfter(':')))
      }
      line.startsWith("#") -> Unit
      line.startsWith("http://", ignoreCase = true) || line.startsWith("https://", ignoreCase = true) -> {
        val metadata = pending
        if (metadata == null || !isChannelMetadata(metadata)) return@forEachLine
        val (streamUrl, inlineHeaders) = splitUrlAndHeaders(line)
        if (!isPlayableStreamUrl(streamUrl)) return@forEachLine
        val groupCandidate = metadata.group.ifBlank { DEFAULT_GROUP }
        val group = canonicalGroups.getOrPut(groupCandidate.lowercase(Locale.ENGLISH)) { groupCandidate }
        val headers = linkedMapOf<String, String>()
        headers.putAll(metadata.headers)
        metadata.options.forEach { (key, value) -> headerName(key)?.let { headers[it] = value.trim() } }
        metadata.properties["inputstream.adaptive.stream_headers"]
          ?.let(::parseHeaderPairs)
          ?.let(headers::putAll)
        headers.putAll(inlineHeaders)
        val drm = parseDrm(metadata.properties)
        val source =
          IptvStreamSource(
            url = streamUrl,
            headers = headers.filterValues(String::isNotBlank),
            mimeType = streamMimeType(streamUrl, metadata.properties),
            drm = drm,
          )
        val channelIndex = metadata.channelIndex
        if (channelIndex == null) {
          channels +=
            IptvChannel(
              id = "iptv-${channels.size}-${metadata.tvgId.orEmpty()}",
              name = metadata.name,
              group = group,
              logoUrl = metadata.logoUrl,
              tvgId = metadata.tvgId,
              url = source.url,
              headers = source.headers,
              mimeType = source.mimeType,
              drm = source.drm,
            )
          metadata.channelIndex = channels.lastIndex
        } else {
          val channel = channels[channelIndex]
          channels[channelIndex] = channel.copy(backupSources = channel.backupSources + source)
        }
      }
    }
  }
  return IptvPlaylist(channels = channels, epgUrl = epgUrl)
}

/**
 * A playlist arranged for browsing: sorted once, with the category and the lowercase forms search
 * compares against already derived, and with the repeated listings of a channel collapsed to one.
 */
internal class IptvBrowseIndex private constructor(private val entries: List<Entry>) {
  private class Entry(
    val channel: IptvChannel,
    val category: String,
    val name: String,
    val group: String,
    val guideId: String,
  )

  val categories: List<IptvCategory> by lazy {
    val counts =
      entries.groupBy(Entry::category).mapValues { (_, group) -> group.distinctChannelCount() }
    buildList {
      add(IptvCategory(ALL_IPTV_CHANNELS, entries.distinctChannelCount()))
      IPTV_CATEGORY_ORDER.forEach { category ->
        counts[category]?.takeIf { it > 0 }?.let { add(IptvCategory(category, it)) }
      }
    }
  }

  // Reversed so the surviving listing is the same one browsing shows: associateBy keeps the last
  // entry it sees, and the collapse in distinctChannels keeps the first.
  private val byKey: Map<String, IptvChannel> by lazy {
    entries.asReversed().associateBy({ it.channel.key }, { it.channel })
  }

  /** The largest groups first: a category of hundreds is unusable filtered by its stragglers. */
  fun groups(category: String?): List<String> =
    listOf(ALL_IPTV_GROUPS) +
      entries
        .filter { it.matchesCategory(category) }
        .groupBy(Entry::group)
        .values
        .sortedWith(compareByDescending<List<Entry>> { it.size }.thenBy { it.first().group })
        .map { it.first().channel.group }

  fun visible(category: String?, group: String?, query: String): List<IptvChannel> {
    val selectedGroup = group?.takeUnless { it == ALL_IPTV_GROUPS || it == ALL_IPTV_CHANNELS }?.lowercase(Locale.ENGLISH)
    val needle = query.trim().lowercase(Locale.ENGLISH)
    return entries
      .filter { entry ->
        entry.matchesCategory(category) &&
          (selectedGroup == null || entry.group == selectedGroup) &&
          (needle.isEmpty() || entry.matches(needle))
      }
      .distinctChannels()
  }

  /** Resolves stored channel keys, keeping the order they were given in and dropping the stale. */
  fun find(keys: List<String>): List<IptvChannel> = keys.mapNotNull(byKey::get)

  private fun Entry.matchesCategory(selected: String?): Boolean =
    selected == null || selected == ALL_IPTV_CHANNELS || category.equals(selected, ignoreCase = true)

  private fun Entry.matches(needle: String): Boolean =
    name.contains(needle) ||
      group.contains(needle) ||
      guideId.contains(needle) ||
      category.contains(needle, ignoreCase = true)

  private fun List<Entry>.distinctChannels(): List<IptvChannel> =
    distinctBy { it.channel.key }.map(Entry::channel)

  private fun List<Entry>.distinctChannelCount(): Int = distinctBy { it.channel.key }.size

  companion object {
    fun of(channels: List<IptvChannel>): IptvBrowseIndex {
      // Every listing gets the addresses all of its twins hold, because collapsing them is about to
      // drop all but one, and the ones dropped are exactly what the player falls back to. Only the
      // sources are pooled: each listing keeps its own name, group, and artwork, so a channel filed
      // under two categories still reads correctly under each.
      val pooledSources =
        channels.groupBy(IptvChannel::key).mapValues { (_, copies) ->
          copies.flatMap(IptvChannel::playbackSources)
        }
      return IptvBrowseIndex(
        channels
          .map { channel ->
            val sources =
              (channel.playbackSources + pooledSources.getValue(channel.key))
                .distinctBy { source -> source.url to source.headers }
                .take(MAX_PLAYBACK_SOURCES)
            Entry(
              channel = channel.copy(backupSources = sources.drop(1)),
              category = iptvCategoryFor(channel),
              name = channel.name.lowercase(Locale.ENGLISH),
              group = channel.group.lowercase(Locale.ENGLISH),
              guideId = channel.tvgId?.lowercase(Locale.ENGLISH).orEmpty(),
            )
          }
          // Sorted once here so that filtering, which happens per keystroke, only ever has to
          // preserve the order rather than rebuild it.
          .sortedWith(compareBy(Entry::name, Entry::group))
      )
    }
  }
}

internal fun iptvGroups(channels: List<IptvChannel>): List<String> =
  buildList {
    add(ALL_IPTV_CHANNELS)
    val seen = mutableSetOf<String>()
    channels.forEach { channel ->
      if (seen.add(channel.group.lowercase(Locale.ENGLISH))) add(channel.group)
    }
  }

internal fun iptvCategories(channels: List<IptvChannel>): List<IptvCategory> =
  IptvBrowseIndex.of(channels).categories

internal fun iptvGroupsForCategory(
  channels: List<IptvChannel>,
  category: String?,
): List<String> = IptvBrowseIndex.of(channels).groups(category)

internal fun iptvCategoryFor(channel: IptvChannel): String =
  iptvCategoryFor(group = channel.group, channelName = channel.name)

internal fun iptvCategoryFor(group: String, channelName: String = ""): String {
  if (group.equals(DLHD_IPTV_GROUP, ignoreCase = true)) return IPTV_CATEGORY_DADDYLIVE
  val normalizedGroup = group.trim().lowercase(Locale.ENGLISH)
  val fallbackToName =
    normalizedGroup.isBlank() ||
      normalizedGroup == "other" ||
      normalizedGroup == "others" ||
      normalizedGroup.contains("group-title")
  val searchable =
    if (fallbackToName) "$normalizedGroup ${channelName.lowercase(Locale.ENGLISH)}"
    else normalizedGroup
  return when {
    searchable.containsAny("sport", "fifa", "football", "cricket", "tennis") -> IPTV_CATEGORY_SPORTS
    searchable.containsAny("news", "business", "legislative") -> IPTV_CATEGORY_NEWS
    searchable.containsAny("kid", "animation", "cartoon", "family") -> IPTV_CATEGORY_KIDS
    searchable.containsAny("movie", "cinema", "film", "classic") -> IPTV_CATEGORY_MOVIES
    searchable.containsAny("music", "radio") -> IPTV_CATEGORY_MUSIC
    searchable.containsAny(
      "knowledge",
      "education",
      "educational",
      "documentary",
      "science",
      "travel",
      "weather",
      "culture",
      "info",
    ) ->
      IPTV_CATEGORY_KNOWLEDGE
    searchable.containsAny("islam", "religion", "religious", "faith") -> IPTV_CATEGORY_FAITH
    searchable.containsAny(
      "entertainment",
      "lifestyle",
      "comedy",
      "cooking",
      "game show",
      "series",
      "shop",
      "relax",
    ) -> IPTV_CATEGORY_ENTERTAINMENT
    searchable.containsAny(
      "regional",
      "hindi",
      "urdu",
      "dhivehi",
      "indonesia",
      "maldives",
      "srilanka",
      "sri lanka",
      "turkey",
      "korea",
      "india",
    ) -> IPTV_CATEGORY_REGIONAL
    else -> IPTV_CATEGORY_OTHER
  }
}

private fun String.containsAny(vararg candidates: String): Boolean = candidates.any(::contains)

internal fun visibleIptvChannels(
  channels: List<IptvChannel>,
  group: String?,
  query: String,
): List<IptvChannel> =
  visibleIptvChannels(
    channels = channels,
    category = null,
    group = group?.takeUnless { it == ALL_IPTV_CHANNELS },
    query = query,
  )

internal fun visibleIptvChannels(
  channels: List<IptvChannel>,
  category: String?,
  group: String?,
  query: String,
): List<IptvChannel> = IptvBrowseIndex.of(channels).visible(category, group, query)

private data class PendingChannel(
  val name: String,
  val group: String,
  val logoUrl: String?,
  val tvgId: String?,
  val options: MutableMap<String, String> = linkedMapOf(),
  val properties: MutableMap<String, String> = linkedMapOf(),
  val headers: MutableMap<String, String> = linkedMapOf(),
  var channelIndex: Int? = null,
)

private val attributePattern = Regex("""([A-Za-z0-9_-]+)\s*=\s*"([^"]*)"""")

private fun parseAttributes(line: String): Map<String, String> =
  attributePattern.findAll(line).associate { match ->
    match.groupValues[1].lowercase(Locale.ENGLISH) to match.groupValues[2]
  }

private fun metadataCommaIndex(line: String): Int {
  var quoted = false
  line.forEachIndexed { index, character ->
    when (character) {
      '"' -> quoted = !quoted
      ',' -> if (!quoted) return index
    }
  }
  return -1
}

private fun String.splitOnce(separator: Char): Pair<String, String>? {
  val index = indexOf(separator)
  if (index <= 0) return null
  return substring(0, index).trim() to substring(index + 1).trim()
}

private fun isChannelMetadata(channel: PendingChannel): Boolean =
  channel.name.isNotBlank() &&
    !(channel.name.startsWith("##") && channel.name.endsWith("##"))

private fun isPlayableStreamUrl(url: String): Boolean {
  val uri = runCatching { java.net.URI(url) }.getOrNull() ?: return false
  val host = uri.host?.lowercase(Locale.ENGLISH).orEmpty()
  // The manifest deliberately blocks cleartext traffic, so showing http:// entries guarantees a
  // playback failure. Keep only TLS streams and exclude playlist promotion links.
  return uri.scheme.equals("https", ignoreCase = true) &&
    host !in setOf("t.me", "telegram.me", "www.telegram.me")
}

private fun splitUrlAndHeaders(line: String): Pair<String, Map<String, String>> {
  val separator = line.indexOf('|')
  if (separator < 0) return line.trim() to emptyMap()
  return line.substring(0, separator).trim() to parseHeaderPairs(line.substring(separator + 1))
}

private fun parseHeaderPairs(value: String): Map<String, String> =
  value.split('&').mapNotNull { pair ->
    val answer = pair.splitOnce('=') ?: return@mapNotNull null
    val name = headerName(decodeHeader(answer.first)) ?: return@mapNotNull null
    name to decodeHeader(answer.second)
  }.toMap()

private fun decodeHeader(value: String): String =
  runCatching {
      // A literal + is common in tokens; URLDecoder would otherwise turn it into a space.
      URLDecoder.decode(value.replace("+", "%2B"), StandardCharsets.UTF_8.name())
    }
    .getOrDefault(value)

private fun headerName(name: String): String? =
  when (name.trim().lowercase(Locale.ENGLISH)) {
    "http-user-agent", "user-agent", "user_agent" -> "User-Agent"
    "http-referrer", "http-referer", "referrer", "referer" -> "Referer"
    "http-origin", "origin" -> "Origin"
    "cookie" -> "Cookie"
    "authorization" -> "Authorization"
    else -> null
  }

private fun parseJsonHeaders(value: String): Map<String, String> =
  runCatching {
      val json = JSONObject(value)
      json.keys().asSequence().mapNotNull { key ->
        headerName(key)?.let { it to json.optString(key) }
      }.toMap()
    }
    .getOrDefault(emptyMap())

private fun streamMimeType(url: String, properties: Map<String, String>): String? {
  val manifestType = properties["inputstream.adaptive.manifest_type"]?.lowercase(Locale.ENGLISH)
  if (manifestType == "hls" || manifestType == "m3u8") return MimeTypes.APPLICATION_M3U8
  if (manifestType == "dash" || manifestType == "mpd") return MimeTypes.APPLICATION_MPD
  val path = url.substringBefore('?').lowercase(Locale.ENGLISH)
  return when {
    path.endsWith(".m3u8") -> MimeTypes.APPLICATION_M3U8
    path.endsWith(".mpd") -> MimeTypes.APPLICATION_MPD
    path.endsWith(".ts") -> MimeTypes.VIDEO_MP2T
    path.endsWith(".mp4") -> MimeTypes.VIDEO_MP4
    else -> null
  }
}

private fun parseDrm(properties: Map<String, String>): StreamDrmConfiguration? {
  val type = properties["inputstream.adaptive.license_type"]?.lowercase(Locale.ENGLISH) ?: return null
  val rawLicense = properties["inputstream.adaptive.license_key"]?.trim()?.takeIf(String::isNotBlank) ?: return null
  val scheme =
    when {
      "clearkey" in type -> StreamDrmScheme.CLEARKEY
      "widevine" in type -> StreamDrmScheme.WIDEVINE
      else -> return null
    }
  val parts = rawLicense.split('|')
  val requestHeaders = parts.getOrNull(1)?.let(::parseHeaderPairs).orEmpty()
  return StreamDrmConfiguration(scheme = scheme, license = rawLicense, requestHeaders = requestHeaders)
}
