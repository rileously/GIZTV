package com.giztv.tv.ui.player

import androidx.compose.foundation.Canvas
import androidx.compose.foundation.background
import androidx.compose.foundation.clickable
import androidx.compose.foundation.focusable
import androidx.compose.foundation.layout.Box
import androidx.compose.foundation.layout.Column
import androidx.compose.foundation.layout.Row
import androidx.compose.foundation.layout.Spacer
import androidx.compose.foundation.layout.fillMaxSize
import androidx.compose.foundation.layout.fillMaxWidth
import androidx.compose.foundation.layout.height
import androidx.compose.foundation.layout.padding
import androidx.compose.foundation.layout.size
import androidx.compose.foundation.layout.width
import androidx.compose.foundation.shape.CircleShape
import androidx.compose.foundation.shape.RoundedCornerShape
import androidx.compose.material.icons.Icons
import androidx.compose.material.icons.filled.ArrowBack
import androidx.compose.material.icons.filled.Forward10
import androidx.compose.material.icons.filled.Pause
import androidx.compose.material.icons.filled.PlayArrow
import androidx.compose.material.icons.filled.Replay10
import androidx.compose.material3.Icon
import androidx.compose.material3.Slider
import androidx.compose.material3.SliderDefaults
import androidx.compose.material3.Text
import androidx.compose.runtime.Composable
import androidx.compose.runtime.DisposableEffect
import androidx.compose.runtime.LaunchedEffect
import androidx.compose.runtime.getValue
import androidx.compose.runtime.mutableLongStateOf
import androidx.compose.runtime.mutableStateOf
import androidx.compose.runtime.remember
import androidx.compose.runtime.setValue
import androidx.compose.ui.Alignment
import androidx.compose.ui.Modifier
import androidx.compose.ui.draw.clip
import androidx.compose.ui.focus.FocusRequester
import androidx.compose.ui.focus.focusRequester
import androidx.compose.ui.graphics.Color
import androidx.compose.ui.graphics.ImageBitmap
import androidx.compose.ui.graphics.vector.ImageVector
import androidx.compose.ui.input.key.Key
import androidx.compose.ui.input.key.KeyEventType
import androidx.compose.ui.input.key.key
import androidx.compose.ui.input.key.onPreviewKeyEvent
import androidx.compose.ui.input.key.type
import androidx.compose.ui.input.pointer.pointerInput
import androidx.compose.ui.unit.IntOffset
import androidx.compose.ui.unit.IntSize
import androidx.compose.ui.text.font.FontWeight
import androidx.compose.ui.text.style.TextAlign
import androidx.compose.ui.unit.Dp
import androidx.compose.ui.unit.dp
import androidx.compose.ui.unit.sp
import com.giztv.tv.data.PlaybackContext
import com.giztv.tv.theme.GizMint
import com.giztv.tv.theme.NightSurface
import com.giztv.tv.theme.SoftWhite
import kotlinx.coroutines.delay

@Composable
internal fun ModernTransportControl(
  icon: ImageVector,
  label: String,
  onClick: () -> Unit,
  modifier: Modifier = Modifier,
  size: Dp = 44.dp,
  onInteraction: () -> Unit = {},
) {
  Box(
    modifier =
      modifier
        .size(size)
        .clip(CircleShape)
        .background(NightSurface.copy(alpha = 0.8f))
        .clickable { onClick() },
    contentAlignment = Alignment.Center,
  ) {
    Icon(
      icon,
      contentDescription = label,
      tint = SoftWhite,
      modifier = Modifier.size(size * 0.55f),
    )
  }
}

/**
 * The desktop player.
 *
 * The address arrives already resolved by the browser side, which is the part that was always
 * working; until now there was nothing behind it but a screen that printed the address and a Back
 * button, which is why nothing on this build ever played. Decoding is VLC's, through vlcj; the
 * picture is copied into Compose (see [VlcVideoEngine]) so the controls can be drawn over it.
 */
