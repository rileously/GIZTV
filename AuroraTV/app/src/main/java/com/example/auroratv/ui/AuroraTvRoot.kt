package com.example.auroratv.ui

import androidx.activity.compose.BackHandler
import androidx.compose.runtime.Composable
import androidx.compose.foundation.layout.Box
import androidx.compose.foundation.layout.Column
import androidx.compose.foundation.layout.fillMaxSize
import androidx.compose.foundation.layout.fillMaxWidth
import androidx.compose.runtime.getValue
import androidx.compose.runtime.mutableIntStateOf
import androidx.compose.runtime.mutableStateOf
import androidx.compose.runtime.remember
import androidx.compose.runtime.saveable.rememberSaveableStateHolder
import androidx.compose.runtime.setValue
import androidx.compose.ui.Modifier
import androidx.compose.runtime.DisposableEffect
import androidx.compose.runtime.LaunchedEffect
import androidx.compose.runtime.SideEffect
import androidx.compose.ui.focus.FocusDirection
import androidx.compose.ui.platform.LocalContext
import androidx.compose.ui.platform.LocalFocusManager
import androidx.compose.runtime.rememberCoroutineScope
import androidx.compose.ui.unit.dp
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
import com.example.auroratv.ui.browser.streamMimeType
import com.example.auroratv.ui.catalog.CatalogScreen
import com.example.auroratv.ui.catalog.STREAM_PROVIDER_COUNT
import com.example.auroratv.ui.catalog.catalogTargetOf
import com.example.auroratv.ui.catalog.nextProviderPageUrl
import com.example.auroratv.ui.catalog.providerIndexOf
import com.example.auroratv.ui.catalog.providerPageUrl
import com.example.auroratv.ui.catalog.TmdbMovie
import com.example.auroratv.ui.catalog.TmdbShow
import com.example.auroratv.ui.catalog.MovieDetailScreen
import com.example.auroratv.ui.catalog.TvShowDetailScreen
import com.example.auroratv.ui.drama.ShortDrama
import com.example.auroratv.ui.drama.ShortDramaDetailScreen
import com.example.auroratv.ui.drama.ShortDramaScreen
import com.example.auroratv.ui.main.AuroraTvApp
import com.example.auroratv.ui.iptv.IptvBrowseState
import com.example.auroratv.ui.iptv.IptvScreen
import com.example.auroratv.ui.dlhd.DlhdSoccerScreen
import com.example.auroratv.ui.sports.SportsScreen
import com.example.auroratv.ui.player.HlsPlayerScreen
import androidx.media3.common.Player
import com.example.auroratv.ui.player.ExternalSubtitleTrack
import com.example.auroratv.ui.player.HlsStreamRequest
import com.example.auroratv.ui.player.PlaybackProgressStore
import com.example.auroratv.ui.player.playbackProgressKeyForPage
import com.example.auroratv.ui.player.shouldComposeInAppPlayerSession
import com.example.auroratv.ui.link.PairingCodeOverlay
import com.example.auroratv.ui.link.RemoteScreen
import com.example.auroratv.ui.update.AppUpdateController
import kotlinx.coroutines.launch

private const val SKYFLIX_URL = "https://skyflix.to/"
/** One attempt per provider after the first: every site gets asked before the viewer is told no. */
private val MAX_AUTOMATIC_STREAM_FAILOVERS = STREAM_PROVIDER_COUNT - 1

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

internal fun nextIptvPlaybackSource(
  sources: List<HlsStreamRequest>,
  currentIndex: Int,
): Pair<Int, HlsStreamRequest>? {
  val nextIndex = currentIndex + 1
  return sources.getOrNull(nextIndex)?.let { nextIndex to it }
}

/** Picks a specific IPTV backup by hand; null when the index is out of range. */
internal fun iptvPlaybackSourceAt(
  sources: List<HlsStreamRequest>,
  index: Int,
): Pair<Int, HlsStreamRequest>? = sources.getOrNull(index)?.let { index to it }

private enum class Destination {
  CATALOG,
  MOVIE_DETAIL,
  SHOW_DETAIL,
  SHORT_DRAMAS,
  DRAMA_DETAIL,
  SPORTS,
  DLHD_SOCCER,
  IPTV,
  REMOTE,
  WEB_HOME,
  BROWSER,
  PLAYER,
}

