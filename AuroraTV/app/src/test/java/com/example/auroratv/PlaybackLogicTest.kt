package com.example.auroratv

import androidx.media3.common.C
import androidx.media3.common.Format
import androidx.media3.common.MimeTypes
import androidx.media3.common.Player
import androidx.media3.exoplayer.source.BehindLiveWindowException
import com.example.auroratv.ui.StreamFailureAction
import com.example.auroratv.ui.catalog.STREAM_PROVIDER_COUNT
import com.example.auroratv.ui.iptvPlaybackSourceAt
import com.example.auroratv.ui.nextIptvPlaybackSource
import com.example.auroratv.ui.streamFailureAction
import com.example.auroratv.ui.player.AutomaticQualityPhase
import com.example.auroratv.ui.player.HlsStreamRequest
import com.example.auroratv.ui.player.PlayerBackAction
import com.example.auroratv.ui.player.PlayerSeekSide
import com.example.auroratv.ui.player.PlayerSwipeControl
import com.example.auroratv.ui.player.ProlongedStallAction
import com.example.auroratv.ui.player.STABLE_QUALITY_LABEL
import com.example.auroratv.ui.player.VideoQualityOption
import com.example.auroratv.ui.player.adjustSubtitleSync
import com.example.auroratv.ui.player.automaticQualityPhaseAfterBuffering
import com.example.auroratv.ui.player.automaticQualityPhaseAfterStall
import com.example.auroratv.ui.player.automaticQualityPromotion
import com.example.auroratv.ui.player.MINI_PLAYER_DISMISS_FLING_VELOCITY_PX_PER_SEC
import com.example.auroratv.ui.player.MINI_PLAYER_RUBBER_BAND_FACTOR
import com.example.auroratv.ui.player.canMinimizeToInAppPlayer
import com.example.auroratv.ui.player.miniPlayerDismissProgress
import com.example.auroratv.ui.player.miniPlayerDragVisualOffset
import com.example.auroratv.ui.player.shouldDismissMiniPlayer
import com.example.auroratv.ui.player.initialAutomaticQualityPhase
import com.example.auroratv.ui.player.isBandwidthStall
import com.example.auroratv.ui.player.dataSaverVideoFormatOrder
import com.example.auroratv.ui.player.isHlsTrackMappingFailure
import com.example.auroratv.ui.player.isBehindLiveWindowFailure
import com.example.auroratv.ui.player.lowestDataVideoFormatIndex
import com.example.auroratv.ui.player.playerBackAction
import com.example.auroratv.ui.player.playerControllerTimeoutMs
import com.example.auroratv.ui.player.playerSeekSide
import com.example.auroratv.ui.player.playerSeekTarget
import com.example.auroratv.ui.player.SubtitlePositionOption
import com.example.auroratv.ui.player.isSubtitleDragTouch
import com.example.auroratv.ui.player.nearestSubtitlePosition
import com.example.auroratv.ui.player.playerSwipeControl
import com.example.auroratv.ui.player.playerSwipeLevel
import com.example.auroratv.ui.player.subtitleCueBaselineY
import com.example.auroratv.ui.player.subtitlePaddingAfterDrag
import com.example.auroratv.ui.player.PROGRESSIVE_DEFAULT_MAX_HEIGHT
import com.example.auroratv.ui.player.isProgressiveStreamRequest
import com.example.auroratv.ui.player.playbackBufferProfile
import com.example.auroratv.ui.player.preferProgressivePlaybackUrl
import com.example.auroratv.ui.player.progressiveHeightLabelToPx
import com.example.auroratv.ui.player.prolongedStallAction
import com.example.auroratv.ui.player.shouldComposeInAppPlayerSession
import com.example.auroratv.ui.player.stallTimeoutMs
import com.example.auroratv.ui.player.reliableHlsLoadErrorPolicy
import com.example.auroratv.ui.player.resumablePlaybackPosition
import com.example.auroratv.ui.player.subtitleSyncDescription
import com.example.auroratv.ui.player.subtitleSyncLabel
import com.example.auroratv.ui.player.touchSeekPositionMs
import com.example.auroratv.ui.player.mediaVolumeIndex
import com.example.auroratv.ui.player.formatPlaybackRating
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
  fun playbackRating_showsOneDecimalWhenPresent() {
    assertEquals("★ 8.2", formatPlaybackRating(8.24))
    assertEquals("★ 7.0", formatPlaybackRating(7.0))
    assertNull(formatPlaybackRating(null))
    assertNull(formatPlaybackRating(0.0))
    assertNull(formatPlaybackRating(-1.0))
  }

  @Test
  fun subtitleSync_stepsFinelyAndStopsAtOneMinute() {
    assertEquals(-500L, adjustSubtitleSync(0L, -500L))
    assertEquals(100L, adjustSubtitleSync(0L, 100L))
    assertEquals(60_000L, adjustSubtitleSync(59_900L, 500L))
    assertEquals(-60_000L, adjustSubtitleSync(-59_900L, -500L))
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
    // A healthy stream shrinks into the in-app mini player instead of tearing playback down.
    assertEquals(
      PlayerBackAction.MINIMIZE_PLAYER,
      playerBackAction(settingsOpen = false, controlsVisible = false, canMinimize = true),
    )
  }

  @Test
  fun inAppMiniPlayer_keepsTheSessionAliveOnlyWhileSomethingIsPlaying() {
    assertTrue(
      shouldComposeInAppPlayerSession(
        hasStreamRequest = true,
        fullPlayerVisible = true,
        miniPlayerActive = false,
      )
    )
    assertTrue(
      shouldComposeInAppPlayerSession(
        hasStreamRequest = true,
        fullPlayerVisible = false,
        miniPlayerActive = true,
      )
    )
    assertFalse(
      shouldComposeInAppPlayerSession(
        hasStreamRequest = false,
        fullPlayerVisible = false,
        miniPlayerActive = true,
      )
    )
    assertFalse(
      shouldComposeInAppPlayerSession(
        hasStreamRequest = true,
        fullPlayerVisible = false,
        miniPlayerActive = false,
      )
    )
  }

  @Test
  fun inAppMiniPlayer_dragDismissesPastThresholdOrWithADownwardFling() {
    val threshold = 200f
    assertTrue(
      shouldDismissMiniPlayer(
        dragOffsetPx = threshold,
        velocityYPxPerSec = 0f,
        dismissThresholdPx = threshold,
      )
    )
    assertTrue(
      shouldDismissMiniPlayer(
        dragOffsetPx = threshold / 2f,
        velocityYPxPerSec = MINI_PLAYER_DISMISS_FLING_VELOCITY_PX_PER_SEC,
        dismissThresholdPx = threshold,
      )
    )
    assertFalse(
      shouldDismissMiniPlayer(
        dragOffsetPx = threshold / 2f,
        velocityYPxPerSec = 0f,
        dismissThresholdPx = threshold,
      )
    )
    assertFalse(
      shouldDismissMiniPlayer(
        dragOffsetPx = -40f,
        velocityYPxPerSec = -2000f,
        dismissThresholdPx = threshold,
      )
    )
  }

  @Test
  fun inAppMiniPlayer_rubberBandsUpwardDragAndTracksDismissProgress() {
    assertEquals(0f, miniPlayerDragVisualOffset(0f))
    assertEquals(120f, miniPlayerDragVisualOffset(120f))
    assertEquals(-40f * MINI_PLAYER_RUBBER_BAND_FACTOR, miniPlayerDragVisualOffset(-40f))
    assertEquals(0f, miniPlayerDismissProgress(-20f, 100f))
    assertEquals(0.5f, miniPlayerDismissProgress(50f, 100f))
    assertEquals(1.5f, miniPlayerDismissProgress(150f, 100f))
  }

  @Test
  fun inAppMiniPlayer_isOfferedOnlyForAHealthyLocalStream() {
    assertTrue(
      canMinimizeToInAppPlayer(
        minimized = false,
        isCasting = false,
        hasError = false,
        playbackFinished = false,
        isTelevision = false,
      )
    )
    assertFalse(
      canMinimizeToInAppPlayer(
        minimized = true,
        isCasting = false,
        hasError = false,
        playbackFinished = false,
        isTelevision = false,
      )
    )
    assertFalse(
      canMinimizeToInAppPlayer(
        minimized = false,
        isCasting = true,
        hasError = false,
        playbackFinished = false,
        isTelevision = false,
      )
    )
    assertFalse(
      canMinimizeToInAppPlayer(
        minimized = false,
        isCasting = false,
        hasError = true,
        playbackFinished = false,
        isTelevision = false,
      )
    )
    assertFalse(
      canMinimizeToInAppPlayer(
        minimized = false,
        isCasting = false,
        hasError = false,
        playbackFinished = true,
        isTelevision = false,
      )
    )
    // Leanback never shrinks into the in-app mini player.
    assertFalse(
      canMinimizeToInAppPlayer(
        minimized = false,
        isCasting = false,
        hasError = false,
        playbackFinished = false,
        isTelevision = true,
      )
    )
  }

  @Test
  fun controlsLinger_longerOnATelevisionThanUnderAThumb() {
    assert(playerControllerTimeoutMs(isTelevision = true) > playerControllerTimeoutMs(isTelevision = false))
  }

  @Test
  fun phonePlayerSwipe_usesLeftForBrightnessAndRightForVolume() {
    assertEquals(PlayerSwipeControl.BRIGHTNESS, playerSwipeControl(startX = 200f, widthPx = 1_000))
    assertEquals(PlayerSwipeControl.VOLUME, playerSwipeControl(startX = 800f, widthPx = 1_000))
    assertEquals(PlayerSwipeControl.VOLUME, playerSwipeControl(startX = 500f, widthPx = 1_000))
  }

  @Test
  fun phonePlayerSwipe_upRaisesTheLevelAndClampsBothEnds() {
    assertEquals(.75f, playerSwipeLevel(.5f, totalVerticalDragPx = -200f, heightPx = 1_000), .001f)
    assertEquals(.25f, playerSwipeLevel(.5f, totalVerticalDragPx = 200f, heightPx = 1_000), .001f)
    assertEquals(1f, playerSwipeLevel(.9f, totalVerticalDragPx = -500f, heightPx = 1_000), .001f)
    assertEquals(0f, playerSwipeLevel(.1f, totalVerticalDragPx = 500f, heightPx = 1_000), .001f)
    assertEquals(8, mediaVolumeIndex(level = .5f, maximumVolume = 15))
  }

  @Test
  fun phoneSubtitleDrag_onlyStartsNearTheCueBandWhenSubtitlesAreOn() {
    val height = 1_000
    val bottom = SubtitlePositionOption.BOTTOM.bottomPadding
    val baseline = subtitleCueBaselineY(height, bottom)
    assertTrue(isSubtitleDragTouch(baseline, height, bottom, subtitlesEnabled = true))
    assertTrue(isSubtitleDragTouch(baseline - 40f, height, bottom, subtitlesEnabled = true))
    assertFalse(isSubtitleDragTouch(baseline, height, bottom, subtitlesEnabled = false))
    assertFalse(isSubtitleDragTouch(height / 2f, height, bottom, subtitlesEnabled = true))
  }

  @Test
  fun phoneSubtitleDrag_upRaisesPaddingAndSnapsToNearestOption() {
    assertEquals(
      .08f + .11f,
      subtitlePaddingAfterDrag(startPadding = .08f, totalVerticalDragPx = -200f, heightPx = 1_000),
      .001f,
    )
    assertEquals(
      SubtitlePositionOption.BOTTOM.bottomPadding,
      subtitlePaddingAfterDrag(startPadding = .08f, totalVerticalDragPx = 400f, heightPx = 1_000),
      .001f,
    )
    assertEquals(SubtitlePositionOption.BOTTOM, nearestSubtitlePosition(.10f))
    assertEquals(SubtitlePositionOption.RAISED, nearestSubtitlePosition(.17f))
    assertEquals(SubtitlePositionOption.HIGH, nearestSubtitlePosition(.28f))
  }

  @Test
  fun phonePlayerDoubleTap_seeksFromTheEdgesAndLeavesTheMiddleAlone() {
    assertEquals(PlayerSeekSide.BACKWARD, playerSeekSide(tapX = 100f, widthPx = 1_000))
    assertEquals(PlayerSeekSide.FORWARD, playerSeekSide(tapX = 900f, widthPx = 1_000))
    assertNull(playerSeekSide(tapX = 500f, widthPx = 1_000))
    assertNull(playerSeekSide(tapX = 100f, widthPx = 0))
  }

  @Test
  fun phonePlayerDoubleTap_stopsAtBothEndsOfTheMedia() {
    assertEquals(70_000L, playerSeekTarget(positionMs = 60_000L, deltaMs = 10_000L, durationMs = 600_000L))
    assertEquals(0L, playerSeekTarget(positionMs = 4_000L, deltaMs = -10_000L, durationMs = 600_000L))
    assertEquals(600_000L, playerSeekTarget(positionMs = 595_000L, deltaMs = 10_000L, durationMs = 600_000L))
    // A live stream reports no duration, so there is no far end to hold the seek back.
    assertEquals(70_000L, playerSeekTarget(positionMs = 60_000L, deltaMs = 10_000L, durationMs = 0L))
  }

  @Test
  fun automaticQuality_requiresStablePlaybackAndARealSafetyBuffer() {
    val lowPromotion = automaticQualityPromotion(AutomaticQualityPhase.LOW_STARTUP)!!
    assertEquals(AutomaticQualityPhase.BALANCED, lowPromotion.nextPhase)
    assertEquals(8_000L, lowPromotion.stablePlaybackMs)
    assertEquals(10_000L, lowPromotion.requiredBufferMs)

    val balancedPromotion = automaticQualityPromotion(AutomaticQualityPhase.BALANCED)!!
    assertEquals(AutomaticQualityPhase.UNRESTRICTED, balancedPromotion.nextPhase)
    assertEquals(12_000L, balancedPromotion.stablePlaybackMs)
    assertEquals(20_000L, balancedPromotion.requiredBufferMs)
    assertNull(automaticQualityPromotion(AutomaticQualityPhase.UNRESTRICTED))
  }

  @Test
  fun automaticQuality_opensLowSoTheFirstSegmentIsCheap() {
    assertEquals(AutomaticQualityPhase.LOW_STARTUP, initialAutomaticQualityPhase())
  }

  @Test
  fun buffering_countsAsASlowdownOnlyWhenNothingElseExplainsIt() {
    fun stall(seekInProgress: Boolean = false, qualityChangeSettling: Boolean = false) =
      isBandwidthStall(
        hasStartedPlayback = true,
        automaticQuality = true,
        compatibilityMode = false,
        seekInProgress = seekInProgress,
        qualityChangeSettling = qualityChangeSettling,
      )

    assertTrue(stall())
    // A jump empties the buffer wherever it lands; refilling it says nothing about the connection.
    assertFalse(stall(seekInProgress = true))
    // Neither does fetching the first segment of a rendition the ramp itself just unlocked.
    assertFalse(stall(qualityChangeSettling = true))
    // Buffering before the first frame is the startup buffer filling, not a stall.
    assertFalse(
      isBandwidthStall(
        hasStartedPlayback = false,
        automaticQuality = true,
        compatibilityMode = false,
        seekInProgress = false,
        qualityChangeSettling = false,
      )
    )
  }

  @Test
  fun rebuffering_stepsDownOnceRatherThanFallingToTheFloor() {
    assertEquals(
      AutomaticQualityPhase.BALANCED,
      automaticQualityPhaseAfterStall(AutomaticQualityPhase.UNRESTRICTED),
    )
    assertEquals(
      AutomaticQualityPhase.LOW_STARTUP,
      automaticQualityPhaseAfterStall(AutomaticQualityPhase.BALANCED),
    )
    assertEquals(
      AutomaticQualityPhase.LOW_STARTUP,
      automaticQualityPhaseAfterStall(AutomaticQualityPhase.LOW_STARTUP),
    )
  }

  @Test
  fun rebuffering_dropsAutomaticQualityButRespectsManualAndCompatibilityChoices() {
    assertEquals(
      AutomaticQualityPhase.BALANCED,
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
    // A double tap used to cost the viewer the quality for the rest of the scene.
    assertEquals(
      AutomaticQualityPhase.UNRESTRICTED,
      automaticQualityPhaseAfterBuffering(
        hasStartedPlayback = true,
        automaticQuality = true,
        compatibilityMode = false,
        currentPhase = AutomaticQualityPhase.UNRESTRICTED,
        seekInProgress = true,
      ),
    )
    // And the ramp used to read its own rendition switch as a reason to undo it.
    assertEquals(
      AutomaticQualityPhase.BALANCED,
      automaticQualityPhaseAfterBuffering(
        hasStartedPlayback = true,
        automaticQuality = true,
        compatibilityMode = false,
        currentPhase = AutomaticQualityPhase.BALANCED,
        qualityChangeSettling = true,
      ),
    )
  }

  @Test
  fun hlsLoading_retriesTransientFailuresBeforeAbandoningAStream() {
    assertEquals(6, reliableHlsLoadErrorPolicy().getMinimumLoadableRetryCount(C.DATA_TYPE_MEDIA))
    assertEquals(2, reliableHlsLoadErrorPolicy(2).getMinimumLoadableRetryCount(C.DATA_TYPE_MEDIA))
  }

  @Test
  fun iptvFailure_advancesThroughBackupsAndStopsAfterTheLastSource() {
    val sources =
      listOf(
        HlsStreamRequest("https://primary.example/live.m3u8", emptyMap()),
        HlsStreamRequest("https://backup.example/live.m3u8", emptyMap()),
      )

    assertEquals(1, nextIptvPlaybackSource(sources, currentIndex = 0)?.first)
    assertEquals(
      "https://backup.example/live.m3u8",
      nextIptvPlaybackSource(sources, currentIndex = 0)?.second?.url,
    )
    assertNull(nextIptvPlaybackSource(sources, currentIndex = 1))
  }

  @Test
  fun iptvServerPick_jumpsToASpecificBackup() {
    val sources =
      listOf(
        HlsStreamRequest("https://primary.example/live.m3u8", emptyMap()),
        HlsStreamRequest("https://backup.example/live.m3u8", emptyMap()),
        HlsStreamRequest("https://spare.example/live.m3u8", emptyMap()),
      )

    assertEquals(
      "https://spare.example/live.m3u8",
      iptvPlaybackSourceAt(sources, index = 2)?.second?.url,
    )
    assertNull(iptvPlaybackSourceAt(sources, index = 3))
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
  fun aStreamThatHasShownNothingAtAll_isGivenUpOnSooner() {
    // A stream that has been playing has proved it exists, so a stall is worth waiting out.
    assertEquals(45_000L, stallTimeoutMs(hasStartedPlayback = true))
    // One that has never produced a frame is more likely dead than slow, and every second spent
    // proving it is a second staring at a spinner before the next server is even asked.
    assertEquals(25_000L, stallTimeoutMs(hasStartedPlayback = false))
    assertTrue(stallTimeoutMs(false) < stallTimeoutMs(true))
  }

  @Test
  fun prolongedStall_reloadsOnceThenRequestsAFreshStream() {
    assertEquals(ProlongedStallAction.RELOAD_CURRENT_STREAM, prolongedStallAction(0))
    assertEquals(ProlongedStallAction.REQUEST_FRESH_STREAM, prolongedStallAction(1))
    assertEquals(ProlongedStallAction.REQUEST_FRESH_STREAM, prolongedStallAction(2))
  }

  @Test
  fun streamFailover_isAvailableOnlyForCatalogTitlesAndIsBounded() {
    // One attempt per provider after the first, so every site is asked before the viewer is told no.
    repeat(STREAM_PROVIDER_COUNT - 1) { completed ->
      assertEquals(
        "failover $completed",
        StreamFailureAction.RESOLVE_FRESH_STREAM,
        streamFailureAction(true, completed),
      )
    }
    assertEquals(StreamFailureAction.SHOW_PLAYER_ERROR, streamFailureAction(true, STREAM_PROVIDER_COUNT - 1))
    // A stream found by plain browsing has no catalog title to ask a second provider about.
    assertEquals(StreamFailureAction.SHOW_PLAYER_ERROR, streamFailureAction(false, 0))
  }

  @Test
  fun phoneAndTv_useFastDesktopStyleStartAndSeekTargets() {
    val phone = playbackBufferProfile(isTelevision = false)
    val television = playbackBufferProfile(isTelevision = true)

    assertEquals(30_000, phone.minBufferMs)
    assertEquals(75_000, phone.maxBufferMs)
    assertEquals(1_500, phone.startBufferMs)
    assertEquals(3_000, phone.rebufferMs)
    assertEquals(2_000, television.startBufferMs)
    assertEquals(4_000, television.rebufferMs)
  }

  @Test
  fun progressive_usesAShorterBufferCushionThanHls() {
    val progressivePhone = playbackBufferProfile(isTelevision = false, progressive = true)
    val progressiveTv = playbackBufferProfile(isTelevision = true, progressive = true)
    val hlsPhone = playbackBufferProfile(isTelevision = false, progressive = false)

    assertEquals(20_000, progressivePhone.minBufferMs)
    assertEquals(60_000, progressivePhone.maxBufferMs)
    assertEquals(1_500, progressivePhone.startBufferMs)
    assertEquals(3_000, progressivePhone.rebufferMs)
    assertEquals(2_000, progressiveTv.startBufferMs)
    assertEquals(4_000, progressiveTv.rebufferMs)
    // HLS keeps the deeper cushion; progressive must not inherit it.
    assertTrue(progressivePhone.minBufferMs < hlsPhone.minBufferMs)
    assertTrue(progressivePhone.maxBufferMs < hlsPhone.maxBufferMs)
  }

  @Test
  fun aFastLink_startsAProgressiveFileWithoutFillingACushionItDoesNotNeed() {
    val fastTv = playbackBufferProfile(isTelevision = true, progressive = true, fastLink = true)
    val slowTv = playbackBufferProfile(isTelevision = true, progressive = true, fastLink = false)

    assertEquals(1_000, fastTv.startBufferMs)
    assertTrue(fastTv.startBufferMs < slowTv.startBufferMs)
    // Only the opening moves. The safety net after a stall is what a stall actually needs, and a
    // link that was fast a moment ago is not necessarily fast now.
    assertEquals(slowTv.rebufferMs, fastTv.rebufferMs)
    assertEquals(slowTv.minBufferMs, fastTv.minBufferMs)
    assertEquals(slowTv.maxBufferMs, fastTv.maxBufferMs)
    // A playlist has a ladder to step down instead, so its opening is left alone.
    assertEquals(
      playbackBufferProfile(isTelevision = true).startBufferMs,
      playbackBufferProfile(isTelevision = true, fastLink = true).startBufferMs,
    )
  }

  @Test
  fun progressiveRequests_areRecognisedFromMimeTypeAndFilename() {
    assertTrue(
      isProgressiveStreamRequest(
        HlsStreamRequest(
          url = "https://moon.ironwallnet.net/mp4/TOKEN/1080p.mp4",
          headers = emptyMap(),
          mimeType = null,
        ),
      ),
    )
    // Cached replays used to omit mimeType and inherit APPLICATION_M3U8; the .mp4 path still wins.
    assertTrue(
      isProgressiveStreamRequest(
        HlsStreamRequest(
          url = "https://moon.ironwallnet.net/mp4/TOKEN/1080p.mp4",
          headers = emptyMap(),
          mimeType = MimeTypes.APPLICATION_M3U8,
        ),
      ),
    )
    assertTrue(
      isProgressiveStreamRequest(
        HlsStreamRequest(
          url = "https://cdn.example/video.bin",
          headers = emptyMap(),
          mimeType = "video/mp4",
        ),
      ),
    )
    assertFalse(
      isProgressiveStreamRequest(
        HlsStreamRequest(
          url = "https://cdn.example/index.m3u8",
          headers = emptyMap(),
          mimeType = MimeTypes.APPLICATION_M3U8,
        ),
      ),
    )
  }

  @Test
  fun progressiveUrls_capOversizedFilenameQualityWithoutTouchingNormalOnes() {
    assertEquals(2160, progressiveHeightLabelToPx("2160p"))
    assertEquals(2160, progressiveHeightLabelToPx("4K"))
    assertEquals(PROGRESSIVE_DEFAULT_MAX_HEIGHT, 1080)

    assertEquals(
      "https://moon.ironwallnet.net/mp4/TOKEN/1080p.mp4",
      preferProgressivePlaybackUrl(
        "https://moon.ironwallnet.net/mp4/TOKEN/2160p.mp4",
      ),
    )
    assertEquals(
      "https://cdn.example/film/1080p.mp4?token=abc",
      preferProgressivePlaybackUrl("https://cdn.example/film/4k.mp4?token=abc"),
    )
    assertEquals(
      "https://cdn.example/film/1080p.mkv",
      preferProgressivePlaybackUrl("https://cdn.example/film/1440p.mkv"),
    )
    // Already at or below the cap: leave alone, including query strings.
    assertEquals(
      "https://moon.ironwallnet.net/mp4/TOKEN/1080p.mp4?e=1",
      preferProgressivePlaybackUrl(
        "https://moon.ironwallnet.net/mp4/TOKEN/1080p.mp4?e=1",
      ),
    )
    assertEquals(
      "https://cdn.example/film/720p.mp4",
      preferProgressivePlaybackUrl("https://cdn.example/film/720p.mp4"),
    )
    // Compatibility mode may ask for 720p when the path is clearly taller.
    assertEquals(
      "https://cdn.example/film/720p.mp4",
      preferProgressivePlaybackUrl("https://cdn.example/film/2160p.mp4", maxHeight = 720),
    )
    // Playlists and unlabeled files are not rewritten.
    assertEquals(
      "https://cdn.example/index.m3u8",
      preferProgressivePlaybackUrl("https://cdn.example/index.m3u8"),
    )
    assertEquals(
      "https://cdn.example/mp4/TOKEN/movie.mp4",
      preferProgressivePlaybackUrl("https://cdn.example/mp4/TOKEN/movie.mp4"),
    )
  }

  private class SampleQueueMappingException : RuntimeException()
}
