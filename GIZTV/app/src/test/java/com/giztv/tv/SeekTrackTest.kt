package com.giztv.tv

import com.giztv.tv.ui.player.seekTrackFractions
import org.junit.Assert.assertEquals
import org.junit.Test

class SeekTrackTest {
  @Test
  fun theBufferSitsAheadOfThePlayhead() {
    val (played, fetched) =
      seekTrackFractions(positionMs = 60_000L, bufferedMs = 120_000L, durationMs = 600_000L)

    assertEquals(0.1f, played, 0.001f)
    assertEquals(0.2f, fetched, 0.001f)
  }

  @Test
  fun aBufferMeasuredBeforeASeekNeverDrawsBehindThePlayhead() {
    // The poll runs every 250ms, so a jump forward can be read against a buffer from before it.
    val (played, fetched) =
      seekTrackFractions(positionMs = 300_000L, bufferedMs = 20_000L, durationMs = 600_000L)

    assertEquals(0.5f, played, 0.001f)
    assertEquals(played, fetched, 0.001f)
  }

  @Test
  fun aFilmOfUnknownLengthDrawsNothing() {
    val (played, fetched) =
      seekTrackFractions(positionMs = 10_000L, bufferedMs = 40_000L, durationMs = 0L)

    assertEquals(0f, played, 0.001f)
    assertEquals(0f, fetched, 0.001f)
  }

  @Test
  fun aFullyFetchedFilmFillsTheTrack() {
    val (_, fetched) =
      seekTrackFractions(positionMs = 1_000L, bufferedMs = 900_000L, durationMs = 600_000L)

    assertEquals(1f, fetched, 0.001f)
  }
}
