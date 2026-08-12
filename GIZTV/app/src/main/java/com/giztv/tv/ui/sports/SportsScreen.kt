package com.giztv.tv.ui.sports

import android.util.Log
import androidx.activity.compose.BackHandler
import androidx.compose.animation.animateColorAsState
import androidx.compose.animation.core.LinearEasing
import androidx.compose.animation.core.RepeatMode
import androidx.compose.animation.core.animateFloat
import androidx.compose.animation.core.animateFloatAsState
import androidx.compose.animation.core.infiniteRepeatable
import androidx.compose.animation.core.rememberInfiniteTransition
import androidx.compose.animation.core.tween
import androidx.compose.foundation.background
import androidx.compose.foundation.border
import androidx.compose.foundation.clickable
import androidx.compose.foundation.focusGroup
import androidx.compose.foundation.gestures.detectTapGestures
import androidx.compose.foundation.horizontalScroll
import androidx.compose.foundation.rememberScrollState
import androidx.compose.foundation.layout.Arrangement
import androidx.compose.foundation.layout.Box
import androidx.compose.foundation.layout.BoxWithConstraints
import androidx.compose.foundation.layout.Column
import androidx.compose.foundation.layout.PaddingValues
import androidx.compose.foundation.layout.Row
import androidx.compose.foundation.layout.Spacer
import androidx.compose.foundation.layout.fillMaxSize
import androidx.compose.foundation.layout.fillMaxWidth
import androidx.compose.foundation.layout.height
import androidx.compose.foundation.layout.padding
import androidx.compose.foundation.layout.size
import androidx.compose.foundation.layout.width
import androidx.compose.foundation.lazy.grid.GridCells
import androidx.compose.foundation.lazy.grid.GridItemSpan
import androidx.compose.foundation.lazy.grid.LazyVerticalGrid
import androidx.compose.foundation.lazy.grid.items
import androidx.compose.foundation.lazy.grid.rememberLazyGridState
import androidx.compose.foundation.shape.CircleShape
import androidx.compose.foundation.shape.RoundedCornerShape
import androidx.compose.material.icons.Icons
import androidx.compose.material.icons.filled.Close
import androidx.compose.material.icons.filled.Search
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
import com.giztv.tv.data.PlaybackContext
import com.giztv.tv.data.SearchHistoryStore
import com.giztv.tv.data.SearchSection
import com.giztv.tv.theme.GizMint
import com.giztv.tv.theme.DeepSpace
import com.giztv.tv.theme.MutedBlue
import com.giztv.tv.theme.NightSurface
import com.giztv.tv.theme.SoftWhite
import com.giztv.tv.ui.catalog.CatalogButton
import com.giztv.tv.ui.catalog.CatalogIconButton
import com.giztv.tv.ui.catalog.CatalogSearchField
import com.giztv.tv.ui.catalog.ChipRow
import com.giztv.tv.ui.catalog.GizTvMark
import com.giztv.tv.ui.catalog.SearchHistoryRow
import com.giztv.tv.ui.catalog.StatusPanel
import com.giztv.tv.ui.catalog.TmdbArtwork
import com.giztv.tv.ui.catalog.remoteFocusNavigation
import kotlinx.coroutines.delay
import kotlinx.coroutines.launch

/** The red of a match in play, used only for that. */
private val LiveRed = Color(0xFFFF5A6E)

/**
 * The live sports listing.
 *
 * The whole schedule arrives in one request, so the chips filter what is already in hand rather
 * than asking again — every sport is one press away with nothing to wait for. Football leads both
 * the chips and the grid, and inside any sport a match in play comes before one about to start.
 *
 * There is no "continue watching" row here on purpose: a match is watched while it is on, and a
 * half-watched fixture from yesterday is not something to offer a viewer again.
 */
