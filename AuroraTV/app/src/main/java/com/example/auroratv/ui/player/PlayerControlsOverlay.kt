package com.example.auroratv.ui.player

import android.app.Activity
import android.content.Context
import android.content.ContextWrapper
import androidx.compose.animation.AnimatedVisibility
import androidx.compose.animation.animateColorAsState
import androidx.compose.animation.fadeIn
import androidx.compose.animation.fadeOut
import androidx.compose.foundation.background
import androidx.compose.foundation.border
import androidx.compose.foundation.clickable
import androidx.compose.foundation.focusable
import androidx.compose.foundation.gestures.detectTapGestures
import androidx.compose.foundation.layout.Arrangement
import androidx.compose.foundation.layout.Box
import androidx.compose.foundation.layout.Column
import androidx.compose.foundation.layout.Row
import androidx.compose.foundation.layout.Spacer
import androidx.compose.foundation.layout.fillMaxHeight
import androidx.compose.foundation.layout.fillMaxSize
import androidx.compose.foundation.layout.fillMaxWidth
import androidx.compose.foundation.layout.height
import androidx.compose.foundation.layout.padding
import androidx.compose.foundation.layout.size
import androidx.compose.foundation.layout.width
import androidx.compose.foundation.layout.widthIn
import androidx.compose.foundation.shape.CircleShape
import androidx.compose.foundation.shape.RoundedCornerShape
import androidx.compose.material.icons.Icons
import androidx.compose.material.icons.filled.Close
import androidx.compose.material.icons.filled.FastForward
import androidx.compose.material.icons.filled.FastRewind
import androidx.compose.material.icons.filled.Info
import androidx.compose.material.icons.filled.Pause
import androidx.compose.material.icons.filled.PlayArrow
import androidx.compose.material.icons.filled.Subtitles
import androidx.compose.material.icons.filled.Tv
import androidx.compose.material3.Icon
import androidx.compose.runtime.Composable
import androidx.compose.runtime.getValue
import androidx.compose.runtime.mutableStateOf
import androidx.compose.runtime.remember
import androidx.compose.runtime.setValue
import androidx.compose.ui.Alignment
import androidx.compose.ui.Modifier
import androidx.compose.ui.draw.clip
import androidx.compose.ui.focus.onFocusChanged
import androidx.compose.ui.graphics.Color
import androidx.compose.ui.graphics.vector.ImageVector
import androidx.compose.ui.input.key.Key
import androidx.compose.ui.input.key.KeyEventType
import androidx.compose.ui.input.key.key
import androidx.compose.ui.input.key.onKeyEvent
import androidx.compose.ui.input.key.type
import androidx.compose.ui.input.pointer.pointerInput
import androidx.core.net.toUri
import androidx.media3.cast.MediaRouteButtonFactory
import androidx.compose.ui.semantics.Role
import androidx.compose.ui.semantics.contentDescription
import androidx.compose.ui.semantics.role
import androidx.compose.ui.semantics.semantics
import androidx.compose.ui.semantics.onClick as semanticsOnClick
import androidx.compose.ui.text.font.FontWeight
import androidx.compose.ui.text.style.TextOverflow
import androidx.compose.ui.unit.Dp
import androidx.compose.ui.unit.dp
import androidx.compose.ui.unit.sp
import androidx.compose.ui.viewinterop.AndroidView
import androidx.mediarouter.app.MediaRouteButton
import androidx.media3.common.C
import androidx.media3.common.Player
import androidx.media3.common.util.UnstableApi
import androidx.tv.material3.Text
import com.example.auroratv.theme.AuroraBlue
import com.example.auroratv.theme.AuroraMint
import com.example.auroratv.theme.DeepSpace
import com.example.auroratv.theme.MutedBlue
import com.example.auroratv.theme.NightSurface
import com.example.auroratv.theme.SoftWhite
import java.util.Locale

internal fun playbackTitle(request: HlsStreamRequest): String {
  request.context?.title?.trim()?.takeIf { it.isNotBlank() }?.let { return it }
  val source = request.sourcePageUrl ?: request.url
  request.title?.trim()
    ?.takeIf { it.isNotBlank() && !it.startsWith("http://") && !it.startsWith("https://") && it != source }
    ?.let { return it }
  return source.toUri().host?.removePrefix("www.")?.uppercase(Locale.ENGLISH) ?: "GIZTV PLAYER"
}

internal fun playbackSubtitle(request: HlsStreamRequest): String? =
  request.context?.subtitle?.trim()?.takeIf { it.isNotBlank() }
    ?: request.subtitle?.trim()?.takeIf { it.isNotBlank() }

internal fun formatPlaybackRating(rating: Double?): String? =
  rating?.takeIf { it > 0.0 }?.let { "★ ${String.format(Locale.US, "%.1f", it)}" }

internal fun seekTargetPosition(currentPositionMs: Long, deltaMs: Long, durationMs: Long): Long {
  val upperBound = durationMs.takeIf { it != C.TIME_UNSET && it > 0L } ?: Long.MAX_VALUE
  return (currentPositionMs.coerceAtLeast(0L) + deltaMs).coerceIn(0L, upperBound)
}

