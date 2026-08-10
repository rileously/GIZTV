package com.giztv.tv.ui.player

import androidx.annotation.OptIn
import androidx.media3.cast.DefaultMediaItemConverter
import androidx.media3.cast.MediaItemConverter
import androidx.media3.common.C
import androidx.media3.common.MediaItem
import androidx.media3.common.util.UnstableApi
import com.google.android.gms.cast.MediaInfo
import com.google.android.gms.cast.MediaMetadata
import com.google.android.gms.cast.MediaQueueItem
import com.google.android.gms.cast.MediaTrack
import com.google.android.gms.common.images.WebImage

/**
 * Converts Media3 items for the default Cast receiver.
 *
 * Adds external subtitle tracks, forces a usable content type, marks live streams, and when the
 * origin needs Referer/Cookie/etc. rewrites the media URL through [CastMediaProxy] so the TV can
 * actually fetch the same bytes the phone plays locally.
 */
@OptIn(UnstableApi::class)
internal class CastSubtitleMediaItemConverter : MediaItemConverter {
  private val delegate = DefaultMediaItemConverter()

  override fun toMediaQueueItem(mediaItem: MediaItem): MediaQueueItem {
    val local = requireNotNull(mediaItem.localConfiguration) { "Cast media needs a playback URI" }
    val extras = mediaItem.requestMetadata.extras
    val headers = headersFromCastExtras(extras)
    val originUrl = local.uri.toString()
    val contentType = resolvePlaybackMimeType(originUrl, local.mimeType)
    val isLive = extras?.getBoolean(CAST_EXTRA_IS_LIVE, false) == true
    val castUrl =
      if (castRequiresPhoneProxy(headers)) {
        CastMediaProxy.publicUrl(originUrl, headers) ?: originUrl
      } else {
        originUrl
      }

    val subtitleConfigurations = local.subtitleConfigurations
    val subtitleTracks =
      subtitleConfigurations.mapIndexed { index, subtitle ->
        val subtitleUrl = subtitle.uri.toString()
        val castSubtitleUrl =
          if (castRequiresPhoneProxy(headers)) {
            CastMediaProxy.publicUrl(subtitleUrl, headers) ?: subtitleUrl
          } else {
            subtitleUrl
          }
        MediaTrack.Builder(CAST_SUBTITLE_TRACK_ID_BASE + index, MediaTrack.TYPE_TEXT)
          .setContentId(castSubtitleUrl)
          .setContentType(subtitle.mimeType ?: "text/vtt")
          .setName(subtitle.label ?: subtitle.language ?: "Subtitle ${index + 1}")
          .setLanguage(subtitle.language ?: "und")
          .setSubtype(MediaTrack.SUBTYPE_SUBTITLES)
          .setRoles(listOf(MediaTrack.ROLE_SUBTITLE))
          .build()
      }

    val metadata = castMetadata(mediaItem, contentType)
    val streamType =
      if (isLive) MediaInfo.STREAM_TYPE_LIVE else MediaInfo.STREAM_TYPE_BUFFERED
    val converted = runCatching { delegate.toMediaQueueItem(mediaItem) }.getOrNull()
    val delegated = converted?.media

    val castMediaInfo =
      MediaInfo.Builder(castUrl)
        .setStreamType(streamType)
        .setContentType(contentType)
        .setContentUrl(castUrl)
        .setMetadata(metadata)
        .apply {
          // Preserve Media3 customData (DRM player config, etc.) from the default converter.
          delegated?.customData?.let(::setCustomData)
          if (subtitleTracks.isNotEmpty()) {
            setMediaTracks(subtitleTracks)
          } else {
            delegated?.mediaTracks?.let(::setMediaTracks)
          }
          delegated?.textTrackStyle?.let(::setTextTrackStyle)
          delegated?.entity?.let(::setEntity)
        }
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
      .setAutoplay(converted?.autoplay ?: true)
      .setStartTime(converted?.startTime ?: 0.0)
      .apply {
        converted?.preloadTime?.let(::setPreloadTime)
        converted?.playbackDuration?.takeIf { it > 0 }?.let(::setPlaybackDuration)
        converted?.customData?.let(::setCustomData)
        preferredEnglishTrackId?.let { setActiveTrackIds(longArrayOf(it)) }
      }
      .build()
  }

  override fun toMediaItem(mediaQueueItem: MediaQueueItem): MediaItem = delegate.toMediaItem(mediaQueueItem)

  private fun castMetadata(mediaItem: MediaItem, contentType: String): MediaMetadata {
    val mediaType =
      when (mediaItem.mediaMetadata.mediaType) {
        androidx.media3.common.MediaMetadata.MEDIA_TYPE_TV_SHOW -> MediaMetadata.MEDIA_TYPE_TV_SHOW
        androidx.media3.common.MediaMetadata.MEDIA_TYPE_MUSIC,
        androidx.media3.common.MediaMetadata.MEDIA_TYPE_RADIO_STATION,
        -> MediaMetadata.MEDIA_TYPE_MUSIC_TRACK
        else ->
          if (contentType.startsWith("audio/", ignoreCase = true)) {
            MediaMetadata.MEDIA_TYPE_MUSIC_TRACK
          } else {
            MediaMetadata.MEDIA_TYPE_MOVIE
          }
      }
    return MediaMetadata(mediaType).apply {
      mediaItem.mediaMetadata.title?.toString()?.takeIf(String::isNotBlank)?.let {
        putString(MediaMetadata.KEY_TITLE, it)
      }
      mediaItem.mediaMetadata.subtitle?.toString()?.takeIf(String::isNotBlank)?.let {
        putString(MediaMetadata.KEY_SUBTITLE, it)
      }
      mediaItem.mediaMetadata.artworkUri?.let { addImage(WebImage(it)) }
    }
  }

  private companion object {
    const val CAST_SUBTITLE_TRACK_ID_BASE = 10_000L
  }
}
