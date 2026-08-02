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
import androidx.compose.foundation.lazy.LazyRow
import androidx.compose.foundation.lazy.grid.GridCells
import androidx.compose.foundation.lazy.grid.GridItemSpan
import androidx.compose.foundation.lazy.grid.LazyVerticalGrid
import androidx.compose.foundation.lazy.grid.items
import androidx.compose.foundation.lazy.grid.rememberLazyGridState
import androidx.compose.foundation.lazy.items
import androidx.compose.foundation.shape.RoundedCornerShape
import androidx.compose.foundation.text.BasicTextField
import androidx.compose.foundation.text.KeyboardActions
import androidx.compose.foundation.text.KeyboardOptions
import androidx.compose.runtime.Composable
import androidx.compose.runtime.LaunchedEffect
import androidx.compose.runtime.getValue
import androidx.compose.runtime.mutableStateOf
import androidx.compose.runtime.remember
import androidx.compose.runtime.rememberCoroutineScope
import androidx.compose.runtime.saveable.rememberSaveable
import androidx.compose.runtime.setValue
import androidx.compose.ui.Alignment
import androidx.compose.ui.Modifier
import androidx.compose.ui.draw.clip
import androidx.compose.ui.focus.FocusRequester
import androidx.compose.ui.focus.focusProperties
import androidx.compose.ui.focus.focusRequester
import androidx.compose.ui.focus.onFocusChanged
import androidx.compose.ui.graphics.Brush
import androidx.compose.ui.graphics.Color
import androidx.compose.ui.graphics.SolidColor
import androidx.compose.ui.graphics.graphicsLayer
import androidx.compose.ui.input.pointer.pointerInput
import androidx.compose.ui.platform.LocalContext
import androidx.compose.ui.platform.LocalFocusManager
import androidx.compose.ui.platform.LocalSoftwareKeyboardController
import androidx.compose.ui.semantics.Role
import androidx.compose.ui.semantics.contentDescription
import androidx.compose.ui.semantics.role
import androidx.compose.ui.semantics.selected
import androidx.compose.ui.semantics.semantics
import androidx.compose.ui.text.TextStyle
import androidx.compose.ui.text.font.FontWeight
import androidx.compose.ui.text.input.ImeAction
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
import com.example.auroratv.theme.AuroraBlue
import com.example.auroratv.theme.AuroraMint
import com.example.auroratv.theme.DeepSpace
import com.example.auroratv.theme.MutedBlue
import com.example.auroratv.theme.NightSurface
import com.example.auroratv.theme.SoftWhite
import java.net.SocketTimeoutException
import java.net.UnknownHostException
import java.util.Locale
import javax.net.ssl.SSLException
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
  val firstTabFocusRequester = remember { FocusRequester() }
  val categoryFocusRequester = remember { FocusRequester() }
  val searchFieldFocusRequester = remember { FocusRequester() }
  val searchButtonFocusRequester = remember { FocusRequester() }
  val continueRowFocusRequester = remember { FocusRequester() }
  val gridFocusRequester = remember { FocusRequester() }
  val gridState = rememberLazyGridState()

  var tab by rememberSaveable {
    mutableStateOf(CatalogTab.entries.firstOrNull { it.name == uiPreferences.lastTab() } ?: CatalogTab.MOVIES)
  }
  var category by rememberSaveable {
    mutableStateOf(
      CatalogCategory.entries.firstOrNull { it.name == uiPreferences.lastCategory(tab.name) }
        ?: CatalogCategory.POPULAR
    )
  }
  var query by rememberSaveable { mutableStateOf("") }
  var searchActive by rememberSaveable { mutableStateOf(false) }
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

  fun load(activeTab: CatalogTab, activeCategory: CatalogCategory, searchQuery: String?) {
    scope.launch {
      loading = true
      errorMessage = null
      runCatching {
          when (activeTab) {
            CatalogTab.MOVIES ->
              movies =
                if (searchQuery.isNullOrBlank()) movieRepository.movies(activeCategory)
                else movieRepository.searchMovies(searchQuery)
            CatalogTab.SHOWS ->
              shows =
                if (searchQuery.isNullOrBlank()) tvRepository.shows(activeCategory)
                else tvRepository.searchShows(searchQuery)
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
    category =
      CatalogCategory.entries.firstOrNull { it.name == uiPreferences.lastCategory(next.name) }
        ?: CatalogCategory.POPULAR
    load(next, category, null)
  }

  fun selectCategory(next: CatalogCategory) {
    if (next == category && !searchActive) return
    category = next
    query = ""
    searchActive = false
    uiPreferences.setLastCategory(tab.name, next.name)
    load(tab, next, null)
  }

  fun runSearch() {
    keyboardController?.hide()
    focusManager.clearFocus()
    searchButtonFocusRequester.requestFocus()
    searchActive = query.isNotBlank()
    load(tab, category, query.trim().takeIf(String::isNotBlank))
  }

  // Re-read on every entry so progress and saved titles reflect what just happened in the player.
  LaunchedEffect(Unit) {
    continueWatching = historyStore.continueWatching()
    load(tab, category, null)
    firstTabFocusRequester.requestFocus()
  }

  // Switching tab or category should start at the top, not wherever the last list was scrolled to.
  LaunchedEffect(tab, category, searchActive, loading) {
    if (!loading && gridState.layoutInfo.totalItemsCount > 0) gridState.scrollToItem(0)
  }

  val browsing = tab != CatalogTab.MY_LIST
  val showContinueRow = continueWatching.isNotEmpty() && !searchActive
  val itemCount =
    when (tab) {
      CatalogTab.MOVIES -> movies.size
      CatalogTab.SHOWS -> shows.size
      CatalogTab.MY_LIST -> savedItems.size
    }
  val heading =
    when {
      searchActive -> "Results for “${query.trim()}”"
      tab == CatalogTab.MY_LIST -> "My List"
      tab == CatalogTab.MOVIES -> "${category.label} movies"
      else -> "${category.label} TV shows"
    }
  val firstBodyFocusRequester = if (showContinueRow) continueRowFocusRequester else gridFocusRequester

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
        openWebModifier =
          Modifier.focusRequester(openWebFocusRequester).focusProperties {
            left = firstTabFocusRequester
            down = if (browsing) categoryFocusRequester else firstBodyFocusRequester
          },
        tabs = {
          ChipRow(
            labels = CatalogTab.entries.map { it.label },
            selectedIndex = CatalogTab.entries.indexOf(tab),
            onSelect = { selectTab(CatalogTab.entries[it]) },
            firstChipFocusRequester = firstTabFocusRequester,
            down = if (browsing) categoryFocusRequester else firstBodyFocusRequester,
            semanticsRole = Role.Tab,
          )
        },
      )
      if (browsing) {
        Spacer(Modifier.height(if (compact) 8.dp else 12.dp))
        CatalogFilterRow(
          narrow = narrow,
          selectedCategory = if (searchActive) null else category,
          onSelectCategory = ::selectCategory,
          query = query,
          placeholder = if (tab == CatalogTab.MOVIES) "Search movies…" else "Search shows…",
          onQueryChanged = { query = it },
          onSearch = ::runSearch,
          categoryFocusRequester = categoryFocusRequester,
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
            onAction = { load(tab, category, query.trim().takeIf(String::isNotBlank)) },
          )
        itemCount == 0 && !showContinueRow ->
          StatusPanel(
            if (tab == CatalogTab.MY_LIST) "Nothing saved yet. Open a title and choose Save to add it here."
            else "Nothing found. Try another title.",
            Modifier.weight(1f),
          )
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
private fun SectionHeading(heading: String, itemCount: Int, narrow: Boolean) {
  Column {
    Row(verticalAlignment = Alignment.CenterVertically) {
      Text(heading, color = SoftWhite, fontWeight = FontWeight.Black, fontSize = if (narrow) 17.sp else 19.sp)
      Spacer(Modifier.width(10.dp))
      if (itemCount > 0) {
        Text("$itemCount titles", color = AuroraMint, fontWeight = FontWeight.Bold, fontSize = 11.sp)
      }
      Spacer(Modifier.weight(1f))
      if (!narrow) {
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

@Composable
private fun ContinueWatchingSection(
  entries: List<WatchHistoryEntry>,
  onResume: (WatchHistoryEntry) -> Unit,
  firstCardFocusRequester: FocusRequester,
  up: FocusRequester,
  down: FocusRequester,
  hasGrid: Boolean,
) {
  Column {
    Text("Continue watching", color = SoftWhite, fontWeight = FontWeight.Black, fontSize = 16.sp)
    Spacer(Modifier.height(8.dp))
    LazyRow(
      modifier = Modifier.fillMaxWidth().focusGroup(),
      horizontalArrangement = Arrangement.spacedBy(12.dp),
    ) {
      items(items = entries, key = { it.pageUrl }) { entry ->
        ContinueWatchingCard(
          entry = entry,
          onClick = { onResume(entry) },
          modifier =
            if (entry.pageUrl == entries.first().pageUrl) {
              Modifier.focusRequester(firstCardFocusRequester).focusProperties {
                this.up = up
                this.down = if (hasGrid) down else FocusRequester.Default
              }
            } else {
              Modifier.focusProperties {
                this.up = up
                this.down = if (hasGrid) down else FocusRequester.Default
              }
            },
        )
      }
    }
  }
}

@Composable
private fun ContinueWatchingCard(
  entry: WatchHistoryEntry,
  onClick: () -> Unit,
  modifier: Modifier = Modifier,
) {
  var focused by remember { mutableStateOf(false) }
  val scale by animateFloatAsState(if (focused) 1.05f else 1f, label = "continue scale")
  val outline by
    animateColorAsState(if (focused) AuroraMint else SoftWhite.copy(alpha = .08f), label = "continue outline")
  // Laid out wide and short: a poster-shaped card here would push the catalog below the fold.
  Row(
    modifier =
      modifier.width(248.dp).graphicsLayer { scaleX = scale; scaleY = scale }
        .clip(RoundedCornerShape(14.dp)).background(NightSurface)
        .border(if (focused) 3.dp else 1.dp, outline, RoundedCornerShape(14.dp))
        .onFocusChanged { focused = it.isFocused }.clickable(onClick = onClick)
        .semantics {
          role = Role.Button
          contentDescription = "Resume ${entry.title}${entry.subtitle?.let { ", $it" } ?: ""}"
        }
        .padding(8.dp),
    verticalAlignment = Alignment.CenterVertically,
  ) {
    // The fixed width keeps the progress bar tied to the poster instead of the whole card.
    Box(modifier = Modifier.width(54.dp), contentAlignment = Alignment.BottomStart) {
      TmdbArtwork(
        url = entry.posterUrl,
        contentDescription = "${entry.title} poster",
        modifier = Modifier.fillMaxWidth().aspectRatio(2f / 3f).clip(RoundedCornerShape(8.dp)),
        compact = true,
      )
      Box(modifier = Modifier.fillMaxWidth().height(4.dp).background(DeepSpace.copy(alpha = .7f))) {
        Box(Modifier.fillMaxWidth(entry.progressFraction).fillMaxHeight().background(AuroraMint))
      }
    }
    Spacer(Modifier.width(10.dp))
    Column(Modifier.weight(1f)) {
      Text(
        entry.title,
        color = SoftWhite,
        fontWeight = FontWeight.Bold,
        fontSize = 12.sp,
        maxLines = 2,
        overflow = TextOverflow.Ellipsis,
        lineHeight = 15.sp,
      )
      entry.subtitle?.takeIf { it.isNotBlank() }?.let {
        Text(it, color = MutedBlue, fontSize = 10.sp, maxLines = 1, overflow = TextOverflow.Ellipsis)
      }
      entry.resumeLabel?.let {
        Text(it, color = AuroraMint, fontWeight = FontWeight.Bold, fontSize = 10.sp, maxLines = 1)
      }
    }
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
  openWebModifier: Modifier,
  tabs: @Composable () -> Unit,
) {
  if (narrow) {
    Column(verticalArrangement = Arrangement.spacedBy(10.dp)) {
      Row(modifier = Modifier.fillMaxWidth(), verticalAlignment = Alignment.CenterVertically) {
        CatalogWordmark()
        Spacer(Modifier.weight(1f))
        CatalogButton(label = "Open web", onClick = onOpenWeb, modifier = openWebModifier)
      }
      tabs()
    }
  } else {
    Row(modifier = Modifier.fillMaxWidth(), verticalAlignment = Alignment.CenterVertically) {
      CatalogWordmark()
      Spacer(Modifier.width(26.dp))
      tabs()
      Spacer(Modifier.weight(1f))
      CatalogButton(label = "Open web", onClick = onOpenWeb, modifier = openWebModifier)
    }
  }
}

@Composable
private fun CatalogWordmark() {
  Row(verticalAlignment = Alignment.CenterVertically) {
    GizTvMark(modifier = Modifier.size(34.dp), cornerRadius = 10.dp)
    Spacer(Modifier.width(11.dp))
    Text("GIZTV", color = SoftWhite, fontWeight = FontWeight.Black, letterSpacing = 2.5.sp, fontSize = 18.sp)
  }
}

/** A horizontal strip of selectable chips wired for D-pad entry and exit. */
@Composable
private fun ChipRow(
  labels: List<String>,
  selectedIndex: Int,
  onSelect: (Int) -> Unit,
  firstChipFocusRequester: FocusRequester,
  semanticsRole: Role,
  up: FocusRequester? = null,
  down: FocusRequester? = null,
  compactChips: Boolean = false,
) {
  Row(modifier = Modifier.focusGroup(), horizontalArrangement = Arrangement.spacedBy(8.dp)) {
    labels.forEachIndexed { index, label ->
      val chipModifier =
        Modifier.focusProperties {
          if (up != null) this.up = up
          if (down != null) this.down = down
        }.let { if (index == 0) it.focusRequester(firstChipFocusRequester) else it }
      CatalogChip(
        label = label,
        selected = index == selectedIndex,
        compact = compactChips,
        semanticsRole = semanticsRole,
        onSelect = { onSelect(index) },
        modifier = chipModifier,
      )
    }
  }
}

@Composable
private fun CatalogChip(
  label: String,
  selected: Boolean,
  compact: Boolean,
  semanticsRole: Role,
  onSelect: () -> Unit,
  modifier: Modifier = Modifier,
) {
  var focused by remember { mutableStateOf(false) }
  val scale by animateFloatAsState(if (focused) 1.06f else 1f, label = "$label scale")
  val background by
    animateColorAsState(
      when {
        selected -> AuroraMint
        focused -> SoftWhite.copy(alpha = .18f)
        else -> NightSurface
      },
      label = "$label background",
    )
  val outline by
    animateColorAsState(if (focused) SoftWhite else SoftWhite.copy(alpha = .12f), label = "$label outline")
  Box(
    modifier =
      modifier.height(if (compact) 32.dp else 38.dp).graphicsLayer { scaleX = scale; scaleY = scale }
        .clip(RoundedCornerShape(19.dp)).background(background)
        .border(if (focused) 2.dp else 1.dp, outline, RoundedCornerShape(19.dp))
        .onFocusChanged { focused = it.isFocused }.clickable(onClick = onSelect)
        .semantics {
          role = semanticsRole
          this.selected = selected
          contentDescription = label
        }
        .padding(horizontal = if (compact) 14.dp else 18.dp),
    contentAlignment = Alignment.Center,
  ) {
    Text(
      label,
      color = if (selected) DeepSpace else SoftWhite,
      fontWeight = FontWeight.Black,
      fontSize = if (compact) 11.sp else 12.sp,
    )
  }
}

/** Listing chips and the search box share one line, so filtering costs a single row. */
@Composable
private fun CatalogFilterRow(
  narrow: Boolean,
  selectedCategory: CatalogCategory?,
  onSelectCategory: (CatalogCategory) -> Unit,
  query: String,
  placeholder: String,
  onQueryChanged: (String) -> Unit,
  onSearch: () -> Unit,
  categoryFocusRequester: FocusRequester,
  searchFieldFocusRequester: FocusRequester,
  searchButtonFocusRequester: FocusRequester,
  tabFocusRequester: FocusRequester,
  bodyFocusRequester: FocusRequester?,
) {
  val categories: @Composable () -> Unit = {
    ChipRow(
      labels = CatalogCategory.entries.map { it.label },
      selectedIndex = selectedCategory?.let { CatalogCategory.entries.indexOf(it) } ?: -1,
      onSelect = { onSelectCategory(CatalogCategory.entries[it]) },
      firstChipFocusRequester = categoryFocusRequester,
      up = tabFocusRequester,
      down = bodyFocusRequester,
      semanticsRole = Role.Tab,
      compactChips = true,
    )
  }
  val fieldModifier =
    Modifier.focusRequester(searchFieldFocusRequester).focusProperties {
      up = tabFocusRequester
      left = categoryFocusRequester
      down = bodyFocusRequester ?: FocusRequester.Default
      right = searchButtonFocusRequester
    }.remoteFocusNavigation(up = tabFocusRequester, down = bodyFocusRequester)
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
  if (narrow) {
    Column(modifier = Modifier.focusGroup(), verticalArrangement = Arrangement.spacedBy(9.dp)) {
      categories()
      Row(horizontalArrangement = Arrangement.spacedBy(9.dp), verticalAlignment = Alignment.CenterVertically) {
        CatalogSearchField(query, placeholder, onQueryChanged, onSearch, fieldModifier.weight(1f))
        CatalogButton("Search", onSearch, buttonModifier)
      }
    }
  } else {
    Row(
      modifier = Modifier.focusGroup().fillMaxWidth(),
      horizontalArrangement = Arrangement.spacedBy(10.dp),
      verticalAlignment = Alignment.CenterVertically,
    ) {
      categories()
      Spacer(Modifier.width(6.dp))
      CatalogSearchField(query, placeholder, onQueryChanged, onSearch, fieldModifier.weight(1f))
      CatalogButton("Search", onSearch, buttonModifier)
    }
  }
}

@Composable
private fun CatalogSearchField(
  value: String,
  placeholder: String,
  onValueChanged: (String) -> Unit,
  onSearch: () -> Unit,
  modifier: Modifier,
) {
  var focused by remember { mutableStateOf(false) }
  val border by animateColorAsState(if (focused) AuroraBlue else SoftWhite.copy(alpha = .14f), label = "search border")
  BasicTextField(
    value = value,
    onValueChange = onValueChanged,
    modifier =
      modifier.height(38.dp).clip(RoundedCornerShape(19.dp)).background(NightSurface)
        .border(if (focused) 2.dp else 1.dp, border, RoundedCornerShape(19.dp))
        .onFocusChanged { focused = it.isFocused }
        .semantics { contentDescription = "Catalog search" },
    textStyle = TextStyle(color = SoftWhite, fontSize = 13.sp, fontWeight = FontWeight.Medium),
    singleLine = true,
    cursorBrush = SolidColor(AuroraMint),
    keyboardOptions = KeyboardOptions(imeAction = ImeAction.Search),
    keyboardActions = KeyboardActions(onSearch = { onSearch() }),
    decorationBox = { inner ->
      Box(Modifier.fillMaxSize().padding(horizontal = 16.dp), contentAlignment = Alignment.CenterStart) {
        if (value.isBlank()) Text(placeholder, color = MutedBlue, fontSize = 13.sp)
        inner()
      }
    },
  )
}

@Composable
private fun PosterCard(
  title: String,
  subtitle: String,
  rating: Double,
  posterUrl: String?,
  actionLabel: String,
  onClick: () -> Unit,
  modifier: Modifier = Modifier,
  watched: Boolean = false,
) {
  var focused by remember { mutableStateOf(false) }
  val scale by animateFloatAsState(if (focused) 1.045f else 1f, label = "card focus scale")
  val outline by animateColorAsState(if (focused) AuroraMint else SoftWhite.copy(alpha = .08f), label = "card outline")
  Column(
    modifier =
      modifier.graphicsLayer { scaleX = scale; scaleY = scale }.clip(RoundedCornerShape(16.dp))
        .background(NightSurface).border(if (focused) 3.dp else 1.dp, outline, RoundedCornerShape(16.dp))
        .onFocusChanged { focused = it.isFocused }.clickable(onClick = onClick)
        .semantics { role = Role.Button; contentDescription = actionLabel }
  ) {
    Box {
      TmdbArtwork(
        url = posterUrl,
        contentDescription = "$title poster",
        modifier = Modifier.fillMaxWidth().aspectRatio(2f / 3f),
        fallbackLabel = "No poster",
      )
      if (watched) {
        Box(
          modifier =
            Modifier.align(Alignment.TopEnd).padding(7.dp).size(24.dp)
              .clip(RoundedCornerShape(12.dp)).background(AuroraMint),
          contentAlignment = Alignment.Center,
        ) {
          Text("✓", color = DeepSpace, fontWeight = FontWeight.Black, fontSize = 13.sp)
        }
      }
    }
    Column(Modifier.padding(horizontal = 11.dp, vertical = 10.dp)) {
      Text(
        title,
        color = SoftWhite,
        fontWeight = FontWeight.Bold,
        fontSize = 14.sp,
        maxLines = 2,
        minLines = 2,
        overflow = TextOverflow.Ellipsis,
        lineHeight = 17.sp,
      )
      Spacer(Modifier.height(5.dp))
      Row(verticalAlignment = Alignment.CenterVertically) {
        Text(subtitle, color = MutedBlue, fontSize = 11.sp)
        Spacer(Modifier.weight(1f))
        if (rating > 0) {
          Text("★ ${String.format(Locale.US, "%.1f", rating)}", color = AuroraMint, fontSize = 11.sp)
        }
      }
    }
  }
}
