package com.example.auroratv.ui.update

import androidx.activity.ComponentActivity
import androidx.compose.ui.input.key.Key
import androidx.compose.ui.test.ExperimentalTestApi
import androidx.compose.ui.test.assertIsFocused
import androidx.compose.ui.test.junit4.createAndroidComposeRule
import androidx.compose.ui.test.onNodeWithTag
import androidx.compose.ui.test.performKeyInput
import androidx.compose.ui.test.pressKey
import androidx.test.platform.app.InstrumentationRegistry
import com.example.auroratv.theme.AuroraTVTheme
import com.example.auroratv.update.AppUpdateInfo
import org.junit.Assert.assertTrue
import org.junit.Before
import org.junit.Rule
import org.junit.Test

@OptIn(ExperimentalTestApi::class)
class AppUpdateOverlayTest {
  @get:Rule val composeTestRule = createAndroidComposeRule<ComponentActivity>()

  @Before
  fun leaveTouchMode() {
    InstrumentationRegistry.getInstrumentation().apply {
      setInTouchMode(false)
      waitForIdleSync()
    }
  }

  @Test
  fun remoteMovesToLaterAndActivatesIt() {
    var dismissed = false
    val update =
      AppUpdateInfo(
        versionCode = 19,
        versionName = "1.7.1",
        apkUrl = "https://example.com/GIZTV.apk",
        sha256 = "a".repeat(64),
        releaseNotes = "Improved playback on slow connections.",
      )
    composeTestRule.setContent {
      AuroraTVTheme {
        AppUpdateOverlay(
          state = UpdateUiState.Available(update),
          onUpdate = {},
          onInstall = {},
          onDismiss = { dismissed = true },
        )
      }
    }

    val updateNow = composeTestRule.onNodeWithTag("update_primary")
    val later = composeTestRule.onNodeWithTag("update_later")
    updateNow.assertIsFocused().performKeyInput { pressKey(Key.DirectionRight) }
    later.assertIsFocused().performKeyInput { pressKey(Key.DirectionCenter) }

    composeTestRule.runOnIdle { assertTrue(dismissed) }
  }
}
