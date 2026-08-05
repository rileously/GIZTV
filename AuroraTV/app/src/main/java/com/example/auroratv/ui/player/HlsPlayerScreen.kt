package com.example.auroratv.ui.player

import android.app.Activity
import android.content.Context
import android.content.ContextWrapper
import android.content.pm.PackageManager
import android.graphics.Color as AndroidColor
import android.media.AudioManager
import android.provider.Settings
import android.view.ViewGroup
import android.view.WindowManager
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
import androidx.compose.foundation.gestures.detectVerticalDragGestures
import androidx.compose.foundation.layout.Arrangement
import androidx.compose.foundation.layout.Box
import androidx.compose.foundation.layout.BoxWithConstraints
import androidx.compose.foundation.layout.Column
import androidx.compose.foundation.layout.ColumnScope
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
import androidx.compose.material.icons.automirrored.filled.VolumeOff
import androidx.compose.material.icons.automirrored.filled.VolumeUp
import androidx.compose.material.icons.filled.Brightness6
import androidx.compose.material.icons.filled.ClosedCaption
import androidx.compose.material.icons.filled.Close
import androidx.compose.material.icons.filled.Check
import androidx.compose.material.icons.filled.FastForward
import androidx.compose.material.icons.filled.FastRewind
import androidx.compose.material.icons.filled.HighQuality
import androidx.compose.material.icons.filled.AspectRatio
import androidx.compose.material.icons.filled.Memory
import androidx.compose.material.icons.filled.Pause
import androidx.compose.material.icons.filled.PlayArrow
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
import androidx.compose.runtime.mutableFloatStateOf
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
import androidx.compose.ui.hapticfeedback.HapticFeedbackType
import androidx.compose.ui.input.key.Key
import androidx.compose.ui.input.key.KeyEventType
import androidx.compose.ui.input.key.key
import androidx.compose.ui.input.key.onKeyEvent
import androidx.compose.ui.input.key.onPreviewKeyEvent
import androidx.compose.ui.input.key.type
import androidx.compose.ui.input.pointer.pointerInput
import androidx.compose.ui.layout.onSizeChanged
import androidx.compose.ui.platform.LocalContext
import androidx.compose.ui.platform.LocalHapticFeedback
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
import androidx.media3.exoplayer.ExoPlayer
import androidx.media3.exoplayer.drm.DefaultDrmSessionManager
import androidx.media3.exoplayer.drm.FrameworkMediaDrm
import androidx.media3.exoplayer.drm.LocalMediaDrmCallback
import androidx.media3.exoplayer.mediacodec.MediaCodecSelector
import androidx.media3.exoplayer.source.BehindLiveWindowException
import androidx.media3.exoplayer.source.DefaultMediaSourceFactory
import androidx.media3.exoplayer.source.MediaSource
import androidx.media3.exoplayer.trackselection.AdaptiveTrackSelection
import androidx.media3.exoplayer.trackselection.DefaultTrackSelector
import androidx.media3.exoplayer.upstream.DefaultBandwidthMeter
import androidx.media3.exoplayer.upstream.DefaultLoadErrorHandlingPolicy
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
import java.util.concurrent.atomic.AtomicLong
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
  val subtitle: String? = null,
  /** Explicit for extensionless adaptive streams; null lets Media3 inspect progressive content. */
  val mimeType: String? = MimeTypes.APPLICATION_M3U8,
  val drm: StreamDrmConfiguration? = null,
  val isLive: Boolean = false,
  /** What the catalog knows about this title; absent for streams found by plain browsing. */
  val context: PlaybackContext? = null,
  /** Zero-based source currently being attempted when one channel has backup stream addresses. */
  val sourceIndex: Int = 0,
  val sourceCount: Int = 1,
)

internal enum class StreamDrmScheme {
  CLEARKEY,
  WIDEVINE,
}

