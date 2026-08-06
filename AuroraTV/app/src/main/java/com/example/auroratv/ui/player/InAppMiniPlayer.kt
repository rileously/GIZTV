package com.example.auroratv.ui.player

import androidx.compose.foundation.background
import androidx.compose.foundation.border
import androidx.compose.foundation.clickable
import androidx.compose.foundation.focusable
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
import androidx.compose.runtime.mutableStateOf
import androidx.compose.runtime.remember
import androidx.compose.runtime.setValue
import androidx.compose.ui.Alignment
import androidx.compose.ui.Modifier
import androidx.compose.ui.draw.clip
import androidx.compose.ui.focus.onFocusChanged
import androidx.compose.ui.graphics.Color
import androidx.compose.ui.input.key.Key
import androidx.compose.ui.input.key.KeyEventType
import androidx.compose.ui.input.key.key
import androidx.compose.ui.input.key.onKeyEvent
import androidx.compose.ui.input.key.type
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
 * Floating YouTube-style player: picture keeps running while the rest of the app is browsable.
 *
 * Drawn in a corner so taps outside fall through to the catalog beneath. The surface expands back
 * to the full player; the close control ends the session.
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

  Column(
    modifier =
      modifier
        .width(cardWidth)
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
        .clickable(onClick = onExpand)
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
