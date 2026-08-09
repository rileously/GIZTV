package com.example.auroratv.ui.player

import com.example.auroratv.data.PlaybackContext
import androidx.media3.common.TrackSelectionOverride

internal enum class StreamDrmScheme {
  CLEARKEY,
  WIDEVINE,
}

internal data class StreamDrmConfiguration(
  val scheme: StreamDrmScheme,
  val license: String,
  val requestHeaders: Map<String, String> = emptyMap(),
)

internal data class ExternalSubtitleTrack(
  val url: String,
  val label: String,
  val language: String? = null,
  val mimeType: String = "text/vtt",
)

internal data class HlsStreamRequest(
  val url: String,
  val headers: Map<String, String> = emptyMap(),
  val subtitles: List<ExternalSubtitleTrack> = emptyList(),
  val sourcePageUrl: String? = null,
  val title: String? = null,
  val subtitle: String? = null,
  val mimeType: String? = null,
  val drm: StreamDrmConfiguration? = null,
  val isLive: Boolean = false,
  val context: PlaybackContext? = null,
  val sourceIndex: Int = 0,
  val sourceCount: Int = 1,
)

internal data class AudioTrackOption(
  val label: String,
  val override: TrackSelectionOverride? = null,
) {
  val isAutomatic: Boolean get() = override == null
}

internal data class SubtitleTrackOption(
  val label: String,
  val override: TrackSelectionOverride? = null,
  val disabled: Boolean = false,
)

internal enum class SubtitleSizeOption(val label: String, val scale: Float) {
  SMALL("Small", .78f),
  NORMAL("Normal", 1f),
  LARGE("Large", 1.25f),
  EXTRA_LARGE("Extra large", 1.5f),
}

internal enum class SubtitlePositionOption(val label: String, val bottomPadding: Float) {
  BOTTOM("Bottom", .08f),
  RAISED("Raised", .18f),
  HIGH("High", .30f),
}

internal enum class SubtitleStyleOption(val label: String) {
  OUTLINE("Outline"),
  DARK_BOX("Dark box"),
}

internal enum class VideoResizeOption(val label: String) {
  FIT("Fit"),
  ZOOM("Zoom"),
  STRETCH("Stretch"),
  FIT_WIDTH("Fit width"),
  FIT_HEIGHT("Fit height"),
}

internal fun preferredEnglishSubtitleIndex(
  count: Int,
  isEnglish: (Int) -> Boolean,
  isHearingImpaired: (Int) -> Boolean,
): Int? {
  val english = (0 until count).filter(isEnglish)
  if (english.isEmpty()) return null
  val preferred = english.filterNot(isHearingImpaired).ifEmpty { english }
  return preferred.getOrNull(1) ?: preferred.first()
}

internal fun isEnglishSubtitleLabel(label: String?, language: String?): Boolean {
  val lang = language?.lowercase()
  return lang in setOf("en", "eng") ||
    lang?.startsWith("en-") == true ||
    label?.contains("english", ignoreCase = true) == true
}

internal fun isHearingImpairedSubtitleLabel(label: String?): Boolean =
  label?.contains("hi", ignoreCase = true) == true
