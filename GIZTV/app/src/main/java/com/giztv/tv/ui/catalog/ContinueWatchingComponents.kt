package com.giztv.tv.ui.catalog

import androidx.compose.animation.animateColorAsState
import androidx.compose.animation.core.animateFloatAsState
import androidx.compose.foundation.background
import androidx.compose.foundation.border
import androidx.compose.foundation.clickable
import androidx.compose.foundation.focusGroup
import androidx.compose.foundation.layout.Arrangement
import androidx.compose.foundation.layout.Box
import androidx.compose.foundation.layout.Column
import androidx.compose.foundation.layout.PaddingValues
import androidx.compose.foundation.layout.Row
import androidx.compose.foundation.layout.Spacer
import androidx.compose.foundation.layout.aspectRatio
import androidx.compose.foundation.layout.fillMaxHeight
import androidx.compose.foundation.layout.fillMaxWidth
import androidx.compose.foundation.layout.height
import androidx.compose.foundation.layout.heightIn
import androidx.compose.foundation.layout.padding
import androidx.compose.foundation.layout.width
import androidx.compose.foundation.layout.widthIn
import androidx.compose.foundation.lazy.LazyListState
import androidx.compose.foundation.lazy.LazyRow
import androidx.compose.foundation.lazy.items
import androidx.compose.foundation.shape.RoundedCornerShape
import androidx.compose.runtime.Composable
import androidx.compose.runtime.LaunchedEffect
import androidx.compose.runtime.getValue
import androidx.compose.runtime.mutableStateOf
import androidx.compose.runtime.remember
import androidx.compose.runtime.saveable.rememberSaveable
import androidx.compose.runtime.setValue
import androidx.compose.ui.Alignment
import androidx.compose.ui.Modifier
import androidx.compose.ui.draw.clip
import androidx.compose.ui.focus.FocusRequester
import androidx.compose.ui.focus.focusProperties
import androidx.compose.ui.focus.focusRequester
import androidx.compose.ui.focus.onFocusChanged
import androidx.compose.ui.graphics.Color
import androidx.compose.ui.graphics.graphicsLayer
import androidx.compose.ui.input.key.Key
import androidx.compose.ui.input.key.KeyEventType
import androidx.compose.ui.input.key.key
import androidx.compose.ui.input.key.onPreviewKeyEvent
import androidx.compose.ui.input.key.type
import androidx.compose.ui.semantics.Role
import androidx.compose.ui.semantics.contentDescription
import androidx.compose.ui.semantics.role
import androidx.compose.ui.semantics.semantics
import androidx.compose.ui.text.font.FontWeight
import androidx.compose.ui.text.style.TextAlign
import androidx.compose.ui.text.style.TextOverflow
import androidx.compose.ui.unit.Dp
import androidx.compose.ui.unit.dp
import androidx.compose.ui.unit.sp
import androidx.compose.ui.window.Dialog
import androidx.tv.material3.Text
import com.giztv.tv.data.WatchHistoryEntry
import com.giztv.tv.theme.GizMint
import com.giztv.tv.theme.DeepSpace
import com.giztv.tv.theme.MutedBlue
import com.giztv.tv.theme.NightSurface
import com.giztv.tv.theme.SoftWhite

@Composable
internal fun ContinueWatchingSection(
  entries: List<WatchHistoryEntry>,
  onResume: (WatchHistoryEntry) -> Unit,
  onClearHistory: (() -> Unit)? = null,
  firstCardFocusRequester: FocusRequester,
  up: FocusRequester,
  down: FocusRequester,
  hasGrid: Boolean,
  onMoveUp: (() -> Unit)? = null,
  onMoveDown: (() -> Unit)? = null,
  edge: Dp = 42.dp,
) {
  Column(
    modifier =
      Modifier.onPreviewKeyEvent { event ->
        if (event.type != KeyEventType.KeyDown) return@onPreviewKeyEvent false
        when (event.key) {
          Key.DirectionUp -> onMoveUp?.let { it(); true } ?: false
          Key.DirectionDown -> onMoveDown?.let { it(); true } ?: false
          else -> false
        }
      }
  ) {
    Text(
      if (entries.isEmpty()) "Watch history" else "Continue watching",
      color = SoftWhite,
      fontWeight = FontWeight.Black,
      fontSize = 16.sp,
      modifier = Modifier.padding(horizontal = edge),
    )
    Spacer(Modifier.height(8.dp))
    val continueListState = rememberSaveable(saver = LazyListState.Saver) { LazyListState() }
    LazyRow(
      state = continueListState,
      modifier = Modifier.fillMaxWidth().focusGroup(),
      contentPadding = PaddingValues(horizontal = edge),
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
      onClearHistory?.let { clearHistory ->
        item(key = "clear_watch_history") {
          ClearWatchHistoryCard(
            onClick = clearHistory,
            modifier =
              (if (entries.isEmpty()) Modifier.focusRequester(firstCardFocusRequester) else Modifier)
                .focusProperties {
                  this.up = up
                  this.down = if (hasGrid) down else FocusRequester.Default
                },
          )
        }
      }
    }
  }
}

