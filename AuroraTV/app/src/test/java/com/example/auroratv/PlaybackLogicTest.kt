package com.example.auroratv

import androidx.media3.common.C
import androidx.media3.common.Format
import androidx.media3.common.Player
import androidx.media3.exoplayer.source.BehindLiveWindowException
import com.example.auroratv.ui.StreamFailureAction
import com.example.auroratv.ui.streamFailureAction
import com.example.auroratv.ui.player.AutomaticQualityPhase
import com.example.auroratv.ui.player.PlayerBackAction
import com.example.auroratv.ui.player.ProlongedStallAction
import com.example.auroratv.ui.player.STABLE_QUALITY_LABEL
import com.example.auroratv.ui.player.VideoQualityOption
import com.example.auroratv.ui.player.adjustSubtitleSync
import com.example.auroratv.ui.player.automaticQualityPhaseAfterBuffering
import com.example.auroratv.ui.player.automaticQualityPromotion
import com.example.auroratv.ui.player.dataSaverVideoFormatOrder
import com.example.auroratv.ui.player.isHlsTrackMappingFailure
import com.example.auroratv.ui.player.isBehindLiveWindowFailure
import com.example.auroratv.ui.player.lowestDataVideoFormatIndex
import com.example.auroratv.ui.player.playerBackAction
import com.example.auroratv.ui.player.playerControllerTimeoutMs
import com.example.auroratv.ui.player.playbackBufferProfile
import com.example.auroratv.ui.player.prolongedStallAction
import com.example.auroratv.ui.player.reliableHlsLoadErrorPolicy
import com.example.auroratv.ui.player.resumablePlaybackPosition
import com.example.auroratv.ui.player.subtitleSyncDescription
import com.example.auroratv.ui.player.subtitleSyncLabel
import com.example.auroratv.ui.player.touchSeekPositionMs
import org.junit.Assert.assertEquals
import org.junit.Assert.assertFalse
import org.junit.Assert.assertNull
import org.junit.Assert.assertTrue
import org.junit.Test

/**
 * Playback rules that need no device to check.
 *
 * The same ground is covered by the instrumented suite where a real player is involved; what is
 * here is the arithmetic and the decisions, which are worth being able to run in a second.
 */
class PlaybackLogicTest {
  @Test
  fun resumePosition_isKeptForAnUnfinishedTitleAndDroppedOtherwise() {
    assertEquals(
      125_000L,
      resumablePlaybackPosition(125_000L, 600_000L, Player.STATE_READY),
    )
    // The first few seconds are not a place worth coming back to.
    assertNull(resumablePlaybackPosition(3_000L, 600_000L, Player.STATE_READY))
    // Close enough to the end to count as finished.
    assertNull(resumablePlaybackPosition(595_000L, 600_000L, Player.STATE_READY))
    assertNull(resumablePlaybackPosition(125_000L, 600_000L, Player.STATE_ENDED))
  }

  @Test
  fun subtitleSync_readsClearlyInBothDirections() {
    assertEquals("-0.5s", subtitleSyncLabel(-500L))
    assertEquals("0s", subtitleSyncLabel(0L))
    assertEquals("+0.5s", subtitleSyncLabel(500L))
    assertEquals("Captions use the source timing", subtitleSyncDescription(0L))
    assertEquals("Captions appear 0.5s earlier", subtitleSyncDescription(-500L))
    assertEquals("Captions appear 1.0s later", subtitleSyncDescription(1_000L))
  }

  @Test
  fun subtitleSync_stepsFinelyAndStopsAtTenSeconds() {
    assertEquals(-500L, adjustSubtitleSync(0L, -500L))
    assertEquals(100L, adjustSubtitleSync(0L, 100L))
    assertEquals(10_000L, adjustSubtitleSync(9_900L, 500L))
    assertEquals(-10_000L, adjustSubtitleSync(-9_900L, -500L))
  }

  @Test
  fun touchSeek_mapsAcrossTheTrackAndRefusesWhatItCannotMeasure() {
    assertEquals(300_000L, touchSeekPositionMs(x = 480f, trackWidthPx = 960, durationMs = 600_000L))
    assertEquals(0L, touchSeekPositionMs(x = 480f, trackWidthPx = 0, durationMs = 600_000L))
    assertEquals(0L, touchSeekPositionMs(x = 480f, trackWidthPx = 960, durationMs = 0L))
  }

  @Test
  fun back_closesOneLayerAtATime() {
    assertEquals(PlayerBackAction.CLOSE_SETTINGS, playerBackAction(settingsOpen = true, controlsVisible = true))
    assertEquals(PlayerBackAction.HIDE_CONTROLS, playerBackAction(settingsOpen = false, controlsVisible = true))
    assertEquals(PlayerBackAction.EXIT_PLAYER, playerBackAction(settingsOpen = false, controlsVisible = false))
  }

  @Test
  fun controlsLinger_longerOnATelevisionThanUnderAThumb() {
    assert(playerControllerTimeoutMs(isTelevision = true) > playerControllerTimeoutMs(isTelevision = false))
  }

  @Test
  fun automaticQuality_requiresStablePlaybackAndARealSafetyBuffer() {
    val lowPromotion = automaticQualityPromotion(AutomaticQualityPhase.LOW_STARTUP)!!
    assertEquals(AutomaticQualityPhase.BALANCED, lowPromotion.nextPhase)
    assertEquals(15_000L, lowPromotion.stablePlaybackMs)
    assertEquals(20_000L, lowPromotion.requiredBufferMs)

    val balancedPromotion = automaticQualityPromotion(AutomaticQualityPhase.BALANCED)!!
    assertEquals(AutomaticQualityPhase.UNRESTRICTED, balancedPromotion.nextPhase)
    assertEquals(30_000L, balancedPromotion.stablePlaybackMs)
    assertEquals(40_000L, balancedPromotion.requiredBufferMs)
    assertNull(automaticQualityPromotion(AutomaticQualityPhase.UNRESTRICTED))
  }

