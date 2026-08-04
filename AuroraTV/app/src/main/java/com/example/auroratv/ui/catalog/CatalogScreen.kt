package com.example.auroratv.ui.catalog

import android.util.Log
import androidx.compose.animation.animateColorAsState
import androidx.compose.animation.core.animateFloatAsState
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
import androidx.compose.material.icons.filled.Language
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
import com.example.auroratv.link.RemoteUiBridge
import com.example.auroratv.theme.AuroraMint
import com.example.auroratv.theme.DeepSpace
import com.example.auroratv.theme.MutedBlue
import com.example.auroratv.theme.NightSurface
import com.example.auroratv.theme.SoftWhite
import java.net.SocketTimeoutException
import java.net.UnknownHostException
import javax.net.ssl.SSLException
import kotlinx.coroutines.async
import kotlinx.coroutines.awaitAll
import kotlinx.coroutines.coroutineScope
import kotlinx.coroutines.delay
import kotlinx.coroutines.Job
import kotlinx.coroutines.launch

/** Long enough that a word typed at speed is one request, short enough to feel immediate. */
private const val SEARCH_DEBOUNCE_MS = 350L

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
  /** Absent on a television, which is the thing being pointed at rather than the thing pointing. */
  onOpenRemote: (() -> Unit)? = null,
  /** A title the viewer has paused on, worth finding the stream for before they ask. */
  onConsidering: (PlaybackContext) -> Unit = {},
  modifier: Modifier = Modifier,
) {
  val context = LocalContext.current
  val movieRepository = remember { TmdbMovieRepository(BuildConfig.TMDB_API_KEY) }
  val tvRepository = remember { TmdbTvRepository(BuildConfig.TMDB_API_KEY) }
  val historyStore = remember(context) { WatchHistoryStore(context) }
  val myListStore = remember(context) { MyListStore(context) }
  val uiPreferences = remember(context) { UiPreferencesStore(context) }
  val scope = rememberCoroutineScope()
  val focusManager = LocalFocusManager.current
  val keyboardController = LocalSoftwareKeyboardController.current
  val openWebFocusRequester = remember { FocusRequester() }
  val shortDramasFocusRequester = remember { FocusRequester() }
  val sportsFocusRequester = remember { FocusRequester() }
  val firstTabFocusRequester = remember { FocusRequester() }
  val searchFieldFocusRequester = remember { FocusRequester() }
  val searchButtonFocusRequester = remember { FocusRequester() }
  val continueRowFocusRequester = remember { FocusRequester() }
  val gridFocusRequester = remember { FocusRequester() }
  // One per listing, so a press of down lands on the next rail rather than wherever focus search
  // decides — the same reason every other list on this screen names its own neighbours.
  val railFocusRequesters = remember { List(CatalogCategory.entries.size) { FocusRequester() } }
  val gridState = rememberLazyGridState()
  val railState = rememberLazyListState()

  var tab by rememberSaveable {
    mutableStateOf(CatalogTab.entries.firstOrNull { it.name == uiPreferences.lastTab() } ?: CatalogTab.MOVIES)
  }
  var query by rememberSaveable { mutableStateOf("") }
  var searchActive by rememberSaveable { mutableStateOf(false) }
  // Every listing is on the page at once, one rail each, rather than behind a filter.
  var movieSections by remember { mutableStateOf<Map<CatalogCategory, List<TmdbMovie>>>(emptyMap()) }
  var showSections by remember { mutableStateOf<Map<CatalogCategory, List<TmdbShow>>>(emptyMap()) }
  var movies by remember { mutableStateOf<List<TmdbMovie>>(emptyList()) }
  var shows by remember { mutableStateOf<List<TmdbShow>>(emptyList()) }
  var savedItems by remember { mutableStateOf<List<LibraryItem>>(emptyList()) }
  var continueWatching by remember { mutableStateOf<List<WatchHistoryEntry>>(emptyList()) }
  var recommendedMovies by remember { mutableStateOf<List<TmdbMovie>>(emptyList()) }
  var recommendedShows by remember { mutableStateOf<List<TmdbShow>>(emptyList()) }
  var recommendationSeeds by remember { mutableStateOf<List<RecommendationSeed>>(emptyList()) }
  val recommendedFocusRequester = remember { FocusRequester() }
  var loading by remember { mutableStateOf(true) }
  var errorMessage by remember { mutableStateOf<String?>(null) }

  fun dismissKeyboard() {
    focusManager.clearFocus()
    keyboardController?.hide()
  }

  suspend fun runLoad(activeTab: CatalogTab, searchQuery: String?) {
    loading = true
    errorMessage = null
    runCatching {
          when (activeTab) {
            CatalogTab.MOVIES ->
              if (searchQuery.isNullOrBlank()) {
                // The listings are independent, so they are fetched together rather than in turn.
                movieSections = coroutineScope {
                  CatalogCategory.entries
                    .map { category -> async { category to movieRepository.movies(category) } }
                    .awaitAll()
                    .toMap()
                }
              } else {
                movies = movieRepository.searchMovies(searchQuery)
              }
            CatalogTab.SHOWS ->
              if (searchQuery.isNullOrBlank()) {
                showSections = coroutineScope {
                  CatalogCategory.entries
                    .map { category -> async { category to tvRepository.shows(category) } }
                    .awaitAll()
                    .toMap()
                }
              } else {
                shows = tvRepository.searchShows(searchQuery)
              }
            CatalogTab.MY_LIST -> savedItems = myListStore.all()
          }
        }
        .onFailure {
          Log.e("GizTvTmdb", "TMDB ${activeTab.name} load failed", it)
          errorMessage = friendlyCatalogError(it)
        }
    loading = false
  }

  fun load(activeTab: CatalogTab, searchQuery: String?) {
    scope.launch { runLoad(activeTab, searchQuery) }
  }

  fun selectTab(next: CatalogTab) {
    if (next == tab) return
    tab = next
    query = ""
    searchActive = false
    errorMessage = null
    uiPreferences.setLastTab(next.name)
    load(next, null)
  }

  fun runSearch() {
    // Nothing typed yet, so Search means "let me type": the field takes focus and the keyboard
    // comes up on purpose, rather than the moment focus passes through the row.
    if (query.isBlank()) {
      searchFieldFocusRequester.requestFocus()
      return
    }
    // Results are already arriving as the viewer types, so this only puts the keyboard away.
    keyboardController?.hide()
    focusManager.clearFocus()
    searchButtonFocusRequester.requestFocus()
  }

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
      }
      return@LaunchedEffect
    }
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
  LaunchedEffect(tab, searchActive) {
    if (searchActive || tab == CatalogTab.MY_LIST) return@LaunchedEffect
    val forShows = tab == CatalogTab.SHOWS
    val watched = historyStore.all()
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
      .onFailure { Log.e("GizTvTmdb", "Recommendations failed", it) }
  }

  // Switching tab should start at the top, not wherever the last list was scrolled to.
  LaunchedEffect(tab, searchActive, loading) {
    if (!loading && gridState.layoutInfo.totalItemsCount > 0) gridState.scrollToItem(0)
    if (!loading && railState.layoutInfo.totalItemsCount > 0) railState.scrollToItem(0)
  }

  val browsing = tab != CatalogTab.MY_LIST
  val showContinueRow = continueWatching.isNotEmpty() && !searchActive
  // Rails carry the browsable listings; a search and My List are a plain grid of one answer.
  val showRails = browsing && !searchActive
  val sections: List<Pair<CatalogCategory, Int>> =
    if (!showRails) emptyList()
    else
      CatalogCategory.entries.map { category ->
        val size =
          if (tab == CatalogTab.MOVIES) movieSections[category].orEmpty().size
          else showSections[category].orEmpty().size
        category to size
      }.filter { (_, size) -> size > 0 }
  val itemCount =
    when {
      showRails -> sections.sumOf { (_, size) -> size }
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
  // Rails past the fold are not composed, and neither a FocusRequester nor focus search can reach a
  // node that does not exist yet. So the move is driven: scroll the neighbour into view first, then
  // hand it the focus. Hoisted because the personalized rail moves into the fixed ones too.
  val leadingRailItems = (if (showContinueRow) 1 else 0) + (if (showRecommendedRail) 1 else 0)
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
  val firstRailFocusRequester = railFocusRequesters.first()
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
    // The inset belongs to the things that do not scroll. Putting it on this column boxed every
    // rail inside it too, so the card at the right edge was clipped by an invisible line instead of
    // running under the edge of the screen the way a rail is supposed to.
    val edge = if (narrow) 18.dp else 42.dp
    val edgeOnly = Modifier.padding(horizontal = edge)
    Column(
      modifier = Modifier.fillMaxSize().padding(vertical = if (compact) 14.dp else 22.dp)
    ) {
      CatalogTopBar(
        modifier = edgeOnly,
        narrow = narrow,
        compact = compact,
        onOpenWeb = { dismissKeyboard(); onOpenWeb() },
        onOpenShortDramas = { dismissKeyboard(); onOpenShortDramas() },
        onOpenSports = { dismissKeyboard(); onOpenSports() },
        onOpenRemote = onOpenRemote?.let { open -> { dismissKeyboard(); open() } },
        openWebModifier =
          Modifier.focusRequester(openWebFocusRequester).focusProperties {
            left = shortDramasFocusRequester
            down = if (browsing) searchButtonFocusRequester else firstBodyFocusRequester
          },
        shortDramasModifier =
          Modifier.focusRequester(shortDramasFocusRequester).focusProperties {
            left = sportsFocusRequester
            right = openWebFocusRequester
            down = if (browsing) searchButtonFocusRequester else firstBodyFocusRequester
          },
        sportsModifier =
          Modifier.focusRequester(sportsFocusRequester).focusProperties {
            left = firstTabFocusRequester
            right = shortDramasFocusRequester
            down = if (browsing) searchButtonFocusRequester else firstBodyFocusRequester
          },
        tabs = {
          SegmentedTabs(
            labels = CatalogTab.entries.map { it.label },
            selectedIndex = CatalogTab.entries.indexOf(tab),
            onSelect = { selectTab(CatalogTab.entries[it]) },
            firstTabFocusRequester = firstTabFocusRequester,
            down = if (browsing) searchButtonFocusRequester else firstBodyFocusRequester,
          )
        },
        search =
          if (!browsing) null
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
      Spacer(Modifier.height(if (compact) 10.dp else 16.dp))

      when {
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
        showRails ->
          LazyColumn(
            state = railState,
            // The focus group is what lets a press of down reach a rail that has not been composed
            // yet: focus search asks the list to bring it into view first.
            modifier = Modifier.weight(1f).fillMaxWidth().focusGroup(),
            contentPadding = PaddingValues(bottom = 22.dp),
            verticalArrangement = Arrangement.spacedBy(if (compact) 12.dp else 20.dp),
          ) {
            if (showContinueRow) {
              item {
                ContinueWatchingSection(
                  entries = continueWatching,
                  onResume = { entry -> onPlay(entry.toPlaybackContext()) },
                  firstCardFocusRequester = continueRowFocusRequester,
                  up = searchButtonFocusRequester,
                  down = if (showRecommendedRail) recommendedFocusRequester else firstRailFocusRequester,
                  hasGrid = sections.isNotEmpty() || showRecommendedRail,
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
                    onMoveDown = { moveToRail(0) },
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
                    onMoveDown = { moveToRail(0) },
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
            itemsIndexed(items = sections, key = { _, (category, _) -> category.name }) {
              index,
              (category, size) ->
              val railFocusRequester = railFocusRequesters[index]
              // Only the way back into the chrome is named. A rail further down the page has not
              // been composed yet, and a FocusRequester for a node that does not exist does
              // nothing at all, so moving between rails is left to focus search, which scrolls the
              // next one into view as it goes.
              val up =
                if (index == 0) {
                  when {
                    showRecommendedRail -> recommendedFocusRequester
                    showContinueRow -> continueRowFocusRequester
                    else -> searchButtonFocusRequester
                  }
                } else {
                  null
                }
              val down: FocusRequester? = null
              val railHeading =
                if (tab == CatalogTab.MOVIES) "${category.label} movies"
                else "${category.label} TV shows"
              if (tab == CatalogTab.MOVIES) {
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
        else ->
          LazyVerticalGrid(
            state = gridState,
            columns = GridCells.Adaptive(minSize = if (narrow) 132.dp else 158.dp),
            modifier = Modifier.weight(1f).fillMaxWidth(),
            // Inset as content rather than as a border, so a focused card at the edge is not
            // clipped by the boundary it sits against.
            contentPadding = PaddingValues(start = edge, end = edge, bottom = 22.dp),
            horizontalArrangement = Arrangement.spacedBy(if (narrow) 12.dp else 18.dp),
            verticalArrangement = Arrangement.spacedBy(if (narrow) 16.dp else 22.dp),
          ) {
            if (showContinueRow) {
              item(span = { GridItemSpan(maxLineSpan) }) {
                ContinueWatchingSection(
                  entries = continueWatching,
                  onResume = { entry -> onPlay(entry.toPlaybackContext()) },
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
 * pushed the catalog itself under the fold. The surface behind them is what makes the row read as a
 * header rather than as loose buttons scattered over the artwork, and it gives the three groups
 * inside it — who you are, where you are, where else you could go — somewhere to sit.
 */
@Composable
private fun CatalogTopBar(
  modifier: Modifier = Modifier,
  narrow: Boolean,
  compact: Boolean,
  onOpenWeb: () -> Unit,
  onOpenShortDramas: () -> Unit,
  onOpenSports: () -> Unit,
  onOpenRemote: (() -> Unit)?,
  openWebModifier: Modifier,
  shortDramasModifier: Modifier,
  sportsModifier: Modifier,
  tabs: @Composable () -> Unit,
  search: (@Composable () -> Unit)?,
) {
  val shape = RoundedCornerShape(if (narrow) 20.dp else 24.dp)
  // Labels do not fit beside the wordmark on a phone, and a clipped label helps nobody; the icon
  // carries it there and the spoken label stays on for a screen reader either way.
  val labelled = !narrow
  val actions: @Composable () -> Unit = {
    Row(
      horizontalArrangement = Arrangement.spacedBy(if (labelled) 8.dp else 6.dp),
      verticalAlignment = Alignment.CenterVertically,
    ) {
      CatalogActionButton(
        label = "Sports",
        icon = Icons.Filled.SportsBasketball,
        showLabel = labelled,
        onClick = onOpenSports,
        modifier = sportsModifier,
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
          horizontal = if (narrow) 12.dp else 16.dp,
          vertical = if (compact) 10.dp else 13.dp,
        ),
    verticalArrangement = Arrangement.spacedBy(if (compact) 9.dp else 12.dp),
  ) {
    if (narrow) {
      Row(modifier = Modifier.fillMaxWidth(), verticalAlignment = Alignment.CenterVertically) {
        CatalogWordmark(compact = true)
        Spacer(Modifier.weight(1f))
        actions()
      }
      tabs()
    } else {
      Row(modifier = Modifier.fillMaxWidth(), verticalAlignment = Alignment.CenterVertically) {
        CatalogWordmark(compact = false)
        Spacer(Modifier.width(24.dp))
        tabs()
        Spacer(Modifier.weight(1f))
        actions()
      }
    }
    search?.invoke()
  }
}

@Composable
private fun CatalogWordmark(compact: Boolean) {
  Row(verticalAlignment = Alignment.CenterVertically) {
    GizTvMark(modifier = Modifier.size(if (compact) 30.dp else 34.dp), cornerRadius = 10.dp)
    Spacer(Modifier.width(10.dp))
    Column {
      Text(
        "GIZTV",
        color = SoftWhite,
        fontWeight = FontWeight.Black,
        letterSpacing = 2.5.sp,
        fontSize = if (compact) 16.sp else 18.sp,
      )
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

