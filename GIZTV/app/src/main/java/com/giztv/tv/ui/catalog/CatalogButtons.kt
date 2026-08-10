package com.giztv.tv.ui.catalog

import androidx.compose.animation.animateColorAsState
import androidx.compose.animation.core.animateFloatAsState
import androidx.compose.foundation.Image
import androidx.compose.foundation.background
import androidx.compose.foundation.border
import androidx.compose.foundation.clickable
import androidx.compose.foundation.focusGroup
import androidx.compose.foundation.layout.Arrangement
import androidx.compose.foundation.layout.Box
import androidx.compose.foundation.layout.Column
import androidx.compose.foundation.layout.Row
import androidx.compose.foundation.layout.Spacer
import androidx.compose.foundation.layout.fillMaxSize
import androidx.compose.foundation.layout.height
import androidx.compose.foundation.layout.padding
import androidx.compose.foundation.layout.size
import androidx.compose.foundation.shape.CircleShape
import androidx.compose.foundation.shape.RoundedCornerShape
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
import androidx.compose.ui.graphics.vector.ImageVector
import androidx.compose.ui.layout.ContentScale
import androidx.compose.ui.res.painterResource
import androidx.compose.ui.semantics.Role
import androidx.compose.ui.semantics.contentDescription
import androidx.compose.ui.semantics.role
import androidx.compose.ui.semantics.selected
import androidx.compose.ui.semantics.semantics
import androidx.compose.ui.text.font.FontWeight
import androidx.compose.ui.text.style.TextAlign
import androidx.compose.ui.unit.Dp
import androidx.compose.ui.unit.dp
import androidx.compose.ui.unit.sp
import androidx.tv.material3.Text
import com.giztv.tv.R
import com.giztv.tv.theme.GizMint
import com.giztv.tv.theme.DeepSpace
import com.giztv.tv.theme.MutedBlue
import com.giztv.tv.theme.NightSurface
import com.giztv.tv.theme.SoftWhite

/** The GIZTV mark, used for branding and as the placeholder while artwork loads. */
@Composable
internal fun GizTvMark(
  modifier: Modifier = Modifier,
  cornerRadius: Dp = 10.dp,
  alpha: Float = 1f,
) {
  Image(
    painter = painterResource(R.drawable.giztv_mark),
    contentDescription = null,
    contentScale = ContentScale.Crop,
    alpha = alpha,
    modifier = modifier.clip(RoundedCornerShape(cornerRadius)),
  )
}

/** Poster, still, or backdrop artwork with a GIZTV placeholder while it loads. */
@Composable
internal fun TmdbArtwork(
  url: String?,
  contentDescription: String,
  modifier: Modifier = Modifier,
  compact: Boolean = false,
  fallbackLabel: String = "No image",
) {
  val bitmap = rememberTmdbImage(url)
  Box(modifier = modifier.background(Color(0xFF172A45)), contentAlignment = Alignment.Center) {
    val loaded = bitmap
    if (loaded != null) {
      Image(
        bitmap = loaded,
        contentDescription = contentDescription,
        modifier = Modifier.fillMaxSize(),
        contentScale = ContentScale.Crop,
      )
    } else {
      Column(horizontalAlignment = Alignment.CenterHorizontally) {
        GizTvMark(
          modifier = Modifier.size(if (compact) 28.dp else 46.dp),
          cornerRadius = if (compact) 7.dp else 11.dp,
          alpha = .55f,
        )
        if (!compact) {
          Spacer(Modifier.height(8.dp))
          Text(fallbackLabel, color = MutedBlue, fontSize = 11.sp)
        }
      }
    }
  }
}

@Composable
internal fun CatalogButton(label: String, onClick: () -> Unit, modifier: Modifier = Modifier) {
  var focused by remember { mutableStateOf(false) }
  val scale by animateFloatAsState(if (focused) 1.05f else 1f, label = "$label scale")
  val background by animateColorAsState(if (focused) GizMint else SoftWhite, label = "$label background")
  Box(
    modifier =
      modifier.height(38.dp).graphicsLayer { scaleX = scale; scaleY = scale }.clip(RoundedCornerShape(19.dp))
        .background(background).border(2.dp, if (focused) SoftWhite else Color.Transparent, RoundedCornerShape(19.dp))
        .onFocusChanged { focused = it.isFocused }.clickable(onClick = onClick)
        .semantics { role = Role.Button; contentDescription = label }.padding(horizontal = 18.dp),
    contentAlignment = Alignment.Center,
  ) {
    Text(label, color = DeepSpace, fontWeight = FontWeight.Black, fontSize = 12.sp, textAlign = TextAlign.Center)
  }
}

