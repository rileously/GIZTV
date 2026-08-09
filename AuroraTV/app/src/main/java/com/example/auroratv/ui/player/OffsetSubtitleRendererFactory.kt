package com.example.auroratv.ui.player

import android.content.Context
import android.media.MediaFormat
import android.os.Build
import android.os.Handler
import android.os.Looper
import androidx.annotation.OptIn
import androidx.media3.common.C
import androidx.media3.common.Format
import androidx.media3.common.MimeTypes
import androidx.media3.common.text.Cue
import androidx.media3.common.text.CueGroup
import androidx.media3.common.util.UnstableApi
import com.google.common.collect.ImmutableList
import androidx.media3.exoplayer.DefaultRenderersFactory
import androidx.media3.exoplayer.ForwardingRenderer
import androidx.media3.exoplayer.Renderer
import androidx.media3.exoplayer.RendererConfiguration
import androidx.media3.exoplayer.audio.AudioRendererEventListener
import androidx.media3.exoplayer.audio.AudioSink
import androidx.media3.exoplayer.audio.MediaCodecAudioRenderer
import androidx.media3.exoplayer.mediacodec.MediaCodecAdapter
import androidx.media3.exoplayer.mediacodec.MediaCodecSelector
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

  /**
   * Swaps the platform audio renderer for one that asks the decoder not to quieten anything.
   *
   * Replaced rather than added alongside, so there is still exactly one decoder for the sound.
   */
  override fun buildAudioRenderers(
    context: Context,
    extensionRendererMode: Int,
    mediaCodecSelector: MediaCodecSelector,
    enableDecoderFallback: Boolean,
    audioSink: AudioSink,
    eventHandler: Handler,
    eventListener: AudioRendererEventListener,
    out: ArrayList<Renderer>,
  ) {
    val firstAudioRenderer = out.size
    super.buildAudioRenderers(
      context,
      extensionRendererMode,
      mediaCodecSelector,
      enableDecoderFallback,
      audioSink,
      eventHandler,
      eventListener,
      out,
    )
    for (index in firstAudioRenderer until out.size) {
      if (out[index] is MediaCodecAudioRenderer) {
        out[index] =
          FullDynamicRangeAudioRenderer(
            context,
            codecAdapterFactory,
            mediaCodecSelector,
            enableDecoderFallback,
            eventHandler,
            eventListener,
            audioSink,
          )
        break
      }
    }
  }
}

/**
 * Plays a soundtrack at the level it was mixed at.
 *
 * Android's AAC decoder normalises loudness and compresses dynamic range by default, using the
 * metadata a stream carries about how loud it believes itself to be. On a film that means the whole
 * mix is pulled down towards a speech reference level and the range between the quietest and
 * loudest parts is squeezed — which is heard as a picture that is too quiet overall and, worse, as
 * dialogue sitting on top of a score that has been pushed underneath it. The reported symptom
 * exactly: low volume, and a mix that sounds filtered down to the voices.
 *
 * These keys turn all of it off, so the decoder hands over what was authored: no normalisation to
 * a target level, no boosting of quiet passages, no attenuation of loud ones, and none of the
 * heavier compression profile. The viewer's own volume control then governs loudness, which is
 * where that decision belongs.
 *
 * Only AAC is touched, because these are AAC and xHE-AAC decoder keys; every other codec is handed
 * to the platform exactly as before.
 */
@OptIn(UnstableApi::class)
internal class FullDynamicRangeAudioRenderer(
  context: Context,
  codecAdapterFactory: MediaCodecAdapter.Factory,
  mediaCodecSelector: MediaCodecSelector,
  enableDecoderFallback: Boolean,
  eventHandler: Handler?,
  eventListener: AudioRendererEventListener?,
  audioSink: AudioSink,
) : MediaCodecAudioRenderer(
    context,
    codecAdapterFactory,
    mediaCodecSelector,
    enableDecoderFallback,
    eventHandler,
    eventListener,
    audioSink,
  ) {
  override fun getMediaFormat(
    format: Format,
    codecMimeType: String,
    codecMaxInputSize: Int,
    codecOperatingRate: Float,
  ): MediaFormat {
    val mediaFormat = super.getMediaFormat(format, codecMimeType, codecMaxInputSize, codecOperatingRate)
    if (isAacMimeType(codecMimeType) || isAacMimeType(format.sampleMimeType)) {
      applyFullDynamicRange(mediaFormat)
    }
    return mediaFormat
  }
}

