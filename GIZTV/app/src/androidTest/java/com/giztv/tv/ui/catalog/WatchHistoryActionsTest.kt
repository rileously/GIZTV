package com.giztv.tv.ui.catalog

import androidx.activity.ComponentActivity
import androidx.compose.runtime.remember
import androidx.compose.ui.focus.FocusRequester
import androidx.compose.ui.test.assertIsFocused
import androidx.compose.ui.test.assertTextEquals
import androidx.compose.ui.test.junit4.createAndroidComposeRule
import androidx.compose.ui.test.onNodeWithContentDescription
import androidx.compose.ui.test.onNodeWithText
import androidx.compose.ui.test.performClick
import androidx.test.platform.app.InstrumentationRegistry
import org.junit.Assert.assertTrue
import org.junit.Before
import org.junit.Rule
import org.junit.Test

class WatchHistoryActionsTest {
  @get:Rule val composeTestRule = createAndroidComposeRule<ComponentActivity>()

  @Before
  fun useRemoteNavigation() {
    InstrumentationRegistry.getInstrumentation().apply {
      setInTouchMode(false)
      waitForIdleSync()
    }
  }

  @Test
  fun completedOnlyHistoryStillOffersTheClearAction() {
    var clearRequested = false
    composeTestRule.setContent {
      val firstCardFocusRequester = remember { FocusRequester() }
      ContinueWatchingSection(
        entries = emptyList(),
        onResume = {},
        onClearHistory = { clearRequested = true },
        firstCardFocusRequester = firstCardFocusRequester,
        up = FocusRequester.Default,
        down = FocusRequester.Default,
        hasGrid = false,
      )
    }

    composeTestRule.onNodeWithText("Watch history").assertTextEquals("Watch history")
    composeTestRule.onNodeWithContentDescription("Clear watch history").performClick()
    composeTestRule.runOnIdle { assertTrue(clearRequested) }
  }

  @Test
  fun confirmationStartsOnTheSafeAction() {
    composeTestRule.setContent {
      ClearWatchHistoryDialog(onDismiss = {}, onConfirm = {})
    }

    composeTestRule.onNodeWithText("Cancel").assertIsFocused()
  }

  @Test
  fun confirmationRunsTheClearAction() {
    var confirmed = false
    composeTestRule.setContent {
      ClearWatchHistoryDialog(onDismiss = {}, onConfirm = { confirmed = true })
    }

    composeTestRule.onNodeWithText("Clear history").performClick()
    composeTestRule.runOnIdle { assertTrue(confirmed) }
  }
}
