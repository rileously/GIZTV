package com.example.auroratv.link

import android.app.Activity
import android.content.Context
import android.media.AudioManager
import android.view.KeyCharacterMap
import android.view.KeyEvent
import androidx.media3.common.C
import androidx.media3.common.Player
import com.example.auroratv.data.PlaybackContext
import com.example.auroratv.home.resumeIntent
import java.lang.ref.WeakReference
import kotlinx.coroutines.Dispatchers
import kotlinx.coroutines.withContext

/**
 * The end of the wire that does things to the television.
 *
 * The screen registers itself here as it comes and goes, so a command that arrives while nothing is
 * playing finds nothing to do rather than a stale player to do it to.
 */
internal object RemoteControl {
  private var activityRef = WeakReference<Activity>(null)
  private var playerRef = WeakReference<Player>(null)
  @Volatile private var playbackContext: PlaybackContext? = null

  @Synchronized
  fun attachActivity(activity: Activity) {
    activityRef = WeakReference(activity)
  }

  @Synchronized
  fun detachActivity(activity: Activity) {
    if (activityRef.get() === activity) activityRef = WeakReference(null)
  }

  @Synchronized
  fun attachPlayer(player: Player, context: PlaybackContext?) {
    playerRef = WeakReference(player)
    playbackContext = context
  }

  @Synchronized
  fun detachPlayer(player: Player) {
    if (playerRef.get() === player) {
      playerRef = WeakReference(null)
      playbackContext = null
    }
  }

  private fun activity(): Activity? = activityRef.get()

  private fun player(): Player? = playerRef.get()

  /**
   * What the phone's remote should be showing.
   *
   * Read on the main thread because a player may only be asked from the thread it was built on, and
   * handed back as a plain value the socket thread can send whenever it likes.
   */
  suspend fun state(): LinkEvent.State =
    withContext(Dispatchers.Main.immediate) {
      val player = player()
      val context = playbackContext
      LinkEvent.State(
        playing = player?.isPlaying == true,
        title = context?.title,
        subtitle = context?.subtitle,
        posterUrl = context?.posterUrl,
        positionMs = player?.currentPosition?.coerceAtLeast(0L) ?: 0L,
        durationMs = player?.duration?.takeIf { it != C.TIME_UNSET }?.coerceAtLeast(0L) ?: 0L,
      )
    }

  suspend fun execute(context: Context, command: LinkCommand) {
    withContext(Dispatchers.Main.immediate) {
      val player = player()
      when (command) {
        // Answered during the handshake, not here.
        is LinkCommand.Hello,
        is LinkCommand.Pair -> Unit
        is LinkCommand.Play ->
          context.applicationContext.startActivity(
            resumeIntent(context.applicationContext, command.pageUrl)
          )
        LinkCommand.Pause -> player?.pause()
        LinkCommand.Resume -> player?.play()
        LinkCommand.Stop -> {
          player?.pause()
          // Leaving the player is a back press, so the app returns wherever the viewer came from
          // rather than being torn down from underneath itself.
          dispatchKey(KeyEvent.KEYCODE_BACK)
        }
        is LinkCommand.Seek -> player?.seekTo(command.positionMs)
        is LinkCommand.SeekBy ->
          player?.let {
            val target = (it.currentPosition + command.deltaMs).coerceAtLeast(0L)
            val end = it.duration.takeIf { d -> d != C.TIME_UNSET }
            it.seekTo(if (end != null) target.coerceAtMost(end) else target)
          }
        is LinkCommand.Volume -> adjustVolume(context, command.delta)
        is LinkCommand.Key -> dispatchKey(command.key.toKeyCode())
        is LinkCommand.Text -> dispatchText(command.text)
      }
    }
  }

  private fun adjustVolume(context: Context, delta: Int) {
    val audio = context.getSystemService(Context.AUDIO_SERVICE) as? AudioManager ?: return
    val direction =
      when {
        delta > 0 -> AudioManager.ADJUST_RAISE
        delta < 0 -> AudioManager.ADJUST_LOWER
        else -> return
      }
    repeat(kotlin.math.abs(delta)) {
      runCatching {
        audio.adjustStreamVolume(AudioManager.STREAM_MUSIC, direction, AudioManager.FLAG_SHOW_UI)
      }
    }
  }

  /**
   * Presses a key on the app's own window.
   *
   * An app cannot put events into the system's input stream without a permission it will never be
   * granted, but it can hand one to its own window, and its own window is the only one the remote
   * has any business driving.
   */
  private fun dispatchKey(keyCode: Int) {
    val activity = activity() ?: return
    val now = android.os.SystemClock.uptimeMillis()
    runCatching {
      activity.dispatchKeyEvent(KeyEvent(now, now, KeyEvent.ACTION_DOWN, keyCode, 0))
      activity.dispatchKeyEvent(KeyEvent(now, now, KeyEvent.ACTION_UP, keyCode, 0))
    }
  }

  /** Typing goes wherever the focus already is, which on a television is the search box. */
  private fun dispatchText(text: String) {
    val activity = activity() ?: return
    val events =
      runCatching {
          KeyCharacterMap.load(KeyCharacterMap.VIRTUAL_KEYBOARD).getEvents(text.toCharArray())
        }
        .getOrNull() ?: return
    events.forEach { runCatching { activity.dispatchKeyEvent(it) } }
  }
}

private fun LinkKey.toKeyCode(): Int =
  when (this) {
    LinkKey.UP -> KeyEvent.KEYCODE_DPAD_UP
    LinkKey.DOWN -> KeyEvent.KEYCODE_DPAD_DOWN
    LinkKey.LEFT -> KeyEvent.KEYCODE_DPAD_LEFT
    LinkKey.RIGHT -> KeyEvent.KEYCODE_DPAD_RIGHT
    LinkKey.CENTER -> KeyEvent.KEYCODE_DPAD_CENTER
    LinkKey.BACK -> KeyEvent.KEYCODE_BACK
  }
