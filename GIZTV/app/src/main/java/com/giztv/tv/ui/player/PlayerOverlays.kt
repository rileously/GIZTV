package com.giztv.tv.ui.player

import android.app.Activity
import android.content.Context
import android.media.AudioManager
import android.provider.Settings
import android.view.WindowManager
import androidx.compose.foundation.background
import androidx.compose.foundation.border
import androidx.compose.foundation.layout.Arrangement
import androidx.compose.foundation.layout.Box
import androidx.compose.foundation.layout.BoxWithConstraints
import androidx.compose.foundation.layout.Column
import androidx.compose.foundation.layout.Spacer
import androidx.compose.foundation.layout.fillMaxHeight
import androidx.compose.foundation.layout.fillMaxWidth
import androidx.compose.foundation.layout.height
import androidx.compose.foundation.layout.padding
import androidx.compose.foundation.layout.size
import androidx.compose.foundation.layout.width
import androidx.compose.foundation.shape.RoundedCornerShape
import androidx.compose.material.icons.Icons
import androidx.compose.material.icons.automirrored.filled.VolumeOff
import androidx.compose.material.icons.automirrored.filled.VolumeUp
import androidx.compose.material.icons.filled.Brightness6
import androidx.compose.material.icons.filled.FastForward
import androidx.compose.material.icons.filled.FastRewind
import androidx.compose.material3.Icon
import androidx.compose.runtime.Composable
import androidx.compose.ui.Alignment
import androidx.compose.ui.Modifier
import androidx.compose.ui.draw.clip
import androidx.compose.ui.graphics.Color
import androidx.compose.ui.semantics.contentDescription
import androidx.compose.ui.semantics.semantics
import androidx.compose.ui.text.font.FontWeight
import androidx.compose.ui.unit.dp
import androidx.compose.ui.unit.sp
import androidx.media3.common.Player
import androidx.tv.material3.Text
import com.giztv.tv.theme.GizMint
import com.giztv.tv.theme.SoftWhite
import kotlin.math.roundToInt

internal fun currentPlayerBrightness(context: Context, activity: Activity?): Float {
  val windowLevel = activity?.window?.attributes?.screenBrightness ?: -1f
  if (windowLevel >= 0f) return windowLevel.coerceIn(0f, 1f)
  val systemLevel =
    Settings.System.getInt(
      context.contentResolver,
      Settings.System.SCREEN_BRIGHTNESS,
      128,
    )
  return (systemLevel / 255f).coerceIn(MINIMUM_WINDOW_BRIGHTNESS, 1f)
}

internal fun Activity.setPlayerBrightness(level: Float) {
  window.attributes =
    window.attributes.apply {
      screenBrightness =
        if (level < 0f) WindowManager.LayoutParams.BRIGHTNESS_OVERRIDE_NONE
        else level.coerceIn(MINIMUM_WINDOW_BRIGHTNESS, 1f)
    }
}

internal fun currentMediaVolume(audioManager: AudioManager): Float {
  val maximum = audioManager.getStreamMaxVolume(AudioManager.STREAM_MUSIC)
  if (maximum <= 0) return 0f
  return (audioManager.getStreamVolume(AudioManager.STREAM_MUSIC).toFloat() / maximum)
    .coerceIn(0f, 1f)
}

internal fun setMediaVolume(audioManager: AudioManager, player: Player, level: Float) {
  val safeLevel = level.coerceIn(0f, 1f)
  if (audioManager.isVolumeFixed) {
    player.volume = safeLevel
    return
  }
  val maximum = audioManager.getStreamMaxVolume(AudioManager.STREAM_MUSIC)
  val target = mediaVolumeIndex(safeLevel, maximum)
  if (target == audioManager.getStreamVolume(AudioManager.STREAM_MUSIC)) return
  runCatching { audioManager.setStreamVolume(AudioManager.STREAM_MUSIC, target, 0) }
    .onFailure { player.volume = safeLevel }
}

