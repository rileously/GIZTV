package com.example.auroratv.ui.player

import android.app.Activity
import android.content.Context
import android.content.ContextWrapper
import android.content.pm.PackageManager
import android.graphics.Color as AndroidColor
import android.view.ViewGroup
import androidx.activity.compose.BackHandler
import androidx.compose.animation.AnimatedVisibility
import androidx.compose.animation.fadeIn
import androidx.compose.animation.fadeOut
import androidx.compose.foundation.background
import androidx.compose.foundation.border
import androidx.compose.foundation.clickable
import androidx.compose.foundation.focusable
import androidx.compose.foundation.focusGroup
import androidx.compose.foundation.gestures.detectHorizontalDragGestures
import androidx.compose.foundation.gestures.detectTapGestures
import androidx.compose.foundation.layout.Arrangement
import androidx.compose.foundation.layout.Box
import androidx.compose.foundation.layout.BoxWithConstraints
import androidx.compose.foundation.layout.Column
import androidx.compose.foundation.layout.Row
import androidx.compose.foundation.layout.Spacer
import androidx.compose.foundation.layout.fillMaxSize
import androidx.compose.foundation.layout.fillMaxHeight
import androidx.compose.foundation.layout.fillMaxWidth
import androidx.compose.foundation.layout.height
import androidx.compose.foundation.layout.heightIn
import androidx.compose.foundation.layout.offset
import androidx.compose.foundation.layout.padding
import androidx.compose.foundation.layout.width
import androidx.compose.foundation.layout.widthIn
import androidx.compose.foundation.layout.size
import androidx.compose.foundation.horizontalScroll
import androidx.compose.foundation.rememberScrollState
import androidx.compose.foundation.shape.CircleShape
import androidx.compose.foundation.shape.RoundedCornerShape
import androidx.compose.foundation.verticalScroll
import androidx.compose.material.icons.Icons
import androidx.compose.material.icons.automirrored.filled.ArrowBack
import androidx.compose.material.icons.automirrored.filled.VolumeUp
import androidx.compose.material.icons.filled.ClosedCaption
import androidx.compose.material.icons.filled.Close
import androidx.compose.material.icons.filled.Check
import androidx.compose.material.icons.filled.Forward10
import androidx.compose.material.icons.filled.HighQuality
import androidx.compose.material.icons.filled.AspectRatio
import androidx.compose.material.icons.filled.Memory
import androidx.compose.material.icons.filled.Pause
import androidx.compose.material.icons.filled.PlayArrow
import androidx.compose.material.icons.filled.Replay10
import androidx.compose.material.icons.filled.Speed
import androidx.compose.material.icons.filled.Tv
import androidx.compose.material3.CircularProgressIndicator
import androidx.compose.material3.Icon
import androidx.compose.material3.MaterialTheme as ComposeMaterialTheme
import androidx.compose.material3.Slider
import androidx.compose.material3.SliderDefaults
import androidx.compose.material3.darkColorScheme as composeDarkColorScheme
import androidx.compose.runtime.Composable
import androidx.compose.runtime.DisposableEffect
import androidx.compose.runtime.LaunchedEffect
import androidx.compose.runtime.getValue
import androidx.compose.runtime.mutableIntStateOf
import androidx.compose.runtime.mutableLongStateOf
import androidx.compose.runtime.mutableStateOf
import androidx.compose.runtime.remember
import androidx.compose.runtime.rememberUpdatedState
import androidx.compose.runtime.setValue
import androidx.compose.ui.Alignment
import androidx.compose.ui.Modifier
import androidx.compose.ui.draw.alpha
import androidx.compose.ui.draw.clip
import androidx.compose.ui.focus.FocusRequester
import androidx.compose.ui.focus.focusRequester
import androidx.compose.ui.focus.onFocusChanged
import androidx.compose.ui.graphics.Brush
import androidx.compose.ui.graphics.Color
import androidx.compose.ui.graphics.vector.ImageVector
import androidx.compose.ui.input.key.Key
import androidx.compose.ui.input.key.KeyEventType
import androidx.compose.ui.input.key.key
import androidx.compose.ui.input.key.onKeyEvent
import androidx.compose.ui.input.key.onPreviewKeyEvent
import androidx.compose.ui.input.key.type
import androidx.compose.ui.input.pointer.pointerInput
import androidx.compose.ui.layout.onSizeChanged
import androidx.compose.ui.platform.LocalContext
import androidx.compose.ui.semantics.Role
import androidx.compose.ui.semantics.contentDescription
import androidx.compose.ui.semantics.onClick as semanticsOnClick
import androidx.compose.ui.semantics.role
import androidx.compose.ui.semantics.selected
import androidx.compose.ui.semantics.semantics
import androidx.compose.ui.text.font.FontWeight
import androidx.compose.ui.text.style.TextOverflow
import androidx.compose.ui.unit.IntOffset
import androidx.compose.ui.unit.dp
import androidx.compose.ui.unit.sp
import androidx.compose.ui.viewinterop.AndroidView
import androidx.lifecycle.Lifecycle
import androidx.lifecycle.LifecycleEventObserver
import androidx.lifecycle.compose.LocalLifecycleOwner
import androidx.media3.common.C
import androidx.media3.common.DeviceInfo
import androidx.media3.common.Format
import androidx.media3.common.MediaItem
import androidx.media3.common.MimeTypes
import androidx.media3.common.ParserException
import androidx.media3.common.PlaybackException
import androidx.media3.common.Player
import androidx.media3.common.TrackSelectionOverride
import androidx.media3.common.Tracks
import androidx.media3.common.VideoSize
import androidx.media3.common.util.UnstableApi
import androidx.media3.cast.CastPlayer
import androidx.media3.cast.MediaRouteButtonFactory
import androidx.media3.cast.RemoteCastPlayer
import androidx.media3.datasource.DefaultDataSource
import androidx.media3.datasource.DefaultHttpDataSource
import androidx.media3.exoplayer.DefaultLoadControl
import androidx.media3.exoplayer.DefaultRenderersFactory
import androidx.media3.exoplayer.ExoPlayer
import androidx.media3.exoplayer.mediacodec.MediaCodecSelector
import androidx.media3.exoplayer.source.DefaultMediaSourceFactory
import androidx.media3.exoplayer.source.MediaSource
import androidx.media3.exoplayer.trackselection.AdaptiveTrackSelection
import androidx.media3.exoplayer.trackselection.DefaultTrackSelector
import androidx.media3.exoplayer.upstream.DefaultBandwidthMeter
import androidx.media3.ui.AspectRatioFrameLayout
import androidx.media3.ui.CaptionStyleCompat
import androidx.media3.ui.PlayerView
import androidx.media3.ui.SubtitleView
import androidx.mediarouter.app.MediaRouteButton
import androidx.core.net.toUri
import androidx.tv.material3.Text
import com.example.auroratv.data.PlaybackContext
import com.example.auroratv.data.WatchHistoryStore
import com.example.auroratv.link.GROUP_AUDIO
import com.example.auroratv.link.GROUP_QUALITY
import com.example.auroratv.link.GROUP_RESIZE
import com.example.auroratv.link.GROUP_SPEED
import com.example.auroratv.link.GROUP_SUBTITLE
import com.example.auroratv.link.LinkCommand
import com.example.auroratv.link.PhoneLink
import com.example.auroratv.link.RemoteControl
import com.example.auroratv.link.RemoteOptionGroup
import com.example.auroratv.link.RemoteOptionItem
import com.example.auroratv.link.RemotePlayerOptions
import com.example.auroratv.theme.AuroraBlue
import com.example.auroratv.theme.AuroraMint
import com.example.auroratv.theme.DeepSpace
import com.example.auroratv.theme.MutedBlue
import com.example.auroratv.theme.NightSurface
import com.example.auroratv.theme.SoftWhite
import com.example.auroratv.gizTvOrientation
import java.util.Locale
import kotlin.math.roundToInt
import kotlinx.coroutines.delay

internal data class HlsStreamRequest(
  val url: String,
  val headers: Map<String, String>,
  val subtitles: List<ExternalSubtitleTrack> = emptyList(),
  val sourcePageUrl: String? = null,
  val title: String? = null,
  /** What the catalog knows about this title; absent for streams found by plain browsing. */
  val context: PlaybackContext? = null,
)

internal data class ExternalSubtitleTrack(
  val url: String,
  val label: String,
  val language: String?,
  val mimeType: String,
)

/** The speeds the phone offers, which are the ones worth reaching for on a sofa. */
private val REMOTE_SPEEDS = listOf(0.5f, 0.75f, 1f, 1.25f, 1.5f, 2f)

private fun remoteSpeedLabel(speed: Float): String =
  if (speed == 1f) "Normal" else "${speed}x".replace(".0x", "x")

private const val AUTO_ADVANCE_SECONDS = 10
private const val COMPATIBILITY_MAX_VIDEO_WIDTH = 1280
private const val COMPATIBILITY_MAX_VIDEO_HEIGHT = 720
private const val COMPATIBILITY_MAX_VIDEO_BITRATE = 5_000_000
private const val STARTUP_MAX_VIDEO_WIDTH = 640
private const val STARTUP_MAX_VIDEO_HEIGHT = 360
private const val QUALITY_RAMP_FIRST_STEP_MS = 4_000L
private const val QUALITY_RAMP_FINAL_STEP_MS = 8_000L
private const val AUTO_QUALITY_INCREASE_BUFFER_MS = 15_000
private const val AUTO_QUALITY_DECREASE_BUFFER_MS = 35_000
private const val AUTO_QUALITY_RETAIN_BUFFER_MS = 25_000
private const val AUTO_QUALITY_BANDWIDTH_FRACTION = .55f

internal enum class AutomaticQualityPhase {
  LOW_STARTUP,
  BALANCED,
  UNRESTRICTED,
}

internal data class VideoQualityOption(
  val label: String,
  val width: Int? = null,
  val height: Int? = null,
) {
  val isAuto: Boolean get() = width == null || height == null
}

internal data class AudioTrackOption(
  val label: String,
  val override: TrackSelectionOverride? = null,
) {
  val isAutomatic: Boolean get() = override == null
}

internal data class SubtitleTrackOption(
  val label: String,
  val override: TrackSelectionOverride? = null,
  val disabled: Boolean = false,
)

internal enum class SubtitleSizeOption(val label: String, val scale: Float) {
  SMALL("Small", .78f),
  NORMAL("Normal", 1f),
  LARGE("Large", 1.25f),
  EXTRA_LARGE("Extra large", 1.5f),
}

internal enum class SubtitlePositionOption(val label: String, val bottomPadding: Float) {
  BOTTOM("Bottom", .08f),
  RAISED("Raised", .18f),
  HIGH("High", .30f),
}

internal enum class SubtitleStyleOption(val label: String) {
  OUTLINE("Outline"),
  DARK_BOX("Dark box"),
}

@androidx.annotation.OptIn(UnstableApi::class)
internal enum class VideoResizeOption(val label: String, val resizeMode: Int) {
  FIT("Fit", AspectRatioFrameLayout.RESIZE_MODE_FIT),
  ZOOM("Zoom", AspectRatioFrameLayout.RESIZE_MODE_ZOOM),
  STRETCH("Stretch", AspectRatioFrameLayout.RESIZE_MODE_FILL),
  FIT_WIDTH("Fit width", AspectRatioFrameLayout.RESIZE_MODE_FIXED_WIDTH),
  FIT_HEIGHT("Fit height", AspectRatioFrameLayout.RESIZE_MODE_FIXED_HEIGHT),
}

private enum class PlayerControlDialog {
  SUBTITLES,
  SUBTITLE_SYNC,
  AUDIO,
  QUALITY,
  PICTURE,
  SPEED,
  DECODER,
}

