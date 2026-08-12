package com.giztv.tv.ui.iptv

import android.util.Log
import androidx.activity.compose.BackHandler
import androidx.compose.animation.animateColorAsState
import androidx.compose.animation.core.animateFloatAsState
import androidx.compose.foundation.background
import androidx.compose.foundation.border
import androidx.compose.foundation.clickable
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
import androidx.compose.foundation.lazy.LazyRow
import androidx.compose.foundation.lazy.itemsIndexed
import androidx.compose.foundation.rememberScrollState
import androidx.compose.foundation.shape.RoundedCornerShape
import androidx.compose.material.icons.Icons
import androidx.compose.material.icons.filled.Close
import androidx.compose.material.icons.filled.Search
import androidx.compose.material.icons.filled.Star
import androidx.compose.material.icons.filled.StarBorder
import androidx.compose.material3.Icon
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
import androidx.compose.ui.semantics.selected
import androidx.compose.ui.semantics.semantics
import androidx.compose.ui.platform.testTag
import androidx.compose.ui.text.font.FontWeight
import androidx.compose.ui.text.style.TextOverflow
import androidx.compose.ui.unit.dp
import androidx.compose.ui.unit.sp
import androidx.tv.material3.Text
import com.giztv.tv.data.IptvChannelStore
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

private const val SEARCH_DEBOUNCE_MS = 180L

internal data class IptvBrowseState(
  val selectedCategory: String = ALL_IPTV_CHANNELS,
  val selectedGroup: String = ALL_IPTV_GROUPS,
  val query: String = "",
)

