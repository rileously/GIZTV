package com.example.auroratv.ui

import androidx.activity.compose.BackHandler
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
import com.example.auroratv.ui.browser.StreamPrefetcher
import com.example.auroratv.ui.catalog.CatalogScreen
import com.example.auroratv.ui.catalog.TmdbShow
import com.example.auroratv.ui.catalog.TvShowDetailScreen
import com.example.auroratv.ui.drama.ShortDrama
import com.example.auroratv.ui.drama.ShortDramaDetailScreen
import com.example.auroratv.ui.drama.ShortDramaScreen
import com.example.auroratv.ui.main.AuroraTvApp
import com.example.auroratv.ui.sports.SportsScreen
import com.example.auroratv.ui.player.HlsPlayerScreen
import com.example.auroratv.ui.player.HlsStreamRequest
import com.example.auroratv.ui.update.AppUpdateController

private const val SKYFLIX_URL = "https://skyflix.to/"

private enum class Destination {
  CATALOG,
  SHOW_DETAIL,
  SHORT_DRAMAS,
  DRAMA_DETAIL,
  SPORTS,
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
  var selectedDrama by remember { mutableStateOf<ShortDrama?>(null) }
  // Held while the browser resolves a stream, then attached to the request the player receives.
  var pendingContext by remember { mutableStateOf<PlaybackContext?>(null) }
  var streamRequest by remember {
    mutableStateOf(initialStreamUrl?.let { HlsStreamRequest(url = it, headers = emptyMap()) })
  }
  // The next episode, being resolved behind the player while its countdown runs.
  var prefetchTarget by remember { mutableStateOf<PlaybackContext?>(null) }
  var prefetched by remember { mutableStateOf<Pair<String, HlsStreamRequest>?>(null) }

  // One stable handler for the drama destinations and the sports page. Registering a BackHandler
  // inside each screen instead would hand the press that leaves the detail page to the listing page
  // as well, dropping the viewer two levels at once.
  BackHandler(
    enabled =
      destination == Destination.SHORT_DRAMAS ||
        destination == Destination.DRAMA_DETAIL ||
        destination == Destination.SPORTS
  ) {
    destination =
      if (destination == Destination.DRAMA_DETAIL) Destination.SHORT_DRAMAS else Destination.CATALOG
  }

  fun openForPlayback(context: PlaybackContext, returnTo: Destination) {
    pendingContext = context
    browserUrl = context.pageUrl
    browserReturnDestination = returnTo
    // Already found while the last episode was finishing, so the loading page is skipped entirely.
    val ready = prefetched?.takeIf { (pageUrl, _) -> pageUrl == context.pageUrl }?.second
    prefetchTarget = null
    prefetched = null
    if (ready != null) {
      streamRequest = ready.copy(context = context)
      destination = Destination.PLAYER
      return
    }
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
      onOpenShortDramas = { destination = Destination.SHORT_DRAMAS },
      onOpenSports = { destination = Destination.SPORTS },
    )
  }

  Box(Modifier.fillMaxSize()) {
    // First in the box, so the player paints over it. Only ever alive while the player is up.
    if (destination == Destination.PLAYER) {
      StreamPrefetcher(
        target = prefetchTarget,
        onResolved = { context, stream -> prefetched = context.pageUrl to stream },
      )
    }

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
      Destination.SHORT_DRAMAS ->
        ShortDramaScreen(
          onOpenDrama = { drama ->
            selectedDrama = drama
            destination = Destination.DRAMA_DETAIL
          },
          onResume = { context -> openForPlayback(context, Destination.SHORT_DRAMAS) },
          onBack = { destination = Destination.CATALOG },
        )
      Destination.DRAMA_DETAIL -> {
        val drama = selectedDrama
        if (drama != null) {
          ShortDramaDetailScreen(
            drama = drama,
            onPlayEpisode = { context -> openForPlayback(context, Destination.DRAMA_DETAIL) },
            onBack = { destination = Destination.SHORT_DRAMAS },
          )
        } else {
          Catalog()
        }
      }
      Destination.SPORTS ->
        SportsScreen(
          onPlay = { context -> openForPlayback(context, Destination.SPORTS) },
          onBack = { destination = Destination.CATALOG },
        )
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
            onPrepareNext = { next -> prefetchTarget = next },
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