@Composable
internal fun SportsScreen(
  onPlay: (PlaybackContext) -> Unit,
  /** A fixture the viewer has paused on, worth finding the stream for before they ask. */
  onConsidering: (PlaybackContext) -> Unit = {},
  onOpenDlhdSoccer: (() -> Unit)? = null,
  onBack: () -> Unit,
  /** Phone footer already provides navigation; hide the redundant Back control. */
  hideBackButton: Boolean = false,
  requestSearchFocus: Boolean = false,
  onSearchFocusHandled: () -> Unit = {},
  modifier: Modifier = Modifier,
) {
  val context = LocalContext.current
  val scope = rememberCoroutineScope()
  val focusManager = LocalFocusManager.current
  val keyboardController = LocalSoftwareKeyboardController.current
  val backFocusRequester = remember { FocusRequester() }
  val refreshFocusRequester = remember { FocusRequester() }
  val chipFocusRequester = remember { FocusRequester() }
  val searchFieldFocusRequester = remember { FocusRequester() }
  val searchButtonFocusRequester = remember { FocusRequester() }
  val searchHistoryFocusRequester = remember { FocusRequester() }
  val gridFocusRequester = remember { FocusRequester() }
  val gridState = rememberLazyGridState()

  var selectedSport by rememberSaveable { mutableStateOf<String?>(null) }
  var query by rememberSaveable { mutableStateOf("") }
  var searchActive by rememberSaveable { mutableStateOf(false) }
  var searchExpanded by rememberSaveable { mutableStateOf(false) }
  val searchHistoryStore = remember(context) { SearchHistoryStore(context) }
  var recentSearches by remember { mutableStateOf(emptyList<String>()) }
  var events by remember { mutableStateOf<List<SportEvent>>(emptyList()) }
  var loading by remember { mutableStateOf(true) }
  var errorMessage by remember { mutableStateOf<String?>(null) }

  fun dismissKeyboard() {
    focusManager.clearFocus()
    keyboardController?.hide()
  }

  fun collapseSearchUi() {
    searchExpanded = false
    if (query.isNotBlank() || searchActive) {
      query = ""
      searchActive = false
    }
    dismissKeyboard()
  }

  fun load(refresh: Boolean) {
    scope.launch {
      loading = true
      errorMessage = null
      runCatching { if (refresh) SportsRepository.refresh() else SportsRepository.events() }
        .onSuccess { events = it }
        .onFailure {
          Log.e("GizTvSports", "Live sports load failed", it)
          errorMessage = friendlySportsError(it)
        }
      loading = false
    }
  }

  fun runSearch() {
    val trimmed = query.trim()
    if (trimmed.isBlank()) {
      searchExpanded = true
      searchFieldFocusRequester.requestFocus()
      return
    }
    dismissKeyboard()
    searchButtonFocusRequester.requestFocus()
    searchActive = true
    searchHistoryStore.record(SearchSection.SPORTS, trimmed)
    recentSearches = searchHistoryStore.recent(SearchSection.SPORTS)
  }

  /** Runs a remembered query again, filtering the schedule already in hand. */
  fun repeatSearch(searched: String) {
    query = searched
    searchExpanded = true
    searchActive = true
    dismissKeyboard()
  }

  fun clearSearchHistory() {
    searchHistoryStore.clear(SearchSection.SPORTS)
    recentSearches = emptyList()
  }

  LaunchedEffect(Unit) { recentSearches = searchHistoryStore.recent(SearchSection.SPORTS) }

  LaunchedEffect(requestSearchFocus) {
    if (!requestSearchFocus) return@LaunchedEffect
    searchExpanded = true
    withFrameNanos {}
    runCatching { searchFieldFocusRequester.requestFocus() }
    onSearchFocusHandled()
  }

  BackHandler(enabled = hideBackButton && searchExpanded) { collapseSearchUi() }

  LaunchedEffect(Unit) {
    load(refresh = false)
    if (!hideBackButton) backFocusRequester.requestFocus()
  }

  // Scores move while the page is open, so it goes back for them on its own. A refresh that fails
  // says nothing and changes nothing: the schedule already on screen is still the best answer.
  LaunchedEffect(Unit) {
    while (true) {
      delay(LIVE_REFRESH_INTERVAL_MS)
      runCatching { SportsRepository.refresh() }.onSuccess { events = it }
    }
  }

  val filters = remember(events) { sportFilters(events) }
  // Held by name rather than position: the feed decides which sports exist, and a sport can drop
  // off it between refreshes without the selection sliding onto a different one.
  val activeSport = selectedSport?.takeIf(filters::contains) ?: filters.firstOrNull()
  val visible =
    remember(events, activeSport, searchActive, query) {
      if (searchActive) searchEvents(events, query) else eventsForSport(events, activeSport)
    }
  val liveCount = remember(visible) { visible.count(SportEvent::live) }

  // A new sport starts at the top rather than wherever the previous list was scrolled to.
  LaunchedEffect(activeSport, searchActive, loading) {
    if (!loading && gridState.layoutInfo.totalItemsCount > 0) gridState.scrollToItem(0)
  }

  // Back is handled by GizTvRoot, as it is for the drama pages, so that one press leaves one
  // page.
  val heading =
    when {
      searchActive -> "Results for “${query.trim()}”"
      activeSport == null || activeSport == ALL_SPORTS -> "Today's fixtures"
      else -> "$activeSport today"
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
    val phoneDense = hideBackButton && narrow
    val showSearchRow = !phoneDense || searchExpanded || searchActive || query.isNotBlank()
    val bodyFocusRequester = gridFocusRequester.takeIf { visible.isNotEmpty() }

    Column(
      modifier =
        Modifier.fillMaxSize().padding(
          horizontal = if (narrow) 14.dp else 42.dp,
          vertical =
            when {
              phoneDense -> 8.dp
              compact -> 14.dp
              else -> 22.dp
            },
        )
    ) {
      Row(modifier = Modifier.fillMaxWidth(), verticalAlignment = Alignment.CenterVertically) {
        GizTvMark(
          modifier = Modifier.size(if (phoneDense) 26.dp else 34.dp),
          cornerRadius = if (phoneDense) 8.dp else 10.dp,
        )
        Spacer(modifier.width(if (phoneDense) 8.dp else 11.dp))
        Text(
          "LIVE SPORTS",
          color = SoftWhite,
          fontWeight = FontWeight.Black,
          letterSpacing = if (phoneDense) 2.sp else 2.5.sp,
          fontSize = if (phoneDense) 15.sp else 18.sp,
        )
        // On a phone the wordmark, the count and two buttons do not fit one line; the count is the
        // part a viewer can do without, because every live card says so itself.
        if (liveCount > 0 && !narrow) {
          Spacer(modifier.width(10.dp))
          LivePill(label = if (liveCount == 1) "1 LIVE" else "$liveCount LIVE")
        }
        Spacer(modifier.weight(1f))
        Row(
          horizontalArrangement = Arrangement.spacedBy(if (phoneDense) 4.dp else 8.dp),
          verticalAlignment = Alignment.CenterVertically,
        ) {
          if (phoneDense && !showSearchRow) {
            CatalogIconButton(
              "Search",
              Icons.Filled.Search,
              {
                searchExpanded = true
                scope.launch {
                  withFrameNanos {}
                  runCatching { searchFieldFocusRequester.requestFocus() }
                }
              },
            )
          }
          CatalogButton(
            label = "Refresh",
            onClick = { dismissKeyboard(); load(refresh = true) },
            modifier =
              Modifier.focusRequester(refreshFocusRequester).focusProperties {
                right = backFocusRequester
                down = chipFocusRequester
              },
          )
          if (onOpenDlhdSoccer != null) {
            CatalogButton(
              label = "Soccer",
              onClick = {
                dismissKeyboard()
                onOpenDlhdSoccer()
              },
            )
          }
          if (!hideBackButton) {
            CatalogButton(
              label = "Back",
              onClick = { dismissKeyboard(); onBack() },
              modifier =
                Modifier.focusRequester(backFocusRequester).focusProperties {
                  left = refreshFocusRequester
                  down = chipFocusRequester
                },
            )
          }
        }
      }
      Spacer(modifier.height(if (phoneDense) 6.dp else if (compact) 8.dp else 12.dp))
      SportsFilterRow(
        filters = filters,
        // A search answers across every sport, so no chip is the selected one while it is showing.
        selectedIndex = if (searchActive) -1 else activeSport?.let(filters::indexOf) ?: -1,
        onSelectSport = { index ->
          selectedSport = filters.getOrNull(index)
          query = ""
          searchActive = false
          if (phoneDense) searchExpanded = false
        },
        query = query,
        onQueryChanged = {
          query = it
          // Clearing the box puts the chosen sport back rather than leaving an empty result page.
          if (it.isBlank()) {
            searchActive = false
            if (phoneDense) searchExpanded = false
          }
        },
        onSearch = ::runSearch,
        showSearch = showSearchRow,
        recentSearches = recentSearches,
        onRepeatSearch = ::repeatSearch,
        onClearSearchHistory = ::clearSearchHistory,
        onCloseSearch = if (phoneDense) ({ collapseSearchUi() }) else null,
        chipFocusRequester = chipFocusRequester,
        searchFieldFocusRequester = searchFieldFocusRequester,
        searchButtonFocusRequester = searchButtonFocusRequester,
        searchHistoryFocusRequester = searchHistoryFocusRequester,
        backFocusRequester = if (hideBackButton) refreshFocusRequester else backFocusRequester,
        bodyFocusRequester = bodyFocusRequester,
        compact = phoneDense,
      )
      Spacer(modifier.height(if (phoneDense) 6.dp else if (compact) 8.dp else 14.dp))

      when {
        loading && events.isEmpty() ->
          StatusPanel("Loading today's fixtures…", Modifier.weight(1f), loading = true)
        errorMessage != null && events.isEmpty() ->
          StatusPanel(
            message = errorMessage ?: "Live sports could not be loaded",
            modifier = Modifier.weight(1f),
            actionLabel = "Try again",
            onAction = { load(refresh = true) },
          )
        visible.isEmpty() ->
          StatusPanel(
            message =
              if (searchActive) "No fixtures match “${query.trim()}”."
              else "Nothing scheduled here right now.",
            modifier = Modifier.weight(1f),
          )
        else ->
          LazyVerticalGrid(
            state = gridState,
            columns = GridCells.Adaptive(minSize = if (narrow) 240.dp else 300.dp),
            modifier = Modifier.weight(1f).fillMaxWidth(),
            contentPadding = PaddingValues(bottom = if (phoneDense) 8.dp else 22.dp),
            horizontalArrangement = Arrangement.spacedBy(if (narrow) 12.dp else 16.dp),
            verticalArrangement = Arrangement.spacedBy(if (phoneDense) 10.dp else if (narrow) 12.dp else 16.dp),
          ) {
            item(span = { GridItemSpan(maxLineSpan) }) {
              Column {
                Row(verticalAlignment = Alignment.CenterVertically) {
                  Text(
                    heading,
                    color = SoftWhite,
                    fontWeight = FontWeight.Black,
                    fontSize = if (narrow) 17.sp else 19.sp,
                  )
                  Spacer(Modifier.width(10.dp))
                  Text(
                    if (visible.size == 1) "1 fixture" else "${visible.size} fixtures",
                    color = GizMint,
                    fontWeight = FontWeight.Bold,
                    fontSize = 11.sp,
                  )
                  Spacer(Modifier.weight(1f))
                  if (!narrow) {
                    Text("hoofoot listing · times shown local", color = MutedBlue.copy(alpha = .6f), fontSize = 9.sp)
                  }
                }
                Spacer(Modifier.height(8.dp))
              }
            }
            items(items = visible, key = { it.id }) { event ->
              SportEventCard(
                event = event,
                onClick = { dismissKeyboard(); onPlay(event.toPlayback()) },
                onDwell = { onConsidering(event.toPlayback()) },
                modifier =
                  if (event.id == visible.first().id) {
                    Modifier.focusRequester(gridFocusRequester)
                  } else {
                    Modifier
                  },
              )
            }
          }
      }
    }
  }
}

