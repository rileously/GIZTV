package com.example.auroratv.ui.drama

import com.example.auroratv.data.PlaybackContext
import com.example.auroratv.data.PlaylistEntry
import java.io.IOException
import kotlin.system.measureTimeMillis
import kotlinx.coroutines.Dispatchers
import kotlinx.coroutines.async
import kotlinx.coroutines.awaitAll
import kotlinx.coroutines.delay
import kotlinx.coroutines.runBlocking
import org.junit.Assert.assertEquals
import org.junit.Assert.assertFalse
import org.junit.Assert.assertNull
import org.junit.Assert.assertTrue
import org.junit.Test

class ShortDramaRepositoryTest {
  @Test
  fun parser_readsTheListingAndSkipsUnusableRows() {
    val dramas =
      parseShortDramas(
        """
        {
          "items": [
            {
              "slug": "42000012638/the-one-you-love-is-her-twin-sister",
              "dramaId": 32161,
              "title": "The One You Love is Her Twin Sister",
              "cover": "https://res.chartdrama.com/5/cover.jpg",
              "latestEpisodeLabel": "EP71 TV",
              "synopsis": "Countryside girl Elise marries in place of her twin.",
              "starring": "Cyrus Fenton",
              "tags": ["Revenge", "Modern"],
              "source": 5
            },
            { "slug": "", "title": "No slug" },
            { "slug": "42000000001/blank-title", "title": "   " },
            { "slug": "not-a-slug", "title": "Missing the id" }
          ]
        }
        """.trimIndent()
      )

    val drama = dramas.single()
    assertEquals("42000012638/the-one-you-love-is-her-twin-sister", drama.slug)
    assertEquals("The One You Love is Her Twin Sister", drama.title)
    assertEquals("https://res.chartdrama.com/5/cover.jpg", drama.coverUrl)
    // The count rides along with the listing, so opening a drama costs no second request.
    assertEquals(71, drama.episodeCount)
    assertEquals(listOf("Revenge", "Modern"), drama.tags)
    assertEquals("Revenge", drama.subtitle)
  }

  @Test
  fun parser_fallsBackToTheCastAndToleratesAMissingCover() {
    val drama =
      parseShortDramas(
        """{"items":[{"slug":"1/untagged","title":"Untagged","starring":"Ada","cover":""}]}"""
      )
        .single()

    assertEquals("Ada", drama.subtitle)
    assertNull(drama.coverUrl)
    assertTrue(drama.tags.isEmpty())
    // Nothing said about episodes still leaves the one about to be opened.
    assertEquals(1, drama.episodeCount)
  }

  @Test
  fun subtitle_fallsBackToTheRunLengthWhenTheListingCarriesNoGenre() {
    val drama =
      parseShortDramas(
        """{"items":[{"slug":"1/plain","title":"Plain","latestEpisodeLabel":"EP50 TV"}]}"""
      )
        .single()

    assertEquals("50 episodes", drama.subtitle)
    assertEquals(
      "1 episode",
      parseShortDramas("""{"items":[{"slug":"1/single","title":"Single"}]}""").single().subtitle,
    )
  }

  @Test
  fun parser_returnsNothingForAnErrorPayload() {
    assertTrue(parseShortDramas("""{"error":"forbidden"}""").isEmpty())
  }

  @Test
  fun episodeCount_readsTheRunLengthOutOfTheLabel() {
    assertEquals(71, episodeCountFromLabel("EP71 TV"))
    assertEquals(50, episodeCountFromLabel("EP50"))
    assertEquals(1, episodeCountFromLabel("Coming soon"))
    assertEquals(1, episodeCountFromLabel(null))
    assertEquals(1, episodeCountFromLabel("EP0"))
  }

  @Test
  fun episodeUrl_usesTheSiteOwnSlugVerbatim() {
    // Taken as given rather than rebuilt from the title, so punctuation is never a question.
    assertEquals(
      "https://dramabox.chartdrama.com/p/42000017050/father-of-my-ex-owner-of-my-heart?ep=1",
      chartDramaEpisodeUrl("42000017050/father-of-my-ex-owner-of-my-heart", 1),
    )
    assertEquals(
      "https://dramabox.chartdrama.com/p/42000012638/the-one-you-love-is-her-twin-sister?ep=12",
      chartDramaEpisodeUrl("/42000012638/the-one-you-love-is-her-twin-sister/", 12),
    )
  }

  @Test(expected = IllegalArgumentException::class)
  fun episodeUrl_rejectsEpisodesBelowOne() {
    chartDramaEpisodeUrl("42000017050/any-drama", 0)
  }

  @Test
  fun userAgent_looksLikeTheBrowserTheListingExpects() {
    // The listing turns away requests that do not look like its own site.
    assertTrue(chartDramaUserAgent().startsWith("Mozilla/5.0"))
    assertTrue(chartDramaUserAgent().contains("GIZTV/"))
  }

  @Test
  fun defaultKeywords_areTitleWordsRatherThanGenres() {
    // chartdrama matches on title text, so a genre word would match nothing at all.
    assertTrue(DEFAULT_DRAMA_KEYWORDS.contains("Love"))
    assertTrue(DEFAULT_DRAMA_KEYWORDS.contains("Marriage"))
    assertFalse(DEFAULT_DRAMA_KEYWORDS.contains("Billionaire"))
    assertFalse(DEFAULT_DRAMA_KEYWORDS.contains("Werewolf"))
  }