@androidx.annotation.OptIn(UnstableApi::class)
@Composable
internal fun HlsPlayerScreen(
  request: HlsStreamRequest,
  onExit: () -> Unit,
  onPlayNext: (PlaybackContext) -> Unit = {},
  /** Said once the episode is over, while the countdown is the only thing left to wait for. */
  onPrepareNext: (PlaybackContext) -> Unit = {},
  /** Said once a title has been handed to the television, so this screen can step aside. */
  onHandedOver: () -> Unit = {},
  /** Said when the stream itself will not play, so a remembered address can be thrown away. */
  onPlaybackFailed: () -> Unit = {},
) {
  val context = LocalContext.current
  val lifecycleOwner = LocalLifecycleOwner.current
  val progressStore = remember(context) { PlaybackProgressStore(context) }
  val historyStore = remember(context) { WatchHistoryStore(context) }
  val subtitleSyncStore = remember(context) { SubtitleSyncStore(context) }
  val playerPreferences = remember(context) { PlayerPreferencesStore(context) }
  val progressKey = remember(request) { playbackProgressKey(request) }
  val subtitleSyncKey = remember(request) { subtitleSyncKey(request) }
  // Continue watching keeps its own position against the catalog page, and it is the one the
  // viewer was just shown. It stands in when the progress store has nothing under this key —
  // which is every catalog title carried over from a build that keyed them by the page the stream
  // happened to be found on.
  val savedProgressMs =
    remember(progressKey) {
      progressStore.load(progressKey).takeIf { it > 0L }
        ?: request.context
          ?.let { historyStore.find(it.pageUrl) }
          ?.takeIf { !it.completed }
          ?.positionMs
          ?.coerceAtLeast(0L)
        ?: 0L
    }
  val isTelevision = remember(context) { context.packageManager.hasSystemFeature(PackageManager.FEATURE_LEANBACK) }
  val activity = remember(context) { context.findActivity() }
  var status by remember(request.url) { mutableStateOf("Starting smoothly in low quality…") }
  var subtitleStatus by remember(request) {
    mutableStateOf(
      if (request.subtitles.isEmpty()) {
        "CC: Looking for English subtitles…"
      } else {
        "CC: Loading ${request.subtitles.size} separate subtitle track(s)…"
      }
    )
  }
  var error by remember(request.url) { mutableStateOf<String?>(null) }
  var isVideoPlaying by remember(request) { mutableStateOf(false) }
  var isBuffering by remember(request) { mutableStateOf(true) }
  var compatibilityMode by remember(request) { mutableStateOf(false) }
  var resumePositionMs by remember(request) { mutableLongStateOf(savedProgressMs) }
  var activeDialog by remember(request) { mutableStateOf<PlayerControlDialog?>(null) }
  val settingsOpen = activeDialog != null
  var resumePlayWhenReady by remember(request) { mutableStateOf(true) }
  var subtitleSize by remember(request) { mutableStateOf(playerPreferences.subtitleSize()) }
  var subtitlePosition by remember(request) { mutableStateOf(playerPreferences.subtitlePosition()) }
  var subtitleStyle by remember(request) { mutableStateOf(playerPreferences.subtitleStyle()) }
  // Read before the player is built, so the stream starts already in sync rather than reloading.
  var subtitleOffsetMs by remember(subtitleSyncKey) { mutableLongStateOf(subtitleSyncStore.load(subtitleSyncKey)) }
  var playbackSpeed by remember(request) { mutableStateOf(playerPreferences.playbackSpeed()) }
  var videoResize by remember(request) { mutableStateOf(VideoResizeOption.FIT) }
  var isCasting by remember(request) { mutableStateOf(false) }
  var videoSize by remember(request) { mutableStateOf(VideoSize.UNKNOWN) }
  var inPictureInPicture by remember { mutableStateOf(false) }
  var selectedQuality by remember(request, compatibilityMode) { mutableStateOf(VideoQualityOption("Auto")) }
  var selectedAudio by remember(request, compatibilityMode) { mutableStateOf(playerPreferences.audioTrack()) }
  var selectedSubtitle by remember(request, compatibilityMode) { mutableStateOf(playerPreferences.subtitleTrack()) }
  val localPlayer =
    remember(request, compatibilityMode) {
      createHlsPlayer(context, request, compatibilityMode, subtitleOffsetMs)
    }
  val player: Player =
    remember(localPlayer, isTelevision) {
      createCastAwarePlayer(context, localPlayer, isTelevision)
    }
  var qualityOptions by remember(player) { mutableStateOf(listOf(VideoQualityOption("Auto"))) }
  var audioOptions by remember(player) { mutableStateOf(listOf(AudioTrackOption("Auto English"))) }
  var subtitleOptions by remember(player) { mutableStateOf(listOf(SubtitleTrackOption("Auto English"), SubtitleTrackOption("Off", disabled = true))) }
  var controlsVisible by remember { mutableStateOf(true) }
  var controlsInteractionVersion by remember { mutableIntStateOf(0) }
  var playbackFinished by remember(request) { mutableStateOf(false) }
  var automaticQualityRampCompleted by remember(player) { mutableStateOf(false) }
  // A two-minute short drama is watched as a run, so it rolls on with no countdown to sit through.
  // A full-length episode gets the pause, which is the viewer's chance to stop after one.
  val shortForm = request.context?.shortForm == true
  val autoAdvanceDelaySeconds = if (shortForm) 0 else AUTO_ADVANCE_SECONDS
  var autoAdvanceSeconds by remember(request) { mutableIntStateOf(autoAdvanceDelaySeconds) }
  val playButtonFocusRequester = remember { FocusRequester() }
  val surfaceFocusRequester = remember { FocusRequester() }
  val nextEntry = request.context?.nextEntry
  val nextPromptVisible = playbackFinished && nextEntry != null

  fun savePlaybackProgress() {
    progressStore.update(
      key = progressKey,
      positionMs = player.currentPosition,
      durationMs = player.duration,
      playbackState = player.playbackState,
    )
    // Only catalog-launched titles carry a context, and only those can be shown in Continue watching.
    request.context?.let { playbackContext ->
      val durationMs = player.duration.takeIf { it != C.TIME_UNSET } ?: 0L
      val resumable =
        resumablePlaybackPosition(player.currentPosition, player.duration, player.playbackState)
      historyStore.record(
        context = playbackContext,
        positionMs = resumable ?: 0L,
        durationMs = durationMs,
        completed = resumable == null && player.playbackState == Player.STATE_ENDED,
      )
    }
  }

  DisposableEffect(activity, isTelevision, shortForm) {
    activity?.requestedOrientation =
      gizTvOrientation(isTelevision = isTelevision, playerActive = true, verticalVideo = shortForm)
    onDispose {
      activity?.requestedOrientation = gizTvOrientation(isTelevision = isTelevision, playerActive = false)
    }
  }

  // Roll into the next episode once one finishes, unless the viewer steps in first.
  LaunchedEffect(nextPromptVisible) {
    // Both are known here: the prompt is only visible when there is a next episode to show, and
    // there is only a next episode when the catalog gave this playback a context.
    if (!nextPromptVisible) return@LaunchedEffect
    val playbackContext = request.context
    val next = nextEntry
    // Nothing is being decoded now the episode is over, so finding the next stream can start while
    // the countdown runs instead of after it.
    onPrepareNext(playbackContext.advanceTo(next))
    autoAdvanceSeconds = autoAdvanceDelaySeconds
    while (autoAdvanceSeconds > 0) {
      delay(1_000L)
      autoAdvanceSeconds--
    }
    onPlayNext(playbackContext.advanceTo(next))
  }

  fun openSettings(dialog: PlayerControlDialog) {
    controlsInteractionVersion++
    activeDialog = dialog
  }

  fun closeSettings() {
    activeDialog = null
    controlsVisible = true
    controlsInteractionVersion++
  }

  BackHandler {
    when (playerBackAction(settingsOpen, controlsVisible)) {
      PlayerBackAction.CLOSE_SETTINGS -> closeSettings()
      PlayerBackAction.HIDE_CONTROLS -> {
        controlsVisible = false
      }
      PlayerBackAction.EXIT_PLAYER -> onExit()
    }
  }

  // What a paired phone's transport controls act on. Registered for as long as this player is the
  // one on screen, so a command that arrives afterwards finds nothing rather than a dead player.
  DisposableEffect(player, request) {
    RemoteControl.attachPlayer(player, request.context)
    onDispose { RemoteControl.detachPlayer(player) }
  }

  // The same settings the television's own dialog offers, handed to whatever is holding the remote.
  // Every pick is routed back through the handlers below rather than reimplemented, so a subtitle
  // chosen on a phone reloads the media source exactly as one chosen on the sofa does.
  val remoteOptions =
    remember(player) {
      object : RemotePlayerOptions {
        override fun groups(): List<RemoteOptionGroup> =
          listOf(
              RemoteOptionGroup(
                GROUP_SUBTITLE,
                "Subtitles",
                subtitleOptions.map { RemoteOptionItem(it.label, it.label, it == selectedSubtitle) },
              ),
              RemoteOptionGroup(
                GROUP_AUDIO,
                "Audio",
                audioOptions.map { RemoteOptionItem(it.label, it.label, it == selectedAudio) },
              ),
              RemoteOptionGroup(
                GROUP_QUALITY,
                "Quality",
                qualityOptions.map { RemoteOptionItem(it.label, it.label, it == selectedQuality) },
              ),
              RemoteOptionGroup(
                GROUP_SPEED,
                "Speed",
                REMOTE_SPEEDS.map {
                  RemoteOptionItem(it.toString(), remoteSpeedLabel(it), it == playbackSpeed)
                },
              ),
              RemoteOptionGroup(
                GROUP_RESIZE,
                "Picture",
                VideoResizeOption.entries.map {
                  RemoteOptionItem(it.name, it.name.lowercase().replace('_', ' ').replaceFirstChar(Char::uppercase), it == videoResize)
                },
              ),
            )
            .filter { it.items.isNotEmpty() }

        override fun select(groupId: String, itemId: String) {
          when (groupId) {
            GROUP_SUBTITLE ->
              subtitleOptions.firstOrNull { it.label == itemId }?.let { option ->
                selectedSubtitle = option
                playerPreferences.setSubtitleTrack(option)
                selectSubtitleTrack(player, option)
              }
            GROUP_AUDIO ->
              audioOptions.firstOrNull { it.label == itemId }?.let { option ->
                selectedAudio = option
                playerPreferences.setAudioTrack(option)
                selectAudioTrack(player, option)
              }
            GROUP_QUALITY ->
              qualityOptions.firstOrNull { it.label == itemId }?.let { option ->
                selectedQuality = option
                selectVideoQuality(player, option)
              }
            GROUP_SPEED ->
              itemId.toFloatOrNull()?.let { speed ->
                playbackSpeed = speed
                playerPreferences.setPlaybackSpeed(speed)
                player.setPlaybackSpeed(speed)
              }
            GROUP_RESIZE ->
              VideoResizeOption.entries.firstOrNull { it.name == itemId }?.let { videoResize = it }
          }
        }

        override fun nudgeSubtitleSync(deltaMs: Long) {
          // Casting hands the subtitles to the receiver, which owns their timing from then on.
          if (isCasting) return
          val target = subtitleOffsetMs + deltaMs
          val currentPositionMs = player.currentPosition.coerceAtLeast(0L)
          val keepPlaying = player.playWhenReady
          subtitleOffsetMs = target
          subtitleSyncStore.save(subtitleSyncKey, target)
          resumePositionMs = currentPositionMs
          resumePlayWhenReady = keepPlaying
          localPlayer.setMediaSource(createHlsMediaSource(context, request, target), currentPositionMs)
          localPlayer.prepare()
          localPlayer.playWhenReady = keepPlaying
        }

        override fun subtitleOffsetMs(): Long = subtitleOffsetMs
      }
    }

  DisposableEffect(remoteOptions) {
    RemoteControl.attachOptions(remoteOptions)
    onDispose { RemoteControl.detachOptions(remoteOptions) }
  }

  // Sending what is on this phone to the television. Only offered on a phone, only when a
  // television has been paired, and only for a catalog title, since a stream found by browsing has
  // no page the television could resolve for itself.
  val handoverContext = request.context
  val canHandOver =
    !isTelevision && handoverContext != null && remember(context) { PhoneLink.hasTelevision(context) }
  var handoverSent by remember(request) { mutableStateOf(false) }

  fun handOverToTelevision() {
    val playback = handoverContext ?: return
    val sent =
      PhoneLink.client(context)
        .playOnTelevision(
          LinkCommand.Play(
            pageUrl = playback.pageUrl,
            title = playback.title,
            subtitle = playback.subtitle,
            posterUrl = playback.posterUrl,
            positionMs = player.currentPosition.coerceAtLeast(0L),
          )
        )
    if (sent) {
      // Leaving the player outright rather than merely pausing it: pausing left this screen sitting
      // on a still frame of something now playing next door, and the useful thing to be looking at
      // from that moment is the remote.
      savePlaybackProgress()
      player.pause()
      handoverSent = true
      onHandedOver()
    }
  }

  DisposableEffect(player, lifecycleOwner) {
    val listener =
      object : Player.Listener {
        override fun onPlaybackStateChanged(playbackState: Int) {
          isBuffering = playbackState == Player.STATE_BUFFERING
          when (playbackState) {
            Player.STATE_READY -> subtitleStatus = describeSubtitleState(player.currentTracks)
            Player.STATE_ENDED -> {
              progressStore.clear(progressKey)
              request.context?.let {
                historyStore.markCompleted(it, player.duration.takeIf { d -> d != C.TIME_UNSET } ?: 0L)
              }
              playbackFinished = true
            }
            else -> Unit
          }
        }

        override fun onTracksChanged(tracks: Tracks) {
          subtitleStatus = describeSubtitleState(tracks)
          qualityOptions = videoQualityOptions(tracks, compatibilityMode)
          if (selectedQuality !in qualityOptions) selectedQuality = qualityOptions.first()
          audioOptions = audioTrackOptions(tracks)
          // Matching by label alone is not enough: the option carrying the override is what
          // actually switches the track, and a remembered label arrives without one.
          selectedAudio = audioOptions.firstOrNull { it.label == selectedAudio.label } ?: audioOptions.first()
          subtitleOptions = subtitleTrackOptions(tracks)
          selectedSubtitle =
            subtitleOptions.firstOrNull { it.label == selectedSubtitle.label } ?: subtitleOptions.first()
          selectVideoQuality(player, selectedQuality)
          selectAudioTrack(player, selectedAudio)
          selectSubtitleTrack(player, selectedSubtitle)
        }

        override fun onDeviceInfoChanged(deviceInfo: DeviceInfo) {
          isCasting = deviceInfo.playbackType == DeviceInfo.PLAYBACK_TYPE_REMOTE
          if (isCasting) status = "Casting to Chromecast · subtitles available in CC"
        }

        override fun onIsPlayingChanged(isPlaying: Boolean) {
          isVideoPlaying = isPlaying
          // A floating window is too small for the controls, and the viewer is looking elsewhere.
          if (!isPlaying && !inPictureInPicture) controlsVisible = true
        }

        override fun onVideoSizeChanged(size: VideoSize) {
          videoSize = size
        }

        override fun onPlayerError(playerError: PlaybackException) {
          android.util.Log.e("AuroraHls", "Playback failed for ${request.url}", playerError)
          // A decoder that cannot cope is this device's problem and the address is fine; anything
          // else and the address is the first suspect.
          if (!isVideoDecoderFailure(playerError)) onPlaybackFailed()
          if (!compatibilityMode && isVideoDecoderFailure(playerError)) {
            resumePositionMs = player.currentPosition.coerceAtLeast(0L)
            savePlaybackProgress()
            isVideoPlaying = false
            error = null
            status = "TV decoder failed · retrying in compatibility mode…"
            android.util.Log.w("AuroraHls", "Retrying with software-first decoder and 720p limit")
            compatibilityMode = true
            return
          }
          val invalidPlaylist = generateSequence<Throwable>(playerError) { it.cause }.any { it is ParserException }
          error =
            when {
              invalidPlaylist -> "The detected URL did not return a valid HLS playlist. Try another server."
              isVideoDecoderFailure(playerError) ->
                "This TV could not decode the video, even in compatibility mode. Try another video server."
              else -> playerError.localizedMessage ?: "This stream could not be played."
            }
          isVideoPlaying = false
          controlsVisible = true
          status = "Playback error"
          savePlaybackProgress()
        }
      }
    val observer =
      LifecycleEventObserver { _, event ->
        when (event) {
          Lifecycle.Event.ON_START -> player.play()
          Lifecycle.Event.ON_STOP -> {
            savePlaybackProgress()
            player.pause()
          }
          else -> Unit
        }
      }

    player.addListener(listener)
    lifecycleOwner.lifecycle.addObserver(observer)
    if (resumePositionMs > 0L) player.seekTo(resumePositionMs)
    player.setPlaybackSpeed(playbackSpeed)
    player.prepare()
    player.playWhenReady = resumePlayWhenReady

    onDispose {
      savePlaybackProgress()
      lifecycleOwner.lifecycle.removeObserver(observer)
      player.removeListener(listener)
      player.release()
    }
  }

  // Start with a small rendition so playback begins quickly. Once video is actually flowing, widen
  // the ceiling in two steps; ExoPlayer still chooses within each ceiling from measured bandwidth.
  LaunchedEffect(player, selectedQuality, compatibilityMode, isVideoPlaying) {
    if (compatibilityMode || automaticQualityRampCompleted) return@LaunchedEffect
    if (!selectedQuality.isAuto) {
      applyAutomaticQualityPhase(player, AutomaticQualityPhase.UNRESTRICTED)
      automaticQualityRampCompleted = true
      return@LaunchedEffect
    }
    if (!isVideoPlaying) return@LaunchedEffect

    status = "Auto quality · measuring your connection"
    delay(QUALITY_RAMP_FIRST_STEP_MS)
    applyAutomaticQualityPhase(player, AutomaticQualityPhase.BALANCED)
    status = "Auto quality · increasing when the buffer is healthy"
    delay(QUALITY_RAMP_FINAL_STEP_MS)
    applyAutomaticQualityPhase(player, AutomaticQualityPhase.UNRESTRICTED)
    automaticQualityRampCompleted = true
    status = "Auto quality · adapting to your network"
  }

  LaunchedEffect(player, progressKey) {
    while (true) {
      delay(3_000L)
      savePlaybackProgress()
    }
  }

  LaunchedEffect(controlsVisible, isVideoPlaying, settingsOpen, controlsInteractionVersion) {
    if (controlsVisible && isVideoPlaying && !settingsOpen) {
      delay(playerControllerTimeoutMs(isTelevision).toLong())
      controlsVisible = false
    }
  }

  LaunchedEffect(controlsVisible, settingsOpen) {
    delay(80L)
    if (settingsOpen) return@LaunchedEffect
    if (controlsVisible) {
      runCatching { playButtonFocusRequester.requestFocus() }
    } else {
      runCatching { surfaceFocusRequester.requestFocus() }
    }
  }

  // What is playing reaches the notification shade and the lock screen, so it can be paused and
  // seeked from there. Casting is left alone: the Cast notification already controls that.
  MediaControlsEffect(
    player = player,
    request = request,
    enabled = remember(context) { context.supportsMediaNotification() } && !isCasting,
  )

  // Leaving the app hands the video to a floating window instead of stopping it.
  val pictureInPictureSupported = remember(context) { context.supportsPictureInPicture() }
  PictureInPictureEffect(
    enabled =
      shouldEnterPictureInPicture(
        supported = pictureInPictureSupported,
        isCasting = isCasting,
        hasError = error != null,
        playbackFinished = playbackFinished,
      ),
    isPlaying = isVideoPlaying,
    aspectRatio =
      remember(videoSize) {
        pictureInPictureAspectRatio(
          videoWidth = (videoSize.width * videoSize.pixelWidthHeightRatio).roundToInt(),
          videoHeight = videoSize.height,
        )
      },
    onPlayPause = { play -> if (play) player.play() else player.pause() },
    onModeChanged = { inPictureInPicture = it },
  )

  // The window is only large enough for the picture itself, so everything drawn over it stands
  // down; a settings panel left open would otherwise come back covering the video on return.
  LaunchedEffect(inPictureInPicture) {
    if (!inPictureInPicture) return@LaunchedEffect
    activeDialog = null
    controlsVisible = false
  }

  val revealControlsKeys =
    remember {
      setOf(
        Key.DirectionCenter,
        Key.Enter,
        Key.NumPadEnter,
        Key.DirectionLeft,
        Key.DirectionRight,
        Key.DirectionUp,
        Key.DirectionDown,
      )
    }

  Box(
    Modifier.fillMaxSize().background(Color.Black)
      .focusRequester(surfaceFocusRequester)
      .onPreviewKeyEvent { event ->
        if (event.type == KeyEventType.KeyDown && event.key in revealControlsKeys) {
          controlsInteractionVersion++
          if (!controlsVisible && !settingsOpen) {
            controlsVisible = true
            true
          } else {
            false
          }
        } else {
          false
        }
      }
      .focusable(enabled = !controlsVisible && !settingsOpen)
      .pointerInput(settingsOpen, controlsVisible) {
        detectTapGestures {
          if (!settingsOpen) {
            controlsVisible = !controlsVisible
            controlsInteractionVersion++
          }
        }
      }
  ) {
    AndroidView(
      factory = {
        PlayerView(it).apply {
          this.player = player
          useController = false
          descendantFocusability = ViewGroup.FOCUS_BLOCK_DESCENDANTS
          resizeMode = videoResize.resizeMode
          setShowBuffering(PlayerView.SHOW_BUFFERING_ALWAYS)
          keepScreenOn = true
          isFocusable = false
          isFocusableInTouchMode = false
        }
      },
      update = { view ->
        view.player = player
        view.descendantFocusability = ViewGroup.FOCUS_BLOCK_DESCENDANTS
        view.resizeMode = videoResize.resizeMode
        applySubtitleAppearance(view, subtitleSize, subtitlePosition, subtitleStyle)
      },
      modifier = Modifier.fillMaxSize(),
    )

    AnimatedVisibility(
      visible = controlsVisible && !settingsOpen && !inPictureInPicture,
      enter = fadeIn(),
      exit = fadeOut(),
    ) {
      ModernPlayerControls(
        player = player,
        request = request,
        isTelevision = isTelevision,
        isPlaying = isVideoPlaying,
        isBuffering = isBuffering,
        status = status,
        selectedQuality = selectedQuality.label,
        selectedAudio = selectedAudio.label,
        selectedSubtitle = selectedSubtitle.label,
        selectedResize = videoResize.label,
        subtitleOffsetMs = subtitleOffsetMs,
        playbackSpeed = playbackSpeed,
        playButtonFocusRequester = playButtonFocusRequester,
        onInteraction = { controlsInteractionVersion++ },
        onBack = onExit,
        onSubtitles = { openSettings(PlayerControlDialog.SUBTITLES) },
        onSubtitleSync = { openSettings(PlayerControlDialog.SUBTITLE_SYNC) },
        onAudio = { openSettings(PlayerControlDialog.AUDIO) },
        onQuality = { openSettings(PlayerControlDialog.QUALITY) },
        onPicture = { openSettings(PlayerControlDialog.PICTURE) },
        onSpeed = { openSettings(PlayerControlDialog.SPEED) },
        onDecoder = { openSettings(PlayerControlDialog.DECODER) },
      )
    }

    // Nothing to offer when the next episode is already starting; the card would only flash.
    if (nextPromptVisible && error == null && !shortForm && !inPictureInPicture) {
      // The prompt is only ever visible with both of these in hand.
      val next = nextEntry
      val playbackContext = request.context
      NextEpisodePrompt(
        label = playbackContext.nextLabel ?: "Next episode",
        secondsRemaining = autoAdvanceSeconds,
        onPlayNow = { onPlayNext(playbackContext.advanceTo(next)) },
        onDismiss = { playbackFinished = false },
        modifier = Modifier.align(Alignment.BottomCenter),
      )
    }

    error?.takeIf { !inPictureInPicture }?.let {
      Column(
        modifier =
          Modifier.align(Alignment.Center).background(DeepSpace.copy(alpha = .94f), RoundedCornerShape(18.dp))
            .padding(horizontal = 34.dp, vertical = 26.dp),
        horizontalAlignment = Alignment.CenterHorizontally,
      ) {
        Text("Stream unavailable", color = SoftWhite, fontWeight = FontWeight.Bold, fontSize = 22.sp)
        Text(it, color = MutedBlue, fontSize = 14.sp, modifier = Modifier.padding(top = 8.dp))
        Text("Press Back to return", color = AuroraMint, fontWeight = FontWeight.Bold, fontSize = 13.sp, modifier = Modifier.padding(top = 14.dp))
      }
    }

    if (canHandOver && controlsVisible) {
      Box(modifier = Modifier.fillMaxSize().padding(horizontal = 22.dp, vertical = 92.dp)) {
        HandoverPill(
          sent = handoverSent,
          onClick = ::handOverToTelevision,
          modifier = Modifier.align(Alignment.TopEnd),
        )
      }
    }

    activeDialog?.let { dialog ->
      PlayerControlDialogOverlay(
        dialog = dialog,
        qualityOptions = qualityOptions,
        selectedQuality = selectedQuality,
        audioOptions = audioOptions,
        selectedAudio = selectedAudio,
        subtitleOptions = subtitleOptions,
        selectedSubtitle = selectedSubtitle,
        subtitleSize = subtitleSize,
        subtitlePosition = subtitlePosition,
        subtitleStyle = subtitleStyle,
        subtitleOffsetMs = subtitleOffsetMs,
        playbackSpeed = playbackSpeed,
        selectedResize = videoResize,
        compatibilityMode = compatibilityMode,
        isCasting = isCasting,
        onQualitySelected = { option ->
          selectedQuality = option
          selectVideoQuality(player, option)
        },
        onAudioSelected = { option ->
          selectedAudio = option
          playerPreferences.setAudioTrack(option)
          selectAudioTrack(player, option)
        },
        onSubtitleSelected = { option ->
          selectedSubtitle = option
          playerPreferences.setSubtitleTrack(option)
          selectSubtitleTrack(player, option)
        },
        onSubtitleSizeSelected = {
          subtitleSize = it
          playerPreferences.setSubtitleSize(it)
        },
        onSubtitlePositionSelected = {
          subtitlePosition = it
          playerPreferences.setSubtitlePosition(it)
        },
        onSubtitleStyleSelected = {
          subtitleStyle = it
          playerPreferences.setSubtitleStyle(it)
        },
        onSubtitleOffsetSelected = { offsetMs ->
          if (offsetMs != subtitleOffsetMs && !isCasting) {
            val currentPositionMs = player.currentPosition.coerceAtLeast(0L)
            val keepPlaying = player.playWhenReady
            subtitleOffsetMs = offsetMs
            subtitleSyncStore.save(subtitleSyncKey, offsetMs)
            resumePositionMs = currentPositionMs
            resumePlayWhenReady = keepPlaying
            localPlayer.setMediaSource(createHlsMediaSource(context, request, offsetMs), currentPositionMs)
            localPlayer.prepare()
            localPlayer.playWhenReady = keepPlaying
          }
        },
        onPlaybackSpeedSelected = { speed ->
          playbackSpeed = speed
          playerPreferences.setPlaybackSpeed(speed)
          player.setPlaybackSpeed(speed)
        },
        onResizeSelected = { videoResize = it },
        onCompatibilityModeSelected = { enabled ->
          if (enabled != compatibilityMode) {
            resumePositionMs = player.currentPosition.coerceAtLeast(0L)
            resumePlayWhenReady = player.playWhenReady
            compatibilityMode = enabled
          }
        },
        onClose = ::closeSettings,
      )
    }
  }
}

