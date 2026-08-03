package com.example.auroratv

import androidx.media3.common.Player
import com.example.auroratv.ui.player.PlayerBackAction
import com.example.auroratv.ui.player.adjustSubtitleSync
import com.example.auroratv.ui.player.playerBackAction
import com.example.auroratv.ui.player.playerControllerTimeoutMs
import com.example.auroratv.ui.player.resumablePlaybackPosition
import com.example.auroratv.ui.player.subtitleSyncDescription
import com.example.auroratv.ui.player.subtitleSyncLabel
import com.example.auroratv.ui.player.touchSeekPositionMs
import org.junit.Assert.assertEquals
import org.junit.Assert.assertNull
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
}
