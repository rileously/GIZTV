package com.example.auroratv.ui.browser

import android.graphics.Color as AndroidColor
import android.os.Build
import android.webkit.CookieManager
import android.webkit.WebSettings
import android.webkit.WebView
import androidx.compose.foundation.layout.fillMaxSize
import androidx.compose.runtime.Composable
import androidx.compose.runtime.DisposableEffect
import androidx.compose.runtime.getValue
import androidx.compose.runtime.mutableStateOf
import androidx.compose.runtime.remember
import androidx.compose.runtime.rememberUpdatedState
import androidx.compose.runtime.setValue
import androidx.compose.ui.Modifier
import androidx.compose.ui.viewinterop.AndroidView
import com.example.auroratv.data.PlaybackContext
import com.example.auroratv.ui.catalog.nextProviderPageUrl
import com.example.auroratv.ui.player.HlsStreamRequest

/**
 * Finds the next episode's stream while the viewer is still looking at the last one.
 *
 * Resolving a stream means loading a page and waiting for its player to ask for the video, which
 * takes long enough to be a wait of its own between episodes. Nothing about it needs to be on
 * screen, so it happens behind the player and the next episode opens on a stream already in hand.
 *
 * It is laid out at full size and covered by the player rather than shrunk to nothing, because a
 * page given no room may never lay out its player at all. Nothing is heard from it: media here
 * needs a gesture to start, the same as during discovery, which is why a stream can be found
 * without a frame ever being played.
 */
@Composable
internal fun StreamPrefetcher(
  target: PlaybackContext?,
  onResolved: (PlaybackContext, HlsStreamRequest) -> Unit,
) {
  if (target == null) return
  val currentOnResolved by rememberUpdatedState(onResolved)
  var client by remember(target.pageUrl) { mutableStateOf<AdBlockingWebViewClient?>(null) }

  AndroidView(
    factory = { context ->
      WebView(context).apply {
        setBackgroundColor(AndroidColor.TRANSPARENT)
        isFocusable = false
        isFocusableInTouchMode = false
        settings.apply {
          javaScriptEnabled = true
          domStorageEnabled = true
          allowFileAccess = false
          allowContentAccess = false
          javaScriptCanOpenWindowsAutomatically = false
          setSupportMultipleWindows(false)
          mediaPlaybackRequiresUserGesture = true
          useWideViewPort = true
          loadWithOverviewMode = true
          builtInZoomControls = false
          displayZoomControls = false
          cacheMode = WebSettings.LOAD_NO_CACHE
          mixedContentMode = WebSettings.MIXED_CONTENT_NEVER_ALLOW
          setGeolocationEnabled(false)
          if (Build.VERSION.SDK_INT >= Build.VERSION_CODES.O) safeBrowsingEnabled = true
        }
        CookieManager.getInstance().setAcceptThirdPartyCookies(this, true)
        val resolver =
          AdBlockingWebViewClient(
            // Nothing here is ever shown, so nothing decorative is ever worth fetching.
            resolvingOnly = true,
            userAgent = settings.userAgentString,
            onPageState = { _, _, _, _ -> },
            onStatus = {},
            onStage = {},
            onStreamDetected = { stream -> currentOnResolved(target, stream) },
            onRendererGone = {},
          )
        webViewClient = resolver
        client = resolver
        // The same provider the foreground resolver would have asked first, so a title found
        // ahead of time is found from the same place as one opened by hand.
        loadUrl(nextProviderPageUrl(target.pageUrl, 0) ?: target.pageUrl)
      }
    },
    // Behind the player, which is opaque and covers it completely.
    modifier = Modifier.fillMaxSize(),
  )

  DisposableEffect(target.pageUrl) {
    onDispose {
      client?.close()
      client = null
    }
  }
}
