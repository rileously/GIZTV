package com.giztv.tv

import com.giztv.tv.ui.player.AutomaticQualityPhase
import com.giztv.tv.ui.player.credibleBitrateEstimate
import com.giztv.tv.ui.player.initialAutomaticQualityPhase
import com.giztv.tv.ui.player.openingBid
import org.junit.Assert.assertEquals
import org.junit.Assert.assertNull
import org.junit.Test

class PlaybackNetworkTest {
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
  fun onlyAMeasurementThatDescribesALinkIsRemembered() {
    // A dead edge, and a replay served off local disk. Neither is this connection.
    assertNull(credibleBitrateEstimate(50_000L))
    assertNull(credibleBitrateEstimate(4_000_000_000L))
    assertEquals(12_000_000L, credibleBitrateEstimate(12_000_000L))
  }
}
