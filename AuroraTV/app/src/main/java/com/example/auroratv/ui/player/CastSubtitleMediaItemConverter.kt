package com.example.auroratv.ui.player

import androidx.annotation.OptIn
import androidx.media3.cast.DefaultMediaItemConverter
import androidx.media3.cast.MediaItemConverter
import androidx.media3.common.C
import androidx.media3.common.MediaItem
import androidx.media3.common.util.UnstableApi
import com.google.android.gms.cast.MediaInfo
import com.google.android.gms.cast.MediaQueueItem
import com.google.android.gms.cast.MediaTrack

/** Adds Media3 external subtitle configurations as Chromecast text tracks. */
@OptIn(UnstableApi::class)
internal class CastSubtitleMediaItemConverter : MediaItemConverter {
  private val delegate = DefaultMediaItemConverter()

  override fun toMediaQueueItem(mediaItem: MediaItem): MediaQueueItem {
    val converted = delegate.toMediaQueueItem(mediaItem)
    val subtitleConfigurations = mediaItem.localConfiguration?.subtitleConfigurations.orEmpty()
    if (subtitleConfigurations.isEmpty()) return converted

    val sourceInfo = requireNotNull(converted.media)
    val subtitleTracks =
      subtitleConfigurations.mapIndexed { index, subtitle ->
        MediaTrack.Builder(CAST_SUBTITLE_TRACK_ID_BASE + index, MediaTrack.TYPE_TEXT)
          .setContentId(subtitle.uri.toString())
          .setContentType(subtitle.mimeType ?: "text/vtt")
          .setName(subtitle.label ?: subtitle.language ?: "Subtitle ${index + 1}")
          .setLanguage(subtitle.language ?: "und")
          .setSubtype(MediaTrack.SUBTYPE_SUBTITLES)
          .setRoles(listOf(MediaTrack.ROLE_SUBTITLE))
          .build()
      }

    val castMediaInfo =
      MediaInfo.Builder(sourceInfo.contentId)
        .setStreamType(sourceInfo.streamType)
        .setContentType(sourceInfo.contentType)
        .setContentUrl(sourceInfo.contentUrl ?: sourceInfo.contentId)
        .setMetadata(sourceInfo.metadata)
        .setCustomData(sourceInfo.customData)
        .setStreamDuration(sourceInfo.streamDuration)
        .setHlsSegmentFormat(sourceInfo.hlsSegmentFormat)
        .setHlsVideoSegmentFormat(sourceInfo.hlsVideoSegmentFormat)
        .setMediaTracks(subtitleTracks)
        .setTextTrackStyle(sourceInfo.textTrackStyle)
        .apply { sourceInfo.entity?.let(::setEntity) }
        .build()

    val preferredEnglishTrackId =
      preferredEnglishSubtitleIndex(
          count = subtitleConfigurations.size,
          isEnglish = { index ->
            val subtitle = subtitleConfigurations[index]
            isEnglishSubtitleLabel(subtitle.label, subtitle.language) ||
              subtitle.selectionFlags and C.SELECTION_FLAG_DEFAULT != 0
          },
          isHearingImpaired = { index ->
            isHearingImpairedSubtitleLabel(subtitleConfigurations[index].label)
          },
        )
        ?.let { CAST_SUBTITLE_TRACK_ID_BASE + it }

    return MediaQueueItem.Builder(castMediaInfo)
      .setAutoplay(converted.autoplay)
      .setStartTime(converted.startTime)
      .setPreloadTime(converted.preloadTime)
      .setPlaybackDuration(converted.playbackDuration)
      .apply {
        converted.customData?.let(::setCustomData)
        preferredEnglishTrackId?.let { setActiveTrackIds(longArrayOf(it)) }
      }
      .build()
  }

  override fun toMediaItem(mediaQueueItem: MediaQueueItem): MediaItem = delegate.toMediaItem(mediaQueueItem)

  private companion object {
    const val CAST_SUBTITLE_TRACK_ID_BASE = 10_000L
  }
}
