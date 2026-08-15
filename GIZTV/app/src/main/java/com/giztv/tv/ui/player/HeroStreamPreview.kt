@file:Suppress("UnsafeOptInUsageError")

package com.giztv.tv.ui.player

import androidx.compose.animation.core.animateFloatAsState
import androidx.compose.animation.core.tween
import androidx.compose.foundation.layout.Box
import androidx.compose.foundation.layout.fillMaxSize
import androidx.compose.runtime.Composable
import androidx.compose.runtime.DisposableEffect
import androidx.compose.runtime.LaunchedEffect
import androidx.compose.runtime.getValue
import androidx.compose.runtime.mutableStateOf
import androidx.compose.runtime.remember
import androidx.compose.runtime.setValue
import androidx.compose.ui.Modifier
import androidx.compose.ui.draw.alpha
import androidx.compose.ui.platform.LocalContext
import androidx.compose.ui.viewinterop.AndroidView
import android.view.LayoutInflater
import android.view.View
import androidx.lifecycle.Lifecycle
import androidx.lifecycle.LifecycleEventObserver
import androidx.lifecycle.compose.LocalLifecycleOwner
import androidx.media3.common.C
import androidx.media3.common.PlaybackException
import androidx.media3.common.Player
import androidx.media3.ui.PlayerView
import com.giztv.tv.R
import kotlinx.coroutines.delay

/**
 * How long the page is looked at before its film is asked to start.
 *
 * Someone stepping through recommendations passes several of these pages in a second, and none of
 * them should have opened a video by the time they are gone.
 */
internal const val HERO_PREVIEW_START_DELAY_MS = 900L

/**
 * How long a preview runs before it stops on its own.
 *
 * A page can be left open indefinitely, and a preview is a taste of a film rather than a way to
 * watch one. The artwork comes back afterwards; pressing play is what starts the film properly.
 */
internal const val HERO_PREVIEW_MAX_MS = 120_000L

/**
 * The most a preview will ask the connection for.
 *
 * It is drawn in a strip a few hundred density pixels tall, behind gradients and a title, and it is
 * spent on someone who is still deciding. A full-quality rendition here would cost a film's worth of
 * data for a page nobody chose to watch.
 */
internal const val HERO_PREVIEW_MAX_WIDTH = 854

internal const val HERO_PREVIEW_MAX_HEIGHT = 480

internal const val HERO_PREVIEW_MAX_BITRATE = 1_500_000

/**
 * The film itself, playing silently behind the artwork of the page describing it.
 *
 * The stream is the one the prefetcher has already found for this title, so nothing is fetched here
 * that pressing Play would not have fetched a moment later — and pressing Play afterwards opens on
 * that same address without a wait.
 *
 * It is never heard. The player is built without a claim on the device's sound ([createHlsPlayer]'s
 * `handleAudioFocus`) and at zero volume, so music playing elsewhere on the phone carries on
 * untouched. It also stands down for anything that does have the sound — the film in the player,
 * the mini player, a page being watched in the browser — through [ActivePlayback], and never asks
 * for it back: whatever the viewer is actually listening to outranks a picture behind a poster.
 */
@Composable
internal fun HeroStreamPreview(
  request: HlsStreamRequest?,
  modifier: Modifier = Modifier,
  startDelayMs: Long = HERO_PREVIEW_START_DELAY_MS,
  maximumDurationMs: Long = HERO_PREVIEW_MAX_MS,
) {
  if (request == null) return
  val context = LocalContext.current
  val lifecycleOwner = LocalLifecycleOwner.current

  // Set when something with a better claim on the screen or the sound starts, and never unset: a
  // preview that comes back by itself would be arguing with whatever silenced it.
  var standDown by remember(request.url) { mutableStateOf(false) }
  var rendered by remember(request.url) { mutableStateOf(false) }

  val player =
    remember(request.url) {
      createHlsPlayer(context = context, request = request, handleAudioFocus = false).apply {
        volume = 0f
        playWhenReady = false
        trackSelectionParameters =
          trackSelectionParameters
            .buildUpon()
            // A picture this size does not need the rendition a television would ask for, and the
            // viewer has not agreed to spend a film's worth of data on a page they are reading.
            .setMaxVideoSize(HERO_PREVIEW_MAX_WIDTH, HERO_PREVIEW_MAX_HEIGHT)
            .setMaxVideoBitrate(HERO_PREVIEW_MAX_BITRATE)
            // Not merely turned down: nothing here decodes a sound or fetches a subtitle at all.
            .setTrackTypeDisabled(C.TRACK_TYPE_AUDIO, true)
            .setTrackTypeDisabled(C.TRACK_TYPE_TEXT, true)
            .build()
      }
    }

  DisposableEffect(player, lifecycleOwner) {
    val audioSource = ActivePlayback.Source { standDown = true }
    ActivePlayback.register(audioSource)

    val listener =
      object : Player.Listener {
        override fun onRenderedFirstFrame() {
          rendered = true
        }

        // A preview is not worth a message, a retry or a server switch. The artwork it was going to
        // cover is still there, which is exactly what the page looked like before this existed.
        override fun onPlayerError(error: PlaybackException) {
          android.util.Log.i("GizHls", "Preview gave up for ${request.url}", error)
          standDown = true
        }

        override fun onPlaybackStateChanged(playbackState: Int) {
          if (playbackState == Player.STATE_ENDED) standDown = true
        }
      }
    player.addListener(listener)

    val observer =
      LifecycleEventObserver { _, event ->
        when (event) {
          Lifecycle.Event.ON_STOP -> player.pause()
          Lifecycle.Event.ON_START -> if (!standDown) player.play()
          else -> Unit
        }
      }
    lifecycleOwner.lifecycle.addObserver(observer)

    onDispose {
      ActivePlayback.unregister(audioSource)
      lifecycleOwner.lifecycle.removeObserver(observer)
      player.removeListener(listener)
      player.release()
    }
  }

  LaunchedEffect(player, standDown) {
    if (standDown) {
      player.pause()
      rendered = false
      return@LaunchedEffect
    }
    delay(startDelayMs)
    player.prepare()
    player.play()
    delay(maximumDurationMs)
    standDown = true
  }

  // Faded in once there is a frame to show, so the artwork is never replaced by a black rectangle
  // while the stream opens, and faded out again the moment this stands down.
  val pictureAlpha by
    animateFloatAsState(
      targetValue = if (rendered && !standDown) 1f else 0f,
      animationSpec = tween(durationMillis = 450),
      label = "HeroPreviewFade",
    )

  Box(modifier = modifier.alpha(pictureAlpha)) {
    AndroidView(
      // Inflated for its surface type; everything the page cares about is set here. See the layout.
      factory = { viewContext ->
        (LayoutInflater.from(viewContext).inflate(R.layout.hero_stream_preview, null) as PlayerView)
          .apply {
            this.player = player
            // Nothing here is watched closely enough to read, and a caption over the page's own
            // title is a mess. The full player is where subtitles belong.
            subtitleView?.visibility = View.GONE
            isFocusable = false
            isFocusableInTouchMode = false
            // The page underneath owns every press, including the one that opens the film.
            isClickable = false
          }
      },
      update = { view -> view.player = player },
      modifier = Modifier.fillMaxSize(),
    )
  }
}