  @Test
  fun rebuffering_dropsAutomaticQualityButRespectsManualAndCompatibilityChoices() {
    assertEquals(
      AutomaticQualityPhase.LOW_STARTUP,
      automaticQualityPhaseAfterBuffering(
        hasStartedPlayback = true,
        automaticQuality = true,
        compatibilityMode = false,
        currentPhase = AutomaticQualityPhase.UNRESTRICTED,
      ),
    )
    assertEquals(
      AutomaticQualityPhase.UNRESTRICTED,
      automaticQualityPhaseAfterBuffering(
        hasStartedPlayback = true,
        automaticQuality = false,
        compatibilityMode = false,
        currentPhase = AutomaticQualityPhase.UNRESTRICTED,
      ),
    )
    assertEquals(
      AutomaticQualityPhase.BALANCED,
      automaticQualityPhaseAfterBuffering(
        hasStartedPlayback = true,
        automaticQuality = true,
        compatibilityMode = true,
        currentPhase = AutomaticQualityPhase.BALANCED,
      ),
    )
  }

  @Test
  fun hlsLoading_retriesTransientFailuresBeforeAbandoningAStream() {
    assertEquals(6, reliableHlsLoadErrorPolicy().getMinimumLoadableRetryCount(C.DATA_TYPE_MEDIA))
  }

  @Test
  fun dataSaver_isDistinctFromNormalAutoAndFixedQuality() {
    val dataSaver = VideoQualityOption(STABLE_QUALITY_LABEL, stable = true)

    assertEquals("Data Saver", dataSaver.label)
    assertTrue(dataSaver.isAuto)
    assertTrue(dataSaver.isStable)
    assertFalse(VideoQualityOption("Auto").isStable)
    assertFalse(VideoQualityOption("720p", width = 1280, height = 720).isAuto)
  }

  @Test
  fun dataSaver_choosesTheLowestManifestBitrateEvenWhenItIs480p() {
    val formats =
      listOf(
        Format.Builder().setWidth(1920).setHeight(1080).setAverageBitrate(5_000_000).build(),
        Format.Builder().setWidth(854).setHeight(480).setAverageBitrate(650_000).build(),
        Format.Builder().setWidth(1280).setHeight(720).setAverageBitrate(2_000_000).build(),
      )

    assertEquals(1, lowestDataVideoFormatIndex(formats))
    assertEquals(listOf(1, 2, 0), dataSaverVideoFormatOrder(formats))
  }

  @Test
  fun dataSaver_fallsBackToTheSmallestResolutionWhenBitratesAreMissing() {
    val formats =
      listOf(
        Format.Builder().setWidth(1280).setHeight(720).build(),
        Format.Builder().setWidth(640).setHeight(360).build(),
        Format.Builder().setWidth(854).setHeight(480).build(),
      )

    assertEquals(1, lowestDataVideoFormatIndex(formats))
    assertNull(lowestDataVideoFormatIndex(emptyList()))
  }

  @Test
  fun malformedLowestHlsTrack_isRecognizedForLocalQualityFallback() {
    val mappingFailure = RuntimeException("source failed", SampleQueueMappingException())

    assertTrue(isHlsTrackMappingFailure(mappingFailure))
    assertFalse(isHlsTrackMappingFailure(IllegalStateException("ordinary source failure")))
  }

  @Test
  fun expiredLiveWindow_isRecognizedForAnAutomaticJumpToLive() {
    val expiredWindow = RuntimeException("source failed", BehindLiveWindowException())

    assertTrue(isBehindLiveWindowFailure(expiredWindow))
    assertFalse(isBehindLiveWindowFailure(IllegalStateException("ordinary source failure")))
  }

  @Test
  fun prolongedStall_reloadsOnceThenRequestsAFreshStream() {
    assertEquals(ProlongedStallAction.RELOAD_CURRENT_STREAM, prolongedStallAction(0))
    assertEquals(ProlongedStallAction.REQUEST_FRESH_STREAM, prolongedStallAction(1))
    assertEquals(ProlongedStallAction.REQUEST_FRESH_STREAM, prolongedStallAction(2))
  }

  @Test
  fun streamFailover_isAvailableOnlyForCatalogTitlesAndIsBounded() {
    assertEquals(StreamFailureAction.RESOLVE_FRESH_STREAM, streamFailureAction(true, 0))
    assertEquals(StreamFailureAction.RESOLVE_FRESH_STREAM, streamFailureAction(true, 1))
    assertEquals(StreamFailureAction.SHOW_PLAYER_ERROR, streamFailureAction(true, 2))
    assertEquals(StreamFailureAction.SHOW_PLAYER_ERROR, streamFailureAction(false, 0))
  }

  @Test
  fun phone_restartsSoonerWhileTvKeepsItsDeepRebufferTarget() {
    val phone = playbackBufferProfile(isTelevision = false)
    val television = playbackBufferProfile(isTelevision = true)

    assertEquals(30_000, phone.minBufferMs)
    assertEquals(75_000, phone.maxBufferMs)
    assertEquals(2_500, phone.startBufferMs)
    assertEquals(5_000, phone.rebufferMs)
    assertEquals(5_000, television.startBufferMs)
    assertEquals(12_000, television.rebufferMs)
  }

  private class SampleQueueMappingException : RuntimeException()
}