@androidx.annotation.OptIn(UnstableApi::class)
@Composable
private fun ModernPlayerControls(
  player: Player,
  request: HlsStreamRequest,
  isTelevision: Boolean,
  isPlaying: Boolean,
  isBuffering: Boolean,
  status: String,
  selectedQuality: String,
  selectedAudio: String,
  selectedSubtitle: String,
  selectedResize: String,
  subtitleOffsetMs: Long,
  playbackSpeed: Float,
  playButtonFocusRequester: FocusRequester,
  onInteraction: () -> Unit,
  onBack: () -> Unit,
  onSubtitles: () -> Unit,
  onSubtitleSync: () -> Unit,
  onAudio: () -> Unit,
  onQuality: () -> Unit,
  onPicture: () -> Unit,
  onSpeed: () -> Unit,
  onDecoder: () -> Unit,
) {
  var positionMs by remember(player) { mutableLongStateOf(player.currentPosition.coerceAtLeast(0L)) }
  var durationMs by remember(player) { mutableLongStateOf(player.duration.takeIf { it != C.TIME_UNSET }?.coerceAtLeast(0L) ?: 0L) }
  var seekPreviewMs by remember(player) { mutableStateOf<Long?>(null) }
  var seekBarFocused by remember { mutableStateOf(false) }
  val surroundingControlsAlpha = if (seekBarFocused) 0f else 1f

  LaunchedEffect(player) {
    while (true) {
      if (seekPreviewMs == null) positionMs = player.currentPosition.coerceAtLeast(0L)
      durationMs = player.duration.takeIf { it != C.TIME_UNSET }?.coerceAtLeast(0L) ?: 0L
      delay(250L)
    }
  }

  val modernColors =
    remember {
      composeDarkColorScheme(
        primary = AuroraMint,
        secondary = AuroraBlue,
        background = Color.Transparent,
        surface = NightSurface,
        onPrimary = DeepSpace,
        onSecondary = DeepSpace,
        onBackground = SoftWhite,
        onSurface = SoftWhite,
      )
    }
  ComposeMaterialTheme(colorScheme = modernColors) {
    BoxWithConstraints(Modifier.fillMaxSize()) {
      val compact = maxWidth < 720.dp
      val horizontalPadding = if (compact) 18.dp else 38.dp
      val centerButtonSize = if (compact) 52.dp else 60.dp
      val playButtonSize = if (compact) 68.dp else 78.dp

      Box(
        Modifier.matchParentSize()
          .background(
            Brush.verticalGradient(
              0f to Color.Black.copy(alpha = .76f),
              .30f to Color.Transparent,
              .58f to Color.Transparent,
              1f to Color.Black.copy(alpha = .92f),
            )
          )
      )

      Row(
        modifier = Modifier.fillMaxWidth().align(Alignment.TopCenter).padding(horizontal = horizontalPadding, vertical = 18.dp),
        verticalAlignment = Alignment.CenterVertically,
        horizontalArrangement = Arrangement.spacedBy(14.dp),
      ) {
        ModernPlayerActionPill(
          icon = Icons.AutoMirrored.Filled.ArrowBack,
          label = "Back",
          value = null,
          onClick = onBack,
          onInteraction = onInteraction,
          modifier = Modifier.alpha(surroundingControlsAlpha),
        )
        Column(Modifier.weight(1f)) {
          Text(
            "NOW PLAYING",
            color = AuroraMint,
            fontWeight = FontWeight.Black,
            fontSize = 11.sp,
          )
          Text(
            playbackTitle(request),
            color = SoftWhite,
            fontWeight = FontWeight.Black,
            fontSize = if (compact) 17.sp else 21.sp,
            maxLines = 1,
            overflow = TextOverflow.Ellipsis,
          )
          playbackSubtitle(request)?.let {
            Text(
              it,
              color = AuroraMint,
              fontWeight = FontWeight.Bold,
              fontSize = if (compact) 11.sp else 13.sp,
              maxLines = 1,
              overflow = TextOverflow.Ellipsis,
            )
          }
        }
        if (isBuffering) {
          CircularProgressIndicator(
            modifier = Modifier.size(28.dp),
            color = AuroraMint,
            strokeWidth = 2.dp,
          )
        }
        if (!compact) ModernPlayerStatusChip(selectedQuality, modifier = Modifier.alpha(surroundingControlsAlpha))
        if (player is CastPlayer) {
          Box(
            modifier =
              Modifier.alpha(surroundingControlsAlpha).size(48.dp).clip(CircleShape)
                .background(SoftWhite.copy(alpha = .13f))
                .border(1.dp, SoftWhite.copy(alpha = .18f), CircleShape),
            contentAlignment = Alignment.Center,
          ) {
            CastRouteButton(Modifier.size(42.dp))
          }
        }
      }

      Row(
        modifier = Modifier.align(Alignment.Center).alpha(surroundingControlsAlpha),
        verticalAlignment = Alignment.CenterVertically,
        horizontalArrangement = Arrangement.spacedBy(if (compact) 20.dp else 30.dp),
      ) {
        ModernTransportControl(
          icon = Icons.Filled.Replay10,
          label = "Back 10 seconds",
          size = centerButtonSize,
          onClick = {
            onInteraction()
            player.seekTo(seekTargetPosition(player.currentPosition, -10_000L, player.duration))
          },
          onInteraction = onInteraction,
        )
        Box(contentAlignment = Alignment.Center) {
          if (isBuffering) {
            CircularProgressIndicator(
              modifier = Modifier.size(playButtonSize + 10.dp),
              color = AuroraMint.copy(alpha = .8f),
              strokeWidth = 2.dp,
            )
          }
          ModernTransportControl(
            icon = if (isPlaying) Icons.Filled.Pause else Icons.Filled.PlayArrow,
            label = if (isPlaying) "Pause" else "Play",
            size = playButtonSize,
            prominent = true,
            modifier = Modifier.focusRequester(playButtonFocusRequester),
            onClick = {
              onInteraction()
              if (isPlaying) {
                player.pause()
              } else {
                if (player.playbackState == Player.STATE_ENDED) player.seekTo(0L)
                player.play()
              }
            },
            onInteraction = onInteraction,
          )
        }
        ModernTransportControl(
          icon = Icons.Filled.Forward10,
          label = "Forward 10 seconds",
          size = centerButtonSize,
          onClick = {
            onInteraction()
            player.seekTo(seekTargetPosition(player.currentPosition, 10_000L, player.duration))
          },
          onInteraction = onInteraction,
        )
      }

      Column(
        modifier =
          Modifier.fillMaxWidth().align(Alignment.BottomCenter)
            .padding(horizontal = horizontalPadding, vertical = if (compact) 14.dp else 24.dp),
      ) {
        if (isTelevision) {
          ModernTvSeekBar(
            positionMs = positionMs,
            durationMs = durationMs,
            onSeek = { targetMs ->
              positionMs = targetMs
              player.seekTo(targetMs)
              onInteraction()
            },
            onScrub = { previewMs -> seekPreviewMs = previewMs },
            onInteraction = onInteraction,
            onFocusChanged = { seekBarFocused = it },
          )
        } else {
          Slider(
            value = (seekPreviewMs ?: positionMs).toFloat().coerceIn(0f, durationMs.coerceAtLeast(1L).toFloat()),
            onValueChange = { value ->
              seekPreviewMs = value.toLong()
              onInteraction()
            },
            onValueChangeFinished = {
              seekPreviewMs?.let(player::seekTo)
              seekPreviewMs = null
              onInteraction()
            },
            valueRange = 0f..durationMs.coerceAtLeast(1L).toFloat(),
            enabled = durationMs > 0L,
            colors =
              SliderDefaults.colors(
                thumbColor = SoftWhite,
                activeTrackColor = AuroraMint,
                inactiveTrackColor = SoftWhite.copy(alpha = .24f),
                disabledThumbColor = MutedBlue,
                disabledActiveTrackColor = MutedBlue.copy(alpha = .5f),
                disabledInactiveTrackColor = SoftWhite.copy(alpha = .14f),
              ),
            modifier = Modifier.fillMaxWidth().height(if (compact) 28.dp else 34.dp),
          )
        }
        Row(
          modifier = Modifier.fillMaxWidth(),
          verticalAlignment = Alignment.CenterVertically,
          horizontalArrangement = Arrangement.spacedBy(10.dp),
        ) {
          Column {
            Text(
              "${formatPlayerTime(seekPreviewMs ?: positionMs)}  /  ${formatPlayerTime(durationMs)}",
              color = SoftWhite,
              fontWeight = FontWeight.Bold,
              fontSize = 12.sp,
            )
            // What the clock will say when the credits roll, and how much of the evening is left in
            // it. Both follow the position being scrubbed to, so dragging the bar answers "will
            // this finish before bed" while the thumb is still down.
            remainingLabel(LocalContext.current, seekPreviewMs ?: positionMs, durationMs)?.let {
              Text(it, color = MutedBlue, fontWeight = FontWeight.Medium, fontSize = 11.sp)
            }
          }
          Spacer(Modifier.weight(1f))
          if (!compact) {
            val hiddenWhileSeekingModifier = Modifier.alpha(surroundingControlsAlpha)
            ModernPlayerActionPill(Icons.AutoMirrored.Filled.VolumeUp, "Audio", selectedAudio, onAudio, onInteraction, showValue = false, modifier = hiddenWhileSeekingModifier)
            ModernPlayerActionPill(Icons.Filled.HighQuality, "Quality", selectedQuality, onQuality, onInteraction, showValue = false, modifier = hiddenWhileSeekingModifier)
            ModernPlayerActionPill(Icons.Filled.ClosedCaption, "Subtitles", selectedSubtitle, onSubtitles, onInteraction, showValue = false, modifier = hiddenWhileSeekingModifier)
            ModernPlayerActionPill(Icons.Filled.AspectRatio, "Picture", selectedResize, onPicture, onInteraction, showValue = false, modifier = hiddenWhileSeekingModifier)
            ModernPlayerActionPill(Icons.Filled.Speed, "Sync", subtitleSyncLabel(subtitleOffsetMs), onSubtitleSync, onInteraction, showValue = false, modifier = hiddenWhileSeekingModifier)
            ModernPlayerActionPill(Icons.Filled.Speed, "Speed", speedLabel(playbackSpeed), onSpeed, onInteraction, showValue = false, modifier = hiddenWhileSeekingModifier)
            ModernPlayerActionPill(Icons.Filled.Memory, "Decoder", null, onDecoder, onInteraction, showValue = false, modifier = hiddenWhileSeekingModifier)
          } else {
            val hiddenWhileSeekingModifier = Modifier.alpha(surroundingControlsAlpha)
            ModernTransportControl(Icons.AutoMirrored.Filled.VolumeUp, "Audio: $selectedAudio", 44.dp, onAudio, onInteraction, modifier = hiddenWhileSeekingModifier)
            ModernTransportControl(Icons.Filled.HighQuality, "Quality: $selectedQuality", 44.dp, onQuality, onInteraction, modifier = hiddenWhileSeekingModifier)
            ModernTransportControl(Icons.Filled.ClosedCaption, "Subtitles: $selectedSubtitle", 44.dp, onSubtitles, onInteraction, modifier = hiddenWhileSeekingModifier)
            ModernTransportControl(Icons.Filled.AspectRatio, "Picture size: $selectedResize", 44.dp, onPicture, onInteraction, modifier = hiddenWhileSeekingModifier)
            ModernTransportControl(Icons.Filled.Speed, "Subtitle sync ${subtitleSyncLabel(subtitleOffsetMs)}", 44.dp, onSubtitleSync, onInteraction, modifier = hiddenWhileSeekingModifier)
            ModernTransportControl(Icons.Filled.Speed, "Playback speed ${speedLabel(playbackSpeed)}", 44.dp, onSpeed, onInteraction, modifier = hiddenWhileSeekingModifier)
            ModernTransportControl(Icons.Filled.Memory, "Decoder", 44.dp, onDecoder, onInteraction, modifier = hiddenWhileSeekingModifier)
          }
        }
      }
    }
  }
}

