package com.example.auroratv.ui.catalog

import androidx.activity.ComponentActivity
import androidx.compose.foundation.clickable
import androidx.compose.foundation.layout.Column
import androidx.compose.foundation.layout.Box
import androidx.compose.foundation.layout.size
import androidx.compose.runtime.getValue
import androidx.compose.runtime.mutableStateOf
import androidx.compose.runtime.remember
import androidx.compose.runtime.setValue
import androidx.compose.ui.Modifier
import androidx.compose.ui.focus.FocusRequester
import androidx.compose.ui.focus.focusRequester
import androidx.compose.ui.focus.onFocusChanged
import androidx.compose.ui.semantics.SemanticsActions
import androidx.compose.ui.semantics.contentDescription
import androidx.compose.ui.semantics.semantics
import androidx.compose.ui.test.assertIsFocused
import androidx.compose.ui.test.junit4.createAndroidComposeRule
import androidx.compose.ui.test.onNodeWithContentDescription
import androidx.compose.ui.test.performSemanticsAction
import androidx.compose.ui.unit.dp
import androidx.test.platform.app.InstrumentationRegistry
import org.junit.Before
import org.junit.Rule
import org.junit.Test

class CatalogFocusRestorationTest {
  @get:Rule val composeTestRule = createAndroidComposeRule<ComponentActivity>()

  @Before
  fun useRemoteNavigation() {
    InstrumentationRegistry.getInstrumentation().apply {
      setInTouchMode(false)
      waitForIdleSync()
    }
  }

  @Test
  fun returningFromDetailRefocusesTheExactCard() {
    var active by mutableStateOf(true)
    var rememberedFocusRequester by mutableStateOf<FocusRequester?>(null)

    composeTestRule.setContent {
      val topFocusRequester = remember { FocusRequester() }
      val cardFocusRequester = remember { FocusRequester() }
      RestoreCatalogFocusEffect(
        isActive = active,
        focusRequester = rememberedFocusRequester,
        settleDelayMs = 0,
        retryDelayMs = 0,
      )
      Column {
        Box(
          Modifier.size(100.dp).focusRequester(topFocusRequester).clickable {}
            .semantics { contentDescription = "Top navigation" }
        )
        Box(
          Modifier.size(100.dp).focusRequester(cardFocusRequester)
            .onFocusChanged {
              if (it.isFocused) rememberedFocusRequester = cardFocusRequester
            }
            .clickable {}
            .semantics { contentDescription = "Selected movie card" }
        )
      }
    }

    val card = composeTestRule.onNodeWithContentDescription("Selected movie card")
    val top = composeTestRule.onNodeWithContentDescription("Top navigation")
    card.performSemanticsAction(SemanticsActions.RequestFocus) { it() }
    card.assertIsFocused()

    composeTestRule.runOnIdle { active = false }
    top.performSemanticsAction(SemanticsActions.RequestFocus) { it() }
    top.assertIsFocused()

    composeTestRule.runOnIdle { active = true }
    composeTestRule.waitForIdle()
    card.assertIsFocused()
  }
}