/** Browse surfaces that keep the phone footer visible (YouTube-style: hide on player/detail). */
private fun Destination.showsPhoneBottomNav(): Boolean =
  when (this) {
    Destination.CATALOG,
    Destination.SHORT_DRAMAS,
    Destination.SPORTS,
    Destination.DLHD_SOCCER,
    Destination.IPTV,
    Destination.WEB_HOME,
    -> true
    else -> false
  }

private fun Destination.toPhoneBottomTab(): PhoneBottomTab? =
  when (this) {
    Destination.CATALOG -> PhoneBottomTab.MOVIES
    Destination.SPORTS,
    Destination.DLHD_SOCCER,
    -> PhoneBottomTab.SPORTS
    Destination.SHORT_DRAMAS -> PhoneBottomTab.SHORTS
    Destination.WEB_HOME -> PhoneBottomTab.WEB
    Destination.IPTV -> PhoneBottomTab.IPTV
    else -> null
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
  /**
   * Keeps each destination's saved state while it is off screen.
   *
   * Destinations are branches of a `when`, so opening a title removes the catalog from the
   * composition entirely and every `rememberSaveable` in it — the scroll position most of all — goes
   * with it. Coming back rebuilt the page at the top. This hands the subtree its state back.
   */
  val destinationState = rememberSaveableStateHolder()
  var selectedMovie by remember { mutableStateOf<TmdbMovie?>(null) }
  var selectedShow by remember { mutableStateOf<TmdbShow?>(null) }
  var selectedDrama by remember { mutableStateOf<ShortDrama?>(null) }
  // Held while the browser resolves a stream, then attached to the request the player receives.
  var pendingContext by remember { mutableStateOf(initialResume) }
  var streamRequest by remember {
    mutableStateOf(initialStreamUrl?.let { HlsStreamRequest(url = it, headers = emptyMap()) })
  }
  var iptvPlaybackSources by remember { mutableStateOf<List<HlsStreamRequest>>(emptyList()) }
  var iptvPlaybackSourceIndex by remember { mutableIntStateOf(0) }
  var iptvBrowseState by remember { mutableStateOf(IptvBrowseState()) }
  // The next episode, being resolved behind the player while its countdown runs.
  var prefetchTarget by remember { mutableStateOf<PlaybackContext?>(null) }
  var prefetched by remember { mutableStateOf<Pair<String, HlsStreamRequest>?>(null) }
  val streamCache = remember(appContext) { StreamCacheStore(appContext) }
  var streamFailoverAttempts by remember { mutableIntStateOf(0) }
  // In-app mini player: the HLS session stays composed while browse destinations sit underneath.
  var playerMinimized by remember { mutableStateOf(false) }
  // Phone bottom-nav Search: expand/focus search in the current searchable section.
  var requestSectionSearch by remember { mutableStateOf(false) }
  val scope = rememberCoroutineScope()
  val showPhoneBottomNav = !isTelevision && destination.showsPhoneBottomNav()

  fun exitDestinationFor(request: HlsStreamRequest): Destination =
    when {
      browserReturnDestination == Destination.IPTV -> Destination.IPTV
      request.context != null -> browserReturnDestination
      else -> Destination.BROWSER
    }

  fun clearPlayerSession() {
    playerMinimized = false
    streamRequest = null
  }

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
  // Mini player leaves the catalog focusable; only the full-screen player owns the pad.
  SideEffect {
    RemoteUiBridge.playerOpen = destination == Destination.PLAYER && !playerMinimized
  }
  // A PLAYER destination with no stream (failover / dismiss race) must not keep the mini flag.
  LaunchedEffect(destination, streamRequest) {
    if (destination == Destination.PLAYER && streamRequest == null) {
      playerMinimized = false
    }
  }

  // One stable handler for the drama destinations and the sports page. Registering a BackHandler
  // inside each screen instead would hand the press that leaves the detail page to the listing page
  // as well, dropping the viewer two levels at once.
  BackHandler(
    enabled =
      destination == Destination.SHORT_DRAMAS ||
        destination == Destination.DRAMA_DETAIL ||
        destination == Destination.SPORTS ||
        destination == Destination.DLHD_SOCCER ||
        destination == Destination.IPTV ||
        destination == Destination.REMOTE
  ) {
    destination =
      when (destination) {
        Destination.DRAMA_DETAIL -> Destination.SHORT_DRAMAS
        Destination.DLHD_SOCCER -> Destination.SPORTS
        else -> Destination.CATALOG
      }
  }

  fun openForPlayback(context: PlaybackContext, returnTo: Destination) {
    streamFailoverAttempts = 0
    playerMinimized = false
    pendingContext = context
    // The context carries the address this title is remembered by, which is not necessarily the
    // provider we ask first. Attempt zero is the head of the list, so reordering the providers
    // actually reorders who gets asked.
    browserUrl = nextProviderPageUrl(context.pageUrl, 0) ?: context.pageUrl
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
    // Stop any floating session while a new title is resolved.
    streamRequest = null
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
          // Progressive CDN files must not inherit the HLS default mime; null lets Media3 sniff.
          mimeType = streamMimeType(cached.url),
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

  fun openPhoneTab(tab: PhoneBottomTab) {
    when (tab) {
      PhoneBottomTab.MOVIES -> {
        requestSectionSearch = false
        destination = Destination.CATALOG
      }
      PhoneBottomTab.SPORTS -> {
        requestSectionSearch = false
        destination = Destination.SPORTS
      }
      PhoneBottomTab.SHORTS -> {
        requestSectionSearch = false
        destination = Destination.SHORT_DRAMAS
      }
      PhoneBottomTab.WEB -> {
        requestSectionSearch = false
        destination = Destination.WEB_HOME
      }
      PhoneBottomTab.IPTV -> {
        requestSectionSearch = false
        destination = Destination.IPTV
      }
      PhoneBottomTab.SEARCH -> {
        // Expand search in the current section when it supports it; otherwise land on Movies.
        when (destination) {
          Destination.CATALOG,
          Destination.SPORTS,
          Destination.DLHD_SOCCER,
          Destination.SHORT_DRAMAS,
          Destination.IPTV,
          -> requestSectionSearch = true
          else -> {
            requestSectionSearch = true
            destination = Destination.CATALOG
          }
        }
      }
    }
  }

  @Composable
  fun Catalog() {
    CatalogScreen(
      onPlay = { context -> openForPlayback(context, Destination.CATALOG) },
      onOpenMovie = { movie ->
        selectedMovie = movie
        destination = Destination.MOVIE_DETAIL
      },
      onOpenShow = { show ->
        selectedShow = show
        destination = Destination.SHOW_DETAIL
      },
      onOpenWeb = { destination = Destination.WEB_HOME },
      onOpenShortDramas = { destination = Destination.SHORT_DRAMAS },
      onOpenSports = { destination = Destination.SPORTS },
      onOpenDlhdSoccer = { destination = Destination.DLHD_SOCCER },
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
      // Phone footer owns destination jumps; top-bar chips stay on leanback.
      showTopDestinationActions = isTelevision,
      requestSearchFocus = requestSectionSearch && destination == Destination.CATALOG,
      onSearchFocusHandled = { requestSectionSearch = false },
    )
  }

  Box(Modifier.fillMaxSize()) {
    Column(modifier = Modifier.fillMaxSize()) {
      Box(modifier = Modifier.weight(1f).fillMaxWidth()) {
        // First in the box, so the player paints over it. Only ever alive while the player is up.
        // Behind whatever is on screen. While the player is up this finds the next episode; on the
        // catalog it finds whatever the viewer has stopped on, so pressing play has nothing left to
        // wait for. Either way it is covered by the screen in front of it and takes no focus.
        if (
          destination == Destination.PLAYER ||
            destination == Destination.CATALOG ||
            destination == Destination.SPORTS ||
            destination == Destination.DLHD_SOCCER
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
                  stream.subtitles.map {
                    CachedSubtitle(it.url, it.label, it.language, it.mimeType)
                  },
                sourcePageUrl = stream.sourcePageUrl,
              )
            },
          )
        }

        when (destination) {
          Destination.CATALOG ->
            destinationState.SaveableStateProvider(Destination.CATALOG) { Catalog() }
          Destination.MOVIE_DETAIL -> {
            val movie = selectedMovie
            if (movie != null) {
              MovieDetailScreen(
                movie = movie,
                onPlay = { context -> openForPlayback(context, Destination.MOVIE_DETAIL) },
                onOpenMovie = { nextMovie -> selectedMovie = nextMovie },
                onBack = { destination = Destination.CATALOG },
              )
            } else {
              Catalog()
            }
          }
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
              hideBackButton = showPhoneBottomNav,
              requestSearchFocus = requestSectionSearch,
              onSearchFocusHandled = { requestSectionSearch = false },
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
              onOpenDlhdSoccer = { destination = Destination.DLHD_SOCCER },
              onConsidering = { considered ->
                if (
                  streamCache.find(considered.pageUrl) == null &&
                    prefetchTarget?.pageUrl != considered.pageUrl
                ) {
                  prefetchTarget = considered
                }
              },
              onBack = { destination = Destination.CATALOG },
              hideBackButton = showPhoneBottomNav,
              requestSearchFocus = requestSectionSearch,
              onSearchFocusHandled = { requestSectionSearch = false },
            )
          Destination.DLHD_SOCCER ->
            DlhdSoccerScreen(
              onPlay = { context -> openForPlayback(context, Destination.DLHD_SOCCER) },
              onConsidering = { considered ->
                if (
                  streamCache.find(considered.pageUrl) == null &&
                    prefetchTarget?.pageUrl != considered.pageUrl
                ) {
                  prefetchTarget = considered
                }
              },
              onBack = { destination = Destination.SPORTS },
              hideBackButton = showPhoneBottomNav,
              requestSearchFocus = requestSectionSearch,
              onSearchFocusHandled = { requestSectionSearch = false },
            )
          Destination.IPTV ->
            IptvScreen(
              onPlay = { channel ->
                if (channel.resolveViaBrowser) {
                  openForPlayback(
                    PlaybackContext(
                      pageUrl = channel.url,
                      title = channel.name,
                      subtitle = channel.group,
                      genres = listOf("Live TV"),
                      kindLabel = "LIVE",
                    ),
                    Destination.IPTV,
                  )
                } else {
                  streamFailoverAttempts = 0
                  pendingContext = null
                  browserReturnDestination = Destination.IPTV
                  iptvPlaybackSources = channel.toPlaybackRequests()
                  iptvPlaybackSourceIndex = 0
                  playerMinimized = false
                  streamRequest = iptvPlaybackSources.first()
                  destination = Destination.PLAYER
                }
              },
              onBack = { destination = Destination.CATALOG },
              browseState = iptvBrowseState,
              onBrowseStateChanged = { iptvBrowseState = it },
              hideBackButton = showPhoneBottomNav,
              requestSearchFocus = requestSectionSearch,
              onSearchFocusHandled = { requestSectionSearch = false },
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
                playerMinimized = false
                streamRequest = request.copy(context = pendingContext)
                destination = Destination.PLAYER
              },
            )
          Destination.PLAYER -> {
            // Full-screen player is drawn above this when-block so a minimized session can keep the
            // same ExoPlayer alive over the catalog. An empty request falls back to the home row.
            if (streamRequest == null) {
              destinationState.SaveableStateProvider(Destination.CATALOG) { Catalog() }
            }
          }
        }
      }

      if (showPhoneBottomNav) {
        PhoneBottomNav(
          selected =
            if (requestSectionSearch) PhoneBottomTab.SEARCH else destination.toPhoneBottomTab(),
          onSelect = ::openPhoneTab,
        )
      }
    }

    val activeRequest = streamRequest
    if (
      activeRequest != null &&
        shouldComposeInAppPlayerSession(
          hasStreamRequest = true,
          fullPlayerVisible = destination == Destination.PLAYER && !playerMinimized,
          miniPlayerActive = playerMinimized,
        )
    ) {
      HlsPlayerScreen(
        request = activeRequest,
        minimized = playerMinimized,
        miniPlayerBottomPadding =
          if (!isTelevision && showPhoneBottomNav) {
            PhoneBottomNavContentHeight + 8.dp
          } else {
            16.dp
          },
        // Leaving a catalog title returns to the list it came from, not to the loading page that
        // resolved it. Streams found by hand still step back to the site they were found on.
        onExit = {
          val returnTo = exitDestinationFor(activeRequest)
          clearPlayerSession()
          destination = returnTo
        },
        onMinimize = {
          // In-app mini player is phone-only; TV / Leanback exits via onExit / Back.
          if (!isTelevision) {
            playerMinimized = true
            destination = exitDestinationFor(activeRequest)
          }
        },
        onExpand = {
          playerMinimized = false
          destination = Destination.PLAYER
        },
        onDismissMini = { clearPlayerSession() },
        onPlayNext = { next -> openForPlayback(next, browserReturnDestination) },
        onPrepareNext = { next -> prefetchTarget = next },
        onHandedOver = {
          clearPlayerSession()
          destination = Destination.REMOTE
        },
        // A hand-picked server: IPTV jumps to that backup link; catalog titles re-resolve on
        // the chosen provider. Progress was already saved by the player before this runs.
        onSwitchServer = switchServer@{ serverIndex ->
          playerMinimized = false
          if (browserReturnDestination == Destination.IPTV) {
            val selected = iptvPlaybackSourceAt(iptvPlaybackSources, serverIndex) ?: return@switchServer
            if (selected.first != iptvPlaybackSourceIndex) {
              iptvPlaybackSourceIndex = selected.first
              streamRequest = selected.second
            }
            return@switchServer
          }
          val playbackContext = activeRequest.context ?: return@switchServer
          val target = catalogTargetOf(playbackContext.pageUrl) ?: return@switchServer
          if (providerIndexOf(activeRequest.sourcePageUrl) == serverIndex) return@switchServer
          val providerUrl = providerPageUrl(target, serverIndex) ?: return@switchServer
          streamCache.forget(playbackContext.pageUrl)
          if (prefetched?.first == playbackContext.pageUrl) {
            prefetched = null
          }
          // Failover continues from the chosen site if it cannot produce a stream.
          streamFailoverAttempts = serverIndex
          pendingContext = playbackContext
          browserUrl = providerUrl
          streamRequest = null
          destination = Destination.BROWSER
        },
        // Signed stream addresses can expire or point to an unhealthy edge. Forget the dead
        // address and resolve the title page again, but bound the automatic loop so a genuinely
        // broken title still presents a useful error instead of loading forever.
        onPlaybackFailed = {
          playerMinimized = false
          if (browserReturnDestination == Destination.IPTV) {
            val nextSource =
              nextIptvPlaybackSource(iptvPlaybackSources, iptvPlaybackSourceIndex)
            if (nextSource != null) {
              val (nextSourceIndex, nextRequest) = nextSource
              iptvPlaybackSourceIndex = nextSourceIndex
              streamRequest = nextRequest
              true
            } else {
              false
            }
          } else {
            val playbackContext = activeRequest.context
            playbackContext?.pageUrl?.let(streamCache::forget)
            when (
              streamFailureAction(
                hasPlaybackContext = playbackContext != null,
                completedFailovers = streamFailoverAttempts,
              )
            ) {
              StreamFailureAction.RESOLVE_FRESH_STREAM -> {
                val canonicalPageUrl = requireNotNull(playbackContext).pageUrl
                val nextAttempt = streamFailoverAttempts + 1
                streamFailoverAttempts = nextAttempt
                // The context keeps the address the catalog knows this title by, so watch
                // history and Continue watching stay put no matter who ends up serving it.
                // Only the page the resolver visits moves on to the next provider.
                pendingContext = playbackContext
                browserUrl = nextProviderPageUrl(canonicalPageUrl, nextAttempt) ?: canonicalPageUrl
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
    }

    // Never interrupt playback or stream discovery with an update prompt.
    if (
      (destination != Destination.PLAYER || playerMinimized) &&
        destination != Destination.BROWSER
    ) {
      AppUpdateController()
    }

    // Unlike the update prompt, this one belongs over the player too: someone pairing a phone is
    // most likely doing it because they want to control what is playing right now.
    PairingCodeOverlay()
  }
}
