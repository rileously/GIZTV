package com.example.auroratv.ui.iptv

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
import com.example.auroratv.theme.AuroraMint
import com.example.auroratv.theme.DeepSpace
import com.example.auroratv.theme.MutedBlue
import com.example.auroratv.theme.NightSurface
import com.example.auroratv.theme.SoftWhite
import com.example.auroratv.ui.catalog.CatalogButton
import com.example.auroratv.ui.catalog.CatalogIconButton
import com.example.auroratv.ui.catalog.CatalogSearchField
import com.example.auroratv.ui.catalog.ChipRow
import com.example.auroratv.ui.catalog.GizTvMark
import com.example.auroratv.ui.catalog.StatusPanel
import com.example.auroratv.ui.catalog.TmdbArtwork
import com.example.auroratv.ui.catalog.remoteFocusNavigation
import kotlinx.coroutines.launch

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
  val gridFocusRequester = remember { FocusRequester() }
  val gridState = rememberLazyGridState()

  var playlist by remember { mutableStateOf<IptvPlaylist?>(null) }
  val selectedCategory = browseState.selectedCategory
  val selectedGroup = browseState.selectedGroup
  val query = browseState.query
  var loading by remember { mutableStateOf(true) }
  var searchExpanded by rememberSaveable { mutableStateOf(false) }
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

  val channels = playlist?.channels.orEmpty()
  val categories = remember(channels) { iptvCategories(channels) }
  val activeCategory = selectedCategory.takeIf { selected -> categories.any { it.label == selected } }
    ?: ALL_IPTV_CHANNELS
  val groups = remember(channels, activeCategory) { iptvGroupsForCategory(channels, activeCategory) }
  val activeGroup = selectedGroup.takeIf(groups::contains) ?: ALL_IPTV_GROUPS
  val searching = query.isNotBlank()
  val visible =
    remember(channels, activeCategory, activeGroup, query) {
      if (query.isNotBlank()) {
        visibleIptvChannels(channels, category = null, group = null, query = query)
      } else {
        visibleIptvChannels(channels, activeCategory, activeGroup, query)
      }
    }

  LaunchedEffect(activeCategory, activeGroup, query, loading) {
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
              "${channels.size} channels · ${categories.size - 1} clear categories",
              color = AuroraMint.copy(alpha = .8f),
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
          if (phoneDense && showSearchRow) {
            CatalogIconButton("Close search", Icons.Filled.Close, ::collapseSearchUi)
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
                down = bodyFocusRequester ?: FocusRequester.Default
              }.remoteFocusNavigation(up = searchUpRequester, down = bodyFocusRequester),
          )
          CatalogButton(
            label = "Search",
            onClick = ::runSearch,
            modifier =
              Modifier.focusRequester(searchButtonFocusRequester).focusProperties {
                up = searchUpRequester
                left = searchFieldFocusRequester
                down = bodyFocusRequester ?: FocusRequester.Default
              }.remoteFocusNavigation(
                up = searchUpRequester,
                left = searchFieldFocusRequester,
                down = bodyFocusRequester,
              ),
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
              if (query.isNotBlank()) "No channels match “${query.trim()}”."
              else "There are no channels in this category.",
            modifier = Modifier.weight(1f),
            actionLabel = if (query.isNotBlank()) "Clear search" else "All channels",
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
                      searching -> "Results for “${query.trim()}”"
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
                    color = AuroraMint,
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
                onClick = { dismissKeyboard(); onPlay(channel) },
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
    else -> AuroraMint
  }

@Composable
private fun IptvChannelCard(
  channel: IptvChannel,
  onClick: () -> Unit,
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
      if (focused) AuroraMint else SoftWhite.copy(alpha = .08f),
      label = "channel outline",
    )
  Row(
    modifier =
      modifier.height(98.dp).graphicsLayer { scaleX = scale; scaleY = scale }
        .clip(RoundedCornerShape(14.dp)).background(NightSurface)
        .border(if (focused) 3.dp else 1.dp, outline, RoundedCornerShape(14.dp))
        .onFocusChanged { focused = it.isFocused }.clickable(onClick = onClick)
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
          color = AuroraMint,
          fontWeight = FontWeight.Black,
          fontSize = 9.sp,
          maxLines = 1,
        )
      }
    }
  }
}
