package com.giztv.tv

import com.giztv.tv.data.failureKey
import org.junit.Assert.assertEquals
import org.junit.Assert.assertNull
import org.junit.Test

/**
 * A short drama's episodes differ only by query, so the key is what decides whether one dead
 * episode hides the whole title or just itself.
 */
class PlaybackFailureKeyTest {

  @Test
  fun everyEpisodeOfADramaSharesOneKey() {
    val one = failureKey("https://dramabox.chartdrama.com/p/42000012638/almost-lover?ep=1")
    val seventy = failureKey("https://dramabox.chartdrama.com/p/42000012638/almost-lover?ep=70")
    assertEquals(one, seventy)
    assertEquals("https://dramabox.chartdrama.com/p/42000012638/almost-lover", one)
  }

  @Test
  fun differentDramasDoNotShareAKey() {
    assertEquals(
      false,
      failureKey("https://dramabox.chartdrama.com/p/1/a?ep=1") ==
        failureKey("https://dramabox.chartdrama.com/p/2/b?ep=1"),
    )
  }

  @Test
  fun fragmentsAndTrailingSlashesDoNotSplitAKey() {
    assertEquals(
      failureKey("https://example.com/p/show"),
      failureKey("https://example.com/p/show/#top"),
    )
  }

  @Test
  fun blankIsNotAKey() {
    assertNull(failureKey("   "))
  }
}
