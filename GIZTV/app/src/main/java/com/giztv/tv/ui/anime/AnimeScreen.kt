package com.giztv.tv.ui.anime

import android.util.Log
import androidx.activity.compose.BackHandler
import androidx.compose.foundation.background
import androidx.compose.foundation.focusGroup
import androidx.compose.foundation.gestures.detectTapGestures
import androidx.compose.foundation.horizontalScroll
import androidx.compose.foundation.layout.Arrangement
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
import androidx.compose.foundation.rememberScrollState
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
import androidx.compose.ui.focus.FocusRequester
import androidx.compose.ui.focus.focusProperties
import androidx.compose.ui.focus.focusRequester
import androidx.compose.ui.graphics.Brush
import androidx.compose.ui.graphics.Color
import androidx.compose.ui.input.pointer.pointerInput
import androidx.compose.ui.platform.LocalContext
import androidx.compose.ui.platform.LocalFocusManager
import androidx.compose.ui.platform.LocalSoftwareKeyboardController
import androidx.compose.ui.semantics.Role
import androidx.compose.ui.text.font.FontWeight
import androidx.compose.ui.unit.dp
import androidx.compose.ui.unit.sp
import androidx.tv.material3.Text
import com.giztv.tv.data.WatchHistoryStore
import com.giztv.tv.theme.GizMint
import com.giztv.tv.theme.DeepSpace
import com.giztv.tv.theme.MutedBlue
import com.giztv.tv.theme.SoftWhite
import com.giztv.tv.ui.catalog.CatalogButton
import com.giztv.tv.ui.catalog.CatalogIconButton
import com.giztv.tv.ui.catalog.CatalogSearchField
import com.giztv.tv.ui.catalog.ChipRow
import com.giztv.tv.ui.catalog.GizTvMark
import com.giztv.tv.ui.catalog.PosterCard
import com.giztv.tv.ui.catalog.StatusPanel
import com.giztv.tv.ui.catalog.remoteFocusNavigation
import kotlinx.coroutines.launch

/** Where the viewer had got to in the catalogue, kept across a trip into a title and back. */
internal data class AnimeBrowseState(
  val sort: AnimeSort = AnimeSort.TRENDING,
  val kind: AnimeKind = AnimeKind.ALL,
  val query: String = "",
)

/**
 * The anime catalogue.
 *
 * A search and the ordering are the same request to the site with different parameters, so the grid
 * is one listing throughout rather than a separate results mode; searching only silences the
 * ordering row, which the site ignores while a query is set.
 */
