package com.giztv.tv

import com.giztv.tv.ui.player.SkipKind
import com.giztv.tv.ui.player.SkipSegment
import com.giztv.tv.ui.player.SkipSegments
import com.giztv.tv.ui.player.at
import com.giztv.tv.ui.player.label
import com.giztv.tv.ui.player.parseAniSkipSegments
import com.giztv.tv.ui.player.parseIntroDbSegments
import com.giztv.tv.ui.player.parseMalId
import com.giztv.tv.ui.player.skipTargetMs
import org.junit.Assert.assertEquals
import org.junit.Assert.assertFalse
import org.junit.Assert.assertNull
import org.junit.Assert.assertTrue
import org.junit.Test

/** Fixtures are the responses these two databases actually returned, not shapes invented to suit. */
class SkipSegmentsTest {
  @Test
  fun theIntroDbGivesAnIntroAndCreditsInMilliseconds() {
    // Breaking Bad S1E1, verbatim.
    val segments =
      parseIntroDbSegments(
        """{"tmdb_id":1396,"type":"tv","season":1,"episode":1,
           "intro":[{"start_ms":228947,"end_ms":246331}],
           "credits":[{"start_ms":3431000,"end_ms":null}]}"""
      )

    assertEquals(SkipSegment(SkipKind.INTRO, 228_947L, 246_331L), segments.intro)
    // A null end means the credits run to the finish, which is not the same as having no end.
    assertEquals(SkipSegment(SkipKind.CREDITS, 3_431_000L, null), segments.credits)
  }

  @Test
  fun aNullStartIsTheVeryBeginningOfTheEpisode() {
    // Inception, which opens on its title rather than reaching one.
    val segments = parseIntroDbSegments("""{"tmdb_id":27205,"type":"movie","intro":[{"start_ms":null,"end_ms":38000}]}""")

    assertEquals(SkipSegment(SkipKind.INTRO, 0L, 38_000L), segments.intro)
    assertNull(segments.credits)
  }

  @Test
  fun aTitleNobodyHasSubmittedIsNotAFailure() {
    assertTrue(parseIntroDbSegments("""{"error":"media not found"}""").isEmpty)
    assertTrue(parseIntroDbSegments("").isEmpty)
    assertTrue(parseIntroDbSegments("<html>gateway timeout</html>").isEmpty)
  }

  @Test
  fun aniSkipGivesSecondsAndIsReadAsMilliseconds() {
    // One Piece episode 1000, verbatim.
    val segments =
      parseAniSkipSegments(
        """{"found":true,"results":[{"interval":{"startTime":13.891,"endTime":123.834},
           "skipType":"op","skipId":"5bcf9262","episodeLength":1430.721}],
           "message":"Successfully found skip times","statusCode":200}"""
      )

    assertEquals(SkipSegment(SkipKind.INTRO, 13_891L, 123_834L), segments.intro)
    assertNull(segments.credits)
  }

  @Test
  fun anOpeningAndAnEndingBecomeTheIntroAndTheCredits() {
    val segments =
      parseAniSkipSegments(
        """{"found":true,"results":[
             {"interval":{"startTime":1372.0,"endTime":1451.0},"skipType":"ed"},
             {"interval":{"startTime":54.0,"endTime":144.0},"skipType":"op"}]}"""
      )

    assertEquals(54_000L, segments.intro?.startMs)
    assertEquals(1_372_000L, segments.credits?.startMs)
  }

  @Test
  fun aMixedOpeningStandsInWhenThereIsNoPlainOne() {
    // "mixed-op" is the opening played over the first scene; a viewer wanting past the opening
    // wants past it either way.
    val segments =
      parseAniSkipSegments(
        """{"found":true,"results":[{"interval":{"startTime":30.0,"endTime":120.0},"skipType":"mixed-op"}]}"""
      )

    assertEquals(SkipKind.INTRO, segments.intro?.kind)
    assertEquals(30_000L, segments.intro?.startMs)
  }

