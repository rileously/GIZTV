package com.giztv.tv.ui.catalog

import androidx.activity.ComponentActivity
import androidx.compose.foundation.ScrollState
import androidx.compose.foundation.clickable
import androidx.compose.foundation.layout.Box
import androidx.compose.foundation.layout.Column
import androidx.compose.foundation.layout.Spacer
import androidx.compose.foundation.layout.height
import androidx.compose.foundation.layout.size
import androidx.compose.foundation.rememberScrollState
import androidx.compose.foundation.verticalScroll
import androidx.compose.runtime.getValue
import androidx.compose.runtime.mutableIntStateOf
import androidx.compose.runtime.remember
import androidx.compose.runtime.setValue
import androidx.compose.ui.Modifier
import androidx.compose.ui.focus.FocusRequester
import androidx.compose.ui.focus.focusRequester
import androidx.compose.ui.semantics.SemanticsActions
import androidx.compose.ui.semantics.contentDescription
import androidx.compose.ui.semantics.semantics
import androidx.compose.ui.test.assertIsFocused
import androidx.compose.ui.test.junit4.createAndroidComposeRule
import androidx.compose.ui.test.onNodeWithContentDescription
import androidx.compose.ui.test.performSemanticsAction
import androidx.compose.ui.unit.dp
import androidx.test.platform.app.InstrumentationRegistry
import org.junit.Assert.assertEquals
import org.junit.Assert.assertTrue
import org.junit.Before
import org.junit.Rule
import org.junit.Test

class MovieDetailFocusResetTest {
  @get:Rule val composeTestRule = createAndroidComposeRule<ComponentActivity>()

  @Before
  fun useRemoteNavigation() {
    InstrumentationRegistry.getInstrumentation().apply {
      setInTouchMode(false)
      waitForIdleSync()
    }
  }

  @Test
  fun openingARecommendationReturnsToTopAndFocusesPlay() {
    var movieId by mutableIntStateOf(1)
    lateinit var detailScrollState: ScrollState

    composeTestRule.setContent {
      detailScrollState = rememberScrollState()
      val playFocusRequester = remember(movieId) { FocusRequester() }
      MovieDetailFocusResetEffect(
        movieId = movieId,
        scrollState = detailScrollState,
        playFocusRequester = playFocusRequester,
        retryDelayMs = 0,
      )
      Column(Modifier.height(160.dp).verticalScroll(detailScrollState)) {
        Box(
          Modifier.size(100.dp).focusRequester(playFocusRequester).clickable {}
            .semantics { contentDescription = "Play current movie" }
        )
        Spacer(Modifier.height(500.dp))
        Box(
          Modifier.size(100.dp).clickable {}
            .semantics { contentDescription = "Open recommended movie" }
        )
      }
    }

    val play = composeTestRule.onNodeWithContentDescription("Play current movie")
    val recommendation = composeTestRule.onNodeWithContentDescription("Open recommended movie")
    recommendation.performSemanticsAction(SemanticsActions.RequestFocus) { it() }
    recommendation.assertIsFocused()
    composeTestRule.runOnIdle { assertTrue(detailScrollState.value > 0) }

    composeTestRule.runOnIdle { movieId = 2 }
    composeTestRule.waitForIdle()

    play.assertIsFocused()
    composeTestRule.runOnIdle { assertEquals(0, detailScrollState.value) }
  }
}