@Composable
internal fun AnimeScreen(
  onOpenAnime: (Anime) -> Unit,
  onBack: () -> Unit,
  browseState: AnimeBrowseState,
  onBrowseStateChanged: (AnimeBrowseState) -> Unit,
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
  val historyStore = remember(context) { WatchHistoryStore(context) }
  val backFocusRequester = remember { FocusRequester() }
  val sortFocusRequester = remember { FocusRequester() }
  val kindFocusRequester = remember { FocusRequester() }
  val searchFieldFocusRequester = remember { FocusRequester() }
  val searchButtonFocusRequester = remember { FocusRequester() }
  val gridFocusRequester = remember { FocusRequester() }
  val gridState = rememberLazyGridState()

  var anime by remember { mutableStateOf<List<Anime>>(emptyList()) }
  var loading by remember { mutableStateOf(true) }
  var errorMessage by remember { mutableStateOf<String?>(null) }
  var searchExpanded by rememberSaveable { mutableStateOf(false) }
  val sort = browseState.sort
  val kind = browseState.kind
  val query = browseState.query
  val searching = query.isNotBlank()

  fun dismissKeyboard() {
    focusManager.clearFocus()
    keyboardController?.hide()
  }

  fun collapseSearchUi() {
    searchExpanded = false
    if (query.isNotBlank()) onBrowseStateChanged(browseState.copy(query = ""))
    dismissKeyboard()
  }

  // Keyed on the whole browse state: every one of its fields is a different request to the site.
  LaunchedEffect(sort, kind, query) {
    loading = true
    errorMessage = null
    runCatching { AnimeRepository.browse(sort = sort, kind = kind, query = query) }
      .onSuccess { anime = it }
      .onFailure {
        Log.e("GizTvAnime", "Anime listing failed", it)
        errorMessage = it.message ?: "The anime catalogue could not be loaded."
        anime = emptyList()
      }
    loading = false
  }

  // Keyed on the loaded listing rather than folded into the load above: at the end of that effect
  // the grid is still the one the previous filter left behind — often scrolled, and for a shorter
  // list — so a scroll issued there lands on the old content and the new listing opens part way
  // down. Waiting for the items to be the ones on screen is what makes the top the top.
  LaunchedEffect(anime) {
    if (anime.isNotEmpty()) gridState.scrollToItem(0)
  }

  fun runSearch() {
    if (query.isBlank()) {
      searchExpanded = true
      searchFieldFocusRequester.requestFocus()
    } else {
      dismissKeyboard()
      searchButtonFocusRequester.requestFocus()
    }
  }

  LaunchedEffect(requestSearchFocus) {
    if (!requestSearchFocus) return@LaunchedEffect
    searchExpanded = true
    withFrameNanos {}
    runCatching { searchFieldFocusRequester.requestFocus() }
    onSearchFocusHandled()
  }

  BackHandler(enabled = hideBackButton && searchExpanded) { collapseSearchUi() }

  LaunchedEffect(Unit) { if (!hideBackButton) backFocusRequester.requestFocus() }

  BoxWithConstraints(
    modifier =
      modifier.fillMaxSize().pointerInput(Unit) { detectTapGestures { dismissKeyboard() } }
        .background(
          Brush.radialGradient(
            colors = listOf(Color(0xFF2A1E52), DeepSpace),
            radius = 1_300f,
            center = androidx.compose.ui.geometry.Offset(1_300f, 180f),
          )
        )
  ) {
    val narrow = maxWidth < 600.dp
    val compact = maxHeight < 600.dp
    val phoneDense = hideBackButton && narrow
    val showSearchRow = !phoneDense || searchExpanded || query.isNotBlank()
    val bodyFocusRequester = gridFocusRequester.takeIf { anime.isNotEmpty() }
    Column(
      modifier =
        Modifier.fillMaxSize().padding(
          horizontal = if (narrow) 14.dp else 42.dp,
          vertical = if (phoneDense) 8.dp else if (compact) 14.dp else 22.dp,
        )
    ) {
      Row(modifier = Modifier.fillMaxWidth(), verticalAlignment = Alignment.CenterVertically) {
        GizTvMark(
          modifier = Modifier.size(if (phoneDense) 26.dp else 34.dp),
          cornerRadius = if (phoneDense) 8.dp else 10.dp,
        )
        Spacer(Modifier.width(if (phoneDense) 8.dp else 11.dp))
        Column {
          Text(
            "ANIME",
            color = SoftWhite,
            fontWeight = FontWeight.Black,
            letterSpacing = if (phoneDense) 2.sp else 2.5.sp,
            fontSize = if (phoneDense) 15.sp else 18.sp,
          )
          if (!narrow) {
            Text(
              if (searching) "Search results" else sort.label,
              color = GizMint.copy(alpha = .8f),
              fontWeight = FontWeight.Bold,
              fontSize = 9.sp,
            )
          }
        }
        Spacer(Modifier.weight(1f))
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
          if (phoneDense && showSearchRow) {
            CatalogIconButton("Close search", Icons.Filled.Close, ::collapseSearchUi)
          }
          if (!hideBackButton) {
            CatalogButton(
              label = "Back",
              onClick = { dismissKeyboard(); onBack() },
              modifier =
                Modifier.focusRequester(backFocusRequester).focusProperties {
                  down = sortFocusRequester
                },
            )
          }
        }
      }
      Spacer(Modifier.height(if (phoneDense) 6.dp else if (compact) 8.dp else 12.dp))

      // The site ignores its own sort while a query is set, so the row would be a lie during one.
      if (!searching) {
        ChipRow(
          labels = AnimeSort.entries.map(AnimeSort::label),
          selectedIndex = AnimeSort.entries.indexOf(sort),
          onSelect = { index ->
            AnimeSort.entries.getOrNull(index)?.let { onBrowseStateChanged(browseState.copy(sort = it)) }
          },
          firstChipFocusRequester = sortFocusRequester,
          semanticsRole = Role.Tab,
          up = backFocusRequester,
          down = kindFocusRequester,
          compactChips = true,
          modifier = Modifier.horizontalScroll(rememberScrollState()),
        )
        Spacer(Modifier.height(if (compact) 6.dp else 9.dp))
      }

      ChipRow(
        labels = AnimeKind.entries.map(AnimeKind::label),
        selectedIndex = AnimeKind.entries.indexOf(kind),
        onSelect = { index ->
          AnimeKind.entries.getOrNull(index)?.let { onBrowseStateChanged(browseState.copy(kind = it)) }
        },
        firstChipFocusRequester = kindFocusRequester,
        semanticsRole = Role.Tab,
        up = if (searching) backFocusRequester else sortFocusRequester,
        down = searchFieldFocusRequester,
        compactChips = true,
        modifier = Modifier.horizontalScroll(rememberScrollState()),
      )
      Spacer(Modifier.height(if (compact) 7.dp else 10.dp))

      if (showSearchRow) {
        Row(
          modifier = Modifier.focusGroup(),
          horizontalArrangement = Arrangement.spacedBy(9.dp),
          verticalAlignment = Alignment.CenterVertically,
        ) {
          CatalogSearchField(
            value = query,
            placeholder = "Search anime…",
            onValueChanged = { onBrowseStateChanged(browseState.copy(query = it)) },
            onSearch = ::runSearch,
            modifier =
              Modifier.weight(1f).focusRequester(searchFieldFocusRequester).focusProperties {
                up = kindFocusRequester
                right = searchButtonFocusRequester
                down = bodyFocusRequester ?: FocusRequester.Default
              }.remoteFocusNavigation(up = kindFocusRequester, down = bodyFocusRequester),
          )
          CatalogButton(
            label = "Search",
            onClick = ::runSearch,
            modifier =
              Modifier.focusRequester(searchButtonFocusRequester).focusProperties {
                up = kindFocusRequester
                left = searchFieldFocusRequester
                down = bodyFocusRequester ?: FocusRequester.Default
              }.remoteFocusNavigation(
                up = kindFocusRequester,
                left = searchFieldFocusRequester,
                down = bodyFocusRequester,
              ),
          )
        }
        Spacer(Modifier.height(if (compact) 8.dp else 14.dp))
      }

      when {
        loading -> StatusPanel("Loading anime…", Modifier.weight(1f), loading = true)
        errorMessage != null ->
          StatusPanel(
            message = errorMessage ?: "The anime catalogue could not be loaded.",
            modifier = Modifier.weight(1f),
            actionLabel = "Try again",
            onAction = { onBrowseStateChanged(browseState.copy()) },
          )
        anime.isEmpty() ->
          StatusPanel(
            message =
              if (searching) "No anime match “${query.trim()}”."
              else "Nothing listed under these filters.",
            modifier = Modifier.weight(1f),
            actionLabel = if (searching) "Clear search" else "Reset filters",
            onAction = { onBrowseStateChanged(AnimeBrowseState()) },
          )
        else ->
          LazyVerticalGrid(
            state = gridState,
            columns = GridCells.Adaptive(minSize = if (narrow) 116.dp else 158.dp),
            modifier = Modifier.weight(1f).fillMaxWidth(),
            contentPadding = PaddingValues(bottom = if (phoneDense) 8.dp else 22.dp),
            horizontalArrangement = Arrangement.spacedBy(if (narrow) 12.dp else 16.dp),
            verticalArrangement = Arrangement.spacedBy(if (narrow) 12.dp else 16.dp),
          ) {
            item(span = { GridItemSpan(maxLineSpan) }) {
              Column {
                Row(verticalAlignment = Alignment.CenterVertically) {
                  Text(
                    if (searching) "Results for “${query.trim()}”" else sort.label,
                    color = SoftWhite,
                    fontWeight = FontWeight.Black,
                    fontSize = if (narrow) 17.sp else 19.sp,
                  )
                  Spacer(Modifier.width(10.dp))
                  Text(
                    if (anime.size == 1) "1 title" else "${anime.size} titles",
                    color = GizMint,
                    fontWeight = FontWeight.Bold,
                    fontSize = 11.sp,
                  )
                  Spacer(Modifier.weight(1f))
                  if (!narrow) {
                    Text("anidb listing", color = MutedBlue.copy(alpha = .6f), fontSize = 9.sp)
                  }
                }
                Spacer(Modifier.height(8.dp))
              }
            }
            items(items = anime, key = Anime::slug) { title ->
              PosterCard(
                title = title.title,
                subtitle = title.subtitle,
                rating = title.score?.toDoubleOrNull() ?: 0.0,
                posterUrl = title.posterUrl,
                actionLabel = "Open ${title.title}",
                onClick = { dismissKeyboard(); onOpenAnime(title) },
                watched = historyStore.find(animeEpisodeIdentity(title.slug, 1))?.completed == true,
                modifier =
                  if (title.slug == anime.first().slug) Modifier.focusRequester(gridFocusRequester)
                  else Modifier,
              )
            }
          }
      }
    }
  }
}
