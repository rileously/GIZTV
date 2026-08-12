package com.giztv.tv.ui.catalog

import androidx.compose.animation.animateColorAsState
import androidx.compose.animation.core.animateFloatAsState
import androidx.compose.foundation.background
import androidx.compose.foundation.border
import androidx.compose.foundation.clickable
import androidx.compose.foundation.focusGroup
import androidx.compose.foundation.horizontalScroll
import androidx.compose.foundation.layout.Arrangement
import androidx.compose.foundation.layout.Column
import androidx.compose.foundation.layout.Row
import androidx.compose.foundation.layout.Spacer
import androidx.compose.foundation.layout.fillMaxWidth
import androidx.compose.foundation.layout.height
import androidx.compose.foundation.layout.padding
import androidx.compose.foundation.layout.size
import androidx.compose.foundation.layout.widthIn
import androidx.compose.foundation.rememberScrollState
import androidx.compose.foundation.shape.RoundedCornerShape
import androidx.compose.material.icons.Icons
import androidx.compose.material.icons.filled.DeleteSweep
import androidx.compose.material.icons.filled.History
import androidx.compose.material3.Icon
import androidx.compose.runtime.Composable
import androidx.compose.runtime.getValue
import androidx.compose.runtime.mutableStateOf
import androidx.compose.runtime.remember
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
import androidx.compose.ui.text.style.TextOverflow
import androidx.compose.ui.unit.dp
import androidx.compose.ui.unit.sp
import androidx.tv.material3.Text
import com.giztv.tv.theme.GizMint
import com.giztv.tv.theme.DeepSpace
import com.giztv.tv.theme.MutedBlue
import com.giztv.tv.theme.NightSurface
import com.giztv.tv.theme.SoftWhite

/**
 * What this listing has been searched for before, offered back as one press each.
 *
 * A row rather than a dropdown: a dropdown has to be opened, and on a television it would sit over
 * the results the pad is trying to reach. This is simply the line under the search box, so it is
 * read on the way past and skipped by carrying on down.
 *
 * [onClear] ends the row — the last chip is what removes the whole list, kept at the far end so a
 * pad running along the queries reaches every one of them before it reaches the thing that throws
 * them away.
 */
@Composable
internal fun SearchHistoryRow(
  queries: List<String>,
  onSelect: (String) -> Unit,
  onClear: () -> Unit,
  modifier: Modifier = Modifier,
  compact: Boolean = false,
  firstChipFocusRequester: FocusRequester? = null,
  up: FocusRequester? = null,
  down: FocusRequester? = null,
  /** Takes a press of down away from [down], for a body that has to be scrolled to first. */
  onMoveDown: (() -> Unit)? = null,
) {
  if (queries.isEmpty()) return
  Column(modifier = modifier.fillMaxWidth()) {
    Row(verticalAlignment = Alignment.CenterVertically, horizontalArrangement = Arrangement.spacedBy(6.dp)) {
      Icon(
        Icons.Filled.History,
        contentDescription = null,
        tint = MutedBlue,
        modifier = Modifier.size(13.dp),
      )
      Text(
        "RECENT SEARCHES",
        color = MutedBlue,
        fontWeight = FontWeight.Black,
        letterSpacing = 1.sp,
        fontSize = 9.sp,
      )
    }
    Spacer(Modifier.height(6.dp))
    Row(
      modifier =
        Modifier.fillMaxWidth().focusGroup().horizontalScroll(rememberScrollState())
          .onPreviewKeyEvent { event ->
            if (
              event.type == KeyEventType.KeyDown &&
                event.key == Key.DirectionDown &&
                onMoveDown != null
            ) {
              onMoveDown()
              true
            } else {
              false
            }
          },
      horizontalArrangement = Arrangement.spacedBy(if (compact) 6.dp else 8.dp),
      verticalAlignment = Alignment.CenterVertically,
    ) {
      queries.forEachIndexed { index, query ->
        SearchHistoryChip(
          label = query,
          compact = compact,
          onClick = { onSelect(query) },
          modifier =
            Modifier.focusProperties {
                if (up != null) this.up = up
                if (down != null) this.down = down
              }
              .remoteFocusNavigation(up = up, down = down)
              .let { chip ->
                if (index == 0 && firstChipFocusRequester != null) {
                  chip.focusRequester(firstChipFocusRequester)
                } else {
                  chip
                }
              },
        )
      }
      ClearSearchHistoryChip(
        compact = compact,
        onClick = onClear,
        modifier =
          Modifier.focusProperties {
              if (up != null) this.up = up
              if (down != null) this.down = down
            }
            .remoteFocusNavigation(up = up, down = down),
      )
    }
  }
}

