package com.giztv.tv.ui.catalog

import android.util.Log
import androidx.activity.compose.BackHandler
import androidx.compose.animation.AnimatedContent
import androidx.compose.animation.animateColorAsState
import androidx.compose.animation.core.animateFloatAsState
import androidx.compose.animation.core.tween
import androidx.compose.animation.fadeIn
import androidx.compose.animation.fadeOut
import androidx.compose.animation.scaleIn
import androidx.compose.animation.scaleOut
import androidx.compose.animation.togetherWith
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
import androidx.compose.foundation.layout.widthIn
import androidx.compose.foundation.lazy.LazyColumn
import androidx.compose.foundation.lazy.LazyListState
import androidx.compose.foundation.lazy.LazyRow
import androidx.compose.foundation.lazy.itemsIndexed
import androidx.compose.foundation.lazy.rememberLazyListState
import androidx.compose.foundation.lazy.grid.GridCells
import androidx.compose.foundation.lazy.grid.GridItemSpan
import androidx.compose.foundation.lazy.grid.LazyGridState
import androidx.compose.foundation.lazy.grid.LazyGridScope
import androidx.compose.foundation.lazy.grid.LazyVerticalGrid
import androidx.compose.foundation.lazy.grid.items
import androidx.compose.foundation.lazy.grid.rememberLazyGridState
import androidx.compose.runtime.saveable.rememberSaveable
import androidx.compose.foundation.lazy.items
import androidx.compose.foundation.shape.RoundedCornerShape
import androidx.compose.material.icons.Icons
import androidx.compose.material.icons.filled.Close
import androidx.compose.material.icons.filled.Language
import androidx.compose.material.icons.filled.LiveTv
import androidx.compose.material.icons.filled.Search
import androidx.compose.material.icons.filled.SettingsRemote
import androidx.compose.material.icons.filled.SportsBasketball
import androidx.compose.material.icons.filled.SportsSoccer
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
import com.giztv.tv.BuildConfig
import kotlinx.coroutines.async
import com.giztv.tv.data.LibraryItem
import com.giztv.tv.data.LibraryKind
import com.giztv.tv.data.MyListStore
import com.giztv.tv.data.PlaybackContext
import com.giztv.tv.data.SearchHistoryStore
import com.giztv.tv.data.SearchSection
import com.giztv.tv.data.UiPreferencesStore
import com.giztv.tv.data.WatchHistoryEntry
import com.giztv.tv.data.WatchHistoryStore
import com.giztv.tv.home.refreshHomeSurfaces
import com.giztv.tv.link.RemoteUiBridge
import com.giztv.tv.theme.GizMint
import com.giztv.tv.theme.DeepSpace
import com.giztv.tv.theme.MutedBlue
import com.giztv.tv.theme.NightSurface
import com.giztv.tv.theme.SoftWhite
import com.giztv.tv.ui.player.PlaybackProgressStore
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

/**
 * How many times a press of the pad asks an arriving rail for the focus before giving up.
 *
 * Spread over roughly a second, which covers a rail answering from the cache or from a reasonable
 * connection. Beyond that the press is let go rather than stealing focus long after it was made.
 */
private const val RAIL_FOCUS_ATTEMPTS = 12
private const val RAIL_FOCUS_RETRY_MS = 100L

/** Reclaims the last focused catalog card after a covering detail page is dismissed. */
@Composable
internal fun RestoreCatalogFocusEffect(
  isActive: Boolean,
  focusRequester: FocusRequester?,
  settleDelayMs: Long = 120L,
  retryAttempts: Int = RAIL_FOCUS_ATTEMPTS,
  retryDelayMs: Long = RAIL_FOCUS_RETRY_MS,
) {
  var wasActive by remember { mutableStateOf(isActive) }
  LaunchedEffect(isActive) {
    val restoring = isActive && !wasActive
    wasActive = isActive
    if (!restoring) return@LaunchedEffect
    val requester = focusRequester ?: return@LaunchedEffect
    if (settleDelayMs > 0) delay(settleDelayMs)
    repeat(retryAttempts) { attempt ->
      withFrameNanos {}
      if (requester.requestFocusIfReady()) return@LaunchedEffect
      if (attempt > 0 && retryDelayMs > 0) delay(retryDelayMs)
    }
  }
}

