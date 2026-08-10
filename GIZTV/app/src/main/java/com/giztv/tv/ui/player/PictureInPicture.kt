package com.giztv.tv.ui.player

import android.app.Activity
import android.app.PendingIntent
import android.app.PictureInPictureParams
import android.app.RemoteAction
import android.content.BroadcastReceiver
import android.content.Context
import android.content.Intent
import android.content.IntentFilter
import android.content.pm.PackageManager
import android.graphics.drawable.Icon
import android.os.Build
import android.util.Rational
import androidx.activity.ComponentActivity
import androidx.annotation.RequiresApi
import androidx.compose.runtime.Composable
import androidx.compose.runtime.DisposableEffect
import androidx.compose.runtime.LaunchedEffect
import androidx.compose.runtime.getValue
import androidx.compose.runtime.rememberUpdatedState
import androidx.compose.ui.platform.LocalContext
import androidx.core.content.ContextCompat
import androidx.core.util.Consumer

private const val PIP_ACTION = "com.giztv.tv.PICTURE_IN_PICTURE_ACTION"
private const val PIP_ACTION_PLAY = 1
private const val PIP_ACTION_PAUSE = 2

// The system will not open a window flatter or narrower than roughly 2.39:1 either way, so an
// unusually shaped stream is nudged into range instead of having the request rejected outright.
private val PIP_MINIMUM_ASPECT = Rational(100, 239)
private val PIP_MAXIMUM_ASPECT = Rational(239, 100)
private val PIP_DEFAULT_ASPECT = Rational(16, 9)

/**
 * Picture in picture is a phone gesture: leaving the app puts the video in a small floating window.
 *
 * A television has no such thing — its home button leaves the app for good and its whole interface
 * is built for a remote — so a leanback device keeps today's behaviour of simply pausing.
 */
internal fun Context.supportsPictureInPicture(): Boolean =
  Build.VERSION.SDK_INT >= Build.VERSION_CODES.O &&
    packageManager.hasSystemFeature(PackageManager.FEATURE_PICTURE_IN_PICTURE) &&
    !packageManager.hasSystemFeature(PackageManager.FEATURE_LEANBACK)

/** The shape of the floating window: the video's own, as far as the system allows. */
internal fun pictureInPictureAspectRatio(videoWidth: Int, videoHeight: Int): Rational {
  if (videoWidth <= 0 || videoHeight <= 0) return PIP_DEFAULT_ASPECT
  val ratio = Rational(videoWidth, videoHeight)
  return when {
    ratio < PIP_MINIMUM_ASPECT -> PIP_MINIMUM_ASPECT
    ratio > PIP_MAXIMUM_ASPECT -> PIP_MAXIMUM_ASPECT
    else -> ratio
  }
}

/**
 * Whether leaving the app should shrink the video into a floating window.
 *
 * A stream that failed has nothing to show, and one being cast is already playing on the
 * television, where a window on the phone would only duplicate the controls.
 */
internal fun shouldEnterPictureInPicture(
  supported: Boolean,
  isCasting: Boolean,
  hasError: Boolean,
  playbackFinished: Boolean,
): Boolean = supported && !isCasting && !hasError && !playbackFinished

/**
 * Hands the video to a floating window when the viewer leaves for another app, and reports the
 * window opening and closing back so the player can put its controls away.
 *
 * From Android 12 the system makes the move itself as the home gesture is drawn, which animates
 * far more smoothly than entering once the gesture has finished; older releases are told to enter
 * on the way out.
 */