@Composable
internal fun HlsPlayerScreen(
  request: HlsStreamRequest,
  onExit: () -> Unit,
  minimized: Boolean = false,
  miniPlayerBottomPadding: Dp = 16.dp,
  onMinimize: () -> Unit = {},
  onExpand: () -> Unit = {},
  onDismissMini: () -> Unit = onExit,
  onPlayNext: (PlaybackContext) -> Unit = {},
  onPrepareNext: (PlaybackContext) -> Unit = {},
  onHandedOver: () -> Unit = {},
  onSwitchServer: (Int) -> Unit = {},
  onPlaybackFailed: () -> Boolean = { false },
  onPlaybackStable: () -> Unit = {},
) {
  val engine = remember(request.url) { VlcVideoEngine() }
  val preferredSubtitleIndex =
    remember(request.url, request.subtitles) {
      preferredEnglishSubtitleIndex(
        count = request.subtitles.size,
        isEnglish = { index ->
          request.subtitles[index].let { isEnglishSubtitleLabel(it.label, it.language) }
        },
        isHearingImpaired = { index -> isHearingImpairedSubtitleLabel(request.subtitles[index].label) },
      ) ?: request.subtitles.indices.firstOrNull()
    }
  var selectedSubtitleIndex by remember(request.url, request.subtitles) {
    mutableStateOf(preferredSubtitleIndex)
  }
  var subtitleCues by remember(request.url) { mutableStateOf(emptyList<SubtitleCue>()) }
  var subtitleLoading by remember(request.url) { mutableStateOf(false) }
  var subtitleLoadError by remember(request.url) { mutableStateOf<String?>(null) }
  var subtitleSize by remember(request.url) { mutableStateOf(SubtitleSizeOption.NORMAL) }
  var subtitlePosition by remember(request.url) { mutableStateOf(SubtitlePositionOption.BOTTOM) }
  var subtitleStyle by remember(request.url) { mutableStateOf(SubtitleStyleOption.OUTLINE) }
  var subtitleOffsetMs by remember(request.url) { mutableLongStateOf(0L) }
  var subtitleSettingsVisible by remember(request.url) { mutableStateOf(false) }
  var displayedFrameCount by remember(request.url) { mutableLongStateOf(0L) }
  var isPlaying by remember(request.url) { mutableStateOf(true) }
  var positionMs by remember(request.url) { mutableStateOf(-1L) }
  var durationMs by remember(request.url) { mutableStateOf(-1L) }
  var scrubbingTo by remember(request.url) { mutableStateOf<Long?>(null) }
  var controlsVisible by remember(request.url) { mutableStateOf(true) }
  var lastInteraction by remember(request.url) { mutableStateOf(0L) }
  var deadStream by remember(request.url) { mutableStateOf(false) }
  val focusRequester = remember(request.url) { FocusRequester() }

  fun noteInteraction() {
    controlsVisible = true
    lastInteraction = System.currentTimeMillis()
  }

  DisposableEffect(request.url) {
    engine.start(request.url, request.headers)
    onDispose { engine.release() }
  }

  LaunchedEffect(request.url, selectedSubtitleIndex) {
    val track = selectedSubtitleIndex?.let(request.subtitles::getOrNull)
    subtitleLoadError = null
    if (track == null) {
      subtitleCues = emptyList()
      subtitleLoading = false
      return@LaunchedEffect
    }
    subtitleLoading = true
    runCatching {
        val body = downloadSubtitleCueBody(track.url, request.headers)
        parseSubtitleCues(body, track.mimeType).also { cues ->
          check(cues.isNotEmpty()) { "This subtitle file contains no readable captions." }
        }
      }
      .onSuccess { cues -> subtitleCues = cues }
      .onFailure { error ->
        subtitleCues = emptyList()
        subtitleLoadError = error.message ?: "Could not load this subtitle track."
      }
    subtitleLoading = false
  }

  // VLC publishes frames from its own thread. Poll the generation counter at display cadence so
  // Compose redraws smoothly, while the slower player status values are sampled only four times a
  // second. The bitmap itself stays behind VlcVideoEngine's lock: Skia must never try to turn it
  // into an Image while VLC is replacing its pixels.
  LaunchedEffect(request.url) {
    focusRequester.requestFocus()
    val startedAt = System.currentTimeMillis()
    var stableReported = false
    var nextStatusPollAt = 0L
    while (true) {
      val now = System.currentTimeMillis()
      val latestFrameCount = engine.frameCount
      if (latestFrameCount != displayedFrameCount) displayedFrameCount = latestFrameCount
      if (now >= nextStatusPollAt) {
        if (scrubbingTo == null) positionMs = engine.timeMs()
        durationMs = engine.durationMs()
        isPlaying = engine.isPlaying()
        nextStatusPollAt = now + STATUS_POLL_INTERVAL_MS
      }
      // A stream that has produced pictures has proved itself, so the failover chain can forget it
      // was ever in doubt.
      if (!stableReported && latestFrameCount > 0L) {
        stableReported = true
        onPlaybackStable()
      }
      // An address that never becomes a picture is a dead one, and there are other sites holding
      // the same title. Asking for the next of them is what the television build does; without it
      // the desktop simply sat on a black screen for as long as the viewer was willing to watch it.
      if (
        !stableReported &&
          engine.isAvailable &&
          now - startedAt > STARTUP_GIVE_UP_MS
      ) {
        if (!onPlaybackFailed()) deadStream = true
        return@LaunchedEffect
      }
      if (
        controlsVisible &&
          !subtitleSettingsVisible &&
          isPlaying &&
          now - lastInteraction > CONTROLS_IDLE_MS
      ) {
        controlsVisible = false
      }
      delay(FRAME_POLL_INTERVAL_MS)
    }
  }

  Box(
    modifier =
      Modifier
        .fillMaxSize()
        .background(Color.Black)
        .focusRequester(focusRequester)
        .focusable()
        .onPreviewKeyEvent { event ->
          if (event.type != KeyEventType.KeyDown) return@onPreviewKeyEvent false
          noteInteraction()
          when (event.key) {
            Key.Spacebar, Key.K -> {
              engine.setPlaying(!isPlaying)
              true
            }
            Key.DirectionLeft, Key.J -> {
              engine.skip(-SKIP_STEP_MS)
              true
            }
            Key.DirectionRight, Key.L -> {
              engine.skip(SKIP_STEP_MS)
              true
            }
            Key.Escape, Key.Backspace -> {
              if (subtitleSettingsVisible) subtitleSettingsVisible = false else onExit()
              true
            }
            else -> false
          }
        }
        .pointerInput(request.url) {
          awaitPointerEventScope {
            while (true) {
              awaitPointerEvent()
              noteInteraction()
            }
          }
        },
    contentAlignment = Alignment.Center,
  ) {
    val startupError = engine.startupError
    when {
      startupError != null ->
        PlayerMessage(title = "Cannot play video", detail = startupError, onExit = onExit)
      deadStream ->
        PlayerMessage(
          title = request.title ?: "GIZTV Player",
          detail = "This stream did not start. Every server has been tried.",
          onExit = onExit,
        )
      displayedFrameCount > 0L ->
        Canvas(modifier = Modifier.fillMaxSize()) {
          // Reading this state invalidates the draw block for every newly decoded frame.
          displayedFrameCount
          engine.withFrame { image ->
            val scale = minOf(size.width / image.width, size.height / image.height)
            val width = (image.width * scale).toInt().coerceAtLeast(1)
            val height = (image.height * scale).toInt().coerceAtLeast(1)
            drawImage(
              image = image,
              dstOffset = IntOffset((size.width.toInt() - width) / 2, (size.height.toInt() - height) / 2),
              dstSize = IntSize(width, height),
            )
          }
        }
      else ->
        PlayerMessage(
          title = request.title ?: "GIZTV Player",
          detail = "Opening the stream…",
         onExit = onExit,
        )
    }

    desktopSubtitleTextAt(
      cues = subtitleCues,
      playbackPositionMs = positionMs,
      offsetMs = subtitleOffsetMs,
    )?.let { text ->
      DesktopSubtitleCueOverlay(
        text = text,
        size = subtitleSize,
        position = subtitlePosition,
        style = subtitleStyle,
      )
    }

    if (startupError == null && controlsVisible && !subtitleSettingsVisible) {
      PlayerControlsOverlay(
        title = request.title,
        subtitle = request.subtitle,
        isPlaying = isPlaying,
        positionMs = scrubbingTo ?: positionMs,
        durationMs = durationMs,
        onTogglePlay = {
          noteInteraction()
          engine.setPlaying(!isPlaying)
        },
        onSkip = {
          noteInteraction()
          engine.skip(it)
        },
        onScrub = { fraction ->
          noteInteraction()
          if (durationMs > 0L) scrubbingTo = (durationMs * fraction).toLong()
        },
        onScrubFinished = {
          scrubbingTo?.let(engine::seekTo)
          scrubbingTo = null
        },
        subtitleAvailable = request.subtitles.isNotEmpty(),
        subtitleEnabled = selectedSubtitleIndex != null,
        onOpenSubtitleSettings = {
          noteInteraction()
          subtitleSettingsVisible = true
        },
        onExit = onExit,
      )
    }

    if (startupError == null && subtitleSettingsVisible) {
      DesktopSubtitleSettingsPanel(
        tracks = request.subtitles,
        selectedTrackIndex = selectedSubtitleIndex,
        size = subtitleSize,
        position = subtitlePosition,
        style = subtitleStyle,
        offsetMs = subtitleOffsetMs,
        loading = subtitleLoading,
        loadError = subtitleLoadError,
        onTrackSelected = { selectedSubtitleIndex = it },
        onSizeSelected = { subtitleSize = it },
        onPositionSelected = { subtitlePosition = it },
        onStyleSelected = { subtitleStyle = it },
        onOffsetSelected = { subtitleOffsetMs = it },
        onClose = {
          subtitleSettingsVisible = false
          noteInteraction()
        },
      )
    }
  }
}

