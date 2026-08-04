package com.example.auroratv.link

import android.app.Notification
import android.app.PendingIntent
import android.app.Service
import android.content.Context
import android.content.Intent
import android.content.pm.ServiceInfo
import android.graphics.Bitmap
import android.os.Build
import android.os.IBinder
import android.support.v4.media.MediaMetadataCompat
import android.support.v4.media.session.MediaSessionCompat
import android.support.v4.media.session.PlaybackStateCompat
import android.util.Log
import androidx.core.app.NotificationChannelCompat
import androidx.core.app.NotificationCompat
import androidx.core.app.NotificationManagerCompat
import androidx.media.VolumeProviderCompat
import androidx.media.app.NotificationCompat.MediaStyle
import com.example.auroratv.MainActivity
import com.example.auroratv.R
import com.example.auroratv.home.loadWidgetPoster
import kotlinx.coroutines.CoroutineScope
import kotlinx.coroutines.Dispatchers
import kotlinx.coroutines.SupervisorJob
import kotlinx.coroutines.cancel
import kotlinx.coroutines.flow.collectLatest
import kotlinx.coroutines.launch

private const val LOG_TAG = "GizTvLink"
private const val CHANNEL_ID = "giztv_television"
private const val NOTIFICATION_ID = 4023

/** The dial on a watch has a hundred steps whatever the television's own scale happens to be. */
private const val VOLUME_STEPS = 100

/**
 * A media session standing in for the television.
 *
 * The point is not this phone's own notification. It is that a watch, a lock screen, a car and a
 * pair of headphones all already know how to drive a media session, and none of them will ever
 * learn to speak GIZTV. Publishing what the television is playing as an ordinary session hands the
 * whole lot pause, seek and a volume dial without any of them being taught anything.
 *
 * A service rather than a screen, because the useful moment is exactly when the phone is in a
 * pocket.
 */
internal class RemoteMediaService : Service() {
  private val scope = CoroutineScope(SupervisorJob() + Dispatchers.Main.immediate)
  private lateinit var session: MediaSessionCompat
  private val volume =
    object : VolumeProviderCompat(VOLUME_CONTROL_ABSOLUTE, VOLUME_STEPS, 0) {
      private val client: LinkClient
        get() = PhoneLink.client(this@RemoteMediaService)

      override fun onSetVolumeTo(volume: Int) {
        client.send(LinkCommand.SetVolume(volume))
        currentVolume = volume
      }

      override fun onAdjustVolume(direction: Int) {
        client.send(LinkCommand.Volume(direction))
        currentVolume = (currentVolume + direction * 5).coerceIn(0, VOLUME_STEPS)
      }
    }
  private var artworkUrl: String? = null
  private var artwork: Bitmap? = null
  private var started = false

  override fun onBind(intent: Intent?): IBinder? = null

  override fun onCreate() {
    super.onCreate()
    val client = PhoneLink.client(this)
    session =
      MediaSessionCompat(this, "GizTvRemote").apply {
        setCallback(
          object : MediaSessionCompat.Callback() {
            override fun onPlay() = client.send(LinkCommand.Resume)

            override fun onPause() = client.send(LinkCommand.Pause)

            override fun onStop() = client.send(LinkCommand.Stop)

            override fun onSeekTo(pos: Long) = client.send(LinkCommand.Seek(pos))

            override fun onFastForward() = client.send(LinkCommand.SeekBy(10_000L))

            override fun onRewind() = client.send(LinkCommand.SeekBy(-10_000L))
          }
        )
        // Remote rather than local, so a watch's crown and the phone's own volume keys move the
        // television's volume instead of this phone's ringer.
        setPlaybackToRemote(volume)
        isActive = true
      }

    scope.launch {
      client.status.collectLatest { status ->
        when (status) {
          is LinkStatus.Connected -> publish(status.state)
          // Nothing to stand in for once the television is gone.
          else -> stopSelf()
        }
      }
    }
  }