@Composable
internal fun CatalogActionButton(
  label: String,
  icon: ImageVector,
  showLabel: Boolean,
  onClick: () -> Unit,
  modifier: Modifier = Modifier,
) {
  var focused by remember { mutableStateOf(false) }
  val scale by animateFloatAsState(if (focused) 1.06f else 1f, label = "$label scale")
  val background by
    animateColorAsState(
      if (focused) GizMint else SoftWhite.copy(alpha = .07f),
      label = "$label background",
    )
  val outline by
    animateColorAsState(
      if (focused) SoftWhite else SoftWhite.copy(alpha = .16f),
      label = "$label outline",
    )
  val content by
    animateColorAsState(
      if (focused) DeepSpace else SoftWhite.copy(alpha = .88f),
      label = "$label content",
    )
  Row(
    modifier =
      modifier.height(38.dp).graphicsLayer { scaleX = scale; scaleY = scale }
        .clip(RoundedCornerShape(19.dp)).background(background)
        .border(if (focused) 2.dp else 1.dp, outline, RoundedCornerShape(19.dp))
        .onFocusChanged { focused = it.isFocused }.clickable(onClick = onClick)
        .semantics { role = Role.Button; contentDescription = label }
        .padding(horizontal = if (showLabel) 15.dp else 10.dp),
    verticalAlignment = Alignment.CenterVertically,
    horizontalArrangement = Arrangement.spacedBy(7.dp),
  ) {
    Icon(icon, contentDescription = null, tint = content, modifier = Modifier.size(17.dp))
    if (showLabel) {
      Text(label, color = content, fontWeight = FontWeight.Bold, fontSize = 12.sp, maxLines = 1)
    }
  }
}

@Composable
internal fun CatalogIconButton(
  label: String,
  icon: ImageVector,
  onClick: () -> Unit,
  modifier: Modifier = Modifier,
) {
  var focused by remember { mutableStateOf(false) }
  val scale by animateFloatAsState(if (focused) 1.08f else 1f, label = "$label scale")
  val background by
    animateColorAsState(
      if (focused) GizMint else GizMint.copy(alpha = .16f),
      label = "$label background",
    )
  val outline by
    animateColorAsState(
      if (focused) SoftWhite else GizMint.copy(alpha = .45f),
      label = "$label outline",
    )
  val content by animateColorAsState(if (focused) DeepSpace else GizMint, label = "$label content")
  Box(
    modifier =
      modifier.size(40.dp).graphicsLayer { scaleX = scale; scaleY = scale }.clip(CircleShape)
        .background(background).border(if (focused) 2.dp else 1.dp, outline, CircleShape)
        .onFocusChanged { focused = it.isFocused }.clickable(onClick = onClick)
        .semantics { role = Role.Button; contentDescription = label },
    contentAlignment = Alignment.Center,
  ) {
    Icon(icon, contentDescription = null, tint = content, modifier = Modifier.size(19.dp))
  }
}

@Composable
internal fun SegmentedTabs(
  labels: List<String>,
  selectedIndex: Int,
  onSelect: (Int) -> Unit,
  firstTabFocusRequester: FocusRequester,
  down: FocusRequester?,
  compact: Boolean = false,
  modifier: Modifier = Modifier,
) {
  val trackRadius = if (compact) 16.dp else 21.dp
  Row(
    modifier =
      modifier.focusGroup().clip(RoundedCornerShape(trackRadius))
        .background(DeepSpace.copy(alpha = .55f))
        .border(1.dp, SoftWhite.copy(alpha = .08f), RoundedCornerShape(trackRadius))
        .padding(if (compact) 2.dp else 3.dp)
  ) {
    labels.forEachIndexed { index, label ->
      SegmentedTab(
        label = label,
        selected = index == selectedIndex,
        onSelect = { onSelect(index) },
        compact = compact,
        modifier =
          Modifier.focusProperties { if (down != null) this.down = down }
            .let { if (index == selectedIndex) it.focusRequester(firstTabFocusRequester) else it },
      )
    }
  }
}

@Composable
private fun SegmentedTab(
  label: String,
  selected: Boolean,
  onSelect: () -> Unit,
  compact: Boolean,
  modifier: Modifier = Modifier,
) {
  var focused by remember { mutableStateOf(false) }
  val background by
    animateColorAsState(
      when {
        selected -> GizMint
        focused -> SoftWhite.copy(alpha = .16f)
        else -> Color.Transparent
      },
      label = "$label background",
    )
  val content by
    animateColorAsState(
      when {
        selected -> DeepSpace
        focused -> SoftWhite
        else -> MutedBlue
      },
      label = "$label content",
    )
  val segmentRadius = if (compact) 14.dp else 17.dp
  Box(
    modifier =
      modifier.height(if (compact) 28.dp else 34.dp).clip(RoundedCornerShape(segmentRadius))
        .background(background)
        .border(
          if (focused) 2.dp else 0.dp,
          if (focused) SoftWhite else Color.Transparent,
          RoundedCornerShape(segmentRadius),
        )
        .onFocusChanged { focused = it.isFocused }.clickable(onClick = onSelect)
        .semantics {
          role = Role.Tab
          this.selected = selected
          contentDescription = label
        }
        .padding(horizontal = if (compact) 10.dp else 17.dp),
    contentAlignment = Alignment.Center,
  ) {
    Text(
      label,
      color = content,
      fontWeight = if (selected) FontWeight.Black else FontWeight.Bold,
      fontSize = if (compact) 11.sp else 12.sp,
      maxLines = 1,
    )
  }
}

@Composable
internal fun ChipRow(
  labels: List<String>,
  selectedIndex: Int,
  onSelect: (Int) -> Unit,
  firstChipFocusRequester: FocusRequester,
  semanticsRole: Role,
  up: FocusRequester? = null,
  down: FocusRequester? = null,
  compactChips: Boolean = false,
  modifier: Modifier = Modifier,
) {
  Row(modifier = modifier.focusGroup(), horizontalArrangement = Arrangement.spacedBy(8.dp)) {
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
internal fun CatalogChip(
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
        selected -> GizMint
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