@Composable
private fun PlayerMessage(title: String, detail: String, onExit: () -> Unit) {
  Column(
    modifier = Modifier.fillMaxWidth().padding(24.dp),
    horizontalAlignment = Alignment.CenterHorizontally,
  ) {
    Text(title, color = SoftWhite, fontWeight = FontWeight.Black, fontSize = 28.sp)
    Spacer(modifier = Modifier.height(12.dp))
    Text(
      detail,
      color = SoftWhite.copy(alpha = 0.75f),
      fontSize = 14.sp,
      textAlign = TextAlign.Center,
    )
    Spacer(modifier = Modifier.height(28.dp))
    ModernTransportControl(icon = Icons.Filled.ArrowBack, label = "Back", onClick = onExit)
  }
}

@Composable
private fun PlayerControlsOverlay(
  title: String?,
  subtitle: String?,
  isPlaying: Boolean,
  positionMs: Long,
  durationMs: Long,
  onTogglePlay: () -> Unit,
  onSkip: (Long) -> Unit,
  onScrub: (Float) -> Unit,
  onScrubFinished: () -> Unit,
  subtitleAvailable: Boolean,
  subtitleEnabled: Boolean,
  onOpenSubtitleSettings: () -> Unit,
  onExit: () -> Unit,
) {
  Box(modifier = Modifier.fillMaxSize()) {
    Row(
      modifier = Modifier.align(Alignment.TopStart).padding(20.dp),
      verticalAlignment = Alignment.CenterVertically,
    ) {
      ModernTransportControl(icon = Icons.Filled.ArrowBack, label = "Back", onClick = onExit)
      Spacer(modifier = Modifier.width(14.dp))
      Column {
        Text(
          title ?: "GIZTV Player",
          color = SoftWhite,
          fontWeight = FontWeight.Black,
          fontSize = 22.sp,
        )
        subtitle?.let { Text(it, color = GizMint, fontSize = 13.sp) }
      }
    }

    Column(
      modifier =
        Modifier
          .align(Alignment.BottomCenter)
          .fillMaxWidth()
          .background(Color.Black.copy(alpha = 0.55f))
          .padding(horizontal = 24.dp, vertical = 16.dp)
    ) {
      val fraction =
        if (durationMs > 0L) (positionMs.coerceAtLeast(0L).toFloat() / durationMs) else 0f
      Slider(
        value = fraction.coerceIn(0f, 1f),
        onValueChange = onScrub,
        onValueChangeFinished = onScrubFinished,
        enabled = durationMs > 0L,
        colors =
          SliderDefaults.colors(
            thumbColor = GizMint,
            activeTrackColor = GizMint,
            inactiveTrackColor = SoftWhite.copy(alpha = 0.25f),
          ),
      )
      Row(verticalAlignment = Alignment.CenterVertically) {
        Text(
          "${formatPlaybackTime(positionMs)} / ${formatPlaybackTime(durationMs)}",
          color = SoftWhite,
          fontSize = 13.sp,
        )
        Spacer(modifier = Modifier.width(20.dp))
        ModernTransportControl(
          icon = Icons.Filled.Replay10,
          label = "Back 10 seconds",
          onClick = { onSkip(-SKIP_STEP_MS) },
        )
        Spacer(modifier = Modifier.width(10.dp))
        ModernTransportControl(
          icon = if (isPlaying) Icons.Filled.Pause else Icons.Filled.PlayArrow,
          label = if (isPlaying) "Pause" else "Play",
          onClick = onTogglePlay,
          size = 54.dp,
        )
        Spacer(modifier = Modifier.width(10.dp))
        ModernTransportControl(
          icon = Icons.Filled.Forward10,
          label = "Forward 10 seconds",
          onClick = { onSkip(SKIP_STEP_MS) },
        )
        if (subtitleAvailable) {
          Spacer(modifier = Modifier.width(10.dp))
          SubtitleToggle(
            enabled = subtitleEnabled,
            onClick = onOpenSubtitleSettings,
          )
        }
      }
    }
  }
}