@Composable
internal fun IptvScreen(
  onPlay: (IptvChannel) -> Unit,
  onBack: () -> Unit,
  browseState: IptvBrowseState,
  onBrowseStateChanged: (IptvBrowseState) -> Unit,
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
  val reloadFocusRequester = remember { FocusRequester() }
  val categoryFocusRequester = remember { FocusRequester() }
  val groupFocusRequester = remember { FocusRequester() }
  val searchFieldFocusRequester = remember { FocusRequester() }
  val searchButtonFocusRequester = remember { FocusRequester() }
  val searchHistoryFocusRequester = remember { FocusRequester() }
  val gridFocusRequester = remember { FocusRequester() }
  val gridState = rememberLazyGridState()

  var playlist by remember { mutableStateOf<IptvPlaylist?>(null) }
  val channelStore = remember(context) { IptvChannelStore(context) }
  var favoriteKeys by remember { mutableStateOf(emptyList<String>()) }
  var recentKeys by remember { mutableStateOf(emptyList<String>()) }
  val selectedCategory = browseState.selectedCategory
  val selectedGroup = browseState.selectedGroup
  val query = browseState.query
  var loading by remember { mutableStateOf(true) }
  var searchExpanded by rememberSaveable { mutableStateOf(false) }
  val searchHistoryStore = remember(context) { SearchHistoryStore(context) }
  var recentSearches by remember { mutableStateOf(searchHistoryStore.recent(SearchSection.IPTV)) }
  var errorMessage by remember { mutableStateOf<String?>(null) }

  fun dismissKeyboard() {
    focusManager.clearFocus()
    keyboardController?.hide()
  }

  fun collapseSearchUi() {
    searchExpanded = false
    if (query.isNotBlank()) {
      onBrowseStateChanged(browseState.copy(query = ""))
    }
    dismissKeyboard()
  }

  fun load(refresh: Boolean) {
    scope.launch {
      loading = true
      errorMessage = null
      runCatching { IptvRepository.playlist(context, refresh) }
        .onSuccess { playlist = it }
        .onFailure {
          Log.e("GizTvIptv", "IPTV playlist load failed", it)
          errorMessage = it.message ?: "The IPTV playlist could not be loaded."
        }
      loading = false
    }
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

  /** Runs a remembered query again, exactly as if it had just been typed. */
  fun repeatSearch(searched: String) {
    searchExpanded = true
    dismissKeyboard()
    onBrowseStateChanged(browseState.copy(query = searched))
  }

  fun clearSearchHistory() {
    searchHistoryStore.clear(SearchSection.IPTV)
    recentSearches = emptyList()
  }

  LaunchedEffect(requestSearchFocus) {
    if (!requestSearchFocus) return@LaunchedEffect
    searchExpanded = true
    withFrameNanos {}
    runCatching { searchFieldFocusRequester.requestFocus() }
    onSearchFocusHandled()
  }

  BackHandler(enabled = hideBackButton && searchExpanded) { collapseSearchUi() }

  LaunchedEffect(Unit) {
    favoriteKeys = channelStore.favorites()
    recentKeys = channelStore.recents()
    load(refresh = false)
    if (!hideBackButton) backFocusRequester.requestFocus()
  }

  // Typing runs ahead of a five-figure playlist. Holding the last keystroke briefly keeps the field
  // responsive and spends one filtering pass on the word rather than one per letter.
  var appliedQuery by remember { mutableStateOf(query) }
  LaunchedEffect(query) {
    if (query.isBlank()) {
      appliedQuery = ""
      return@LaunchedEffect
    }
    delay(SEARCH_DEBOUNCE_MS)
    appliedQuery = query
    // Recorded once the guide has actually been filtered rather than on each keystroke. Everything
    // this query was typed through is a prefix of it, and the store drops those.
    searchHistoryStore.record(SearchSection.IPTV, query)
    recentSearches = searchHistoryStore.recent(SearchSection.IPTV)
  }

  val index = playlist?.browseIndex
  val channels = playlist?.channels.orEmpty()
  val favorites = remember(index, favoriteKeys) { index?.find(favoriteKeys).orEmpty() }
  val recents = remember(index, recentKeys) { index?.find(recentKeys).orEmpty() }
  val categories =
    remember(index, favorites, recents) {
      buildList {
        if (favorites.isNotEmpty()) add(IptvCategory(IPTV_CATEGORY_FAVORITES, favorites.size))
        if (recents.isNotEmpty()) add(IptvCategory(IPTV_CATEGORY_RECENT, recents.size))
        addAll(index?.categories.orEmpty())
      }
    }
  val activeCategory = selectedCategory.takeIf { selected -> categories.any { it.label == selected } }
    ?: ALL_IPTV_CHANNELS
  val pinnedCategory = activeCategory == IPTV_CATEGORY_FAVORITES || activeCategory == IPTV_CATEGORY_RECENT
  val groups =
    remember(index, activeCategory) {
      if (pinnedCategory) listOf(ALL_IPTV_GROUPS) else index?.groups(activeCategory).orEmpty()
    }
  val activeGroup = selectedGroup.takeIf(groups::contains) ?: ALL_IPTV_GROUPS
  val searching = appliedQuery.isNotBlank()
  val visible =
    remember(index, activeCategory, activeGroup, appliedQuery, favorites, recents) {
      when {
        // Search deliberately ignores the pinned and category filters: the point of reaching for it
        // in a guide this size is to stop narrowing by hand.
        appliedQuery.isNotBlank() -> index?.visible(null, null, appliedQuery).orEmpty()
        activeCategory == IPTV_CATEGORY_FAVORITES -> favorites
        activeCategory == IPTV_CATEGORY_RECENT -> recents
        else -> index?.visible(activeCategory, activeGroup, "").orEmpty()
      }
    }

  LaunchedEffect(activeCategory, activeGroup, appliedQuery, loading) {
    if (!loading && gridState.layoutInfo.totalItemsCount > 0) gridState.scrollToItem(0)
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
    val showSearchRow = !phoneDense || searchExpanded || query.isNotBlank()
    val showGroupFilters = activeCategory != ALL_IPTV_CHANNELS && groups.size > 2
    val categoryDownRequester = if (showGroupFilters) groupFocusRequester else searchFieldFocusRequester
    val searchUpRequester = if (showGroupFilters) groupFocusRequester else categoryFocusRequester
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
        Column {
          Text(
            "LIVE TV",
            color = SoftWhite,
            fontWeight = FontWeight.Black,
            letterSpacing = if (phoneDense) 2.sp else 2.5.sp,
            fontSize = if (phoneDense) 15.sp else 18.sp,
          )
          if (!narrow && channels.isNotEmpty()) {
            Text(
              // The playlist's own categories, and its count of distinct channels rather than of
              // listings, so the figure here matches what All channels actually shows.
              "${index?.categories?.first()?.channelCount ?: channels.size}" +
                " channels · ${(index?.categories?.size ?: 1) - 1} clear categories",
              color = GizMint.copy(alpha = .8f),
              fontWeight = FontWeight.Bold,
              fontSize = 9.sp,
            )
          }
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
            label = "Reload",
            onClick = { dismissKeyboard(); load(refresh = true) },
            modifier =
              Modifier.focusRequester(reloadFocusRequester).focusProperties {
                right = backFocusRequester
                down = categoryFocusRequester
              },
          )
          if (!hideBackButton) {
            CatalogButton(
              label = "Back",
              onClick = { dismissKeyboard(); onBack() },
              modifier =
                Modifier.focusRequester(backFocusRequester).focusProperties {
                  left = reloadFocusRequester
                  down = categoryFocusRequester
                },
            )
          }
        }
      }
      Spacer(modifier.height(if (phoneDense) 6.dp else if (compact) 8.dp else 12.dp))

      IptvCategoryStrip(
        categories = categories,
        selectedCategory = activeCategory,
        onSelect = { category ->
          onBrowseStateChanged(
            browseState.copy(
              selectedCategory = category.label,
              selectedGroup = ALL_IPTV_GROUPS,
              query = "",
            )
          )
        },
        firstCategoryFocusRequester = categoryFocusRequester,
        up = backFocusRequester,
        down = categoryDownRequester,
        compact = compact,
      )
      Spacer(Modifier.height(if (compact) 7.dp else 10.dp))

      if (showGroupFilters) {
        ChipRow(
          labels = groups,
          selectedIndex = groups.indexOf(activeGroup),
          onSelect = { index ->
            onBrowseStateChanged(
              browseState.copy(
                selectedGroup = groups.getOrNull(index) ?: ALL_IPTV_GROUPS,
                query = "",
              )
            )
          },
          firstChipFocusRequester = groupFocusRequester,
          semanticsRole = Role.Tab,
          up = categoryFocusRequester,
          down = searchFieldFocusRequester,
          compactChips = true,
          modifier = Modifier.horizontalScroll(rememberScrollState()),
        )
        Spacer(Modifier.height(9.dp))
      }

      if (showSearchRow) {
        // Down out of the box lands on the remembered queries when there are any, so the pad
        // passes through them on its way to the guide rather than over them.
        val showHistory = recentSearches.isNotEmpty()
        val belowSearch =
          if (showHistory) searchHistoryFocusRequester else bodyFocusRequester ?: FocusRequester.Default
        val remoteBelowSearch = if (showHistory) searchHistoryFocusRequester else bodyFocusRequester
        Row(
          modifier = Modifier.focusGroup(),
          horizontalArrangement = Arrangement.spacedBy(9.dp),
          verticalAlignment = Alignment.CenterVertically,
        ) {
          CatalogSearchField(
            value = query,
            placeholder = "Search all channels…",
            onValueChanged = { onBrowseStateChanged(browseState.copy(query = it)) },
            onSearch = ::runSearch,
            modifier =
              Modifier.weight(1f).focusRequester(searchFieldFocusRequester).focusProperties {
                up = searchUpRequester
                right = searchButtonFocusRequester
                down = belowSearch
              }.remoteFocusNavigation(up = searchUpRequester, down = remoteBelowSearch),
          )
          CatalogButton(
            label = "Search",
            onClick = ::runSearch,
            modifier =
              Modifier.focusRequester(searchButtonFocusRequester).focusProperties {
                up = searchUpRequester
                left = searchFieldFocusRequester
                down = belowSearch
              }.remoteFocusNavigation(
                up = searchUpRequester,
                left = searchFieldFocusRequester,
                down = remoteBelowSearch,
              ),
          )
          if (phoneDense) {
            CatalogIconButton("Clear search", Icons.Filled.Close, ::collapseSearchUi)
          }
        }
        if (showHistory) {
          Spacer(Modifier.height(if (compact) 6.dp else 9.dp))
          SearchHistoryRow(
            queries = recentSearches,
            onSelect = ::repeatSearch,
            onClear = ::clearSearchHistory,
            compact = phoneDense,
            firstChipFocusRequester = searchHistoryFocusRequester,
            up = searchFieldFocusRequester,
            down = bodyFocusRequester,
          )
        }
        Spacer(Modifier.height(if (compact) 8.dp else 14.dp))
      }

      when {
        loading && channels.isEmpty() ->
          StatusPanel("Loading IPTV channels…", Modifier.weight(1f), loading = true)
        errorMessage != null && channels.isEmpty() ->
          StatusPanel(
            message = errorMessage ?: "The IPTV playlist could not be loaded.",
            modifier = Modifier.weight(1f),
            actionLabel = "Try again",
            onAction = { load(refresh = true) },
          )
        visible.isEmpty() ->
          StatusPanel(
            message =
              when {
                searching -> "No channels match “${appliedQuery.trim()}”."
                activeCategory == IPTV_CATEGORY_FAVORITES ->
                  "No favourites yet. Star a channel to keep it here."
                activeCategory == IPTV_CATEGORY_RECENT -> "Nothing watched yet."
                else -> "There are no channels in this category."
              },
            modifier = Modifier.weight(1f),
            actionLabel = if (searching) "Clear search" else "All channels",
            onAction = { onBrowseStateChanged(IptvBrowseState()) },
          )
        else ->
          LazyVerticalGrid(
            state = gridState,
            columns = GridCells.Adaptive(minSize = if (narrow) 220.dp else 270.dp),
            modifier = Modifier.weight(1f).fillMaxWidth(),
            contentPadding = PaddingValues(bottom = if (phoneDense) 8.dp else 22.dp),
            horizontalArrangement = Arrangement.spacedBy(if (narrow) 12.dp else 16.dp),
            verticalArrangement = Arrangement.spacedBy(if (phoneDense) 10.dp else if (narrow) 12.dp else 16.dp),
          ) {
            item(span = { GridItemSpan(maxLineSpan) }) {
              Column {
                Row(verticalAlignment = Alignment.CenterVertically) {
                  Text(
                    when {
                      searching -> "Results for “${appliedQuery.trim()}”"
                      activeGroup != ALL_IPTV_GROUPS -> activeGroup
                      else -> activeCategory
                    },
                    color = SoftWhite,
                    fontWeight = FontWeight.Black,
                    fontSize = if (narrow) 17.sp else 19.sp,
                  )
                  Spacer(Modifier.width(10.dp))
                  Text(
                    if (visible.size == 1) "1 channel" else "${visible.size} channels",
                    color = GizMint,
                    fontWeight = FontWeight.Bold,
                    fontSize = 11.sp,
                  )
                }
                Spacer(Modifier.height(8.dp))
              }
            }
            items(items = visible, key = IptvChannel::id) { channel ->
              IptvChannelCard(
                channel = channel,
                favorite = channel.key in favoriteKeys,
                onClick = {
                  dismissKeyboard()
                  channelStore.recordWatch(channel.key)
                  recentKeys = channelStore.recents()
                  onPlay(channel)
                },
                onToggleFavorite = {
                  channelStore.toggleFavorite(channel.key)
                  favoriteKeys = channelStore.favorites()
                },
                modifier =
                  if (channel.id == visible.first().id) Modifier.focusRequester(gridFocusRequester)
                  else Modifier,
              )
            }
          }
      }
    }
  }
}

