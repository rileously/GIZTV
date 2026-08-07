package com.example.auroratv.ui.catalog

import androidx.test.ext.junit.runners.AndroidJUnit4
import java.util.concurrent.atomic.AtomicInteger
import kotlinx.coroutines.async
import kotlinx.coroutines.awaitAll
import kotlinx.coroutines.delay
import kotlinx.coroutines.runBlocking
import org.junit.Assert.assertEquals
import org.junit.Assert.assertNull
import org.junit.Assert.assertTrue
import org.junit.Before
import org.junit.Test
import org.junit.runner.RunWith

/** Instrumented because the cache ages its entries off `SystemClock`. */
@RunWith(AndroidJUnit4::class)
class CatalogCacheTest {
  @Before
  fun clearCache() {
    CatalogCache.clear()
  }

  @Test
  fun peek_answersNothingUntilSomethingHasBeenFetched() {
    assertNull(CatalogCache.peek<List<String>>("MOVIES:trending"))
  }

  @Test
  fun fetch_storesTheResultSoTheNextLookNeedsNoRequest() = runBlocking {
    val requests = AtomicInteger()

    val first = CatalogCache.fetch("MOVIES:popular") { requests.incrementAndGet(); listOf("Dune") }
    val cached = CatalogCache.peek<List<String>>("MOVIES:popular")

    assertEquals(listOf("Dune"), first)
    assertEquals(listOf("Dune"), cached?.value)
    // Just fetched, so the rail can be drawn from it without going back to TMDB.
    assertTrue(cached?.fresh == true)
    assertEquals(1, requests.get())
  }

  @Test
  fun fetch_runsOnceWhenTwoCallersWantTheSameRail() = runBlocking {
    val requests = AtomicInteger()
    val load: suspend () -> List<String> = {
      requests.incrementAndGet()
      // Long enough that the second caller certainly arrives mid-flight, which is the case that
      // used to cost a whole extra set of requests when a viewer flipped between tabs.
      delay(120)
      listOf("Arrival")
    }

    val answers =
      listOf(
          async { CatalogCache.fetch("SHOWS:trending", load) },
          async { CatalogCache.fetch("SHOWS:trending", load) },
        )
        .awaitAll()

    assertEquals(listOf(listOf("Arrival"), listOf("Arrival")), answers)
    assertEquals(1, requests.get())
  }

  @Test
  fun fetch_keepsDifferentRailsApart() = runBlocking {
    CatalogCache.fetch("MOVIES:genre-horror") { listOf("The Thing") }
    CatalogCache.fetch("SHOWS:genre-horror") { listOf("Hannibal") }

    assertEquals(listOf("The Thing"), CatalogCache.peek<List<String>>("MOVIES:genre-horror")?.value)
    assertEquals(listOf("Hannibal"), CatalogCache.peek<List<String>>("SHOWS:genre-horror")?.value)
  }

  @Test
  fun clear_sendsTheNextLookBackToTheNetwork() = runBlocking {
    CatalogCache.fetch("MOVIES:acclaimed") { listOf("Paddington 2") }

    CatalogCache.clear()

    assertNull(CatalogCache.peek<List<String>>("MOVIES:acclaimed"))
  }

  @Test
  fun cacheKey_namesTheTabAsWellAsTheRail() {
    val category = catalogCategories(currentYear = 2026).first { it.id == "trending" }

    assertEquals("MOVIES:trending", catalogCacheKey(CatalogTab.MOVIES, category))
    assertEquals("SHOWS:trending", catalogCacheKey(CatalogTab.SHOWS, category))
  }
}
