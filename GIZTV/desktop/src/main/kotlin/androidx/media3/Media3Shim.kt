package androidx.media3.common

import android.net.Uri

object C {
    const val TRACK_TYPE_UNKNOWN = -1
    const val TRACK_TYPE_DEFAULT = 0
    const val TRACK_TYPE_VIDEO = 1
    const val TRACK_TYPE_AUDIO = 2
    const val TRACK_TYPE_TEXT = 3
    const val VIDEO_SCALING_MODE_SCALE_TO_FIT = 1
    const val VIDEO_SCALING_MODE_SCALE_TO_FIT_WITH_CROPPING = 2
    const val AUDIO_CONTENT_TYPE_MOVIE = 3
    const val USAGE_MEDIA = 1
    const val TIME_UNSET = -9223372036854775807L
}

object MimeTypes {
    const val APPLICATION_M3U8 = "application/x-mpegURL"
    const val APPLICATION_MPD = "application/dash+xml"
    const val VIDEO_MP4 = "video/mp4"
    const val VIDEO_WEBM = "video/webm"
    const val VIDEO_H264 = "video/avc"
    const val VIDEO_H265 = "video/hevc"
    const val VIDEO_MP2T = "video/mp2t"
}

open class MediaItem {
    companion object {
        @JvmStatic
        fun fromUri(uri: String): MediaItem = MediaItem()
        @JvmStatic
        fun fromUri(uri: Uri): MediaItem = MediaItem()
    }
}

class AudioAttributes {
    class Builder {
        fun setUsage(usage: Int) = this
        fun setContentType(type: Int) = this
        fun build() = AudioAttributes()
    }
}

class TrackSelectionParameters {
    class Builder {
        fun setTrackTypeDisabled(type: Int, disabled: Boolean) = this
        fun build() = TrackSelectionParameters()
    }
}

class TrackSelectionOverride(val group: Any, val trackIndex: Int)
class Format(val sampleMimeType: String? = null, val language: String? = null, val label: String? = null)
class Tracks
class VideoSize(val width: Int = 1920, val height: Int = 1080)
class PlaybackException(msg: String? = null, cause: Throwable? = null) : Exception(msg, cause)

interface Player {
    var playWhenReady: Boolean
    val isPlaying: Boolean
    val currentPosition: Long
    val duration: Long
    var volume: Float

    fun play()
    fun pause()
    fun seekTo(positionMs: Long)
    fun setMediaItem(item: MediaItem)
    fun prepare()
    fun release()

    interface Listener {
        fun onIsPlayingChanged(isPlaying: Boolean) {}
        fun onPlaybackStateChanged(state: Int) {}
        fun onPlayerError(error: PlaybackException) {}
    }

    fun addListener(listener: Listener) {}
    fun removeListener(listener: Listener) {}

    companion object {
        const val STATE_IDLE = 1
        const val STATE_BUFFERING = 2
        const val STATE_READY = 3
        const val STATE_ENDED = 4
    }
}