  @Test
  fun aniSkipSayingNoIsReadAsNothingRatherThanAsAnError() {
    assertTrue(
      parseAniSkipSegments("""{"found":false,"results":[],"message":"No skip times found","statusCode":404}""")
        .isEmpty
    )
    assertTrue(parseAniSkipSegments("not json at all").isEmpty)
  }

  @Test
  fun anIntervalThatEndsBeforeItStartsIsIgnored() {
    // Bad data should cost nothing: a button that seeks backwards is worse than no button.
    assertTrue(parseIntroDbSegments("""{"intro":[{"start_ms":90000,"end_ms":1000}]}""").isEmpty)
    assertTrue(
      parseAniSkipSegments("""{"found":true,"results":[{"interval":{"startTime":90.0,"endTime":1.0},"skipType":"op"}]}""")
        .isEmpty
    )
  }

  @Test
  fun theButtonIsOfferedOnlyInsideTheSegment() {
    val segments =
      SkipSegments(
        intro = SkipSegment(SkipKind.INTRO, 60_000L, 150_000L),
        credits = SkipSegment(SkipKind.CREDITS, 1_400_000L, null),
      )
    val durationMs = 1_500_000L

    assertNull(segments.at(0L, durationMs))
    assertNull(segments.at(59_999L, durationMs))
    assertEquals(SkipKind.INTRO, segments.at(60_000L, durationMs)?.kind)
    assertEquals(SkipKind.INTRO, segments.at(149_999L, durationMs)?.kind)
    // The far edge is where the skip lands, so offering it there would loop.
    assertNull(segments.at(150_000L, durationMs))
    assertEquals(SkipKind.CREDITS, segments.at(1_450_000L, durationMs)?.kind)
  }

  @Test
  fun creditsWithNoEndRunToTheEndOfTheEpisode() {
    val credits = SkipSegment(SkipKind.CREDITS, 1_400_000L, null)

    assertEquals(1_500_000L, credits.skipTargetMs(1_500_000L))
    // A stream still reporting no duration has nowhere to send the playhead, so it stays put.
    assertEquals(1_400_000L, credits.skipTargetMs(0L))
    assertEquals(150_000L, SkipSegment(SkipKind.INTRO, 60_000L, 150_000L).skipTargetMs(1_500_000L))
  }

  @Test
  fun theCreditsButtonNamesTheNextEpisodeWhenThereIsOne() {
    val intro = SkipSegment(SkipKind.INTRO, 0L, 90_000L)
    val credits = SkipSegment(SkipKind.CREDITS, 1_400_000L, null)

    assertEquals("Skip intro", intro.label(hasNextEpisode = false))
    assertEquals("Skip intro", intro.label(hasNextEpisode = true))
    assertEquals("Next episode", credits.label(hasNextEpisode = true))
    assertEquals("Skip credits", credits.label(hasNextEpisode = false))
  }

  @Test
  fun theMalIdIsReadFromTheLinkRatherThanFromTheSlug() {
    // anidb.app's own id for One Piece is 3880 and MyAnimeList's is 21, so only the link will do.
    assertEquals(
      21,
      parseMalId("""<a href="https://myanimelist.net/anime/21/One-Piece" target="_blank">MAL</a>"""),
    )
    assertEquals(37521, parseMalId("""<a href="https://myanimelist.net/anime/37521">MyAnimeList</a>"""))
    assertNull(parseMalId("""<a href="https://anilist.co/anime/21">AniList</a>"""))
    assertNull(parseMalId("<html>nothing here</html>"))
  }

  @Test
  fun emptySegmentsKnowTheyAreEmpty() {
    assertTrue(SkipSegments().isEmpty)
    assertFalse(SkipSegments(intro = SkipSegment(SkipKind.INTRO, 0L, 1_000L)).isEmpty)
  }
}
