package com.example.auroratv.ui.player

import java.io.BufferedReader
import java.io.StringReader
import java.net.HttpURLConnection
import java.net.URL
import java.util.Locale
import java.util.concurrent.TimeUnit
import kotlinx.coroutines.Dispatchers
import kotlinx.coroutines.withContext

/** One timed line from an external VTT/SRT file, used by pause-and-match sync. */
internal data class SubtitleCue(
  val startMs: Long,
  val endMs: Long,
  val text: String,
)

internal const val NEARBY_SUBTITLE_CUE_WINDOW_MS = 45_000L

/** Cues whose start falls inside [positionMs] ± [windowMs], in timeline order. */
internal fun nearbySubtitleCues(
  cues: List<SubtitleCue>,
  positionMs: Long,
  windowMs: Long = NEARBY_SUBTITLE_CUE_WINDOW_MS,
): List<SubtitleCue> {
  val from = (positionMs - windowMs).coerceAtLeast(0L)
  val to = positionMs + windowMs
  return cues.filter { it.startMs in from..to }
}

/**
 * Offset that makes [cueStartMs] land on [playbackPositionMs].
 *
 * Positive means captions appear later than the source file; negative means earlier.
 */
internal fun subtitleOffsetForCueMatch(playbackPositionMs: Long, cueStartMs: Long): Long =
  (playbackPositionMs - cueStartMs).coerceIn(-MAX_SUBTITLE_SYNC_MS, MAX_SUBTITLE_SYNC_MS)

internal fun parseSubtitleCues(body: String, mimeType: String?): List<SubtitleCue> {
  val normalized = mimeType?.lowercase(Locale.US).orEmpty()
  return when {
    normalized.contains("vtt") || body.trimStart().startsWith("WEBVTT", ignoreCase = true) ->
      parseWebVttCues(body)
    normalized.contains("subrip") || normalized.contains("srt") -> parseSubRipCues(body)
    body.trimStart().startsWith("WEBVTT", ignoreCase = true) -> parseWebVttCues(body)
    else -> {
      val vtt = parseWebVttCues(body)
      if (vtt.isNotEmpty()) vtt else parseSubRipCues(body)
    }
  }
}

internal fun parseWebVttCues(body: String): List<SubtitleCue> {
  val cues = mutableListOf<SubtitleCue>()
  val reader = BufferedReader(StringReader(body.replace("\r\n", "\n").replace('\r', '\n')))
  var line = reader.readLine()
  // Skip the WEBVTT header and any header metadata until the first blank line.
  if (line != null && line.trimStart().startsWith("WEBVTT", ignoreCase = true)) {
    while (line != null && line.isNotBlank()) {
      line = reader.readLine()
    }
    line = reader.readLine()
  }
  while (line != null) {
    if (line.isBlank()) {
      line = reader.readLine()
      continue
    }
    var timingLine = line
    if (!timingLine.contains("-->")) {
      // Optional cue identifier on its own line.
      timingLine = reader.readLine() ?: break
    }
    val times = parseVttTimingLine(timingLine) ?: run {
      line = reader.readLine()
      continue
    }
    val textLines = mutableListOf<String>()
    line = reader.readLine()
    while (line != null && line.isNotBlank()) {
      textLines += stripVttTags(line)
      line = reader.readLine()
    }
    val text = textLines.joinToString("\n").trim()
    if (text.isNotEmpty() && times.second > times.first) {
      cues += SubtitleCue(startMs = times.first, endMs = times.second, text = text)
    }
  }
  return cues
}

internal fun parseSubRipCues(body: String): List<SubtitleCue> {
  val cues = mutableListOf<SubtitleCue>()
  val blocks =
    body
      .replace("\r\n", "\n")
      .replace('\r', '\n')
      .split(Regex("\n\\s*\n"))
  for (block in blocks) {
    val lines = block.lines().map { it.trimEnd() }.filter { it.isNotBlank() }
    if (lines.isEmpty()) continue
    val timingIndex = lines.indexOfFirst { it.contains("-->") }
    if (timingIndex < 0) continue
    val times = parseSrtTimingLine(lines[timingIndex]) ?: continue
    val text =
      lines
        .drop(timingIndex + 1)
        .joinToString("\n") { stripVttTags(it) }
        .trim()
    if (text.isNotEmpty() && times.second > times.first) {
      cues += SubtitleCue(startMs = times.first, endMs = times.second, text = text)
    }
  }
  return cues
}