@Composable
private fun ModernTvSeekBar(
  positionMs: Long,
  durationMs: Long,
  onSeek: (Long) -> Unit,
  onScrub: (Long?) -> Unit,
  onInteraction: () -> Unit,
  onFocusChanged: (Boolean) -> Unit,
) {
  var focused by remember { mutableStateOf(false) }
  var remoteSeekTargetMs by remember { mutableLongStateOf(positionMs) }
  var touchSeekTargetMs by remember { mutableStateOf<Long?>(null) }
  var trackWidthPx by remember { mutableIntStateOf(0) }
  LaunchedEffect(positionMs) { remoteSeekTargetMs = positionMs }
  // Touch gestures outlive individual duration updates, so read the latest value inside them.
  val latestDurationMs by rememberUpdatedState(durationMs)
  val scrubbing = touchSeekTargetMs != null
  val highlighted = focused || scrubbing
  val progress =
    if (durationMs > 0L) {
      ((touchSeekTargetMs ?: remoteSeekTargetMs).toFloat() / durationMs.toFloat()).coerceIn(0f, 1f)
    } else {
      0f
    }
  Column(
    modifier =
      Modifier.fillMaxWidth().height(42.dp)
        .onSizeChanged { trackWidthPx = it.width }
        .onFocusChanged {
          focused = it.isFocused
          onFocusChanged(it.isFocused)
          if (it.isFocused) {
            remoteSeekTargetMs = positionMs
            onInteraction()
          }
        }
        .onPreviewKeyEvent { event ->
          if (event.key == Key.DirectionLeft || event.key == Key.DirectionRight) {
            if (event.type == KeyEventType.KeyDown) {
              val direction = if (event.key == Key.DirectionLeft) -1 else 1
              val deltaMs = remoteSeekDeltaMs(direction, event.nativeKeyEvent.repeatCount)
              remoteSeekTargetMs = seekTargetPosition(remoteSeekTargetMs, deltaMs, durationMs)
              onSeek(remoteSeekTargetMs)
            }
            true
          } else {
            false
          }
        }
        .focusable()
        .pointerInput(Unit) {
          detectHorizontalDragGestures(
            onDragStart = { offset ->
              val duration = latestDurationMs
              if (duration > 0L) {
                val target = touchSeekPositionMs(offset.x, size.width, duration)
                touchSeekTargetMs = target
                onScrub(target)
                onInteraction()
              }
            },
            onHorizontalDrag = { change, _ ->
              val duration = latestDurationMs
              if (duration > 0L) {
                change.consume()
                val target = touchSeekPositionMs(change.position.x, size.width, duration)
                touchSeekTargetMs = target
                onScrub(target)
                onInteraction()
              }
            },
            onDragEnd = {
              touchSeekTargetMs?.let { target ->
                remoteSeekTargetMs = target
                onSeek(target)
              }
              touchSeekTargetMs = null
              onScrub(null)
            },
            onDragCancel = {
              touchSeekTargetMs = null
              onScrub(null)
            },
          )
        }
        .pointerInput(Unit) {
          detectTapGestures { offset ->
            val duration = latestDurationMs
            if (duration > 0L) {
              val target = touchSeekPositionMs(offset.x, size.width, duration)
              remoteSeekTargetMs = target
              onSeek(target)
            }
          }
        }
        .semantics {
          contentDescription =
            "Playback timeline. Tap left or right to seek 10 seconds. Hold to seek faster. " +
              "Touch the timeline to seek, or drag along it to scrub"
        },
    verticalArrangement = Arrangement.Center,
  ) {
    if (highlighted) {
      Text(
        if (scrubbing) "DRAG TO SCRUB     RELEASE TO SEEK"
        else "◀  10s     HOLD TO SEEK FASTER     10s  ▶",
        color = AuroraMint,
        fontWeight = FontWeight.Black,
        fontSize = 10.sp,
        modifier = Modifier.align(Alignment.End).padding(bottom = 3.dp),
      )
    }
    Box(modifier = Modifier.fillMaxWidth().height(16.dp), contentAlignment = Alignment.CenterStart) {
      Box(
        modifier =
          Modifier.fillMaxWidth().height(if (highlighted) 9.dp else 6.dp)
            .clip(RoundedCornerShape(99.dp))
            .background(SoftWhite.copy(alpha = if (highlighted) .32f else .22f)),
      ) {
        Box(
          Modifier.fillMaxWidth(progress).fillMaxHeight()
            .clip(RoundedCornerShape(99.dp))
            .background(if (highlighted) AuroraMint else SoftWhite)
        )
      }
      if (highlighted) {
        val thumbSize = if (scrubbing) 16.dp else 12.dp
        Box(
          modifier =
            Modifier.offset {
                IntOffset(
                  x = (progress * trackWidthPx - thumbSize.toPx() / 2f).roundToInt()
                    .coerceIn(0, (trackWidthPx - thumbSize.roundToPx()).coerceAtLeast(0)),
                  y = 0,
                )
              }
              .size(thumbSize)
              .clip(CircleShape)
              .background(AuroraMint)
        )
      }
    }
  }
}

