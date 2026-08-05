package com.example.auroratv.ui.iptv

import android.util.Log
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
import androidx.compose.foundation.rememberScrollState
import androidx.compose.foundation.shape.RoundedCornerShape
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
import com.example.auroratv.theme.AuroraMint
import com.example.auroratv.theme.DeepSpace
import com.example.auroratv.theme.MutedBlue
import com.example.auroratv.theme.NightSurface
import com.example.auroratv.theme.SoftWhite
import com.example.auroratv.ui.catalog.CatalogButton
import com.example.auroratv.ui.catalog.CatalogSearchField
import com.example.auroratv.ui.catalog.ChipRow
import com.example.auroratv.ui.catalog.GizTvMark
import com.example.auroratv.ui.catalog.StatusPanel
import com.example.auroratv.ui.catalog.TmdbArtwork
import com.example.auroratv.ui.catalog.remoteFocusNavigation
import kotlinx.coroutines.launch

@Composable
internal fun IptvScreen(
  onPlay: (IptvChannel) -> Unit,
  onBack: () -> Unit,
  modifier: Modifier = Modifier,
) {
  val context = LocalContext.current
  val scope = rememberCoroutineScope()
  val focusManager = LocalFocusManager.current
  val keyboardController = LocalSoftwareKeyboardController.current
  val backFocusRequester = remember { FocusRequester() }
  val reloadFocusRequester = remember { FocusRequester() }
  val chipFocusRequester = remember { FocusRequester() }
  val searchFieldFocusRequester = remember { FocusRequester() }
  val searchButtonFocusRequester = remember { FocusRequester() }
  val gridFocusRequester = remember { FocusRequester() }
  val gridState = rememberLazyGridState()

  var playlist by remember { mutableStateOf<IptvPlaylist?>(null) }
  var selectedGroup by rememberSaveable { mutableStateOf(ALL_IPTV_CHANNELS) }
  var query by rememberSaveable { mutableStateOf("") }
  var loading by remember { mutableStateOf(true) }
  var errorMessage by remember { mutableStateOf<String?>(null) }

  fun dismissKeyboard() {
    focusManager.clearFocus()
    keyboardController?.hide()
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
      searchFieldFocusRequester.requestFocus()
    } else {
      dismissKeyboard()
      searchButtonFocusRequester.requestFocus()
    }
  }

  LaunchedEffect(Unit) {
    load(refresh = false)
    backFocusRequester.requestFocus()
  }

  val channels = playlist?.channels.orEmpty()
  val groups = remember(channels) { iptvGroups(channels) }
  val activeGroup = selectedGroup.takeIf(groups::contains) ?: ALL_IPTV_CHANNELS
  val visible = remember(channels, activeGroup, query) { visibleIptvChannels(channels, activeGroup, query) }

  LaunchedEffect(activeGroup, query, loading) {
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
    val bodyFocusRequester = gridFocusRequester.takeIf { visible.isNotEmpty() }
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
        Column {
          Text(
            "IPTV",
            color = SoftWhite,
            fontWeight = FontWeight.Black,
            letterSpacing = 2.5.sp,
            fontSize = 18.sp,
          )
          if (!narrow && channels.isNotEmpty()) {
            Text(
              "${channels.size} channels · ${groups.size - 1} groups",
              color = AuroraMint.copy(alpha = .8f),
              fontWeight = FontWeight.Bold,
              fontSize = 9.sp,
            )
          }
        }
        Spacer(Modifier.weight(1f))
        Row(horizontalArrangement = Arrangement.spacedBy(8.dp), verticalAlignment = Alignment.CenterVertically) {
          CatalogButton(
            label = "Reload",
            onClick = { dismissKeyboard(); load(refresh = true) },
            modifier =
              Modifier.focusRequester(reloadFocusRequester).focusProperties {
                right = backFocusRequester
                down = chipFocusRequester
              },
          )
          CatalogButton(
            label = "Back",
            onClick = { dismissKeyboard(); onBack() },
            modifier =
              Modifier.focusRequester(backFocusRequester).focusProperties {
                left = reloadFocusRequester
                down = chipFocusRequester
              },
          )
        }
      }
      Spacer(Modifier.height(if (compact) 8.dp else 12.dp))

      if (groups.isNotEmpty()) {
        ChipRow(
          labels = groups,
          selectedIndex = groups.indexOf(activeGroup),
          onSelect = { index ->
            selectedGroup = groups.getOrNull(index) ?: ALL_IPTV_CHANNELS
            query = ""
          },
          firstChipFocusRequester = chipFocusRequester,
          semanticsRole = Role.Tab,
          up = backFocusRequester,
          down = searchFieldFocusRequester,
          compactChips = true,
          modifier = Modifier.horizontalScroll(rememberScrollState()),
        )
        Spacer(Modifier.height(9.dp))
      }

      Row(
        modifier = Modifier.focusGroup(),
        horizontalArrangement = Arrangement.spacedBy(9.dp),
        verticalAlignment = Alignment.CenterVertically,
      ) {
        CatalogSearchField(
          value = query,
          placeholder = "Search channels or groups…",
          onValueChanged = { query = it },
          onSearch = ::runSearch,
          modifier =
            Modifier.weight(1f).focusRequester(searchFieldFocusRequester).focusProperties {
              up = chipFocusRequester
              right = searchButtonFocusRequester
              down = bodyFocusRequester ?: FocusRequester.Default
            }.remoteFocusNavigation(up = chipFocusRequester, down = bodyFocusRequester),
        )
        CatalogButton(
          label = "Search",
          onClick = ::runSearch,
          modifier =
            Modifier.focusRequester(searchButtonFocusRequester).focusProperties {
              up = chipFocusRequester
              left = searchFieldFocusRequester
              down = bodyFocusRequester ?: FocusRequester.Default
            }.remoteFocusNavigation(
              up = chipFocusRequester,
              left = searchFieldFocusRequester,
              down = bodyFocusRequester,
            ),
        )
      }
      Spacer(Modifier.height(if (compact) 8.dp else 14.dp))

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
              else "There are no channels in this group.",
            modifier = Modifier.weight(1f),
          )
        else ->
          LazyVerticalGrid(
            state = gridState,
            columns = GridCells.Adaptive(minSize = if (narrow) 220.dp else 270.dp),
            modifier = Modifier.weight(1f).fillMaxWidth(),
            contentPadding = PaddingValues(bottom = 22.dp),
            horizontalArrangement = Arrangement.spacedBy(if (narrow) 12.dp else 16.dp),
            verticalArrangement = Arrangement.spacedBy(if (narrow) 12.dp else 16.dp),
          ) {
            item(span = { GridItemSpan(maxLineSpan) }) {
              Column {
                Row(verticalAlignment = Alignment.CenterVertically) {
                  Text(
                    if (query.isNotBlank()) "Results for “${query.trim()}”" else activeGroup,
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
private fun IptvChannelCard(
  channel: IptvChannel,
  onClick: () -> Unit,
  modifier: Modifier = Modifier,
) {
  var focused by remember { mutableStateOf(false) }
  val scale by animateFloatAsState(if (focused) 1.035f else 1f, label = "channel focus scale")
  val outline by
    animateColorAsState(
      if (focused) AuroraMint else SoftWhite.copy(alpha = .08f),
      label = "channel outline",
    )
  Row(
    modifier =
      modifier.height(92.dp).graphicsLayer { scaleX = scale; scaleY = scale }
        .clip(RoundedCornerShape(14.dp)).background(NightSurface)
        .border(if (focused) 3.dp else 1.dp, outline, RoundedCornerShape(14.dp))
        .onFocusChanged { focused = it.isFocused }.clickable(onClick = onClick)
        .semantics {
          role = Role.Button
          contentDescription = "Watch ${channel.name}, ${channel.group}, ${channel.formatLabel}"
        }
        .padding(10.dp),
    verticalAlignment = Alignment.CenterVertically,
  ) {
    TmdbArtwork(
      url = channel.logoUrl,
      contentDescription = "",
      compact = true,
      modifier = Modifier.size(70.dp).clip(RoundedCornerShape(10.dp)),
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
          channel.group,
          color = MutedBlue,
          fontSize = 10.sp,
          maxLines = 1,
          overflow = TextOverflow.Ellipsis,
          modifier = Modifier.weight(1f),
        )
        Spacer(Modifier.width(8.dp))
        Text(
          channel.formatLabel,
          color = AuroraMint,
          fontWeight = FontWeight.Black,
          fontSize = 9.sp,
          maxLines = 1,
        )
      }
    }
  }
}
