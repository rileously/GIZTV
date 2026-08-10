package com.giztv.tv.ui.iptv

import androidx.activity.ComponentActivity
import androidx.compose.foundation.layout.Box
import androidx.compose.foundation.layout.fillMaxSize
import androidx.compose.runtime.MutableState
import androidx.compose.runtime.remember
import androidx.compose.runtime.mutableStateOf
import androidx.compose.ui.Modifier
import androidx.compose.ui.platform.testTag
import androidx.compose.ui.test.assertIsSelected
import androidx.compose.ui.test.assertIsDisplayed
import androidx.compose.ui.test.junit4.createAndroidComposeRule
import androidx.compose.ui.test.onAllNodesWithTag
import androidx.compose.ui.test.onNodeWithTag
import androidx.compose.ui.test.performClick
import org.junit.Rule
import org.junit.Test

class IptvPlayerReturnStateTest {

  @get:Rule val composeTestRule = createAndroidComposeRule<ComponentActivity>()

  @Test
  fun returningFromPlayerKeepsSelectedCategory() {
    lateinit var showIptv: MutableState<Boolean>
    lateinit var browseState: MutableState<IptvBrowseState>

    composeTestRule.setContent {
      showIptv = remember { mutableStateOf(true) }
      browseState = remember { mutableStateOf(IptvBrowseState()) }

      if (showIptv.value) {
        IptvScreen(
          onPlay = {},
          onBack = {},
          browseState = browseState.value,
          onBrowseStateChanged = { browseState.value = it },
        )
      } else {
        Box(Modifier.fillMaxSize().testTag("player"))
      }
    }

    composeTestRule.waitUntil(timeoutMillis = 20_000) {
      composeTestRule.onAllNodesWithTag("iptv-category:Sports").fetchSemanticsNodes().isNotEmpty()
    }
    val sports = composeTestRule.onNodeWithTag("iptv-category:Sports")
    sports.assertIsDisplayed().performClick().assertIsSelected()

    composeTestRule.runOnIdle { showIptv.value = false }
    composeTestRule.onNodeWithTag("player").assertIsDisplayed()

    composeTestRule.runOnIdle { showIptv.value = true }
    composeTestRule.waitUntil(timeoutMillis = 20_000) {
      composeTestRule.onAllNodesWithTag("iptv-category:Sports").fetchSemanticsNodes().isNotEmpty()
    }
    composeTestRule.onNodeWithTag("iptv-category:Sports").assertIsSelected()
  }
}
