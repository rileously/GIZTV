package com.example.auroratv.ui.iptv

import android.content.Context
import androidx.media3.common.MimeTypes
import com.example.auroratv.ui.player.HlsStreamRequest
import com.example.auroratv.ui.player.StreamDrmConfiguration
import com.example.auroratv.ui.player.StreamDrmScheme
import java.io.Reader
import java.net.URLDecoder
import java.nio.charset.StandardCharsets
import java.util.Locale
import kotlinx.coroutines.Dispatchers
import kotlinx.coroutines.withContext
import org.json.JSONObject

internal const val ALL_IPTV_CHANNELS = "All channels"
private const val DEFAULT_GROUP = "Other"
private const val BUNDLED_PLAYLIST = "iptv/play.m3u"

internal data class IptvPlaylist(
  val channels: List<IptvChannel>,
  val epgUrl: String? = null,
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
) {
  val formatLabel: String
    get() =
      when (mimeType) {
        MimeTypes.APPLICATION_M3U8 -> "HLS"
        MimeTypes.APPLICATION_MPD -> "DASH"
        MimeTypes.VIDEO_MP2T -> "MPEG-TS"
        MimeTypes.VIDEO_MP4 -> "MP4"
        else -> "LIVE"
      }

  fun toPlaybackRequest(): HlsStreamRequest =
    HlsStreamRequest(
      url = url,
      headers = headers,
      sourcePageUrl = url,
      title = name,
      subtitle = group,
      mimeType = mimeType,
      drm = drm,
      isLive = true,
    )
}

internal object IptvRepository {
  @Volatile private var cached: IptvPlaylist? = null

  suspend fun playlist(context: Context, refresh: Boolean = false): IptvPlaylist =
    withContext(Dispatchers.IO) {
      if (!refresh) cached?.let { return@withContext it }
      val parsed =
        context.assets.open(BUNDLED_PLAYLIST).bufferedReader().use(::parseIptvPlaylist)
      cached = parsed
      parsed
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
        epgUrl = parseAttributes(line)["url-tvg"]?.takeIf(String::isNotBlank)
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
        pending = null
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
        channels +=
          IptvChannel(
            id = "iptv-${channels.size}-${metadata.tvgId.orEmpty()}",
            name = metadata.name,
            group = group,
            logoUrl = metadata.logoUrl,
            tvgId = metadata.tvgId,
            url = streamUrl,
            headers = headers.filterValues(String::isNotBlank),
            mimeType = streamMimeType(streamUrl, metadata.properties),
            drm = drm,
          )
      }
    }
  }
  return IptvPlaylist(channels = channels, epgUrl = epgUrl)
}

internal fun iptvGroups(channels: List<IptvChannel>): List<String> =
  buildList {
    add(ALL_IPTV_CHANNELS)
    val seen = mutableSetOf<String>()
    channels.forEach { channel ->
      if (seen.add(channel.group.lowercase(Locale.ENGLISH))) add(channel.group)
    }
  }

internal fun visibleIptvChannels(
  channels: List<IptvChannel>,
  group: String?,
  query: String,
): List<IptvChannel> {
  val selected = group?.takeUnless { it == ALL_IPTV_CHANNELS }
  val needle = query.trim()
  return channels.filter { channel ->
    val inGroup = selected == null || channel.group.equals(selected, ignoreCase = true)
    val matches =
      needle.isBlank() || channel.name.contains(needle, ignoreCase = true) ||
        channel.group.contains(needle, ignoreCase = true) ||
        channel.tvgId?.contains(needle, ignoreCase = true) == true
    inGroup && matches
  }
}

private data class PendingChannel(
  val name: String,
  val group: String,
  val logoUrl: String?,
  val tvgId: String?,
  val options: MutableMap<String, String> = linkedMapOf(),
  val properties: MutableMap<String, String> = linkedMapOf(),
  val headers: MutableMap<String, String> = linkedMapOf(),
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
  val host = runCatching { java.net.URI(url).host?.lowercase(Locale.ENGLISH) }.getOrNull().orEmpty()
  return host !in setOf("t.me", "telegram.me", "www.telegram.me")
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
