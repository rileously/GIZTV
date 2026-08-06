package com.example.auroratv.ui.player

import android.content.Context
import android.os.Looper
import androidx.annotation.OptIn
import androidx.media3.common.C
import androidx.media3.common.Format
import androidx.media3.common.text.Cue
import androidx.media3.common.text.CueGroup
import androidx.media3.common.util.UnstableApi
import com.google.common.collect.ImmutableList
import androidx.media3.exoplayer.DefaultRenderersFactory
import androidx.media3.exoplayer.ForwardingRenderer
import androidx.media3.exoplayer.Renderer
import androidx.media3.exoplayer.RendererConfiguration
import androidx.media3.exoplayer.source.MediaSource.MediaPeriodId
import androidx.media3.exoplayer.source.SampleStream
import androidx.media3.exoplayer.text.TextOutput
import java.util.concurrent.atomic.AtomicLong

internal const val MAX_SUBTITLE_SYNC_MS = 60_000L

/**
 * Gives every text renderer a clock that can move independently of the picture and sound.
 *
 * Subtitle parsing normally happens while media is loaded. Changing a parser offset after that is
 * too late because buffered cues already contain their final timestamps. Moving the text renderer's
 * clock instead applies to embedded and separate subtitles on the very next render pass.
 */
@OptIn(UnstableApi::class)
internal class OffsetSubtitleRenderersFactory(
  context: Context,
  private val offsetMs: AtomicLong,
) : DefaultRenderersFactory(context) {
  override fun buildTextRenderers(
    context: Context,
    output: TextOutput,
    outputLooper: Looper,
    extensionRendererMode: Int,
    out: ArrayList<Renderer>,
  ) {
    val firstTextRenderer = out.size
    // Filtering here rather than where subtitles are fetched, because it is the last point every
    // track passes through: the ones this app finds separately, and the ones that arrive inside
    // the stream, are both on their way to the screen by now.
    super.buildTextRenderers(
      context,
      AdFreeTextOutput(output),
      outputLooper,
      extensionRendererMode,
      out,
    )
    for (index in firstTextRenderer until out.size) {
      if (out[index].trackType == C.TRACK_TYPE_TEXT) {
        out[index] = SubtitleOffsetRenderer(out[index], offsetMs)
      }
    }
  }
}

/**
 * Keeps a track's own advertising off the picture.
 *
 * The renderer produces whatever the file contained; this decides what reaches the screen. A group
 * left with no cues at all is still passed on, so an advert simply leaves a gap where it was
 * rather than freezing the previous line on screen.
 */
@OptIn(UnstableApi::class)
internal class AdFreeTextOutput(private val delegate: TextOutput) : TextOutput {
  override fun onCues(cueGroup: CueGroup) {
    val kept = cueGroup.cues.filterNot { isPromotionalSubtitleCue(it.text) }
    if (kept.size == cueGroup.cues.size) {
      delegate.onCues(cueGroup)
    } else {
      delegate.onCues(CueGroup(ImmutableList.copyOf(kept), cueGroup.presentationTimeUs))
    }
  }

  @Deprecated("Superseded by the CueGroup overload, which media3 calls.")
  override fun onCues(cues: MutableList<Cue>) {
    @Suppress("DEPRECATION")
    delegate.onCues(cues.filterNot { isPromotionalSubtitleCue(it.text) }.toMutableList())
  }
}

/** For positive offsets the subtitle clock runs behind; negative offsets make it run ahead. */
@OptIn(UnstableApi::class)
internal class SubtitleOffsetRenderer(
  renderer: Renderer,
  private val offsetMs: AtomicLong,
) : ForwardingRenderer(renderer) {
  override fun enable(
    configuration: RendererConfiguration,
    formats: Array<Format>,
    stream: SampleStream,
    positionUs: Long,
    joining: Boolean,
    mayRenderStartOfStream: Boolean,
    startPositionUs: Long,
    offsetUs: Long,
    mediaPeriodId: MediaPeriodId,
  ) {
    super.enable(
      configuration,
      formats,
      stream,
      subtitleRendererPositionUs(positionUs, offsetMs.get()),
      joining,
      mayRenderStartOfStream,
      startPositionUs,
      offsetUs,
      mediaPeriodId,
    )
  }

  override fun resetPosition(positionUs: Long, joining: Boolean) {
    super.resetPosition(subtitleRendererPositionUs(positionUs, offsetMs.get()), joining)
  }

  override fun supportsResetPositionWithoutKeyFrameReset(positionUs: Long): Boolean =
    super.supportsResetPositionWithoutKeyFrameReset(
      subtitleRendererPositionUs(positionUs, offsetMs.get())
    )

  override fun getDurationToProgressUs(positionUs: Long, elapsedRealtimeUs: Long): Long =
    super.getDurationToProgressUs(
      subtitleRendererPositionUs(positionUs, offsetMs.get()),
      elapsedRealtimeUs,
    )

  override fun render(positionUs: Long, elapsedRealtimeUs: Long) {
    super.render(subtitleRendererPositionUs(positionUs, offsetMs.get()), elapsedRealtimeUs)
  }
}

internal fun subtitleRendererPositionUs(playbackPositionUs: Long, offsetMs: Long): Long {
  val safeOffsetUs = offsetMs.coerceIn(-MAX_SUBTITLE_SYNC_MS, MAX_SUBTITLE_SYNC_MS) * 1_000L
  return (playbackPositionUs - safeOffsetUs).coerceAtLeast(0L)
}
