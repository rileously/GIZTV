package com.example.auroratv.ui.player

import androidx.compose.animation.core.Animatable
import androidx.compose.animation.core.Spring
import androidx.compose.animation.core.spring
import androidx.compose.animation.core.tween
import androidx.compose.foundation.background
import androidx.compose.foundation.border
import androidx.compose.foundation.clickable
import androidx.compose.foundation.focusable
import androidx.compose.foundation.gestures.Orientation
import androidx.compose.foundation.gestures.draggable
import androidx.compose.foundation.gestures.rememberDraggableState
import androidx.compose.foundation.layout.Arrangement
import androidx.compose.foundation.layout.Box
import androidx.compose.foundation.layout.Column
import androidx.compose.foundation.layout.Row
import androidx.compose.foundation.layout.fillMaxSize
import androidx.compose.foundation.layout.fillMaxWidth
import androidx.compose.foundation.layout.height
import androidx.compose.foundation.layout.padding
import androidx.compose.foundation.layout.size
import androidx.compose.foundation.layout.width
import androidx.compose.foundation.shape.CircleShape
import androidx.compose.foundation.shape.RoundedCornerShape
import androidx.compose.material.icons.Icons
import androidx.compose.material.icons.filled.Close
import androidx.compose.material.icons.filled.Pause
import androidx.compose.material.icons.filled.PlayArrow
import androidx.compose.material3.Icon
import androidx.compose.runtime.Composable
import androidx.compose.runtime.getValue
import androidx.compose.runtime.mutableFloatStateOf
import androidx.compose.runtime.mutableStateOf
import androidx.compose.runtime.remember
import androidx.compose.runtime.rememberCoroutineScope
import androidx.compose.runtime.rememberUpdatedState
import androidx.compose.runtime.setValue
import androidx.compose.ui.Alignment
import androidx.compose.ui.Modifier
import androidx.compose.ui.draw.clip
import androidx.compose.ui.focus.onFocusChanged
import androidx.compose.ui.graphics.Color
import androidx.compose.ui.graphics.graphicsLayer
import androidx.compose.ui.input.key.Key
import androidx.compose.ui.input.key.KeyEventType
import androidx.compose.ui.input.key.key
import androidx.compose.ui.input.key.onKeyEvent
import androidx.compose.ui.input.key.type
import androidx.compose.ui.platform.LocalDensity
import androidx.compose.ui.semantics.Role
import androidx.compose.ui.semantics.contentDescription
import androidx.compose.ui.semantics.onClick as semanticsOnClick
import androidx.compose.ui.semantics.role
import androidx.compose.ui.semantics.semantics
import androidx.compose.ui.text.font.FontWeight
import androidx.compose.ui.text.style.TextOverflow
import androidx.compose.ui.unit.dp
import androidx.compose.ui.unit.sp
import androidx.compose.ui.viewinterop.AndroidView
import androidx.media3.common.Player
import androidx.media3.ui.PlayerView
import androidx.tv.material3.Text
import com.example.auroratv.theme.AuroraBlue
import com.example.auroratv.theme.AuroraMint
import com.example.auroratv.theme.DeepSpace
import com.example.auroratv.theme.MutedBlue
import com.example.auroratv.theme.NightSurface
import com.example.auroratv.theme.SoftWhite
import kotlinx.coroutines.launch

/** Downward drag distance (dp) that counts as a dismiss without needing a fling. */
internal val MINI_PLAYER_DISMISS_THRESHOLD_DP = 72.dp

/** Downward fling speed (px/s) that dismisses even below the distance threshold. */
internal const val MINI_PLAYER_DISMISS_FLING_VELOCITY_PX_PER_SEC = 1400f

/** How much upward (negative) drag is resisted while rubber-banding. */
internal const val MINI_PLAYER_RUBBER_BAND_FACTOR = 0.35f

/**
 * Whether the in-app player composable should stay alive above browse destinations.
 *
 * Keeping the session composed is what reuses the same ExoPlayer when shrinking to the mini
 * player and expanding again.
 */
internal fun shouldComposeInAppPlayerSession(
  hasStreamRequest: Boolean,
  fullPlayerVisible: Boolean,
  miniPlayerActive: Boolean,
): Boolean = hasStreamRequest && (fullPlayerVisible || miniPlayerActive)

/**
 * Shrink is offered only while there is still a picture worth floating over the catalog.
 *
 * Television / Leanback keeps full-screen exit-on-back; the YouTube-style mini player is phone-only.
 */
internal fun canMinimizeToInAppPlayer(
  minimized: Boolean,
  isCasting: Boolean,
  hasError: Boolean,
  playbackFinished: Boolean,
  isTelevision: Boolean = false,
): Boolean = !isTelevision && !minimized && !isCasting && !hasError && !playbackFinished