@Composable
private fun IptvCategoryStrip(
  categories: List<IptvCategory>,
  selectedCategory: String,
  onSelect: (IptvCategory) -> Unit,
  firstCategoryFocusRequester: FocusRequester,
  up: FocusRequester,
  down: FocusRequester,
  compact: Boolean,
) {
  Column {
    Text(
      "Browse categories",
      color = MutedBlue,
      fontWeight = FontWeight.Bold,
      fontSize = 10.sp,
      letterSpacing = .7.sp,
    )
    Spacer(Modifier.height(5.dp))
    LazyRow(
      modifier = Modifier.fillMaxWidth().focusGroup(),
      horizontalArrangement = Arrangement.spacedBy(9.dp),
      contentPadding = PaddingValues(horizontal = 2.dp, vertical = 2.dp),
    ) {
      itemsIndexed(categories, key = { _, category -> category.label }) { _, category ->
        IptvCategoryCard(
          category = category,
          selected = category.label == selectedCategory,
          onClick = { onSelect(category) },
          compact = compact,
          modifier =
            Modifier.then(
                if (category.label == selectedCategory) Modifier.focusRequester(firstCategoryFocusRequester)
                else Modifier
              )
              .focusProperties {
                this.up = up
                this.down = down
              },
        )
      }
    }
  }
}

