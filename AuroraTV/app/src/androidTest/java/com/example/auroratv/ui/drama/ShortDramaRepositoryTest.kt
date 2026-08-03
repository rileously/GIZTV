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
  fun searchParser_readsDramaFieldsAndSkipsInvalidRows() {
    val dramas =
      parseShortDramas(
        """
        [
          {
            "bookId": "42000012638",
            "bookName": "The One You Love is Her Twin Sister",
            "introduction": "Countryside girl Elise Jensen marries Cyrus Fenton.",
            "cover": "https://hwztchapter.dramaboxdb.com/cover.jpg?t=1",
            "protagonist": "Cyrus Fenton,  Elise Jensen",
            "tagNames": ["Revenge", "Modern"]
          },
          { "bookId": "", "bookName": "Missing id" },
          { "bookId": "42000000001", "bookName": "   " }
        ]
        """.trimIndent()
      )

    val drama = dramas.single()
    assertEquals("42000012638", drama.bookId)
    assertEquals("The One You Love is Her Twin Sister", drama.bookName)
    assertEquals("https://hwztchapter.dramaboxdb.com/cover.jpg?t=1", drama.coverUrl)
    assertEquals(listOf("Revenge", "Modern"), drama.tags)
    // The first tag reads better on a card than the cast list does.
    assertEquals("Revenge", drama.subtitle)
  }

  @Test
  fun searchParser_fallsBackToTheCastWhenThereAreNoTags() {
    val drama =
      parseShortDramas("""[{"bookId":"1","bookName":"Untagged","protagonist":"Ada"}]""").single()

    assertEquals("Ada", drama.subtitle)
    assertNull(drama.coverUrl)
    assertTrue(drama.tags.isEmpty())
  }

  @Test
  fun searchParser_returnsNothingForAnErrorPayload() {
    assertTrue(parseShortDramas("""{"error":"Terlalu Banyak Permintaan"}""").isEmpty())
  }

  @Test
  fun detailParser_readsChapterCountAndTags() {
    val detail =
      parseShortDramaDetail(
        """
        {
          "bookId": "42000012638",
          "bookName": "The One You Love is Her Twin Sister",
          "coverWap": "https://hwztchapter.dramaboxdb.com/cover.jpg",
          "chapterCount": 71,
          "introduction": "A synopsis.",
          "tags": ["Revenge", "Modern"]
        }
        """.trimIndent()
      )

    assertEquals(71, detail.chapterCount)
    assertEquals("https://hwztchapter.dramaboxdb.com/cover.jpg", detail.coverUrl)
    assertEquals(listOf("Revenge", "Modern"), detail.tags)
  }

  @Test
  fun detailParser_alwaysLeavesAtLeastOneEpisode() {
    assertEquals(1, parseShortDramaDetail("""{"bookId":"1","chapterCount":0}""").chapterCount)
  }

  @Test
  fun slug_lowercasesAndHyphenatesTheTitle() {
    assertEquals(
      "the-one-you-love-is-her-twin-sister",
      chartDramaSlug("The One You Love is Her Twin Sister"),
    )
    assertEquals("ceo-s-secret-wife", chartDramaSlug("CEO's Secret Wife!"))
  }

  @Test
  fun slug_collapsesPunctuationAndSpacingIntoOneHyphen() {
    // A comma and the space after it are one run of punctuation, so they are one hyphen, not two.
    assertEquals(
      "father-of-my-ex-owner-of-my-heart",
      chartDramaSlug("Father of My Ex, Owner of My Heart"),
    )
    assertEquals("married-at-first-sight", chartDramaSlug("  Married — At First Sight!!  "))
    assertEquals(
      "https://dramabox.chartdrama.com/p/42000017050/father-of-my-ex-owner-of-my-heart?ep=1",
      chartDramaEpisodeUrl("42000017050", "Father of My Ex, Owner of My Heart", 1),
    )
  }

  @Test
  fun episodeUrl_carriesBookIdSlugAndEpisode() {
    assertEquals(
      "https://dramabox.chartdrama.com/p/42000012638/the-one-you-love-is-her-twin-sister?ep=1",
      chartDramaEpisodeUrl("42000012638", "The One You Love is Her Twin Sister", 1),
    )
  }

  @Test(expected = IllegalArgumentException::class)
  fun episodeUrl_rejectsEpisodesBelowOne() {
    chartDramaEpisodeUrl("42000012638", "Any Drama", 0)
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
      "DramaBox is busy right now. Try again in 45 seconds.",
      DramaBoxBusyException(45_000L).message,
    )
  }
}
