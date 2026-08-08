package com.example.auroratv.ui.player

import android.os.Bundle
import androidx.media3.common.MimeTypes
import java.net.URI
import java.util.Locale

/** Extras keys attached to [androidx.media3.common.MediaItem.requestMetadata] for Cast. */
internal const val CAST_EXTRA_HEADER_KEYS = "giztv.cast.headerKeys"
internal const val CAST_EXTRA_HEADER_VALUES = "giztv.cast.headerValues"
internal const val CAST_EXTRA_IS_LIVE = "giztv.cast.isLive"
internal const val CAST_EXTRA_HAS_DRM = "giztv.cast.hasDrm"

/**
 * MIME type Chromecast / Media3 should announce for a playback URL.
 *
 * Progressive files must not keep a stale HLS mime (Cast then tries to parse an MP4 as a playlist).
 * Adaptive playlists need an explicit HLS/DASH type so the default receiver picks the right pipeline.
 */
internal fun resolvePlaybackMimeType(url: String, declaredMime: String?): String {
  val path = url.substringBefore('#').substringBefore('?').lowercase(Locale.US)
  when {
    path.endsWith(".mp4") || path.endsWith(".m4v") -> return MimeTypes.VIDEO_MP4
    path.endsWith(".webm") -> return MimeTypes.VIDEO_WEBM
    path.endsWith(".mkv") -> return "video/x-matroska"
    path.endsWith(".mpd") -> return MimeTypes.APPLICATION_MPD
    path.endsWith(".m3u8") -> return MimeTypes.APPLICATION_M3U8
  }
  val mime = declaredMime?.trim().orEmpty()
  if (mime.contains("mpegurl", ignoreCase = true) || mime.equals(MimeTypes.APPLICATION_M3U8, true)) {
    return MimeTypes.APPLICATION_M3U8
  }
  if (mime.contains("dash", ignoreCase = true) || mime.equals(MimeTypes.APPLICATION_MPD, true)) {
    return MimeTypes.APPLICATION_MPD
  }
  if (mime.startsWith("video/", ignoreCase = true) || mime.startsWith("application/", ignoreCase = true)) {
    return mime
  }
  // Catalog streams default to HLS when the path has no useful extension.
  return MimeTypes.APPLICATION_M3U8
}

/** Whether the default Cast receiver is likely able to play this container at all. */
internal fun isCastContainerSupported(mimeType: String?): Boolean {
  val mime = mimeType?.lowercase(Locale.US).orEmpty()
  return mime == MimeTypes.APPLICATION_M3U8.lowercase(Locale.US) ||
    mime.contains("mpegurl") ||
    mime == MimeTypes.APPLICATION_MPD.lowercase(Locale.US) ||
    mime == MimeTypes.VIDEO_MP4 ||
    mime == "video/mp4" ||
    mime == MimeTypes.VIDEO_WEBM ||
    mime == "video/webm" ||
    mime.startsWith("audio/")
}

/** Headers that must ride with the media request or Cast will see a different (often blocked) URL. */
internal fun castSensitiveHeaders(headers: Map<String, String>): Map<String, String> =
  headers
    .filterKeys { key ->
      val name = key.lowercase(Locale.US)
      name !in setOf("accept-encoding", "connection", "content-length", "host", "range")
    }
    .filterValues(String::isNotBlank)

/** True when Cast cannot fetch the origin URL as-is and needs the phone to proxy it. */
internal fun castRequiresPhoneProxy(headers: Map<String, String>): Boolean {
  val sensitive = castSensitiveHeaders(headers)
  if (sensitive.isEmpty()) return false
  return sensitive.keys.any { key ->
    val name = key.lowercase(Locale.US)
    name == "referer" ||
      name == "origin" ||
      name == "cookie" ||
      name == "authorization" ||
      name == "user-agent" ||
      name.startsWith("x-")
  }
}

/**
 * Whether Chromecast can fetch this playlist URI itself instead of bouncing through the phone.
 *
 * Media segments are the bulk of Cast traffic. Sending every `.ts` / `.m4s` through the phone
 * doubles Wi-Fi hops and feels like constant buffering. Playlists and keys still need the proxy
 * (rewrite + Referer), but bare segment URLs usually work once Cast has the absolute CDN address.
 * Cookie / Authorization streams stay fully proxied — those CDNs reject headerless segment GETs.
 */