/**
 * Sport chips on one line, the search box on the next.
 *
 * The catalog and the drama page share a line because they have a fixed handful of chips; here the
 * feed decides how many there are, and a busy night of ten sports left nothing of the search box
 * but a sliver and pushed its button off the screen entirely. The chips scroll for the same reason,
 * so an eleventh sport is reachable rather than clipped.
 */
@Composable
private fun SportsFilterRow(
  filters: List<String>,
  selectedIndex: Int,
  onSelectSport: (Int) -> Unit,
  query: String,
  onQueryChanged: (String) -> Unit,
  onSearch: () -> Unit,
  showSearch: Boolean = true,
  recentSearches: List<String> = emptyList(),
  onRepeatSearch: (String) -> Unit = {},
  onClearSearchHistory: () -> Unit = {},
  chipFocusRequester: FocusRequester,
  searchFieldFocusRequester: FocusRequester,
  searchButtonFocusRequester: FocusRequester,
  searchHistoryFocusRequester: FocusRequester,
  backFocusRequester: FocusRequester,
  bodyFocusRequester: FocusRequester?,
  compact: Boolean = false,
  onCloseSearch: (() -> Unit)? = null,
) {
  // Down out of the box lands on the remembered queries when there are any, so the pad passes
  // through them on its way to the fixtures rather than over them.
  val showHistory = showSearch && recentSearches.isNotEmpty()
  val belowSearch =
    if (showHistory) searchHistoryFocusRequester else bodyFocusRequester ?: FocusRequester.Default
  val fieldModifier =
    Modifier.focusRequester(searchFieldFocusRequester).focusProperties {
      up = backFocusRequester
      left = chipFocusRequester
      down = belowSearch
      right = searchButtonFocusRequester
    }.remoteFocusNavigation(
      up = backFocusRequester,
      down = if (showHistory) searchHistoryFocusRequester else bodyFocusRequester,
    )
  val buttonModifier =
    Modifier.focusRequester(searchButtonFocusRequester).focusProperties {
      up = backFocusRequester
      left = searchFieldFocusRequester
      down = belowSearch
    }.remoteFocusNavigation(
      up = backFocusRequester,
      left = searchFieldFocusRequester,
      down = if (showHistory) searchHistoryFocusRequester else bodyFocusRequester,
    )
  Column(modifier = Modifier.focusGroup(), verticalArrangement = Arrangement.spacedBy(9.dp)) {
    if (filters.isNotEmpty()) {
      ChipRow(
        labels = filters,
        selectedIndex = selectedIndex,
        onSelect = onSelectSport,
        firstChipFocusRequester = chipFocusRequester,
        up = backFocusRequester,
        down = bodyFocusRequester,
        semanticsRole = Role.Tab,
        compactChips = true,
        modifier = Modifier.horizontalScroll(rememberScrollState()),
      )
    }
    if (showSearch) {
      Row(horizontalArrangement = Arrangement.spacedBy(9.dp), verticalAlignment = Alignment.CenterVertically) {
        CatalogSearchField(query, "Search teams or leagues…", onQueryChanged, onSearch, fieldModifier.weight(1f))
        CatalogButton("Search", onSearch, buttonModifier)
        onCloseSearch?.let { close ->
          CatalogIconButton("Clear search", Icons.Filled.Close, close)
        }
      }
      SearchHistoryRow(
        queries = recentSearches,
        onSelect = onRepeatSearch,
        onClear = onClearSearchHistory,
        compact = compact,
        firstChipFocusRequester = searchHistoryFocusRequester,
        up = searchFieldFocusRequester,
        down = bodyFocusRequester,
      )
    }
  }
}

