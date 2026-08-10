package com.example.auroratv.ui.catalog

import androidx.compose.animation.animateColorAsState
import androidx.compose.animation.core.LinearEasing
import androidx.compose.animation.core.RepeatMode
import androidx.compose.animation.core.animateFloat
import androidx.compose.animation.core.animateFloatAsState
import androidx.compose.animation.core.infiniteRepeatable
import androidx.compose.animation.core.rememberInfiniteTransition
import androidx.compose.animation.core.tween
import androidx.compose.foundation.background
import androidx.compose.foundation.border
import androidx.compose.foundation.clickable
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
import androidx.compose.foundation.layout.width
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
import androidx.compose.ui.focus.onFocusChanged
import androidx.compose.ui.graphics.Color
import androidx.compose.ui.graphics.SolidColor
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
import androidx.compose.ui.text.TextStyle
import androidx.compose.ui.text.font.FontWeight
import androidx.compose.ui.text.input.ImeAction
import androidx.compose.ui.text.style.TextAlign
import androidx.compose.ui.text.style.TextOverflow
import androidx.compose.ui.unit.dp
import androidx.compose.ui.unit.sp
import androidx.tv.material3.Text
import com.example.auroratv.theme.AuroraBlue
import com.example.auroratv.theme.AuroraMint
import com.example.auroratv.theme.DeepSpace
import com.example.auroratv.theme.MutedBlue
import com.example.auroratv.theme.NightSurface
import com.example.auroratv.theme.SoftWhite
import kotlinx.coroutines.delay
import java.util.Locale

@Composable
internal fun StatusPanel(
  message: String,
  modifier: Modifier,
  actionLabel: String? = null,
  loading: Boolean = false,
  onAction: () -> Unit = {},
) {
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

@Composable
internal fun RailSkeleton(narrow: Boolean, modifier: Modifier = Modifier) {
  val shimmer = rememberInfiniteTransition(label = "rail shimmer")
  val alpha by
    shimmer.animateFloat(
      initialValue = .04f,
      targetValue = .12f,
      animationSpec =
        infiniteRepeatable(tween(durationMillis = 1_000, easing = LinearEasing), RepeatMode.Reverse),
      label = "rail shimmer alpha",
    )
  val edge = if (narrow) 18.dp else 42.dp
  val cardWidth = if (narrow) 132.dp else 158.dp
  Column(modifier) {
    Box(
      modifier =
        Modifier.padding(horizontal = edge).height(19.dp).width(if (narrow) 150.dp else 210.dp)
          .clip(RoundedCornerShape(6.dp)).background(SoftWhite.copy(alpha = alpha))
    )
    Spacer(Modifier.height(8.dp))
    Row(
      modifier = Modifier.fillMaxWidth().padding(horizontal = edge),
      horizontalArrangement = Arrangement.spacedBy(if (narrow) 12.dp else 18.dp),
    ) {
      repeat(if (narrow) 4 else 8) {
        Column(
          modifier =
            Modifier.width(cardWidth).clip(RoundedCornerShape(16.dp)).background(NightSurface)
        ) {
          Box(
            modifier =
              Modifier.fillMaxWidth().aspectRatio(2f / 3f).background(SoftWhite.copy(alpha = alpha))
          )
          Column(Modifier.padding(horizontal = 11.dp, vertical = 10.dp)) {
            Box(Modifier.fillMaxWidth().height(34.dp).clip(RoundedCornerShape(5.dp)).background(SoftWhite.copy(alpha = alpha)))
            Spacer(Modifier.height(5.dp))
            Box(Modifier.width(46.dp).height(13.dp).clip(RoundedCornerShape(5.dp)).background(SoftWhite.copy(alpha = alpha)))
          }
        }
      }
    }
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
  val outline by
    animateColorAsState(
      if (focused) AuroraMint else SoftWhite.copy(alpha = .10f),
      label = "card outline",
    )
  Column(
    modifier =
      modifier.graphicsLayer { scaleX = scale; scaleY = scale }
        .onFocusChanged { focused = it.isFocused }.clickable(onClick = onClick)
        .semantics { role = Role.Button; contentDescription = actionLabel }
  ) {
    Box(
      modifier =
        Modifier.fillMaxWidth().aspectRatio(2f / 3f).clip(RoundedCornerShape(14.dp))
          .border(if (focused) 3.dp else 1.dp, outline, RoundedCornerShape(14.dp))
    ) {
      TmdbArtwork(
        url = posterUrl,
        contentDescription = "$title poster",
        modifier = Modifier.fillMaxSize(),
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
    Column(Modifier.padding(horizontal = 3.dp, vertical = 8.dp)) {
      Text(
        title,
        color = SoftWhite,
        fontWeight = FontWeight.Bold,
        fontSize = 13.sp,
        maxLines = 2,
        minLines = 2,
        overflow = TextOverflow.Ellipsis,
        lineHeight = 16.sp,
      )
      Spacer(Modifier.height(3.dp))
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

internal fun Modifier.remoteFocusNavigation(
  up: FocusRequester? = null,
  down: FocusRequester? = null,
  left: FocusRequester? = null,
  right: FocusRequester? = null,
): Modifier =
  onPreviewKeyEvent { event ->
    if (event.type != KeyEventType.KeyDown) return@onPreviewKeyEvent false
    val destination =
      when (event.key) {
        Key.DirectionUp -> up
        Key.DirectionDown -> down
        Key.DirectionLeft -> left
        Key.DirectionRight -> right
        else -> null
      } ?: return@onPreviewKeyEvent false
    runCatching { destination.requestFocus() }.getOrDefault(false)
  }
