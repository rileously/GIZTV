package com.example.auroratv.ui.catalog

import android.util.Log
import androidx.activity.compose.BackHandler
import androidx.compose.animation.Crossfade
import androidx.compose.animation.animateColorAsState
import androidx.compose.animation.core.animateFloatAsState
import androidx.compose.animation.core.tween
import androidx.compose.foundation.background
import androidx.compose.foundation.border
import androidx.compose.foundation.clickable
import androidx.compose.foundation.gestures.detectTapGestures
import androidx.compose.foundation.focusGroup
import androidx.compose.foundation.layout.Arrangement
import androidx.compose.foundation.layout.Box
import androidx.compose.foundation.layout.BoxWithConstraints
import androidx.compose.foundation.layout.Column
import androidx.compose.foundation.layout.PaddingValues
import androidx.compose.foundation.layout.Row
import androidx.compose.foundation.layout.Spacer
import androidx.compose.foundation.layout.aspectRatio
import androidx.compose.foundation.layout.fillMaxHeight
import androidx.compose.foundation.layout.fillMaxSize
import androidx.compose.foundation.layout.fillMaxWidth
import androidx.compose.foundation.layout.height
import androidx.compose.foundation.layout.padding
import androidx.compose.foundation.layout.size
import androidx.compose.foundation.layout.width
import androidx.compose.foundation.lazy.LazyColumn
import androidx.compose.foundation.lazy.LazyRow
import androidx.compose.foundation.lazy.itemsIndexed
import androidx.compose.foundation.lazy.rememberLazyListState
import androidx.compose.foundation.lazy.grid.GridCells
import androidx.compose.foundation.lazy.grid.GridItemSpan
import androidx.compose.foundation.lazy.grid.LazyVerticalGrid
import androidx.compose.foundation.lazy.grid.items
import androidx.compose.foundation.lazy.grid.rememberLazyGridState
import androidx.compose.foundation.lazy.items
import androidx.compose.foundation.shape.RoundedCornerShape
import androidx.compose.material.icons.Icons
import androidx.compose.material.icons.filled.Close
import androidx.compose.material.icons.filled.Language
import androidx.compose.material.icons.filled.LiveTv
import androidx.compose.material.icons.filled.Search
import androidx.compose.material.icons.filled.SettingsRemote
import androidx.compose.material.icons.filled.SportsBasketball
import androidx.compose.material.icons.filled.Theaters
import androidx.compose.runtime.Composable
import androidx.compose.runtime.DisposableEffect
import androidx.compose.runtime.LaunchedEffect
import androidx.compose.runtime.getValue
import androidx.compose.runtime.mutableStateOf
import androidx.compose.runtime.remember
import androidx.compose.runtime.rememberCoroutineScope
import androidx.compose.runtime.saveable.rememberSaveable
import androidx.compose.runtime.setValue
import androidx.compose.runtime.snapshotFlow
import androidx.compose.runtime.withFrameNanos
import androidx.compose.ui.Alignment
import androidx.compose.ui.Modifier
import androidx.compose.ui.draw.clip
import androidx.compose.ui.focus.FocusRequester
import androidx.compose.ui.focus.focusProperties
import androidx.compose.ui.focus.focusRequester
import androidx.compose.ui.focus.onFocusChanged
import androidx.compose.ui.graphics.Brush
import androidx.compose.ui.graphics.Color
import androidx.compose.ui.graphics.graphicsLayer
import androidx.compose.ui.input.key.Key
import androidx.compose.ui.input.key.KeyEventType
import androidx.compose.ui.input.key.key
import androidx.compose.ui.input.key.onPreviewKeyEvent
import androidx.compose.ui.input.key.type
import androidx.compose.ui.input.pointer.pointerInput
import androidx.compose.ui.platform.LocalContext
import androidx.compose.ui.platform.LocalFocusManager
import androidx.compose.ui.platform.LocalSoftwareKeyboardController
import androidx.compose.ui.semantics.contentDescription
import androidx.compose.ui.semantics.role
import androidx.compose.ui.semantics.semantics
import androidx.compose.ui.text.font.FontWeight
import androidx.compose.ui.text.style.TextOverflow
import androidx.compose.ui.unit.dp
import androidx.compose.ui.unit.sp
import androidx.tv.material3.Text
import com.example.auroratv.BuildConfig
import com.example.auroratv.data.LibraryItem
import com.example.auroratv.data.LibraryKind
import com.example.auroratv.data.MyListStore
import com.example.auroratv.data.PlaybackContext
import com.example.auroratv.data.UiPreferencesStore
import com.example.auroratv.data.WatchHistoryEntry
import com.example.auroratv.data.WatchHistoryStore
import com.example.auroratv.home.refreshHomeSurfaces
import com.example.auroratv.link.RemoteUiBridge
import com.example.auroratv.theme.AuroraMint
import com.example.auroratv.theme.DeepSpace
import com.example.auroratv.theme.MutedBlue
import com.example.auroratv.theme.NightSurface
import com.example.auroratv.theme.SoftWhite
import com.example.auroratv.ui.player.PlaybackProgressStore
import java.net.SocketTimeoutException
import java.net.UnknownHostException
import javax.net.ssl.SSLException
import kotlinx.coroutines.async
import kotlinx.coroutines.awaitAll
import kotlinx.coroutines.coroutineScope
import kotlinx.coroutines.delay
import kotlinx.coroutines.Job
import kotlinx.coroutines.launch
import kotlinx.coroutines.sync.Semaphore
import kotlinx.coroutines.sync.withPermit

/** Long enough that a word typed at speed is one request, short enough to feel immediate. */
private const val SEARCH_DEBOUNCE_MS = 350L

/**
 * Rails fetched before the viewer has scrolled anywhere.
 *
 * Enough to fill the first screen and a little past it. Asking for all eighteen up front was the
 * whole of the wait on a cold start: the screen sat on "Loading…" until the slowest of them
 * answered, however long ago the first one had.
 */
private const val EAGER_RAILS = 4

/** How far past the last visible rail to fetch, so scrolling meets posters rather than shimmer. */
private const val RAIL_LOOKAHEAD = 3

/**
 * Rails allowed to be fetching at once.
 *
 * They all go to the same host over `HttpURLConnection`, so eighteen at once queued for the
 * connection pool regardless — but with the rail under the viewer's eyes as likely to be last in
 * that queue as first.
 */
private const val RAIL_CONCURRENCY = 4

/** Long enough to read as a fade, short enough that nobody waits on the animation itself. */
private const val RAIL_FADE_MS = 260

/** One rail on the page: its listing, and how much of it is known so far. */
private data class RailSlot(val category: CatalogCategory, val size: Int, val pending: Boolean)

/**
 * The listings this session already holds for [tab], read without suspending.
 *
 * The point is that it can be done while composing. Everything else about a rail arrives through a
 * coroutine, which is a frame too late to stop the placeholder being drawn first.
 */
private fun <T : Any> heldRails(
  tab: CatalogTab,
  categories: List<CatalogCategory>,
): Map<CatalogCategory, List<T>> = buildMap {
  categories.forEach { category ->
    CatalogCache.peek<List<T>>(catalogCacheKey(tab, category))?.let { put(category, it.value) }
  }
}

internal enum class CatalogTab(val label: String) {
  MOVIES("Movies"),
  SHOWS("TV Shows"),
  MY_LIST("My List"),
}

