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
import androidx.compose.foundation.layout.PaddingValues
import androidx.compose.foundation.layout.Box
import androidx.compose.foundation.layout.Column
import androidx.compose.foundation.layout.Row
import androidx.compose.foundation.layout.Spacer
import androidx.compose.foundation.layout.aspectRatio
import androidx.compose.foundation.layout.fillMaxHeight
import androidx.compose.foundation.layout.fillMaxSize
import androidx.compose.foundation.layout.fillMaxWidth
import androidx.compose.foundation.layout.height
import androidx.compose.foundation.layout.padding
import androidx.compose.foundation.layout.size
import androidx.compose.foundation.layout.width
import androidx.compose.foundation.layout.widthIn
import androidx.compose.foundation.lazy.LazyRow
import androidx.compose.foundation.lazy.items
import androidx.compose.foundation.shape.CircleShape
import androidx.compose.foundation.shape.RoundedCornerShape
import androidx.compose.foundation.text.BasicTextField
import androidx.compose.foundation.text.KeyboardActions
import androidx.compose.foundation.text.KeyboardOptions
import androidx.compose.material.icons.Icons
import androidx.compose.material.icons.filled.Search
import androidx.compose.material3.Icon
import androidx.compose.runtime.Composable
import androidx.compose.runtime.LaunchedEffect
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
import androidx.compose.ui.graphics.vector.ImageVector
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
import kotlinx.coroutines.delay
import androidx.compose.ui.unit.dp
import androidx.compose.ui.unit.sp
import androidx.compose.ui.window.Dialog
import androidx.tv.material3.Text
import com.example.auroratv.R
import com.example.auroratv.data.WatchHistoryEntry
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

/**
 * A destination in the top bar: Sports, Short dramas, the web browser.
 *
 * Deliberately quieter than the tabs. Three filled buttons beside a row of filled tabs read as six
 * equal choices with nothing to tell them apart; an outline keeps the ranking legible — where you
 * are first, where else you could go second.
 */
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
      if (focused) AuroraMint else SoftWhite.copy(alpha = .07f),
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
        // Icon-only is narrower than the label ever could be, which is what lets all three fit
        // beside the wordmark on a phone instead of spilling off the edge.
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

/** A round button carrying only an icon, for actions whose shape already says what they do. */
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
      if (focused) AuroraMint else AuroraMint.copy(alpha = .16f),
      label = "$label background",
    )
  val outline by
    animateColorAsState(
      if (focused) SoftWhite else AuroraMint.copy(alpha = .45f),
      label = "$label outline",
    )
  val content by animateColorAsState(if (focused) DeepSpace else AuroraMint, label = "$label content")
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

/**
 * The primary tabs, drawn on one shared track rather than as loose pills.
 *
 * The track is what says these three are a single choice. As separate pills they were three more
 * buttons in a bar that already had plenty, and nothing in the shape said which ones changed the
 * page under them and which ones left it.
 */
@Composable
internal fun SegmentedTabs(
  labels: List<String>,
  selectedIndex: Int,
  onSelect: (Int) -> Unit,
  firstTabFocusRequester: FocusRequester,
  down: FocusRequester?,
  /** Phone chrome: shorter track and tighter labels so the header stays one compact band. */
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
            .let { if (index == 0) it.focusRequester(firstTabFocusRequester) else it },
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
        selected -> AuroraMint
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
  // No scale on focus here: a segment growing out of its own track looks like a bug rather than a
  // highlight, so the ring and the fill carry it instead.
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
      modifier.height(40.dp).clip(RoundedCornerShape(20.dp)).background(DeepSpace.copy(alpha = .55f))
        .border(if (focused) 2.dp else 1.dp, border, RoundedCornerShape(20.dp))
        .onFocusChanged { focused = it.isFocused }
        .semantics { contentDescription = "Catalog search" },
    textStyle = TextStyle(color = SoftWhite, fontSize = 13.sp, fontWeight = FontWeight.Medium),
    singleLine = true,
    cursorBrush = SolidColor(AuroraMint),
    keyboardOptions = KeyboardOptions(imeAction = ImeAction.Search),
    keyboardActions = KeyboardActions(onSearch = { onSearch() }),
    decorationBox = { inner ->
      Row(
        modifier = Modifier.fillMaxSize().padding(start = 14.dp, end = 16.dp),
        verticalAlignment = Alignment.CenterVertically,
      ) {
        Icon(
          Icons.Filled.Search,
          contentDescription = null,
          tint = if (focused) AuroraBlue else MutedBlue,
          modifier = Modifier.size(17.dp),
        )
        Spacer(Modifier.width(10.dp))
        Box(Modifier.weight(1f), contentAlignment = Alignment.CenterStart) {
          if (value.isBlank()) Text(placeholder, color = MutedBlue, fontSize = 13.sp)
          inner()
        }
      }
    },
  )
}