internal fun formatPlayerTime(positionMs: Long): String {
  val totalSeconds = positionMs.coerceAtLeast(0L) / 1_000L
  val hours = totalSeconds / 3_600L
  val minutes = (totalSeconds % 3_600L) / 60L
  val seconds = totalSeconds % 60L
  return if (hours > 0L) "%d:%02d:%02d".format(Locale.ENGLISH, hours, minutes, seconds)
  else "%02d:%02d".format(Locale.ENGLISH, minutes, seconds)
}

internal tailrec fun Context.findActivity(): Activity? =
  when (this) {
    is Activity -> this
    is ContextWrapper -> baseContext.findActivity()
    else -> null
  }

@androidx.annotation.OptIn(UnstableApi::class)
@Composable
internal fun CastRouteButton(modifier: Modifier = Modifier) {
  AndroidView(
    factory = { context ->
      MediaRouteButton(context).apply {
        contentDescription = "Cast video with subtitles"
        minimumWidth = 0
        minimumHeight = 0
        MediaRouteButtonFactory.setUpMediaRouteButton(context, this)
      }
    },
    modifier = modifier,
  )
}

@Composable
internal fun HandoverPill(sent: Boolean, onClick: () -> Unit, modifier: Modifier = Modifier) {
  Row(
    modifier =
      modifier
        .background(if (sent) NightSurface else AuroraMint.copy(alpha = .92f), RoundedCornerShape(20.dp))
        .clickable(enabled = !sent, onClick = onClick)
        .padding(horizontal = 16.dp, vertical = 10.dp),
    verticalAlignment = Alignment.CenterVertically,
  ) {
    Icon(
      Icons.Filled.Tv,
      contentDescription = null,
      tint = if (sent) SoftWhite else DeepSpace,
      modifier = Modifier.size(20.dp),
    )
    Spacer(Modifier.width(8.dp))
    Text(
      if (sent) "Playing on TV" else "Play on TV",
      color = if (sent) SoftWhite else DeepSpace,
      fontWeight = FontWeight.Bold,
      fontSize = 13.sp,
    )
  }
}

@Composable
internal fun ModernPlayerActionPill(
  icon: ImageVector,
  label: String,
  value: String?,
  onClick: () -> Unit,
  onInteraction: () -> Unit,
  showValue: Boolean = true,
  modifier: Modifier = Modifier,
) {
  var focused by remember { mutableStateOf(false) }
  Row(
    modifier =
      modifier.height(44.dp).clip(RoundedCornerShape(22.dp))
        .background(if (focused) AuroraMint else SoftWhite.copy(alpha = .13f))
        .border(2.dp, if (focused) AuroraBlue else SoftWhite.copy(alpha = .16f), RoundedCornerShape(22.dp))
        .onFocusChanged {
          focused = it.isFocused
          if (it.isFocused) onInteraction()
        }
        .onKeyEvent { event ->
          if (
            event.type == KeyEventType.KeyDown && event.nativeKeyEvent.repeatCount == 0 &&
              (event.key == Key.DirectionCenter || event.key == Key.Enter || event.key == Key.NumPadEnter)
          ) {
            onClick()
            true
          } else {
            false
          }
        }
        .focusable()
        .pointerInput(onClick) { detectTapGestures { onClick() } }
        .semantics {
          role = Role.Button
          contentDescription = if (value == null) label else "$label, $value"
          semanticsOnClick {
            onClick()
            true
          }
        }
        .padding(horizontal = 14.dp),
    verticalAlignment = Alignment.CenterVertically,
    horizontalArrangement = Arrangement.spacedBy(7.dp),
  ) {
    Icon(icon, contentDescription = null, tint = if (focused) DeepSpace else SoftWhite, modifier = Modifier.size(19.dp))
    Text(
      label,
      color = if (focused) DeepSpace else SoftWhite,
      fontWeight = FontWeight.Bold,
      fontSize = 12.sp,
      maxLines = 1,
      softWrap = false,
    )
    value?.takeIf { showValue }?.let {
      Text(it, color = if (focused) DeepSpace.copy(alpha = .7f) else MutedBlue, fontSize = 11.sp)
    }
  }
}

@Composable
internal fun ModernPlayerStatusChip(label: String, icon: ImageVector? = null, modifier: Modifier = Modifier) {
  Row(
    modifier =
      modifier.height(34.dp).widthIn(max = 150.dp).clip(RoundedCornerShape(17.dp))
        .background(DeepSpace.copy(alpha = .72f))
        .border(1.dp, SoftWhite.copy(alpha = .14f), RoundedCornerShape(17.dp))
        .padding(horizontal = 11.dp),
    verticalAlignment = Alignment.CenterVertically,
    horizontalArrangement = Arrangement.spacedBy(6.dp),
  ) {
    icon?.let { Icon(it, contentDescription = null, tint = AuroraMint, modifier = Modifier.size(16.dp)) }
    Text(
      label,
      color = SoftWhite,
      fontWeight = FontWeight.Bold,
      fontSize = 11.sp,
      maxLines = 1,
      overflow = TextOverflow.Ellipsis,
    )
  }
}
