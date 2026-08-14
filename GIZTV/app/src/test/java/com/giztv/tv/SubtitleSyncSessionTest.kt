package com.giztv.tv

import com.giztv.tv.ui.player.MAX_SUBTITLE_SYNC_MS
import com.giztv.tv.ui.player.SubtitleCue
import com.giztv.tv.ui.player.SubtitleSyncSession
import com.giztv.tv.ui.player.subtitleSyncNudgeStepMs
import com.giztv.tv.ui.player.suggestedSubtitleCue
import org.junit.Assert.assertEquals
import org.junit.Assert.assertNull
import org.junit.Test

class SubtitleSyncSessionTest {
  @Test
  fun nudge_resetAndUndoAreReversible() {
    val session = SubtitleSyncSession(openingOffsetMs = 700L)

    assertEquals(600L, session.nudge(-100L).currentOffsetMs)
    assertEquals(0L, session.nudge(500L).reset().currentOffsetMs)
    assertEquals(700L, session.nudge(500L).undo().currentOffsetMs)
    assertEquals(
      MAX_SUBTITLE_SYNC_MS,
      session.nudge(MAX_SUBTITLE_SYNC_MS).currentOffsetMs,
    )
  }

  @Test
  fun matchNowUsesThePositionAtTheActualButtonPress() {
    val cue = SubtitleCue(startMs = 55_000L, endMs = 57_000L, text = "Listen for me")
    val session = SubtitleSyncSession(openingOffsetMs = 0L)

    val matched = session.matchNow(playbackPositionMs = 60_000L, cue = cue)

    assertEquals(5_000L, matched.currentOffsetMs)
    assertEquals(55_000L, matched.matchedCueStartMs)
  }

  @Test
  fun heldNudgeAcceleratesAfterSeveralRepeats() {
    assertEquals(100L, subtitleSyncNudgeStepMs(repeatCount = 0))
    assertEquals(100L, subtitleSyncNudgeStepMs(repeatCount = 3))
    assertEquals(500L, subtitleSyncNudgeStepMs(repeatCount = 4))
  }

  @Test
  fun cueSuggestionUsesTheAdjustedSubtitleClock() {
    val cues =
      listOf(
        SubtitleCue(8_000L, 9_000L, "Past"),
        SubtitleCue(12_000L, 13_000L, "Next"),
      )

    assertEquals("Next", suggestedSubtitleCue(cues, playbackPositionMs = 15_000L, offsetMs = 3_000L)?.text)
    assertEquals("Past", suggestedSubtitleCue(cues, playbackPositionMs = 8_500L, offsetMs = 0L)?.text)
    assertNull(suggestedSubtitleCue(emptyList(), playbackPositionMs = 1_000L, offsetMs = 0L))
  }
}
