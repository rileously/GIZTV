package com.example.auroratv

import android.content.pm.ActivityInfo
import android.content.pm.PackageManager
import android.os.Bundle
import androidx.activity.compose.setContent
import androidx.appcompat.app.AppCompatActivity
import androidx.core.net.toUri
import com.example.auroratv.data.flushHttpResponseCache
import com.example.auroratv.data.installHttpResponseCache
import com.example.auroratv.home.EXTRA_RESUME_PAGE_URL
import com.example.auroratv.home.refreshHomeSurfaces
import com.example.auroratv.theme.AuroraTVTheme
import com.example.auroratv.ui.AuroraTvRoot
import com.example.auroratv.update.UpdateCheckWorker

class MainActivity : AppCompatActivity() {
  override fun onCreate(savedInstanceState: Bundle?) {
    installHttpResponseCache(this)
    requestedOrientation =
      gizTvOrientation(
        isTelevision = packageManager.hasSystemFeature(PackageManager.FEATURE_LEANBACK),
        playerActive = false,
      )
    super.onCreate(savedInstanceState)
    UpdateCheckWorker.schedule(this)
    // Also on the way in, not only on the way out. A television asks the viewer to approve the row
    // the first time it is offered one, and that question belongs in front of someone who has just
    // opened GIZTV rather than behind someone who has just left it.
    refreshHomeSurfaces(applicationContext)
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
    val launchResumePageUrl = intent?.getStringExtra(EXTRA_RESUME_PAGE_URL)
    setContent {
      AuroraTVTheme {
        AuroraTvRoot(
          initialStreamUrl = launchStreamUrl,
          initialBrowserUrl = launchBrowserUrl,
          initialResumePageUrl = launchResumePageUrl,
        )
      }
    }
  }

  override fun onStop() {
    super.onStop()
    flushHttpResponseCache()
    // Whatever was watched this time round changes what the home screens should be offering. Doing
    // it on the way out rather than on every progress tick keeps one write per visit to the app.
    refreshHomeSurfaces(applicationContext)
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
