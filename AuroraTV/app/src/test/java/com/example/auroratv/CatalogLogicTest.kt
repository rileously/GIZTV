package com.example.auroratv

import com.example.auroratv.data.DEFAULT_RESOLUTION_TIMEOUT_MS
import com.example.auroratv.data.MAXIMUM_RESOLUTION_TIMEOUT_MS
import com.example.auroratv.data.WatchHistoryEntry
import com.example.auroratv.data.formatWatchTime
import com.example.auroratv.data.streamResolutionTimeoutMs
import com.example.auroratv.ui.catalog.RecommendationSeed
import com.example.auroratv.ui.catalog.mergeRecommendations
import com.example.auroratv.ui.catalog.recommendationHeading
import com.example.auroratv.ui.catalog.recommendationSeeds
import com.example.auroratv.ui.catalog.tmdbMovieIdFromPageUrl
import org.junit.Assert.assertEquals
import org.junit.Assert.assertNull
import org.junit.Assert.assertTrue
import org.junit.Test

/** What the catalog decides to show, and how long it waits for a stream. */
class CatalogLogicTest {
  @Test
  fun movieId_isReadBackOutOfThePageTheCatalogBuilt() {
    assertEquals(603, tmdbMovieIdFromPageUrl("https://vidfast.vc/movie/603?autoPlay=true&sub=en"))
    assertEquals(603, tmdbMovieIdFromPageUrl("https://vidfast.vc/movie/603"))
    assertEquals(603, tmdbMovieIdFromPageUrl("https://vidfast.vc/movie/603#player"))
    assertNull(tmdbMovieIdFromPageUrl("https://vidfast.vc/tv/1399/1/1"))
    assertNull(tmdbMovieIdFromPageUrl("https://vidfast.vc/movie/not-a-number"))
    assertNull(tmdbMovieIdFromPageUrl("https://example.com/"))
  }

  @Test
  fun seeds_comeFromTitlesActuallyWatched() {
    val entries =
      listOf(
        historyEntry(pageUrl = "https://vidfast.vc/movie/11", progress = 1f, updatedAtMs = 300L),
        // Two minutes in and abandoned: no opinion worth acting on.
        historyEntry(pageUrl = "https://vidfast.vc/movie/22", progress = .05f, updatedAtMs = 200L),
        historyEntry(pageUrl = "https://vidfast.vc/movie/33", progress = .6f, updatedAtMs = 100L),
      )

    val seeds = recommendationSeeds(entries, forShows = false)

    assertEquals(listOf(11, 33), seeds.map { it.id })
  }

  @Test
  fun seeds_leaveOutShortDramasAndTheWrongTab() {
    val entries =
      listOf(
        historyEntry(pageUrl = "https://vidfast.vc/movie/11", progress = 1f, updatedAtMs = 300L),
        historyEntry(
          pageUrl = "https://chartdrama.com/drama/9",
          progress = 1f,
          updatedAtMs = 400L,
          shortForm = true,
        ),
        historyEntry(pageUrl = "https://vidfast.vc/tv/77/1/1", progress = 1f, updatedAtMs = 200L, showId = 77),
      )

    // A short drama belongs to its own page, and a show is not a film.
    assertEquals(listOf(11), recommendationSeeds(entries, forShows = false).map { it.id })
    assertEquals(listOf(77), recommendationSeeds(entries, forShows = true).map { it.id })
  }

  @Test
  fun seeds_areCappedSoTheRailIsAboutSomebody() {
    val entries =
      (1..10).map { index ->
        historyEntry("https://vidfast.vc/movie/$index", progress = 1f, updatedAtMs = index.toLong())
      }

    assertEquals(3, recommendationSeeds(entries, forShows = false).size)
  }

  @Test
  fun recommendations_favourWhatEverySeedAgreesOnAndDropWhatIsSeen() {
    val answers =
      listOf(
        listOf(Suggestion(1), Suggestion(2), Suggestion(3)),
        listOf(Suggestion(3), Suggestion(4)),
        listOf(Suggestion(3), Suggestion(2)),
      )

    val merged = mergeRecommendations(answers, Suggestion::id, watchedIds = setOf(4))

    // Suggested by all three seeds, so it leads; the one already watched is gone.
    assertEquals(3, merged.first().id)
    assertEquals(listOf(3, 2, 1), merged.map { it.id })
  }

  @Test
  fun recommendations_nameTheTitleOnlyWhenThereIsOne() {
    assertEquals("For you", recommendationHeading(emptyList()))
    assertEquals("Because you watched Alien", recommendationHeading(listOf(RecommendationSeed(1, "Alien"))))
    assertEquals(
      "For you",
      recommendationHeading(listOf(RecommendationSeed(1, "Alien"), RecommendationSeed(2, "Heat"))),
    )
  }

  @Test
  fun resolutionTimeout_growsForSlowTitlesAndNeverShrinks() {
    assertEquals(DEFAULT_RESOLUTION_TIMEOUT_MS, streamResolutionTimeoutMs(0L))
    // A title that has always been quick still gets the full default.
    assertEquals(DEFAULT_RESOLUTION_TIMEOUT_MS, streamResolutionTimeoutMs(4_000L))
    assertEquals(36_000L, streamResolutionTimeoutMs(24_000L))
    assertEquals(MAXIMUM_RESOLUTION_TIMEOUT_MS, streamResolutionTimeoutMs(120_000L))
    assertTrue(streamResolutionTimeoutMs(50_000L) <= MAXIMUM_RESOLUTION_TIMEOUT_MS)
  }

  @Test
  fun watchTime_readsAsAClock() {
    assertEquals("0:00", formatWatchTime(0L))
    assertEquals("1:05", formatWatchTime(65_000L))
    assertEquals("1:01:05", formatWatchTime(3_665_000L))
    assertEquals("0:00", formatWatchTime(-5_000L))
  }

  private data class Suggestion(val id: Int)

  private fun historyEntry(
    pageUrl: String,
    progress: Float,
    updatedAtMs: Long,
    shortForm: Boolean = false,
    showId: Int? = null,
  ): WatchHistoryEntry {
    val durationMs = 100_000L
    return WatchHistoryEntry(
      pageUrl = pageUrl,
      title = "Title",
      subtitle = null,
      posterUrl = null,
      positionMs = (durationMs * progress).toLong(),
      durationMs = durationMs,
      completed = progress >= 1f,
      updatedAtMs = updatedAtMs,
      showId = showId,
      shortForm = shortForm,
    )
  }
}
