package com.giztv.tv

import com.giztv.tv.ui.player.AutomaticQualityPhase
import com.giztv.tv.ui.player.credibleBitrateEstimate
import com.giztv.tv.ui.player.initialAutomaticQualityPhase
import com.giztv.tv.ui.player.looksLikePlaylist
import com.giztv.tv.ui.player.openingBid
import com.giztv.tv.ui.player.stableCacheKey
import org.junit.Assert.assertEquals
import org.junit.Assert.assertFalse
import org.junit.Assert.assertNull
import org.junit.Assert.assertTrue
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
    assertEquals(
      AutomaticQualityPhase.LOW_STARTUP,
      initialAutomaticQualityPhase(3_000_000L, measuredThisSession = true),
    )
  }

  @Test
  fun whatTheAppRemembersFromLastTimeNeverSkipsARung() {
    // The viewer may have left the Wi-Fi it was measured on. Only this session's evidence counts.
    assertEquals(
      AutomaticQualityPhase.LOW_STARTUP,
      initialAutomaticQualityPhase(80_000_000L, measuredThisSession = false),
    )
  }

  @Test
  fun aLinkMeasuredMomentsAgoOpensWhereItHasBeenShownToWork() {
    assertEquals(
      AutomaticQualityPhase.BALANCED,
      initialAutomaticQualityPhase(12_000_000L, measuredThisSession = true),
    )
  }

  @Test
  fun theTopOfTheLadderIsStillEarnedByPlaying() {
    assertEquals(
      AutomaticQualityPhase.BALANCED,
      initialAutomaticQualityPhase(80_000_000L, measuredThisSession = true),
    )
  }

  @Test
  fun aRememberedSpeedIsOpenedWithBelowWhatItMeasured() {
    assertEquals(6_000_000L, openingBid(10_000_000L))
  }

  @Test
  fun playlistsAreRecognisedWhateverIsHungOffTheAddress() {
    assertTrue(looksLikePlaylist("https://cdn.example.com/a/index.m3u8?token=x&y=2"))
    assertTrue(looksLikePlaylist("https://cdn.example.com/a/stream.MPD"))
    assertFalse(looksLikePlaylist("https://cdn.example.com/a/segment-4.ts?token=x"))
    assertFalse(looksLikePlaylist("https://cdn.example.com/film-1080p.mp4"))
  }

  @Test
  fun onlyAMeasurementThatDescribesALinkIsRemembered() {
    // A dead edge, and a replay served off local disk. Neither is this connection.
    assertNull(credibleBitrateEstimate(50_000L))
    assertNull(credibleBitrateEstimate(4_000_000_000L))
    assertEquals(12_000_000L, credibleBitrateEstimate(12_000_000L))
  }
}
