package com.example.auroratv.home

import android.content.Context
import androidx.test.core.app.ApplicationProvider
import com.example.auroratv.data.WatchHistoryEntry
import com.example.auroratv.data.WatchHistoryStore
import org.junit.Assert.assertEquals
import org.junit.Assert.assertNull
import org.junit.Assert.assertTrue
import org.junit.Before
import org.junit.Test

class ContinueWatchingSurfaceTest {
  private val context: Context = ApplicationProvider.getApplicationContext()
  private val store = WatchHistoryStore(context)

  @Before
  fun clearHistory() {
    store.clear()
  }

  private fun entry(
    pageUrl: String,
    updatedAtMs: Long,
    shortForm: Boolean = false,
    positionMs: Long = 60_000L,
    completed: Boolean = false,
  ) =
    WatchHistoryEntry(
      pageUrl = pageUrl,
      title = "Title for $pageUrl",
      subtitle = "S1 · E1",
      posterUrl = "https://image.tmdb.org/t/p/w300/poster.jpg",
      positionMs = positionMs,
      durationMs = 600_000L,
      completed = completed,
      updatedAtMs = updatedAtMs,
      shortForm = shortForm,
    )

  @Test
  fun bothKindsShareOneRowMostRecentFirst() {
    store.save(entry("https://example.com/film", updatedAtMs = 100L))
    store.save(entry("https://example.com/drama", updatedAtMs = 300L, shortForm = true))
    store.save(entry("https://example.com/episode", updatedAtMs = 200L))

    val entries = store.continueWatchingAnywhere()

    assertEquals(
      listOf(
        "https://example.com/drama",
        "https://example.com/episode",
        "https://example.com/film",
      ),
      entries.map { it.pageUrl },
    )
  }

  @Test
  fun finishedAndUnstartedTitlesAreLeftOut() {
    store.save(entry("https://example.com/finished", updatedAtMs = 300L, completed = true))
    store.save(entry("https://example.com/unstarted", updatedAtMs = 200L, positionMs = 0L))
    store.save(entry("https://example.com/started", updatedAtMs = 100L))

    val entries = store.continueWatchingAnywhere()

    assertEquals(listOf("https://example.com/started"), entries.map { it.pageUrl })
  }

  @Test
  fun theRowIsCappedSoTheLauncherIsNotFlooded() {
    repeat(HOME_SURFACE_LIMIT + 4) { store.save(entry("https://example.com/$it", it.toLong())) }

    assertEquals(HOME_SURFACE_LIMIT, store.continueWatchingAnywhere().size)
  }

  @Test
  fun tappingATitleCarriesEnoughOfItToPlay() {
    val saved = entry("https://example.com/film", updatedAtMs = 100L)
    store.save(saved)

    val intent = resumeIntent(context, saved)
    val resumed = resumeContextFor(context, intent.getStringExtra(EXTRA_RESUME_PAGE_URL))

    assertEquals(saved.pageUrl, resumed?.pageUrl)
    assertEquals(saved.title, resumed?.title)
    assertEquals(saved.posterUrl, resumed?.posterUrl)
  }

  @Test
  fun aTitleNoLongerInTheHistoryResumesNothing() {
    assertNull(resumeContextFor(context, "https://example.com/forgotten"))
  }

  @Test
  fun noPageUrlResumesNothing() {
    assertNull(resumeContextFor(context, null))
  }

  /** Posters go into a parcel with a hard size limit, so they are decoded down on the way in. */
  @Test
  fun oversizedPostersAreSampledDown() {
    assertEquals(1, sampleSizeFor(sourceWidth = 160, targetWidth = 160))
    assertEquals(2, sampleSizeFor(sourceWidth = 320, targetWidth = 160))
    assertEquals(8, sampleSizeFor(sourceWidth = 1280, targetWidth = 160))
    assertTrue(sampleSizeFor(sourceWidth = 0, targetWidth = 160) == 1)
  }
}
