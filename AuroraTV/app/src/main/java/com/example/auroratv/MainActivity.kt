package com.example.auroratv

import android.content.pm.ActivityInfo
import android.content.pm.PackageManager
import android.os.Bundle
import androidx.activity.compose.setContent
import androidx.appcompat.app.AppCompatActivity
import androidx.core.net.toUri
import com.example.auroratv.theme.AuroraTVTheme
import com.example.auroratv.ui.AuroraTvRoot

class MainActivity : AppCompatActivity() {
  override fun onCreate(savedInstanceState: Bundle?) {
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
}

internal fun gizTvOrientation(isTelevision: Boolean, playerActive: Boolean): Int =
  if (isTelevision || playerActive) {
    ActivityInfo.SCREEN_ORIENTATION_SENSOR_LANDSCAPE
  } else {
    ActivityInfo.SCREEN_ORIENTATION_PORTRAIT
  }