/**
 * One fixture, laid out as a scoreboard rather than a poster.
 *
 * A match has two sides and a time, and a viewer picks one by reading exactly those; a poster-shaped
 * card would give most of its area to a badge and push half the schedule under the fold.
 */
/** Long enough to separate running down a list of fixtures from choosing one. */
private const val FIXTURE_DWELL_MS = 1_200L

@Composable
private fun SportEventCard(
  event: SportEvent,
  onClick: () -> Unit,
  modifier: Modifier = Modifier,
  /** Said once this fixture has sat under the focus long enough to count as being considered. */
  onDwell: (() -> Unit)? = null,
) {
  var focused by remember { mutableStateOf(false) }
  if (onDwell != null) {
    LaunchedEffect(focused) {
      if (!focused) return@LaunchedEffect
      delay(FIXTURE_DWELL_MS)
      onDwell()
    }
  }
  val scale by animateFloatAsState(if (focused) 1.035f else 1f, label = "fixture focus scale")
  val outline by
    animateColorAsState(
      when {
        focused -> GizMint
        event.live -> LiveRed.copy(alpha = .45f)
        else -> SoftWhite.copy(alpha = .08f)
      },
      label = "fixture outline",
    )
  Column(
    modifier =
      modifier.graphicsLayer { scaleX = scale; scaleY = scale }.clip(RoundedCornerShape(14.dp))
        .background(NightSurface).border(if (focused) 3.dp else 1.dp, outline, RoundedCornerShape(14.dp))
        .onFocusChanged { focused = it.isFocused }.clickable(onClick = onClick)
        .semantics {
          role = Role.Button
          contentDescription =
            "Watch ${event.match}, ${event.sport}, ${event.subtitle}, ${event.statusLabel}"
        }
        .padding(horizontal = 13.dp, vertical = 12.dp)
  ) {
    Row(verticalAlignment = Alignment.CenterVertically) {
      if (event.live) {
        LivePill(label = "LIVE")
      } else {
        Text(
          event.statusLabel,
          color = if (event.ended) MutedBlue else GizMint,
          fontWeight = FontWeight.Black,
          fontSize = 11.sp,
        )
      }
      Spacer(Modifier.weight(1f))
      Text(
        event.sport.uppercase(),
        color = MutedBlue.copy(alpha = .75f),
        fontWeight = FontWeight.Bold,
        letterSpacing = 1.sp,
        fontSize = 9.sp,
        maxLines = 1,
      )
    }
    Spacer(Modifier.height(10.dp))
    if (event.isPairing) {
      TeamRow(name = event.homeTeam, badgeUrl = event.homeBadgeUrl, score = event.homeScore, live = event.live)
      Spacer(Modifier.height(7.dp))
      TeamRow(name = event.awayTeam, badgeUrl = event.awayBadgeUrl, score = event.awayScore, live = event.live)
    } else {
      // One name and no opponent: it takes both lines rather than being given a phantom rival.
      TeamRow(
        name = event.homeTeam,
        badgeUrl = event.homeBadgeUrl,
        score = event.score,
        live = event.live,
        lines = 2,
      )
    }
    Spacer(Modifier.height(10.dp))
    val kickOff = event.kickOffLabel
    // A match in play keeps its kick-off time here, where the pill has given the corner to "LIVE".
    val trailing = kickOff.takeIf { event.live }
    if (event.subtitle.isNotBlank() || trailing != null) {
      Row(verticalAlignment = Alignment.CenterVertically) {
        Text(
          event.subtitle,
          color = MutedBlue,
          fontSize = 10.sp,
          maxLines = 1,
          overflow = TextOverflow.Ellipsis,
          modifier = Modifier.weight(1f),
        )
        if (trailing != null) {
          Spacer(Modifier.width(8.dp))
          Text(trailing, color = MutedBlue.copy(alpha = .7f), fontSize = 10.sp, maxLines = 1)
        }
      }
    }
  }
}

