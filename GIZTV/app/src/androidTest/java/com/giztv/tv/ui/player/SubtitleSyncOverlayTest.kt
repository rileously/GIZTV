package com.giztv.tv.ui.player

import androidx.activity.ComponentActivity
import androidx.compose.ui.test.junit4.createAndroidComposeRule
import androidx.compose.ui.test.onNodeWithContentDescription
import androidx.compose.ui.test.performClick
import androidx.media3.exoplayer.ExoPlayer
import androidx.test.ext.junit.runners.AndroidJUnit4
import org.junit.Assert.assertEquals
import org.junit.Assert.assertTrue
import org.junit.Rule
import org.junit.Test
import org.junit.runner.RunWith

@RunWith(AndroidJUnit4::class)
class SubtitleSyncOverlayTest {
  @get:Rule val composeTestRule = createAndroidComposeRule<ComponentActivity>()

  @Test
  fun openingAndNudgingTheOverlayNeverPausesPlayback() {
    lateinit var player: ExoPlayer
    var appliedOffsetMs: Long? = null
    composeTestRule.runOnIdle {
      player = ExoPlayer.Builder(composeTestRule.activity).build()
      player.playWhenReady = true
    }

    try {
      composeTestRule.setContent {
        SubtitleSyncMiniOverlay(
          player = player,
          request =
            HlsStreamRequest(
              url = "https://video.example.com/stream.m3u8",
              headers = emptyMap(),
            ),
          selectedSubtitleLabel = "Auto English",
          offsetMs = 0L,
          isCasting = false,
          onOffsetSelected = { appliedOffsetMs = it },
          onClose = {},
        )
      }

      composeTestRule.runOnIdle { assertTrue(player.playWhenReady) }
      composeTestRule
        .onNodeWithContentDescription(
          "Subtitles earlier. Captions use the source timing. " +
            "100 milliseconds per press; hold for faster adjustment."
        )
        .performClick()

      composeTestRule.runOnIdle {
        assertEquals(-100L, appliedOffsetMs)
        assertTrue(player.playWhenReady)
      }
    } finally {
      composeTestRule.runOnIdle { player.release() }
    }
  }
}
