package androidx.media3.exoplayer

import android.content.Context
import androidx.media3.common.AudioAttributes
import androidx.media3.common.MediaItem
import androidx.media3.common.PlaybackException
import androidx.media3.common.Player
import androidx.media3.common.TrackSelectionParameters

open class ExoPlayer : Player {
    override var playWhenReady: Boolean = true
    override var isPlaying: Boolean = false
    override var currentPosition: Long = 0L
    override var duration: Long = 1000L
    override var volume: Float = 1.0f

    var trackSelectionParameters: TrackSelectionParameters = TrackSelectionParameters()

    private val listeners = mutableListOf<Player.Listener>()

    override fun play() {
        isPlaying = true
        listeners.forEach { it.onIsPlayingChanged(true) }
    }

    override fun pause() {
        isPlaying = false
        listeners.forEach { it.onIsPlayingChanged(false) }
    }

    override fun seekTo(positionMs: Long) {
        currentPosition = positionMs
    }

    override fun setMediaItem(item: MediaItem) {}
    override fun prepare() {
        listeners.forEach { it.onPlaybackStateChanged(Player.STATE_READY) }
    }
    override fun release() {}

    override fun addListener(listener: Player.Listener) { listeners.add(listener) }
    override fun removeListener(listener: Player.Listener) { listeners.remove(listener) }

    class Builder(context: Context) {
        fun setAudioAttributes(attrs: AudioAttributes, handleAudioFocus: Boolean) = this
        fun setLoadControl(control: Any) = this
        fun setTrackSelector(selector: Any) = this
        fun setMediaSourceFactory(factory: Any) = this
        fun build(): ExoPlayer = ExoPlayer()
    }
}

class DefaultLoadControl {
    class Builder {
        fun setBufferDurationsMs(minBufferMs: Int, maxBufferMs: Int, bufferForPlaybackMs: Int, bufferForPlaybackAfterRebufferMs: Int) = this
        fun setPrioritizeTimeOverSizeThresholds(prioritize: Boolean) = this
        fun build() = DefaultLoadControl()
    }
}