/**
 * One side of a fixture.
 *
 * A team with no badge in the feed still lines up with one that has: the space is held either way,
 * so the two names start at the same place and the card reads as a pair.
 */
@Composable
private fun TeamRow(name: String, badgeUrl: String?, score: String?, live: Boolean, lines: Int = 1) {
  Row(verticalAlignment = Alignment.CenterVertically) {
    // The card already describes itself in full, so the badge adds nothing for a screen reader.
    TmdbArtwork(
      url = badgeUrl,
      contentDescription = "",
      modifier = Modifier.size(24.dp).clip(RoundedCornerShape(6.dp)),
      compact = true,
    )
    Spacer(Modifier.width(10.dp))
    Text(
      name.ifBlank { "TBC" },
      color = SoftWhite,
      fontWeight = FontWeight.Bold,
      fontSize = 13.sp,
      maxLines = lines,
      minLines = lines,
      lineHeight = 17.sp,
      overflow = TextOverflow.Ellipsis,
      modifier = Modifier.weight(1f),
    )
    if (score != null) {
      Spacer(Modifier.width(8.dp))
      Text(
        score,
        color = if (live) SoftWhite else MutedBlue,
        fontWeight = FontWeight.Black,
        fontSize = 15.sp,
      )
    }
  }
}

/** The one red thing on the page: a match that can be watched this minute. */
@Composable
private fun LivePill(label: String) {
  val pulse = rememberInfiniteTransition(label = "live pulse")
  val dotAlpha by
    pulse.animateFloat(
      initialValue = .35f,
      targetValue = 1f,
      animationSpec =
        infiniteRepeatable(tween(durationMillis = 800, easing = LinearEasing), RepeatMode.Reverse),
      label = "live dot alpha",
    )
  Row(
    modifier =
      Modifier.clip(RoundedCornerShape(9.dp)).background(LiveRed.copy(alpha = .16f))
        .border(1.dp, LiveRed.copy(alpha = .55f), RoundedCornerShape(9.dp))
        .padding(horizontal = 7.dp, vertical = 3.dp),
    verticalAlignment = Alignment.CenterVertically,
  ) {
    Box(Modifier.size(6.dp).clip(CircleShape).background(LiveRed.copy(alpha = dotAlpha)))
    Spacer(Modifier.width(5.dp))
    Text(label, color = LiveRed, fontWeight = FontWeight.Black, letterSpacing = .8.sp, fontSize = 9.sp)
  }
}
