package com.giztv.tv.ui.browser

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
import androidx.compose.ui.platform.LocalContext
import com.giztv.tv.data.PlaybackContext
import com.giztv.tv.data.StreamResolutionStore
import com.giztv.tv.ui.catalog.nextProviderPageUrl
import com.giztv.tv.ui.player.ActivePlayback
import com.giztv.tv.ui.player.HlsStreamRequest
import com.giztv.tv.ui.player.PlaybackHeadroom
import com.giztv.tv.ui.player.mayPrefetch

/**
 * Finds the next episode's stream while the viewer is still looking at the last one.
 *
 * Resolving a stream means loading a page and waiting for its player to ask for the video, which
 * takes long enough to be a wait of its own between episodes. Nothing about it needs to be on
 * screen, so it happens behind the player and the next episode opens on a stream already in hand.
 *
 * It is laid out at full size and covered by the player rather than shrunk to nothing, because a
 * page given no room may never lay out its player at all.
 *
 * Nothing is heard from it. Media here needs a gesture to start, which is how most titles are found
 * without a frame ever being played — but a provider that only asks for its video once its own play
 * control is pressed has to be pressed ([startVideasyPlaybackScript]), and that starts a whole film
 * behind the one being watched. Every page loaded here is muted by [silenceWebMediaScript] for as
 * long as it is loaded, and the view itself is destroyed the moment this stops being wanted.
 */
@Composable
internal fun StreamPrefetcher(
  target: PlaybackContext?,
  onResolved: (PlaybackContext, HlsStreamRequest) -> Unit,
) {
  if (target == null) return
  val currentOnResolved by rememberUpdatedState(onResolved)
  var client by remember(target.pageUrl) { mutableStateOf<AdBlockingWebViewClient?>(null) }
  var webView by remember { mutableStateOf<WebView?>(null) }

  // Never at the expense of the film being watched. A page loaded here is a second video on the
  // same connection, so it waits for the one in front of the viewer to be comfortably ahead — and
  // gives way entirely the moment that one starts refilling.
  var started by remember(target.pageUrl) { mutableStateOf(false) }
  if (!mayPrefetch(PlaybackHeadroom.status, started)) return
  // Muted rather than stopped: this page is only useful while it keeps running, and it is never
  // heard in the first place. Registering it is what lets the player insist on that.
  val audioSource = remember {
    ActivePlayback.Source {
      val view = webView ?: return@Source
      view.post { view.evaluateJavascript(silenceWebMediaScript, null) }
    }
  }
  DisposableEffect(audioSource) {
    ActivePlayback.register(audioSource)
    onDispose { ActivePlayback.unregister(audioSource) }
  }
  val appContext = LocalContext.current.applicationContext
  val resolutionStore = remember(appContext) { StreamResolutionStore(appContext) }
  // Whichever site last gave up a stream, which for a viewer who keeps picking one server is the
  // one worth asking ahead of time. Nothing learned yet means the head of the list, as before.
  val prefetchAttempt = remember(target.pageUrl) { resolutionStore.lastProviderIndex() ?: 0 }

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
          // Ordinary cache rules, for the reason the foreground resolver uses them: a player's
          // script bundle is the same on every visit and re-fetching it is most of the wait. It
          // defers to the same run-wide judgement, so one page that resolved to nothing out of the
          // cache turns it off here too.
          cacheMode = resolverCacheMode()
          mixedContentMode = WebSettings.MIXED_CONTENT_NEVER_ALLOW
          setGeolocationEnabled(false)
          userAgentString = CHROME_USER_AGENT
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
            onStreamDetected = { stream ->
              // The address is what this page was for. Whatever it started playing to produce it is
              // now pure cost — bytes taken from the film being watched — so the page is stopped
              // here rather than left running until this view is taken down.
              webView?.let { view -> view.post { view.evaluateJavascript(stopWebMediaScript, null) } }
              currentOnResolved(target, stream)
            },
            onRendererGone = {},
            // Nobody is waiting for this page, and a film behind a film must not be heard.
            silenceMedia = true,
          )
        webViewClient = resolver
        client = resolver
        webView = this
        started = true
        loadUrl(nextProviderPageUrl(target.pageUrl, prefetchAttempt) ?: target.pageUrl)
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

  // A page told to press play keeps playing for as long as the view holding it exists, and this one
  // is behind everything: closing the client alone left a film running where nobody could see it,
  // let alone stop it. Tied to the view rather than to the title, because the view outlives one.
  DisposableEffect(Unit) {
    onDispose {
      webView?.apply {
        stopLoading()
        loadUrl("about:blank")
        removeAllViews()
        destroy()
      }
      webView = null
      // Whatever this page had got through is gone with it, so the next attempt is a new one and
      // has to wait for the same clear water as the first.
      started = false
    }
  }
}
