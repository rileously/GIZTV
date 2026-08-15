package com.giztv.tv

import com.giztv.tv.ui.player.SEEK_RESUME_BUFFER_MS
import com.giztv.tv.ui.player.SEEK_RESUME_WINDOW_MS
import com.giztv.tv.ui.player.seekBackBufferMs
import com.giztv.tv.ui.player.shouldResumeFromSeek
import org.junit.Assert.assertEquals
import org.junit.Assert.assertFalse
import org.junit.Assert.assertTrue
import org.junit.Test

private fun bufferedMs(ms: Long) = ms * 1_000L

class SeekAwareLoadControlTest {
  @Test
  fun aJumpResumesOnALittleRatherThanOnTheStallCushion() {
    assertTrue(
      shouldResumeFromSeek(
        rebuffering = true,
        bufferedDurationUs = bufferedMs(SEEK_RESUME_BUFFER_MS),
        sinceSeekMs = 300L,
      )
    )
  }

  @Test
  fun aStallStillWaitsForTheFullCushion() {
    // Nothing was seeked; this is the connection failing, and the load control underneath decides.
    assertFalse(
      shouldResumeFromSeek(
        rebuffering = true,
        bufferedDurationUs = bufferedMs(2_000L),
        sinceSeekMs = 90_000L,
      )
    )
  }

  @Test
  fun aStallThatArrivesLongAfterTheSeekIsStillAStall() {
    assertFalse(
      shouldResumeFromSeek(
        rebuffering = true,
        bufferedDurationUs = bufferedMs(2_000L),
        sinceSeekMs = SEEK_RESUME_WINDOW_MS + 1L,
      )
    )
  }

  @Test
  fun anEmptyBufferIsNeverEnoughToResumeOn() {
    assertFalse(
      shouldResumeFromSeek(rebuffering = true, bufferedDurationUs = 0L, sinceSeekMs = 200L)
    )
  }

  @Test
  fun playbackThatIsNotWaitingOnTheBufferIsLeftAlone() {
    assertFalse(
      shouldResumeFromSeek(
        rebuffering = false,
        bufferedDurationUs = bufferedMs(30_000L),
        sinceSeekMs = 200L,
      )
    )
  }

  @Test
  fun aDeviceWithRoomKeepsMoreOfWhatWasWatched() {
    assertEquals(45_000, seekBackBufferMs(lowRamDevice = false))
    assertEquals(15_000, seekBackBufferMs(lowRamDevice = true))
  }
}
