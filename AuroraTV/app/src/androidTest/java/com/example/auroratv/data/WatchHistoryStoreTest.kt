package com.example.auroratv.data

import androidx.test.core.app.ApplicationProvider
import org.junit.Assert.assertEquals
import org.junit.Assert.assertFalse
import org.junit.Assert.assertNotNull
import org.junit.Assert.assertNull
import org.junit.Assert.assertTrue
import org.junit.Before
import org.junit.Test

class WatchHistoryStoreTest {
  private val store = WatchHistoryStore(ApplicationProvider.getApplicationContext())

  private val episodeContext =
    PlaybackContext(
      pageUrl = "https://vidfast.vc/tv/94997/1/3?autoPlay=true",
      title = "House of the Dragon",
      subtitle = "S1 · E3 · Second of His Name",
      posterUrl = "https://image.tmdb.org/t/p/w500/poster.jpg",
      showId = 94997,
      seasonNumber = 1,
      episodeNumber = 3,
    )

  @Before
  fun clearStore() {
    store.clear()
  }

  @Test
  fun record_roundTripsEveryFieldNeededToShowItAgain() {
    store.record(episodeContext, positionMs = 90_000L, durationMs = 3_600_000L, completed = false)

    val entry = store.find(episodeContext.pageUrl)
    assertNotNull(entry)
    assertEquals("House of the Dragon", entry?.title)
    assertEquals("S1 · E3 · Second of His Name", entry?.subtitle)
    assertEquals(94997, entry?.showId)
    assertEquals(1, entry?.seasonNumber)
    assertEquals(3, entry?.episodeNumber)
    assertEquals(90_000L, entry?.positionMs)
    assertEquals("Resume 1:30", entry?.resumeLabel)
    assertEquals(0.025f, entry?.progressFraction ?: 0f, 0.001f)
  }

  @Test
  fun markCompleted_reportsFullProgressAndDropsTheResumeLabel() {
    store.record(episodeContext, positionMs = 90_000L, durationMs = 3_600_000L, completed = false)
    store.markCompleted(episodeContext, durationMs = 3_600_000L)

    val entry = store.find(episodeContext.pageUrl)
    assertTrue(entry?.completed == true)
    assertEquals(1f, entry?.progressFraction ?: 0f, 0.001f)
    assertNull(entry?.resumeLabel)
  }

  @Test
  fun continueWatching_ordersByRecencyAndHidesFinishedTitles() {
    val movie = PlaybackContext(pageUrl = "https://vidfast.vc/movie/1", title = "Older")
    val finished = PlaybackContext(pageUrl = "https://vidfast.vc/movie/2", title = "Finished")
    store.record(movie, positionMs = 60_000L, durationMs = 600_000L, completed = false)
    store.record(episodeContext, positionMs = 90_000L, durationMs = 3_600_000L, completed = false)
    store.markCompleted(finished, durationMs = 600_000L)

    val titles = store.continueWatching().map { it.title }
    assertEquals(listOf("House of the Dragon", "Older"), titles)
    assertFalse(titles.contains("Finished"))
  }

  @Test
  fun continueWatching_keepsShortDramasOutOfTheFilmCatalog() {
    val drama =
      PlaybackContext(
        pageUrl = "https://dramabox.chartdrama.com/p/42000012638/a-drama?ep=3",
        title = "A Short Drama",
        episodeNumber = 3,
        shortForm = true,
      )
    store.record(episodeContext, positionMs = 90_000L, durationMs = 3_600_000L, completed = false)
    store.record(drama, positionMs = 30_000L, durationMs = 120_000L, completed = false)

    // The catalog row shows films and shows; the drama page shows dramas. Neither borrows the other.
    assertEquals(listOf("House of the Dragon"), store.continueWatching().map { it.title })
    assertEquals(
      listOf("A Short Drama"),
      store.continueWatching(shortForm = true).map { it.title },
    )
    assertTrue(store.find(drama.pageUrl)?.shortForm == true)
    assertFalse(store.find(episodeContext.pageUrl)?.shortForm == true)
  }

  @Test
  fun find_ignoresTheFragmentSoTheSameEpisodeMatches() {
    store.record(episodeContext, positionMs = 30_000L, durationMs = 600_000L, completed = false)

    assertNotNull(store.find("${episodeContext.pageUrl}#player"))
  }

  @Test
  fun remove_forgetsASingleTitle() {
    store.record(episodeContext, positionMs = 30_000L, durationMs = 600_000L, completed = false)
    store.remove(episodeContext.pageUrl)

    assertNull(store.find(episodeContext.pageUrl))
  }

  @Test
  fun formatWatchTime_usesHoursOnlyWhenNeeded() {
    assertEquals("0:45", formatWatchTime(45_000L))
    assertEquals("1:30", formatWatchTime(90_000L))
    assertEquals("1:01:05", formatWatchTime(3_665_000L))
  }
}