/** End-of-episode card offering the next episode, with a countdown that plays it automatically. */
@Composable
private fun NextEpisodePrompt(
  label: String,
  secondsRemaining: Int,
  onPlayNow: () -> Unit,
  onDismiss: () -> Unit,
  modifier: Modifier = Modifier,
) {
  val playFocusRequester = remember { FocusRequester() }
  LaunchedEffect(Unit) { playFocusRequester.requestFocus() }
  Column(
    modifier =
      modifier.padding(38.dp).background(DeepSpace.copy(alpha = .95f), RoundedCornerShape(18.dp))
        .border(2.dp, AuroraMint.copy(alpha = .5f), RoundedCornerShape(18.dp))
        .padding(horizontal = 28.dp, vertical = 20.dp)
  ) {
    Text("UP NEXT", color = AuroraMint, fontWeight = FontWeight.Black, letterSpacing = 2.sp, fontSize = 11.sp)
    Spacer(Modifier.height(6.dp))
    Text(
      label,
      color = SoftWhite,
      fontWeight = FontWeight.Bold,
      fontSize = 18.sp,
      maxLines = 1,
      overflow = TextOverflow.Ellipsis,
    )
    Spacer(Modifier.height(14.dp))
    Row(horizontalArrangement = Arrangement.spacedBy(12.dp), verticalAlignment = Alignment.CenterVertically) {
      PlayerPromptButton(
        label = if (secondsRemaining > 0) "Play now  ·  ${secondsRemaining}s" else "Play now",
        primary = true,
        onClick = onPlayNow,
        modifier = Modifier.focusRequester(playFocusRequester),
      )
      PlayerPromptButton(label = "Stay here", primary = false, onClick = onDismiss)
    }
  }
}

@Composable
private fun PlayerPromptButton(
  label: String,
  primary: Boolean,
  onClick: () -> Unit,
  modifier: Modifier = Modifier,
) {
  var focused by remember { mutableStateOf(false) }
  Box(
    modifier =
      modifier.height(46.dp).clip(RoundedCornerShape(12.dp))
        .background(
          when {
            focused -> AuroraMint
            primary -> SoftWhite
            else -> NightSurface
          }
        )
        .border(2.dp, if (focused) SoftWhite else Color.Transparent, RoundedCornerShape(12.dp))
        .onFocusChanged { focused = it.isFocused }
        .focusable()
        .onKeyEvent { event ->
          val selectPressed =
            (event.key == Key.DirectionCenter || event.key == Key.Enter || event.key == Key.NumPadEnter)
          if (event.type == KeyEventType.KeyUp && selectPressed) {
            onClick()
            true
          } else {
            false
          }
        }
        .pointerInput(onClick) { detectTapGestures { onClick() } }
        .semantics { role = Role.Button; contentDescription = label }
        .padding(horizontal = 22.dp),
    contentAlignment = Alignment.Center,
  ) {
    Text(
      label,
      color = if (focused || primary) DeepSpace else SoftWhite,
      fontWeight = FontWeight.Black,
      fontSize = 13.sp,
    )
  }
}

/** Maps a horizontal touch position on the timeline to the playback position it points at. */
internal fun touchSeekPositionMs(x: Float, trackWidthPx: Int, durationMs: Long): Long {
  if (trackWidthPx <= 0 || durationMs <= 0L) return 0L
  val fraction = (x / trackWidthPx.toFloat()).coerceIn(0f, 1f)
  return (fraction * durationMs).toLong().coerceIn(0L, durationMs)
}

internal fun remoteSeekDeltaMs(direction: Int, repeatCount: Int): Long {
  val stepMs =
    when {
      repeatCount >= 18 -> 60_000L
      repeatCount >= 8 -> 30_000L
      else -> 10_000L
    }
  return if (direction < 0) -stepMs else stepMs
}

@Composable
private fun ModernTransportControl(
  icon: ImageVector,
  label: String,
  size: androidx.compose.ui.unit.Dp,
  onClick: () -> Unit,
  onInteraction: () -> Unit,
  modifier: Modifier = Modifier,
  prominent: Boolean = false,
) {
  var focused by remember { mutableStateOf(false) }
  val background =
    when {
      focused -> AuroraMint
      prominent -> SoftWhite
      else -> SoftWhite.copy(alpha = .13f)
    }
  val foreground = if (focused || prominent) DeepSpace else SoftWhite
  Box(
    modifier =
      modifier.size(size).clip(CircleShape).background(background)
        .border(if (focused) 3.dp else 1.dp, if (focused) AuroraBlue else SoftWhite.copy(alpha = .16f), CircleShape)
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
      tint = foreground,
      modifier = Modifier.size(size * if (prominent) .48f else .44f),
    )
  }
}

/** "Play on TV", and afterwards a note that it went. */
@Composable
private fun HandoverPill(sent: Boolean, onClick: () -> Unit, modifier: Modifier = Modifier) {
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
private fun ModernPlayerActionPill(
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
    Text(label, color = if (focused) DeepSpace else SoftWhite, fontWeight = FontWeight.Bold, fontSize = 12.sp)
    value?.takeIf { showValue }?.let {
      Text(it, color = if (focused) DeepSpace.copy(alpha = .7f) else MutedBlue, fontSize = 11.sp)
    }
  }
}