/** How long a card is looked at before it counts as being considered rather than passed over. */
private const val DWELL_MS = 1_200L

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
  /**
   * Said once this card has been sitting under the focus for a moment.
   *
   * Not the instant focus arrives: running a rail from one end to the other passes over a dozen
   * cards nobody meant to look at, and each one would start work that is thrown away a moment
   * later. A pause is what separates browsing from choosing.
   */
  onDwell: (() -> Unit)? = null,
) {
  var focused by remember { mutableStateOf(false) }
  if (onDwell != null) {
    LaunchedEffect(focused) {
      if (!focused) return@LaunchedEffect
      delay(DWELL_MS)
      onDwell()
    }
  }
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
@Composable
internal fun ContinueWatchingSection(
  entries: List<WatchHistoryEntry>,
  onResume: (WatchHistoryEntry) -> Unit,
  onClearHistory: (() -> Unit)? = null,
  firstCardFocusRequester: FocusRequester,
  up: FocusRequester,
  down: FocusRequester,
  hasGrid: Boolean,
  /** The screen's own inset, applied here so the rail itself can reach the edges. */
  edge: Dp = 42.dp,
) {
  Column {
    Text(
      if (entries.isEmpty()) "Watch history" else "Continue watching",
      color = SoftWhite,
      fontWeight = FontWeight.Black,
      fontSize = 16.sp,
      modifier = Modifier.padding(horizontal = edge),
    )
    Spacer(Modifier.height(8.dp))
    LazyRow(
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

/** The destructive action lives at the end of the rail, where it is reachable but not accidental. */
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

/** Confirms the irreversible history reset and keeps Cancel focused first for TV remotes. */
@Composable
internal fun ClearWatchHistoryDialog(onDismiss: () -> Unit, onConfirm: () -> Unit) {
  val cancelFocusRequester = remember { FocusRequester() }
  val clearFocusRequester = remember { FocusRequester() }

  Dialog(onDismissRequest = onDismiss) {
    Column(
      modifier =
        Modifier.fillMaxWidth(.9f).widthIn(max = 520.dp).clip(RoundedCornerShape(22.dp))
          .background(NightSurface).border(1.dp, SoftWhite.copy(alpha = .16f), RoundedCornerShape(22.dp))
          .padding(24.dp),
    ) {
      Text(
        "Clear watch history?",
        color = SoftWhite,
        fontWeight = FontWeight.Black,
        fontSize = 22.sp,
      )
      Spacer(Modifier.height(10.dp))
      Text(
        "This removes watched markers, recommendations, and every saved resume position. This can't be undone.",
        color = MutedBlue,
        fontSize = 14.sp,
        lineHeight = 20.sp,
      )
      Spacer(Modifier.height(22.dp))
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
    animateColorAsState(if (focused) AuroraMint else SoftWhite.copy(alpha = .08f), label = "continue outline")
  // Laid out wide and short: a poster-shaped card here would push the catalog below the fold.
  Row(
    modifier =
      modifier.width(248.dp).graphicsLayer { scaleX = scale; scaleY = scale }
        .clip(RoundedCornerShape(14.dp)).background(NightSurface)
        .border(if (focused) 3.dp else 1.dp, outline, RoundedCornerShape(14.dp))
        .onFocusChanged { focused = it.isFocused }.clickable(onClick = onClick)
        .semantics {
          role = Role.Button
          contentDescription = "Resume ${entry.title}${entry.subtitle?.let { ", $it" } ?: ""}"
        }
        .padding(8.dp),
    verticalAlignment = Alignment.CenterVertically,
  ) {
    // The fixed width keeps the progress bar tied to the poster instead of the whole card.
    Box(modifier = Modifier.width(54.dp), contentAlignment = Alignment.BottomStart) {
      TmdbArtwork(
        url = entry.posterUrl,
        contentDescription = "${entry.title} poster",
        modifier = Modifier.fillMaxWidth().aspectRatio(2f / 3f).clip(RoundedCornerShape(8.dp)),
        compact = true,
      )
      Box(modifier = Modifier.fillMaxWidth().height(4.dp).background(DeepSpace.copy(alpha = .7f))) {
        Box(Modifier.fillMaxWidth(entry.progressFraction).fillMaxHeight().background(AuroraMint))
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
        Text(it, color = AuroraMint, fontWeight = FontWeight.Bold, fontSize = 10.sp, maxLines = 1)
      }
    }
  }
}