@Composable
internal fun PictureInPictureEffect(
  enabled: Boolean,
  isPlaying: Boolean,
  aspectRatio: Rational,
  onPlayPause: (Boolean) -> Unit,
  onModeChanged: (Boolean) -> Unit,
) {
  if (Build.VERSION.SDK_INT < Build.VERSION_CODES.O) return
  val activity = LocalContext.current.findComponentActivity() ?: return
  val currentOnPlayPause by rememberUpdatedState(onPlayPause)
  val currentOnModeChanged by rememberUpdatedState(onModeChanged)
  val currentEnabled by rememberUpdatedState(enabled)
  // Read when the viewer actually leaves, which is long after the listener was registered.
  val currentAspectRatio by rememberUpdatedState(aspectRatio)
  val currentIsPlaying by rememberUpdatedState(isPlaying)

  // Kept current so the window opens at the video's shape and its button matches what the video is
  // doing, both while it floats and at the moment the system enters on the viewer's behalf.
  LaunchedEffect(activity, enabled, isPlaying, aspectRatio) {
    runCatching {
      activity.setPictureInPictureParams(
        pictureInPictureParams(activity, aspectRatio, isPlaying, autoEnter = enabled)
      )
    }
  }

  DisposableEffect(activity) {
    val onUserLeave =
      Runnable {
        if (currentEnabled && Build.VERSION.SDK_INT < Build.VERSION_CODES.S) {
          runCatching {
            activity.enterPictureInPictureMode(
              pictureInPictureParams(activity, currentAspectRatio, currentIsPlaying, autoEnter = false)
            )
          }
        }
      }
    val onModeChange =
      Consumer<androidx.core.app.PictureInPictureModeChangedInfo> { info ->
        currentOnModeChanged(info.isInPictureInPictureMode)
      }
    val actionReceiver =
      object : BroadcastReceiver() {
        override fun onReceive(context: Context?, intent: Intent?) {
          if (intent?.action != PIP_ACTION) return
          currentOnPlayPause(intent.getIntExtra(PIP_ACTION, PIP_ACTION_PLAY) == PIP_ACTION_PLAY)
        }
      }

    activity.addOnUserLeaveHintListener(onUserLeave)
    activity.addOnPictureInPictureModeChangedListener(onModeChange)
    ContextCompat.registerReceiver(
      activity,
      actionReceiver,
      IntentFilter(PIP_ACTION),
      ContextCompat.RECEIVER_NOT_EXPORTED,
    )

    onDispose {
      activity.removeOnUserLeaveHintListener(onUserLeave)
      activity.removeOnPictureInPictureModeChangedListener(onModeChange)
      runCatching { activity.unregisterReceiver(actionReceiver) }
      // Leaving the player must not leave the rest of the app shrinking into a window.
      runCatching {
        activity.setPictureInPictureParams(
          pictureInPictureParams(activity, currentAspectRatio, currentIsPlaying, autoEnter = false)
        )
      }
    }
  }
}

@RequiresApi(Build.VERSION_CODES.O)
private fun pictureInPictureParams(
  activity: Activity,
  aspectRatio: Rational,
  isPlaying: Boolean,
  autoEnter: Boolean,
): PictureInPictureParams {
  val builder =
    PictureInPictureParams.Builder()
      .setAspectRatio(aspectRatio)
      .setActions(listOf(playPauseAction(activity, isPlaying)))
  if (Build.VERSION.SDK_INT >= Build.VERSION_CODES.S) {
    builder.setAutoEnterEnabled(autoEnter)
    builder.setSeamlessResizeEnabled(true)
  }
  return builder.build()
}

@RequiresApi(Build.VERSION_CODES.O)
private fun playPauseAction(activity: Activity, isPlaying: Boolean): RemoteAction {
  val requestCode = if (isPlaying) PIP_ACTION_PAUSE else PIP_ACTION_PLAY
  val title = if (isPlaying) "Pause" else "Play"
  val icon = if (isPlaying) android.R.drawable.ic_media_pause else android.R.drawable.ic_media_play
  val intent =
    Intent(PIP_ACTION).setPackage(activity.packageName).putExtra(PIP_ACTION, requestCode)
  val pendingIntent =
    PendingIntent.getBroadcast(
      activity,
      requestCode,
      intent,
      PendingIntent.FLAG_UPDATE_CURRENT or PendingIntent.FLAG_IMMUTABLE,
    )
  return RemoteAction(Icon.createWithResource(activity, icon), title, title, pendingIntent)
}

private tailrec fun Context.findComponentActivity(): ComponentActivity? =
  when (this) {
    is ComponentActivity -> this
    is android.content.ContextWrapper -> baseContext.findComponentActivity()
    else -> null
  }
