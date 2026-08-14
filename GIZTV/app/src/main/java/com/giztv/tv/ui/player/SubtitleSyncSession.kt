package com.giztv.tv.ui.player

/**
 * The small, reversible editing session behind the live subtitle-sync overlay.
 *
 * Keeping this independent of Compose and Media3 makes the important timing rules deterministic:
 * the opening value can always be restored, repeated nudges clamp safely, and matching a spoken
 * line always uses the playback position captured at the button press.
 */
internal data class SubtitleSyncSession(
  val openingOffsetMs: Long,
  val currentOffsetMs: Long = openingOffsetMs,
  val matchedCueStartMs: Long? = null,
) {
  fun nudge(deltaMs: Long): SubtitleSyncSession =
    copy(currentOffsetMs = adjustSubtitleSync(currentOffsetMs, deltaMs), matchedCueStartMs = null)

  fun matchNow(playbackPositionMs: Long, cue: SubtitleCue): SubtitleSyncSession =
    copy(
      currentOffsetMs = subtitleOffsetForCueMatch(playbackPositionMs, cue.startMs),
      matchedCueStartMs = cue.startMs,
    )

  fun reset(): SubtitleSyncSession = copy(currentOffsetMs = 0L, matchedCueStartMs = null)

  fun undo(): SubtitleSyncSession =
    copy(currentOffsetMs = openingOffsetMs, matchedCueStartMs = null)

  fun acceptExternalOffset(offsetMs: Long): SubtitleSyncSession =
    if (offsetMs == currentOffsetMs) this
    else copy(currentOffsetMs = offsetMs.coerceIn(-MAX_SUBTITLE_SYNC_MS, MAX_SUBTITLE_SYNC_MS))
}

/** Fine for the first presses, then fast enough that holding a remote button is useful. */
internal fun subtitleSyncNudgeStepMs(repeatCount: Int): Long =
  if (repeatCount >= SUBTITLE_SYNC_ACCELERATION_REPEAT) 500L else 100L

/** The line currently due, or the next line the viewer can listen for. */
internal fun suggestedSubtitleCue(
  cues: List<SubtitleCue>,
  playbackPositionMs: Long,
  offsetMs: Long,
): SubtitleCue? {
  if (cues.isEmpty()) return null
  val sourceClockMs = (playbackPositionMs - offsetMs).coerceAtLeast(0L)
  return cues.firstOrNull { it.endMs >= sourceClockMs } ?: cues.last()
}

private const val SUBTITLE_SYNC_ACCELERATION_REPEAT = 4