@Composable
private fun SubtitleToggle(enabled: Boolean, onClick: () -> Unit) {
  Box(
    modifier =
      Modifier
        .height(40.dp)
        .clip(RoundedCornerShape(9.dp))
        .background(if (enabled) GizMint else NightSurface.copy(alpha = 0.8f))
        .clickable(onClick = onClick)
        .padding(horizontal = 11.dp),
    contentAlignment = Alignment.Center,
  ) {
    Text(
      text = "CC",
      color = if (enabled) Color.Black else SoftWhite,
      fontSize = 13.sp,
      fontWeight = FontWeight.Black,
    )
  }
}

/** Long enough that a viewer reaching for the seek bar does not lose it mid-reach. */
private const val CONTROLS_IDLE_MS = 3_000L
private const val SKIP_STEP_MS = 10_000L
/** Roughly one display refresh; unchanged frame counters do not invalidate Compose. */
private const val FRAME_POLL_INTERVAL_MS = 16L
/** Player clocks do not need frame-rate polling. */
private const val STATUS_POLL_INTERVAL_MS = 250L
/**
 * How long an address gets to become a picture before the next site is asked for the title.
 *
 * Generous, because a busy CDN can be slow to open without being dead, and giving up means
 * resolving the whole title again somewhere else.
 */
private const val STARTUP_GIVE_UP_MS = 25_000L

internal fun shouldComposeInAppPlayerSession(
  hasStreamRequest: Boolean,
  fullPlayerVisible: Boolean,
  miniPlayerActive: Boolean,
): Boolean = hasStreamRequest && (fullPlayerVisible || miniPlayerActive)