@Composable
private fun ModernPlayerStatusChip(label: String, icon: ImageVector? = null, modifier: Modifier = Modifier) {
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

/**
 * What the viewer is watching, preferring the catalog's own title over anything scraped from the
 * page. Falling back to the host is a last resort for streams found by plain browsing.
 */
private fun playbackTitle(request: HlsStreamRequest): String {
  request.context?.title?.trim()?.takeIf { it.isNotBlank() }?.let { return it }
  val source = request.sourcePageUrl ?: request.url
  request.title?.trim()
    ?.takeIf { it.isNotBlank() && !it.startsWith("http://") && !it.startsWith("https://") && it != source }
    ?.let { return it }
  return source.toUri().host?.removePrefix("www.")?.uppercase(Locale.ENGLISH) ?: "GIZTV PLAYER"
}

/** Season and episode for a show, or the release year for a film. */
private fun playbackSubtitle(request: HlsStreamRequest): String? =
  request.context?.subtitle?.trim()?.takeIf { it.isNotBlank() }

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

private tailrec fun Context.findActivity(): Activity? =
  when (this) {
    is Activity -> this
    is ContextWrapper -> baseContext.findActivity()
    else -> null
  }

@androidx.annotation.OptIn(UnstableApi::class)
@Composable
private fun CastRouteButton(modifier: Modifier = Modifier) {
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
private fun SubtitleSyncMiniOverlay(
  offsetMs: Long,
  isCasting: Boolean,
  onOffsetSelected: (Long) -> Unit,
  onClose: () -> Unit,
) {
  val firstChoiceFocus = remember { FocusRequester() }
  LaunchedEffect(Unit) {
    delay(100L)
    runCatching { firstChoiceFocus.requestFocus() }
  }
  BoxWithConstraints(Modifier.fillMaxSize(), contentAlignment = Alignment.TopCenter) {
    val panelWidth = minOf(maxWidth - 32.dp, 900.dp)
    Column(
      modifier =
        Modifier.width(panelWidth).padding(top = 22.dp)
          .clip(RoundedCornerShape(24.dp))
          .background(NightSurface.copy(alpha = .94f))
          .border(1.dp, SoftWhite.copy(alpha = .18f), RoundedCornerShape(24.dp))
          .padding(horizontal = 18.dp, vertical = 14.dp),
      verticalArrangement = Arrangement.spacedBy(10.dp),
    ) {
      Row(Modifier.fillMaxWidth(), verticalAlignment = Alignment.CenterVertically) {
        Column(Modifier.weight(1f)) {
          Text("Subtitle sync", color = SoftWhite, fontWeight = FontWeight.Black, fontSize = 20.sp)
          Text("Watch the spoken line and adjust while captions stay visible", color = AuroraMint, fontSize = 11.sp)
        }
        ModernTransportControl(
          icon = Icons.Filled.Close,
          label = "Close subtitle sync",
          size = 40.dp,
          onClick = onClose,
          onInteraction = {},
        )
      }
      SubtitleSyncControls(
        offsetMs = offsetMs,
        isCasting = isCasting,
        onOffsetSelected = onOffsetSelected,
        firstChoiceModifier = Modifier.focusRequester(firstChoiceFocus),
      )
    }
  }
}

@Composable
private fun PlayerControlDialogOverlay(
  dialog: PlayerControlDialog,
  qualityOptions: List<VideoQualityOption>,
  selectedQuality: VideoQualityOption,
  audioOptions: List<AudioTrackOption>,
  selectedAudio: AudioTrackOption,
  subtitleOptions: List<SubtitleTrackOption>,
  selectedSubtitle: SubtitleTrackOption,
  subtitleSize: SubtitleSizeOption,
  subtitlePosition: SubtitlePositionOption,
  subtitleStyle: SubtitleStyleOption,
  subtitleOffsetMs: Long,
  playbackSpeed: Float,
  selectedResize: VideoResizeOption,
  compatibilityMode: Boolean,
  isCasting: Boolean,
  onQualitySelected: (VideoQualityOption) -> Unit,
  onAudioSelected: (AudioTrackOption) -> Unit,
  onSubtitleSelected: (SubtitleTrackOption) -> Unit,
  onSubtitleSizeSelected: (SubtitleSizeOption) -> Unit,
  onSubtitlePositionSelected: (SubtitlePositionOption) -> Unit,
  onSubtitleStyleSelected: (SubtitleStyleOption) -> Unit,
  onSubtitleOffsetSelected: (Long) -> Unit,
  onPlaybackSpeedSelected: (Float) -> Unit,
  onResizeSelected: (VideoResizeOption) -> Unit,
  onCompatibilityModeSelected: (Boolean) -> Unit,
  onClose: () -> Unit,
) {
  if (dialog == PlayerControlDialog.SUBTITLE_SYNC) {
    SubtitleSyncMiniOverlay(
      offsetMs = subtitleOffsetMs,
      isCasting = isCasting,
      onOffsetSelected = onSubtitleOffsetSelected,
      onClose = onClose,
    )
    return
  }

  val firstChoiceFocus = remember(dialog) { FocusRequester() }
  val playbackSpeeds = listOf(.75f, 1f, 1.25f, 1.5f)
  val title =
    when (dialog) {
      PlayerControlDialog.SUBTITLES -> "Subtitles"
      PlayerControlDialog.SUBTITLE_SYNC -> "Subtitle sync"
      PlayerControlDialog.AUDIO -> "Audio track"
      PlayerControlDialog.QUALITY -> "Video quality"
      PlayerControlDialog.PICTURE -> "Picture size"
      PlayerControlDialog.SPEED -> "Playback speed"
      PlayerControlDialog.DECODER -> "TV compatibility"
    }
  val description =
    when (dialog) {
      PlayerControlDialog.SUBTITLES -> "Choose any detected subtitle track and its appearance"
      PlayerControlDialog.SUBTITLE_SYNC -> "Match captions to the spoken line"
      PlayerControlDialog.AUDIO -> "Choose the language or audio mix"
      PlayerControlDialog.QUALITY -> "Automatic adapts to your connection"
      PlayerControlDialog.PICTURE -> "Fit shows the complete picture without cropping"
      PlayerControlDialog.SPEED -> "Change how quickly the video plays"
      PlayerControlDialog.DECODER -> "Use compatibility mode only when video decoding fails"
    }

  LaunchedEffect(dialog) {
    delay(100L)
    runCatching { firstChoiceFocus.requestFocus() }
  }

  BoxWithConstraints(
    modifier = Modifier.fillMaxSize().background(Color.Black.copy(alpha = .46f)),
    contentAlignment = Alignment.Center,
  ) {
    val panelWidth = minOf(maxWidth - 32.dp, 580.dp)
    val panelHeight = maxHeight * .84f
    Column(
      modifier =
        Modifier.width(panelWidth).heightIn(max = panelHeight)
          .clip(RoundedCornerShape(28.dp))
          .background(NightSurface.copy(alpha = .97f))
          .border(1.dp, SoftWhite.copy(alpha = .18f), RoundedCornerShape(28.dp))
          .padding(horizontal = 22.dp, vertical = 20.dp),
      verticalArrangement = Arrangement.spacedBy(16.dp),
    ) {
      Row(Modifier.fillMaxWidth(), verticalAlignment = Alignment.CenterVertically) {
        Column(Modifier.weight(1f)) {
          Text(title, color = SoftWhite, fontWeight = FontWeight.Black, fontSize = 24.sp)
          Text(description, color = AuroraMint, fontSize = 12.sp)
        }
        ModernTransportControl(
          icon = Icons.Filled.Close,
          label = "Close $title",
          size = 42.dp,
          onClick = onClose,
          onInteraction = {},
        )
      }

      Column(
        modifier = Modifier.fillMaxWidth().verticalScroll(rememberScrollState()),
        verticalArrangement = Arrangement.spacedBy(12.dp),
      ) {
        when (dialog) {
          PlayerControlDialog.SUBTITLES -> {
            DialogOptionList(
              options = subtitleOptions.map { it.label },
              selected = selectedSubtitle.label,
              onSelected = { label -> subtitleOptions.firstOrNull { it.label == label }?.let(onSubtitleSelected) },
              firstChoiceModifier = Modifier.focusRequester(firstChoiceFocus),
            )
            DialogChoiceSection(
              title = "Size",
              options = SubtitleSizeOption.entries.map { it.label },
              selected = subtitleSize.label,
              onSelected = { label -> SubtitleSizeOption.entries.firstOrNull { it.label == label }?.let(onSubtitleSizeSelected) },
            )
            DialogChoiceSection(
              title = "Position",
              options = SubtitlePositionOption.entries.map { it.label },
              selected = subtitlePosition.label,
              onSelected = { label -> SubtitlePositionOption.entries.firstOrNull { it.label == label }?.let(onSubtitlePositionSelected) },
            )
            DialogChoiceSection(
              title = "Style",
              options = SubtitleStyleOption.entries.map { it.label },
              selected = subtitleStyle.label,
              onSelected = { label -> SubtitleStyleOption.entries.firstOrNull { it.label == label }?.let(onSubtitleStyleSelected) },
            )
          }
          PlayerControlDialog.AUDIO ->
            DialogOptionList(
              options = audioOptions.map { it.label },
              selected = selectedAudio.label,
              onSelected = { label -> audioOptions.firstOrNull { it.label == label }?.let(onAudioSelected) },
              firstChoiceModifier = Modifier.focusRequester(firstChoiceFocus),
            )
          PlayerControlDialog.QUALITY ->
            DialogOptionList(
              options = qualityOptions.map { it.label },
              selected = selectedQuality.label,
              onSelected = { label -> qualityOptions.firstOrNull { it.label == label }?.let(onQualitySelected) },
              firstChoiceModifier = Modifier.focusRequester(firstChoiceFocus),
            )
          PlayerControlDialog.PICTURE ->
            DialogOptionList(
              options = VideoResizeOption.entries.map { it.label },
              selected = selectedResize.label,
              onSelected = { label -> VideoResizeOption.entries.firstOrNull { it.label == label }?.let(onResizeSelected) },
              firstChoiceModifier = Modifier.focusRequester(firstChoiceFocus),
            )
          PlayerControlDialog.SPEED ->
            DialogOptionList(
              options = playbackSpeeds.map(::speedLabel),
              selected = speedLabel(playbackSpeed),
              onSelected = { label -> playbackSpeeds.firstOrNull { speedLabel(it) == label }?.let(onPlaybackSpeedSelected) },
              firstChoiceModifier = Modifier.focusRequester(firstChoiceFocus),
            )
          PlayerControlDialog.DECODER ->
            DialogOptionList(
              options = listOf("Automatic", "TV compatible"),
              selected = if (compatibilityMode) "TV compatible" else "Automatic",
              onSelected = { onCompatibilityModeSelected(it == "TV compatible") },
              firstChoiceModifier = Modifier.focusRequester(firstChoiceFocus),
            )
          PlayerControlDialog.SUBTITLE_SYNC -> Unit
        }
      }
    }
  }
}

@Composable
private fun DialogOptionList(
  options: List<String>,
  selected: String,
  onSelected: (String) -> Unit,
  firstChoiceModifier: Modifier,
) {
  Column(Modifier.fillMaxWidth(), verticalArrangement = Arrangement.spacedBy(8.dp)) {
    options.forEachIndexed { index, option ->
      DialogOption(
        label = option,
        selected = option == selected,
        onClick = { onSelected(option) },
        modifier = if (index == 0) firstChoiceModifier else Modifier,
      )
    }
  }
}

@Composable
private fun DialogOption(
  label: String,
  selected: Boolean,
  onClick: () -> Unit,
  modifier: Modifier = Modifier,
) {
  var focused by remember { mutableStateOf(false) }
  val background =
    when {
      focused -> SoftWhite
      selected -> AuroraMint.copy(alpha = .94f)
      else -> DeepSpace.copy(alpha = .72f)
    }
  val foreground = if (focused || selected) DeepSpace else SoftWhite
  Row(
    modifier =
      modifier.fillMaxWidth().height(52.dp).clip(RoundedCornerShape(15.dp)).background(background)
        .border(2.dp, if (focused) AuroraBlue else SoftWhite.copy(alpha = .10f), RoundedCornerShape(15.dp))
        .onFocusChanged { focused = it.isFocused }
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
          role = Role.RadioButton
          this.selected = selected
          semanticsOnClick {
            onClick()
            true
          }
        }
        .padding(horizontal = 16.dp),
    verticalAlignment = Alignment.CenterVertically,
  ) {
    Text(label, color = foreground, fontWeight = FontWeight.Bold, fontSize = 14.sp, modifier = Modifier.weight(1f))
    if (selected) Icon(Icons.Filled.Check, contentDescription = "Selected", tint = DeepSpace, modifier = Modifier.size(20.dp))
  }
}

@Composable
private fun DialogChoiceSection(
  title: String,
  options: List<String>,
  selected: String,
  onSelected: (String) -> Unit,
) {
  Column(
    modifier =
      Modifier.fillMaxWidth().clip(RoundedCornerShape(16.dp))
        .background(DeepSpace.copy(alpha = .62f)).padding(horizontal = 14.dp, vertical = 12.dp),
    verticalArrangement = Arrangement.spacedBy(9.dp),
  ) {
    Text(title.uppercase(Locale.ENGLISH), color = AuroraMint, fontWeight = FontWeight.Black, fontSize = 11.sp)
    Row(
      modifier = Modifier.fillMaxWidth().horizontalScroll(rememberScrollState()),
      horizontalArrangement = Arrangement.spacedBy(8.dp),
    ) {
      options.forEach { option ->
        SettingsChoiceChip(label = option, selected = option == selected, onClick = { onSelected(option) })
      }
    }
  }
}

@Composable
private fun PlayerSettingsPanel(
  qualityOptions: List<VideoQualityOption>,
  selectedQuality: VideoQualityOption,
  audioOptions: List<AudioTrackOption>,
  selectedAudio: AudioTrackOption,
  subtitleSize: SubtitleSizeOption,
  subtitlePosition: SubtitlePositionOption,
  subtitleStyle: SubtitleStyleOption,
  subtitleOffsetMs: Long,
  playbackSpeed: Float,
  compatibilityMode: Boolean,
  isCasting: Boolean,
  onQualitySelected: (VideoQualityOption) -> Unit,
  onAudioSelected: (AudioTrackOption) -> Unit,
  onSubtitleSizeSelected: (SubtitleSizeOption) -> Unit,
  onSubtitlePositionSelected: (SubtitlePositionOption) -> Unit,
  onSubtitleStyleSelected: (SubtitleStyleOption) -> Unit,
  onSubtitleOffsetSelected: (Long) -> Unit,
  onPlaybackSpeedSelected: (Float) -> Unit,
  onCompatibilityModeSelected: (Boolean) -> Unit,
  onClose: () -> Unit,
) {
  val firstChoiceFocus = remember { FocusRequester() }
  val playbackSpeeds = listOf(.75f, 1f, 1.25f, 1.5f)
  LaunchedEffect(Unit) {
    delay(100)
    firstChoiceFocus.requestFocus()
  }

  BoxWithConstraints(
    modifier = Modifier.fillMaxSize(),
    contentAlignment = Alignment.CenterEnd,
  ) {
    val panelWidth = minOf(maxWidth * if (maxWidth < 700.dp) .58f else .44f, 460.dp)
    Column(
      modifier =
        Modifier.width(panelWidth).fillMaxHeight().padding(vertical = 10.dp, horizontal = 10.dp)
          .verticalScroll(rememberScrollState()).focusGroup().clip(RoundedCornerShape(22.dp))
          .background(NightSurface.copy(alpha = .92f))
          .border(1.dp, SoftWhite.copy(alpha = .16f), RoundedCornerShape(22.dp))
          .padding(horizontal = 18.dp, vertical = 16.dp),
      verticalArrangement = Arrangement.spacedBy(10.dp),
    ) {
      Row(Modifier.fillMaxWidth(), verticalAlignment = Alignment.CenterVertically) {
        Column {
          Text("Player settings", color = SoftWhite, fontWeight = FontWeight.Black, fontSize = 22.sp)
          Text("Video keeps playing while you adjust", color = AuroraMint, fontSize = 11.sp)
        }
        Spacer(Modifier.weight(1f))
        SettingsChoiceChip(label = "Close", selected = false, onClick = onClose)
      }

      SubtitleSyncControls(
        offsetMs = subtitleOffsetMs,
        isCasting = isCasting,
        onOffsetSelected = onSubtitleOffsetSelected,
        firstChoiceModifier = Modifier.focusRequester(firstChoiceFocus),
      )
      SettingsRow(
        title = "Subtitle size",
        options = SubtitleSizeOption.entries.map { it.label },
        selected = subtitleSize.label,
        onSelected = { label -> SubtitleSizeOption.entries.firstOrNull { it.label == label }?.let(onSubtitleSizeSelected) },
      )
      SettingsRow(
        title = "Subtitle position",
        options = SubtitlePositionOption.entries.map { it.label },
        selected = subtitlePosition.label,
        onSelected = { label -> SubtitlePositionOption.entries.firstOrNull { it.label == label }?.let(onSubtitlePositionSelected) },
      )
      SettingsRow(
        title = "Subtitle style",
        options = SubtitleStyleOption.entries.map { it.label },
        selected = subtitleStyle.label,
        onSelected = { label -> SubtitleStyleOption.entries.firstOrNull { it.label == label }?.let(onSubtitleStyleSelected) },
      )
      SettingsRow(
        title = "Audio track",
        options = audioOptions.map { it.label },
        selected = selectedAudio.label,
        onSelected = { label -> audioOptions.firstOrNull { it.label == label }?.let(onAudioSelected) },
      )
      SettingsRow(
        title = "Quality",
        options = qualityOptions.map { it.label },
        selected = selectedQuality.label,
        onSelected = { label -> qualityOptions.firstOrNull { it.label == label }?.let(onQualitySelected) },
      )
      SettingsRow(
        title = "Playback speed",
        options = playbackSpeeds.map(::speedLabel),
        selected = speedLabel(playbackSpeed),
        onSelected = { label -> playbackSpeeds.firstOrNull { speedLabel(it) == label }?.let(onPlaybackSpeedSelected) },
      )
      SettingsRow(
        title = "Decoder",
        options = listOf("Automatic", "TV compatible"),
        selected = if (compatibilityMode) "TV compatible" else "Automatic",
        onSelected = { onCompatibilityModeSelected(it == "TV compatible") },
      )

      Text(
        "English audio is preferred automatically. Use the sync buttons while watching a spoken line: minus shows captions earlier, plus shows them later. Chromecast uses source timing.",
        color = MutedBlue,
        fontSize = 11.sp,
      )
    }
  }
}

@Composable
private fun SubtitleSyncControls(
  offsetMs: Long,
  isCasting: Boolean,
  onOffsetSelected: (Long) -> Unit,
  firstChoiceModifier: Modifier = Modifier,
) {
  Column(
    modifier =
      Modifier.fillMaxWidth().clip(RoundedCornerShape(14.dp))
        .background(DeepSpace.copy(alpha = .72f)).padding(horizontal = 12.dp, vertical = 10.dp)
  ) {
    Row(Modifier.fillMaxWidth(), verticalAlignment = Alignment.CenterVertically) {
      Column {
        Text("SUBTITLE SYNC", color = AuroraMint, fontWeight = FontWeight.Black, fontSize = 12.sp)
        Text(
          if (isCasting) "Source timing is used while casting" else subtitleSyncDescription(offsetMs),
          color = SoftWhite,
          fontSize = 11.sp,
        )
      }
      Spacer(Modifier.weight(1f))
      Text(if (isCasting) "CAST" else subtitleSyncLabel(offsetMs), color = AuroraMint, fontWeight = FontWeight.Black, fontSize = 15.sp)
    }
    if (!isCasting) {
      Spacer(Modifier.height(8.dp))
      Row(
        modifier = Modifier.fillMaxWidth().horizontalScroll(rememberScrollState()),
        horizontalArrangement = Arrangement.spacedBy(7.dp),
      ) {
        SettingsChoiceChip(
          label = "−0.5s",
          selected = false,
          onClick = { onOffsetSelected(adjustSubtitleSync(offsetMs, -500L)) },
          modifier = firstChoiceModifier,
        )
        SettingsChoiceChip(
          label = "−0.1s",
          selected = false,
          onClick = { onOffsetSelected(adjustSubtitleSync(offsetMs, -100L)) },
        )
        SettingsChoiceChip(label = "Reset", selected = offsetMs == 0L, onClick = { onOffsetSelected(0L) })
        SettingsChoiceChip(
          label = "+0.1s",
          selected = false,
          onClick = { onOffsetSelected(adjustSubtitleSync(offsetMs, 100L)) },
        )
        SettingsChoiceChip(
          label = "+0.5s",
          selected = false,
          onClick = { onOffsetSelected(adjustSubtitleSync(offsetMs, 500L)) },
        )
      }
    }
  }
}

@Composable
private fun SettingsRow(
  title: String,
  options: List<String>,
  selected: String,
  onSelected: (String) -> Unit,
  firstChoiceModifier: Modifier = Modifier,
) {
  Row(Modifier.fillMaxWidth().height(42.dp), verticalAlignment = Alignment.CenterVertically) {
    Text(title, color = SoftWhite, fontWeight = FontWeight.Bold, fontSize = 13.sp, modifier = Modifier.width(138.dp))
    Row(
      modifier = Modifier.weight(1f).horizontalScroll(rememberScrollState()),
      horizontalArrangement = Arrangement.spacedBy(8.dp),
      verticalAlignment = Alignment.CenterVertically,
    ) {
      options.forEachIndexed { index, option ->
        SettingsChoiceChip(
          label = option,
          selected = option == selected,
          onClick = { onSelected(option) },
          modifier = if (index == 0) firstChoiceModifier else Modifier,
        )
      }
    }
  }
}

@Composable
private fun SettingsChoiceChip(
  label: String,
  selected: Boolean,
  onClick: () -> Unit,
  modifier: Modifier = Modifier,
) {
  var focused by remember { mutableStateOf(false) }
  val background =
    when {
      focused -> SoftWhite
      selected -> AuroraMint
      else -> DeepSpace
    }
  val foreground = if (focused || selected) DeepSpace else SoftWhite
  Box(
    modifier =
      modifier.height(36.dp).clip(RoundedCornerShape(9.dp)).background(background)
        .border(2.dp, if (focused) AuroraBlue else Color.Transparent, RoundedCornerShape(9.dp))
        .onFocusChanged { focused = it.isFocused }
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
        }.focusable().pointerInput(onClick) { detectTapGestures { onClick() } }
        .semantics {
          role = Role.RadioButton
          this.selected = selected
          semanticsOnClick {
            onClick()
            true
          }
        }.padding(horizontal = 13.dp),
    contentAlignment = Alignment.Center,
  ) {
    Text(label, color = foreground, fontWeight = FontWeight.Bold, fontSize = 12.sp)
  }
}

