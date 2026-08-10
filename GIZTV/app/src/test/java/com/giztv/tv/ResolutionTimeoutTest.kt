package com.giztv.tv

import com.giztv.tv.data.DEFAULT_RESOLUTION_TIMEOUT_MS
import com.giztv.tv.data.MAXIMUM_RESOLUTION_TIMEOUT_MS
import com.giztv.tv.data.MINIMUM_RESOLUTION_TIMEOUT_MS
import com.giztv.tv.data.streamResolutionTimeoutMs
import org.junit.Assert.assertEquals
import org.junit.Assert.assertTrue
import org.junit.Test

/** How long a page is given before it is declared not to be answering. */
class ResolutionTimeoutTest {

  /** Nothing known about it yet, so it gets the benefit of the doubt in full. */
  @Test
  fun anUnknownPageGetsTheDefault() {
    assertEquals(DEFAULT_RESOLUTION_TIMEOUT_MS, streamResolutionTimeoutMs(0L))
    assertEquals(DEFAULT_RESOLUTION_TIMEOUT_MS, streamResolutionTimeoutMs(-1L))
  }

  /** A site that has always been quick no longer keeps anyone waiting half a minute for nothing. */
  @Test
  fun aQuickSiteGivesUpSooner() {
    val timeout = streamResolutionTimeoutMs(3_000L)

    assertTrue("$timeout should be under the old fixed wait", timeout < DEFAULT_RESOLUTION_TIMEOUT_MS)
    assertEquals(MINIMUM_RESOLUTION_TIMEOUT_MS, timeout)
  }

  /** Three times what it has taken before: a slow night still finishes rather than failing. */
  @Test
  fun aSlowSiteIsGivenRoomToBeSlowAgain() {
    assertEquals(36_000L, streamResolutionTimeoutMs(12_000L))
    assertTrue(streamResolutionTimeoutMs(12_000L) > DEFAULT_RESOLUTION_TIMEOUT_MS)
  }

  /** However quick it has been, nothing is cut off before the floor. */
  @Test
  fun nothingIsCutOffBeforeTheFloor() {
    listOf(300L, 1_000L, 4_999L).forEach {
      assertEquals(MINIMUM_RESOLUTION_TIMEOUT_MS, streamResolutionTimeoutMs(it))
    }
  }

  /** And a page that once took a minute does not get three. */
  @Test
  fun aHungPageStillEnds() {
    assertEquals(MAXIMUM_RESOLUTION_TIMEOUT_MS, streamResolutionTimeoutMs(60_000L))
    assertEquals(MAXIMUM_RESOLUTION_TIMEOUT_MS, streamResolutionTimeoutMs(600_000L))
  }

  @Test
  fun everyAnswerStaysWithinItsBounds() {
    listOf(0L, 250L, 3_000L, 8_000L, 20_000L, 45_000L, 120_000L).forEach {
      val timeout = streamResolutionTimeoutMs(it)
      assertTrue("$it gave $timeout", timeout in MINIMUM_RESOLUTION_TIMEOUT_MS..MAXIMUM_RESOLUTION_TIMEOUT_MS)
    }
  }
}