@Composable
internal fun CatalogScreen(
  onPlay: (PlaybackContext) -> Unit,
  onOpenShow: (TmdbShow) -> Unit,
  onOpenWeb: () -> Unit,
  onOpenShortDramas: () -> Unit,
  onOpenSports: () -> Unit,
  onOpenIptv: () -> Unit,
  /** Absent on a television, which is the thing being pointed at rather than the thing pointing. */
  onOpenRemote: (() -> Unit)? = null,
  /** A title the viewer has paused on, worth finding the stream for before they ask. */
  onConsidering: (PlaybackContext) -> Unit = {},
  /**
   * Leanback keeps Sports / Shorts / Web / IPTV in the top bar. On phone those live in the
   * bottom footer, so the header only keeps the wordmark, compact tabs, and Remote — search
   * expands on demand from the header icon or footer Search.
   */
  showTopDestinationActions: Boolean = true,
  /** When true, expand and focus the catalog search field (phone bottom-nav Search). */
  requestSearchFocus: Boolean = false,
  onSearchFocusHandled: () -> Unit = {},
  modifier: Modifier = Modifier,
) {
  val context = LocalContext.current
  val movieRepository = remember { TmdbMovieRepository(BuildConfig.TMDB_API_KEY) }
  val tvRepository = remember { TmdbTvRepository(BuildConfig.TMDB_API_KEY) }
  val historyStore = remember(context) { WatchHistoryStore(context) }
  val playbackProgressStore = remember(context) { PlaybackProgressStore(context) }
  val myListStore = remember(context) { MyListStore(context) }
  val uiPreferences = remember(context) { UiPreferencesStore(context) }
  val scope = rememberCoroutineScope()
  val focusManager = LocalFocusManager.current
  val keyboardController = LocalSoftwareKeyboardController.current
  val openWebFocusRequester = remember { FocusRequester() }
  val shortDramasFocusRequester = remember { FocusRequester() }
  val sportsFocusRequester = remember { FocusRequester() }
  val iptvFocusRequester = remember { FocusRequester() }
  val firstTabFocusRequester = remember { FocusRequester() }
  val searchFieldFocusRequester = remember { FocusRequester() }
  val searchButtonFocusRequester = remember { FocusRequester() }
  val continueRowFocusRequester = remember { FocusRequester() }
  val gridFocusRequester = remember { FocusRequester() }
  // One per listing, so a press of down lands on the next rail rather than wherever focus search
  // decides — the same reason every other list on this screen names its own neighbours.
  // Worked out once: a decade rail depends on the current year, and recomposing must not shuffle
  // the rails under a viewer who is part-way along one.
  val categories = remember { catalogCategories() }
  val railFocusRequesters = remember(categories) { List(categories.size) { FocusRequester() } }
  val gridState = rememberLazyGridState()
  val railState = rememberLazyListState()
  // Phone footer owns destinations; hide the always-on search row until asked.
  val phoneChrome = !showTopDestinationActions

  var tab by rememberSaveable {
    mutableStateOf(CatalogTab.entries.firstOrNull { it.name == uiPreferences.lastTab() } ?: CatalogTab.MOVIES)
  }
  var query by rememberSaveable { mutableStateOf("") }
  var searchActive by rememberSaveable { mutableStateOf(false) }
  var searchExpanded by rememberSaveable { mutableStateOf(false) }
  // Every listing is on the page at once, one rail each, rather than behind a filter.
  //
  // Seeded from the cache while composing rather than left empty for a coroutine to fill. Opening a
  // title tears this screen down — the catalog is one branch of a `when` — so coming back rebuilds
  // it from nothing, and rails that were on screen a moment ago would drop to placeholders and fade
  // back in. The listings were never gone; only the state holding them was.
  var movieSections by remember { mutableStateOf(heldRails<TmdbMovie>(CatalogTab.MOVIES, categories)) }
  var showSections by remember { mutableStateOf(heldRails<TmdbShow>(CatalogTab.SHOWS, categories)) }
  var movies by remember { mutableStateOf<List<TmdbMovie>>(emptyList()) }
  var shows by remember { mutableStateOf<List<TmdbShow>>(emptyList()) }
  var savedItems by remember { mutableStateOf<List<LibraryItem>>(emptyList()) }
  var watchHistory by remember { mutableStateOf<List<WatchHistoryEntry>>(emptyList()) }
  var continueWatching by remember { mutableStateOf<List<WatchHistoryEntry>>(emptyList()) }
  var recommendedMovies by remember { mutableStateOf<List<TmdbMovie>>(emptyList()) }
  var recommendedShows by remember { mutableStateOf<List<TmdbShow>>(emptyList()) }
  var recommendationSeeds by remember { mutableStateOf<List<RecommendationSeed>>(emptyList()) }
  // Whoever was top-billed in the last thing watched, and the rest of their work.
  var featuredActor by remember { mutableStateOf<TmdbActor?>(null) }
  var actorMovies by remember { mutableStateOf<List<TmdbMovie>>(emptyList()) }
  var actorShows by remember { mutableStateOf<List<TmdbShow>>(emptyList()) }
  val recommendedFocusRequester = remember { FocusRequester() }
  val actorFocusRequester = remember { FocusRequester() }
  var loading by remember { mutableStateOf(true) }
  var errorMessage by remember { mutableStateOf<String?>(null) }
  var confirmingHistoryClear by rememberSaveable { mutableStateOf(false) }
  /** How far down the listings have been asked for; grows as the viewer scrolls towards them. */
  var railsRequested by remember { mutableStateOf(EAGER_RAILS) }
  /**
   * Rails already asked for, keyed by tab.
   *
   * The lookahead re-runs on every scroll, so without this a rail already in flight would be
   * started again on each frame the list moved.
   */
  val railsStarted = remember { mutableSetOf<String>() }
  val railGate = remember { Semaphore(RAIL_CONCURRENCY) }

  fun dismissKeyboard() {
    focusManager.clearFocus()
    keyboardController?.hide()
  }

  fun expandSearchUi() {
    searchExpanded = true
  }

  /**
   * Fetches one rail, putting whatever is already in hand on screen before the request goes out.
   *
   * Up to three answers arrive for the same rail, each replacing the last in place: the copy this
   * session already holds, the copy the previous run left on disk, and what TMDB sends back. The
   * rail fills in as they land instead of staying blank until the last of them.
   */
  suspend fun <T : Any> loadRail(
    key: String,
    label: String,
    stored: suspend () -> List<T>?,
    fetch: suspend () -> List<T>,
    /** What this rail already holds, and null while it has never answered. */
    held: () -> List<T>?,
    publish: (List<T>) -> Unit,
    /** Whether any rail on this tab has titles in it, which decides if a failure is worth saying. */
    anyContent: () -> Boolean,
  ) {
    val cached = CatalogCache.peek<List<T>>(key)
    if (cached != null) {
      publish(cached.value)
      // Fetched minutes ago, and a listing does not move that fast: a request whose result nobody
      // would notice is a request worth not making.
      if (cached.fresh) return
    } else {
      // Costs no network, so it is worth trying even on a good connection — the posters land a
      // beat later rather than a round trip later.
      runCatching { stored() }.getOrNull()?.takeIf { it.isNotEmpty() }?.let(publish)
    }

    runCatching { railGate.withPermit { CatalogCache.fetch(key, fetch) } }
      .onSuccess {
        publish(it)
        if (it.isNotEmpty()) errorMessage = null
      }
      .onFailure { error ->
        // Leaving the screen cancels this mid-flight, which is ordinary and not worth reporting.
        if (error is kotlinx.coroutines.CancellationException) throw error
        Log.e("GizTvTmdb", "TMDB rail $label failed", error)
        // A rail that will not load simply does not appear. Only when none of them will is there
        // anything the viewer can act on, and only then is it said.
        if (!anyContent()) errorMessage = friendlyCatalogError(error)
        // Marked answered either way, so the placeholder stops shimmering at something that is
        // never coming.
        if (held() == null) publish(emptyList())
      }
  }

  /** Starts every rail down to [upTo] that has not been asked for yet. */
  fun requestRails(activeTab: CatalogTab, upTo: Int) {
    if (activeTab == CatalogTab.MY_LIST) return
    categories.take(upTo.coerceIn(EAGER_RAILS, categories.size)).forEach { category ->
      val key = catalogCacheKey(activeTab, category)
      if (!railsStarted.add(key)) return@forEach
      scope.launch {
        if (activeTab == CatalogTab.MOVIES) {
          loadRail(
            key = key,
            label = category.id,
            stored = { movieRepository.storedMovies(category) },
            fetch = { movieRepository.movies(category) },
            held = { movieSections[category] },
            publish = { movieSections = movieSections + (category to it) },
            anyContent = { movieSections.values.any { items -> items.isNotEmpty() } },
          )
        } else {
          loadRail(
            key = key,
            label = category.id,
            stored = { tvRepository.storedShows(category) },
            fetch = { tvRepository.shows(category) },
            held = { showSections[category] },
            publish = { showSections = showSections + (category to it) },
            anyContent = { showSections.values.any { items -> items.isNotEmpty() } },
          )
        }
      }
    }
  }

  /** The views that are one answer rather than a page of rails: a search, and My List. */
  suspend fun runLoad(activeTab: CatalogTab, searchQuery: String?) {
    loading = true
    errorMessage = null
    runCatching {
          when {
            activeTab == CatalogTab.MY_LIST -> savedItems = myListStore.all()
            activeTab == CatalogTab.MOVIES ->
              movies = movieRepository.searchMovies(searchQuery.orEmpty())
            else -> shows = tvRepository.searchShows(searchQuery.orEmpty())
          }
        }
        .onFailure {
          Log.e("GizTvTmdb", "TMDB ${activeTab.name} load failed", it)
          errorMessage = friendlyCatalogError(it)
        }
    loading = false
  }

  fun load(activeTab: CatalogTab, searchQuery: String?) {
    if (searchQuery.isNullOrBlank() && activeTab != CatalogTab.MY_LIST) {
      // The rails carry their own waiting, one placeholder each, so there is nothing here to hold
      // the whole screen on.
      loading = false
      errorMessage = null
      requestRails(activeTab, railsRequested)
      return
    }
    scope.launch { runLoad(activeTab, searchQuery) }
  }

  /** Throws away what failed and asks again, from the top. */
  fun retryRails(activeTab: CatalogTab) {
    errorMessage = null
    CatalogCache.clear()
    railsStarted.clear()
    // A rail that failed is held as an empty answer, which is how it stops shimmering. Dropping
    // those puts it back to unanswered, so the retry shows placeholders filling in rather than a
    // blank page with nothing on it at all.
    movieSections = movieSections.filterValues { it.isNotEmpty() }
    showSections = showSections.filterValues { it.isNotEmpty() }
    requestRails(activeTab, railsRequested)
  }

  fun collapseSearchUi() {
    searchExpanded = false
    if (query.isNotBlank() || searchActive) {
      query = ""
      searchActive = false
      errorMessage = null
      load(tab, null)
    }
    dismissKeyboard()
  }

  fun selectTab(next: CatalogTab) {
    if (next == tab) return
    tab = next
    query = ""
    searchActive = false
    searchExpanded = false
    errorMessage = null
    uiPreferences.setLastTab(next.name)
    load(next, null)
  }

  fun runSearch() {
    // Nothing typed yet, so Search means "let me type": the field takes focus and the keyboard
    // comes up on purpose, rather than the moment focus passes through the row.
    if (query.isBlank()) {
      searchExpanded = true
      searchFieldFocusRequester.requestFocus()
      return
    }
    // Results are already arriving as the viewer types, so this only puts the keyboard away.
    keyboardController?.hide()
    focusManager.clearFocus()
    searchButtonFocusRequester.requestFocus()
  }

  LaunchedEffect(requestSearchFocus) {
    if (!requestSearchFocus) return@LaunchedEffect
    searchExpanded = true
    // One frame so the search row is placed after a destination change into this screen.
    withFrameNanos {}
    runCatching { searchFieldFocusRequester.requestFocus() }
    onSearchFocusHandled()
  }

  BackHandler(enabled = phoneChrome && searchExpanded) { collapseSearchUi() }

  // Lent to a paired phone so a search typed on a real keyboard lands in the box rather than
  // arriving as a stream of key presses aimed at whatever happens to be focused. The television's
  // own keyboard stays down: the typing is happening in someone's hand, not on the screen.
  DisposableEffect(Unit) {
    RemoteUiBridge.typeIntoSearch = { typed ->
      query = typed
      load(tab, typed.takeIf(String::isNotBlank))
    }
    onDispose { RemoteUiBridge.typeIntoSearch = null }
  }

  /**
   * Searches as the viewer types.
   *
   * Each keystroke restarts this effect, which cancels the wait and any request already in
   * flight — so results can never arrive out of order, and a fast typist costs one request rather
   * than one per letter. An emptied box falls straight back to the rails, which are still in hand.
   */
  LaunchedEffect(query, tab) {
    val trimmed = query.trim()
    if (trimmed.isBlank()) {
      // Only when a search is being abandoned. On the first composition there is nothing to undo,
      // and clearing the flag then would cut the opening load short.
      if (searchActive) {
        searchActive = false
        errorMessage = null
        loading = false
        // Phone: clearing results collapses the expandable field.
        if (phoneChrome) searchExpanded = false
      }
      return@LaunchedEffect
    }
    if (phoneChrome) searchExpanded = true
    delay(SEARCH_DEBOUNCE_MS)
    // Set before the request, not after it. Cancellation is cooperative, so a superseded search
    // can still be finishing its last steps, and an assignment made after the await would switch
    // the results back on just as an emptied box had switched them off.
    searchActive = true
    runLoad(tab, trimmed)
  }

  // The likeliest thing anyone is about to press, warmed without being asked to dwell on it. One
  // title per visit to the catalog, and skipped when its stream is already known.
  LaunchedEffect(continueWatching.firstOrNull()?.pageUrl) {
    val first = continueWatching.firstOrNull() ?: return@LaunchedEffect
    onConsidering(first.toPlaybackContext())
  }

  // Re-read on every entry so progress and saved titles reflect what just happened in the player.
  LaunchedEffect(Unit) {
    watchHistory = historyStore.all()
    continueWatching = historyStore.continueWatching()
    load(tab, null)
    firstTabFocusRequester.requestFocus()
  }

  /**
   * A rail built from what this viewer has actually watched.
   *
   * It loads on its own rather than as part of [runLoad], so a slow or empty answer never holds up
   * the listings everyone gets. Someone with no history yet simply sees the fixed rails.
   */
  LaunchedEffect(tab, searchActive, watchHistory) {
    if (searchActive || tab == CatalogTab.MY_LIST) return@LaunchedEffect
    val forShows = tab == CatalogTab.SHOWS
    val watched = watchHistory
    val seeds = recommendationSeeds(watched, forShows = forShows)
    recommendationSeeds = seeds
    if (seeds.isEmpty()) {
      if (forShows) recommendedShows = emptyList() else recommendedMovies = emptyList()
      return@LaunchedEffect
    }
    val watchedIds =
      watched.mapNotNull { if (forShows) it.showId else tmdbMovieIdFromPageUrl(it.pageUrl) }.toSet()
    runCatching {
        coroutineScope {
          // One seed failing is not the rail failing; the others still have answers.
          if (forShows) {
            val answers =
              seeds
                .map { seed ->
                  async { runCatching { tvRepository.recommendations(seed.id) }.getOrDefault(emptyList()) }
                }
                .awaitAll()
            recommendedShows = mergeRecommendations(answers, TmdbShow::id, watchedIds)
          } else {
            val answers =
              seeds
                .map { seed ->
                  async { runCatching { movieRepository.recommendations(seed.id) }.getOrDefault(emptyList()) }
                }
                .awaitAll()
            recommendedMovies = mergeRecommendations(answers, TmdbMovie::id, watchedIds)
          }
        }
      }
      .onFailure { error ->
        if (error is kotlinx.coroutines.CancellationException) throw error
        Log.e("GizTvTmdb", "Recommendations failed", error)
      }
  }

  /**
   * The rest of a familiar face's work.
   *
   * Seeded from the most recent thing watched rather than all of them, because this rail is about
   * one person: blending the leads of three different films gives a row about nobody. Loaded on its
   * own for the same reason the recommendations are — two requests deep, and worth none of the
   * viewer's waiting if the answer is empty.
   */
  LaunchedEffect(tab, searchActive, watchHistory) {
    if (searchActive || tab == CatalogTab.MY_LIST) return@LaunchedEffect
    val forShows = tab == CatalogTab.SHOWS
    val seed = recommendationSeeds(watchHistory, forShows = forShows, limit = 1).firstOrNull()
    if (seed == null) {
      featuredActor = null
      if (forShows) actorShows = emptyList() else actorMovies = emptyList()
      return@LaunchedEffect
    }
    val watchedIds =
      watchHistory
        .mapNotNull { if (forShows) it.showId else tmdbMovieIdFromPageUrl(it.pageUrl) }
        .toSet()
    runCatching {
        val actor =
          if (forShows) tvRepository.topBilledActor(seed.id) else movieRepository.topBilledActor(seed.id)
        if (actor == null) {
          featuredActor = null
          return@runCatching
        }
        if (forShows) {
          val found = tvRepository.showsWithActor(actor.id).filter { it.id !in watchedIds }
          actorShows = found
          featuredActor = actor.takeIf { found.isNotEmpty() }
        } else {
          val found = movieRepository.moviesWithActor(actor.id).filter { it.id !in watchedIds }
          actorMovies = found
          featuredActor = actor.takeIf { found.isNotEmpty() }
        }
      }
      .onFailure { error ->
        // Leaving the screen cancels this mid-flight, which is ordinary and not worth reporting.
        if (error is kotlinx.coroutines.CancellationException) throw error
        Log.e("GizTvTmdb", "Cast rail failed", error)
        featuredActor = null
      }
  }

  // Switching tab should start at the top, not wherever the last list was scrolled to. Arriving on
  // the screen is not switching, though — and every return from a title arrives, because opening one
  // tears this screen down. Sending the viewer back to the top of the page they had scrolled through
  // is most of what made coming back feel like a reload.
  var arrived by remember { mutableStateOf(false) }
  LaunchedEffect(tab, searchActive) {
    if (!arrived) {
      arrived = true
      return@LaunchedEffect
    }
    if (gridState.layoutInfo.totalItemsCount > 0) gridState.scrollToItem(0)
    if (railState.layoutInfo.totalItemsCount > 0) railState.scrollToItem(0)
  }

  val browsing = tab != CatalogTab.MY_LIST
  // Even completed-only history needs a reachable way to be cleared; in that case the row contains
  // just the clear action and is titled "Watch history".
  val showContinueRow = watchHistory.isNotEmpty() && !searchActive
  // Rails carry the browsable listings; a search and My List are a plain grid of one answer.
  val showRails = browsing && !searchActive
  // A category missing from the map has not answered yet, and holds a placeholder rather than
  // being left out — otherwise every rail that lands shifts the ones under it down the page.
  val sections: List<RailSlot> =
    if (!showRails) emptyList()
    else
      categories
        .map { category ->
          val loaded =
            if (tab == CatalogTab.MOVIES) movieSections[category] else showSections[category]
          RailSlot(category = category, size = loaded?.size ?: 0, pending = loaded == null)
        }
        // Only a rail that answered with nothing is dropped.
        .filter { it.pending || it.size > 0 }
  val itemCount =
    when {
      showRails -> sections.sumOf { it.size }
      tab == CatalogTab.MOVIES -> movies.size
      tab == CatalogTab.SHOWS -> shows.size
      else -> savedItems.size
    }
  val heading =
    when {
      searchActive -> "Results for “${query.trim()}”"
      else -> "My List"
    }
  val recommended = if (tab == CatalogTab.MOVIES) recommendedMovies.size else recommendedShows.size
  val showRecommendedRail = showRails && recommended > 0
  val actorItems = if (tab == CatalogTab.MOVIES) actorMovies.size else actorShows.size
  val showActorRail = showRails && featuredActor != null && actorItems > 0
  // Rails past the fold are not composed, and neither a FocusRequester nor focus search can reach a
  // node that does not exist yet. So the move is driven: scroll the neighbour into view first, then
  // hand it the focus. Hoisted because the personalized rail moves into the fixed ones too.
  val leadingRailItems =
    (if (showContinueRow) 1 else 0) +
      (if (showRecommendedRail) 1 else 0) +
      (if (showActorRail) 1 else 0)
  // One move at a time. Holding the pad down used to start a fresh animation per press, each
  // cancelling the last part-way and each racing the scrolling that focus does on its own, which
  // left the list parked somewhere between two rails with no way back up.
  val railMove = remember { mutableStateOf<Job?>(null) }
  val moveToRail: (Int) -> Unit = { target ->
    railMove.value?.cancel()
    railMove.value =
      scope.launch {
        val index = target + leadingRailItems
        // A rail already on screen needs no scrolling of ours: focus brings itself into view, and
        // animating on top of that is the fight rather than the smoothness.
        val onScreen = railState.layoutInfo.visibleItemsInfo.any { it.index == index }
        if (!onScreen) railState.animateScrollToItem(index)
        runCatching { railFocusRequesters[target].requestFocus() }
          .onFailure {
            // One frame later the rail has certainly been placed.
            withFrameNanos {}
            runCatching { railFocusRequesters[target].requestFocus() }
          }
      }
    Unit
  }
  /**
   * Asks for the rails the viewer is about to reach.
   *
   * The lookahead is what keeps this invisible: fetching starts a few rails before the one being
   * scrolled towards, so the placeholder is usually gone by the time it comes into view. Nothing
   * is ever un-asked-for again, hence the running high-water mark rather than a window.
   */
  LaunchedEffect(tab, searchActive) {
    if (searchActive || tab == CatalogTab.MY_LIST) return@LaunchedEffect
    requestRails(tab, railsRequested)
    snapshotFlow { railState.layoutInfo.visibleItemsInfo.lastOrNull()?.index ?: 0 }
      .collect { lastVisible ->
        // Rail indices sit past the personalised rows at the top of the list; only a rail that
        // answered with nothing separates this from the position in [categories], and the
        // lookahead swallows that drift.
        val reach = lastVisible - leadingRailItems + 1 + RAIL_LOOKAHEAD
        if (reach > railsRequested) railsRequested = reach
        requestRails(tab, railsRequested)
      }
  }

  // The first rail that has actually answered. A placeholder holds no card, and a FocusRequester
  // for a node that was never attached throws when something is sent to it — so the chrome and the
  // personalised rails aim here rather than at position zero, which may still be shimmering.
  val firstLoadedRail = sections.indexOfFirst { !it.pending }.coerceAtLeast(0)
  val firstRailFocusRequester = railFocusRequesters[firstLoadedRail]
  val firstBodyFocusRequester =
    when {
      showContinueRow -> continueRowFocusRequester
      showRails -> firstRailFocusRequester
      else -> gridFocusRequester
    }

  BoxWithConstraints(
    modifier =
      modifier.fillMaxSize().pointerInput(Unit) { detectTapGestures { dismissKeyboard() } }
        .background(
          Brush.radialGradient(
            colors = listOf(Color(0xFF17345C), DeepSpace),
            radius = 1_300f,
            center = androidx.compose.ui.geometry.Offset(1_300f, 180f),
          )
        )
  ) {
    val narrow = maxWidth < 600.dp
    val compact = maxHeight < 600.dp
    val phoneDense = phoneChrome && narrow
    // Phone: search stays collapsed until the header icon or footer Search opens it. TV keeps
    // the leanback field always available for the remote.
    val showSearchRow = browsing && (!phoneChrome || searchExpanded || searchActive)
    val chromeDown =
      when {
        showSearchRow -> searchButtonFocusRequester
        itemCount > 0 || showContinueRow -> firstBodyFocusRequester
        else -> FocusRequester.Default
      }
    // The inset belongs to the things that do not scroll. Putting it on this column boxed every
    // rail inside it too, so the card at the right edge was clipped by an invisible line instead of
    // running under the edge of the screen the way a rail is supposed to.
    val edge = if (narrow) 14.dp else 42.dp
    val edgeOnly = Modifier.padding(horizontal = edge)
    Column(
      modifier =
        Modifier.fillMaxSize().padding(
          vertical =
            when {
              phoneDense -> 8.dp
              compact -> 14.dp
              else -> 22.dp
            }
        )
    ) {
      CatalogTopBar(
        modifier = edgeOnly,
        narrow = narrow,
        compact = compact || phoneDense,
        phoneDense = phoneDense,
        showDestinationActions = showTopDestinationActions,
        onOpenWeb = { dismissKeyboard(); onOpenWeb() },
        onOpenShortDramas = { dismissKeyboard(); onOpenShortDramas() },
        onOpenSports = { dismissKeyboard(); onOpenSports() },
        onOpenIptv = { dismissKeyboard(); onOpenIptv() },
        onOpenRemote = onOpenRemote?.let { open -> { dismissKeyboard(); open() } },
        onOpenSearch =
          if (phoneChrome && browsing && !showSearchRow) {
            {
              expandSearchUi()
              scope.launch {
                withFrameNanos {}
                runCatching { searchFieldFocusRequester.requestFocus() }
              }
            }
          } else {
            null
          },
        onCloseSearch =
          if (phoneChrome && showSearchRow) {
            { collapseSearchUi() }
          } else {
            null
          },
        openWebModifier =
          Modifier.focusRequester(openWebFocusRequester).focusProperties {
            left = shortDramasFocusRequester
            down = chromeDown
          },
        shortDramasModifier =
          Modifier.focusRequester(shortDramasFocusRequester).focusProperties {
            left = iptvFocusRequester
            right = openWebFocusRequester
            down = chromeDown
          },
        iptvModifier =
          Modifier.focusRequester(iptvFocusRequester).focusProperties {
            left = sportsFocusRequester
            right = shortDramasFocusRequester
            down = chromeDown
          },
        sportsModifier =
          Modifier.focusRequester(sportsFocusRequester).focusProperties {
            left = firstTabFocusRequester
            right = iptvFocusRequester
            down = chromeDown
          },
        tabs = {
          SegmentedTabs(
            labels = CatalogTab.entries.map { it.label },
            selectedIndex = CatalogTab.entries.indexOf(tab),
            onSelect = { selectTab(CatalogTab.entries[it]) },
            firstTabFocusRequester = firstTabFocusRequester,
            down = chromeDown,
            compact = phoneDense,
          )
        },
        search =
          if (!showSearchRow) null
          else {
            {
              CatalogSearchRow(
                narrow = narrow,
                query = query,
                placeholder = if (tab == CatalogTab.MOVIES) "Search movies…" else "Search shows…",
                onQueryChanged = { query = it },
                onSearch = ::runSearch,
                searchFieldFocusRequester = searchFieldFocusRequester,
                searchButtonFocusRequester = searchButtonFocusRequester,
                tabFocusRequester = firstTabFocusRequester,
                bodyFocusRequester =
                  firstBodyFocusRequester.takeIf { itemCount > 0 || showContinueRow },
              )
            }
          },
      )
      Spacer(
        modifier.height(
          when {
            phoneDense -> 6.dp
            compact -> 10.dp
            else -> 16.dp
          }
        )
      )

      when {
        // Nothing at all came back, on any rail: the network is the problem, and a page of
        // placeholders that will never fill would be a lie about it.
        showRails && errorMessage != null && itemCount == 0 ->
          StatusPanel(
            message = errorMessage ?: "Content could not be loaded",
            modifier = edgeOnly.weight(1f),
            actionLabel = "Try again",
            onAction = { retryRails(tab) },
          )
        // Otherwise the rails go up straight away, each waiting for itself. There is no screen-wide
        // loading panel here any more: it was one wait as long as the slowest of eighteen requests,
        // with nothing to look at meanwhile.
        showRails ->
          LazyColumn(
            state = railState,
            // The focus group is what lets a press of down reach a rail that has not been composed
            // yet: focus search asks the list to bring it into view first.
            modifier = Modifier.weight(1f).fillMaxWidth().focusGroup(),
            contentPadding = PaddingValues(bottom = if (phoneDense) 8.dp else 22.dp),
            verticalArrangement =
              Arrangement.spacedBy(
                when {
                  phoneDense -> 10.dp
                  compact -> 12.dp
                  else -> 20.dp
                }
              ),
          ) {
            if (showContinueRow) {
              item {
                ContinueWatchingSection(
                  entries = continueWatching,
                  onResume = { entry -> onPlay(entry.toPlaybackContext()) },
                  onClearHistory = { confirmingHistoryClear = true },
                  firstCardFocusRequester = continueRowFocusRequester,
                  up = searchButtonFocusRequester,
                  down =
                    when {
                      showRecommendedRail -> recommendedFocusRequester
                      showActorRail -> actorFocusRequester
                      else -> firstRailFocusRequester
                    },
                  hasGrid = sections.isNotEmpty() || showRecommendedRail || showActorRail,
                  edge = edge,
                )
              }
            }

            // Above the fixed listings, because it is the one rail built for this viewer.
            if (showRecommendedRail) {
              item(key = "recommended") {
                val up = if (showContinueRow) continueRowFocusRequester else searchButtonFocusRequester
                if (tab == CatalogTab.MOVIES) {
                  CatalogRail(
                    heading = recommendationHeading(recommendationSeeds),
                    items = recommendedMovies,
                    key = { it.id },
                    narrow = narrow,
                    attribution = false,
                    firstCardFocusRequester = recommendedFocusRequester,
                    up = up,
                    down = null,
                    onMoveDown = { if (showActorRail) actorFocusRequester.requestFocus() else moveToRail(firstLoadedRail) },
                    onMoveUp = null,
                  ) { movie, cardModifier ->
                    PosterCard(
                      title = movie.title,
                      subtitle = movie.year ?: "—",
                      rating = movie.voteAverage,
                      posterUrl = movie.posterUrl,
                      actionLabel = "Play ${movie.title}",
                      watched = historyStore.find(vidfastMovieUrl(movie.id))?.completed == true,
                      onClick = { onPlay(movie.toPlaybackContext()) },
                      onDwell = { onConsidering(movie.toPlaybackContext()) },
                      modifier = cardModifier,
                    )
                  }
                } else {
                  CatalogRail(
                    heading = recommendationHeading(recommendationSeeds),
                    items = recommendedShows,
                    key = { it.id },
                    narrow = narrow,
                    attribution = false,
                    firstCardFocusRequester = recommendedFocusRequester,
                    up = up,
                    down = null,
                    onMoveDown = { if (showActorRail) actorFocusRequester.requestFocus() else moveToRail(firstLoadedRail) },
                    onMoveUp = null,
                  ) { show, cardModifier ->
                    PosterCard(
                      title = show.name,
                      subtitle = show.year ?: "—",
                      rating = show.voteAverage,
                      posterUrl = show.posterUrl,
                      actionLabel = "Open ${show.name}",
                      onClick = { onOpenShow(show) },
                      modifier = cardModifier,
                    )
                  }
                }
              }
            }
            // A familiar face from the last thing watched, and the rest of their work.
            if (showActorRail) {
              item(key = "cast") {
                val up =
                  when {
                    showRecommendedRail -> recommendedFocusRequester
                    showContinueRow -> continueRowFocusRequester
                    else -> searchButtonFocusRequester
                  }
                val heading = "More with ${featuredActor?.name.orEmpty()}"
                if (tab == CatalogTab.MOVIES) {
                  CatalogRail(
                    heading = heading,
                    items = actorMovies,
                    key = { it.id },
                    narrow = narrow,
                    attribution = false,
                    firstCardFocusRequester = actorFocusRequester,
                    up = up,
                    down = null,
                    onMoveDown = { moveToRail(firstLoadedRail) },
                    onMoveUp = null,
                  ) { movie, cardModifier ->
                    PosterCard(
                      title = movie.title,
                      subtitle = movie.year ?: "—",
                      rating = movie.voteAverage,
                      posterUrl = movie.posterUrl,
                      actionLabel = "Play ${movie.title}",
                      watched = historyStore.find(vidfastMovieUrl(movie.id))?.completed == true,
                      onClick = { onPlay(movie.toPlaybackContext()) },
                      onDwell = { onConsidering(movie.toPlaybackContext()) },
                      modifier = cardModifier,
                    )
                  }
                } else {
                  CatalogRail(
                    heading = heading,
                    items = actorShows,
                    key = { it.id },
                    narrow = narrow,
                    attribution = false,
                    firstCardFocusRequester = actorFocusRequester,
                    up = up,
                    down = null,
                    onMoveDown = { moveToRail(firstLoadedRail) },
                    onMoveUp = null,
                  ) { show, cardModifier ->
                    PosterCard(
                      title = show.name,
                      subtitle = show.year ?: "—",
                      rating = show.voteAverage,
                      posterUrl = show.posterUrl,
                      actionLabel = "Open ${show.name}",
                      onClick = { onOpenShow(show) },
                      modifier = cardModifier,
                    )
                  }
                }
              }
            }
            itemsIndexed(items = sections, key = { _, slot -> slot.category.id }) { index, slot ->
              val category = slot.category
              val railFocusRequester = railFocusRequesters[index]
              // Only the way back into the chrome is named. A rail further down the page has not
              // been composed yet, and a FocusRequester for a node that does not exist does
              // nothing at all, so moving between rails is left to focus search, which scrolls the
              // next one into view as it goes.
              // The topmost rail with anything in it, rather than position zero: while the rails
              // above it are still placeholders, this is the one a press of up has to leave from.
              val up =
                if (index == firstLoadedRail) {
                  // Whatever is immediately above, which is the last of the personalised rails
                  // when the viewer has any history behind them.
                  when {
                    showActorRail -> actorFocusRequester
                    showRecommendedRail -> recommendedFocusRequester
                    showContinueRow -> continueRowFocusRequester
                    else -> searchButtonFocusRequester
                  }
                } else {
                  null
                }
              val down: FocusRequester? = null
              val railHeading =
                when {
                  category.standaloneLabel -> category.label
                  tab == CatalogTab.MOVIES -> "${category.label} movies"
                  else -> "${category.label} TV shows"
                }
              // Posters fade in over the placeholder rather than replacing it between two frames.
              // The swap happens in a slot that is already the right height, so the fade is the
              // only thing that moves.
              Crossfade(
                targetState = slot.pending,
                animationSpec = tween(durationMillis = RAIL_FADE_MS),
                label = "rail ${category.id}",
                // Rails that answer out of order, and the odd one that answers with nothing, slide
                // into place instead of jumping.
                modifier = Modifier.animateItem(),
              ) { waiting ->
                if (waiting) {
                  RailSkeleton(narrow = narrow)
                } else if (tab == CatalogTab.MOVIES) {
                  CatalogRail(
                    heading = railHeading,
                    items = movieSections[category].orEmpty(),
                    key = { it.id },
                    narrow = narrow,
                    attribution = index == 0,
                    firstCardFocusRequester = railFocusRequester,
                    up = up,
                    down = down,
                    onMoveDown = if (index + 1 < sections.size) { { moveToRail(index + 1) } } else null,
                    onMoveUp = if (index > 0) { { moveToRail(index - 1) } } else null,
                  ) { movie, cardModifier ->
                    PosterCard(
                      title = movie.title,
                      subtitle = movie.year ?: "—",
                      rating = movie.voteAverage,
                      posterUrl = movie.posterUrl,
                      actionLabel = "Play ${movie.title}",
                      watched = historyStore.find(vidfastMovieUrl(movie.id))?.completed == true,
                      onClick = { onPlay(movie.toPlaybackContext()) },
                      onDwell = { onConsidering(movie.toPlaybackContext()) },
                      modifier = cardModifier,
                    )
                  }
                } else {
                  CatalogRail(
                    heading = railHeading,
                    items = showSections[category].orEmpty(),
                    key = { it.id },
                    narrow = narrow,
                    attribution = index == 0,
                    firstCardFocusRequester = railFocusRequester,
                    up = up,
                    down = down,
                    onMoveDown = if (index + 1 < sections.size) { { moveToRail(index + 1) } } else null,
                    onMoveUp = if (index > 0) { { moveToRail(index - 1) } } else null,
                  ) { show, cardModifier ->
                    PosterCard(
                      title = show.name,
                      subtitle = show.year ?: "—",
                      rating = show.voteAverage,
                      posterUrl = show.posterUrl,
                      actionLabel = "Open ${show.name}",
                      onClick = { onOpenShow(show) },
                      modifier = cardModifier,
                    )
                  }
                }
              }
            }
          }
        // Only when there is nothing to show. Typing would otherwise replace the results with a
        // loading panel on every letter, which flickers and loses the viewer's place.
        loading && itemCount == 0 -> StatusPanel("Loading…", edgeOnly.weight(1f), loading = true)
        errorMessage != null && itemCount == 0 ->
          StatusPanel(
            message = errorMessage ?: "Content could not be loaded",
            modifier = edgeOnly.weight(1f),
            actionLabel = "Try again",
            onAction = { load(tab, query.trim().takeIf(String::isNotBlank)) },
          )
        itemCount == 0 && !showContinueRow ->
          StatusPanel(
            if (tab == CatalogTab.MY_LIST) "Nothing saved yet. Open a title and choose Save to add it here."
            else "Nothing found. Try another title.",
            edgeOnly.weight(1f),
          )
        else ->
          LazyVerticalGrid(
            state = gridState,
            columns = GridCells.Adaptive(minSize = if (narrow) 132.dp else 158.dp),
            modifier = Modifier.weight(1f).fillMaxWidth(),
            // Inset as content rather than as a border, so a focused card at the edge is not
            // clipped by the boundary it sits against.
            contentPadding =
              PaddingValues(start = edge, end = edge, bottom = if (phoneDense) 8.dp else 22.dp),
            horizontalArrangement = Arrangement.spacedBy(if (narrow) 12.dp else 18.dp),
            verticalArrangement = Arrangement.spacedBy(if (narrow) 16.dp else 22.dp),
          ) {
            if (showContinueRow) {
              item(span = { GridItemSpan(maxLineSpan) }) {
                ContinueWatchingSection(
                  entries = continueWatching,
                  onResume = { entry -> onPlay(entry.toPlaybackContext()) },
                  onClearHistory = { confirmingHistoryClear = true },
                  firstCardFocusRequester = continueRowFocusRequester,
                  up = if (browsing) searchButtonFocusRequester else firstTabFocusRequester,
                  down = gridFocusRequester,
                  hasGrid = itemCount > 0,
                  edge = edge,
                )
              }
            }
            if (itemCount > 0) {
              item(span = { GridItemSpan(maxLineSpan) }) {
                SectionHeading(heading = heading, itemCount = itemCount, narrow = narrow)
              }
            }
            when (tab) {
              CatalogTab.MOVIES ->
                items(items = movies, key = { it.id }) { movie ->
                  PosterCard(
                    title = movie.title,
                    subtitle = movie.year ?: "—",
                    rating = movie.voteAverage,
                    posterUrl = movie.posterUrl,
                    actionLabel = "Play ${movie.title}",
                    watched = historyStore.find(vidfastMovieUrl(movie.id))?.completed == true,
                    onClick = { onPlay(movie.toPlaybackContext()) },
                    onDwell = { onConsidering(movie.toPlaybackContext()) },
                    modifier = gridEntryModifier(movie.id == movies.firstOrNull()?.id, gridFocusRequester),
                  )
                }
              CatalogTab.SHOWS ->
                items(items = shows, key = { it.id }) { show ->
                  PosterCard(
                    title = show.name,
                    subtitle = show.year ?: "—",
                    rating = show.voteAverage,
                    posterUrl = show.posterUrl,
                    actionLabel = "Open ${show.name}",
                    onClick = { onOpenShow(show) },
                    modifier = gridEntryModifier(show.id == shows.firstOrNull()?.id, gridFocusRequester),
                  )
                }
              CatalogTab.MY_LIST ->
                items(items = savedItems, key = { "${it.kind}-${it.id}" }) { item ->
                  PosterCard(
                    title = item.title,
                    subtitle = item.year ?: "—",
                    rating = item.voteAverage,
                    posterUrl = item.posterUrl,
                    actionLabel =
                      if (item.kind == LibraryKind.SHOW) "Open ${item.title}" else "Play ${item.title}",
                    onClick = {
                      if (item.kind == LibraryKind.SHOW) onOpenShow(item.toShow())
                      else onPlay(item.toPlaybackContext())
                    },
                    modifier =
                      gridEntryModifier(item.id == savedItems.firstOrNull()?.id, gridFocusRequester),
                  )
                }
            }
          }
      }
    }

    if (confirmingHistoryClear) {
      ClearWatchHistoryDialog(
        onDismiss = { confirmingHistoryClear = false },
        onConfirm = {
          historyStore.clear()
          playbackProgressStore.clearAll()
          watchHistory = emptyList()
          continueWatching = emptyList()
          recommendationSeeds = emptyList()
          recommendedMovies = emptyList()
          recommendedShows = emptyList()
          // Built from the history that has just been thrown away, so it goes with it.
          featuredActor = null
          actorMovies = emptyList()
          actorShows = emptyList()
          confirmingHistoryClear = false
          refreshHomeSurfaces(context)
          scope.launch {
            withFrameNanos {}
            runCatching { searchButtonFocusRequester.requestFocus() }
          }
        },
      )
    }
  }
}

