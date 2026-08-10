package com.giztv.tv.link

import android.app.Activity
import android.content.Context
import android.media.AudioManager
import android.view.InputDevice
import android.view.KeyCharacterMap
import android.view.KeyEvent
import androidx.media3.common.C
import androidx.media3.common.Player
import com.giztv.tv.data.PlaybackContext
import com.giztv.tv.data.WatchHistoryEntry
import com.giztv.tv.data.WatchHistoryStore
import com.giztv.tv.home.resumeIntent
import com.giztv.tv.ui.player.PlaybackProgressStore
import com.giztv.tv.ui.player.playbackProgressKeyForPage
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
  @Volatile private var playerOptions: RemotePlayerOptions? = null

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
  fun attachOptions(options: RemotePlayerOptions) {
    playerOptions = options
  }

  @Synchronized
  fun detachOptions(options: RemotePlayerOptions) {
    if (playerOptions === options) playerOptions = null
  }

  /** The player's settings as the phone should see them, read on the thread that owns them. */
  suspend fun options(): LinkEvent.Options =
    withContext(Dispatchers.Main.immediate) {
      val source = playerOptions
      LinkEvent.Options(
        groups = source?.groups().orEmpty(),
        subtitleOffsetMs = source?.subtitleOffsetMs() ?: 0L,
      )
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
  suspend fun state(context0: Context? = null): LinkEvent.State =
    withContext(Dispatchers.Main.immediate) {
      val player = player()
      val context = playbackContext
      LinkEvent.State(
        playing = player?.isPlaying == true,
        pageUrl = context?.pageUrl,
        title = context?.title,
        subtitle = context?.subtitle,
        posterUrl = context?.posterUrl,
        positionMs = player?.currentPosition?.coerceAtLeast(0L) ?: 0L,
        durationMs = player?.duration?.takeIf { it != C.TIME_UNSET }?.coerceAtLeast(0L) ?: 0L,
        volume = context0?.let(::currentVolumePercent) ?: 0,
      )
    }

  suspend fun execute(context: Context, command: LinkCommand) {
    withContext(Dispatchers.Main.immediate) {
      val player = player()
      when (command) {
        // Answered during the handshake, not here.
        is LinkCommand.Hello,
        is LinkCommand.Pair -> Unit
        is LinkCommand.Play -> receiveHandover(context.applicationContext, command)
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
        is LinkCommand.SetVolume -> setVolume(context, command.percent)
        is LinkCommand.Key -> press(command.key)
        is LinkCommand.Text -> type(command.text)
        is LinkCommand.SelectOption -> playerOptions?.select(command.groupId, command.itemId)
        is LinkCommand.SubtitleSync -> playerOptions?.nudgeSubtitleSync(command.deltaMs)
      }
    }
  }

  /**
   * Takes a title sent from a phone and opens it.
   *
   * The television resumes from what it already knows about a title, so the phone's title and
   * position are written into its own stores first. Otherwise a film watched entirely on a phone
   * would arrive here as an unknown page and start from the beginning, which is the one thing
   * handing it over was meant to avoid.
   */
  private fun receiveHandover(context: Context, command: LinkCommand.Play) {
    val existing = WatchHistoryStore(context).find(command.pageUrl)
    WatchHistoryStore(context)
      .save(
        WatchHistoryEntry(
          pageUrl = command.pageUrl,
          title = command.title,
          subtitle = command.subtitle,
          posterUrl = command.posterUrl,
          positionMs = command.positionMs,
          durationMs = existing?.durationMs ?: 0L,
          completed = false,
          updatedAtMs = System.currentTimeMillis(),
          showId = existing?.showId,
          seasonNumber = existing?.seasonNumber,
          episodeNumber = existing?.episodeNumber,
          shortForm = existing?.shortForm ?: false,
        )
      )
    // The player reads its resume position from here rather than from the history.
    PlaybackProgressStore(context)
      .update(
        key = playbackProgressKeyForPage(command.pageUrl),
        positionMs = command.positionMs,
        durationMs = existing?.durationMs ?: 0L,
        playbackState = Player.STATE_READY,
      )
    context.startActivity(resumeIntent(context, command.pageUrl))
  }

  /** Where the television's own music volume sits, as a percentage the phone's slider can show. */
  private fun currentVolumePercent(context: Context): Int {
    val audio = context.getSystemService(Context.AUDIO_SERVICE) as? AudioManager ?: return 0
    val max = audio.getStreamMaxVolume(AudioManager.STREAM_MUSIC).takeIf { it > 0 } ?: return 0
    return (audio.getStreamVolume(AudioManager.STREAM_MUSIC) * 100 / max).coerceIn(0, 100)
  }

  private fun setVolume(context: Context, percent: Int) {
    val audio = context.getSystemService(Context.AUDIO_SERVICE) as? AudioManager ?: return
    val max = audio.getStreamMaxVolume(AudioManager.STREAM_MUSIC).takeIf { it > 0 } ?: return
    val level = (percent.coerceIn(0, 100) * max / 100).coerceIn(0, max)
    runCatching { audio.setStreamVolume(AudioManager.STREAM_MUSIC, level, 0) }
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
   * A pad press, moving focus where the screen has lent us a way to.
   *
   * Arrows go through the screen's own focus system, which works whether or not anything was
   * focused to begin with. Select and back stay as key events: those the window handles perfectly
   * well once something is focused.
   */
  private fun press(key: LinkKey) {
    val moved = when (key) {
      LinkKey.UP, LinkKey.DOWN, LinkKey.LEFT, LinkKey.RIGHT ->
        RemoteUiBridge.moveFocus?.invoke(key) ?: false
      else -> false
    }
    if (!moved) dispatchKey(key.toKeyCode())
  }

  /**
   * Typing, which is only ever meant for the search box.
   *
   * Ignored while the player is up: there is nothing to type into behind a film, and letters
   * arriving as key presses would be read as playback shortcuts.
   */
  private fun type(text: String) {
    if (RemoteUiBridge.playerOpen) return
    val search = RemoteUiBridge.typeIntoSearch
    if (search != null) search(text) else dispatchText(text)
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
    // A key event with no input source is dropped by the focus system, which is why an event built
    // from the short constructor moves nothing. The device and source below are what make this
    // count as a real press arriving from a real pad.
    fun event(action: Int) =
      KeyEvent(
        now,
        now,
        action,
        keyCode,
        0,
        0,
        KeyCharacterMap.VIRTUAL_KEYBOARD,
        0,
        0,
        InputDevice.SOURCE_KEYBOARD,
      )
    runCatching {
      activity.dispatchKeyEvent(event(KeyEvent.ACTION_DOWN))
      activity.dispatchKeyEvent(event(KeyEvent.ACTION_UP))
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
    LinkKey.BACKSPACE -> KeyEvent.KEYCODE_DEL
  }