internal data class StreamDrmConfiguration(
  val scheme: StreamDrmScheme,
  /** A license URL, a ClearKey kid:key pair, or a ClearKey JSON response. */
  val license: String,
  val requestHeaders: Map<String, String> = emptyMap(),
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
private const val STABLE_MAX_VIDEO_BITRATE = 800_000
private const val QUALITY_RAMP_FIRST_STEP_MS = 15_000L
private const val QUALITY_RAMP_FINAL_STEP_MS = 30_000L
private const val QUALITY_RAMP_RECHECK_MS = 5_000L
private const val QUALITY_RAMP_BALANCED_BUFFER_MS = 20_000L
private const val QUALITY_RAMP_UNRESTRICTED_BUFFER_MS = 40_000L
private const val AUTO_QUALITY_RECOVERY_HOLD_MS = 120_000L
private const val AUTO_QUALITY_INCREASE_BUFFER_MS = 30_000
private const val AUTO_QUALITY_DECREASE_BUFFER_MS = 45_000
private const val AUTO_QUALITY_RETAIN_BUFFER_MS = 40_000
private const val AUTO_QUALITY_BANDWIDTH_FRACTION = .55f
private const val RELIABLE_MIN_BUFFER_MS = 30_000
private const val RELIABLE_MAX_BUFFER_MS = 75_000
private const val RELIABLE_START_BUFFER_MS = 5_000
private const val RELIABLE_REBUFFER_MS = 12_000
private const val PHONE_START_BUFFER_MS = 2_500
private const val PHONE_REBUFFER_MS = 5_000
private const val PHONE_AUTO_MAX_VIDEO_WIDTH = 1280
private const val PHONE_AUTO_MAX_VIDEO_HEIGHT = 720
private const val PHONE_AUTO_MAX_VIDEO_BITRATE = 3_000_000
private const val PHONE_AUTO_MAX_VIDEO_FRAME_RATE = 30
private const val RELIABLE_HTTP_CONNECT_TIMEOUT_MS = 20_000
private const val RELIABLE_HTTP_READ_TIMEOUT_MS = 60_000
private const val RELIABLE_HLS_RETRY_COUNT = 6
private const val BACKUP_AVAILABLE_RETRY_COUNT = 2
private const val PROLONGED_STALL_TIMEOUT_MS = 45_000L
private const val STABLE_PLAYBACK_RESET_MS = 60_000L
private const val LOCAL_STALL_RECOVERY_ATTEMPTS = 1
private const val PLAYER_SWIPE_SENSITIVITY = 1.25f
private const val PLAYER_GESTURE_FEEDBACK_MS = 650L
private const val MINIMUM_WINDOW_BRIGHTNESS = .01f
/** One row of a quick panel: the chips, and whatever has to line up beside them. */
private val CHOICE_CHIP_HEIGHT = 36.dp
private const val PLAYER_DOUBLE_TAP_SEEK_MS = 10_000L
private const val PLAYER_DOUBLE_TAP_EDGE_FRACTION = .35f
/** How long a seek burst stays on screen, and how long further taps keep winding it on. */
private const val PLAYER_SEEK_BURST_WINDOW_MS = 800L
internal const val STABLE_QUALITY_LABEL = "Data Saver"

internal enum class PlayerSwipeControl {
  BRIGHTNESS,
  VOLUME,
}

private data class PlayerSwipeFeedback(
  val control: PlayerSwipeControl,
  val level: Float,
)

/** The half where a vertical player swipe began owns the whole gesture. */
internal fun playerSwipeControl(startX: Float, widthPx: Int): PlayerSwipeControl =
  if (startX < widthPx.coerceAtLeast(1) / 2f) {
    PlayerSwipeControl.BRIGHTNESS
  } else {
    PlayerSwipeControl.VOLUME
  }

/** Up increases and down decreases, with one screen-height covering the useful range. */
internal fun playerSwipeLevel(
  startLevel: Float,
  totalVerticalDragPx: Float,
  heightPx: Int,
): Float {
  if (heightPx <= 0) return startLevel.coerceIn(0f, 1f)
  return (startLevel - (totalVerticalDragPx / heightPx) * PLAYER_SWIPE_SENSITIVITY)
    .coerceIn(0f, 1f)
}

internal fun mediaVolumeIndex(level: Float, maximumVolume: Int): Int =
  (level.coerceIn(0f, 1f) * maximumVolume.coerceAtLeast(0)).roundToInt()

internal enum class PlayerSeekSide {
  BACKWARD,
  FORWARD,
}

/** A run of taps on one edge, shown as a single total rather than one jump after another. */
private data class PlayerSeekBurst(
  val side: PlayerSeekSide,
  val totalMs: Long,
)

/**
 * The outer edges own double-tap seeking.
 *
 * The middle stays a plain tap target: a thumb landing in the centre of the picture is reaching for
 * the controls, and the play button sits there once they are up.
 */
internal fun playerSeekSide(tapX: Float, widthPx: Int): PlayerSeekSide? {
  val width = widthPx.toFloat()
  if (width <= 0f) return null
  return when {
    tapX < width * PLAYER_DOUBLE_TAP_EDGE_FRACTION -> PlayerSeekSide.BACKWARD
    tapX > width * (1f - PLAYER_DOUBLE_TAP_EDGE_FRACTION) -> PlayerSeekSide.FORWARD
    else -> null
  }
}

/** Keeps a burst inside the media, so taps at either end settle on the edge instead of overshooting. */
internal fun playerSeekTarget(positionMs: Long, deltaMs: Long, durationMs: Long): Long {
  val target = (positionMs + deltaMs).coerceAtLeast(0L)
  // Live playlists and streams still being read report no duration; there is no far end to clamp to.
  if (durationMs <= 0L) return target
  return target.coerceAtMost(durationMs)
}

internal enum class AutomaticQualityPhase {
  LOW_STARTUP,
  BALANCED,
  UNRESTRICTED,
}

internal data class PlaybackBufferProfile(
  val minBufferMs: Int,
  val maxBufferMs: Int,
  val startBufferMs: Int,
  val rebufferMs: Int,
)

internal fun playbackBufferProfile(isTelevision: Boolean): PlaybackBufferProfile =
  PlaybackBufferProfile(
    minBufferMs = RELIABLE_MIN_BUFFER_MS,
    maxBufferMs = RELIABLE_MAX_BUFFER_MS,
    startBufferMs = if (isTelevision) RELIABLE_START_BUFFER_MS else PHONE_START_BUFFER_MS,
    rebufferMs = if (isTelevision) RELIABLE_REBUFFER_MS else PHONE_REBUFFER_MS,
  )

internal data class AutomaticQualityPromotion(
  val nextPhase: AutomaticQualityPhase,
  val stablePlaybackMs: Long,
  val requiredBufferMs: Long,
)

internal fun automaticQualityPromotion(phase: AutomaticQualityPhase): AutomaticQualityPromotion? =
  when (phase) {
    AutomaticQualityPhase.LOW_STARTUP ->
      AutomaticQualityPromotion(
        nextPhase = AutomaticQualityPhase.BALANCED,
        stablePlaybackMs = QUALITY_RAMP_FIRST_STEP_MS,
        requiredBufferMs = QUALITY_RAMP_BALANCED_BUFFER_MS,
      )
    AutomaticQualityPhase.BALANCED ->
      AutomaticQualityPromotion(
        nextPhase = AutomaticQualityPhase.UNRESTRICTED,
        stablePlaybackMs = QUALITY_RAMP_FINAL_STEP_MS,
        requiredBufferMs = QUALITY_RAMP_UNRESTRICTED_BUFFER_MS,
      )
    AutomaticQualityPhase.UNRESTRICTED -> null
  }

internal fun automaticQualityPhaseAfterBuffering(
  hasStartedPlayback: Boolean,
  automaticQuality: Boolean,
  compatibilityMode: Boolean,
  currentPhase: AutomaticQualityPhase,
): AutomaticQualityPhase =
  if (hasStartedPlayback && automaticQuality && !compatibilityMode) {
    AutomaticQualityPhase.LOW_STARTUP
  } else {
    currentPhase
  }

internal data class VideoQualityOption(
  val label: String,
  val width: Int? = null,
  val height: Int? = null,
  val stable: Boolean = false,
) {
  val isAuto: Boolean get() = width == null || height == null
  val isStable: Boolean get() = stable && isAuto
}

internal enum class ProlongedStallAction {
  RELOAD_CURRENT_STREAM,
  REQUEST_FRESH_STREAM,
}

internal fun prolongedStallAction(recoveryAttempts: Int): ProlongedStallAction =
  if (recoveryAttempts < LOCAL_STALL_RECOVERY_ATTEMPTS) {
    ProlongedStallAction.RELOAD_CURRENT_STREAM
  } else {
    ProlongedStallAction.REQUEST_FRESH_STREAM
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

  ;

  /** The next size round, so the button can be pressed rather than opened. */
  fun next(): VideoResizeOption = entries[(ordinal + 1) % entries.size]
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
  /** Requests another source or a fresh resolution. True means the caller accepted the retry. */
  onPlaybackFailed: () -> Boolean = { false },
  /** A full minute without interruption makes earlier failovers irrelevant again. */
  onPlaybackStable: () -> Unit = {},
) {
  val context = LocalContext.current
  val lifecycleOwner = LocalLifecycleOwner.current
  val progressStore = remember(context) { PlaybackProgressStore(context) }
  val historyStore = remember(context) { WatchHistoryStore(context) }
  val subtitleSyncStore = remember(context) { SubtitleSyncStore(context) }
  val playerPreferences = remember(context) { PlayerPreferencesStore(context) }
  val progressKey = remember(request) { playbackProgressKey(request) }
  val subtitleSyncKey = remember(request) { subtitleSyncKey(request) }
  val isLiveContent = request.isLive || request.context?.kindLabel.equals("LIVE", ignoreCase = true)
  // Continue watching keeps its own position against the catalog page, and it is the one the
  // viewer was just shown. It stands in when the progress store has nothing under this key —
  // which is every catalog title carried over from a build that keyed them by the page the stream
  // happened to be found on.
  val savedProgressMs =
    remember(progressKey, isLiveContent) {
      if (isLiveContent) {
        // A saved offset belongs to yesterday's sliding window, not today's live edge.
        progressStore.clear(progressKey)
        0L
      } else {
        progressStore.load(progressKey).takeIf { it > 0L }
          ?: request.context
            ?.let { historyStore.find(it.pageUrl) }
            ?.takeIf { !it.completed }
            ?.positionMs
            ?.coerceAtLeast(0L)
          ?: 0L
      }
    }
  val isTelevision = remember(context) { context.packageManager.hasSystemFeature(PackageManager.FEATURE_LEANBACK) }
  val activity = remember(context) { context.findActivity() }
  val audioManager = remember(context) { context.getSystemService(Context.AUDIO_SERVICE) as AudioManager }
  val haptics = LocalHapticFeedback.current
  var brightnessLevel by
    remember(activity) { mutableFloatStateOf(currentPlayerBrightness(context, activity)) }
  var status by
    remember(request.url) {
      mutableStateOf(
        if (request.sourceIndex > 0) {
          "Primary link failed · trying backup ${request.sourceIndex} of ${request.sourceCount - 1}…"
        } else {
          "Starting smoothly in low quality…"
        }
      )
    }
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
  // Read before the player is built, so its text renderer starts on the saved subtitle clock.
  var subtitleOffsetMs by remember(subtitleSyncKey) { mutableLongStateOf(subtitleSyncStore.load(subtitleSyncKey)) }
  // Shared with the text renderer, which reads it on every render pass.
  val subtitleOffset = remember(subtitleSyncKey) { AtomicLong(subtitleOffsetMs) }
  var playbackSpeed by remember(request) { mutableStateOf(playerPreferences.playbackSpeed()) }
  var videoResize by remember(request) { mutableStateOf(VideoResizeOption.FIT) }
  var isCasting by remember(request) { mutableStateOf(false) }
  var videoSize by remember(request) { mutableStateOf(VideoSize.UNKNOWN) }
  var inPictureInPicture by remember { mutableStateOf(false) }
  var selectedQuality by remember(request, compatibilityMode) {
    mutableStateOf(
      if (playerPreferences.stablePlayback()) VideoQualityOption(STABLE_QUALITY_LABEL, stable = true)
      else VideoQualityOption("Auto")
    )
  }
  var selectedAudio by remember(request, compatibilityMode) { mutableStateOf(playerPreferences.audioTrack()) }
  var selectedSubtitle by remember(request, compatibilityMode) { mutableStateOf(playerPreferences.subtitleTrack()) }
  val localPlayer =
    remember(request, compatibilityMode) {
      createHlsPlayer(
        context = context,
        request = request,
        compatibilityMode = compatibilityMode,
        subtitleOffset = subtitleOffset,
        startPositionMs = resumePositionMs,
        isTelevision = isTelevision,
      )
    }
  val player: Player =
    remember(localPlayer, isTelevision) {
      createCastAwarePlayer(context, localPlayer, isTelevision)
    }
  var qualityOptions by remember(player) {
    mutableStateOf(
      listOf(VideoQualityOption("Auto"), VideoQualityOption(STABLE_QUALITY_LABEL, stable = true))
    )
  }
  var audioOptions by remember(player) { mutableStateOf(listOf(AudioTrackOption("Auto English"))) }
  var subtitleOptions by remember(player) { mutableStateOf(listOf(SubtitleTrackOption("Auto English"), SubtitleTrackOption("Off", disabled = true))) }
  var controlsVisible by remember { mutableStateOf(true) }
  var controlsInteractionVersion by remember { mutableIntStateOf(0) }
  var swipeFeedback by remember(request) { mutableStateOf<PlayerSwipeFeedback?>(null) }
  var swipeInProgress by remember(request) { mutableStateOf(false) }
  var seekBurst by remember(request) { mutableStateOf<PlayerSeekBurst?>(null) }
  var playbackFinished by remember(request) { mutableStateOf(false) }
  var automaticQualityPhase by remember(player) { mutableStateOf(AutomaticQualityPhase.LOW_STARTUP) }
  var automaticQualityRecoveryLock by remember(player) { mutableStateOf(false) }
  var dataSaverFallbackRank by remember(player) { mutableIntStateOf(0) }
  var hasStartedPlayback by remember(player) { mutableStateOf(false) }
  var wantsPlayback by remember(player) { mutableStateOf(true) }
  var stallRecoveryAttempts by remember(player) { mutableIntStateOf(0) }
  // A two-minute short drama is watched as a run, so it rolls on with no countdown to sit through.
  // A full-length episode gets the pause, which is the viewer's chance to stop after one.
  val shortForm = request.context?.shortForm == true
  val autoAdvanceDelaySeconds = if (shortForm) 0 else AUTO_ADVANCE_SECONDS
  var autoAdvanceSeconds by remember(request) { mutableIntStateOf(autoAdvanceDelaySeconds) }
  val playButtonFocusRequester = remember { FocusRequester() }
  val surfaceFocusRequester = remember { FocusRequester() }
  val nextEntry = request.context?.nextEntry
  val nextPromptVisible = playbackFinished && nextEntry != null

  /**
   * Moves the captions, and nothing else.
   *
   * The text renderer reads the offset on every frame. Cycling only the text track clears the cue
   * currently on screen and immediately resolves it against the new clock; video and audio keep
   * their buffer and never restart.
   */
  fun applySubtitleOffset(offsetMs: Long) {
    // Casting hands the subtitles to the receiver, which owns their timing from then on.
    val safeOffsetMs = offsetMs.coerceIn(-MAX_SUBTITLE_SYNC_MS, MAX_SUBTITLE_SYNC_MS)
    if (isCasting || safeOffsetMs == subtitleOffsetMs) return
    subtitleOffsetMs = safeOffsetMs
    subtitleOffset.set(safeOffsetMs)
    subtitleSyncStore.save(subtitleSyncKey, safeOffsetMs)
    refreshSubtitleRenderer(localPlayer)
  }

  /**
   * Jumps one step and folds it into the burst on screen.
   *
   * Each tap moves the player another step from where it already is, while the overlay counts the
   * run as one total — so four taps read "40 seconds" rather than flashing "10" four times.
   */
  fun seekByTap(side: PlayerSeekSide) {
    if (!player.isCurrentMediaItemSeekable) return
    val stepMs =
      if (side == PlayerSeekSide.BACKWARD) -PLAYER_DOUBLE_TAP_SEEK_MS else PLAYER_DOUBLE_TAP_SEEK_MS
    player.seekTo(playerSeekTarget(player.currentPosition, stepMs, player.duration))
    val running = seekBurst
    seekBurst =
      PlayerSeekBurst(
        side = side,
        totalMs =
          if (running?.side == side) running.totalMs + PLAYER_DOUBLE_TAP_SEEK_MS
          else PLAYER_DOUBLE_TAP_SEEK_MS,
      )
    controlsInteractionVersion++
  }

  fun savePlaybackProgress() {
    if (isLiveContent) {
      // Live playlists slide forward. Persisting their relative position makes a later retry seek
      // behind the window and can leave the player waiting for a manual pause/play cycle.
      progressStore.clear(progressKey)
      return
    }
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

  // A hardware volume press while this player is visible should target media too. Brightness is a
  // window-only override: leaving the player restores exactly what the activity was using before.
  DisposableEffect(activity, isTelevision) {
    val originalVolumeStream = activity?.volumeControlStream
    val originalBrightness = activity?.window?.attributes?.screenBrightness
    if (!isTelevision) activity?.volumeControlStream = AudioManager.STREAM_MUSIC
    onDispose {
      if (!isTelevision) {
        originalVolumeStream?.let { activity.volumeControlStream = it }
        originalBrightness?.let { activity.setPlayerBrightness(it) }
      }
    }
  }

  LaunchedEffect(swipeFeedback, swipeInProgress) {
    if (swipeFeedback != null && !swipeInProgress) {
      delay(PLAYER_GESTURE_FEEDBACK_MS)
      swipeFeedback = null
    }
  }

  // Every tap replaces the burst, which restarts this wait — the overlay only clears once the
  // viewer has stopped tapping, and clearing it is what ends the run.
  LaunchedEffect(seekBurst) {
    if (seekBurst == null) return@LaunchedEffect
    delay(PLAYER_SEEK_BURST_WINDOW_MS)
    seekBurst = null
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
                dataSaverFallbackRank = 0
                playerPreferences.setStablePlayback(option.isStable)
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
          applySubtitleOffset(subtitleOffsetMs + deltaMs)
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
            Player.STATE_BUFFERING -> {
              if (
                hasStartedPlayback &&
                  selectedQuality.isAuto &&
                  !selectedQuality.isStable &&
                  !compatibilityMode
              ) {
                automaticQualityRecoveryLock = true
              }
              val nextPhase =
                automaticQualityPhaseAfterBuffering(
                  hasStartedPlayback = hasStartedPlayback,
                  automaticQuality = selectedQuality.isAuto,
                  compatibilityMode = compatibilityMode,
                  currentPhase = automaticQualityPhase,
                )
              if (nextPhase != automaticQualityPhase) {
                automaticQualityPhase = nextPhase
                applyAutomaticQualityPhase(player, nextPhase, isTelevision)
                status = "Connection slowed - rebuilding a safety buffer in low quality"
              }
            }
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
          selectVideoQuality(player, selectedQuality, dataSaverFallbackRank)
          selectAudioTrack(player, selectedAudio)
          selectSubtitleTrack(player, selectedSubtitle)
        }

        override fun onDeviceInfoChanged(deviceInfo: DeviceInfo) {
          isCasting = deviceInfo.playbackType == DeviceInfo.PLAYBACK_TYPE_REMOTE
          if (isCasting) status = "Casting to Chromecast · subtitles available in CC"
        }

        override fun onIsPlayingChanged(isPlaying: Boolean) {
          isVideoPlaying = isPlaying
          if (isPlaying) hasStartedPlayback = true
          // A floating window is too small for the controls, and the viewer is looking elsewhere.
          if (!isPlaying && !inPictureInPicture) controlsVisible = true
        }

        override fun onPlayWhenReadyChanged(playWhenReady: Boolean, reason: Int) {
          wantsPlayback = playWhenReady
        }

        override fun onVideoSizeChanged(size: VideoSize) {
          videoSize = size
        }

        override fun onPlayerError(playerError: PlaybackException) {
          android.util.Log.e("AuroraHls", "Playback failed for ${request.url}", playerError)
          if (isBehindLiveWindowFailure(playerError)) {
            // The playlist advanced past the segment we were reading. Jump to its current default
            // position locally; resolving the same URL again can carry the stale offset forward.
            resumePositionMs = 0L
            isVideoPlaying = false
            error = null
            status = "Live stream moved ahead - catching up..."
            player.seekToDefaultPosition()
            player.prepare()
            player.play()
            return
          }
          if (selectedQuality.isStable && isHlsTrackMappingFailure(playerError)) {
            val nextRank = dataSaverFallbackRank + 1
            if (selectVideoQuality(player, selectedQuality, nextRank)) {
              dataSaverFallbackRank = nextRank
              resumePositionMs = player.currentPosition.coerceAtLeast(0L)
              savePlaybackProgress()
              isVideoPlaying = false
              error = null
              status = "Lowest source track was malformed - retrying the next-lowest quality"
              player.stop()
              player.seekTo(resumePositionMs)
              player.prepare()
              player.play()
              return
            }
          }
          // A decoder that cannot cope is this device's problem and the address is fine; anything
          // else and the address is the first suspect.
          val decoderFailure = isVideoDecoderFailure(playerError)
          if (!decoderFailure && onPlaybackFailed()) {
            savePlaybackProgress()
            return
          }
          if (!compatibilityMode && decoderFailure) {
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
              invalidPlaylist -> "The detected URL did not return a valid stream manifest. Try another server."
              decoderFailure ->
                "This TV could not decode the video, even in compatibility mode. Try another video server."
              request.isLive && request.sourceCount > 1 ->
                "All ${request.sourceCount} sources for this channel are currently offline. Try Reload or another channel."
              request.isLive ->
                "This channel is currently offline. Try Reload for newer links or choose another channel."
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
    // The source already starts here; this only matters for a player that was handed a position
    // after it was built, such as one rebuilt for compatibility mode.
    if (resumePositionMs > 0L && player.currentPosition < resumePositionMs / 2) {
      player.seekTo(resumePositionMs)
    }
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

  // Changing back to Auto always earns a fresh conservative start. Data Saver pins the source's
  // actual lowest supported rendition once the HLS tracks are known.
  LaunchedEffect(player, selectedQuality, compatibilityMode) {
    if (compatibilityMode && !selectedQuality.isStable) return@LaunchedEffect
    automaticQualityRecoveryLock = false
    dataSaverFallbackRank = 0
    automaticQualityPhase =
      if (selectedQuality.isAuto) AutomaticQualityPhase.LOW_STARTUP
      else AutomaticQualityPhase.UNRESTRICTED
    // A manual fixed rendition is explicit permission to exceed the phone Auto ceiling.
    applyAutomaticQualityPhase(
      player,
      automaticQualityPhase,
      isTelevision = isTelevision || !selectedQuality.isAuto,
    )
    if (selectedQuality.isStable) {
      status = "Data Saver - using the lowest available quality"
    }
  }

  // Quality only climbs after uninterrupted playback and a real forward safety buffer. A slow or
  // unstable link can therefore remain low instead of oscillating into an unsustainable rendition.
  // After a real rebuffer, Auto needs two uninterrupted minutes before it may begin climbing again.
  LaunchedEffect(
    player,
    selectedQuality,
    compatibilityMode,
    isVideoPlaying,
    automaticQualityPhase,
    automaticQualityRecoveryLock,
  ) {
    if (
      compatibilityMode ||
        !selectedQuality.isAuto ||
        selectedQuality.isStable ||
        !isVideoPlaying
    ) {
      return@LaunchedEffect
    }
    if (automaticQualityRecoveryLock) {
      status = "Auto quality - holding low after a connection slowdown"
      delay(AUTO_QUALITY_RECOVERY_HOLD_MS)
      while (player.totalBufferedDuration < QUALITY_RAMP_UNRESTRICTED_BUFFER_MS) {
        status = "Auto quality - waiting for a safe recovery buffer"
        delay(QUALITY_RAMP_RECHECK_MS)
      }
      automaticQualityRecoveryLock = false
      return@LaunchedEffect
    }
    val promotion = automaticQualityPromotion(automaticQualityPhase) ?: run {
      status = "Auto quality - adapting with extra bandwidth headroom"
      return@LaunchedEffect
    }

    status = "Auto quality - building a safety buffer"
    delay(promotion.stablePlaybackMs)
    while (player.totalBufferedDuration < promotion.requiredBufferMs) {
      status = "Auto quality - holding low quality until the buffer is safe"
      delay(QUALITY_RAMP_RECHECK_MS)
    }
    applyAutomaticQualityPhase(player, promotion.nextPhase, isTelevision)
    automaticQualityPhase = promotion.nextPhase
  }

  // A stream can keep the player in BUFFERING forever without producing an error. Give the same
  // address one clean reload, then ask the catalog resolver for a fresh signed address/server.
  LaunchedEffect(player, isBuffering, wantsPlayback, error) {
    if (!isBuffering || !wantsPlayback || error != null) return@LaunchedEffect
    delay(PROLONGED_STALL_TIMEOUT_MS)
    when (prolongedStallAction(stallRecoveryAttempts)) {
      ProlongedStallAction.RELOAD_CURRENT_STREAM -> {
        stallRecoveryAttempts++
        val positionMs = player.currentPosition.coerceAtLeast(0L)
        savePlaybackProgress()
        error = null
        status = "Connection stalled - reloading this stream once"
        player.stop()
        player.seekTo(positionMs)
        player.prepare()
        player.play()
      }
      ProlongedStallAction.REQUEST_FRESH_STREAM -> {
        savePlaybackProgress()
        if (!onPlaybackFailed()) {
          error = "This stream stayed stalled. Try another server."
          status = "Playback stalled"
          controlsVisible = true
        }
      }
    }
  }

  // Once playback has remained uninterrupted for a full minute, a previous recovery no longer
  // counts against this title. Future trouble starts with the least disruptive step again.
  LaunchedEffect(player, isVideoPlaying) {
    if (!isVideoPlaying) return@LaunchedEffect
    delay(STABLE_PLAYBACK_RESET_MS)
    stallRecoveryAttempts = 0
    onPlaybackStable()
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
      .pointerInput(isTelevision, settingsOpen, player) {
        fun toggleControls() {
          if (settingsOpen) return
          controlsVisible = !controlsVisible
          controlsInteractionVersion++
        }

        // A remote never double-taps, and waiting for a second tap that cannot come would only
        // put a delay in front of every press of the select button.
        if (isTelevision) {
          detectTapGestures { toggleControls() }
          return@pointerInput
        }

        detectTapGestures(
          onDoubleTap = { offset ->
            if (settingsOpen) return@detectTapGestures
            val side = playerSeekSide(offset.x, size.width)
            if (side == null) {
              toggleControls()
              return@detectTapGestures
            }
            haptics.performHapticFeedback(HapticFeedbackType.LongPress)
            seekByTap(side)
          },
          onTap = { offset ->
            if (settingsOpen) return@detectTapGestures
            // Once a burst is up, single taps on the same edge keep winding it on: after the first
            // double-tap the viewer should not have to double-tap again for every ten seconds.
            val side = playerSeekSide(offset.x, size.width)
            if (side != null && seekBurst?.side == side) {
              seekByTap(side)
              return@detectTapGestures
            }
            toggleControls()
          },
        )
      }
      .pointerInput(isTelevision, settingsOpen, inPictureInPicture, player) {
        if (isTelevision || settingsOpen || inPictureInPicture) return@pointerInput

        var activeControl: PlayerSwipeControl? = null
        var startLevel = 0f
        var totalDragPx = 0f
        detectVerticalDragGestures(
          onDragStart = { start ->
            val control = playerSwipeControl(start.x, size.width)
            activeControl = control
            startLevel =
              when (control) {
                PlayerSwipeControl.BRIGHTNESS -> brightnessLevel
                PlayerSwipeControl.VOLUME ->
                  if (audioManager.isVolumeFixed) player.volume else currentMediaVolume(audioManager)
              }
            totalDragPx = 0f
            swipeInProgress = true
            swipeFeedback = activeControl?.let { PlayerSwipeFeedback(it, startLevel) }
            controlsInteractionVersion++
          },
          onVerticalDrag = { change, dragAmount ->
            val control = activeControl ?: return@detectVerticalDragGestures
            change.consume()
            totalDragPx += dragAmount
            val level = playerSwipeLevel(startLevel, totalDragPx, size.height)
            when (control) {
              PlayerSwipeControl.BRIGHTNESS -> {
                brightnessLevel = level
                activity?.setPlayerBrightness(level.coerceAtLeast(MINIMUM_WINDOW_BRIGHTNESS))
              }
              PlayerSwipeControl.VOLUME -> {
                setMediaVolume(audioManager, player, level)
              }
            }
            swipeFeedback = PlayerSwipeFeedback(control, level)
          },
          onDragEnd = {
            activeControl = null
            swipeInProgress = false
          },
          onDragCancel = {
            activeControl = null
            swipeInProgress = false
          },
        )
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
        // Five sizes and an obvious order: pressing it once is quicker than opening a list to
        // pick the next one along, and the pill shows where you have got to.
        onPicture = { videoResize = videoResize.next() },
        onSpeed = { openSettings(PlayerControlDialog.SPEED) },
        onDecoder = { openSettings(PlayerControlDialog.DECODER) },
      )
    }

    if (!isTelevision && !inPictureInPicture) {
      swipeFeedback?.let { feedback ->
        PlayerSwipeFeedbackOverlay(
          feedback = feedback,
          modifier =
            Modifier.align(
                if (feedback.control == PlayerSwipeControl.BRIGHTNESS) Alignment.CenterStart
                else Alignment.CenterEnd
              )
              .padding(horizontal = 24.dp),
        )
      }

      // Sits under the swipe pill in the file so a burst never paints over a level being dragged.
      seekBurst?.let { burst ->
        PlayerSeekBurstOverlay(
          burst = burst,
          modifier =
            Modifier.align(
                if (burst.side == PlayerSeekSide.BACKWARD) Alignment.CenterStart
                else Alignment.CenterEnd
              )
              .fillMaxWidth(PLAYER_DOUBLE_TAP_EDGE_FRACTION),
        )
      }
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
          dataSaverFallbackRank = 0
          playerPreferences.setStablePlayback(option.isStable)
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
        onSubtitleOffsetSelected = { offsetMs -> applySubtitleOffset(offsetMs) },
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

private fun currentPlayerBrightness(context: Context, activity: Activity?): Float {
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

private fun Activity.setPlayerBrightness(level: Float) {
  window.attributes =
    window.attributes.apply {
      screenBrightness =
        if (level < 0f) WindowManager.LayoutParams.BRIGHTNESS_OVERRIDE_NONE
        else level.coerceIn(MINIMUM_WINDOW_BRIGHTNESS, 1f)
    }
}

private fun currentMediaVolume(audioManager: AudioManager): Float {
  val maximum = audioManager.getStreamMaxVolume(AudioManager.STREAM_MUSIC)
  if (maximum <= 0) return 0f
  return (audioManager.getStreamVolume(AudioManager.STREAM_MUSIC).toFloat() / maximum)
    .coerceIn(0f, 1f)
}

private fun setMediaVolume(audioManager: AudioManager, player: Player, level: Float) {
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
private fun PlayerSwipeFeedbackOverlay(
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
  // Standing up rather than lying down: the bar fills the way the thumb is moving, so the gesture
  // and the readout point the same direction.
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
    Text("$percentage%", color = AuroraMint, fontWeight = FontWeight.Black, fontSize = 12.sp)
    Spacer(Modifier.height(12.dp))
    Box(
      modifier =
        Modifier.width(6.dp).height(124.dp).clip(RoundedCornerShape(3.dp))
          .background(SoftWhite.copy(alpha = .18f)),
      contentAlignment = Alignment.BottomCenter,
    ) {
      Box(
        Modifier.fillMaxWidth().fillMaxHeight(level).clip(RoundedCornerShape(3.dp))
          .background(AuroraMint)
      )
    }
    Spacer(Modifier.height(12.dp))
    Icon(icon, contentDescription = null, tint = AuroraMint, modifier = Modifier.size(22.dp))
  }
}

@Composable
private fun PlayerSeekBurstOverlay(burst: PlayerSeekBurst, modifier: Modifier = Modifier) {
  val backward = burst.side == PlayerSeekSide.BACKWARD
  val label = "${burst.totalMs / 1_000L} seconds"
  // A half-round wash off the edge it was tapped on, so the side that moved is unmistakable.
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
      val playButtonSize = if (compact) 68.dp else 78.dp
      // Seeking lives on the timeline and remote keys. The center is deliberately limited to one
      // faint play/pause target so controls never obscure the picture, including while buffering.
      val playButtonAlpha = .22f

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
            modifier = Modifier.focusRequester(playButtonFocusRequester).alpha(playButtonAlpha),
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
            // Smaller than the running time above it, so this column stays no wider than it was
            // and the row of pills beside it keeps the room it had.
            remainingLabel(LocalContext.current, seekPreviewMs ?: positionMs, durationMs)?.let {
              Text(
                it,
                color = MutedBlue,
                fontWeight = FontWeight.Medium,
                fontSize = 10.sp,
                maxLines = 1,
                softWrap = false,
              )
            }
          }
          // The pills take whatever room is left and slide when there is not enough of it. Shaving
          // their labels to fit only moved the problem to the next screen that was narrower still.
          Row(
            modifier = Modifier.weight(1f).horizontalScroll(rememberScrollState()),
            horizontalArrangement = Arrangement.spacedBy(if (compact) 6.dp else 8.dp, Alignment.End),
            verticalAlignment = Alignment.CenterVertically,
          ) {
          if (!compact) {
            val hiddenWhileSeekingModifier = Modifier.alpha(surroundingControlsAlpha)
            ModernPlayerActionPill(Icons.AutoMirrored.Filled.VolumeUp, "Audio", selectedAudio, onAudio, onInteraction, showValue = false, modifier = hiddenWhileSeekingModifier)
            ModernPlayerActionPill(Icons.Filled.HighQuality, "Quality", selectedQuality, onQuality, onInteraction, showValue = false, modifier = hiddenWhileSeekingModifier)
            ModernPlayerActionPill(Icons.Filled.ClosedCaption, "Subtitles", selectedSubtitle, onSubtitles, onInteraction, showValue = false, modifier = hiddenWhileSeekingModifier)
            ModernPlayerActionPill(Icons.Filled.AspectRatio, "Picture", selectedResize, onPicture, onInteraction, showValue = true, modifier = hiddenWhileSeekingModifier)
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
    // A pill is a label, not a paragraph: "Decoder" was folding onto a second line and taking the
    // whole row with it once the time column grew an end-time under it.
    Text(
      label,
      color = if (focused) DeepSpace else SoftWhite,
      fontWeight = FontWeight.Bold,
      fontSize = 12.sp,
      maxLines = 1,
      softWrap = false,
    )
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
    ?: request.subtitle?.trim()?.takeIf { it.isNotBlank() }

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
  // Nudging the captions means watching them, so this one keeps out of the picture: a single pill
  // wide enough for its own buttons, no dimming scrim, and no heading repeating what the readout
  // already says. The full-width panel it used to borrow covered the band the subtitles sit in.
  Box(modifier = Modifier.fillMaxSize(), contentAlignment = Alignment.BottomCenter) {
    Row(
      modifier =
        Modifier
          // Clear of the transport row underneath, so both are usable at once.
          .padding(bottom = 104.dp, start = 20.dp, end = 20.dp)
          .clip(RoundedCornerShape(26.dp))
          .background(NightSurface.copy(alpha = .96f))
          .border(1.dp, SoftWhite.copy(alpha = .16f), RoundedCornerShape(26.dp))
          // Only bites on a narrow upright screen; in landscape the pill wraps its own buttons.
          .horizontalScroll(rememberScrollState())
          .padding(horizontal = 10.dp, vertical = 8.dp),
      verticalAlignment = Alignment.CenterVertically,
      horizontalArrangement = Arrangement.spacedBy(7.dp),
    ) {
      Icon(
        Icons.Filled.ClosedCaption,
        contentDescription = "Subtitle sync",
        tint = AuroraMint,
        modifier = Modifier.size(20.dp),
      )
      Text(
        if (isCasting) "The receiver owns the timing while casting" else subtitleSyncHeadline(offsetMs),
        color = SoftWhite,
        fontWeight = FontWeight.Bold,
        fontSize = 12.sp,
        maxLines = 1,
        softWrap = false,
      )
      if (!isCasting) {
        Spacer(Modifier.width(3.dp))
        SettingsChoiceChip(
          label = "-0.5s",
          selected = false,
          onClick = { onOffsetSelected(adjustSubtitleSync(offsetMs, -500L)) },
          modifier = Modifier.focusRequester(firstChoiceFocus),
        )
        SettingsChoiceChip(
          label = "-0.1s",
          selected = false,
          onClick = { onOffsetSelected(adjustSubtitleSync(offsetMs, -100L)) },
        )
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
        // Nothing to undo while the captions are already on the source timing.
        if (offsetMs != 0L) {
          SettingsChoiceChip(label = "Reset", selected = false, onClick = { onOffsetSelected(0L) })
        }
      }
      ModernTransportControl(
        icon = Icons.Filled.Close,
        label = "Close subtitle sync",
        size = 34.dp,
        onClick = onClose,
        onInteraction = {},
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
  // The same word and icon as the button that opened it, so the pill reads as that button unfolding.
  val title =
    when (dialog) {
      PlayerControlDialog.SUBTITLES -> "Subtitles"
      PlayerControlDialog.SUBTITLE_SYNC -> "Subtitle sync"
      PlayerControlDialog.AUDIO -> "Audio"
      PlayerControlDialog.QUALITY -> "Quality"
      PlayerControlDialog.PICTURE -> "Picture"
      PlayerControlDialog.SPEED -> "Speed"
      PlayerControlDialog.DECODER -> "Decoder"
    }
  val icon =
    when (dialog) {
      PlayerControlDialog.SUBTITLES -> Icons.Filled.ClosedCaption
      PlayerControlDialog.SUBTITLE_SYNC -> Icons.Filled.ClosedCaption
      PlayerControlDialog.AUDIO -> Icons.AutoMirrored.Filled.VolumeUp
      PlayerControlDialog.QUALITY -> Icons.Filled.HighQuality
      PlayerControlDialog.PICTURE -> Icons.Filled.AspectRatio
      PlayerControlDialog.SPEED -> Icons.Filled.Speed
      PlayerControlDialog.DECODER -> Icons.Filled.Memory
    }

  LaunchedEffect(dialog) {
    delay(100L)
    runCatching { firstChoiceFocus.requestFocus() }
  }

  PlayerQuickPanel(icon = icon, title = title, onClose = onClose) {
        when (dialog) {
          PlayerControlDialog.SUBTITLES -> {
            DialogChoiceSection(
              title = "Track",
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

/**
 * A settings pill that sits on the film rather than in front of it.
 *
 * These were full-height sheets in the middle of the screen with their choices stacked down the
 * page, so changing the audio track meant losing sight of what you were changing it for. Then they
 * became a bar the full width of the picture, which three short chips never needed and which dimmed
 * the film behind a scrim to hold all that empty space. This one is as wide as its own buttons and
 * no wider, leaves the picture alone, and lays its choices out along a line a pad can run through.
 *
 * The heading is an icon and one word — the same icon as the button that opened it. The sentence
 * that used to sit beside it described what the choices below already say.
 */
@Composable
private fun PlayerQuickPanel(
  icon: ImageVector,
  title: String,
  onClose: () -> Unit,
  content: @Composable ColumnScope.() -> Unit,
) {
  BoxWithConstraints(modifier = Modifier.fillMaxSize(), contentAlignment = Alignment.BottomCenter) {
    Row(
      modifier =
        Modifier.widthIn(max = maxWidth - 32.dp)
          // Clear of the transport row underneath, so both are usable at once.
          .padding(bottom = 104.dp)
          .clip(RoundedCornerShape(26.dp))
          .background(NightSurface.copy(alpha = .96f))
          .border(1.dp, SoftWhite.copy(alpha = .16f), RoundedCornerShape(26.dp))
          .padding(start = 14.dp, end = 8.dp, top = 8.dp, bottom = 8.dp),
      // Level with the first line of choices. Centred, they floated at the mid-point of a pill four
      // rows tall and read as belonging to nothing in particular.
      verticalAlignment = Alignment.Top,
      horizontalArrangement = Arrangement.spacedBy(10.dp),
    ) {
      Row(
        modifier = Modifier.height(CHOICE_CHIP_HEIGHT),
        verticalAlignment = Alignment.CenterVertically,
        horizontalArrangement = Arrangement.spacedBy(9.dp),
      ) {
        Icon(icon, contentDescription = null, tint = AuroraMint, modifier = Modifier.size(20.dp))
        Text(
          title,
          color = SoftWhite,
          fontWeight = FontWeight.Bold,
          fontSize = 12.sp,
          maxLines = 1,
          softWrap = false,
        )
      }
      Column(
        modifier = Modifier.weight(1f, fill = false),
        verticalArrangement = Arrangement.spacedBy(6.dp),
        content = content,
      )
      Box(modifier = Modifier.height(CHOICE_CHIP_HEIGHT), contentAlignment = Alignment.Center) {
        ModernTransportControl(
          icon = Icons.Filled.Close,
          label = "Close $title",
          size = 34.dp,
          onClick = onClose,
          onInteraction = {},
        )
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
  DialogChoiceSection(
    title = null,
    options = options,
    selected = selected,
    onSelected = onSelected,
    firstChoiceModifier = firstChoiceModifier,
  )
}

/**
 * One group of choices on a single line, with its name at the left.
 *
 * Each group used to be a boxed card of its own, stacked down a scrolling column — which put the
 * subtitles' size, position and style below the fold of a panel that showed no sign there was
 * anything under it. Four labelled lines fit in the space one of those cards took.
 */
@Composable
private fun DialogChoiceSection(
  title: String?,
  options: List<String>,
  selected: String,
  onSelected: (String) -> Unit,
  firstChoiceModifier: Modifier = Modifier,
) {
  Row(
    modifier = Modifier.horizontalScroll(rememberScrollState()),
    verticalAlignment = Alignment.CenterVertically,
    horizontalArrangement = Arrangement.spacedBy(7.dp),
  ) {
    title?.let {
      Text(
        it.uppercase(Locale.ENGLISH),
        color = AuroraMint,
        fontWeight = FontWeight.Black,
        fontSize = 10.sp,
        maxLines = 1,
        softWrap = false,
        // A shared minimum keeps the groups' chips lined up under one another.
        modifier = Modifier.widthIn(min = 56.dp),
      )
    }
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
      Text(
        if (isCasting) "CAST" else subtitleSyncHeadline(offsetMs),
        color = AuroraMint,
        fontWeight = FontWeight.Black,
        fontSize = 15.sp,
        maxLines = 1,
        softWrap = false,
      )
    }
    if (!isCasting) {
      Spacer(Modifier.height(10.dp))
      // Named by what they do rather than by their sign. "+0.5s" does not say whether a caption is
      // about to arrive sooner or later, and getting it the wrong way round means watching the film
      // drift further out before you realise which way to go.
      Row(
        modifier = Modifier.fillMaxWidth().horizontalScroll(rememberScrollState()),
        horizontalArrangement = Arrangement.spacedBy(7.dp),
      ) {
        SettingsChoiceChip(
          label = "Earlier ½s",
          selected = false,
          onClick = { onOffsetSelected(adjustSubtitleSync(offsetMs, -500L)) },
          modifier = firstChoiceModifier,
        )
        SettingsChoiceChip(
          label = "Earlier a touch",
          selected = false,
          onClick = { onOffsetSelected(adjustSubtitleSync(offsetMs, -100L)) },
        )
        SettingsChoiceChip(label = "In sync", selected = offsetMs == 0L, onClick = { onOffsetSelected(0L) })
        SettingsChoiceChip(
          label = "Later a touch",
          selected = false,
          onClick = { onOffsetSelected(adjustSubtitleSync(offsetMs, 100L)) },
        )
        SettingsChoiceChip(
          label = "Later ½s",
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
      modifier.height(CHOICE_CHIP_HEIGHT).clip(RoundedCornerShape(9.dp)).background(background)
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

/**
 * What the sync is doing, in words.
 *
 * A signed number tells the viewer how far but not which way, and which way is the only thing they
 * are trying to work out while a caption drifts against the speech.
 */
internal fun subtitleSyncHeadline(offsetMs: Long): String =
  when {
    offsetMs == 0L -> "In sync"
    offsetMs < 0L -> "${subtitleSyncMagnitudeLabel(offsetMs)} earlier"
    else -> "${subtitleSyncMagnitudeLabel(offsetMs)} later"
  }

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
  (offsetMs + deltaMs).coerceIn(-MAX_SUBTITLE_SYNC_MS, MAX_SUBTITLE_SYNC_MS)

/** Re-selects only text so the cue already on screen is recalculated against the new clock. */
internal fun refreshSubtitleRenderer(player: Player) {
  val selectedParameters = player.trackSelectionParameters
  player.trackSelectionParameters =
    selectedParameters.buildUpon().setTrackTypeDisabled(C.TRACK_TYPE_TEXT, true).build()
  player.trackSelectionParameters = selectedParameters
}

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
  return listOf(
    VideoQualityOption("Auto"),
    VideoQualityOption(STABLE_QUALITY_LABEL, stable = true),
  ) + fixedOptions
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
internal fun selectVideoQuality(
  player: Player,
  option: VideoQualityOption,
  dataSaverRank: Int = 0,
): Boolean {
  val parameters = player.trackSelectionParameters.buildUpon().clearOverridesOfType(C.TRACK_TYPE_VIDEO)
  val candidates =
    player.currentTracks.groups
      .filter { it.type == C.TRACK_TYPE_VIDEO }
      .flatMap { group ->
        (0 until group.length)
          .filter(group::isTrackSupported)
          .map { index -> Triple(group, index, group.getTrackFormat(index)) }
      }
  val candidate =
    when {
      option.isStable -> {
        val orderedIndices = dataSaverVideoFormatOrder(candidates.map { it.third })
        orderedIndices.getOrNull(dataSaverRank)?.let(candidates::get)
      }
      !option.isAuto ->
        candidates
          .filter { (_, _, format) -> format.width == option.width && format.height == option.height }
          .maxByOrNull { (_, _, format) -> format.bitrate }
      else -> null
    }
  if (option.isStable && candidate == null) return false
  candidate?.let { (group, index, _) ->
    parameters.setOverrideForType(TrackSelectionOverride(group.mediaTrackGroup, index))
  }
  val updatedParameters = parameters.build()
  if (updatedParameters != player.trackSelectionParameters) {
    player.trackSelectionParameters = updatedParameters
  }
  return option.isAuto || candidate != null
}

/**
 * Finds the least demanding supported rendition. HLS manifests normally publish a bitrate; when
 * they do not, resolution is the safest available proxy for the data rate.
 */
internal fun lowestDataVideoFormatIndex(formats: List<Format>): Int? {
  return dataSaverVideoFormatOrder(formats).firstOrNull()
}

internal fun dataSaverVideoFormatOrder(formats: List<Format>): List<Int> {
  return formats.indices.sortedWith(
    compareBy<Int> {
      formats[it].bitrate.takeIf { bitrate -> bitrate > 0 } ?: Int.MAX_VALUE
    }.thenBy {
      val format = formats[it]
      if (format.width > 0 && format.height > 0) {
        format.width.toLong() * format.height.toLong()
      } else {
        Long.MAX_VALUE
      }
    }
  )
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
  subtitleOffset: AtomicLong = AtomicLong(0L),
  /**
   * Where to begin.
   *
   * Given to the source rather than seeked for afterwards. A seek issued before a player has
   * prepared is a request against a timeline that does not exist yet, and it was being dropped:
   * every title opened from Continue watching started from the beginning.
   */
  startPositionMs: Long = 0L,
  /** Phones use a faster restart profile; televisions retain the deeper safety buffer. */
  isTelevision: Boolean =
    context.packageManager.hasSystemFeature(PackageManager.FEATURE_LEANBACK),
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
  val mediaSource = createHlsMediaSource(context, request)
  val bufferProfile = playbackBufferProfile(isTelevision)
  val loadControl =
    DefaultLoadControl.Builder()
      // Slow links need time rather than bytes: keep up to 75 seconds ahead, wait for a useful five
      // seconds before the first frame, and rebuild a larger cushion after any interruption.
      .setBufferDurationsMsForStreaming(
        bufferProfile.minBufferMs,
        bufferProfile.maxBufferMs,
        bufferProfile.startBufferMs,
        bufferProfile.rebufferMs,
      )
      .setPrioritizeTimeOverSizeThresholdsForStreaming(true)
      .build()
  val renderersFactory =
    OffsetSubtitleRenderersFactory(context, subtitleOffset)
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
        selectionBuilder
          .setMaxVideoSize(STARTUP_MAX_VIDEO_WIDTH, STARTUP_MAX_VIDEO_HEIGHT)
          .setMaxVideoBitrate(STABLE_MAX_VIDEO_BITRATE)
        if (!isTelevision) {
          selectionBuilder.setMaxVideoFrameRate(PHONE_AUTO_MAX_VIDEO_FRAME_RATE)
        }
      }
      trackSelectionParameters = selectionBuilder.build()
      if (startPositionMs > 0L) setMediaSource(mediaSource, startPositionMs) else setMediaSource(mediaSource)
    }
}

@androidx.annotation.OptIn(UnstableApi::class)
internal fun createHlsMediaSource(
  context: android.content.Context,
  request: HlsStreamRequest,
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
      .setConnectTimeoutMs(RELIABLE_HTTP_CONNECT_TIMEOUT_MS)
      // A segment arriving slowly is still progress. Let it finish instead of turning a weak but
      // usable connection into a timeout/retry loop.
      .setReadTimeoutMs(RELIABLE_HTTP_READ_TIMEOUT_MS)
      .setDefaultRequestProperties(requestProperties)
  val dataSourceFactory = DefaultDataSource.Factory(context, httpFactory)
  val mediaItem = createMediaItem(request)
  val mediaSourceFactory =
    DefaultMediaSourceFactory(dataSourceFactory)
    .setLoadErrorHandlingPolicy(
      reliableHlsLoadErrorPolicy(
        if (request.sourceIndex < request.sourceCount - 1) {
          BACKUP_AVAILABLE_RETRY_COUNT
        } else {
          RELIABLE_HLS_RETRY_COUNT
        }
      )
    )
  request.drm?.inlineClearKeyResponse()?.let { keyResponse ->
    val drmSessionManager =
      DefaultDrmSessionManager.Builder()
        .setUuidAndExoMediaDrmProvider(C.CLEARKEY_UUID, FrameworkMediaDrm.DEFAULT_PROVIDER)
        .setMultiSession(true)
        .build(LocalMediaDrmCallback(keyResponse.toByteArray(Charsets.UTF_8)))
    mediaSourceFactory.setDrmSessionManagerProvider { drmSessionManager }
  }
  return mediaSourceFactory.createMediaSource(mediaItem)
}

internal fun reliableHlsLoadErrorPolicy(
  retryCount: Int = RELIABLE_HLS_RETRY_COUNT,
): DefaultLoadErrorHandlingPolicy = DefaultLoadErrorHandlingPolicy(retryCount)

/** Applies only a temporary ceiling; the adaptive selector remains responsible for the rendition. */
@androidx.annotation.OptIn(UnstableApi::class)
internal fun applyAutomaticQualityPhase(
  player: Player,
  phase: AutomaticQualityPhase,
  isTelevision: Boolean = true,
) {
  val builder = player.trackSelectionParameters.buildUpon()
  when (phase) {
    AutomaticQualityPhase.LOW_STARTUP ->
      builder
        .setMaxVideoSize(STARTUP_MAX_VIDEO_WIDTH, STARTUP_MAX_VIDEO_HEIGHT)
        .setMaxVideoBitrate(STABLE_MAX_VIDEO_BITRATE)
        .setMaxVideoFrameRate(if (isTelevision) Int.MAX_VALUE else PHONE_AUTO_MAX_VIDEO_FRAME_RATE)
    AutomaticQualityPhase.BALANCED ->
      builder
        .setMaxVideoSize(COMPATIBILITY_MAX_VIDEO_WIDTH, COMPATIBILITY_MAX_VIDEO_HEIGHT)
        .setMaxVideoBitrate(if (isTelevision) Int.MAX_VALUE else PHONE_AUTO_MAX_VIDEO_BITRATE)
        .setMaxVideoFrameRate(if (isTelevision) Int.MAX_VALUE else PHONE_AUTO_MAX_VIDEO_FRAME_RATE)
    AutomaticQualityPhase.UNRESTRICTED -> {
      if (isTelevision) {
        builder.clearVideoSizeConstraints().setMaxVideoBitrate(Int.MAX_VALUE)
      } else {
        // Fast access to the internet does not guarantee a streaming host can sustain its highest
        // rendition. Keep phone Auto inside an efficient 720p/30fps envelope; fixed quality choices
        // still clear this ceiling when the viewer explicitly asks for one.
        builder
          .setMaxVideoSize(PHONE_AUTO_MAX_VIDEO_WIDTH, PHONE_AUTO_MAX_VIDEO_HEIGHT)
          .setMaxVideoBitrate(PHONE_AUTO_MAX_VIDEO_BITRATE)
          .setMaxVideoFrameRate(PHONE_AUTO_MAX_VIDEO_FRAME_RATE)
      }
    }
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

internal fun isHlsTrackMappingFailure(error: Throwable): Boolean =
  generateSequence(error) { it.cause }.any { cause ->
    cause.javaClass.simpleName == "SampleQueueMappingException"
  }

internal fun isBehindLiveWindowFailure(error: Throwable): Boolean =
  generateSequence(error) { it.cause }.any { cause -> cause is BehindLiveWindowException }

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
    .apply {
      request.mimeType?.let(::setMimeType)
      request.drm?.toMedia3Configuration()?.let(::setDrmConfiguration)
    }
    .setSubtitleConfigurations(subtitles)
    .build()
}

private fun StreamDrmConfiguration.toMedia3Configuration(): MediaItem.DrmConfiguration? {
  val licenseUri =
    when {
      scheme != StreamDrmScheme.CLEARKEY -> license.substringBefore('|').trim()
      license.startsWith("http://", ignoreCase = true) ||
        license.startsWith("https://", ignoreCase = true) -> license.substringBefore('|').trim()
      else -> null
    }
  val uuid = if (scheme == StreamDrmScheme.CLEARKEY) C.CLEARKEY_UUID else C.WIDEVINE_UUID
  return MediaItem.DrmConfiguration.Builder(uuid)
    .apply { licenseUri?.takeIf(String::isNotBlank)?.let(::setLicenseUri) }
    .setMultiSession(true)
    .setLicenseRequestHeaders(requestHeaders)
    .build()
}

private fun StreamDrmConfiguration.inlineClearKeyResponse(): String? =
  if (
    scheme == StreamDrmScheme.CLEARKEY &&
      !license.startsWith("http://", ignoreCase = true) &&
      !license.startsWith("https://", ignoreCase = true)
  ) {
    normalizeClearKeyLicense(license)
  } else {
    null
  }

/** Turns Kodi-style ClearKey values into the JWK response Android's ClearKey CDM expects. */
internal fun normalizeClearKeyLicense(raw: String): String? {
  val value = raw.trim()
  if (value.startsWith("{")) {
    val parsed = runCatching { org.json.JSONObject(value) }.getOrNull() ?: return null
    if (parsed.has("keys")) return parsed.toString()
    val pairs =
      parsed.keys().asSequence().mapNotNull { kid ->
        parsed.optString(kid).takeIf { isHexKey(kid) && isHexKey(it) }?.let { kid to it }
      }.toList()
    return clearKeyJwk(pairs)
  }
  val pairs =
    value.split(',').mapNotNull { pair ->
      val separator = pair.indexOf(':')
      if (separator <= 0) return@mapNotNull null
      val kid = pair.substring(0, separator).trim()
      val key = pair.substring(separator + 1).trim()
      (kid to key).takeIf { isHexKey(kid) && isHexKey(key) }
    }
  return clearKeyJwk(pairs)
}

private fun isHexKey(value: String): Boolean =
  value.length == 32 && value.all { it.isDigit() || it.lowercaseChar() in 'a'..'f' }

private fun clearKeyJwk(pairs: List<Pair<String, String>>): String? {
  if (pairs.isEmpty()) return null
  val keys =
    pairs.joinToString(",") { (kid, key) ->
      "{\"kty\":\"oct\",\"k\":\"${hexToBase64Url(key)}\",\"kid\":\"${hexToBase64Url(kid)}\"}"
    }
  return "{\"keys\":[$keys],\"type\":\"temporary\"}"
}

private const val BASE64_URL_ALPHABET =
  "ABCDEFGHIJKLMNOPQRSTUVWXYZabcdefghijklmnopqrstuvwxyz0123456789-_"

private fun hexToBase64Url(hex: String): String {
  val bytes = ByteArray(hex.length / 2) { index -> hex.substring(index * 2, index * 2 + 2).toInt(16).toByte() }
  val answer = StringBuilder((bytes.size * 4 + 2) / 3)
  var buffer = 0
  var bits = 0
  bytes.forEach { byte ->
    buffer = (buffer shl 8) or (byte.toInt() and 0xff)
    bits += 8
    while (bits >= 6) {
      bits -= 6
      answer.append(BASE64_URL_ALPHABET[(buffer shr bits) and 0x3f])
    }
  }
  if (bits > 0) answer.append(BASE64_URL_ALPHABET[(buffer shl (6 - bits)) and 0x3f])
  return answer.toString()
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
