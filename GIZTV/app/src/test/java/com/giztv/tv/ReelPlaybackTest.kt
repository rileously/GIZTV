package com.giztv.tv

import com.giztv.tv.data.PlaybackContext
import com.giztv.tv.data.ReelTitle
import com.giztv.tv.data.WatchHistoryEntry
import com.giztv.tv.ui.drama.ShortDrama
import com.giztv.tv.ui.drama.chartDramaSlugOf
import com.giztv.tv.ui.drama.reelAround
import com.giztv.tv.ui.drama.resumeEpisodeFor
import com.giztv.tv.ui.drama.shortDramaPlayback
import com.giztv.tv.ui.drama.toReelTitle
import com.giztv.tv.ui.player.ReelGestureZone
import com.giztv.tv.ui.player.ReelPanelSwipe
import com.giztv.tv.ui.player.ReelSwipe
import com.giztv.tv.ui.player.reelGestureZone
import com.giztv.tv.ui.player.reelCoverTopOffset
import com.giztv.tv.ui.player.reelPanelSwipe
import com.giztv.tv.ui.player.reelSlideOffset
import com.giztv.tv.ui.player.reelSwipeTarget
import com.giztv.tv.ui.player.reelTitleSwipe
import org.junit.Assert.assertEquals
import org.junit.Assert.assertNull
import org.junit.Assert.assertTrue
import org.junit.Test
import kotlin.math.abs

/** Phone-sized: 1080 across, 2280 down, which is what the fractions below are measured against. */
private const val WIDTH_PX = 1080
private const val HEIGHT_PX = 2280

class ReelPlaybackTest {
  private fun drama(slug: String, title: String, episodes: Int = 3) =
    ShortDrama(
      slug = slug,
      title = title,
      coverUrl = "https://chartdrama.com/$slug.jpg",
      episodeCount = episodes,
      synopsis = "A synopsis for $title",
      starring = null,
      tags = listOf("Romance", "Revenge"),
    )

  @Test
  fun theEdgesKeepBrightnessAndVolume_andTheMiddleNavigates() {
    // A thumb on the far left or right is still reaching for the controls it has always had.
    assertEquals(ReelGestureZone.BRIGHTNESS, reelGestureZone(20f, WIDTH_PX))
    assertEquals(ReelGestureZone.VOLUME, reelGestureZone(WIDTH_PX - 20f, WIDTH_PX))
    assertEquals(ReelGestureZone.NAVIGATE, reelGestureZone(WIDTH_PX / 2f, WIDTH_PX))
    // Most of the picture navigates, which is what makes the reel swipe the easy one to land.
    assertEquals(ReelGestureZone.NAVIGATE, reelGestureZone(WIDTH_PX * .3f, WIDTH_PX))
    assertEquals(ReelGestureZone.NAVIGATE, reelGestureZone(WIDTH_PX * .7f, WIDTH_PX))
  }

  @Test
  fun aPlayerThatHasNotBeenMeasuredYetNavigatesNowhere() {
    // No size means no fractions to measure a swipe against, so nothing is read as one.
    assertNull(reelTitleSwipe(0f, -2_000f, 0))
    assertNull(reelPanelSwipe(-2_000f, 0f, 0))
  }

  @Test
  fun upIsTheNextDramaAndDownIsThePreviousOne() {
    val far = HEIGHT_PX * .4f

    assertEquals(ReelSwipe.NEXT_TITLE, reelTitleSwipe(0f, -far, HEIGHT_PX))
    assertEquals(ReelSwipe.PREVIOUS_TITLE, reelTitleSwipe(0f, far, HEIGHT_PX))
  }

  @Test
  fun aShortDragIsNotASwipe() {
    // A finger that barely moved was a tap that wobbled, and taps belong to the controls.
    assertNull(reelTitleSwipe(0f, -40f, HEIGHT_PX))
    assertNull(reelTitleSwipe(0f, 40f, HEIGHT_PX))
    assertNull(reelTitleSwipe(0f, 0f, HEIGHT_PX))
  }

  @Test
  fun aDragThatWentMostlyAcrossBelongsToTheEpisodesPanel() {
    // Far enough down to count on its own, but it travelled further across than it did down.
    assertNull(reelTitleSwipe(-900f, 500f, HEIGHT_PX))
    // The other way round: a swipe that leans upward is still a swipe upward.
    assertEquals(ReelSwipe.NEXT_TITLE, reelTitleSwipe(-120f, -600f, HEIGHT_PX))
  }

