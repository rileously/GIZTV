package com.giztv.tv.ui

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
import androidx.compose.animation.AnimatedContent
import androidx.compose.animation.core.FastOutSlowInEasing
import androidx.compose.animation.core.tween
import androidx.compose.animation.fadeIn
import androidx.compose.animation.fadeOut
import androidx.compose.animation.scaleIn
import androidx.compose.animation.scaleOut
import androidx.compose.animation.togetherWith
import androidx.compose.ui.draw.alpha
import androidx.compose.ui.focus.FocusDirection
import androidx.compose.ui.focus.focusProperties
import androidx.compose.ui.platform.LocalContext
import androidx.compose.ui.platform.LocalFocusManager
import androidx.compose.runtime.rememberCoroutineScope
import androidx.compose.ui.unit.dp
import com.giztv.tv.data.PlaybackContext
import com.giztv.tv.data.CachedSubtitle
import com.giztv.tv.data.StreamCacheStore
import com.giztv.tv.data.WatchHistoryStore
import com.giztv.tv.data.streamStillLive
import com.giztv.tv.home.isTelevision
import com.giztv.tv.home.resumeContextFor
import com.giztv.tv.link.LinkKey
import com.giztv.tv.link.RemoteUiBridge
import com.giztv.tv.ui.browser.BrowserScreen
import com.giztv.tv.ui.browser.StreamPrefetcher
import com.giztv.tv.ui.browser.streamMimeType
import com.giztv.tv.ui.catalog.CatalogScreen
import com.giztv.tv.ui.catalog.STREAM_PROVIDER_COUNT
import com.giztv.tv.ui.catalog.catalogTargetOf
import androidx.compose.runtime.key
import com.giztv.tv.ui.catalog.nextProviderPageUrl
import com.giztv.tv.ui.catalog.playbackServerOptions
import com.giztv.tv.ui.catalog.providerIdOf
import com.giztv.tv.ui.catalog.providerIndexOf
import com.giztv.tv.ui.catalog.providerPageUrl
import com.giztv.tv.ui.catalog.TmdbMovie
import com.giztv.tv.ui.catalog.TmdbShow
import com.giztv.tv.ui.catalog.MovieDetailScreen
import com.giztv.tv.ui.catalog.PersonDetailScreen
import com.giztv.tv.ui.catalog.TvShowDetailScreen
import android.util.Log
import com.giztv.tv.ui.anime.Anime
import com.giztv.tv.ui.anime.AnimeBrowseState
import com.giztv.tv.ui.anime.AnimeDetailScreen
import com.giztv.tv.ui.anime.AnimeScreen
import com.giztv.tv.ui.anime.animeEpisodeRef
import com.giztv.tv.ui.anime.resolveAnimeEpisode
import com.giztv.tv.ui.drama.ShortDrama
import com.giztv.tv.ui.drama.ShortDramaDetailScreen
import com.giztv.tv.ui.drama.ShortDramaScreen
import com.giztv.tv.ui.drama.resumeEpisodeFor
import com.giztv.tv.ui.drama.shortDramaPlayback
import com.giztv.tv.ui.main.GizTvApp
import com.giztv.tv.ui.iptv.IptvBrowseState
import com.giztv.tv.ui.iptv.IptvScreen
import com.giztv.tv.ui.dlhd.DlhdSoccerScreen
import com.giztv.tv.ui.sports.SportsScreen
import com.giztv.tv.ui.player.HlsPlayerScreen
import androidx.media3.common.Player
import com.giztv.tv.ui.player.ExternalSubtitleTrack
import com.giztv.tv.ui.player.HlsStreamRequest
import com.giztv.tv.ui.player.PlaybackProgressStore
import com.giztv.tv.ui.player.playbackProgressKeyForPage
import com.giztv.tv.ui.player.shouldComposeInAppPlayerSession
import com.giztv.tv.ui.link.PairingCodeOverlay
import com.giztv.tv.ui.link.RemoteScreen
import com.giztv.tv.ui.update.AppUpdateController
import kotlinx.coroutines.launch

