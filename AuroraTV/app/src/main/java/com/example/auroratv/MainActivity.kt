package com.example.auroratv

import android.content.pm.ActivityInfo
import android.content.pm.PackageManager
import android.os.Bundle
import androidx.activity.compose.setContent
import androidx.appcompat.app.AppCompatActivity
import androidx.core.net.toUri
import com.example.auroratv.data.flushHttpResponseCache
import com.example.auroratv.data.installHttpResponseCache
import com.example.auroratv.theme.AuroraTVTheme
import com.example.auroratv.ui.AuroraTvRoot

class MainActivity : AppCompatActivity() {
  override fun onCreate(savedInstanceState: Bundle?) {
    installHttpResponseCache(this)
    requestedOrientation =
      gizTvOrientation(
        isTelevision = packageManager.hasSystemFeature(PackageManager.FEATURE_LEANBACK),
        playerActive = false,
      )
    super.onCreate(savedInstanceState)
    val launchStreamUrl =
      intent?.data?.toString()?.takeIf {
        (it.startsWith("https://") || it.startsWith("http://")) && it.contains(".m3u8", ignoreCase = true)
      }
    val launchBrowserUrl =
      intent?.data?.toString()?.takeIf {
        val uri = it.toUri()
        launchStreamUrl == null && uri.scheme == "https" &&
          (uri.host.equals("skyflix.to", ignoreCase = true) || uri.host.equals("www.skyflix.to", ignoreCase = true))
      }
    setContent {
      AuroraTVTheme {
        AuroraTvRoot(initialStreamUrl = launchStreamUrl, initialBrowserUrl = launchBrowserUrl)
      }
    }
  }

  override fun onStop() {
    super.onStop()
    flushHttpResponseCache()
  }
}

/**
 * Which way up the app sits.
 *
 * A television is always landscape. A phone turns landscape to give a widescreen picture the whole
 * display, but a short drama is shot 9:16 — rotating for it would letterbox a portrait video inside
 * a landscape window and waste most of the screen, so [verticalVideo] keeps it upright.
 */
internal fun gizTvOrientation(
  isTelevision: Boolean,
  playerActive: Boolean,
  verticalVideo: Boolean = false,
): Int =
  if (isTelevision || (playerActive && !verticalVideo)) {
    ActivityInfo.SCREEN_ORIENTATION_SENSOR_LANDSCAPE
  } else {
    ActivityInfo.SCREEN_ORIENTATION_PORTRAIT
  }
