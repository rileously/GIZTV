package com.example.auroratv.ui.drama

import android.util.Log
import androidx.compose.foundation.background
import androidx.compose.foundation.focusGroup
import androidx.compose.foundation.gestures.detectTapGestures
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
import androidx.compose.runtime.Composable
import androidx.compose.runtime.LaunchedEffect
import androidx.compose.runtime.getValue
import androidx.compose.runtime.mutableIntStateOf
import androidx.compose.runtime.mutableStateOf
import androidx.compose.runtime.remember
import androidx.compose.runtime.rememberCoroutineScope
import androidx.compose.runtime.saveable.rememberSaveable
import androidx.compose.runtime.setValue
import androidx.compose.ui.Alignment
import androidx.compose.ui.Modifier
import androidx.compose.ui.focus.FocusRequester
import androidx.compose.ui.focus.focusProperties
import androidx.compose.ui.focus.focusRequester
import androidx.compose.ui.graphics.Brush
import androidx.compose.ui.graphics.Color
import androidx.compose.ui.input.pointer.pointerInput
import androidx.compose.ui.platform.LocalFocusManager
import androidx.compose.ui.platform.LocalSoftwareKeyboardController
import androidx.compose.ui.semantics.Role
import androidx.compose.ui.text.font.FontWeight
import androidx.compose.ui.unit.dp
import androidx.compose.ui.unit.sp
import androidx.tv.material3.Text
import com.example.auroratv.theme.AuroraMint
import com.example.auroratv.theme.DeepSpace
import com.example.auroratv.theme.MutedBlue
import com.example.auroratv.theme.SoftWhite
import com.example.auroratv.ui.catalog.CatalogButton
import com.example.auroratv.ui.catalog.CatalogSearchField
import com.example.auroratv.ui.catalog.ChipRow
import com.example.auroratv.ui.catalog.GizTvMark
import com.example.auroratv.ui.catalog.PosterCard
import com.example.auroratv.ui.catalog.StatusPanel
import com.example.auroratv.ui.catalog.friendlyCatalogError
import com.example.auroratv.ui.catalog.remoteFocusNavigation
import kotlinx.coroutines.launch

/**
 * The short drama listing.
 *
 * chartdrama matches on title text rather than genre, so the keyword chips are title words: the
 * page opens on the first one and every chip press is exactly one request, which the repository
 * caches and paces.
 */
