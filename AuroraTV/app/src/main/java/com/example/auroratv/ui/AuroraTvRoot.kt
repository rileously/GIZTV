package com.example.auroratv.ui

import androidx.activity.compose.BackHandler
import androidx.compose.runtime.Composable
import androidx.compose.foundation.layout.Box
import androidx.compose.foundation.layout.fillMaxSize
import androidx.compose.runtime.getValue
import androidx.compose.runtime.mutableIntStateOf
import androidx.compose.runtime.mutableStateOf
import androidx.compose.runtime.remember
import androidx.compose.runtime.saveable.rememberSaveable
import androidx.compose.runtime.setValue
import androidx.compose.ui.Modifier
import androidx.compose.runtime.DisposableEffect
import androidx.compose.runtime.LaunchedEffect
import androidx.compose.runtime.SideEffect
import androidx.compose.ui.focus.FocusDirection
import androidx.compose.ui.platform.LocalContext
import androidx.compose.ui.platform.LocalFocusManager
import androidx.compose.runtime.rememberCoroutineScope
import com.example.auroratv.data.PlaybackContext
import com.example.auroratv.data.CachedSubtitle
import com.example.auroratv.data.StreamCacheStore
import com.example.auroratv.data.streamStillLive
import com.example.auroratv.home.isTelevision
import com.example.auroratv.home.resumeContextFor
import com.example.auroratv.link.LinkKey
import com.example.auroratv.link.RemoteUiBridge
import com.example.auroratv.ui.browser.BrowserScreen
import com.example.auroratv.ui.browser.StreamPrefetcher
import com.example.auroratv.ui.catalog.CatalogScreen
import com.example.auroratv.ui.catalog.TmdbShow
import com.example.auroratv.ui.catalog.TvShowDetailScreen
import com.example.auroratv.ui.drama.ShortDrama
import com.example.auroratv.ui.drama.ShortDramaDetailScreen
import com.example.auroratv.ui.drama.ShortDramaScreen
import com.example.auroratv.ui.main.AuroraTvApp
import com.example.auroratv.ui.iptv.IptvScreen
import com.example.auroratv.ui.sports.SportsScreen
import com.example.auroratv.ui.player.HlsPlayerScreen
import androidx.media3.common.Player
import com.example.auroratv.ui.player.ExternalSubtitleTrack
import com.example.auroratv.ui.player.HlsStreamRequest
import com.example.auroratv.ui.player.PlaybackProgressStore
import com.example.auroratv.ui.player.playbackProgressKeyForPage
import com.example.auroratv.ui.link.PairingCodeOverlay
import com.example.auroratv.ui.link.RemoteScreen
import com.example.auroratv.ui.update.AppUpdateController
import kotlinx.coroutines.launch

private const val SKYFLIX_URL = "https://skyflix.to/"
private const val MAX_AUTOMATIC_STREAM_FAILOVERS = 2

internal enum class StreamFailureAction {
  RESOLVE_FRESH_STREAM,
  SHOW_PLAYER_ERROR,
}

internal fun streamFailureAction(
  hasPlaybackContext: Boolean,
  completedFailovers: Int,
): StreamFailureAction =
  if (hasPlaybackContext && completedFailovers < MAX_AUTOMATIC_STREAM_FAILOVERS) {
    StreamFailureAction.RESOLVE_FRESH_STREAM
  } else {
    StreamFailureAction.SHOW_PLAYER_ERROR
  }

private enum class Destination {
  CATALOG,
  SHOW_DETAIL,
  SHORT_DRAMAS,
  DRAMA_DETAIL,
  SPORTS,
  IPTV,
  REMOTE,
  WEB_HOME,
  BROWSER,
  PLAYER,
}