  @Test
  fun theCoverFollowsTheFingerFromTheFirstPixel() {
    // Not gated on the trigger distance: a swipe that shows nothing until it is far enough along
    // cannot be learned, and cannot be called off half way either.
    assertEquals(-30f, reelSlideOffset(-30f, HEIGHT_PX, hasTarget = true), 0f)
    assertEquals(600f, reelSlideOffset(600f, HEIGHT_PX, hasTarget = true), 0f)
    // A drag longer than the screen has nowhere further to pull the cover in from.
    assertEquals(-HEIGHT_PX.toFloat(), reelSlideOffset(-9_000f, HEIGHT_PX, hasTarget = true), 0f)
  }

  @Test
  fun theEndOfAReelGivesALittleAndNoMore() {
    // Enough movement to read as "nothing that way", far too little to read as a swipe landing.
    val give = reelSlideOffset(-2_000f, HEIGHT_PX, hasTarget = false)

    assertTrue(give < 0f)
    assertTrue(abs(give) <= HEIGHT_PX * .1f)
    assertEquals(0f, reelSlideOffset(-2_000f, 0, hasTarget = false), 0f)
  }

  @Test
  fun aCompletedSwipeLeavesTheCoverExactlyFillingTheScreen() {
    // Up: the next drama starts a screen below and arrives at the top.
    assertEquals(
      HEIGHT_PX.toFloat(),
      reelCoverTopOffset(ReelSwipe.NEXT_TITLE, 0f, HEIGHT_PX),
      0f,
    )
    assertEquals(
      0f,
      reelCoverTopOffset(ReelSwipe.NEXT_TITLE, -HEIGHT_PX.toFloat(), HEIGHT_PX),
      0f,
    )
    // Down: the previous one comes the other way and lands in the same place.
    assertEquals(
      -HEIGHT_PX.toFloat(),
      reelCoverTopOffset(ReelSwipe.PREVIOUS_TITLE, 0f, HEIGHT_PX),
      0f,
    )
    assertEquals(
      0f,
      reelCoverTopOffset(ReelSwipe.PREVIOUS_TITLE, HEIGHT_PX.toFloat(), HEIGHT_PX),
      0f,
    )
  }

  @Test
  fun leftOpensThePanelAndRightPutsItAway() {
    val far = WIDTH_PX * .4f

    assertEquals(ReelPanelSwipe.OPEN, reelPanelSwipe(-far, 0f, WIDTH_PX))
    assertEquals(ReelPanelSwipe.CLOSE, reelPanelSwipe(far, 0f, WIDTH_PX))
    // Short, or mostly vertical, and it is not a panel gesture at all.
    assertNull(reelPanelSwipe(-30f, 0f, WIDTH_PX))
    assertNull(reelPanelSwipe(-far, -1_200f, WIDTH_PX))
  }

  @Test
  fun theReelRunsBothWaysAndStopsAtItsEnds() {
    val listing = listOf(drama("a/one", "One"), drama("b/two", "Two"), drama("c/three", "Three"))
    val reel = reelAround(listing[1], listing)
    val playing = listing[1].toReelTitle().shortDramaPlayback(reel)

    assertEquals("Two", playing.currentReelTitle?.title)
    assertEquals("Three", playing.nextReelTitle?.title)
    assertEquals("One", playing.previousReelTitle?.title)

    val first = listing[0].toReelTitle().shortDramaPlayback(reel)
    assertNull(first.previousReelTitle)
    val last = listing[2].toReelTitle().shortDramaPlayback(reel)
    assertNull(last.nextReelTitle)
  }

  @Test
  fun aDramaThatIsNotInTheListingIsStillPartOfItsOwnReel() {
    // Otherwise a drama reached from anywhere but the grid would be missing from its own reel, and
    // a reel with nothing playing in it has no next title to offer.
    val stranger = drama("z/stranger", "Stranger")
    val reel = reelAround(stranger, listOf(drama("a/one", "One")))

    assertEquals(listOf("Stranger", "One"), reel.map(ReelTitle::title))
    assertEquals("One", stranger.toReelTitle().shortDramaPlayback(reel).nextReelTitle?.title)
  }

  @Test
  fun aTitleOutsideAReelNavigatesNowhere() {
    val alone = PlaybackContext(pageUrl = "https://example.com/film", title = "A Film")

    assertNull(alone.currentReelTitle)
    assertNull(alone.nextReelTitle)
    assertNull(alone.previousReelTitle)
    assertNull(reelSwipeTarget(alone, ReelSwipe.NEXT_TITLE))
    assertNull(reelSwipeTarget(null, ReelSwipe.NEXT_TITLE))
  }

