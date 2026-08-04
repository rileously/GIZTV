package com.example.auroratv.ui.player

import androidx.annotation.OptIn
import androidx.media3.common.C
import androidx.media3.common.Format
import androidx.media3.common.util.Consumer
import androidx.media3.common.util.UnstableApi
import androidx.media3.extractor.text.CuesWithTiming
import androidx.media3.extractor.text.DefaultSubtitleParserFactory
import androidx.media3.extractor.text.SubtitleParser
import java.util.concurrent.atomic.AtomicLong

/**
 * Shifts parsed subtitle cues without changing video or audio timing.
 *
 * The shift is read at the moment each cue is parsed rather than fixed when the player is built.
 * Baking it in meant that changing it needed a new media source, and a new media source meant the
 * film stopped and started again to move a caption a quarter of a second.
 */
@OptIn(UnstableApi::class)
internal class OffsetSubtitleParserFactory(private val offsetMs: AtomicLong) : SubtitleParser.Factory {
  private val delegate = DefaultSubtitleParserFactory()

  override fun supportsFormat(format: Format): Boolean = delegate.supportsFormat(format)

  override fun getCueReplacementBehavior(format: Format): Int = delegate.getCueReplacementBehavior(format)

  override fun create(format: Format): SubtitleParser {
    val parser = delegate.create(format)
    return object : SubtitleParser {
      override fun parse(
        data: ByteArray,
        offset: Int,
        length: Int,
        outputOptions: SubtitleParser.OutputOptions,
        output: Consumer<CuesWithTiming>,
      ) {
        val offsetUs = offsetMs.get() * 1_000L
        parser.parse(data, offset, length, outputOptions) { cues ->
          output.accept(shiftSubtitleCues(cues, offsetUs))
        }
      }

      override fun reset() = parser.reset()

      override fun getCueReplacementBehavior(): Int = parser.cueReplacementBehavior
    }
  }
}

@OptIn(UnstableApi::class)
internal fun shiftSubtitleCues(cues: CuesWithTiming, offsetUs: Long): CuesWithTiming {
  if (offsetUs == 0L || cues.startTimeUs == C.TIME_UNSET) return cues
  val rawStartUs = cues.startTimeUs + offsetUs
  val shiftedStartUs = rawStartUs.coerceAtLeast(0L)
  val clippedDurationUs =
    if (cues.durationUs == C.TIME_UNSET) {
      C.TIME_UNSET
    } else {
      (cues.durationUs - (shiftedStartUs - rawStartUs)).coerceAtLeast(0L)
    }
  return CuesWithTiming(cues.cues, shiftedStartUs, clippedDurationUs)
}