  @Test
  fun playbackContext_treatsASeasonlessEpisodeAsAnEpisode() {
    val playlist =
      (1..3).map { PlaylistEntry(episodeNumber = it, name = "Episode $it", pageUrl = "page/$it") }
    val context =
      PlaybackContext(
        pageUrl = "page/1",
        title = "A Short Drama",
        episodeNumber = 1,
        playlist = playlist,
      )

    assertTrue(context.isEpisode)
    assertEquals("E2 · Episode 2", context.nextLabel)
    // No season means no "S…" segment; TMDB episodes keep theirs.
    assertEquals("E2 · Episode 2", context.advanceTo(playlist[1]).subtitle)
    assertEquals(
      "S1 · E2 · Episode 2",
      context.copy(seasonNumber = 1).advanceTo(playlist[1]).subtitle,
    )
  }

  @Test
  fun playbackContext_isNotAnEpisodeWithoutAnEpisodeNumber() {
    assertFalse(PlaybackContext(pageUrl = "page", title = "A Movie").isEpisode)
  }

  @Test
  fun playbackContext_marksShortDramasAsShortFormThroughTheWholeRun() {
    val playlist =
      (1..3).map { PlaylistEntry(episodeNumber = it, name = "Episode $it", pageUrl = "page/$it") }
    val context =
      PlaybackContext(
        pageUrl = "page/1",
        title = "A Short Drama",
        episodeNumber = 1,
        playlist = playlist,
        shortForm = true,
      )

    assertTrue(context.shortForm)
    // The flag has to survive the hop to the next episode, or the run reverts to a countdown.
    assertTrue(context.advanceTo(playlist[1]).shortForm)
    assertFalse(PlaybackContext(pageUrl = "page", title = "An Episode", episodeNumber = 1).shortForm)
  }

  @Test
  fun cache_servesAStoredAnswerWithoutLoadingAgain() = runBlocking {
    var loads = 0
    val cache = DramaBoxCache<String>(ttlMs = 60_000)
    val load: suspend () -> String = { loads += 1; "answer" }

    assertEquals("answer", cache.get("love", load))
    assertEquals("answer", cache.get("love", load))
    assertEquals(1, loads)
    // A different key is a different question.
    assertEquals("answer", cache.get("ceo", load))
    assertEquals(2, loads)
  }

  @Test
  fun cache_loadsAgainOnceTheAnswerHasExpired() = runBlocking {
    var loads = 0
    var clock = 0L
    val cache = DramaBoxCache<String>(ttlMs = 1_000, now = { clock })

    cache.get("love") { loads += 1; "answer" }
    clock = 999
    cache.get("love") { loads += 1; "answer" }
    assertEquals(1, loads)
    clock = 1_000
    cache.get("love") { loads += 1; "answer" }
    assertEquals(2, loads)
  }

  @Test
  fun cache_neverStoresAFailure() = runBlocking {
    val cache = DramaBoxCache<String>(ttlMs = 60_000)
    runCatching { cache.get("love") { throw IOException("offline") } }

    var loads = 0
    assertEquals("answer", cache.get("love") { loads += 1; "answer" })
    assertEquals(1, loads)
  }

  @Test
  fun cache_letsOnlyOneConcurrentCallerLoadAKey() = runBlocking {
    var loads = 0
    val cache = DramaBoxCache<String>(ttlMs = 60_000)
    val answers =
      (1..5)
        .map { async(Dispatchers.Default) { cache.get("love") { loads += 1; delay(50); "answer" } } }
        .awaitAll()

    assertEquals(List(5) { "answer" }, answers)
    assertEquals(1, loads)
  }

  @Test
  fun rateLimiter_holdsCallersBackOnceTheWindowIsSpent() = runBlocking {
    val limiter = DramaBoxRateLimiter(maxRequests = 2, windowMs = 300)
    limiter.acquire()
    limiter.acquire()

    val waitedMs = measureTimeMillis { limiter.acquire() }
    // The third request cannot go out until the first falls out of the window.
    assertTrue("waited only ${waitedMs}ms", waitedMs >= 200)
  }

  @Test
  fun rateLimiter_waitsOutTheCooldownTheServerAsksFor() = runBlocking {
    val limiter = DramaBoxRateLimiter(maxRequests = 8, windowMs = 60_000)
    limiter.backOff(retryAfterMs = 250)

    val waitedMs = measureTimeMillis { limiter.acquire() }
    assertTrue("waited only ${waitedMs}ms", waitedMs >= 200)
  }

  @Test
  fun retryAfter_readsTheHeaderAndFallsBackToAFullWindow() {
    assertEquals(45_000L, retryAfterMs("45"))
    assertEquals(45_000L, retryAfterMs("  45  "))
    assertEquals(60_000L, retryAfterMs(null))
    // An HTTP-date is legal but unused here, so it reads as "no useful answer".
    assertEquals(60_000L, retryAfterMs("Wed, 21 Oct 2026 07:28:00 GMT"))
    // Absurd values are clamped rather than trusted.
    assertEquals(300_000L, retryAfterMs("86400"))
    assertEquals(1_000L, retryAfterMs("0"))
  }

  @Test
  fun busyException_tellsTheViewerHowLongToWait() {
    assertEquals(
      "Short dramas are busy right now. Try again in 45 seconds.",
      DramaBoxBusyException(45_000L).message,
    )
  }
}
