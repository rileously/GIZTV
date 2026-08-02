package com.example.auroratv.ui.main

import androidx.activity.ComponentActivity
import androidx.compose.ui.test.assertCountEquals
import androidx.compose.ui.test.assertIsFocused
import androidx.compose.ui.test.ExperimentalTestApi
import androidx.compose.ui.input.key.Key
import androidx.compose.ui.semantics.SemanticsActions
import androidx.compose.ui.test.junit4.createAndroidComposeRule
import androidx.compose.ui.test.onAllNodesWithText
import androidx.compose.ui.test.onNodeWithContentDescription
import androidx.compose.ui.test.onNodeWithText
import androidx.compose.ui.test.performClick
import androidx.compose.ui.test.performKeyInput
import androidx.compose.ui.test.performSemanticsAction
import androidx.compose.ui.test.performTextReplacement
import androidx.compose.ui.test.pressKey
import androidx.test.platform.app.InstrumentationRegistry
import org.junit.Assert.assertEquals
import org.junit.Assert.assertNull
import org.junit.Before
import org.junit.Rule
import org.junit.Test

/** UI tests for [com.example.auroratv.ui.main.MainScreen]. */
@OptIn(ExperimentalTestApi::class)
class MainScreenTest {

  @get:Rule val composeTestRule = createAndroidComposeRule<ComponentActivity>()
  private var openedUrl: String? = null

  @Before
  fun setup() {
    // Sibling tests click, which puts the device in touch mode; focus is never granted there, so
    // the D-pad test would fail purely on the order it happened to run in.
    InstrumentationRegistry.getInstrumentation().apply {
      setInTouchMode(false)
      waitForIdleSync()
    }
    openedUrl = null
    composeTestRule.setContent { AuroraTvApp(onOpenBrowser = { openedUrl = it }) }
  }

  @Test
  fun websiteLauncher_replacesFictionalCatalog() {
    composeTestRule.onNodeWithText("GIZTV").assertExists()
    composeTestRule.onNodeWithText("Browse the web with GIZTV").assertExists()
    composeTestRule.onNodeWithText("Visit Website").assertExists()
    composeTestRule.onNodeWithText("skyflix.to").assertExists()
    composeTestRule.onAllNodesWithText("Beyond the Horizon").assertCountEquals(0)
  }

  @Test
  fun enteredDomain_isNormalizedAndOpened() {
    composeTestRule.onNodeWithContentDescription("Website URL").performTextReplacement("example.com")
    composeTestRule.onNodeWithText("Visit Website").performClick()

    composeTestRule.runOnIdle { assertEquals("https://example.com", openedUrl) }
  }

  @Test
  fun skyflixQuickButton_opensProtectedBrowser() {
    composeTestRule.onNodeWithContentDescription("Open skyflix.to").performClick()

    composeTestRule.runOnIdle { assertEquals("https://skyflix.to/", openedUrl) }
  }

  @Test
  fun malformedAddress_isRejected() {
    composeTestRule.onNodeWithContentDescription("Website URL").performTextReplacement("not a url")
    composeTestRule.onNodeWithText("Visit Website").performClick()

    composeTestRule.onNodeWithText("Enter a valid website address").assertExists()
    composeTestRule.runOnIdle { assertNull(openedUrl) }
  }

  @Test
  fun dpadMovesThroughUrlVisitAndQuickAccess() {
    composeTestRule.onNodeWithText("Visit Website").performSemanticsAction(SemanticsActions.RequestFocus) { it() }
    composeTestRule.onNodeWithText("Visit Website").assertIsFocused()

    composeTestRule.onNodeWithText("Visit Website").performKeyInput { pressKey(Key.DirectionUp) }
    composeTestRule.onNodeWithContentDescription("Website URL").assertIsFocused()

    composeTestRule.onNodeWithContentDescription("Website URL").performKeyInput { pressKey(Key.DirectionDown) }
    composeTestRule.onNodeWithText("Visit Website").assertIsFocused()

    composeTestRule.onNodeWithText("Visit Website").performKeyInput { pressKey(Key.DirectionDown) }
    composeTestRule.onNodeWithContentDescription("Open skyflix.to").assertIsFocused()
  }
}