private fun speedLabel(speed: Float): String = if (speed == 1f) "1×" else "${speed}×"

internal fun subtitleSyncLabel(offsetMs: Long): String =
  when {
    offsetMs == 0L -> "0s"
    offsetMs > 0L -> "+${offsetMs / 1_000f}s"
    else -> "${offsetMs / 1_000f}s"
  }

internal fun subtitleSyncDescription(offsetMs: Long): String =
  when {
    offsetMs == 0L -> "Captions use the source timing"
    offsetMs < 0L -> "Captions appear ${subtitleSyncMagnitudeLabel(offsetMs)} earlier"
    else -> "Captions appear ${subtitleSyncMagnitudeLabel(offsetMs)} later"
  }

private fun subtitleSyncMagnitudeLabel(offsetMs: Long): String =
  "${kotlin.math.abs(offsetMs) / 1_000f}s"

internal fun adjustSubtitleSync(offsetMs: Long, deltaMs: Long): Long =
  (offsetMs + deltaMs).coerceIn(-10_000L, 10_000L)

@androidx.annotation.OptIn(UnstableApi::class)
internal fun videoQualityOptions(tracks: Tracks, compatibilityMode: Boolean): List<VideoQualityOption> {
  val formats =
    tracks.groups
      .filter { it.type == C.TRACK_TYPE_VIDEO }
      .flatMap { group ->
        (0 until group.length)
          .filter(group::isTrackSupported)
          .map(group::getTrackFormat)
      }
      .filter { it.width > 0 && it.height > 0 }
      .filter {
        !compatibilityMode ||
          (it.width <= COMPATIBILITY_MAX_VIDEO_WIDTH && it.height <= COMPATIBILITY_MAX_VIDEO_HEIGHT)
      }
      .sortedWith(compareByDescending<Format> { it.width * it.height }.thenByDescending { it.bitrate })
  val fixedOptions =
    formats
      .map { VideoQualityOption(qualityLabel(it.width, it.height), it.width, it.height) }
      .distinctBy { it.label }
  return listOf(VideoQualityOption("Auto")) + fixedOptions
}

private fun qualityLabel(width: Int, height: Int): String =
  when {
    width >= 3800 -> "4K"
    width >= 2500 -> "1440p"
    width >= 1900 -> "1080p"
    width >= 1250 -> "720p"
    width >= 840 -> "480p"
    width >= 620 -> "360p"
    else -> "${height}p"
  }

@androidx.annotation.OptIn(UnstableApi::class)
internal fun selectVideoQuality(player: Player, option: VideoQualityOption) {
  val parameters = player.trackSelectionParameters.buildUpon().clearOverridesOfType(C.TRACK_TYPE_VIDEO)
  if (!option.isAuto) {
    val candidate =
      player.currentTracks.groups
        .filter { it.type == C.TRACK_TYPE_VIDEO }
        .flatMap { group ->
          (0 until group.length)
            .filter(group::isTrackSupported)
            .map { index -> Triple(group, index, group.getTrackFormat(index)) }
        }
        .filter { (_, _, format) -> format.width == option.width && format.height == option.height }
        .maxByOrNull { (_, _, format) -> format.bitrate }
    candidate?.let { (group, index, _) ->
      parameters.setOverrideForType(TrackSelectionOverride(group.mediaTrackGroup, index))
    }
  }
  val updatedParameters = parameters.build()
  if (updatedParameters != player.trackSelectionParameters) {
    player.trackSelectionParameters = updatedParameters
  }
}

@androidx.annotation.OptIn(UnstableApi::class)
internal fun audioTrackOptions(tracks: Tracks): List<AudioTrackOption> {
  val usedLabels = mutableMapOf<String, Int>()
  val explicitOptions =
    tracks.groups
      .filter { it.type == C.TRACK_TYPE_AUDIO }
      .flatMap { group ->
        (0 until group.length)
          .filter(group::isTrackSupported)
          .map { index ->
            val format = group.getTrackFormat(index)
            val baseLabel = audioTrackLabel(format, index)
            val occurrence = (usedLabels[baseLabel] ?: 0) + 1
            usedLabels[baseLabel] = occurrence
            AudioTrackOption(
              label = if (occurrence == 1) baseLabel else "$baseLabel $occurrence",
              override = TrackSelectionOverride(group.mediaTrackGroup, index),
            )
          }
      }
      .sortedWith(
        compareBy<AudioTrackOption>(
          { if (it.label.contains("English", ignoreCase = true)) 0 else 1 },
          { it.label.lowercase() },
        )
      )
  return listOf(AudioTrackOption("Auto English")) + explicitOptions
}

private fun audioTrackLabel(format: Format, index: Int): String {
  val languageName =
    format.language
      ?.takeIf { it.isNotBlank() && it != "und" }
      ?.let { language ->
        when (language.lowercase()) {
          "en", "eng" -> "English"
          "hi", "hin" -> "Hindi"
          else -> Locale.forLanguageTag(language).getDisplayLanguage(Locale.ENGLISH).takeIf(String::isNotBlank)
        }
      }
  val name = format.label?.takeIf(String::isNotBlank) ?: languageName ?: "Audio ${index + 1}"
  val channelSuffix = if (format.channelCount >= 6 && !name.contains("5.1")) " · 5.1" else ""
  return name + channelSuffix
}

@androidx.annotation.OptIn(UnstableApi::class)
internal fun selectAudioTrack(player: Player, option: AudioTrackOption) {
  val builder = player.trackSelectionParameters.buildUpon().clearOverridesOfType(C.TRACK_TYPE_AUDIO)
  val audioOverride = option.override ?: preferredEnglishAudioOverride(player.currentTracks)
  audioOverride?.let(builder::setOverrideForType)
  val updatedParameters = builder.build()
  if (updatedParameters != player.trackSelectionParameters) {
    player.trackSelectionParameters = updatedParameters
  }
}

@androidx.annotation.OptIn(UnstableApi::class)
internal fun subtitleTrackOptions(tracks: Tracks): List<SubtitleTrackOption> {
  val usedLabels = mutableMapOf<String, Int>()
  val explicitOptions =
    tracks.groups
      .filter { it.type == C.TRACK_TYPE_TEXT }
      .flatMap { group ->
        (0 until group.length)
          .filter(group::isTrackSupported)
          .map { index ->
            val format = group.getTrackFormat(index)
            val baseLabel =
              format.label?.takeIf(String::isNotBlank)
                ?: format.language?.takeIf { it.isNotBlank() && it != "und" }
                  ?.let { Locale.forLanguageTag(it).getDisplayLanguage(Locale.ENGLISH) }
                ?: "Subtitle ${index + 1}"
            val occurrence = (usedLabels[baseLabel] ?: 0) + 1
            usedLabels[baseLabel] = occurrence
            SubtitleTrackOption(
              label = if (occurrence == 1) baseLabel else "$baseLabel $occurrence",
              override = TrackSelectionOverride(group.mediaTrackGroup, index),
            )
          }
      }
      .sortedWith(
        compareBy<SubtitleTrackOption>(
          { if (it.label.contains("English", ignoreCase = true)) 0 else 1 },
          { it.label.lowercase() },
        )
      )
  return listOf(
    SubtitleTrackOption("Auto English"),
    SubtitleTrackOption("Off", disabled = true),
  ) + explicitOptions
}

