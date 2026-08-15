package com.giztv.tv

import com.giztv.tv.ui.player.PREFETCH_HEADROOM_MS
import com.giztv.tv.ui.player.PlaybackHeadroomStatus
import com.giztv.tv.ui.player.mayPrefetch
import com.giztv.tv.ui.player.playbackHeadroomStatus
import org.junit.Assert.assertEquals
import org.junit.Assert.assertFalse
import org.junit.Assert.assertTrue
import org.junit.Test

class PlaybackHeadroomTest {
  @Test
  fun aRefillingFilmLeavesNothingForAnythingElse() {
    assertEquals(
      PlaybackHeadroomStatus.STARVED,
      playbackHeadroomStatus(isBuffering = true, bufferedAheadMs = 60_000L),
    )
  }

  @Test
  fun aDeepBufferIsHeadroom() {
    assertEquals(
      PlaybackHeadroomStatus.HEALTHY,
      playbackHeadroomStatus(isBuffering = false, bufferedAheadMs = PREFETCH_HEADROOM_MS),
    )
  }

  @Test
  fun aThinBufferIsNotWorthSpending() {
    assertEquals(
      PlaybackHeadroomStatus.TIGHT,
      playbackHeadroomStatus(isBuffering = false, bufferedAheadMs = PREFETCH_HEADROOM_MS - 1),
    )
  }

  @Test
  fun nothingPlayingMeansTheLinkIsFree() {
    assertTrue(mayPrefetch(PlaybackHeadroomStatus.IDLE, alreadyStarted = false))
  }

  @Test
  fun aResolveWaitsForClearWaterButIsNotAbandonedInIt() {
    assertFalse(mayPrefetch(PlaybackHeadroomStatus.TIGHT, alreadyStarted = false))
    assertTrue(mayPrefetch(PlaybackHeadroomStatus.TIGHT, alreadyStarted = true))
  }

  @Test
  fun aFilmThatIsRefillingStopsEvenAResolveAlreadyUnderWay() {
    assertFalse(mayPrefetch(PlaybackHeadroomStatus.STARVED, alreadyStarted = true))
  }
}
