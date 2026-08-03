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
import androidx.compose.runtime.Composable
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
import androidx.compose.ui.semantics.Role
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
import kotlinx.coroutines.launch

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
  var loading by remember { mutableStateOf(true) }
  var errorMessage by remember { mutableStateOf<String?>(null) }

  fun dismissKeyboard() {
    focusManager.clearFocus()
    keyboardController?.hide()
  }

  fun load(activeTab: CatalogTab, searchQuery: String?) {
    scope.launch {
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
    keyboardController?.hide()
    focusManager.clearFocus()
    searchButtonFocusRequester.requestFocus()
    searchActive = true
    load(tab, query.trim())
  }

  // Re-read on every entry so progress and saved titles reflect what just happened in the player.
  LaunchedEffect(Unit) {
    continueWatching = historyStore.continueWatching()
    load(tab, null)
    firstTabFocusRequester.requestFocus()
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
    Column(
      modifier =
        Modifier.fillMaxSize().padding(
          horizontal = if (narrow) 18.dp else 42.dp,
          vertical = if (compact) 14.dp else 22.dp,
        )
    ) {
      CatalogTopBar(
        narrow = narrow,
        onOpenWeb = { dismissKeyboard(); onOpenWeb() },
        onOpenShortDramas = { dismissKeyboard(); onOpenShortDramas() },
        openWebModifier =
          Modifier.focusRequester(openWebFocusRequester).focusProperties {
            left = shortDramasFocusRequester
            down = if (browsing) searchButtonFocusRequester else firstBodyFocusRequester
          },
        shortDramasModifier =
          Modifier.focusRequester(shortDramasFocusRequester).focusProperties {
            left = firstTabFocusRequester
            right = openWebFocusRequester
            down = if (browsing) searchButtonFocusRequester else firstBodyFocusRequester
          },
        tabs = {
          ChipRow(
            labels = CatalogTab.entries.map { it.label },
            selectedIndex = CatalogTab.entries.indexOf(tab),
            onSelect = { selectTab(CatalogTab.entries[it]) },
            firstChipFocusRequester = firstTabFocusRequester,
            down = if (browsing) searchButtonFocusRequester else firstBodyFocusRequester,
            semanticsRole = Role.Tab,
          )
        },
      )
      if (browsing) {
        Spacer(Modifier.height(if (compact) 8.dp else 12.dp))
        CatalogSearchRow(
          query = query,
          placeholder = if (tab == CatalogTab.MOVIES) "Search movies…" else "Search shows…",
          onQueryChanged = { query = it },
          onSearch = ::runSearch,
          searchFieldFocusRequester = searchFieldFocusRequester,
          searchButtonFocusRequester = searchButtonFocusRequester,
          tabFocusRequester = firstTabFocusRequester,
          bodyFocusRequester = firstBodyFocusRequester.takeIf { itemCount > 0 || showContinueRow },
        )
      }
      Spacer(Modifier.height(if (compact) 8.dp else 14.dp))

      when {
        loading -> StatusPanel("Loading…", Modifier.weight(1f), loading = true)
        errorMessage != null ->
          StatusPanel(
            message = errorMessage ?: "Content could not be loaded",
            modifier = Modifier.weight(1f),
            actionLabel = "Try again",
            onAction = { load(tab, query.trim().takeIf(String::isNotBlank)) },
          )
        itemCount == 0 && !showContinueRow ->
          StatusPanel(
            if (tab == CatalogTab.MY_LIST) "Nothing saved yet. Open a title and choose Save to add it here."
            else "Nothing found. Try another title.",
            Modifier.weight(1f),
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
                  down = firstRailFocusRequester,
                  hasGrid = sections.isNotEmpty(),
                )
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
                  if (showContinueRow) continueRowFocusRequester else searchButtonFocusRequester
                } else {
                  null
                }
              val down: FocusRequester? = null
              // Rails past the fold are not composed, and neither a FocusRequester nor focus search
              // can reach a node that does not exist yet. So the move is driven: scroll the
              // neighbour into view first, then hand it the focus.
              val moveToRail: (Int) -> Unit = { target ->
                scope.launch {
                  railState.scrollToItem(target + if (showContinueRow) 1 else 0)
                  runCatching { railFocusRequesters[target].requestFocus() }
                    .onFailure {
                      // One frame later the rail has certainly been placed.
                      withFrameNanos {}
                      runCatching { railFocusRequesters[target].requestFocus() }
                    }
                }
                Unit
              }
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
            contentPadding = PaddingValues(bottom = 22.dp),
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
) {
  Column {
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
 * Wordmark, tabs and the web button on one line.
 *
 * Stacking these separately cost four rows of chrome before any artwork; on a 10-foot layout that
 * pushed the catalog itself under the fold.
 */
@Composable
private fun CatalogTopBar(
  narrow: Boolean,
  onOpenWeb: () -> Unit,
  onOpenShortDramas: () -> Unit,
  openWebModifier: Modifier,
  shortDramasModifier: Modifier,
  tabs: @Composable () -> Unit,
) {
  val actions: @Composable () -> Unit = {
    Row(horizontalArrangement = Arrangement.spacedBy(8.dp), verticalAlignment = Alignment.CenterVertically) {
      CatalogButton(label = "Short dramas", onClick = onOpenShortDramas, modifier = shortDramasModifier)
      CatalogButton(label = "Open web", onClick = onOpenWeb, modifier = openWebModifier)
    }
  }
  if (narrow) {
    Column(verticalArrangement = Arrangement.spacedBy(10.dp)) {
      Row(modifier = Modifier.fillMaxWidth(), verticalAlignment = Alignment.CenterVertically) {
        CatalogWordmark()
        Spacer(Modifier.weight(1f))
        actions()
      }
      tabs()
    }
  } else {
    Row(modifier = Modifier.fillMaxWidth(), verticalAlignment = Alignment.CenterVertically) {
      CatalogWordmark()
      Spacer(Modifier.width(26.dp))
      tabs()
      Spacer(Modifier.weight(1f))
      actions()
    }
  }
}

@Composable
private fun CatalogWordmark() {
  Row(verticalAlignment = Alignment.CenterVertically) {
    GizTvMark(modifier = Modifier.size(34.dp), cornerRadius = 10.dp)
    Spacer(Modifier.width(11.dp))
    Text("GIZTV", color = SoftWhite, fontWeight = FontWeight.Black, letterSpacing = 2.5.sp, fontSize = 18.sp)
    Spacer(Modifier.width(9.dp))
    Box(
      modifier =
        Modifier.clip(RoundedCornerShape(9.dp)).background(AuroraMint.copy(alpha = .14f))
          .border(1.dp, AuroraMint.copy(alpha = .55f), RoundedCornerShape(9.dp)).padding(horizontal = 7.dp, vertical = 3.dp),
      contentAlignment = Alignment.Center,
    ) {
      Text("v${BuildConfig.VERSION_NAME}", color = AuroraMint, fontWeight = FontWeight.Bold, fontSize = 9.sp)
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
    SectionHeading(
      heading = heading,
      itemCount = items.size,
      narrow = narrow,
      attribution = attribution,
    )
    LazyRow(
      modifier = Modifier.fillMaxWidth().focusGroup(),
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
    CatalogSearchField(query, placeholder, onQueryChanged, onSearch, fieldModifier.weight(1f))
    CatalogButton("Search", onSearch, buttonModifier)
  }
}

