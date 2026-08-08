package com.example.auroratv.ui.dlhd

import android.util.Log
import androidx.activity.compose.BackHandler
import androidx.compose.animation.animateColorAsState
import androidx.compose.animation.core.animateFloatAsState
import androidx.compose.foundation.background
import androidx.compose.foundation.border
import androidx.compose.foundation.clickable
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
import androidx.compose.material.icons.Icons
import androidx.compose.material.icons.filled.Close
import androidx.compose.material.icons.filled.Search
import androidx.compose.material.icons.filled.SportsSoccer
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
import androidx.tv.material3.Icon
import androidx.tv.material3.Text
import com.example.auroratv.data.PlaybackContext
import com.example.auroratv.theme.AuroraMint
import com.example.auroratv.theme.DeepSpace
import com.example.auroratv.theme.MutedBlue
import com.example.auroratv.theme.NightSurface
import com.example.auroratv.theme.SoftWhite
import com.example.auroratv.ui.catalog.CatalogButton
import com.example.auroratv.ui.catalog.CatalogIconButton
import com.example.auroratv.ui.catalog.CatalogSearchField
import com.example.auroratv.ui.catalog.GizTvMark
import com.example.auroratv.ui.catalog.StatusPanel
import kotlinx.coroutines.delay
import kotlinx.coroutines.launch

/**
 * DaddyLive soccer schedule as a browsable page.
 *
 * Each card is a fixture; pressing it opens the first channel in the native player via the same
 * browser-resolve path every other title uses. Extra channels show as chips when more than one
 * stream carries the match.
 */