private fun gridEntryModifier(isFirst: Boolean, gridFocusRequester: FocusRequester): Modifier =
  if (isFirst) Modifier.focusRequester(gridFocusRequester) else Modifier

private fun TmdbMovie.toPlaybackContext(): PlaybackContext =
  PlaybackContext(
    pageUrl = vidfastMovieUrl(id),
    title = title,
    subtitle = year,
    posterUrl = posterUrl,
    year = year,
    overview = overview,
    rating = voteAverage.takeIf { it > 0.0 },
  )

private fun LibraryItem.toPlaybackContext(): PlaybackContext =
  PlaybackContext(
    pageUrl = vidfastMovieUrl(id),
    title = title,
    subtitle = year,
    posterUrl = posterUrl,
    year = year,
    overview = overview,
    rating = voteAverage.takeIf { it > 0.0 },
  )

private fun LibraryItem.toShow(): TmdbShow =
  TmdbShow(
    id = id,
    name = title,
    firstAirDate = date,
    voteAverage = voteAverage,
    overview = overview,
    posterPath = posterPath,
  )

private fun WatchHistoryEntry.toPlaybackContext(): PlaybackContext =
  PlaybackContext(
    pageUrl = pageUrl,
    title = title,
    subtitle = subtitle,
    posterUrl = posterUrl,
    showId = showId,
    seasonNumber = seasonNumber,
    episodeNumber = episodeNumber,
  )