@Composable
private fun ClearWatchHistoryCard(onClick: () -> Unit, modifier: Modifier = Modifier) {
  var focused by remember { mutableStateOf(false) }
  val scale by animateFloatAsState(if (focused) 1.05f else 1f, label = "clear history scale")
  val background by
    animateColorAsState(
      if (focused) Color(0xFFFF8A80) else NightSurface,
      label = "clear history background",
    )
  val outline by
    animateColorAsState(
      if (focused) SoftWhite else Color(0xFFFF8A80).copy(alpha = .55f),
      label = "clear history outline",
    )
  Box(
    modifier =
      modifier.width(184.dp).height(97.dp).graphicsLayer { scaleX = scale; scaleY = scale }
        .clip(RoundedCornerShape(14.dp)).background(background)
        .border(if (focused) 3.dp else 1.dp, outline, RoundedCornerShape(14.dp))
        .onFocusChanged { focused = it.isFocused }.clickable(onClick = onClick)
        .semantics {
          role = Role.Button
          contentDescription = "Clear watch history"
        }
        .padding(horizontal = 18.dp),
    contentAlignment = Alignment.Center,
  ) {
    Text(
      "Clear watch history",
      color = if (focused) DeepSpace else Color(0xFFFFB4AB),
      fontWeight = FontWeight.Black,
      fontSize = 12.sp,
      textAlign = TextAlign.Center,
    )
  }
}

@Composable
internal fun ClearWatchHistoryDialog(onDismiss: () -> Unit, onConfirm: () -> Unit) {
  val cancelFocusRequester = remember { FocusRequester() }
  val clearFocusRequester = remember { FocusRequester() }

  Dialog(onDismissRequest = onDismiss) {
    Column(
      modifier =
        Modifier.fillMaxWidth(.95f).widthIn(max = 520.dp).heightIn(max = 420.dp).clip(RoundedCornerShape(18.dp))
          .background(NightSurface).border(1.dp, SoftWhite.copy(alpha = .16f), RoundedCornerShape(18.dp))
          .padding(16.dp),
    ) {
      Text(
        "CLEAR WATCH HISTORY?",
        color = SoftWhite,
        fontWeight = FontWeight.Black,
        fontSize = 18.sp,
        letterSpacing = 1.2.sp,
      )
      Spacer(Modifier.height(8.dp))
      Text(
        "This removes watched markers, recommendations, and every saved resume position. This can't be undone.",
        color = MutedBlue,
        fontSize = 13.sp,
        lineHeight = 18.sp,
      )
      Spacer(Modifier.height(16.dp))
      Row(
        modifier = Modifier.fillMaxWidth(),
        horizontalArrangement = Arrangement.End,
        verticalAlignment = Alignment.CenterVertically,
      ) {
        CatalogButton(
          label = "Cancel",
          onClick = onDismiss,
          modifier =
            Modifier.focusRequester(cancelFocusRequester).focusProperties {
              right = clearFocusRequester
            },
        )
        Spacer(Modifier.width(12.dp))
        CatalogButton(
          label = "Clear history",
          onClick = onConfirm,
          modifier =
            Modifier.focusRequester(clearFocusRequester).focusProperties {
              left = cancelFocusRequester
            },
        )
      }
    }
  }

  LaunchedEffect(Unit) { cancelFocusRequester.requestFocus() }
}

@Composable
internal fun ContinueWatchingCard(
  entry: WatchHistoryEntry,
  onClick: () -> Unit,
  modifier: Modifier = Modifier,
) {
  var focused by remember { mutableStateOf(false) }
  val scale by animateFloatAsState(if (focused) 1.05f else 1f, label = "continue scale")
  val outline by
    animateColorAsState(if (focused) GizMint else SoftWhite.copy(alpha = .08f), label = "continue outline")
  Row(
    modifier =
      modifier.width(228.dp).graphicsLayer { scaleX = scale; scaleY = scale }
        .clip(RoundedCornerShape(14.dp))
        .background(if (focused) NightSurface.copy(alpha = .92f) else Color.Transparent)
        .border(if (focused) 3.dp else 0.dp, outline, RoundedCornerShape(14.dp))
        .onFocusChanged { focused = it.isFocused }.clickable(onClick = onClick)
        .semantics {
          role = Role.Button
          contentDescription = "Resume ${entry.title}${entry.subtitle?.let { ", $it" } ?: ""}"
        }
        .padding(7.dp),
    verticalAlignment = Alignment.CenterVertically,
  ) {
    Box(
      modifier =
        Modifier.width(60.dp).clip(RoundedCornerShape(9.dp))
          .border(1.dp, SoftWhite.copy(alpha = .12f), RoundedCornerShape(9.dp)),
      contentAlignment = Alignment.BottomStart,
    ) {
      TmdbArtwork(
        url = entry.posterUrl,
        contentDescription = "${entry.title} poster",
        modifier = Modifier.fillMaxWidth().aspectRatio(2f / 3f),
        compact = true,
      )
      Box(modifier = Modifier.fillMaxWidth().height(4.dp).background(DeepSpace.copy(alpha = .7f))) {
        Box(Modifier.fillMaxWidth(entry.progressFraction).fillMaxHeight().background(GizMint))
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
        Text(it, color = GizMint, fontWeight = FontWeight.Bold, fontSize = 10.sp, maxLines = 1)
      }
    }
  }
}