/**
 * Maps accumulated finger travel to on-screen offset: free downward motion, rubber-banded upward.
 */
internal fun miniPlayerDragVisualOffset(rawOffsetPx: Float): Float =
  if (rawOffsetPx < 0f) rawOffsetPx * MINI_PLAYER_RUBBER_BAND_FACTOR else rawOffsetPx

/**
 * Dismiss when the user has dragged far enough down, or flung downward quickly.
 *
 * [dragOffsetPx] should be the raw (pre-rubber-band) vertical travel; positive is down.
 */
internal fun shouldDismissMiniPlayer(
  dragOffsetPx: Float,
  velocityYPxPerSec: Float,
  dismissThresholdPx: Float,
  flingVelocityPxPerSec: Float = MINI_PLAYER_DISMISS_FLING_VELOCITY_PX_PER_SEC,
): Boolean =
  dragOffsetPx >= dismissThresholdPx || velocityYPxPerSec >= flingVelocityPxPerSec

/** Alpha / scale progress from 0 (rest) to 1+ while dragging toward dismiss. */
internal fun miniPlayerDismissProgress(
  visualOffsetPx: Float,
  dismissThresholdPx: Float,
): Float =
  if (dismissThresholdPx <= 0f) 0f
  else (visualOffsetPx / dismissThresholdPx).coerceAtLeast(0f)

/**
 * Floating YouTube-style player: picture keeps running while the rest of the app is browsable.
 *
 * Drawn in a corner so taps outside fall through to the catalog beneath. The surface expands back
 * to the full player; the close control ends the session. On phone, a downward drag dismisses the
 * same way as Close.
 */