/** Prefer the selected chooser label; otherwise the Auto English pick among external tracks. */
internal fun resolveSubtitleTrackForCueMatch(
  tracks: List<ExternalSubtitleTrack>,
  selectedLabel: String?,
): ExternalSubtitleTrack? {
  if (tracks.isEmpty()) return null
  selectedLabel
    ?.takeUnless { it.equals("Auto English", ignoreCase = true) || it.equals("Off", ignoreCase = true) }
    ?.let { label -> resolveNumberedSubtitleTrack(tracks, label) }
    ?.let { return it }
  val index =
    preferredEnglishSubtitleIndex(
      count = tracks.size,
      isEnglish = { isEnglishSubtitleLabel(tracks[it].label, tracks[it].language) },
      isHearingImpaired = { isHearingImpairedSubtitleLabel(tracks[it].label) },
    )
  return index?.let(tracks::get) ?: tracks.firstOrNull()
}

/**
 * Chooser labels number duplicate base names ("English", "English 2"). Map that back onto the
 * external track list, which still carries the unnumbered source label.
 */
internal fun resolveNumberedSubtitleTrack(
  tracks: List<ExternalSubtitleTrack>,
  chooserLabel: String,
): ExternalSubtitleTrack? {
  tracks.firstOrNull { it.label.equals(chooserLabel, ignoreCase = true) }?.let { return it }
  val match = Regex("""^(.*?)(?:\s+(\d+))?$""").matchEntire(chooserLabel.trim()) ?: return null
  val base = match.groupValues[1].trim()
  if (base.isEmpty()) return null
  val occurrence = match.groupValues[2].toIntOrNull() ?: 1
  val sameBase = tracks.filter { it.label.equals(base, ignoreCase = true) }
  return sameBase.getOrNull(occurrence - 1)
}

internal suspend fun downloadSubtitleCueBody(
  url: String,
  headers: Map<String, String>,
): String =
  withContext(Dispatchers.IO) {
    val connection = URL(url).openConnection() as HttpURLConnection
    try {
      connection.requestMethod = "GET"
      connection.connectTimeout = 8_000
      connection.readTimeout = 12_000
      connection.instanceFollowRedirects = true
      headers.forEach { (key, value) -> connection.setRequestProperty(key, value) }
      if (connection.getRequestProperty("User-Agent") == null) {
        connection.setRequestProperty(
          "User-Agent",
          "Mozilla/5.0 (Linux; Android 12) AppleWebKit/537.36 Chrome/120.0.0.0 Safari/537.36",
        )
      }
      if (connection.responseCode !in 200..299) {
        error("Subtitle download HTTP ${connection.responseCode}")
      }
      connection.inputStream.bufferedReader(Charsets.UTF_8).use { it.readText() }
    } finally {
      connection.disconnect()
    }
  }

private fun parseVttTimingLine(line: String): Pair<Long, Long>? {
  val parts = line.split("-->", limit = 2)
  if (parts.size != 2) return null
  val start = parseSubtitleTimestamp(parts[0].trim().substringBefore(' ')) ?: return null
  val end = parseSubtitleTimestamp(parts[1].trim().substringBefore(' ')) ?: return null
  return start to end
}

private fun parseSrtTimingLine(line: String): Pair<Long, Long>? {
  val parts = line.split("-->", limit = 2)
  if (parts.size != 2) return null
  val start = parseSubtitleTimestamp(parts[0].trim().replace(',', '.')) ?: return null
  val end = parseSubtitleTimestamp(parts[1].trim().substringBefore(' ').replace(',', '.')) ?: return null
  return start to end
}

/** Accepts `HH:MM:SS.mmm`, `MM:SS.mmm`, and comma milliseconds. */
internal fun parseSubtitleTimestamp(raw: String): Long? {
  val cleaned = raw.trim().replace(',', '.')
  val match =
    Regex("""^(?:(\d{1,2}):)?(\d{1,2}):(\d{1,2})(?:\.(\d{1,3}))?$""").matchEntire(cleaned)
      ?: return null
  val hours = match.groupValues[1].toLongOrNull() ?: 0L
  val minutes = match.groupValues[2].toLong()
  val seconds = match.groupValues[3].toLong()
  val millis =
    match.groupValues[4]
      .takeIf(String::isNotBlank)
      ?.padEnd(3, '0')
      ?.take(3)
      ?.toLongOrNull()
      ?: 0L
  return TimeUnit.HOURS.toMillis(hours) +
    TimeUnit.MINUTES.toMillis(minutes) +
    TimeUnit.SECONDS.toMillis(seconds) +
    millis
}

private fun stripVttTags(line: String): String =
  line
    .replace(Regex("</?[^>]+>"), "")
    .replace(Regex("&nbsp;", RegexOption.IGNORE_CASE), " ")
    .replace("&amp;", "&")
    .replace("&lt;", "<")
    .replace("&gt;", ">")
    .trim()