private fun isAacMimeType(mimeType: String?): Boolean {
  val normalized = mimeType?.lowercase() ?: return false
  return normalized == MimeTypes.AUDIO_AAC || normalized.contains("mp4a") || normalized.contains("aac")
}

/**
 * The decoder keys that together mean "leave it alone".
 *
 * A target reference level outside 0..127 is how the platform is told not to normalise loudness at
 * all. Boost and attenuation at zero mean no dynamic-range gain is applied in either direction, and
 * the heavy compression profile is off. On API 28 and above the MPEG-D effect is switched off too,
 * which is the one that governs xHE-AAC.
 */
private fun applyFullDynamicRange(mediaFormat: MediaFormat) {
  mediaFormat.setInteger(MediaFormat.KEY_AAC_DRC_TARGET_REFERENCE_LEVEL, -1)
  mediaFormat.setInteger(MediaFormat.KEY_AAC_DRC_BOOST_FACTOR, 0)
  mediaFormat.setInteger(MediaFormat.KEY_AAC_DRC_ATTENUATION_FACTOR, 0)
  mediaFormat.setInteger(MediaFormat.KEY_AAC_DRC_HEAVY_COMPRESSION, 0)
  if (Build.VERSION.SDK_INT >= Build.VERSION_CODES.P) {
    mediaFormat.setInteger(MediaFormat.KEY_AAC_DRC_EFFECT_TYPE, AAC_DRC_EFFECT_NONE)
  }
}

/** `MediaFormat.DRC_EFFECT_TYPE_NONE`, named here because the constant is API 28. */
private const val AAC_DRC_EFFECT_NONE = -1

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
    val kept =
      cueGroup.cues.filterNot { isPromotionalSubtitleCue(it.text) }.map { it.atViewerChosenPosition() }
    delegate.onCues(CueGroup(ImmutableList.copyOf(kept), cueGroup.presentationTimeUs))
  }

  @Deprecated("Superseded by the CueGroup overload, which media3 calls.")
  override fun onCues(cues: MutableList<Cue>) {
    @Suppress("DEPRECATION")
    delegate.onCues(
      cues.filterNot { isPromotionalSubtitleCue(it.text) }.map { it.atViewerChosenPosition() }.toMutableList()
    )
  }
}

/**
 * Drops a cue's own idea of where it belongs on screen.
 *
 * `SubtitleView.setBottomPaddingFraction` only governs cues that do not carry a position of their
 * own, and WebVTT lines nearly always do — so the viewer could move the Subtitle position setting
 * between Bottom, Raised and High, watch the value change, and see the text stay exactly where it
 * was. Clearing the line here hands placement back to the setting.
 *
 * The cost is that a track which deliberately lifted a line clear of on-screen text no longer can.
 * That is the right trade: a setting the viewer has asked for should win over a guess the file made
 * about a screen it never saw.
 */
@OptIn(UnstableApi::class)
private fun Cue.atViewerChosenPosition(): Cue =
  if (line == Cue.DIMEN_UNSET && position == Cue.DIMEN_UNSET) {
    this
  } else {
    buildUpon()
      .setLine(Cue.DIMEN_UNSET, Cue.LINE_TYPE_FRACTION)
      .setLineAnchor(Cue.TYPE_UNSET)
      .setPosition(Cue.DIMEN_UNSET)
      .setPositionAnchor(Cue.TYPE_UNSET)
      .build()
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
