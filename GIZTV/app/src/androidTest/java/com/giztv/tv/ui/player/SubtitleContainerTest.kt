package com.giztv.tv.ui.player

import android.view.ViewGroup
import androidx.activity.ComponentActivity
import androidx.annotation.OptIn
import androidx.compose.ui.test.junit4.createAndroidComposeRule
import androidx.media3.common.util.UnstableApi
import androidx.media3.ui.PlayerView
import org.junit.Assert.assertSame
import org.junit.Rule
import org.junit.Test

/**
 * Where captions are parked, which is what decides whether a zoomed picture crops them away.
 *
 * The player lays its subtitles inside the content frame beside the video surface. A mode that
 * covers rather than fits measures that frame past the player's bounds, so captions placed against
 * it land below the screen — which is what short dramas do on a phone, where they open zoomed.
 */
@OptIn(UnstableApi::class)
class SubtitleContainerTest {

  @get:Rule val composeTestRule = createAndroidComposeRule<ComponentActivity>()

  private fun playerView(): PlayerView = PlayerView(composeTestRule.activity)

  @Test
  fun captionsMoveToTheOverlayWhileThePictureIsCropped() {
    composeTestRule.runOnUiThread {
      val view = playerView()
      val subtitles = requireNotNull(view.subtitleView)
      val contentFrame = requireNotNull(view.videoSurfaceView?.parent as? ViewGroup)
      val overlay = requireNotNull(view.overlayFrameLayout)

      // Where the player puts them, and where a letterboxed picture wants them.
      assertSame(contentFrame, subtitles.parent)

      keepSubtitlesOnScreen(view, VideoResizeOption.ZOOM)
      assertSame(overlay, subtitles.parent)

      // Back home once the picture fits again, so captions travel with a letterboxed picture.
      keepSubtitlesOnScreen(view, VideoResizeOption.FIT)
      assertSame(contentFrame, subtitles.parent)
    }
  }

  @Test
  fun everyCroppingModeIsCoveredAndNoOtherIs() {
    composeTestRule.runOnUiThread {
      val view = playerView()
      val subtitles = requireNotNull(view.subtitleView)
      val contentFrame = requireNotNull(view.videoSurfaceView?.parent as? ViewGroup)
      val overlay = requireNotNull(view.overlayFrameLayout)

      VideoResizeOption.entries.forEach { option ->
        keepSubtitlesOnScreen(view, option)
        val expected = if (option.cropsPicture) overlay else contentFrame
        assertSame("${option.label} parked captions in the wrong place", expected, subtitles.parent)
      }
    }
  }

  @Test
  fun applyingTheSameModeTwiceLeavesTheViewAlone() {
    composeTestRule.runOnUiThread {
      val view = playerView()
      val subtitles = requireNotNull(view.subtitleView)

      keepSubtitlesOnScreen(view, VideoResizeOption.ZOOM)
      val parked = subtitles.parent
      // The update block runs on every recomposition, so a no-op has to stay a no-op rather than
      // detaching and reattaching the view under the captions being drawn.
      keepSubtitlesOnScreen(view, VideoResizeOption.ZOOM)
      assertSame(parked, subtitles.parent)
    }
  }
}