@androidx.annotation.OptIn(UnstableApi::class)
internal fun selectSubtitleTrack(player: Player, option: SubtitleTrackOption) {
  val builder =
    player.trackSelectionParameters.buildUpon()
      .clearOverridesOfType(C.TRACK_TYPE_TEXT)
      .setTrackTypeDisabled(C.TRACK_TYPE_TEXT, option.disabled)
  if (!option.disabled) {
    option.override?.let(builder::setOverrideForType)
    if (option.override == null) {
      builder
        .setPreferredTextLanguages("en", "eng")
        .setSelectUndeterminedTextLanguage(true)
    }
  }
  val updatedParameters = builder.build()
  if (updatedParameters != player.trackSelectionParameters) {
    player.trackSelectionParameters = updatedParameters
  }
}

@androidx.annotation.OptIn(UnstableApi::class)
private fun preferredEnglishAudioOverride(tracks: Tracks): TrackSelectionOverride? {
  val candidate =
    tracks.groups
      .filter { it.type == C.TRACK_TYPE_AUDIO }
      .flatMap { group ->
        (0 until group.length)
          .filter(group::isTrackSupported)
          .map { index -> Triple(group, index, group.getTrackFormat(index)) }
      }
      .firstOrNull { (_, _, format) ->
        format.language?.lowercase() in setOf("en", "eng") ||
          format.label?.contains("english", ignoreCase = true) == true
      }
  return candidate?.let { (group, index, _) -> TrackSelectionOverride(group.mediaTrackGroup, index) }
}

@androidx.annotation.OptIn(UnstableApi::class)
internal fun applySubtitleAppearance(
  playerView: PlayerView,
  size: SubtitleSizeOption,
  position: SubtitlePositionOption,
  style: SubtitleStyleOption,
) {
  val captionStyle =
    when (style) {
      SubtitleStyleOption.OUTLINE ->
        CaptionStyleCompat(
          AndroidColor.WHITE,
          AndroidColor.TRANSPARENT,
          AndroidColor.TRANSPARENT,
          CaptionStyleCompat.EDGE_TYPE_OUTLINE,
          AndroidColor.BLACK,
          null,
        )
      SubtitleStyleOption.DARK_BOX ->
        CaptionStyleCompat(
          AndroidColor.WHITE,
          0xB3000000.toInt(),
          AndroidColor.TRANSPARENT,
          CaptionStyleCompat.EDGE_TYPE_NONE,
          AndroidColor.BLACK,
          null,
        )
    }
  playerView.subtitleView?.apply {
    setViewType(SubtitleView.VIEW_TYPE_CANVAS)
    setApplyEmbeddedFontSizes(false)
    setFractionalTextSize(SubtitleView.DEFAULT_TEXT_SIZE_FRACTION * size.scale)
    setBottomPaddingFraction(position.bottomPadding)
    setStyle(captionStyle)
  }
}

@androidx.annotation.OptIn(UnstableApi::class)
internal fun createCastAwarePlayer(
  context: android.content.Context,
  localPlayer: ExoPlayer,
  isTelevision: Boolean,
): Player {
  if (isTelevision) return localPlayer
  var remotePlayer: RemoteCastPlayer? = null
  return try {
    remotePlayer =
      RemoteCastPlayer.Builder(context.applicationContext)
        .setMediaItemConverter(CastSubtitleMediaItemConverter())
        .build()
    CastPlayer.Builder(context.applicationContext)
      .setLocalPlayer(localPlayer)
      .setRemotePlayer(remotePlayer)
      .build()
  } catch (error: RuntimeException) {
    remotePlayer?.release()
    android.util.Log.w("GizTvCast", "Chromecast is unavailable on this phone", error)
    localPlayer
  }
}

@androidx.annotation.OptIn(UnstableApi::class)
internal fun createHlsPlayer(
  context: android.content.Context,
  request: HlsStreamRequest,
  compatibilityMode: Boolean = false,
  subtitleOffsetMs: Long = 0L,
): ExoPlayer {
  val bandwidthMeter = DefaultBandwidthMeter.getSingletonInstance(context)
  val trackSelector =
    DefaultTrackSelector(
      context,
      AdaptiveTrackSelection.Factory(
        AUTO_QUALITY_INCREASE_BUFFER_MS,
        AUTO_QUALITY_DECREASE_BUFFER_MS,
        AUTO_QUALITY_RETAIN_BUFFER_MS,
        AUTO_QUALITY_BANDWIDTH_FRACTION,
      ),
    )
  val mediaSource = createHlsMediaSource(context, request, subtitleOffsetMs)
  val loadControl =
    DefaultLoadControl.Builder()
      .setBufferDurationsMs(
        15_000,
        60_000,
        2_000,
        4_000,
      )
      .setPrioritizeTimeOverSizeThresholds(true)
      .build()
  val renderersFactory =
    DefaultRenderersFactory(context)
      .setEnableDecoderFallback(true)
      .apply {
        if (compatibilityMode) setMediaCodecSelector(MediaCodecSelector.PREFER_SOFTWARE)
      }

  return ExoPlayer.Builder(context, renderersFactory)
    .setLoadControl(loadControl)
    .setBandwidthMeter(bandwidthMeter)
    .setTrackSelector(trackSelector)
    .build()
    .apply {
      setHandleAudioBecomingNoisy(true)
      val selectionBuilder =
        trackSelectionParameters
          .buildUpon()
          .setPreferredAudioLanguages("en", "eng")
          .setTrackTypeDisabled(C.TRACK_TYPE_TEXT, false)
          .setPreferredTextLanguages("en", "eng")
          .setSelectUndeterminedTextLanguage(true)
      if (compatibilityMode) {
        selectionBuilder
          .setMaxVideoSize(COMPATIBILITY_MAX_VIDEO_WIDTH, COMPATIBILITY_MAX_VIDEO_HEIGHT)
          .setMaxVideoBitrate(COMPATIBILITY_MAX_VIDEO_BITRATE)
      } else {
        selectionBuilder.setMaxVideoSize(STARTUP_MAX_VIDEO_WIDTH, STARTUP_MAX_VIDEO_HEIGHT)
      }
      trackSelectionParameters = selectionBuilder.build()
      setMediaSource(mediaSource)
    }
}

@androidx.annotation.OptIn(UnstableApi::class)
internal fun createHlsMediaSource(
  context: android.content.Context,
  request: HlsStreamRequest,
  subtitleOffsetMs: Long = 0L,
): MediaSource {
  val bandwidthMeter = DefaultBandwidthMeter.getSingletonInstance(context)
  val safeHeaders =
    request.headers.filterKeys {
      it.lowercase() !in setOf("accept-encoding", "connection", "content-length", "host", "range")
    }
  val userAgent = safeHeaders.entries.firstOrNull { it.key.equals("user-agent", ignoreCase = true) }?.value ?: "GIZTV/1.0"
  val requestProperties = safeHeaders.filterKeys { !it.equals("user-agent", ignoreCase = true) }
  val httpFactory =
    DefaultHttpDataSource.Factory()
      .setUserAgent(userAgent)
      .setTransferListener(bandwidthMeter)
      .setAllowCrossProtocolRedirects(true)
      .setConnectTimeoutMs(15_000)
      .setReadTimeoutMs(25_000)
      .setDefaultRequestProperties(requestProperties)
  val dataSourceFactory = DefaultDataSource.Factory(context, httpFactory)
  val mediaItem = createMediaItem(request)
  return DefaultMediaSourceFactory(dataSourceFactory)
    .setSubtitleParserFactory(OffsetSubtitleParserFactory(subtitleOffsetMs))
    .createMediaSource(mediaItem)
}

/** Applies only a temporary ceiling; the adaptive selector remains responsible for the rendition. */
@androidx.annotation.OptIn(UnstableApi::class)
internal fun applyAutomaticQualityPhase(player: Player, phase: AutomaticQualityPhase) {
  val builder = player.trackSelectionParameters.buildUpon()
  when (phase) {
    AutomaticQualityPhase.LOW_STARTUP ->
      builder.setMaxVideoSize(STARTUP_MAX_VIDEO_WIDTH, STARTUP_MAX_VIDEO_HEIGHT)
    AutomaticQualityPhase.BALANCED ->
      builder.setMaxVideoSize(COMPATIBILITY_MAX_VIDEO_WIDTH, COMPATIBILITY_MAX_VIDEO_HEIGHT)
    AutomaticQualityPhase.UNRESTRICTED -> builder.clearVideoSizeConstraints()
  }
  val updatedParameters = builder.build()
  if (updatedParameters != player.trackSelectionParameters) {
    player.trackSelectionParameters = updatedParameters
  }
}

internal fun isVideoDecoderFailure(error: Throwable): Boolean =
  generateSequence(error) { it.cause }.any { cause ->
    cause.javaClass.simpleName.contains("MediaCodecVideo", ignoreCase = true) ||
      cause.message?.contains("MediaCodecVideoRenderer", ignoreCase = true) == true
  }

internal fun createMediaItem(request: HlsStreamRequest): MediaItem {
  val subtitles =
    request.subtitles.distinctBy { it.url }.map { track ->
      MediaItem.SubtitleConfiguration.Builder(track.url.toUri())
        .setMimeType(track.mimeType)
        .setLabel(track.label)
        .apply {
          track.language?.let(::setLanguage)
          if (track.label.equals("English", ignoreCase = true)) {
            setSelectionFlags(C.SELECTION_FLAG_DEFAULT)
          }
        }
        .build()
    }

  return MediaItem.Builder()
    .setUri(request.url)
    .setMimeType(MimeTypes.APPLICATION_M3U8)
    .setSubtitleConfigurations(subtitles)
    .build()
}

private fun describeSubtitleState(tracks: Tracks): String {
  val textGroups = tracks.groups.filter { it.type == C.TRACK_TYPE_TEXT && it.isSupported }
  if (textGroups.isEmpty()) return "CC: This stream provides no subtitle track"

  val selectedFormats =
    textGroups.flatMap { group ->
      (0 until group.length)
        .filter(group::isTrackSelected)
        .map(group::getTrackFormat)
    }
  if (selectedFormats.any(::isEnglish)) return "CC: English subtitles on"
  if (selectedFormats.isNotEmpty()) return "CC: Subtitles on (language not labelled)"

  val englishAvailable =
    textGroups.any { group ->
      (0 until group.length).any { isEnglish(group.getTrackFormat(it)) }
    }
  return if (englishAvailable) {
    "CC: English available - use the subtitle control"
  } else {
    "CC: English is not provided by this stream"
  }
}

private fun isEnglish(format: Format): Boolean {
  val language = format.language?.lowercase().orEmpty()
  return language == "en" || language == "eng" || language.startsWith("en-") ||
    format.label?.contains("english", ignoreCase = true) == true
}

internal fun playerControllerTimeoutMs(isTelevision: Boolean): Int =
  if (isTelevision) 5_000 else 3_000

internal enum class PlayerBackAction {
  CLOSE_SETTINGS,
  HIDE_CONTROLS,
  EXIT_PLAYER,
}

internal fun playerBackAction(settingsOpen: Boolean, controlsVisible: Boolean): PlayerBackAction =
  when {
    settingsOpen -> PlayerBackAction.CLOSE_SETTINGS
    controlsVisible -> PlayerBackAction.HIDE_CONTROLS
    else -> PlayerBackAction.EXIT_PLAYER
  }

/**
 * "Ends 21:20 · 35 min left", or nothing at all.
 *
 * A live stream has no end to predict and a duration that has not arrived yet is not worth guessing
 * at, so both give back null rather than a figure that would only be wrong. The clock follows the
 * device's own 12- or 24-hour setting.
 */
private fun remainingLabel(context: Context, positionMs: Long, durationMs: Long): String? {
  if (durationMs <= 0L) return null
  val remainingMs = (durationMs - positionMs).coerceAtLeast(0L)
  val minutesLeft = (remainingMs + 30_000L) / 60_000L
  val endsAt = java.util.Date(System.currentTimeMillis() + remainingMs)
  val clock = android.text.format.DateFormat.getTimeFormat(context).format(endsAt)
  val left =
    when {
      minutesLeft <= 0L -> "finishing"
      minutesLeft < 60L -> "${minutesLeft}m left"
      else -> {
        val hours = minutesLeft / 60L
        val minutes = minutesLeft % 60L
        if (minutes == 0L) "${hours}h left" else "${hours}h ${minutes}m left"
      }
    }
  return "Ends $clock · $left"
}