@Composable
internal fun InAppMiniPlayer(
  player: Player,
  title: String,
  subtitle: String?,
  isPlaying: Boolean,
  isTelevision: Boolean,
  onExpand: () -> Unit,
  onDismiss: () -> Unit,
  onPlayPause: () -> Unit,
  modifier: Modifier = Modifier,
) {
  val cardWidth = if (isTelevision) 360.dp else 220.dp
  val videoHeight = if (isTelevision) 202.dp else 124.dp
  var cardFocused by remember { mutableStateOf(false) }

  val density = LocalDensity.current
  val dismissThresholdPx =
    remember(density) { with(density) { MINI_PLAYER_DISMISS_THRESHOLD_DP.toPx() } }
  val dismissExitPx = remember(density) { with(density) { 320.dp.toPx() } }
  val scope = rememberCoroutineScope()
  val onDismissUpdated by rememberUpdatedState(onDismiss)

  var rawDragOffsetPx by remember { mutableFloatStateOf(0f) }
  var visualOffsetPx by remember { mutableFloatStateOf(0f) }
  var dismissInFlight by remember { mutableStateOf(false) }
  val dismissInFlightUpdated by rememberUpdatedState(dismissInFlight)

  val draggableState =
    rememberDraggableState { delta ->
      if (dismissInFlightUpdated) return@rememberDraggableState
      rawDragOffsetPx += delta
      visualOffsetPx = miniPlayerDragVisualOffset(rawDragOffsetPx)
    }

  val dismissProgress = miniPlayerDismissProgress(visualOffsetPx, dismissThresholdPx)
  val dragAlpha = (1f - dismissProgress * 0.55f).coerceIn(0.2f, 1f)
  val dragScale = (1f - dismissProgress.coerceAtMost(1.25f) * 0.1f).coerceIn(0.85f, 1f)

  Column(
    modifier =
      modifier
        .width(cardWidth)
        .graphicsLayer {
          translationY = visualOffsetPx
          alpha = dragAlpha
          scaleX = dragScale
          scaleY = dragScale
        }
        .then(
          if (!isTelevision) {
            Modifier.draggable(
              state = draggableState,
              orientation = Orientation.Vertical,
              enabled = !dismissInFlight,
              onDragStopped = { velocity ->
                if (dismissInFlightUpdated) return@draggable
                val shouldDismiss =
                  shouldDismissMiniPlayer(
                    dragOffsetPx = rawDragOffsetPx,
                    velocityYPxPerSec = velocity,
                    dismissThresholdPx = dismissThresholdPx,
                  )
                scope.launch {
                  if (shouldDismiss) {
                    dismissInFlight = true
                    val anim = Animatable(visualOffsetPx)
                    anim.animateTo(
                      targetValue = dismissExitPx,
                      animationSpec = tween(durationMillis = 180),
                    ) {
                      visualOffsetPx = value
                      rawDragOffsetPx = value
                    }
                    onDismissUpdated()
                  } else {
                    val anim = Animatable(visualOffsetPx)
                    anim.animateTo(
                      targetValue = 0f,
                      animationSpec =
                        spring(
                          dampingRatio = Spring.DampingRatioMediumBouncy,
                          stiffness = Spring.StiffnessMediumLow,
                        ),
                    ) {
                      visualOffsetPx = value
                    }
                    rawDragOffsetPx = 0f
                    visualOffsetPx = 0f
                  }
                }
              },
            )
          } else {
            Modifier
          },
        )
        .clip(RoundedCornerShape(14.dp))
        .background(NightSurface.copy(alpha = .96f))
        .border(
          width = if (cardFocused) 2.dp else 1.dp,
          color = if (cardFocused) AuroraMint else SoftWhite.copy(alpha = .18f),
          shape = RoundedCornerShape(14.dp),
        )
        .onFocusChanged { cardFocused = it.isFocused }
        .onKeyEvent { event ->
          if (
            event.type == KeyEventType.KeyDown &&
              event.nativeKeyEvent.repeatCount == 0 &&
              (event.key == Key.DirectionCenter ||
                event.key == Key.Enter ||
                event.key == Key.NumPadEnter)
          ) {
            onExpand()
            true
          } else {
            false
          }
        }
        .focusable()
        .clickable(enabled = !dismissInFlight, onClick = onExpand)
        .semantics {
          role = Role.Button
          contentDescription = "Expand player, $title"
          semanticsOnClick {
            onExpand()
            true
          }
        },
  ) {
    Box(
      modifier =
        Modifier.fillMaxWidth().height(videoHeight).background(Color.Black)
    ) {
      AndroidView(
        factory = { context ->
          PlayerView(context).apply {
            this.player = player
            useController = false
            setShowBuffering(PlayerView.SHOW_BUFFERING_NEVER)
            keepScreenOn = true
            isFocusable = false
            isFocusableInTouchMode = false
          }
        },
        update = { view -> view.player = player },
        modifier = Modifier.fillMaxSize(),
      )
      Row(
        modifier =
          Modifier
            .align(Alignment.TopEnd)
            .padding(6.dp),
        horizontalArrangement = Arrangement.spacedBy(6.dp),
      ) {
        MiniPlayerIconButton(
          icon = if (isPlaying) Icons.Filled.Pause else Icons.Filled.PlayArrow,
          label = if (isPlaying) "Pause" else "Play",
          onClick = onPlayPause,
        )
        MiniPlayerIconButton(
          icon = Icons.Filled.Close,
          label = "Close mini player",
          onClick = onDismiss,
        )
      }
    }
    Column(modifier = Modifier.padding(horizontal = 10.dp, vertical = 8.dp)) {
      Text(
        title,
        color = SoftWhite,
        fontWeight = FontWeight.Bold,
        fontSize = if (isTelevision) 15.sp else 13.sp,
        maxLines = 1,
        overflow = TextOverflow.Ellipsis,
      )
      subtitle?.let {
        Text(
          it,
          color = MutedBlue,
          fontWeight = FontWeight.Medium,
          fontSize = if (isTelevision) 12.sp else 11.sp,
          maxLines = 1,
          overflow = TextOverflow.Ellipsis,
          modifier = Modifier.padding(top = 2.dp),
        )
      }
    }
  }
}

@Composable
private fun MiniPlayerIconButton(
  icon: androidx.compose.ui.graphics.vector.ImageVector,
  label: String,
  onClick: () -> Unit,
) {
  var focused by remember { mutableStateOf(false) }
  Box(
    modifier =
      Modifier
        .size(34.dp)
        .clip(CircleShape)
        .background(if (focused) AuroraMint else DeepSpace.copy(alpha = .72f))
        .border(
          width = if (focused) 2.dp else 1.dp,
          color = if (focused) AuroraBlue else SoftWhite.copy(alpha = .24f),
          shape = CircleShape,
        )
        .onFocusChanged { focused = it.isFocused }
        .onKeyEvent { event ->
          if (
            event.type == KeyEventType.KeyDown &&
              event.nativeKeyEvent.repeatCount == 0 &&
              (event.key == Key.DirectionCenter ||
                event.key == Key.Enter ||
                event.key == Key.NumPadEnter)
          ) {
            onClick()
            true
          } else {
            false
          }
        }
        .focusable()
        .clickable(onClick = onClick)
        .semantics {
          role = Role.Button
          contentDescription = label
          semanticsOnClick {
            onClick()
            true
          }
        },
    contentAlignment = Alignment.Center,
  ) {
    Icon(
      imageVector = icon,
      contentDescription = null,
      tint = if (focused) DeepSpace else SoftWhite,
      modifier = Modifier.size(18.dp),
    )
  }
}
