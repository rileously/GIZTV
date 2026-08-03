package com.example.auroratv.ui.catalog

import androidx.compose.animation.animateColorAsState
import androidx.compose.animation.core.LinearEasing
import androidx.compose.animation.core.RepeatMode
import androidx.compose.animation.core.animateFloat
import androidx.compose.animation.core.animateFloatAsState
import androidx.compose.animation.core.infiniteRepeatable
import androidx.compose.animation.core.rememberInfiniteTransition
import androidx.compose.animation.core.tween
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
import androidx.compose.foundation.layout.aspectRatio
import androidx.compose.foundation.layout.fillMaxSize
import androidx.compose.foundation.layout.fillMaxWidth
import androidx.compose.foundation.layout.height
import androidx.compose.foundation.layout.padding
import androidx.compose.foundation.layout.size
import androidx.compose.foundation.shape.CircleShape
import androidx.compose.foundation.shape.RoundedCornerShape
import androidx.compose.foundation.text.BasicTextField
import androidx.compose.foundation.text.KeyboardActions
import androidx.compose.foundation.text.KeyboardOptions
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
import androidx.compose.ui.graphics.SolidColor
import androidx.compose.ui.graphics.graphicsLayer
import androidx.compose.ui.input.key.Key
import androidx.compose.ui.input.key.KeyEventType
import androidx.compose.ui.input.key.key
import androidx.compose.ui.input.key.onPreviewKeyEvent
import androidx.compose.ui.input.key.type
import androidx.compose.ui.layout.ContentScale
import androidx.compose.ui.res.painterResource
import androidx.compose.ui.semantics.Role
import androidx.compose.ui.semantics.contentDescription
import androidx.compose.ui.semantics.role
import androidx.compose.ui.semantics.selected
import androidx.compose.ui.semantics.semantics
import androidx.compose.ui.text.TextStyle
import androidx.compose.ui.text.font.FontWeight
import androidx.compose.ui.text.input.ImeAction
import androidx.compose.ui.text.style.TextAlign
import androidx.compose.ui.text.style.TextOverflow
import androidx.compose.ui.unit.Dp
import androidx.compose.ui.unit.dp
import androidx.compose.ui.unit.sp
import androidx.tv.material3.Text
import com.example.auroratv.R
import com.example.auroratv.theme.AuroraBlue
import com.example.auroratv.theme.AuroraMint
import com.example.auroratv.theme.DeepSpace
import com.example.auroratv.theme.MutedBlue
import com.example.auroratv.theme.NightSurface
import com.example.auroratv.theme.SoftWhite
import java.util.Locale

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
  val background by animateColorAsState(if (focused) AuroraMint else SoftWhite, label = "$label background")
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
internal fun StatusPanel(
  message: String,
  modifier: Modifier,
  actionLabel: String? = null,
  loading: Boolean = false,
  onAction: () -> Unit = {},
) {
  // While loading the mark breathes, so the panel reads as busy rather than stuck.
  val pulse = rememberInfiniteTransition(label = "status pulse")
  val markAlpha by
    pulse.animateFloat(
      initialValue = if (loading) .35f else .7f,
      targetValue = if (loading) .9f else .7f,
      animationSpec =
        infiniteRepeatable(tween(durationMillis = 900, easing = LinearEasing), RepeatMode.Reverse),
      label = "status mark alpha",
    )
  Box(modifier = modifier.fillMaxWidth(), contentAlignment = Alignment.Center) {
    Column(horizontalAlignment = Alignment.CenterHorizontally) {
      GizTvMark(modifier = Modifier.size(56.dp), cornerRadius = 14.dp, alpha = markAlpha)
      Spacer(Modifier.height(14.dp))
      Text(message, color = MutedBlue, fontSize = 15.sp, textAlign = TextAlign.Center)
      if (actionLabel != null) {
        Spacer(Modifier.height(14.dp))
        CatalogButton(actionLabel, onAction)
      }
    }
  }
}

