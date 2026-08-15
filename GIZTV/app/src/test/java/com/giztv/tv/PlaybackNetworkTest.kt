package com.giztv.tv

import com.giztv.tv.ui.player.AutomaticQualityPhase
import com.giztv.tv.ui.player.credibleBitrateEstimate
import com.giztv.tv.ui.player.initialAutomaticQualityPhase
import com.giztv.tv.ui.player.stableCacheKey
import org.junit.Assert.assertEquals
import org.junit.Assert.assertNull
import org.junit.Test

class PlaybackNetworkTest {
  @Test
  fun aSignatureThatChangesEveryResolveIsNotPartOfTheKey() {
    val first =
      stableCacheKey("https://cdn.example.com/films/1234/segment-9.ts?token=aaa&expires=1")
    val second =
      stableCacheKey("https://cdn.example.com/films/1234/segment-9.ts?token=bbb&expires=2")

    assertEquals(first, second)
    assertEquals("cdn.example.com/films/1234/segment-9.ts", first)
  }

  @Test
  fun differentSegmentsOfTheSameFilmAreKeptApart() {
    val ninth = stableCacheKey("https://cdn.example.com/films/1234/segment-9.ts?token=a")
    val tenth = stableCacheKey("https://cdn.example.com/films/1234/segment-10.ts?token=a")

    assertEquals(false, ninth == tenth)
  }

  @Test
  fun thesameSegmentPathOnDifferentHostsIsKeptApart() {
    val one = stableCacheKey("https://one.example.com/hls/index.m3u8")
    val other = stableCacheKey("https://two.example.com/hls/index.m3u8")

    assertEquals(false, one == other)
  }

  @Test
  fun anAddressWithNothingToStripSurvivesWhole() {
    assertEquals("cdn.example.com/a.ts", stableCacheKey("https://cdn.example.com/a.ts"))
    assertEquals("file.ts", stableCacheKey("file.ts"))
  }

  @Test
  fun anUnmeasuredLinkStillOpensCheaply() {
    assertEquals(AutomaticQualityPhase.LOW_STARTUP, initialAutomaticQualityPhase())
    assertEquals(AutomaticQualityPhase.LOW_STARTUP, initialAutomaticQualityPhase(3_000_000L))
  }

  @Test
  fun aMeasuredLinkOpensWhereItHasAlreadyBeenShownToWork() {
    assertEquals(AutomaticQualityPhase.BALANCED, initialAutomaticQualityPhase(8_000_000L))
    assertEquals(AutomaticQualityPhase.UNRESTRICTED, initialAutomaticQualityPhase(25_000_000L))
  }

  @Test
  fun onlyAMeasurementThatDescribesALinkIsRemembered() {
    // A dead edge, and a replay served off local disk. Neither is this connection.
    assertNull(credibleBitrateEstimate(50_000L))
    assertNull(credibleBitrateEstimate(4_000_000_000L))
    assertEquals(12_000_000L, credibleBitrateEstimate(12_000_000L))
  }
}