private const val SKYFLIX_URL = "https://skyflix.to/"
/** Saved-state key for the catalog shown behind a player that has lost its stream. */
private const val PLAYER_FALLBACK_STATE_KEY = "catalog-behind-player"
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

/** Adds the page being left to a detail history, or starts a fresh history from the catalog. */
internal fun <T> detailHistoryAfterOpen(
  history: List<T>,
  current: T?,
  fromCatalog: Boolean,
): List<T> = if (fromCatalog || current == null) emptyList() else history + current

/** Removes and returns the exact detail page Back should reveal next. */
internal fun <T> detailHistoryAfterBack(history: List<T>): Pair<List<T>, T?> =
  if (history.isEmpty()) history to null else history.dropLast(1) to history.last()

private enum class Destination {
  CATALOG,
  MOVIE_DETAIL,
  PERSON_DETAIL,
  SHOW_DETAIL,
  SHORT_DRAMAS,
  DRAMA_DETAIL,
  ANIME,
  ANIME_DETAIL,
  SPORTS,
  DLHD_SOCCER,
  IPTV,
  REMOTE,
  WEB_HOME,
  BROWSER,
  PLAYER,
}

private data class PersonSelection(val id: Int, val name: String, val isDirector: Boolean = false)

/** A browse-detail page that Back can return to before finally uncovering the catalog. */
private sealed interface CatalogDetailRoute {
  data class Movie(val movie: TmdbMovie) : CatalogDetailRoute

  data class Person(val person: PersonSelection) : CatalogDetailRoute

  data class Show(val show: TmdbShow) : CatalogDetailRoute
}

/** Browse surfaces that keep the phone footer visible (YouTube-style: hide on player/detail). */
private fun Destination.showsPhoneBottomNav(): Boolean =
  when (this) {
    Destination.CATALOG,
    Destination.SHORT_DRAMAS,
    Destination.ANIME,
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
    Destination.ANIME -> PhoneBottomTab.ANIME
    Destination.WEB_HOME -> PhoneBottomTab.WEB
    Destination.IPTV -> PhoneBottomTab.IPTV
    else -> null
  }