/** A horizontal strip of selectable chips wired for D-pad entry and exit. */
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
) {
  Row(modifier = Modifier.focusGroup(), horizontalArrangement = Arrangement.spacedBy(8.dp)) {
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
        selected -> AuroraMint
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

@Composable
internal fun CatalogSearchField(
  value: String,
  placeholder: String,
  onValueChanged: (String) -> Unit,
  onSearch: () -> Unit,
  modifier: Modifier,
) {
  var focused by remember { mutableStateOf(false) }
  val border by animateColorAsState(if (focused) AuroraBlue else SoftWhite.copy(alpha = .14f), label = "search border")
  BasicTextField(
    value = value,
    onValueChange = onValueChanged,
    modifier =
      modifier.height(38.dp).clip(RoundedCornerShape(19.dp)).background(NightSurface)
        .border(if (focused) 2.dp else 1.dp, border, RoundedCornerShape(19.dp))
        .onFocusChanged { focused = it.isFocused }
        .semantics { contentDescription = "Catalog search" },
    textStyle = TextStyle(color = SoftWhite, fontSize = 13.sp, fontWeight = FontWeight.Medium),
    singleLine = true,
    cursorBrush = SolidColor(AuroraMint),
    keyboardOptions = KeyboardOptions(imeAction = ImeAction.Search),
    keyboardActions = KeyboardActions(onSearch = { onSearch() }),
    decorationBox = { inner ->
      Box(Modifier.fillMaxSize().padding(horizontal = 16.dp), contentAlignment = Alignment.CenterStart) {
        if (value.isBlank()) Text(placeholder, color = MutedBlue, fontSize = 13.sp)
        inner()
      }
    },
  )
}

@Composable
internal fun PosterCard(
  title: String,
  subtitle: String,
  rating: Double,
  posterUrl: String?,
  actionLabel: String,
  onClick: () -> Unit,
  modifier: Modifier = Modifier,
  watched: Boolean = false,
) {
  var focused by remember { mutableStateOf(false) }
  val scale by animateFloatAsState(if (focused) 1.045f else 1f, label = "card focus scale")
  val outline by animateColorAsState(if (focused) AuroraMint else SoftWhite.copy(alpha = .08f), label = "card outline")
  Column(
    modifier =
      modifier.graphicsLayer { scaleX = scale; scaleY = scale }.clip(RoundedCornerShape(16.dp))
        .background(NightSurface).border(if (focused) 3.dp else 1.dp, outline, RoundedCornerShape(16.dp))
        .onFocusChanged { focused = it.isFocused }.clickable(onClick = onClick)
        .semantics { role = Role.Button; contentDescription = actionLabel }
  ) {
    Box {
      TmdbArtwork(
        url = posterUrl,
        contentDescription = "$title poster",
        modifier = Modifier.fillMaxWidth().aspectRatio(2f / 3f),
        fallbackLabel = "No poster",
      )
      if (watched) {
        Box(
          modifier =
            Modifier.align(Alignment.TopEnd).padding(7.dp).size(24.dp)
              .clip(RoundedCornerShape(12.dp)).background(AuroraMint),
          contentAlignment = Alignment.Center,
        ) {
          Text("✓", color = DeepSpace, fontWeight = FontWeight.Black, fontSize = 13.sp)
        }
      }
    }
    Column(Modifier.padding(horizontal = 11.dp, vertical = 10.dp)) {
      Text(
        title,
        color = SoftWhite,
        fontWeight = FontWeight.Bold,
        fontSize = 14.sp,
        maxLines = 2,
        minLines = 2,
        overflow = TextOverflow.Ellipsis,
        lineHeight = 17.sp,
      )
      Spacer(Modifier.height(5.dp))
      Row(verticalAlignment = Alignment.CenterVertically) {
        Text(subtitle, color = MutedBlue, fontSize = 11.sp)
        Spacer(Modifier.weight(1f))
        if (rating > 0) {
          Text("★ ${String.format(Locale.US, "%.1f", rating)}", color = AuroraMint, fontSize = 11.sp)
        }
      }
    }
  }
}

/**
 * Moves focus explicitly on D-pad presses.
 *
 * Compose's own focus search struggles across the lazy lists and grids used by the catalog, so
 * screens hand it the exact neighbour for each direction.
 */
internal fun Modifier.remoteFocusNavigation(
  up: FocusRequester? = null,
  down: FocusRequester? = null,
  left: FocusRequester? = null,
  right: FocusRequester? = null,
): Modifier =
  onPreviewKeyEvent { event ->
    val destination =
      when (event.key) {
        Key.DirectionUp -> up
        Key.DirectionDown -> down
        Key.DirectionLeft -> left
        Key.DirectionRight -> right
        else -> null
      }
    if (destination == null) {
      false
    } else {
      if (event.type == KeyEventType.KeyDown) destination.requestFocus()
      true
    }
  }