/** Maps transport failures onto wording a viewer can act on, keeping the raw cause in logcat. */
internal fun friendlyCatalogError(error: Throwable): String =
  when (error) {
    is SSLException ->
      "Secure connection to TMDB failed. Check this device's date and time, then try again."
    is UnknownHostException -> "No internet connection. Check the network and try again."
    is SocketTimeoutException -> "TMDB took too long to respond. Try again."
    else -> error.message ?: "Content could not be loaded"
  }

@Composable
private fun SectionHeading(
  heading: String,
  itemCount: Int,
  narrow: Boolean,
  attribution: Boolean = true,
  modifier: Modifier = Modifier,
) {
  Column(modifier) {
    Row(verticalAlignment = Alignment.CenterVertically) {
      Text(heading, color = SoftWhite, fontWeight = FontWeight.Black, fontSize = if (narrow) 17.sp else 19.sp)
      Spacer(Modifier.width(10.dp))
      if (itemCount > 0) {
        Text("$itemCount titles", color = AuroraMint, fontWeight = FontWeight.Bold, fontSize = 11.sp)
      }
      Spacer(Modifier.weight(1f))
      // Stated once at the top of the page rather than over every rail.
      if (!narrow && attribution) {
        Text(
          "TMDB API · not endorsed or certified by TMDB",
          color = MutedBlue.copy(alpha = .6f),
          fontSize = 9.sp,
        )
      }
    }
    Spacer(Modifier.height(8.dp))
  }
}


