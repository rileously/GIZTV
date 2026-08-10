package com.example.auroratv.ui.player

import android.Manifest
import android.app.PendingIntent
import android.content.Context
import android.content.Intent
import android.content.pm.PackageManager
import android.graphics.Bitmap
import android.graphics.BitmapFactory
import android.os.Build
import androidx.activity.compose.rememberLauncherForActivityResult
import androidx.activity.result.contract.ActivityResultContracts
import androidx.annotation.OptIn
import androidx.compose.runtime.Composable
import androidx.compose.runtime.DisposableEffect
import androidx.compose.runtime.LaunchedEffect
import androidx.compose.runtime.remember
import androidx.compose.ui.platform.LocalContext
import androidx.compose.ui.res.stringResource
import androidx.core.content.ContextCompat
import androidx.media3.common.Player
import androidx.media3.common.util.UnstableApi
import androidx.media3.session.MediaSession
import androidx.media3.ui.PlayerNotificationManager
import com.example.auroratv.BuildConfig
import com.example.auroratv.MainActivity
import com.example.auroratv.R
import java.net.HttpURLConnection
import java.net.URL
import kotlinx.coroutines.CoroutineScope
import kotlinx.coroutines.Dispatchers
import kotlinx.coroutines.SupervisorJob
import kotlinx.coroutines.cancel
import kotlinx.coroutines.launch
import kotlinx.coroutines.withContext

internal const val MEDIA_NOTIFICATION_ID = 4021
private const val MEDIA_CHANNEL_ID = "giztv_now_playing"
private const val MEDIA_SESSION_ID = "giztv_player"

/** Asked for at the first video of a run, not before every one of them. */
private var notificationPermissionRequested = false

/**
 * Whether the phone has somewhere to put media controls.
 *
 * A television has no notification shade or lock screen to reach them from, and its playback is
 * driven by the remote in front of the viewer, so it is left out.
 */
internal fun Context.supportsMediaNotification(): Boolean =
  !packageManager.hasSystemFeature(PackageManager.FEATURE_LEANBACK)

/**
 * Publishes what is playing to the notification shade and lock screen, so it can be paused and
 * seeked without returning to the app.
 *
 * The session is what carries the controls: it is what the shade, the lock screen, a headset
 * button and a car stereo all talk to. The notification is what makes the system show them.
 */
@OptIn(UnstableApi::class)
@Composable
internal fun MediaControlsEffect(
  player: Player,
  request: HlsStreamRequest,
  enabled: Boolean,
) {
  if (!enabled) return
  val context = LocalContext.current

  // Android 13 onwards will not show the notification without this, and the shade and lock screen
  // controls come with it. Refusing costs nothing but the controls; playback is untouched, and it
  // is asked for once rather than in front of every video.
  val notificationPermission =
    rememberLauncherForActivityResult(ActivityResultContracts.RequestPermission()) {}
  LaunchedEffect(Unit) {
    if (notificationPermissionRequested) return@LaunchedEffect
    if (Build.VERSION.SDK_INT < Build.VERSION_CODES.TIRAMISU) return@LaunchedEffect
    val granted =
      ContextCompat.checkSelfPermission(context, Manifest.permission.POST_NOTIFICATIONS) ==
        PackageManager.PERMISSION_GRANTED
    if (granted) return@LaunchedEffect
    notificationPermissionRequested = true
    runCatching { notificationPermission.launch(Manifest.permission.POST_NOTIFICATIONS) }
  }

  val artworkUrl = request.context?.posterUrl
  val title = request.context?.title ?: request.title ?: stringResource(R.string.app_name)
  val subtitle = request.context?.subtitle.orEmpty()

  DisposableEffect(player, title, subtitle, artworkUrl) {
    val artworkScope = CoroutineScope(SupervisorJob() + Dispatchers.Main.immediate)
    val session = createMediaSession(context, player, MEDIA_SESSION_ID)
    val notifications =
      createMediaNotificationManager(
        context = context,
        title = title,
        subtitle = subtitle.takeIf { it.isNotBlank() },
        artworkUrl = artworkUrl,
        artworkScope = artworkScope,
        onDismissed = player::pause,
      )
    session?.platformToken?.let(notifications::setMediaSessionToken)
    notifications.setPlayer(player)

    onDispose {
      artworkScope.cancel()
      notifications.setPlayer(null)
      session?.release()
    }
  }
}