internal fun castCanCastFetchDirect(absoluteUrl: String, headers: Map<String, String>): Boolean {
  val sensitive = castSensitiveHeaders(headers)
  val needsAuthHeaders =
    sensitive.keys.any { key ->
      val name = key.lowercase(Locale.US)
      name == "cookie" || name == "authorization"
    }
  if (needsAuthHeaders) return false
  return isHlsMediaSegmentUrl(absoluteUrl)
}

/** True for typical HLS media segment paths (not playlists, keys, or init maps). */
internal fun isHlsMediaSegmentUrl(url: String): Boolean {
  val path = url.substringBefore('#').substringBefore('?').lowercase(Locale.US)
  return path.endsWith(".ts") ||
    path.endsWith(".m4s") ||
    path.endsWith(".cmfv") ||
    path.endsWith(".cmfa") ||
    path.endsWith(".aac") ||
    path.endsWith(".mp3") ||
    path.endsWith(".vtt") ||
    path.endsWith(".webvtt")
}

internal fun castHeadersBundle(headers: Map<String, String>): Bundle {
  val filtered = castSensitiveHeaders(headers)
  val keys = filtered.keys.toTypedArray()
  val values = keys.map { filtered.getValue(it) }.toTypedArray()
  return Bundle().apply {
    putStringArray(CAST_EXTRA_HEADER_KEYS, keys)
    putStringArray(CAST_EXTRA_HEADER_VALUES, values)
  }
}

internal fun headersFromCastExtras(extras: Bundle?): Map<String, String> {
  if (extras == null) return emptyMap()
  val keys = extras.getStringArray(CAST_EXTRA_HEADER_KEYS) ?: return emptyMap()
  val values = extras.getStringArray(CAST_EXTRA_HEADER_VALUES) ?: return emptyMap()
  if (keys.size != values.size) return emptyMap()
  return keys.mapIndexed { index, key -> key to values[index] }.toMap()
}

/**
 * Rewrites an HLS playlist so every media URI is fetched through [proxyUriFor].
 *
 * Tag lines keep their structure; only URI-bearing attributes and bare URI lines are rewritten.
 */
internal fun rewriteHlsPlaylistForCastProxy(
  body: String,
  playlistUrl: String,
  proxyUriFor: (absoluteUrl: String) -> String,
): String {
  val base = playlistUrl.substringBefore('#')
  return body
    .lineSequence()
    .map { line ->
      val trimmed = line.trim()
      when {
        trimmed.isEmpty() -> line
        trimmed.startsWith("#") -> rewriteHlsTagLine(line, base, proxyUriFor)
        else -> proxyUriFor(resolveAgainst(base, trimmed))
      }
    }
    .joinToString("\n")
}

private fun rewriteHlsTagLine(
  line: String,
  baseUrl: String,
  proxyUriFor: (absoluteUrl: String) -> String,
): String {
  // URI="..." appears on EXT-X-KEY / MAP / MEDIA / SESSION-KEY / PART / PRELOAD-HINT / etc.
  val pattern = Regex("""URI="([^"]+)"""", RegexOption.IGNORE_CASE)
  return pattern.replace(line) { match ->
    val absolute = resolveAgainst(baseUrl, match.groupValues[1])
    """URI="${proxyUriFor(absolute)}""""
  }
}

internal fun resolveAgainst(baseUrl: String, reference: String): String {
  val ref = reference.trim()
  if (ref.startsWith("http://", ignoreCase = true) || ref.startsWith("https://", ignoreCase = true)) {
    return ref
  }
  return runCatching { URI(baseUrl).resolve(ref).toString() }.getOrDefault(ref)
}

internal fun looksLikeHlsPlaylist(contentType: String?, bodyPrefix: String): Boolean {
  if (contentType?.contains("mpegurl", ignoreCase = true) == true) return true
  if (contentType?.contains("m3u8", ignoreCase = true) == true) return true
  return bodyPrefix.trimStart().startsWith("#EXTM3U")
}