/** Compact shortcuts shown between the featured title and the catalog rails. */
private val CATALOG_SHORTCUTS =
  listOf("All", "Top Rated", "Action", "Comedy", "Drama", "Sci-Fi", "Animation", "Horror")

/** The rail opened by a home shortcut; `null` means the top of the home screen. */
internal fun catalogShortcutCategoryId(label: String): String? =
  when (label) {
    "Top Rated" -> "acclaimed"
    "Action" -> "genre-action"
    "Comedy" -> "genre-comedy"
    "Drama" -> "genre-drama"
    "Sci-Fi" -> "genre-scifi"
    "Animation" -> "genre-animation"
    "Horror" -> "genre-horror"
    else -> null
  }

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
  onOpenMovie: (TmdbMovie) -> Unit,
  onOpenShow: (TmdbShow) -> Unit,
  onOpenWeb: () -> Unit,
  onOpenShortDramas: () -> Unit,
  onOpenAnime: () -> Unit,
  onOpenSports: () -> Unit,
  onOpenDlhdSoccer: () -> Unit = {},
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
  /** False while a detail page is covering the still-composed catalog. */
  isActive: Boolean = true,
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
  val searchHistoryStore = remember(context) { SearchHistoryStore(context) }
  val scope = rememberCoroutineScope()
  val focusManager = LocalFocusManager.current
  val keyboardController = LocalSoftwareKeyboardController.current
  val openWebFocusRequester = remember { FocusRequester() }
  val shortDramasFocusRequester = remember { FocusRequester() }
  val animeFocusRequester = remember { FocusRequester() }
  val sportsFocusRequester = remember { FocusRequester() }
  val soccerFocusRequester = remember { FocusRequester() }
  val iptvFocusRequester = remember { FocusRequester() }
  val firstTabFocusRequester = remember { FocusRequester() }
  val searchFieldFocusRequester = remember { FocusRequester() }
  val searchButtonFocusRequester = remember { FocusRequester() }
  val searchHistoryFocusRequester = remember { FocusRequester() }
  val heroFocusRequester = remember { FocusRequester() }
  val filterFocusRequester = remember { FocusRequester() }
  val continueRowFocusRequester = remember { FocusRequester() }
  val gridFocusRequester = remember { FocusRequester() }
  var lastContentFocusRequester by remember { mutableStateOf<FocusRequester?>(null) }
  // One per listing, so a press of down lands on the next rail rather than wherever focus search
  // decides — the same reason every other list on this screen names its own neighbours.
  // Worked out once: a decade rail depends on the current year, and recomposing must not shuffle
  // the rails under a viewer who is part-way along one.
  val categories = remember { catalogCategories() }
  val railFocusRequesters = remember(categories) { List(categories.size) { FocusRequester() } }
  val gridState = rememberSaveable(saver = LazyGridState.Saver) { LazyGridState() }
  val railState = rememberSaveable(saver = LazyListState.Saver) { LazyListState() }
  // Phone footer owns destinations; hide the always-on search row until asked.
  val phoneChrome = !showTopDestinationActions

  var tab by rememberSaveable {
    mutableStateOf(CatalogTab.entries.firstOrNull { it.name == uiPreferences.lastTab() } ?: CatalogTab.MOVIES)
  }
  var query by rememberSaveable { mutableStateOf("") }
  var searchActive by rememberSaveable { mutableStateOf(false) }
  var searchExpanded by rememberSaveable { mutableStateOf(false) }
  // Films and series are looked for separately, so they remember separately: the tab decides which
  // list is being added to and which is offered back under the box.
  val searchSection =
    if (tab == CatalogTab.SHOWS) SearchSection.TV_SHOWS else SearchSection.MOVIES
  var recentSearches by remember { mutableStateOf(emptyList<String>()) }
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
  var selectedGenreFilter by rememberSaveable { mutableStateOf("All") }
  val spotlightMovie = remember(recommendedMovies, movieSections) {
    recommendedMovies.firstOrNull() ?: movieSections.values.flatten().firstOrNull()
  }
  val spotlightShow = remember(recommendedShows, showSections) {
    recommendedShows.firstOrNull() ?: showSections.values.flatten().firstOrNull()
  }
  /** How far down the listings have been asked for; grows as the viewer scrolls towards them. */
  var railsRequested by rememberSaveable { mutableStateOf(EAGER_RAILS) }
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
      if (activeTab == CatalogTab.MY_LIST && searchQuery.isNullOrBlank()) {
        savedItems = myListStore.all()
      } else if (!searchQuery.isNullOrBlank()) {
        val q = searchQuery.trim()
        val movieDeferred = scope.async { movieRepository.searchMovies(q) }
        val showDeferred = scope.async { tvRepository.searchShows(q) }
        movies = movieDeferred.await()
        shows = showDeferred.await()
      } else {
        if (activeTab == CatalogTab.MOVIES) {
          movies = movieRepository.searchMovies("")
        } else {
          shows = tvRepository.searchShows("")
        }
      }
    }.onFailure {
      Log.e("GizTvTmdb", "TMDB load failed", it)
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

  fun rememberSearch(searched: String) {
    searchHistoryStore.record(searchSection, searched)
    recentSearches = searchHistoryStore.recent(searchSection)
  }

  /** Runs a remembered query again, exactly as if it had just been typed. */
  fun repeatSearch(searched: String) {
    query = searched
    searchExpanded = true
    keyboardController?.hide()
    focusManager.clearFocus()
  }

  fun clearSearchHistory() {
    searchHistoryStore.clear(searchSection)
    recentSearches = emptyList()
  }

  // Re-read per tab, and on every entry so a search made on another screen's turn is here too.
  LaunchedEffect(searchSection) { recentSearches = searchHistoryStore.recent(searchSection) }

  LaunchedEffect(requestSearchFocus) {
    if (!requestSearchFocus) return@LaunchedEffect
    searchExpanded = true
    // One frame so the search row is placed after a destination change into this screen.
    withFrameNanos {}
    runCatching { searchFieldFocusRequester.requestFocus() }
    onSearchFocusHandled()
  }

  BackHandler(enabled = phoneChrome && searchExpanded) { collapseSearchUi() }

  // A television has no touch event to establish its first focus target. Start on the active tab
  // once, while preserving the focused rail when this saveable catalog returns from a detail page.
  LaunchedEffect(showTopDestinationActions) {
    if (!showTopDestinationActions) return@LaunchedEffect
    // Let the activity regain window focus before accepting a successful request; during a cold
    // launch the node can attach one frame before Android finishes restoring the task.
    delay(300)
    repeat(RAIL_FOCUS_ATTEMPTS) { attempt ->
      withFrameNanos {}
      if (firstTabFocusRequester.requestFocusIfReady()) {
        return@LaunchedEffect
      }
      if (attempt > 0) delay(RAIL_FOCUS_RETRY_MS)
    }
  }

  // Detail pages temporarily disable focus on the catalog without destroying it. When the page is
  // uncovered, explicitly return focus to the exact card/button that opened it; relying on spatial
  // focus search otherwise starts again at the selected top tab.
  RestoreCatalogFocusEffect(isActive, lastContentFocusRequester)

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
    // Recorded once the search has actually been made rather than on each keystroke. Everything
    // this query was typed through is a prefix of it, and the store drops those, so a run of
    // letters leaves one entry behind instead of one per letter.
    rememberSearch(trimmed)
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
    if (!searchActive && query.isBlank() && movieSections.isEmpty() && showSections.isEmpty()) {
      load(tab, null)
    }
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
  var arrived by rememberSaveable { mutableStateOf(false) }
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
      searchActive -> movies.size + shows.size
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
  val showHero =
    showRails &&
      when (tab) {
        CatalogTab.MOVIES -> spotlightMovie != null
        CatalogTab.SHOWS -> spotlightShow != null
        CatalogTab.MY_LIST -> false
      }
  // Rails past the fold are not composed, and neither a FocusRequester nor focus search can reach a
  // node that does not exist yet. So the move is driven: scroll the neighbour into view first, then
  // hand it the focus. Hoisted because the personalized rail moves into the fixed ones too.
  val leadingRailItems =
    (if (showHero) 1 else 0) +
      1 + // Category shortcuts are always present while browsing rails.
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
        // A rail that has not answered yet holds a placeholder, and a placeholder has no card to
        // focus. Scrolling to it is also what asks for it, so the answer is on its way — the press
        // waits for it rather than being dropped. Dropping it was what left the pad dead at the
        // edge of the loaded rails: focus could not move down, and nothing below would load until
        // it did.
        repeat(RAIL_FOCUS_ATTEMPTS) { attempt ->
          if (
            railFocusRequesters[target].requestFocusIfReady()
          ) return@launch
          withFrameNanos {}
          if (attempt > 0) delay(RAIL_FOCUS_RETRY_MS)
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
  // Down from Search must land on something that is on screen (and therefore composed). Jumping
  // straight to a genre rail while Continue / Recommended / Cast fill the fold left the requester
  // unattached, and the pad stopped on Search.
  val firstBodyFocusRequester =
    when {
      showHero -> heroFocusRequester
      showRails -> filterFocusRequester
      else -> gridFocusRequester
    }
  val focusAboveFilters =
    when {
      showHero -> heroFocusRequester
      else -> searchButtonFocusRequester
    }
  val focusBelowFilters =
    when {
      showContinueRow -> continueRowFocusRequester
      showRecommendedRail -> recommendedFocusRequester
      showActorRail -> actorFocusRequester
      else -> firstRailFocusRequester
    }
  val filterItemIndex = if (showHero) 1 else 0
  val continueItemIndex = filterItemIndex + 1
  val recommendedItemIndex = continueItemIndex + if (showContinueRow) 1 else 0
  val actorItemIndex = recommendedItemIndex + if (showRecommendedRail) 1 else 0
  val focusHomeItem: (Int, FocusRequester) -> Unit = { itemIndex, requester ->
    railMove.value?.cancel()
    railMove.value =
      scope.launch {
        val layoutInfo = railState.layoutInfo
        val placed = layoutInfo.visibleItemsInfo.firstOrNull { it.index == itemIndex }
        val fullyVisible =
          placed != null &&
            placed.offset >= layoutInfo.viewportStartOffset &&
            placed.offset + placed.size <= layoutInfo.viewportEndOffset
        if (!fullyVisible) railState.animateScrollToItem(itemIndex)
        repeat(RAIL_FOCUS_ATTEMPTS) { attempt ->
          if (requester.requestFocusIfReady()) return@launch
          withFrameNanos {}
          if (attempt > 0) delay(RAIL_FOCUS_RETRY_MS)
        }
      }
    Unit
  }
  val moveAboveFilters: () -> Unit = {
    when {
      showHero -> focusHomeItem(0, heroFocusRequester)
      else -> searchButtonFocusRequester.requestFocus()
    }
    Unit
  }
  val moveBelowFilters: () -> Unit = {
    when {
      showContinueRow -> focusHomeItem(continueItemIndex, continueRowFocusRequester)
      showRecommendedRail -> focusHomeItem(recommendedItemIndex, recommendedFocusRequester)
      showActorRail -> focusHomeItem(actorItemIndex, actorFocusRequester)
      else -> moveToRail(firstLoadedRail)
    }
    Unit
  }
  val activateCatalogShortcut: (String) -> Unit = { label ->
    selectedGenreFilter = label
    val categoryId = catalogShortcutCategoryId(label)
    if (categoryId == null) {
      focusHomeItem(0, firstBodyFocusRequester)
    } else {
      sections.indexOfFirst { it.category.id == categoryId }
        .takeIf { it >= 0 }
        ?.let(moveToRail)
    }
  }

  BoxWithConstraints(
    modifier =
      modifier.fillMaxSize().pointerInput(Unit) { detectTapGestures { dismissKeyboard() } }
        .background(
          Brush.radialGradient(
            colors = listOf(Color(0xFF143843), Color(0xFF091421), Color(0xFF03070E)),
            radius = 1_450f,
            center = androidx.compose.ui.geometry.Offset(1_300f, 180f),
          )
        )
  ) {
    val narrow = maxWidth < 600.dp
    val compact = maxHeight < 600.dp
    val phoneDense = phoneChrome && narrow
    val labelDestinationActions = maxWidth >= 1_180.dp
    // Phone: search stays collapsed until the header icon or footer Search opens it. TV keeps
    // the leanback field always available for the remote.
    val showSearchRow = browsing && (!phoneChrome || searchExpanded || searchActive)
    val chromeDown =
      when {
        showSearchRow -> searchButtonFocusRequester
        showRails || showContinueRow || itemCount > 0 -> firstBodyFocusRequester
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
        labelDestinationActions = labelDestinationActions,
        showDestinationActions = showTopDestinationActions,
        onOpenWeb = { dismissKeyboard(); onOpenWeb() },
        onOpenShortDramas = { dismissKeyboard(); onOpenShortDramas() },
        onOpenAnime = { dismissKeyboard(); onOpenAnime() },
        onOpenSports = { dismissKeyboard(); onOpenSports() },
        onOpenDlhdSoccer = { dismissKeyboard(); onOpenDlhdSoccer() },
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
        openWebModifier =
          Modifier.focusRequester(openWebFocusRequester).focusProperties {
            left = animeFocusRequester
            down = chromeDown
          },
        shortDramasModifier =
          Modifier.focusRequester(shortDramasFocusRequester).focusProperties {
            left = iptvFocusRequester
            right = animeFocusRequester
            down = chromeDown
          },
        animeModifier =
          Modifier.focusRequester(animeFocusRequester).focusProperties {
            left = shortDramasFocusRequester
            right = openWebFocusRequester
            down = chromeDown
          },
        iptvModifier =
          Modifier.focusRequester(iptvFocusRequester).focusProperties {
            left = soccerFocusRequester
            right = shortDramasFocusRequester
            down = chromeDown
          },
        sportsModifier =
          Modifier.focusRequester(sportsFocusRequester).focusProperties {
            left = firstTabFocusRequester
            right = soccerFocusRequester
            down = chromeDown
          },
        soccerModifier =
          Modifier.focusRequester(soccerFocusRequester).focusProperties {
            left = sportsFocusRequester
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
              val showHistoryRow = recentSearches.isNotEmpty()
              val moveIntoBody: () -> Unit = {
                if (showRails) focusHomeItem(0, firstBodyFocusRequester)
                else runCatching { firstBodyFocusRequester.requestFocus() }
                Unit
              }
              Column(verticalArrangement = Arrangement.spacedBy(if (phoneDense) 6.dp else 9.dp)) {
                CatalogSearchRow(
                  narrow = narrow,
                  query = query,
                  placeholder = if (tab == CatalogTab.MOVIES) "Search movies…" else "Search shows…",
                  onQueryChanged = { query = it },
                  onSearch = ::runSearch,
                  searchFieldFocusRequester = searchFieldFocusRequester,
                  searchButtonFocusRequester = searchButtonFocusRequester,
                  tabFocusRequester = firstTabFocusRequester,
                  // Down out of the box lands on the remembered queries when there are any, so
                  // the pad reaches them on the way to the results rather than past them.
                  bodyFocusRequester =
                    if (showHistoryRow) searchHistoryFocusRequester
                    else firstBodyFocusRequester.takeIf { showRails || showContinueRow || itemCount > 0 },
                  onMoveDown =
                    if (showHistoryRow) {
                      { runCatching { searchHistoryFocusRequester.requestFocus() }; Unit }
                    } else {
                      moveIntoBody
                    },
                  onCloseSearch = if (phoneChrome) ({ collapseSearchUi() }) else null,
                )
                SearchHistoryRow(
                  queries = recentSearches,
                  onSelect = ::repeatSearch,
                  onClear = ::clearSearchHistory,
                  compact = phoneDense,
                  firstChipFocusRequester = searchHistoryFocusRequester,
                  up = searchFieldFocusRequester,
                  down =
                    firstBodyFocusRequester.takeIf { showRails || showContinueRow || itemCount > 0 },
                  onMoveDown = moveIntoBody,
                )
              }
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

      AnimatedContent(
        targetState = tab,
        transitionSpec = {
          (fadeIn(animationSpec = tween(220)) + scaleIn(initialScale = 0.97f)) togetherWith
            (fadeOut(animationSpec = tween(160)) + scaleOut(targetScale = 1.01f))
        },
        label = "TabContentMorphTransition",
        modifier = Modifier.weight(1f).fillMaxWidth(),
      ) { currentTab ->
        when {
          // Nothing at all came back, on any rail: the network is the problem, and a page of
          // placeholders that will never fill would be a lie about it.
          showRails && errorMessage != null && itemCount == 0 ->
            StatusPanel(
              message = errorMessage ?: "Content could not be loaded",
              modifier = edgeOnly.fillMaxSize(),
              actionLabel = "Try again",
              onAction = { retryRails(currentTab) },
            )
          // Otherwise the rails go up straight away, each waiting for itself. There is no screen-wide
          // loading panel here any more: it was one wait as long as the slowest of eighteen requests,
          // with nothing to look at meanwhile.
          showRails ->
            LazyColumn(
              state = railState,
              // The focus group is what lets a press of down reach a rail that has not been composed
              // yet: focus search asks the list to bring it into view first.
              modifier = Modifier.fillMaxSize().focusGroup(),
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
            if (tab == CatalogTab.MOVIES && spotlightMovie != null) {
              item(key = "hero_spotlight_movie") {
                HeroSpotlightBanner(
                  title = spotlightMovie.title,
                  subtitle = spotlightMovie.year ?: "Movie",
                  overview = spotlightMovie.overview,
                  rating = spotlightMovie.voteAverage,
                  posterUrl = spotlightMovie.posterUrl,
                  backdropUrl = spotlightMovie.backdropUrl,
                  onPlay = { onPlay(spotlightMovie.toPlaybackContext()) },
                  onOpenDetails = { onOpenMovie(spotlightMovie) },
                  firstCardFocusRequester = heroFocusRequester,
                  up = searchButtonFocusRequester,
                  down = filterFocusRequester,
                  onFocused = { lastContentFocusRequester = it },
                  edge = edge,
                  narrow = narrow,
                )
              }
            } else if (tab == CatalogTab.SHOWS && spotlightShow != null) {
              item(key = "hero_spotlight_show") {
                HeroSpotlightBanner(
                  title = spotlightShow.name,
                  subtitle = spotlightShow.year ?: "TV Series",
                  overview = spotlightShow.overview,
                  rating = spotlightShow.voteAverage,
                  posterUrl = spotlightShow.posterUrl,
                  backdropUrl = spotlightShow.backdropUrl,
                  onPlay = { onOpenShow(spotlightShow) },
                  onOpenDetails = { onOpenShow(spotlightShow) },
                  firstCardFocusRequester = heroFocusRequester,
                  up = searchButtonFocusRequester,
                  down = filterFocusRequester,
                  onFocused = { lastContentFocusRequester = it },
                  edge = edge,
                  narrow = narrow,
                )
              }
            }

            item(key = "catalog_filters") {
              CatalogFilterRow(
                filters = CATALOG_SHORTCUTS,
                selectedFilter = selectedGenreFilter,
                onSelectFilter = activateCatalogShortcut,
                firstFilterFocusRequester = filterFocusRequester,
                up = focusAboveFilters,
                down = focusBelowFilters,
                onMoveUp = moveAboveFilters,
                onMoveDown = moveBelowFilters,
                edge = edge,
              )
            }

            if (showContinueRow) {
              item {
                ContinueWatchingSection(
                  entries = continueWatching,
                  onResume = { entry -> onPlay(entry.toPlaybackContext()) },
                  onClearHistory = { confirmingHistoryClear = true },
                  firstCardFocusRequester = continueRowFocusRequester,
                  up = filterFocusRequester,
                  down =
                    when {
                      showRecommendedRail -> recommendedFocusRequester
                      showActorRail -> actorFocusRequester
                      else -> firstRailFocusRequester
                    },
                  hasGrid = sections.isNotEmpty() || showRecommendedRail || showActorRail,
                  onMoveUp = { focusHomeItem(filterItemIndex, filterFocusRequester) },
                  onMoveDown = {
                    when {
                      showRecommendedRail ->
                        focusHomeItem(recommendedItemIndex, recommendedFocusRequester)
                      showActorRail -> focusHomeItem(actorItemIndex, actorFocusRequester)
                      else -> moveToRail(firstLoadedRail)
                    }
                  },
                  edge = edge,
                )
              }
            }

            // Above the fixed listings, because it is the one rail built for this viewer.
            if (showRecommendedRail) {
              item(key = "recommended") {
                val up = if (showContinueRow) continueRowFocusRequester else filterFocusRequester
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
                    onMoveDown = {
                      if (showActorRail) focusHomeItem(actorItemIndex, actorFocusRequester)
                      else moveToRail(firstLoadedRail)
                    },
                    onMoveUp = {
                      focusHomeItem(
                        if (showContinueRow) continueItemIndex else filterItemIndex,
                        up,
                      )
                    },
                    onCardFocused = { lastContentFocusRequester = it },
                  ) { movie, cardModifier ->
                    PosterCard(
                      title = movie.title,
                      subtitle = movie.year ?: "—",
                      rating = movie.voteAverage,
                      posterUrl = movie.posterUrl,
                      actionLabel = "Open ${movie.title}",
                      watched = historyStore.find(vidfastMovieUrl(movie.id))?.completed == true,
                      onClick = { onOpenMovie(movie) },
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
                    onMoveDown = {
                      if (showActorRail) focusHomeItem(actorItemIndex, actorFocusRequester)
                      else moveToRail(firstLoadedRail)
                    },
                    onMoveUp = {
                      focusHomeItem(
                        if (showContinueRow) continueItemIndex else filterItemIndex,
                        up,
                      )
                    },
                    onCardFocused = { lastContentFocusRequester = it },
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
                    else -> filterFocusRequester
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
                    onMoveUp = {
                      focusHomeItem(
                        when {
                          showRecommendedRail -> recommendedItemIndex
                          showContinueRow -> continueItemIndex
                          else -> filterItemIndex
                        },
                        up,
                      )
                    },
                    onCardFocused = { lastContentFocusRequester = it },
                  ) { movie, cardModifier ->
                    PosterCard(
                      title = movie.title,
                      subtitle = movie.year ?: "—",
                      rating = movie.voteAverage,
                      posterUrl = movie.posterUrl,
                      actionLabel = "Open ${movie.title}",
                      watched = historyStore.find(vidfastMovieUrl(movie.id))?.completed == true,
                      onClick = { onOpenMovie(movie) },
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
                    onMoveUp = {
                      focusHomeItem(
                        when {
                          showRecommendedRail -> recommendedItemIndex
                          showContinueRow -> continueItemIndex
                          else -> filterItemIndex
                        },
                        up,
                      )
                    },
                    onCardFocused = { lastContentFocusRequester = it },
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
                    else -> filterFocusRequester
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
              // Swapped outright rather than crossfaded. The placeholder already holds the slot at
              // the right height, so there is nothing to smooth over — and wrapping every rail in an
              // animation container put a layer between the pad and the cards it has to reach.
              Box(modifier = Modifier.animateItem()) {
                if (slot.pending) {
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
                    // Above the first loaded rail, Up is named via [up] (cast / recommended /
                    // continue / search). Intercepting it here would send the pad at a placeholder.
                    onMoveUp =
                      if (index > firstLoadedRail) {
                        { moveToRail(index - 1) }
                      } else {
                        up?.let { requester ->
                          { focusHomeItem((leadingRailItems - 1).coerceAtLeast(0), requester) }
                        }
                      },
                    onCardFocused = { lastContentFocusRequester = it },
                  ) { movie, cardModifier ->
                    PosterCard(
                      title = movie.title,
                      subtitle = movie.year ?: "—",
                      rating = movie.voteAverage,
                      posterUrl = movie.posterUrl,
                      actionLabel = "Open ${movie.title}",
                      watched = historyStore.find(vidfastMovieUrl(movie.id))?.completed == true,
                      onClick = { onOpenMovie(movie) },
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
                    onMoveUp =
                      if (index > firstLoadedRail) {
                        { moveToRail(index - 1) }
                      } else {
                        up?.let { requester ->
                          { focusHomeItem((leadingRailItems - 1).coerceAtLeast(0), requester) }
                        }
                      },
                    onCardFocused = { lastContentFocusRequester = it },
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
        loading && itemCount == 0 -> StatusPanel("Loading…", edgeOnly.fillMaxSize(), loading = true)
        errorMessage != null && itemCount == 0 ->
          StatusPanel(
            message = errorMessage ?: "Content could not be loaded",
            modifier = edgeOnly.fillMaxSize(),
            actionLabel = "Try again",
            onAction = { load(tab, query.trim().takeIf(String::isNotBlank)) },
          )
        itemCount == 0 && !showContinueRow ->
          StatusPanel(
            if (tab == CatalogTab.MY_LIST) "Nothing saved yet. Open a title and choose Save to add it here."
            else "Nothing found. Try another title.",
            edgeOnly.fillMaxSize(),
          )
        else ->
          LazyVerticalGrid(
            state = gridState,
            columns = GridCells.Adaptive(minSize = if (narrow) 132.dp else 158.dp),
            modifier = Modifier.fillMaxSize(),
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
            if (searchActive) {
              // One search asks both TMDB endpoints, so both kinds always come back. Which is
              // shown first follows the tab the search was typed on: asking the TV Shows tab for
              // a series and being handed a screenful of films is the wrong answer arriving in
              // front of the right one, and on a television that is a page of scrolling to undo.
              val showsLead = tab == CatalogTab.SHOWS
              val movieResults: LazyGridScope.(Boolean) -> Unit = { leading ->
                if (movies.isNotEmpty()) {
                  item(span = { GridItemSpan(maxLineSpan) }) {
                    SectionHeading(heading = "Movies (${movies.size})", itemCount = movies.size, narrow = narrow)
                  }
                  items(items = movies, key = { "movie-${it.id}" }) { movie ->
                    PosterCard(
                      title = movie.title,
                      subtitle = movie.year ?: "—",
                      rating = movie.voteAverage,
                      posterUrl = movie.posterUrl,
                      actionLabel = "Open ${movie.title}",
                      watched = historyStore.find(vidfastMovieUrl(movie.id))?.completed == true,
                      onClick = { onOpenMovie(movie) },
                      onDwell = { onConsidering(movie.toPlaybackContext()) },
                      modifier =
                        gridEntryModifier(
                          leading && movie.id == movies.firstOrNull()?.id,
                          gridFocusRequester,
                        ) { lastContentFocusRequester = it },
                    )
                  }
                }
              }
              val showResults: LazyGridScope.(Boolean) -> Unit = { leading ->
                if (shows.isNotEmpty()) {
                  item(span = { GridItemSpan(maxLineSpan) }) {
                    SectionHeading(heading = "TV Shows (${shows.size})", itemCount = shows.size, narrow = narrow)
                  }
                  items(items = shows, key = { "show-${it.id}" }) { show ->
                    PosterCard(
                      title = show.name,
                      subtitle = show.year ?: "—",
                      rating = show.voteAverage,
                      posterUrl = show.posterUrl,
                      actionLabel = "Open ${show.name}",
                      onClick = { onOpenShow(show) },
                      modifier =
                        gridEntryModifier(
                          leading && show.id == shows.firstOrNull()?.id,
                          gridFocusRequester,
                        ) { lastContentFocusRequester = it },
                    )
                  }
                }
              }
              // The pad lands on the first card of whichever section is actually on screen first,
              // so an empty leading section hands the grid's entry point to the other one.
              if (showsLead) {
                showResults(this, true)
                movieResults(this, shows.isEmpty())
              } else {
                movieResults(this, true)
                showResults(this, movies.isEmpty())
              }
            } else if (itemCount > 0) {
              item(span = { GridItemSpan(maxLineSpan) }) {
                SectionHeading(heading = heading, itemCount = itemCount, narrow = narrow)
              }
              when (tab) {
                CatalogTab.MOVIES ->
                  items(items = movies, key = { it.id }) { movie ->
                    PosterCard(
                      title = movie.title,
                      subtitle = movie.year ?: "—",
                      rating = movie.voteAverage,
                      posterUrl = movie.posterUrl,
                      actionLabel = "Open ${movie.title}",
                      watched = historyStore.find(vidfastMovieUrl(movie.id))?.completed == true,
                      onClick = { onOpenMovie(movie) },
                      onDwell = { onConsidering(movie.toPlaybackContext()) },
                      modifier =
                        gridEntryModifier(movie.id == movies.firstOrNull()?.id, gridFocusRequester) {
                          lastContentFocusRequester = it
                        },
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
                      modifier =
                        gridEntryModifier(show.id == shows.firstOrNull()?.id, gridFocusRequester) {
                          lastContentFocusRequester = it
                        },
                    )
                  }
                CatalogTab.MY_LIST ->
                  items(items = savedItems, key = { "${it.kind}-${it.id}" }) { item ->
                    PosterCard(
                      title = item.title,
                      subtitle = item.year ?: "—",
                      rating = item.voteAverage,
                      posterUrl = item.posterUrl,
                      actionLabel = "Open ${item.title}",
                      onClick = {
                        if (item.kind == LibraryKind.SHOW) onOpenShow(item.toShow())
                        else onOpenMovie(item.toMovie())
                      },
                      modifier =
                        gridEntryModifier(item.id == savedItems.firstOrNull()?.id, gridFocusRequester) {
                          lastContentFocusRequester = it
                        },
                    )
                  }
              }
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