@Composable
private fun IptvCategoryCard(
  category: IptvCategory,
  selected: Boolean,
  onClick: () -> Unit,
  compact: Boolean,
  modifier: Modifier = Modifier,
) {
  var focused by remember { mutableStateOf(false) }
  val accent = iptvCategoryAccent(category.label)
  val scale by animateFloatAsState(if (focused) 1.04f else 1f, label = "IPTV category focus")
  val container by
    animateColorAsState(
      when {
        selected -> accent.copy(alpha = .24f)
        focused -> SoftWhite.copy(alpha = .12f)
        else -> NightSurface.copy(alpha = .9f)
      },
      label = "IPTV category background",
    )
  val outline by
    animateColorAsState(
      if (focused || selected) accent else SoftWhite.copy(alpha = .08f),
      label = "IPTV category outline",
    )
  Column(
    modifier =
      modifier.width(if (compact) 132.dp else 150.dp).height(if (compact) 56.dp else 64.dp)
        .testTag("iptv-category:${category.label}")
        .graphicsLayer { scaleX = scale; scaleY = scale }
        .clip(RoundedCornerShape(14.dp)).background(container)
        .border(if (focused) 3.dp else 1.dp, outline, RoundedCornerShape(14.dp))
        .onFocusChanged { focused = it.isFocused }.clickable(onClick = onClick)
        .semantics {
          role = Role.Tab
          this.selected = selected
          contentDescription = "${category.label}, ${category.channelCount} channels"
        }
        .padding(horizontal = 12.dp, vertical = if (compact) 7.dp else 9.dp),
    verticalArrangement = Arrangement.Center,
  ) {
    Text(
      category.label,
      color = if (selected) SoftWhite else SoftWhite.copy(alpha = .92f),
      fontWeight = FontWeight.Black,
      fontSize = if (compact) 11.sp else 12.sp,
      maxLines = 1,
      overflow = TextOverflow.Ellipsis,
    )
    Spacer(Modifier.height(2.dp))
    Text(
      "${category.channelCount} channels",
      color = accent,
      fontWeight = FontWeight.Bold,
      fontSize = 9.sp,
      maxLines = 1,
    )
  }
}

