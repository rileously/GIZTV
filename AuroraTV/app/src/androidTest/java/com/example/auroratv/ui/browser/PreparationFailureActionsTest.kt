package com.example.auroratv.ui.browser

import androidx.activity.ComponentActivity
import androidx.compose.ui.input.key.Key
import androidx.compose.ui.test.ExperimentalTestApi
import androidx.compose.ui.test.assertIsFocused
import androidx.compose.ui.test.junit4.createAndroidComposeRule
import androidx.compose.ui.test.onNodeWithText
import androidx.compose.ui.test.performKeyInput
import androidx.compose.ui.test.pressKey
import androidx.test.platform.app.InstrumentationRegistry
import org.junit.Assert.assertEquals
import org.junit.Assert.assertFalse
import org.junit.Assert.assertTrue
import org.junit.Before
import org.junit.Rule
import org.junit.Test

@OptIn(ExperimentalTestApi::class)
class PreparationFailureActionsTest {

  @get:Rule val composeTestRule = createAndroidComposeRule<ComponentActivity>()

  @Before
  fun leaveTouchMode() {
    InstrumentationRegistry.getInstrumentation().apply {
      setInTouchMode(false)
      waitForIdleSync()
    }
  }

  @Test
  fun remoteMovesAcrossAndActivatesEveryFailureAction() {
    var selectedAction = ""
    composeTestRule.setContent {
      PreparationFailureActions(
        onRetry = { selectedAction = "retry" },
        onShowPage = { selectedAction = "page" },
        onCancel = { selectedAction = "back" },
      )
    }

    val retry = composeTestRule.onNodeWithText("Try again")
    val page = composeTestRule.onNodeWithText("Open the page")
    val back = composeTestRule.onNodeWithText("Back")

    retry.assertIsFocused().performKeyInput { pressKey(Key.DirectionCenter) }
    composeTestRule.runOnIdle { assertEquals("retry", selectedAction) }

    retry.performKeyInput { pressKey(Key.DirectionRight) }
    page.assertIsFocused().performKeyInput { pressKey(Key.DirectionCenter) }
    composeTestRule.runOnIdle { assertEquals("page", selectedAction) }

    page.performKeyInput { pressKey(Key.DirectionRight) }
    back.assertIsFocused().performKeyInput { pressKey(Key.DirectionCenter) }
    composeTestRule.runOnIdle { assertEquals("back", selectedAction) }

    back.performKeyInput { pressKey(Key.DirectionLeft) }
    page.assertIsFocused()
  }

  @Test
  fun streamFoundStageCannotShowFalseTimeout() {
    assertTrue(PreparationStage.OPENING.canTimeOut)
    assertTrue(PreparationStage.FINDING_STREAM.canTimeOut)
    assertFalse(PreparationStage.LOADING_SUBTITLES.canTimeOut)
  }
}