/**
 * What the shade, the lock screen, a headset button and a car stereo all talk to.
 *
 * A session cannot always be opened — another one of ours may still be closing, and a player being
 * handed to Chromecast may refuse — and the notification is still worth showing without it.
 */
@OptIn(UnstableApi::class)
internal fun createMediaSession(context: Context, player: Player, id: String): MediaSession? =
  runCatching {
      MediaSession.Builder(context.applicationContext, player)
        .setId(id)
        .setSessionActivity(openPlayerIntent(context))
        .build()
    }
    .getOrElse { error ->
      android.util.Log.w("GizTvMedia", "Media session unavailable", error)
      null
    }

/** The notification carrying the controls, and what makes the system show them at all. */
@OptIn(UnstableApi::class)
internal fun createMediaNotificationManager(
  context: Context,
  title: CharSequence,
  subtitle: CharSequence?,
  artworkUrl: String?,
  artworkScope: CoroutineScope,
  onDismissed: () -> Unit,
): PlayerNotificationManager =
  PlayerNotificationManager.Builder(context, MEDIA_NOTIFICATION_ID, MEDIA_CHANNEL_ID)
    .setChannelNameResourceId(R.string.media_channel_name)
    .setChannelDescriptionResourceId(R.string.media_channel_description)
    .setSmallIconResourceId(R.drawable.ic_media_notification)
    .setMediaDescriptionAdapter(
      object : PlayerNotificationManager.MediaDescriptionAdapter {
        override fun getCurrentContentTitle(player: Player): CharSequence = title

        override fun getCurrentContentText(player: Player): CharSequence? = subtitle

        override fun createCurrentContentIntent(player: Player): PendingIntent =
          openPlayerIntent(context)

        override fun getCurrentLargeIcon(
          player: Player,
          callback: PlayerNotificationManager.BitmapCallback,
        ): Bitmap? {
          val url = artworkUrl ?: return null
          MediaArtworkCache.get(url)?.let { return it }
          artworkScope.launch {
            val artwork = loadArtwork(url) ?: return@launch
            MediaArtworkCache.put(url, artwork)
            callback.onBitmap(artwork)
          }
          return null
        }
      }
    )
    .setNotificationListener(
      object : PlayerNotificationManager.NotificationListener {
        // Swiping the notification away is a way of saying stop, and it can only be swiped away
        // once the video is already paused.
        override fun onNotificationCancelled(notificationId: Int, dismissedByUser: Boolean) {
          if (dismissedByUser) onDismissed()
        }
      }
    )
    .build()
    .apply {
      // One video at a time, so a queue's worth of buttons would all be dead.
      setUseNextAction(false)
      setUsePreviousAction(false)
      setUseStopAction(false)
    }

/** Tapping the notification comes back to the video rather than starting the app over. */
private fun openPlayerIntent(context: Context): PendingIntent =
  PendingIntent.getActivity(
    context,
    0,
    Intent(context, MainActivity::class.java).addFlags(
      Intent.FLAG_ACTIVITY_SINGLE_TOP or Intent.FLAG_ACTIVITY_REORDER_TO_FRONT
    ),
    PendingIntent.FLAG_UPDATE_CURRENT or PendingIntent.FLAG_IMMUTABLE,
  )

private suspend fun loadArtwork(url: String): Bitmap? =
  withContext(Dispatchers.IO) {
    runCatching {
        val connection = URL(url).openConnection() as HttpURLConnection
        try {
          connection.connectTimeout = 8_000
          connection.readTimeout = 10_000
          connection.setRequestProperty("User-Agent", "GIZTV/${BuildConfig.VERSION_NAME}")
          connection.inputStream.use { BitmapFactory.decodeStream(it) }
        } finally {
          connection.disconnect()
        }
      }
      .getOrNull()
  }

/** One poster at a time is all a notification ever shows, and it is asked for repeatedly. */
private object MediaArtworkCache {
  private var key: String? = null
  private var artwork: Bitmap? = null

  @Synchronized fun get(url: String): Bitmap? = artwork.takeIf { key == url }

  @Synchronized fun put(url: String, bitmap: Bitmap) {
    key = url
    artwork = bitmap
  }
}