@Composable
fun GizTvRoot(
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
  var selectedPerson by remember { mutableStateOf<PersonSelection?>(null) }
  var selectedShow by remember { mutableStateOf<TmdbShow?>(null) }
  var catalogDetailBackStack by remember { mutableStateOf<List<CatalogDetailRoute>>(emptyList()) }
  var selectedDrama by remember { mutableStateOf<ShortDrama?>(null) }
  // The grid a drama was opened from, which is the reel the player swipes along.
  var dramaListing by remember { mutableStateOf<List<ShortDrama>>(emptyList()) }
  var selectedAnime by remember { mutableStateOf<Anime?>(null) }
  var animeBrowseState by remember { mutableStateOf(AnimeBrowseState()) }
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
  /** Bumped to rebuild the browser when a server is chosen by hand rather than by failover. */
  var browserReloadToken by remember { mutableIntStateOf(0) }
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

  /**
   * Stops looking for the title that is already playing.
   *
   * The prefetcher exists to have the *next* thing ready, but it is pointed at a title by the
   * catalog before that title is opened, and nothing used to unpoint it once the player had what it
   * needed. The player destination composes it again, so a fresh WebView opened the same page a
   * second time and ground out the whole resolution again — ads, subtitle catalogs and all —
   * directly behind a film that was still filling its first buffer, competing for the connection it
   * was supposed to be saving. Measured on both a playlist server and a file server: every title
   * resolved exactly twice.
   */
  LaunchedEffect(destination, streamRequest?.context?.pageUrl) {
    if (destination != Destination.PLAYER) return@LaunchedEffect
    val playing = streamRequest?.context?.pageUrl ?: return@LaunchedEffect
    if (prefetchTarget?.pageUrl == playing) prefetchTarget = null
  }

  // One stable handler for the drama destinations and the sports page. Registering a BackHandler
  // inside each screen instead would hand the press that leaves the detail page to the listing page
  // as well, dropping the viewer two levels at once.
  BackHandler(
    enabled =
      destination == Destination.SHORT_DRAMAS ||
        destination == Destination.DRAMA_DETAIL ||
        destination == Destination.ANIME ||
        destination == Destination.ANIME_DETAIL ||
        destination == Destination.SPORTS ||
        destination == Destination.DLHD_SOCCER ||
        destination == Destination.IPTV ||
        destination == Destination.REMOTE
  ) {
    destination =
      when (destination) {
        Destination.DRAMA_DETAIL -> Destination.SHORT_DRAMAS
        Destination.ANIME_DETAIL -> Destination.ANIME
        Destination.DLHD_SOCCER -> Destination.SPORTS
        else -> Destination.CATALOG
      }
  }

  /**
   * Plays a stream found earlier, if it is still there, while the search for a fresh one runs.
   *
   * The loading page is already up by the time this is called, so it races the search rather than
   * delaying it: whichever answers first is the one the viewer gets, and a remembered address that
   * has quietly died costs only the moment spent asking. Naming a [providerId] asks for what that
   * one site last gave up, which is what makes a hand-picked server instant on the second choosing.
   */
  fun raceRememberedStream(context: PlaybackContext, providerId: String? = null) {
    val cached = streamCache.find(context.pageUrl, providerId) ?: return
    scope.launch {
      if (!streamStillLive(cached.url, cached.headers)) {
        streamCache.forget(context.pageUrl, providerId)
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

  fun openStreamProviderPlayback(context: PlaybackContext, returnTo: Destination) {
    streamFailoverAttempts = 0
    playerMinimized = false
    pendingContext = context
    // The context carries the address this title is remembered by, which is not necessarily the
    // provider we ask first. Attempt zero is the head of the list, so reordering the providers
    // actually reorders who gets asked.
    browserUrl = nextProviderPageUrl(context.pageUrl, 0) ?: context.pageUrl
    browserReturnDestination = returnTo
    // Already found while preloading or watching, so the loading page is skipped entirely.
    val ready = prefetched?.takeIf { (pageUrl, _) -> pageUrl == context.pageUrl }?.second
    if (ready != null) {
      prefetchTarget = null
      prefetched = null
      streamRequest = ready.copy(context = context)
      destination = Destination.PLAYER
      return
    }
    val isPrefetching = prefetchTarget?.pageUrl == context.pageUrl
    if (!isPrefetching) {
      prefetchTarget = null
      prefetched = null
    }
    // Stop any floating session while a new title is resolved.
    streamRequest = null
    destination = Destination.BROWSER

    // Whoever answered last time, tried against the page being ground through again.
    raceRememberedStream(context)
  }

  /**
   * Everything that opens a title, including the next episode a finished one rolls into and a
   * resume arriving from the home row.
   *
   * An anime episode is pulled out here rather than handed to the provider race. The address such
   * an episode is remembered by is an identity this app minted, not a page anyone can be asked
   * about, and resolving it by loading the site's own player hands back whichever language that
   * page opens on — the dub. That is what used to undo a viewer's choice of sub the moment one
   * episode ended and the next began.
   */
  fun openForPlayback(context: PlaybackContext, returnTo: Destination) {
    if (animeEpisodeRef(context.pageUrl) == null) {
      openStreamProviderPlayback(context, returnTo)
      return
    }
    streamFailoverAttempts = 0
    playerMinimized = false
    pendingContext = context
    browserReturnDestination = returnTo
    iptvPlaybackSources = emptyList()
    iptvPlaybackSourceIndex = 0
    prefetchTarget = null
    prefetched = null
    scope.launch {
      val resolved =
        runCatching { resolveAnimeEpisode(appContext, context) }
          .onFailure { Log.w("GizTvRoot", "Anime episode could not be resolved", it) }
          .getOrNull()
      if (resolved != null) {
        streamRequest = resolved
        destination = Destination.PLAYER
      } else {
        // No worse than before this path existed: something plays, even if the site picks the
        // language. Deliberately not left silent, because a dead Play now is its own bug.
        openStreamProviderPlayback(context, returnTo)
      }
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
      PhoneBottomTab.ANIME -> {
        requestSectionSearch = false
        destination = Destination.ANIME
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
          Destination.ANIME,
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

  fun currentCatalogDetailRoute(): CatalogDetailRoute? =
    when (destination) {
      Destination.MOVIE_DETAIL -> selectedMovie?.let(CatalogDetailRoute::Movie)
      Destination.PERSON_DETAIL -> selectedPerson?.let(CatalogDetailRoute::Person)
      Destination.SHOW_DETAIL -> selectedShow?.let(CatalogDetailRoute::Show)
      else -> null
    }

  fun showCatalogDetail(route: CatalogDetailRoute) {
    when (route) {
      is CatalogDetailRoute.Movie -> {
        selectedMovie = route.movie
        destination = Destination.MOVIE_DETAIL
      }
      is CatalogDetailRoute.Person -> {
        selectedPerson = route.person
        destination = Destination.PERSON_DETAIL
      }
      is CatalogDetailRoute.Show -> {
        selectedShow = route.show
        destination = Destination.SHOW_DETAIL
      }
    }
  }

  fun openCatalogDetail(route: CatalogDetailRoute, fromCatalog: Boolean = false) {
    catalogDetailBackStack =
      detailHistoryAfterOpen(
        history = catalogDetailBackStack,
        current = currentCatalogDetailRoute(),
        fromCatalog = fromCatalog,
      )
    showCatalogDetail(route)
  }

  fun returnFromCatalogDetail() {
    val (remaining, previous) = detailHistoryAfterBack(catalogDetailBackStack)
    catalogDetailBackStack = remaining
    if (previous == null) {
      destination = Destination.CATALOG
    } else {
      showCatalogDetail(previous)
    }
  }

  @Composable
  fun Catalog() {
    CatalogScreen(
      onPlay = { context -> openForPlayback(context, Destination.CATALOG) },
      onOpenMovie = { movie ->
        openCatalogDetail(CatalogDetailRoute.Movie(movie), fromCatalog = true)
      },
      onOpenShow = { show ->
        openCatalogDetail(CatalogDetailRoute.Show(show), fromCatalog = true)
      },
      onOpenWeb = { destination = Destination.WEB_HOME },
      onOpenShortDramas = { destination = Destination.SHORT_DRAMAS },
      onOpenAnime = { destination = Destination.ANIME },
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
      isActive = destination == Destination.CATALOG,
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
            destination == Destination.MOVIE_DETAIL ||
            destination == Destination.SHOW_DETAIL ||
            destination == Destination.PERSON_DETAIL ||
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
                providerId = providerIdOf(stream.sourcePageUrl),
              )
              if (destination == Destination.BROWSER && pendingContext?.pageUrl == context.pageUrl) {
                prefetchTarget = null
                prefetched = null
                streamRequest = stream.copy(context = context)
                destination = Destination.PLAYER
              }
            },
          )
        }

        val isCatalogActive = destination == Destination.CATALOG
        val isDetailScreen =
          destination == Destination.MOVIE_DETAIL ||
            destination == Destination.SHOW_DETAIL ||
            destination == Destination.PERSON_DETAIL

        if (isCatalogActive || isDetailScreen) {
          Box(
            modifier =
              Modifier.fillMaxSize()
                .alpha(if (isCatalogActive) 1f else 0f)
                .focusProperties { canFocus = isCatalogActive }
          ) {
            destinationState.SaveableStateProvider(Destination.CATALOG) { Catalog() }
          }
        }

        AnimatedContent(
          targetState = destination,
          transitionSpec = {
            val isEnteringDetail =
              targetState in
                listOf(
                  Destination.MOVIE_DETAIL,
                  Destination.SHOW_DETAIL,
                  Destination.PERSON_DETAIL,
                  Destination.DRAMA_DETAIL,
                  Destination.ANIME_DETAIL,
                  Destination.PLAYER,
                  Destination.BROWSER,
                )
            if (isEnteringDetail) {
              (fadeIn(animationSpec = tween(280, easing = FastOutSlowInEasing)) +
                scaleIn(initialScale = 0.95f, animationSpec = tween(280, easing = FastOutSlowInEasing))) togetherWith
                (fadeOut(animationSpec = tween(180)) +
                  scaleOut(targetScale = 0.98f, animationSpec = tween(180)))
            } else {
              (fadeIn(animationSpec = tween(240)) +
                scaleIn(initialScale = 1.03f, animationSpec = tween(240))) togetherWith
                (fadeOut(animationSpec = tween(180)) +
                  scaleOut(targetScale = 0.96f, animationSpec = tween(180)))
            }
          },
          label = "PageMorphTransition",
        ) { targetDestination ->
          when (targetDestination) {
            Destination.CATALOG -> Unit
            Destination.MOVIE_DETAIL -> {
              val movie = selectedMovie
              if (movie != null) {
                MovieDetailScreen(
                  movie = movie,
                  onPlay = { context -> openForPlayback(context, Destination.MOVIE_DETAIL) },
                  onOpenMovie = { nextMovie ->
                    openCatalogDetail(CatalogDetailRoute.Movie(nextMovie))
                  },
                  onOpenPerson = { id, name, isDirector ->
                    openCatalogDetail(
                      CatalogDetailRoute.Person(PersonSelection(id, name, isDirector))
                    )
                  },
                  onConsidering = { considered ->
                    if (streamCache.find(considered.pageUrl) == null && prefetchTarget?.pageUrl != considered.pageUrl) {
                      prefetchTarget = considered
                    }
                  },
                  onBack = ::returnFromCatalogDetail,
                )
              } else {
                Catalog()
              }
            }
            Destination.PERSON_DETAIL -> {
              val person = selectedPerson
              if (person != null) {
                PersonDetailScreen(
                  personId = person.id,
                  personName = person.name,
                  isDirector = person.isDirector,
                  onOpenMovie = { movie ->
                    openCatalogDetail(CatalogDetailRoute.Movie(movie))
                  },
                  onBack = ::returnFromCatalogDetail,
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
                  onConsidering = { considered ->
                    if (streamCache.find(considered.pageUrl) == null && prefetchTarget?.pageUrl != considered.pageUrl) {
                      prefetchTarget = considered
                    }
                  },
                  onBack = ::returnFromCatalogDetail,
                )
              } else {
                Catalog()
              }
            }
            Destination.SHORT_DRAMAS ->
              ShortDramaScreen(
                onOpenDrama = { drama, listing ->
                  selectedDrama = drama
                  dramaListing = listing
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
                  listing = dramaListing,
                )
              } else {
                Catalog()
              }
            }
            Destination.ANIME ->
              AnimeScreen(
                onOpenAnime = { anime ->
                  selectedAnime = anime
                  destination = Destination.ANIME_DETAIL
                },
                onBack = { destination = Destination.CATALOG },
                browseState = animeBrowseState,
                onBrowseStateChanged = { animeBrowseState = it },
                hideBackButton = showPhoneBottomNav,
                requestSearchFocus = requestSectionSearch,
                onSearchFocusHandled = { requestSectionSearch = false },
              )
            Destination.ANIME_DETAIL -> {
              val anime = selectedAnime
              if (anime != null) {
                AnimeDetailScreen(
                  anime = anime,
                  // The episode resolves to a plain HLS playlist on this site, so it goes straight
                  // to the player the way an IPTV channel does rather than through the browser.
                  onPlayEpisode = { request ->
                    streamFailoverAttempts = 0
                    pendingContext = request.context
                    browserReturnDestination = Destination.ANIME_DETAIL
                    iptvPlaybackSources = emptyList()
                    iptvPlaybackSourceIndex = 0
                    playerMinimized = false
                    streamRequest = request
                    destination = Destination.PLAYER
                  },
                  onBack = { destination = Destination.ANIME },
                )
              } else {
                Catalog()
              }
            }
            Destination.REMOTE ->
              RemoteScreen(
                onBack = { destination = Destination.CATALOG },
                onPlayHere = { pageUrl, title, subtitle, posterUrl, positionMs ->
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
              GizTvApp(
                onOpenBrowser = { url ->
                  pendingContext = null
                  browserUrl = url
                  browserReturnDestination = Destination.WEB_HOME
                  destination = Destination.BROWSER
                },
                onOpenMovies = { destination = Destination.CATALOG },
              )
            Destination.BROWSER ->
              // Keyed so that choosing a server actually reloads: the page is only loaded when the
              // web view is built, so changing the address of a browser already on screen would
              // otherwise do nothing.
              key(browserReloadToken) {
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
                  // Movies and series only. Providers are addressed by TMDB id, so a title that is
                  // not one of those has no second server to offer, and playbackServerOptions
                  // answers empty for every other kind — sports, short dramas, anime, IPTV.
                  servers =
                    pendingContext
                      ?.let { playbackServerOptions(it.pageUrl, sourceCount = 1) }
                      .orEmpty(),
                  onSelectServer = { index ->
                    val canonical = pendingContext?.pageUrl ?: return@BrowserScreen
                    // Kept in step with the automatic run so that a hand-picked server is where
                    // the automatic failover carries on from, rather than starting over at SR1.
                    streamFailoverAttempts = index
                    browserUrl = nextProviderPageUrl(canonical, index) ?: canonical
                    browserReloadToken += 1
                  },
                )
              }
            Destination.PLAYER -> {
              if (streamRequest == null) {
                // Its own key, deliberately. The catalog above is provided under
                // Destination.CATALOG, and a transition that composes both at once — which is what
                // AnimatedContent does, and what a player left without a stream lands in — handed
                // SaveableStateHolder the same key twice and brought the whole interface down with
                // "Key CATALOG was used multiple times".
                destinationState.SaveableStateProvider(PLAYER_FALLBACK_STATE_KEY) { Catalog() }
              }
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
        // A reel swipe onto the drama beside this one. The reel travels with the title being
        // played, so the run keeps its neighbours however many dramas the viewer swipes through,
        // and a drama already part-watched opens where it was left rather than back at episode one.
        onPlayReelTitle = { target ->
          val reel = activeRequest.context?.reel.orEmpty()
          openForPlayback(
            target.shortDramaPlayback(
              reel = reel,
              episode =
                resumeEpisodeFor(target.id, target.episodeCount, WatchHistoryStore(appContext).all()),
            ),
            // Back out of the drama that was swiped onto and the listing is the honest place to
            // land: the detail page belongs to whichever drama was opened by hand.
            Destination.SHORT_DRAMAS,
          )
        },
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
          // Only the most-recent entry, which belongs to the server being left. What the chosen
          // one gave up last time is kept, and is what makes coming back to it instant.
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
          // A viewer who picks a server is saying where their titles are, but only the resolve
          // that follows records it: a choice that turns out not to work should not become the
          // place every later title is looked for first.
          raceRememberedStream(playbackContext, providerIdOf(providerUrl))
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
            // The site that served this one is out too: its address failed under playback, so
            // choosing that server again must go looking rather than hand back the same dead one.
            playbackContext?.pageUrl?.let {
              streamCache.forget(it, providerIdOf(activeRequest.sourcePageUrl))
            }
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