@Composable
internal fun ShortDramaScreen(
  onOpenDrama: (ShortDrama) -> Unit,
  onBack: () -> Unit,
  modifier: Modifier = Modifier,
) {
  val scope = rememberCoroutineScope()
  val focusManager = LocalFocusManager.current
  val keyboardController = LocalSoftwareKeyboardController.current
  val backFocusRequester = remember { FocusRequester() }
  val keywordFocusRequester = remember { FocusRequester() }
  val searchFieldFocusRequester = remember { FocusRequester() }
  val searchButtonFocusRequester = remember { FocusRequester() }
  val gridFocusRequester = remember { FocusRequester() }
  val gridState = rememberLazyGridState()

  var keywordIndex by rememberSaveable { mutableIntStateOf(0) }
  var query by rememberSaveable { mutableStateOf("") }
  var searchActive by rememberSaveable { mutableStateOf(false) }
  var dramas by remember { mutableStateOf<List<ShortDrama>>(emptyList()) }
  var loading by remember { mutableStateOf(true) }
  var errorMessage by remember { mutableStateOf<String?>(null) }

  fun dismissKeyboard() {
    focusManager.clearFocus()
    keyboardController?.hide()
  }

  fun load(searchQuery: String) {
    scope.launch {
      loading = true
      errorMessage = null
      runCatching { ShortDramaRepository.search(searchQuery) }
        .onSuccess { dramas = it }
        .onFailure {
          Log.e("GizTvShortDrama", "Short drama search failed for \"$searchQuery\"", it)
          errorMessage = friendlyCatalogError(it)
        }
      loading = false
    }
  }

  fun selectKeyword(index: Int) {
    if (index == keywordIndex && !searchActive) return
    keywordIndex = index
    query = ""
    searchActive = false
    load(DEFAULT_DRAMA_KEYWORDS[index])
  }

  fun runSearch() {
    val trimmed = query.trim()
    if (trimmed.isBlank()) return
    dismissKeyboard()
    searchButtonFocusRequester.requestFocus()
    searchActive = true
    load(trimmed)
  }

  LaunchedEffect(Unit) {
    load(DEFAULT_DRAMA_KEYWORDS[keywordIndex])
    backFocusRequester.requestFocus()
  }

  // A new keyword starts at the top rather than wherever the previous list was scrolled to.
  LaunchedEffect(keywordIndex, searchActive, loading) {
    if (!loading && gridState.layoutInfo.totalItemsCount > 0) gridState.scrollToItem(0)
  }

  // Back is handled by AuroraTvRoot: a handler registered here would also receive the press that
  // just left the drama detail screen, skipping this page entirely.
  val heading =
    if (searchActive) "Results for “${query.trim()}”" else "${DEFAULT_DRAMA_KEYWORDS[keywordIndex]} short dramas"

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
    val bodyFocusRequester = gridFocusRequester.takeIf { dramas.isNotEmpty() }

    Column(
      modifier =
        Modifier.fillMaxSize().padding(
          horizontal = if (narrow) 18.dp else 42.dp,
          vertical = if (compact) 14.dp else 22.dp,
        )
    ) {
      Row(modifier = Modifier.fillMaxWidth(), verticalAlignment = Alignment.CenterVertically) {
        GizTvMark(modifier = Modifier.size(34.dp), cornerRadius = 10.dp)
        Spacer(Modifier.width(11.dp))
        Text(
          "SHORT DRAMAS",
          color = SoftWhite,
          fontWeight = FontWeight.Black,
          letterSpacing = 2.5.sp,
          fontSize = 18.sp,
        )
        Spacer(Modifier.weight(1f))
        CatalogButton(
          label = "Back",
          onClick = { dismissKeyboard(); onBack() },
          modifier =
            Modifier.focusRequester(backFocusRequester).focusProperties {
              down = keywordFocusRequester
            },
        )
      }
      Spacer(Modifier.height(if (compact) 8.dp else 12.dp))
      DramaFilterRow(
        narrow = narrow,
        selectedKeyword = keywordIndex.takeIf { !searchActive } ?: -1,
        onSelectKeyword = ::selectKeyword,
        query = query,
        onQueryChanged = { query = it },
        onSearch = ::runSearch,
        keywordFocusRequester = keywordFocusRequester,
        searchFieldFocusRequester = searchFieldFocusRequester,
        searchButtonFocusRequester = searchButtonFocusRequester,
        backFocusRequester = backFocusRequester,
        bodyFocusRequester = bodyFocusRequester,
      )
      Spacer(Modifier.height(if (compact) 8.dp else 14.dp))

      when {
        loading -> StatusPanel("Loading short dramas…", Modifier.weight(1f), loading = true)
        errorMessage != null ->
          StatusPanel(
            message = errorMessage ?: "Short dramas could not be loaded",
            modifier = Modifier.weight(1f),
            actionLabel = "Try again",
            onAction = {
              load(if (searchActive) query.trim() else DEFAULT_DRAMA_KEYWORDS[keywordIndex])
            },
          )
        dramas.isEmpty() -> StatusPanel("Nothing found. Try another title.", Modifier.weight(1f))
        else ->
          LazyVerticalGrid(
            state = gridState,
            columns = GridCells.Adaptive(minSize = if (narrow) 132.dp else 158.dp),
            modifier = Modifier.weight(1f).fillMaxWidth(),
            contentPadding = PaddingValues(bottom = 22.dp),
            horizontalArrangement = Arrangement.spacedBy(if (narrow) 12.dp else 18.dp),
            verticalArrangement = Arrangement.spacedBy(if (narrow) 16.dp else 22.dp),
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
                    "${dramas.size} titles",
                    color = AuroraMint,
                    fontWeight = FontWeight.Bold,
                    fontSize = 11.sp,
                  )
                  Spacer(Modifier.weight(1f))
                  if (!narrow) {
                    Text("chartdrama listing", color = MutedBlue.copy(alpha = .6f), fontSize = 9.sp)
                  }
                }
                Spacer(Modifier.height(8.dp))
              }
            }
            items(items = dramas, key = { it.slug }) { drama ->
              PosterCard(
                title = drama.title,
                subtitle = drama.subtitle,
                rating = 0.0,
                posterUrl = drama.coverUrl,
                actionLabel = "Open ${drama.title}",
                onClick = { dismissKeyboard(); onOpenDrama(drama) },
                modifier =
                  if (drama.slug == dramas.first().slug) {
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

/** Keyword chips and the search box share one line, matching the movie catalog. */
@Composable
private fun DramaFilterRow(
  narrow: Boolean,
  selectedKeyword: Int,
  onSelectKeyword: (Int) -> Unit,
  query: String,
  onQueryChanged: (String) -> Unit,
  onSearch: () -> Unit,
  keywordFocusRequester: FocusRequester,
  searchFieldFocusRequester: FocusRequester,
  searchButtonFocusRequester: FocusRequester,
  backFocusRequester: FocusRequester,
  bodyFocusRequester: FocusRequester?,
) {
  val keywords: @Composable () -> Unit = {
    ChipRow(
      labels = DEFAULT_DRAMA_KEYWORDS,
      selectedIndex = selectedKeyword,
      onSelect = onSelectKeyword,
      firstChipFocusRequester = keywordFocusRequester,
      up = backFocusRequester,
      down = bodyFocusRequester,
      semanticsRole = Role.Tab,
      compactChips = true,
    )
  }
  val fieldModifier =
    Modifier.focusRequester(searchFieldFocusRequester).focusProperties {
      up = backFocusRequester
      left = keywordFocusRequester
      down = bodyFocusRequester ?: FocusRequester.Default
      right = searchButtonFocusRequester
    }.remoteFocusNavigation(up = backFocusRequester, down = bodyFocusRequester)
  val buttonModifier =
    Modifier.focusRequester(searchButtonFocusRequester).focusProperties {
      up = backFocusRequester
      left = searchFieldFocusRequester
      down = bodyFocusRequester ?: FocusRequester.Default
    }.remoteFocusNavigation(
      up = backFocusRequester,
      left = searchFieldFocusRequester,
      down = bodyFocusRequester,
    )
  if (narrow) {
    Column(modifier = Modifier.focusGroup(), verticalArrangement = Arrangement.spacedBy(9.dp)) {
      keywords()
      Row(horizontalArrangement = Arrangement.spacedBy(9.dp), verticalAlignment = Alignment.CenterVertically) {
        CatalogSearchField(query, "Search short dramas…", onQueryChanged, onSearch, fieldModifier.weight(1f))
        CatalogButton("Search", onSearch, buttonModifier)
      }
    }
  } else {
    Row(
      modifier = Modifier.focusGroup().fillMaxWidth(),
      horizontalArrangement = Arrangement.spacedBy(10.dp),
      verticalAlignment = Alignment.CenterVertically,
    ) {
      keywords()
      Spacer(Modifier.width(6.dp))
      CatalogSearchField(query, "Search short dramas…", onQueryChanged, onSearch, fieldModifier.weight(1f))
      CatalogButton("Search", onSearch, buttonModifier)
    }
  }
}