/**
 * Wordmark, tabs, destinations and the search box, gathered into one header surface.
 *
 * Stacking these separately cost four rows of chrome before any artwork; on a 10-foot layout that
 * pushed the catalog itself under the fold. On phone the footer owns destinations, so this surface
 * stays to brand + compact tabs + Remote, with search expanding only when asked.
 */
@Composable
private fun CatalogTopBar(
  modifier: Modifier = Modifier,
  narrow: Boolean,
  compact: Boolean,
  phoneDense: Boolean = false,
  showDestinationActions: Boolean,
  onOpenWeb: () -> Unit,
  onOpenShortDramas: () -> Unit,
  onOpenSports: () -> Unit,
  onOpenIptv: () -> Unit,
  onOpenRemote: (() -> Unit)?,
  onOpenSearch: (() -> Unit)? = null,
  onCloseSearch: (() -> Unit)? = null,
  openWebModifier: Modifier,
  shortDramasModifier: Modifier,
  sportsModifier: Modifier,
  iptvModifier: Modifier,
  tabs: @Composable () -> Unit,
  search: (@Composable () -> Unit)?,
) {
  val shape = RoundedCornerShape(if (phoneDense) 16.dp else if (narrow) 20.dp else 24.dp)
  // Labels do not fit beside the wordmark on a phone, and a clipped label helps nobody; the icon
  // carries it there and the spoken label stays on for a screen reader either way.
  val labelled = !narrow
  val actions: @Composable () -> Unit = {
    Row(
      horizontalArrangement = Arrangement.spacedBy(if (phoneDense) 4.dp else if (labelled) 8.dp else 6.dp),
      verticalAlignment = Alignment.CenterVertically,
    ) {
      if (showDestinationActions) {
        CatalogActionButton(
          label = "Sports",
          icon = Icons.Filled.SportsBasketball,
          showLabel = labelled,
          onClick = onOpenSports,
          modifier = sportsModifier,
        )
        CatalogActionButton(
          label = "IPTV",
          icon = Icons.Filled.LiveTv,
          showLabel = labelled,
          onClick = onOpenIptv,
          modifier = iptvModifier,
        )
        CatalogActionButton(
          label = "Short dramas",
          icon = Icons.Filled.Theaters,
          showLabel = labelled,
          onClick = onOpenShortDramas,
          modifier = shortDramasModifier,
        )
        CatalogActionButton(
          label = "Open web",
          icon = Icons.Filled.Language,
          showLabel = labelled,
          onClick = onOpenWeb,
          modifier = openWebModifier,
        )
      }
      onOpenSearch?.let { open ->
        CatalogIconButton("Search", Icons.Filled.Search, open)
      }
      onCloseSearch?.let { close ->
        CatalogIconButton("Close search", Icons.Filled.Close, close)
      }
      onOpenRemote?.let { open ->
        CatalogActionButton(
          label = "Remote",
          icon = Icons.Filled.SettingsRemote,
          showLabel = labelled,
          onClick = open,
        )
      }
    }
  }
  Column(
    modifier =
      modifier.fillMaxWidth().clip(shape)
        .background(
          Brush.horizontalGradient(
            listOf(NightSurface.copy(alpha = .95f), NightSurface.copy(alpha = .55f))
          )
        )
        .border(1.dp, SoftWhite.copy(alpha = .07f), shape)
        .padding(
          horizontal = if (phoneDense) 10.dp else if (narrow) 12.dp else 16.dp,
          vertical = if (phoneDense) 8.dp else if (compact) 10.dp else 13.dp,
        ),
    verticalArrangement = Arrangement.spacedBy(if (phoneDense) 6.dp else if (compact) 9.dp else 12.dp),
  ) {
    if (narrow) {
      Row(modifier = Modifier.fillMaxWidth(), verticalAlignment = Alignment.CenterVertically) {
        CatalogWordmark(compact = true, dense = phoneDense)
        Spacer(modifier.weight(1f))
        actions()
      }
      // Phone: keep the Movies / TV Shows / My List track centered under the full header width.
      Box(modifier = Modifier.fillMaxWidth(), contentAlignment = Alignment.Center) {
        tabs()
      }
    } else {
      Row(modifier = Modifier.fillMaxWidth(), verticalAlignment = Alignment.CenterVertically) {
        CatalogWordmark(compact = false)
        Spacer(modifier.width(24.dp))
        tabs()
        Spacer(modifier.weight(1f))
        actions()
      }
    }
    search?.invoke()
  }
}

