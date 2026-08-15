@file:Suppress("UnsafeOptInUsageError")

package com.giztv.tv.ui.player

import android.os.SystemClock
import androidx.media3.common.Timeline
import androidx.media3.exoplayer.LoadControl
import androidx.media3.exoplayer.Renderer
import androidx.media3.exoplayer.analytics.PlayerId
import androidx.media3.exoplayer.source.MediaSource
import androidx.media3.exoplayer.source.TrackGroupArray
import androidx.media3.exoplayer.trackselection.ExoTrackSelection
import androidx.media3.exoplayer.upstream.Allocator

/**
 * How long a jump counts as a jump.
 *
 * The fetch that follows a seek either lands inside this window or the wait has stopped being about
 * the seek and started being about the connection, at which point the ordinary cushion is right.
 */
internal const val SEEK_RESUME_WINDOW_MS = 12_000L

/** What has to be in hand to resume from a seek, as opposed to from a link that failed. */
internal const val SEEK_RESUME_BUFFER_MS = 1_200L

/**
 * Resumes sooner from a jump than from a stall.
 *
 * ExoPlayer measures both with one number. Whatever empties the buffer — the viewer skipping
 * forward, or the connection failing — nothing plays again until `bufferForPlaybackAfterRebuffer` is
 * held, and this app asks for three seconds on a phone and four on a television. After a stall that
 * is exactly right: the link has just proved it cannot keep up, and resuming on a thin buffer only
 * stalls again a moment later. After a seek it is three seconds of nothing for no reason at all —
 * the buffer was emptied on purpose, by someone who is waiting to see where they landed.
 *
 * So the two are told apart. A seek is noted when it happens, and for a short window afterwards
 * playback may resume on [SEEK_RESUME_BUFFER_MS]; everything else, including a stall that arrives
 * later in that same window, is handed to the load control underneath with its full cushion intact.
 *
 * Every other decision — how much to load, how much to keep behind, when to stop — belongs to the
 * delegate and is passed straight through.
 */
internal class SeekAwareLoadControl(private val delegate: LoadControl) : LoadControl {
  @Volatile private var lastSeekAtMs = 0L

  /** Called when the player reports that the position moved because someone asked it to. */
  fun noteSeek() {
    lastSeekAtMs = SystemClock.elapsedRealtime()
  }

  override fun shouldStartPlayback(parameters: LoadControl.Parameters): Boolean {
    if (
      shouldResumeFromSeek(
        rebuffering = parameters.rebuffering,
        bufferedDurationUs = parameters.bufferedDurationUs,
        sinceSeekMs = SystemClock.elapsedRealtime() - lastSeekAtMs,
      )
    ) {
      return true
    }
    return delegate.shouldStartPlayback(parameters)
  }

  override fun getAllocator(playerId: PlayerId): Allocator = delegate.getAllocator(playerId)

  override fun onPrepared(playerId: PlayerId) = delegate.onPrepared(playerId)

  override fun onStopped(playerId: PlayerId) = delegate.onStopped(playerId)

  override fun onReleased(playerId: PlayerId) = delegate.onReleased(playerId)

  override fun onTracksSelected(
    parameters: LoadControl.Parameters,
    trackGroups: TrackGroupArray,
    trackSelections: Array<out ExoTrackSelection>,
  ) = delegate.onTracksSelected(parameters, trackGroups, trackSelections)

  override fun getBackBufferDurationUs(playerId: PlayerId): Long =
    delegate.getBackBufferDurationUs(playerId)

  override fun retainBackBufferFromKeyframe(playerId: PlayerId): Boolean =
    delegate.retainBackBufferFromKeyframe(playerId)

  override fun shouldContinueLoading(parameters: LoadControl.Parameters): Boolean =
    delegate.shouldContinueLoading(parameters)

  override fun shouldContinuePreloading(
    playerId: PlayerId,
    timeline: Timeline,
    mediaPeriodId: MediaSource.MediaPeriodId,
    bufferedDurationUs: Long,
  ): Boolean =
    delegate.shouldContinuePreloading(playerId, timeline, mediaPeriodId, bufferedDurationUs)

  @Deprecated("Deprecated in the interface; forwarded so nothing is lost if it is called.")
  override fun onTracksSelected(
    playerId: PlayerId,
    timeline: Timeline,
    mediaPeriodId: MediaSource.MediaPeriodId,
    renderers: Array<out Renderer>,
    trackGroups: TrackGroupArray,
    trackSelections: Array<out ExoTrackSelection>,
  ) {
    @Suppress("DEPRECATION")
    delegate.onTracksSelected(
      playerId,
      timeline,
      mediaPeriodId,
      renderers,
      trackGroups,
      trackSelections,
    )
  }
}

/**
 * Whether the wait the viewer is in belongs to a seek they just made.
 *
 * Only ever shortens a wait that the buffer being empty is causing, and only for as long as the
 * seek is what emptied it.
 */
internal fun shouldResumeFromSeek(
  rebuffering: Boolean,
  bufferedDurationUs: Long,
  sinceSeekMs: Long,
  seekWindowMs: Long = SEEK_RESUME_WINDOW_MS,
  resumeBufferMs: Long = SEEK_RESUME_BUFFER_MS,
): Boolean =
  rebuffering &&
    sinceSeekMs in 0 until seekWindowMs &&
    bufferedDurationUs >= resumeBufferMs * 1_000L
