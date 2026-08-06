package com.example.auroratv

import com.example.auroratv.ui.player.ExternalSubtitleTrack
import com.example.auroratv.ui.player.nearbySubtitleCues
import com.example.auroratv.ui.player.parseSubRipCues
import com.example.auroratv.ui.player.parseSubtitleCues
import com.example.auroratv.ui.player.parseSubtitleTimestamp
import com.example.auroratv.ui.player.parseWebVttCues
import com.example.auroratv.ui.player.resolveSubtitleTrackForCueMatch
import com.example.auroratv.ui.player.subtitleOffsetForCueMatch
import org.junit.Assert.assertEquals
import org.junit.Assert.assertTrue
import org.junit.Test

class SubtitleCueParserTest {

  @Test
  fun webVtt_parsesTimedCuesAndStripsTags() {
    val body =
      """
      WEBVTT

      1
      00:01:00.000 --> 00:01:02.500
      <c>Hello</c> there

      00:01:05.000 --> 00:01:07.000
      Second line
      """.trimIndent()
    val cues = parseWebVttCues(body)
    assertEquals(2, cues.size)
    assertEquals(60_000L, cues[0].startMs)
    assertEquals(62_500L, cues[0].endMs)
    assertEquals("Hello there", cues[0].text)
    assertEquals("Second line", cues[1].text)
  }

  @Test
  fun subRip_parsesCommaMilliseconds() {
    val body =
      """
      1
      00:00:01,000 --> 00:00:03,000
      First

      2
      00:00:04,500 --> 00:00:06,000
      Second
      """.trimIndent()
    val cues = parseSubRipCues(body)
    assertEquals(2, cues.size)
    assertEquals(1_000L, cues[0].startMs)
    assertEquals(4_500L, cues[1].startMs)
    assertEquals("Second", cues[1].text)
  }

  @Test
  fun nearbyWindow_keepsOnlyCuesAroundPosition() {
    val cues =
      parseSubtitleCues(
        """
        WEBVTT

        00:00:10.000 --> 00:00:11.000
        Far early

        00:01:00.000 --> 00:01:01.000
        Near

        00:02:00.000 --> 00:02:01.000
        Far late
        """.trimIndent(),
        mimeType = "text/vtt",
      )
    val nearby = nearbySubtitleCues(cues, positionMs = 60_000L, windowMs = 45_000L)
    assertEquals(listOf("Near"), nearby.map { it.text })
  }

  @Test
  fun cueMatchOffset_alignsCueStartToPlaybackPosition() {
    // Spoken line at 1:00; cue file says the line starts at 0:55 → captions are 5s early → +5s later.
    assertEquals(5_000L, subtitleOffsetForCueMatch(playbackPositionMs = 60_000L, cueStartMs = 55_000L))
    assertEquals(-2_000L, subtitleOffsetForCueMatch(playbackPositionMs = 60_000L, cueStartMs = 62_000L))
    assertEquals(30_000L, subtitleOffsetForCueMatch(playbackPositionMs = 90_000L, cueStartMs = 0L))
  }

  @Test
  fun timestampParser_acceptsShortAndLongForms() {
    assertEquals(90_500L, parseSubtitleTimestamp("01:30.500"))
    assertEquals(3_665_000L, parseSubtitleTimestamp("01:01:05.000"))
    assertEquals(3_000L, parseSubtitleTimestamp("00:00:03,000"))
  }

  @Test
  fun resolveTrack_prefersSelectedLabelThenSecondEnglish() {
    val tracks =
      listOf(
        ExternalSubtitleTrack("https://a/en1.vtt", "English", "en", "text/vtt"),
        ExternalSubtitleTrack("https://a/en2.vtt", "English", "en", "text/vtt"),
        ExternalSubtitleTrack("https://a/es.vtt", "Spanish", "es", "text/vtt"),
      )
    assertEquals("https://a/en2.vtt", resolveSubtitleTrackForCueMatch(tracks, "English 2")?.url)
    assertEquals("https://a/en2.vtt", resolveSubtitleTrackForCueMatch(tracks, "Auto English")?.url)
    assertTrue(resolveSubtitleTrackForCueMatch(tracks, "Spanish")?.label == "Spanish")
  }
}