@Composable
private fun CatalogWordmark(compact: Boolean, dense: Boolean = false) {
  Row(verticalAlignment = Alignment.CenterVertically) {
    GizTvMark(
      modifier = Modifier.size(if (dense) 26.dp else if (compact) 30.dp else 34.dp),
      cornerRadius = if (dense) 8.dp else 10.dp,
    )
    Spacer(Modifier.width(if (dense) 8.dp else 10.dp))
    Column {
      Text(
        "GIZTV",
        color = SoftWhite,
        fontWeight = FontWeight.Black,
        letterSpacing = if (dense) 2.sp else 2.5.sp,
        fontSize = if (dense) 15.sp else if (compact) 16.sp else 18.sp,
      )
      if (!dense) {
        // Under the wordmark rather than beside it: the version is the least important thing in the
        // bar, and a bordered chip on the same line gave it the weight of a button.
        Text(
          "v${BuildConfig.VERSION_NAME}",
          color = AuroraMint.copy(alpha = .75f),
          fontWeight = FontWeight.Bold,
          letterSpacing = 1.sp,
          fontSize = 9.sp,
        )
      }
    }
  }
}

/**
 * One listing rail: a heading and a row of posters.
 *
 * Every listing is on the page at once, so a viewer reaches Top rated with two presses of down
 * rather than by first choosing it from a filter.
 */