@Composable
fun AuroraTvRoot(
  initialStreamUrl: String? = null,
  initialBrowserUrl: String? = null,
  initialResumePageUrl: String? = null,
) {
  val appContext = LocalContext.current.applicationContext
  val isTelevision = remember(appContext) { appContext.isTelevision() }
  val initialResume =
    remember(initialResumePageUrl) { resumeContextFor(appContext, initialResumePageUrl) }
  // Deliberately not saveable: opening the app should always land on the catalog. Restoring these
  // would drop the viewer back into a half-loaded page or a player whose stream no longer exists.
  var destination by remember {
    mutableStateOf(
      when {
        initialStreamUrl != null -> Destination.PLAYER
        // A title picked from a widget or the television's own row still has to have its stream
        // found, so it goes through the same loading page a title picked in the catalog does.
        initialResume != null || initialBrowserUrl != null -> Destination.BROWSER
        else -> Destination.CATALOG
      }
    )
  }
  var browserUrl by remember {
    mutableStateOf(initialResume?.pageUrl ?: initialBrowserUrl ?: SKYFLIX_URL)
  }
  var browserReturnDestination by remember { mutableStateOf(Destination.CATALOG) }
  var selectedShow by remember { mutableStateOf<TmdbShow?>(null) }
  var selectedDrama by remember { mutableStateOf<ShortDrama?>(null) }
  // Held while the browser resolves a stream, then attached to the request the player receives.
  var pendingContext by remember { mutableStateOf(initialResume) }
  var streamRequest by remember {
    mutableStateOf(initialStreamUrl?.let { HlsStreamRequest(url = it, headers = emptyMap()) })
  }
  // The next episode, being resolved behind the player while its countdown runs.
  var prefetchTarget by remember { mutableStateOf<PlaybackContext?>(null) }
  var prefetched by remember { mutableStateOf<Pair<String, HlsStreamRequest>?>(null) }
  val streamCache = remember(appContext) { StreamCacheStore(appContext) }
  var streamFailoverAttempts by remember { mutableIntStateOf(0) }
  val scope = rememberCoroutineScope()

  // What a paired phone's pad actually drives. Compose moves focus perfectly well when asked; what
  // it will not do is respond to a key event the app posted to itself while still in touch mode.
  val focusManager = LocalFocusManager.current
  DisposableEffect(focusManager) {
    RemoteUiBridge.moveFocus = { key ->
      when (key) {
        LinkKey.UP -> focusManager.moveFocus(FocusDirection.Up)
        LinkKey.DOWN -> focusManager.moveFocus(FocusDirection.Down)
        LinkKey.LEFT -> focusManager.moveFocus(FocusDirection.Left)
        LinkKey.RIGHT -> focusManager.moveFocus(FocusDirection.Right)
        else -> false
      }
    }
    onDispose { RemoteUiBridge.moveFocus = null }
  }
  SideEffect { RemoteUiBridge.playerOpen = destination == Destination.PLAYER }

  // One stable handler for the drama destinations and the sports page. Registering a BackHandler
  // inside each screen instead would hand the press that leaves the detail page to the listing page
  // as well, dropping the viewer two levels at once.
  BackHandler(
    enabled =
      destination == Destination.SHORT_DRAMAS ||
        destination == Destination.DRAMA_DETAIL ||
        destination == Destination.SPORTS ||
        destination == Destination.IPTV ||
        destination == Destination.REMOTE
  ) {
    destination =
      if (destination == Destination.DRAMA_DETAIL) Destination.SHORT_DRAMAS else Destination.CATALOG
  }

  fun openForPlayback(context: PlaybackContext, returnTo: Destination) {
    streamFailoverAttempts = 0
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

    // A stream found earlier is worth trying before the page is ground through again. The loading
    // page is already up, so this races the search rather than delaying it: whichever answers first
    // is the one the viewer gets, and a remembered address that has quietly died costs only the
    // moment spent asking.
    val cached = streamCache.find(context.pageUrl) ?: return
    scope.launch {
      if (!streamStillLive(cached.url, cached.headers)) {
        streamCache.forget(context.pageUrl)
        return@launch
      }
      // Only if the viewer is still waiting for this same title and has not been served already.
      if (pendingContext?.pageUrl != context.pageUrl || destination != Destination.BROWSER) {
        return@launch
      }
      streamRequest =
        HlsStreamRequest(
          url = cached.url,
          headers = cached.headers,
          subtitles =
            cached.subtitles.map { ExternalSubtitleTrack(it.url, it.label, it.language, it.mimeType) },
          sourcePageUrl = cached.sourcePageUrl,
          context = context,
        )
      destination = Destination.PLAYER
    }
  }

  // A title asked for while the app was already open — from a widget, the television's own row,
  // or a phone handing one over. The first one is handled by the starting destination above; this
  // catches every one after it, which previously needed the activity to be destroyed to arrive.
  var lastResumed by remember { mutableStateOf(initialResumePageUrl) }
  LaunchedEffect(initialResumePageUrl) {
    val pageUrl = initialResumePageUrl ?: return@LaunchedEffect
    if (pageUrl == lastResumed) return@LaunchedEffect
    lastResumed = pageUrl
    resumeContextFor(appContext, pageUrl)?.let { openForPlayback(it, Destination.CATALOG) }
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
      onOpenIptv = { destination = Destination.IPTV },
      // Nothing to find if it is already known, and nothing worth starting over a title that is
      // already being looked for.
      onConsidering = { considered ->
        if (streamCache.find(considered.pageUrl) == null && prefetchTarget?.pageUrl != considered.pageUrl) {
          prefetchTarget = considered
        }
      },
      // A television is what a remote points at, so it is not offered one of its own.
      onOpenRemote =
        if (isTelevision) null else ({ destination = Destination.REMOTE }),
    )
  }

  Box(Modifier.fillMaxSize()) {
    // First in the box, so the player paints over it. Only ever alive while the player is up.
    // Behind whatever is on screen. While the player is up this finds the next episode; on the
    // catalog it finds whatever the viewer has stopped on, so pressing play has nothing left to
    // wait for. Either way it is covered by the screen in front of it and takes no focus.
    if (
      destination == Destination.PLAYER ||
        destination == Destination.CATALOG ||
        destination == Destination.SPORTS
    ) {
      StreamPrefetcher(
        target = prefetchTarget,
        onResolved = { context, stream ->
          prefetched = context.pageUrl to stream
          // Kept beyond this run of the app too, since the work has been done either way.
          streamCache.remember(
            pageUrl = context.pageUrl,
            url = stream.url,
            headers = stream.headers,
            subtitles =
              stream.subtitles.map { CachedSubtitle(it.url, it.label, it.language, it.mimeType) },
            sourcePageUrl = stream.sourcePageUrl,
          )
        },
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
      Destination.REMOTE ->
        RemoteScreen(
          onBack = { destination = Destination.CATALOG },
          onPlayHere = { pageUrl, title, subtitle, posterUrl, positionMs ->
            // The position comes from the television, so the phone's player has to be told it
            // before the title opens: it resumes from what this device has stored, and this device
            // has been in another room.
            PlaybackProgressStore(appContext)
              .update(
                key = playbackProgressKeyForPage(pageUrl),
                positionMs = positionMs,
                durationMs = 0L,
                playbackState = Player.STATE_READY,
              )
            openForPlayback(
              PlaybackContext(
                pageUrl = pageUrl,
                title = title,
                subtitle = subtitle,
                posterUrl = posterUrl,
              ),
              Destination.CATALOG,
            )
          },
        )
      Destination.SPORTS ->
        SportsScreen(
          onPlay = { context -> openForPlayback(context, Destination.SPORTS) },
          onConsidering = { considered ->
            if (
              streamCache.find(considered.pageUrl) == null &&
                prefetchTarget?.pageUrl != considered.pageUrl
            ) {
              prefetchTarget = considered
            }
          },
          onBack = { destination = Destination.CATALOG },
        )
      Destination.IPTV ->
        IptvScreen(
          onPlay = { channel ->
            streamFailoverAttempts = 0
            pendingContext = null
            browserReturnDestination = Destination.IPTV
            streamRequest = channel.toPlaybackRequest()
            destination = Destination.PLAYER
          },
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
                when {
                  browserReturnDestination == Destination.IPTV -> Destination.IPTV
                  request.context != null -> browserReturnDestination
                  else -> Destination.BROWSER
                }
            },
            onPlayNext = { next -> openForPlayback(next, browserReturnDestination) },
            onPrepareNext = { next -> prefetchTarget = next },
            onHandedOver = { destination = Destination.REMOTE },
            // Signed stream addresses can expire or point to an unhealthy edge. Forget the dead
            // address and resolve the title page again, but bound the automatic loop so a genuinely
            // broken title still presents a useful error instead of loading forever.
            onPlaybackFailed = {
              if (browserReturnDestination == Destination.IPTV) {
                false
              } else {
                val playbackContext = request.context
                playbackContext?.pageUrl?.let(streamCache::forget)
                when (
                  streamFailureAction(
                    hasPlaybackContext = playbackContext != null,
                    completedFailovers = streamFailoverAttempts,
                  )
                ) {
                  StreamFailureAction.RESOLVE_FRESH_STREAM -> {
                    streamFailoverAttempts++
                    pendingContext = playbackContext
                    browserUrl = requireNotNull(playbackContext).pageUrl
                    streamRequest = null
                    destination = Destination.BROWSER
                    true
                  }
                  StreamFailureAction.SHOW_PLAYER_ERROR -> false
                }
              }
            },
            onPlaybackStable = { streamFailoverAttempts = 0 },
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

    // Unlike the update prompt, this one belongs over the player too: someone pairing a phone is
    // most likely doing it because they want to control what is playing right now.
    PairingCodeOverlay()
  }
}