@Composable
internal fun DlhdSoccerScreen(
  onPlay: (PlaybackContext) -> Unit,
  onConsidering: (PlaybackContext) -> Unit = {},
  onBack: () -> Unit,
  hideBackButton: Boolean = false,
  requestSearchFocus: Boolean = false,
  onSearchFocusHandled: () -> Unit = {},
  modifier: Modifier = Modifier,
) {
  val scope = rememberCoroutineScope()
  val focusManager = LocalFocusManager.current
  val keyboardController = LocalSoftwareKeyboardController.current
  val backFocusRequester = remember { FocusRequester() }
  val refreshFocusRequester = remember { FocusRequester() }
  val searchFieldFocusRequester = remember { FocusRequester() }
  val searchButtonFocusRequester = remember { FocusRequester() }
  val gridFocusRequester = remember { FocusRequester() }
  val gridState = rememberLazyGridState()

  var query by rememberSaveable { mutableStateOf("") }
  var searchActive by rememberSaveable { mutableStateOf(false) }
  var searchExpanded by rememberSaveable { mutableStateOf(false) }
  var events by remember { mutableStateOf<List<DlhdSoccerEvent>>(emptyList()) }
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
      runCatching { if (refresh) DlhdSoccerRepository.refresh() else DlhdSoccerRepository.events() }
        .onSuccess { events = it }
        .onFailure {
          Log.e("GizTvDlhd", "Soccer schedule load failed", it)
          errorMessage = friendlyDlhdError(it)
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

  LaunchedEffect(Unit) {
    while (true) {
      delay(DLHD_REFRESH_INTERVAL_MS)
      runCatching { DlhdSoccerRepository.refresh() }.onSuccess { events = it }
    }
  }

  val visible =
    remember(events, searchActive, query) {
      if (searchActive) searchDlhdEvents(events, query) else events
    }

  LaunchedEffect(searchActive, loading) {
    if (!loading && gridState.layoutInfo.totalItemsCount > 0) gridState.scrollToItem(0)
  }

  BoxWithConstraints(
    modifier =
      modifier.fillMaxSize().pointerInput(Unit) { detectTapGestures { dismissKeyboard() } }
        .background(
          Brush.radialGradient(
            colors = listOf(Color(0xFF143A2A), DeepSpace),
            radius = 1_300f,
            center = androidx.compose.ui.geometry.Offset(1_300f, 180f),
          )
        )
  ) {
    val narrow = maxWidth < 600.dp
    val compact = maxHeight < 600.dp
    val phoneDense = hideBackButton && narrow
    val showSearchRow = !phoneDense || searchExpanded || searchActive || query.isNotBlank()

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
        Spacer(Modifier.width(if (phoneDense) 8.dp else 11.dp))
        Icon(
          Icons.Filled.SportsSoccer,
          contentDescription = null,
          tint = AuroraMint,
          modifier = Modifier.size(if (phoneDense) 18.dp else 22.dp),
        )
        Spacer(Modifier.width(8.dp))
        Text(
          "SOCCER",
          color = SoftWhite,
          fontWeight = FontWeight.Black,
          letterSpacing = if (phoneDense) 2.sp else 2.5.sp,
          fontSize = if (phoneDense) 15.sp else 18.sp,
        )
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
          CatalogButton(
            label = "Refresh",
            onClick = {
              dismissKeyboard()
              load(refresh = true)
            },
            modifier =
              Modifier.focusRequester(refreshFocusRequester).focusProperties {
                right = backFocusRequester
                down = searchFieldFocusRequester
              },
          )
          if (!hideBackButton) {
            CatalogButton(
              label = "Back",
              onClick = {
                dismissKeyboard()
                onBack()
              },
              modifier =
                Modifier.focusRequester(backFocusRequester).focusProperties {
                  left = refreshFocusRequester
                  down = searchFieldFocusRequester
                },
            )
          }
        }
      }
      Spacer(Modifier.height(if (phoneDense) 6.dp else if (compact) 8.dp else 12.dp))

      if (showSearchRow) {
        Row(
          horizontalArrangement = Arrangement.spacedBy(9.dp),
          verticalAlignment = Alignment.CenterVertically,
          modifier = Modifier.fillMaxWidth(),
        ) {
          CatalogSearchField(
            query,
            "Search teams, leagues, channels…",
            {
              query = it
              if (it.isBlank()) {
                searchActive = false
                if (phoneDense) searchExpanded = false
              }
            },
            ::runSearch,
            Modifier.weight(1f).focusRequester(searchFieldFocusRequester).focusProperties {
              up = if (hideBackButton) refreshFocusRequester else backFocusRequester
              down = gridFocusRequester
              right = searchButtonFocusRequester
            },
          )
          CatalogButton(
            "Search",
            ::runSearch,
            Modifier.focusRequester(searchButtonFocusRequester).focusProperties {
              up = if (hideBackButton) refreshFocusRequester else backFocusRequester
              left = searchFieldFocusRequester
              down = gridFocusRequester
            },
          )
        }
        Spacer(Modifier.height(if (phoneDense) 6.dp else if (compact) 8.dp else 14.dp))
      }

      when {
        loading && events.isEmpty() ->
          StatusPanel("Loading soccer schedule…", Modifier.weight(1f), loading = true)
        errorMessage != null && events.isEmpty() ->
          StatusPanel(
            message = errorMessage ?: "Soccer schedule could not be loaded",
            modifier = Modifier.weight(1f),
            actionLabel = "Try again",
            onAction = { load(refresh = true) },
          )
        visible.isEmpty() ->
          StatusPanel(
            message =
              if (searchActive) "No fixtures match “${query.trim()}”."
              else "No soccer events listed right now.",
            modifier = Modifier.weight(1f),
          )
        else ->
          LazyVerticalGrid(
            state = gridState,
            columns = GridCells.Adaptive(minSize = if (narrow) 240.dp else 300.dp),
            modifier = Modifier.weight(1f).fillMaxWidth(),
            contentPadding = PaddingValues(bottom = if (phoneDense) 8.dp else 22.dp),
            horizontalArrangement = Arrangement.spacedBy(if (narrow) 12.dp else 16.dp),
            verticalArrangement =
              Arrangement.spacedBy(if (phoneDense) 10.dp else if (narrow) 12.dp else 16.dp),
          ) {
            item(span = { GridItemSpan(maxLineSpan) }) {
              Column {
                Row(verticalAlignment = Alignment.CenterVertically) {
                  Text(
                    if (searchActive) "Results for “${query.trim()}”" else "All soccer events",
                    color = SoftWhite,
                    fontWeight = FontWeight.Black,
                    fontSize = if (narrow) 17.sp else 19.sp,
                  )
                  Spacer(Modifier.width(10.dp))
                  Text(
                    if (visible.size == 1) "1 match" else "${visible.size} matches",
                    color = AuroraMint,
                    fontWeight = FontWeight.Bold,
                    fontSize = 11.sp,
                  )
                  Spacer(Modifier.weight(1f))
                  if (!narrow) {
                    Text(
                      "DaddyLive · opens in player",
                      color = MutedBlue.copy(alpha = .6f),
                      fontSize = 9.sp,
                    )
                  }
                }
                Spacer(Modifier.height(8.dp))
              }
            }
            items(items = visible, key = { it.id }) { event ->
              DlhdSoccerEventCard(
                event = event,
                onPlay = { channel ->
                  dismissKeyboard()
                  onPlay(event.toPlayback(channel))
                },
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

private const val FIXTURE_DWELL_MS = 1_200L

@Composable
private fun DlhdSoccerEventCard(
  event: DlhdSoccerEvent,
  onPlay: (DlhdSoccerChannel) -> Unit,
  onDwell: (() -> Unit)? = null,
  modifier: Modifier = Modifier,
) {
  var focused by remember { mutableStateOf(false) }
  if (onDwell != null) {
    LaunchedEffect(focused) {
      if (!focused) return@LaunchedEffect
      delay(FIXTURE_DWELL_MS)
      onDwell()
    }
  }
  val scale by animateFloatAsState(if (focused) 1.035f else 1f, label = "soccer focus scale")
  val outline by
    animateColorAsState(
      if (focused) AuroraMint else SoftWhite.copy(alpha = .08f),
      label = "soccer outline",
    )
  Column(
    modifier =
      modifier.graphicsLayer {
          scaleX = scale
          scaleY = scale
        }
        .clip(RoundedCornerShape(14.dp))
        .background(NightSurface)
        .border(if (focused) 3.dp else 1.dp, outline, RoundedCornerShape(14.dp))
        .onFocusChanged { focused = it.isFocused }
        .clickable { onPlay(event.channels.first()) }
        .semantics {
          role = Role.Button
          contentDescription =
            "Watch ${event.match}, ${event.league}, ${event.channelLabel}, ${event.kickOffLabel}"
        }
        .padding(horizontal = 13.dp, vertical = 12.dp)
  ) {
    Row(verticalAlignment = Alignment.CenterVertically) {
      Text(
        event.kickOffLabel ?: event.ukTime,
        color = AuroraMint,
        fontWeight = FontWeight.Black,
        fontSize = 14.sp,
      )
      Spacer(Modifier.weight(1f))
      Text(
        "SOCCER",
        color = MutedBlue.copy(alpha = .75f),
        fontWeight = FontWeight.Bold,
        letterSpacing = 1.sp,
        fontSize = 9.sp,
        maxLines = 1,
      )
    }
    Spacer(Modifier.height(10.dp))
    Text(
      event.match,
      color = SoftWhite,
      fontWeight = FontWeight.Bold,
      fontSize = 14.sp,
      maxLines = 2,
      minLines = 2,
      lineHeight = 18.sp,
      overflow = TextOverflow.Ellipsis,
    )
    if (event.league.isNotBlank()) {
      Spacer(Modifier.height(6.dp))
      Text(
        event.league,
        color = MutedBlue,
        fontSize = 11.sp,
        maxLines = 1,
        overflow = TextOverflow.Ellipsis,
      )
    }
    Spacer(Modifier.height(10.dp))
    if (event.channels.size == 1) {
      Text(
        event.primaryChannel,
        color = SoftWhite.copy(alpha = .78f),
        fontWeight = FontWeight.SemiBold,
        fontSize = 11.sp,
        maxLines = 1,
        overflow = TextOverflow.Ellipsis,
      )
    } else {
      Row(
        modifier = Modifier.fillMaxWidth().horizontalScroll(rememberScrollState()),
        horizontalArrangement = Arrangement.spacedBy(6.dp),
      ) {
        event.channels.forEach { channel ->
          Text(
            channel.name,
            color = SoftWhite,
            fontWeight = FontWeight.Bold,
            fontSize = 10.sp,
            maxLines = 1,
            modifier =
              Modifier.clip(RoundedCornerShape(8.dp))
                .background(AuroraMint.copy(alpha = .14f))
                .border(1.dp, AuroraMint.copy(alpha = .35f), RoundedCornerShape(8.dp))
                .clickable { onPlay(channel) }
                .padding(horizontal = 8.dp, vertical = 5.dp),
          )
        }
      }
    }
  }
}