@Composable
private fun <T> CatalogRail(
  heading: String,
  items: List<T>,
  key: (T) -> Any,
  narrow: Boolean,
  attribution: Boolean,
  firstCardFocusRequester: FocusRequester,
  up: FocusRequester?,
  down: FocusRequester?,
  onMoveDown: (() -> Unit)?,
  onMoveUp: (() -> Unit)?,
  card: @Composable (T, Modifier) -> Unit,
) {
  if (items.isEmpty()) return
  val firstKey = key(items.first())
  Column(
    modifier =
      Modifier.onPreviewKeyEvent { event ->
        if (event.type != KeyEventType.KeyDown) return@onPreviewKeyEvent false
        when (event.key) {
          Key.DirectionDown -> onMoveDown?.let { it(); true } ?: false
          Key.DirectionUp -> onMoveUp?.let { it(); true } ?: false
          else -> false
        }
      }
  ) {
    // The same inset the rest of the screen uses, applied here rather than around the rail.
    val edge = if (narrow) 18.dp else 42.dp
    SectionHeading(
      heading = heading,
      itemCount = items.size,
      narrow = narrow,
      attribution = attribution,
      modifier = Modifier.padding(horizontal = edge),
    )
    LazyRow(
      modifier = Modifier.fillMaxWidth().focusGroup(),
      // The inset lives here rather than around the whole rail, so the first card lines up with
      // everything else while the last one is free to scroll off the edge of the screen.
      contentPadding = PaddingValues(horizontal = edge),
      horizontalArrangement = Arrangement.spacedBy(if (narrow) 12.dp else 18.dp),
    ) {
      items(items = items, key = key) { item ->
        val cardModifier =
          Modifier.width(if (narrow) 132.dp else 158.dp)
            .let { if (key(item) == firstKey) it.focusRequester(firstCardFocusRequester) else it }
            .focusProperties {
              if (up != null) this.up = up
              if (down != null) this.down = down
            }
        card(item, cardModifier)
      }
    }
  }
}

