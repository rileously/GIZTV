package com.example.auroratv.ui

import androidx.compose.runtime.Composable
import androidx.compose.foundation.layout.Box
import androidx.compose.foundation.layout.fillMaxSize
import androidx.compose.runtime.getValue
import androidx.compose.runtime.mutableStateOf
import androidx.compose.runtime.remember
import androidx.compose.runtime.saveable.rememberSaveable
import androidx.compose.runtime.setValue
import androidx.compose.ui.Modifier
import com.example.auroratv.data.PlaybackContext
import com.example.auroratv.ui.browser.BrowserScreen
import com.example.auroratv.ui.catalog.CatalogScreen
import com.example.auroratv.ui.catalog.TmdbShow
import com.example.auroratv.ui.catalog.TvShowDetailScreen
import com.example.auroratv.ui.main.AuroraTvApp
import com.example.auroratv.ui.player.HlsPlayerScreen
import com.example.auroratv.ui.player.HlsStreamRequest
import com.example.auroratv.ui.update.AppUpdateController

private const val SKYFLIX_URL = "https://skyflix.to/"

private enum class Destination {
  CATALOG,
  SHOW_DETAIL,
  WEB_HOME,
  BROWSER,
  PLAYER,
}

@Composable
fun AuroraTvRoot(initialStreamUrl: String? = null, initialBrowserUrl: String? = null) {
  // Deliberately not saveable: opening the app should always land on the catalog. Restoring these
  // would drop the viewer back into a half-loaded page or a player whose stream no longer exists.
  var destination by remember {
    mutableStateOf(
      when {
        initialStreamUrl != null -> Destination.PLAYER
        initialBrowserUrl != null -> Destination.BROWSER
        else -> Destination.CATALOG
      }
    )
  }
  var browserUrl by remember { mutableStateOf(initialBrowserUrl ?: SKYFLIX_URL) }
  var browserReturnDestination by remember { mutableStateOf(Destination.CATALOG) }
  var selectedShow by remember { mutableStateOf<TmdbShow?>(null) }
  // Held while the browser resolves a stream, then attached to the request the player receives.
  var pendingContext by remember { mutableStateOf<PlaybackContext?>(null) }
  var streamRequest by remember {
    mutableStateOf(initialStreamUrl?.let { HlsStreamRequest(url = it, headers = emptyMap()) })
  }

  fun openForPlayback(context: PlaybackContext, returnTo: Destination) {
    pendingContext = context
    browserUrl = context.pageUrl
    browserReturnDestination = returnTo
    destination = Destination.BROWSER
  }

  @Composable
  fun Catalog() {
    CatalogScreen(
      onPlay = { context -> openForPlayback(context, Destination.CATALOG) },
      onOpenShow = { show ->
        selectedShow = show
        destination = Destination.SHOW_DETAIL
      },
      onOpenWeb = { destination = Destination.WEB_HOME },
    )
  }

  Box(Modifier.fillMaxSize()) {
    when (destination) {
      Destination.CATALOG -> Catalog()
      Destination.SHOW_DETAIL -> {
        val show = selectedShow
        if (show != null) {
          TvShowDetailScreen(
            show = show,
            onPlayEpisode = { context -> openForPlayback(context, Destination.SHOW_DETAIL) },
            onBack = { destination = Destination.CATALOG },
          )
        } else {
          Catalog()
        }
      }
      Destination.WEB_HOME ->
        AuroraTvApp(
          onOpenBrowser = { url ->
            pendingContext = null
            browserUrl = url
            browserReturnDestination = Destination.WEB_HOME
            destination = Destination.BROWSER
          },
          onOpenMovies = { destination = Destination.CATALOG },
        )
      Destination.BROWSER ->
        BrowserScreen(
          initialUrl = browserUrl,
          onUrlChanged = { browserUrl = it },
          playback = pendingContext,
          onExit = { destination = browserReturnDestination },
          onStreamDetected = { request ->
            streamRequest = request.copy(context = pendingContext)
            destination = Destination.PLAYER
          },
        )
      Destination.PLAYER -> {
        val request = streamRequest
        if (request != null) {
          HlsPlayerScreen(
            request = request,
            // Leaving a catalog title returns to the list it came from, not to the loading page that
            // resolved it. Streams found by hand still step back to the site they were found on.
            onExit = {
              destination =
                if (request.context != null) browserReturnDestination else Destination.BROWSER
            },
            onPlayNext = { next -> openForPlayback(next, browserReturnDestination) },
          )
        } else {
          Catalog()
        }
      }
    }

    // Never interrupt playback or stream discovery with an update prompt.
    if (destination != Destination.PLAYER && destination != Destination.BROWSER) {
      AppUpdateController()
    }
  }
}