private fun iptvCategoryAccent(category: String): Color =
  when (category) {
    IPTV_CATEGORY_FAVORITES -> Color(0xFFFFD166)
    IPTV_CATEGORY_RECENT -> Color(0xFF9BE7FF)
    IPTV_CATEGORY_DADDYLIVE -> Color(0xFFFF8A65)
    IPTV_CATEGORY_SPORTS -> Color(0xFF59E6A8)
    IPTV_CATEGORY_NEWS -> Color(0xFF64B5FF)
    IPTV_CATEGORY_MOVIES -> Color(0xFFC19BFF)
    IPTV_CATEGORY_ENTERTAINMENT -> Color(0xFFFFA86B)
    IPTV_CATEGORY_KIDS -> Color(0xFFFF7DB5)
    IPTV_CATEGORY_MUSIC -> Color(0xFF8ED9FF)
    IPTV_CATEGORY_KNOWLEDGE -> Color(0xFFFFD166)
    IPTV_CATEGORY_FAITH -> Color(0xFF75D8C7)
    IPTV_CATEGORY_REGIONAL -> Color(0xFF9EB7FF)
    else -> GizMint
  }

@Composable
private fun IptvChannelCard(
  channel: IptvChannel,
  favorite: Boolean,
  onClick: () -> Unit,
  onToggleFavorite: () -> Unit,
  modifier: Modifier = Modifier,
) {
  val category = remember(channel) { iptvCategoryFor(channel) }
  val sourceCount = remember(channel) { channel.playbackSources.size }
  val channelContext =
    remember(channel, category) {
      if (channel.group.equals(category, ignoreCase = true)) channel.group
      else "$category · ${channel.group}"
    }
  var focused by remember { mutableStateOf(false) }
  val scale by animateFloatAsState(if (focused) 1.035f else 1f, label = "channel focus scale")
  val outline by
    animateColorAsState(
      if (focused) GizMint else SoftWhite.copy(alpha = .08f),
      label = "channel outline",
    )
  Row(
    modifier =
      modifier.height(98.dp).graphicsLayer { scaleX = scale; scaleY = scale }
        .clip(RoundedCornerShape(14.dp)).background(NightSurface)
        .border(if (focused) 3.dp else 1.dp, outline, RoundedCornerShape(14.dp))
        // hasFocus rather than isFocused: the star inside the card is focusable in its own right,
        // and the card it belongs to should not go dark the moment the remote reaches it.
        .onFocusChanged { focused = it.hasFocus }.clickable(onClick = onClick)
        .semantics {
          role = Role.Button
          contentDescription =
            "Watch ${channel.name}, ${channel.group}, ${channel.formatLabel}, $sourceCount source${if (sourceCount == 1) "" else "s"}"
        }
        .padding(11.dp),
    verticalAlignment = Alignment.CenterVertically,
  ) {
    TmdbArtwork(
      url = channel.logoUrl,
      contentDescription = "",
      compact = true,
      modifier = Modifier.size(74.dp).clip(RoundedCornerShape(11.dp)),
    )
    Spacer(Modifier.width(12.dp))
    Column(Modifier.weight(1f), verticalArrangement = Arrangement.Center) {
      Text(
        channel.name,
        color = SoftWhite,
        fontWeight = FontWeight.Black,
        fontSize = 13.sp,
        maxLines = 2,
        lineHeight = 17.sp,
        overflow = TextOverflow.Ellipsis,
      )
      Spacer(Modifier.height(5.dp))
      Row(verticalAlignment = Alignment.CenterVertically) {
        Text(
          channelContext,
          color = MutedBlue,
          fontSize = 10.sp,
          maxLines = 1,
          overflow = TextOverflow.Ellipsis,
          modifier = Modifier.weight(1f),
        )
        Spacer(Modifier.width(8.dp))
        Text(
          "LIVE · ${channel.formatLabel}" + if (sourceCount > 1) " · $sourceCount links" else "",
          color = GizMint,
          fontWeight = FontWeight.Black,
          fontSize = 9.sp,
          maxLines = 1,
        )
      }
    }
    IptvFavoriteToggle(channelName = channel.name, favorite = favorite, onToggle = onToggleFavorite)
  }
}

@Composable
private fun IptvFavoriteToggle(channelName: String, favorite: Boolean, onToggle: () -> Unit) {
  var focused by remember { mutableStateOf(false) }
  val tint = if (favorite) GizMint else SoftWhite.copy(alpha = if (focused) .95f else .45f)
  Icon(
    imageVector = if (favorite) Icons.Filled.Star else Icons.Filled.StarBorder,
    contentDescription = null,
    tint = tint,
    modifier =
      Modifier.size(34.dp)
        .testTag("iptv-favorite:$channelName")
        .clip(RoundedCornerShape(10.dp))
        .background(if (focused) SoftWhite.copy(alpha = .14f) else Color.Transparent)
        .border(
          if (focused) 2.dp else 0.dp,
          if (focused) GizMint else Color.Transparent,
          RoundedCornerShape(10.dp),
        )
        .onFocusChanged { focused = it.isFocused }
        .clickable(onClick = onToggle)
        .semantics {
          role = Role.Checkbox
          selected = favorite
          contentDescription =
            if (favorite) "Remove $channelName from favourites" else "Add $channelName to favourites"
        }
        .padding(7.dp),
  )
}
