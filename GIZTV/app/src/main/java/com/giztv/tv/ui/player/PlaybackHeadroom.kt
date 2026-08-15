package com.giztv.tv.ui.player

import androidx.compose.runtime.getValue
import androidx.compose.runtime.mutableStateOf
import androidx.compose.runtime.setValue

/** How much of the connection the film being watched can spare for anything else. */
internal enum class PlaybackHeadroomStatus {
  /** Nothing is playing, so the link is nobody else's. */
  IDLE,

  /** Playing with a comfortable cushion ahead of the playhead. */
  HEALTHY,

  /** Playing, but the cushion is thin enough that a second download would be felt. */
  TIGHT,

  /** Playing and refilling — every byte spent elsewhere is a byte the film is waiting for. */
  STARVED,
}

/**
 * How far ahead a film has to be buffered before the app may go looking for the next one.
 *
 * Half a minute is roughly the point at which the adaptive selector stops treating the buffer as
 * something to protect and starts treating it as headroom to spend.
 */
internal const val PREFETCH_HEADROOM_MS = 30_000L

/**
 * What the film being watched has to spare, as the rest of the app sees it.
 *
 * Finding the next episode means loading a provider's page and letting its player start, which is a
 * second video coming down the same connection as the one being watched. It is worth doing — it is
 * the whole reason one episode rolls into the next without a wait — but not at any price, and the
 * price is paid by the viewer at exactly the moment they are deepest into an episode.
 *
 * The player publishes here; the prefetcher reads. Written as Compose state so that a film falling
 * behind stops a resolve without anything having to poll for it.
 */
internal object PlaybackHeadroom {
  var status by mutableStateOf(PlaybackHeadroomStatus.IDLE)
    private set

  fun report(status: PlaybackHeadroomStatus) {
    this.status = status
  }

  fun idle() {
    status = PlaybackHeadroomStatus.IDLE
  }
}

/**
 * What a playing film leaves for a speculative resolve.
 *
 * [bufferedAheadMs] is how much of the film is already in hand beyond the playhead.
 */
internal fun playbackHeadroomStatus(
  isBuffering: Boolean,
  bufferedAheadMs: Long,
  headroomMs: Long = PREFETCH_HEADROOM_MS,
): PlaybackHeadroomStatus =
  when {
    isBuffering -> PlaybackHeadroomStatus.STARVED
    bufferedAheadMs >= headroomMs -> PlaybackHeadroomStatus.HEALTHY
    else -> PlaybackHeadroomStatus.TIGHT
  }

/**
 * Whether the prefetcher may run.
 *
 * A resolve already under way is allowed to finish through a thin patch, because abandoning one
 * halfway wastes everything it has already spent and the next attempt starts from the beginning.
 * A film that is actually refilling is the exception: nothing else may be fetched until it is not.
 */
internal fun mayPrefetch(
  status: PlaybackHeadroomStatus,
  alreadyStarted: Boolean,
): Boolean =
  when (status) {
    PlaybackHeadroomStatus.IDLE,
    PlaybackHeadroomStatus.HEALTHY -> true
    PlaybackHeadroomStatus.TIGHT -> alreadyStarted
    PlaybackHeadroomStatus.STARVED -> false
  }