  @Test
  fun theWholeRunTravelsWithTheEpisodeBeingPlayed() {
    val one = drama("a/one", "One", episodes = 4)
    val playback = one.toReelTitle().shortDramaPlayback(reelAround(one, listOf(one)), episode = 3)

    assertEquals("https://chartdrama.com/p/a/one?ep=3", playback.pageUrl)
    assertEquals(3, playback.episodeNumber)
    assertEquals("Episode 3", playback.subtitle)
    assertTrue(playback.shortForm)
    assertEquals(4, playback.playlist.size)
    assertEquals(4, playback.playlist.last().episodeNumber)
    // The rest of the run is what the player rolls into once an episode ends.
    assertEquals("https://chartdrama.com/p/a/one?ep=4", playback.nextEntry?.pageUrl)
  }

  @Test
  fun anEpisodeNumberOutsideTheRunIsPulledBackIntoIt() {
    val one = drama("a/one", "One", episodes = 2)
    val reel = reelAround(one, listOf(one))

    assertEquals(1, one.toReelTitle().shortDramaPlayback(reel, episode = 0).episodeNumber)
    assertEquals(2, one.toReelTitle().shortDramaPlayback(reel, episode = 99).episodeNumber)
  }

  @Test
  fun advancingAnEpisodeKeepsTheReelItCameFrom() {
    val one = drama("a/one", "One", episodes = 3)
    val listing = listOf(one, drama("b/two", "Two"))
    val playback = one.toReelTitle().shortDramaPlayback(reelAround(one, listing))
    val next = playback.advanceTo(playback.nextEntry!!)

    // Rolling into episode two must not cost the viewer the swipe onto the next drama.
    assertEquals("Two", next.nextReelTitle?.title)
    // The per-episode context drops the drama's own description, so the reel entry keeps it.
    assertEquals("A synopsis for One", next.currentReelTitle?.overview)
  }

  private fun watched(
    slug: String,
    episode: Int,
    completed: Boolean,
    atMs: Long,
    shortForm: Boolean = true,
  ) =
    WatchHistoryEntry(
      pageUrl = "https://chartdrama.com/p/$slug?ep=$episode",
      title = "Whatever",
      subtitle = "Episode $episode",
      posterUrl = null,
      positionMs = if (completed) 0L else 42_000L,
      durationMs = 120_000L,
      completed = completed,
      updatedAtMs = atMs,
      episodeNumber = episode,
      shortForm = shortForm,
    )

  @Test
  fun aDramaNeverWatchedOpensAtItsFirstEpisode() {
    assertEquals(1, resumeEpisodeFor("a/one", 40, emptyList()))
    // Somebody else's drama says nothing about this one.
    assertEquals(1, resumeEpisodeFor("a/one", 40, listOf(watched("b/two", 12, false, 900L))))
  }

  @Test
  fun aSwipeLandsWhereTheDramaWasLeftRatherThanBackAtTheStart() {
    val history =
      listOf(
        watched("a/one", 12, completed = false, atMs = 900L),
        watched("a/one", 3, completed = true, atMs = 100L),
      )

    // The one stopped part way is returned to, and the player's saved position takes it from there.
    assertEquals(12, resumeEpisodeFor("a/one", 40, history))
  }

  @Test
  fun anEpisodeWatchedToTheEndHandsOverToTheNextOne() {
    val history = listOf(watched("a/one", 12, completed = true, atMs = 900L))

    assertEquals(13, resumeEpisodeFor("a/one", 40, history))
    // Except at the end of the run, where there is no next one to hand over to.
    assertEquals(12, resumeEpisodeFor("a/one", 12, history))
  }

  @Test
  fun onlyShortDramaHistoryCountsTowardsWhereADramaResumes() {
    // A film or a TV episode that happened to be recorded against a chartdrama address is not a
    // place in a run, and the reel must not treat it as one.
    val history = listOf(watched("a/one", 9, completed = false, atMs = 900L, shortForm = false))

    assertEquals(1, resumeEpisodeFor("a/one", 40, history))
  }

  @Test
  fun anEpisodeAddressSaysWhichDramaItBelongsTo() {
    assertEquals("42000012638/twin-sister", chartDramaSlugOf("https://chartdrama.com/p/42000012638/twin-sister?ep=7"))
    assertEquals("42000012638/twin-sister", chartDramaSlugOf("https://chartdrama.com/p/42000012638/twin-sister"))
    // Anything belonging to another source has to fall through untouched.
    assertNull(chartDramaSlugOf("https://anidb.app/anime/vinland-saga-5999?ep=1"))
    assertNull(chartDramaSlugOf("https://chartdrama.com/"))
  }
}