  private suspend fun publish(state: LinkEvent.State) {
    val title = state.title
    if (title == null) {
      stopSelf()
      return
    }

    if (state.posterUrl != null && state.posterUrl != artworkUrl) {
      artworkUrl = state.posterUrl
      artwork = loadWidgetPoster(state.posterUrl)
    }

    session.setMetadata(
      MediaMetadataCompat.Builder()
        .putString(MediaMetadataCompat.METADATA_KEY_TITLE, title)
        .putString(MediaMetadataCompat.METADATA_KEY_ARTIST, state.subtitle.orEmpty())
        .putLong(MediaMetadataCompat.METADATA_KEY_DURATION, state.durationMs)
        .putBitmap(MediaMetadataCompat.METADATA_KEY_ALBUM_ART, artwork)
        .build()
    )
    session.setPlaybackState(
      PlaybackStateCompat.Builder()
        .setActions(
          PlaybackStateCompat.ACTION_PLAY or
            PlaybackStateCompat.ACTION_PAUSE or
            PlaybackStateCompat.ACTION_PLAY_PAUSE or
            PlaybackStateCompat.ACTION_STOP or
            PlaybackStateCompat.ACTION_SEEK_TO or
            PlaybackStateCompat.ACTION_FAST_FORWARD or
            PlaybackStateCompat.ACTION_REWIND
        )
        .setState(
          if (state.playing) PlaybackStateCompat.STATE_PLAYING else PlaybackStateCompat.STATE_PAUSED,
          state.positionMs,
          1f,
        )
        .build()
    )
    // The dial follows the television rather than the last thing this phone asked for, so a change
    // made with the television's own remote turns up on the watch too.
    volume.currentVolume = state.volume.coerceIn(0, VOLUME_STEPS)

    showNotification(state, title)
  }

  private fun showNotification(state: LinkEvent.State, title: String) {
    NotificationManagerCompat.from(this)
      .createNotificationChannel(
        NotificationChannelCompat.Builder(CHANNEL_ID, NotificationManagerCompat.IMPORTANCE_LOW)
          .setName(getString(R.string.television_channel_name))
          .setDescription(getString(R.string.television_channel_description))
          .build()
      )

    val notification: Notification =
      NotificationCompat.Builder(this, CHANNEL_ID)
        .setSmallIcon(R.drawable.ic_media_notification)
        .setContentTitle(title)
        .setContentText(state.subtitle ?: getString(R.string.television_playing))
        .setLargeIcon(artwork)
        .setContentIntent(
          PendingIntent.getActivity(
            this,
            2,
            Intent(this, MainActivity::class.java),
            PendingIntent.FLAG_UPDATE_CURRENT or PendingIntent.FLAG_IMMUTABLE,
          )
        )
        .setOngoing(state.playing)
        .setStyle(MediaStyle().setMediaSession(session.sessionToken))
        .build()

    if (!started) {
      started = true
      runCatching {
        if (Build.VERSION.SDK_INT >= Build.VERSION_CODES.Q) {
          startForeground(NOTIFICATION_ID, notification, ServiceInfo.FOREGROUND_SERVICE_TYPE_MEDIA_PLAYBACK)
        } else {
          startForeground(NOTIFICATION_ID, notification)
        }
      }
        .onFailure {
          Log.w(LOG_TAG, "The television's media session could not go to the foreground", it)
          started = false
        }
    } else {
      runCatching { NotificationManagerCompat.from(this).notify(NOTIFICATION_ID, notification) }
    }
  }

  override fun onStartCommand(intent: Intent?, flags: Int, startId: Int): Int = START_NOT_STICKY

  override fun onDestroy() {
    super.onDestroy()
    session.isActive = false
    session.release()
    scope.cancel()
  }

  internal companion object {
    /**
     * Brought up once the television is playing something, and left to stop itself once it is not.
     *
     * Only a phone runs this: a television announcing itself to its own lock screen would be
     * announcing to nobody.
     */
    fun sync(context: Context, playing: Boolean) {
      val app = context.applicationContext
      runCatching {
        if (playing) {
          val intent = Intent(app, RemoteMediaService::class.java)
          if (Build.VERSION.SDK_INT >= Build.VERSION_CODES.O) app.startForegroundService(intent)
          else app.startService(intent)
        } else {
          app.stopService(Intent(app, RemoteMediaService::class.java))
        }
      }
    }
  }
}