/** The search box, on its own line now that the listings are rails rather than a filter. */
@Composable
private fun CatalogSearchRow(
  narrow: Boolean,
  query: String,
  placeholder: String,
  onQueryChanged: (String) -> Unit,
  onSearch: () -> Unit,
  searchFieldFocusRequester: FocusRequester,
  searchButtonFocusRequester: FocusRequester,
  tabFocusRequester: FocusRequester,
  bodyFocusRequester: FocusRequester?,
) {
  val fieldModifier =
    Modifier.focusRequester(searchFieldFocusRequester).focusProperties {
      up = tabFocusRequester
      down = bodyFocusRequester ?: FocusRequester.Default
      right = searchButtonFocusRequester
    }.remoteFocusNavigation(up = tabFocusRequester, down = bodyFocusRequester)
  // Down from the chrome lands here rather than in the field, so passing through the row never
  // summons the keyboard; pressing it is what opens the field for typing.
  val buttonModifier =
    Modifier.focusRequester(searchButtonFocusRequester).focusProperties {
      up = tabFocusRequester
      left = searchFieldFocusRequester
      down = bodyFocusRequester ?: FocusRequester.Default
    }.remoteFocusNavigation(
      up = tabFocusRequester,
      left = searchFieldFocusRequester,
      down = bodyFocusRequester,
    )
  Row(
    modifier = Modifier.focusGroup().fillMaxWidth(),
    horizontalArrangement = Arrangement.spacedBy(10.dp),
    verticalAlignment = Alignment.CenterVertically,
  ) {
    // Stretched the full width of a television the field is a metre of empty box for a two-word
    // query. Capped, it sits under the wordmark and tabs it belongs with.
    val width = if (narrow) Modifier.weight(1f) else Modifier.width(560.dp)
    CatalogSearchField(query, placeholder, onQueryChanged, onSearch, fieldModifier.then(width))
    // Round and icon-only: it is the same magnifier as the one in the field, so the pairing reads
    // without a word, and the shape keeps it from looking like a fourth destination button.
    CatalogIconButton("Search", Icons.Filled.Search, onSearch, buttonModifier)
  }
}