@Composable
internal fun SubtitleDragFeedbackOverlay(
  bottomPaddingFraction: Float,
  positionLabel: String,
  modifier: Modifier = Modifier,
) {
  BoxWithConstraints(modifier) {
    val pad = maxHeight * bottomPaddingFraction.coerceIn(0f, 1f)
    Box(
      Modifier
        .align(Alignment.BottomCenter)
        .padding(bottom = pad)
        .fillMaxWidth(.46f)
        .height(2.dp)
        .clip(RoundedCornerShape(1.dp))
        .background(SoftWhite.copy(alpha = .28f)),
    )
    Text(
      text = positionLabel,
      color = SoftWhite.copy(alpha = .72f),
      fontSize = 12.sp,
      fontWeight = FontWeight.SemiBold,
      modifier =
        Modifier
          .align(Alignment.BottomCenter)
          .padding(bottom = pad + 10.dp)
          .semantics { contentDescription = "Subtitle position $positionLabel" },
    )
  }
}

@Composable
internal fun PlayerSwipeFeedbackOverlay(
  feedback: PlayerSwipeFeedback,
  modifier: Modifier = Modifier,
) {
  val level = feedback.level.coerceIn(0f, 1f)
  val percentage = (level * 100f).roundToInt()
  val label = if (feedback.control == PlayerSwipeControl.BRIGHTNESS) "Brightness" else "Volume"
  val icon =
    when {
      feedback.control == PlayerSwipeControl.BRIGHTNESS -> Icons.Filled.Brightness6
      level == 0f -> Icons.AutoMirrored.Filled.VolumeOff
      else -> Icons.AutoMirrored.Filled.VolumeUp
    }
  val shape = RoundedCornerShape(28.dp)
  Column(
    modifier =
      modifier.width(56.dp).clip(shape)
        .background(Color.Black.copy(alpha = .82f))
        .border(1.dp, SoftWhite.copy(alpha = .2f), shape)
        .semantics { contentDescription = "$label $percentage percent" }
        .padding(horizontal = 10.dp, vertical = 16.dp),
    horizontalAlignment = Alignment.CenterHorizontally,
  ) {
    Text("$percentage%", color = GizMint, fontWeight = FontWeight.Black, fontSize = 12.sp)
    Spacer(Modifier.height(12.dp))
    Box(
      modifier =
        Modifier.width(6.dp).height(124.dp).clip(RoundedCornerShape(3.dp))
          .background(SoftWhite.copy(alpha = .18f)),
      contentAlignment = Alignment.BottomCenter,
    ) {
      Box(
        Modifier.fillMaxWidth().fillMaxHeight(level).clip(RoundedCornerShape(3.dp))
          .background(GizMint)
      )
    }
    Spacer(Modifier.height(12.dp))
    Icon(icon, contentDescription = null, tint = GizMint, modifier = Modifier.size(22.dp))
  }
}

@Composable
internal fun PlayerSeekBurstOverlay(burst: PlayerSeekBurst, modifier: Modifier = Modifier) {
  val backward = burst.side == PlayerSeekSide.BACKWARD
  val label = "${burst.totalMs / 1_000L} seconds"
  val shape =
    if (backward) RoundedCornerShape(topEnd = 360.dp, bottomEnd = 360.dp)
    else RoundedCornerShape(topStart = 360.dp, bottomStart = 360.dp)
  Column(
    modifier =
      modifier.fillMaxHeight().clip(shape).background(Color.Black.copy(alpha = .36f))
        .semantics {
          contentDescription = if (backward) "Rewound $label" else "Forward $label"
        },
    horizontalAlignment = Alignment.CenterHorizontally,
    verticalArrangement = Arrangement.Center,
  ) {
    Icon(
      if (backward) Icons.Filled.FastRewind else Icons.Filled.FastForward,
      contentDescription = null,
      tint = SoftWhite,
      modifier = Modifier.size(40.dp),
    )
    Spacer(Modifier.height(8.dp))
    Text(label, color = SoftWhite, fontWeight = FontWeight.Bold, fontSize = 14.sp)
  }
}