@Composable
private fun SearchHistoryChip(
  label: String,
  compact: Boolean,
  onClick: () -> Unit,
  modifier: Modifier = Modifier,
) {
  var focused by remember { mutableStateOf(false) }
  val scale by animateFloatAsState(if (focused) 1.06f else 1f, label = "recent search scale")
  val background by
    animateColorAsState(
      if (focused) GizMint else NightSurface,
      label = "recent search background",
    )
  val outline by
    animateColorAsState(
      if (focused) SoftWhite else SoftWhite.copy(alpha = .10f),
      label = "recent search outline",
    )
  val radius = if (compact) 13.dp else 15.dp
  Row(
    modifier =
      modifier.height(if (compact) 26.dp else 30.dp)
        .graphicsLayer { scaleX = scale; scaleY = scale }
        .clip(RoundedCornerShape(radius))
        .background(background)
        .border(if (focused) 2.dp else 1.dp, outline, RoundedCornerShape(radius))
        .onFocusChanged { focused = it.isFocused }
        .clickable(onClick = onClick)
        .semantics { role = Role.Button; contentDescription = "Search again for $label" }
        .padding(horizontal = if (compact) 10.dp else 13.dp),
    verticalAlignment = Alignment.CenterVertically,
  ) {
    Text(
      label,
      color = if (focused) DeepSpace else SoftWhite.copy(alpha = .9f),
      fontWeight = FontWeight.Bold,
      fontSize = if (compact) 10.sp else 11.sp,
      maxLines = 1,
      overflow = TextOverflow.Ellipsis,
      modifier = Modifier.widthIn(max = 190.dp),
    )
  }
}

@Composable
private fun ClearSearchHistoryChip(compact: Boolean, onClick: () -> Unit, modifier: Modifier = Modifier) {
  var focused by remember { mutableStateOf(false) }
  val scale by animateFloatAsState(if (focused) 1.06f else 1f, label = "clear history scale")
  val background by
    animateColorAsState(
      if (focused) SoftWhite else Color.Transparent,
      label = "clear history background",
    )
  val content by
    animateColorAsState(if (focused) DeepSpace else MutedBlue, label = "clear history content")
  val radius = if (compact) 13.dp else 15.dp
  Row(
    modifier =
      modifier.height(if (compact) 26.dp else 30.dp)
        .graphicsLayer { scaleX = scale; scaleY = scale }
        .clip(RoundedCornerShape(radius))
        .background(background)
        .border(1.dp, if (focused) SoftWhite else MutedBlue.copy(alpha = .4f), RoundedCornerShape(radius))
        .onFocusChanged { focused = it.isFocused }
        .clickable(onClick = onClick)
        .semantics { role = Role.Button; contentDescription = "Clear search history" }
        .padding(horizontal = if (compact) 9.dp else 12.dp),
    verticalAlignment = Alignment.CenterVertically,
    horizontalArrangement = Arrangement.spacedBy(5.dp),
  ) {
    Icon(
      Icons.Filled.DeleteSweep,
      contentDescription = null,
      tint = content,
      modifier = Modifier.size(if (compact) 13.dp else 15.dp),
    )
    Text("Clear", color = content, fontWeight = FontWeight.Bold, fontSize = if (compact) 10.sp else 11.sp, maxLines = 1)
  }
}
