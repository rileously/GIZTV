package com.example.auroratv

import com.example.auroratv.ui.catalog.PlaybackCastMember
import com.example.auroratv.ui.catalog.PlaybackDirector
import com.example.auroratv.ui.catalog.PlaybackReview
import com.example.auroratv.ui.catalog.looksLikeLowQualityReview
import com.example.auroratv.ui.catalog.pauseReviewExcerpt
import com.example.auroratv.ui.player.PauseCastRotationStore
import com.example.auroratv.ui.player.PauseTip
import com.example.auroratv.ui.player.PauseTipCatalog
import com.example.auroratv.ui.player.PauseTipKind
import com.example.auroratv.ui.player.PauseTipRotationStore
import com.example.auroratv.ui.player.PauseTriviaFact
import com.example.auroratv.ui.player.buildPauseTipPool
import com.example.auroratv.ui.player.looksLikeMarketingSlogan
import com.example.auroratv.ui.player.nextPauseCastIndex
import com.example.auroratv.ui.player.pauseCastTriviaDetail
import com.example.auroratv.ui.player.pauseReviewAttribution
import com.example.auroratv.ui.player.pauseTriviaFacts
import com.example.auroratv.ui.player.pauseTriviaSnippet
import com.example.auroratv.ui.player.selectNextPauseTipId
import com.example.auroratv.ui.player.structuredTriviaFactoid
import kotlin.random.Random
import org.junit.Assert.assertEquals
import org.junit.Assert.assertFalse
import org.junit.Assert.assertNotEquals
import org.junit.Assert.assertNull
import org.junit.Assert.assertTrue
import org.junit.Before
import org.junit.Test

class PauseCastTriviaTest {
  @Before
  fun resetRotation() {
    PauseTipRotationStore.clear()
    PauseCastRotationStore.clear()
  }

  @Test
  fun nextIndex_startsAtZeroAndWrapsAround() {
    assertEquals(0, nextPauseCastIndex(lastShownIndex = null, castSize = 3))
    assertEquals(1, nextPauseCastIndex(lastShownIndex = 0, castSize = 3))
    assertEquals(2, nextPauseCastIndex(lastShownIndex = 1, castSize = 3))
    assertEquals(0, nextPauseCastIndex(lastShownIndex = 2, castSize = 3))
  }

  @Test
  fun nextIndex_isUnavailableWhenCastIsEmpty() {
    assertEquals(-1, nextPauseCastIndex(lastShownIndex = null, castSize = 0))
    assertEquals(-1, nextPauseCastIndex(lastShownIndex = 4, castSize = 0))
  }

  @Test
  fun tipPool_includesCastDirectorFactsAndReviews() {
    val catalog =
      PauseTipCatalog(
        cast =
          listOf(
            PlaybackCastMember(name = "A", character = "Lead", order = 0),
            PlaybackCastMember(name = "B", character = "Friend", order = 1),
          ),
        director = PlaybackDirector(name = "Jane Director"),
        triviaFacts =
          listOf(
            PauseTriviaFact(id = "fact:release", line = "Released 2012 · ★ 8.0"),
            PauseTriviaFact(id = "fact:runtime", line = "Runtime 2h 23m"),
          ),
        reviews =
          listOf(
            PlaybackReview(
              id = "r1",
              author = "Alex",
              excerpt = "A gripping story with sharp performances throughout the runtime.",
              rating = 8.0,
            ),
          ),
      )
    val pool = catalog.buildPauseTipPool()
    assertEquals(6, pool.size)
    assertEquals(
      setOf(PauseTipKind.CAST, PauseTipKind.DIRECTOR, PauseTipKind.TRIVIA, PauseTipKind.REVIEW),
      pool.map { it.kind }.toSet(),
    )
    assertTrue(pool.any { it.tipId == "review:r1" })
    assertTrue(pool.any { it.tipId == "fact:runtime" })
  }

  @Test
  fun selectNext_avoidsRepeatsUntilPoolCovered() {
    val ids = listOf("a", "b", "c")
    val shown = mutableSetOf<String>()
    val lastAt = mutableMapOf<String, Long>()
    var last: String? = null
    var clock = 0L
    val random = Random(0)
    repeat(3) {
      val pick =
        selectNextPauseTipId(
          poolIds = ids,
          shownInRound = shown,
          lastShownId = last,
          lastShownAt = lastAt,
          random = random,
        )!!
      assertFalse(pick in shown)
      shown.add(pick)
      last = pick
      clock += 1
      lastAt[pick] = clock
    }
    assertEquals(ids.toSet(), shown)
    // Fourth pick starts a new round via LRS (oldest = first shown).
    val oldest = lastAt.minBy { it.value }.key
    val next =
      selectNextPauseTipId(
        poolIds = ids,
        shownInRound = shown,
        lastShownId = last,
        lastShownAt = lastAt,
        random = random,
      )
    assertEquals(oldest, next)
    assertNotEquals(last, next)
  }

  @Test
  fun rotationStore_coversFullPoolBeforeRepeatingAnyTipId() {
    val catalog =
      PauseTipCatalog(
        cast =
          listOf(
            PlaybackCastMember(name = "A", character = "Lead", order = 0),
            PlaybackCastMember(name = "B", character = "Friend", order = 1),
          ),
        director = PlaybackDirector(name = "Jane Director", profilePath = "/jane.jpg"),
        triviaFacts = listOf(PauseTriviaFact(id = "fact:release", line = "Released 2012 · ★ 8.0")),
        reviews =
          listOf(
            PlaybackReview(
              id = "rev-1",
              author = "Sam",
              excerpt = "An unforgettable thriller that rewards a second viewing every time.",
              rating = 9.0,
            ),
          ),
      )
    val poolIds = catalog.buildPauseTipPool().map { it.tipId }.toSet()
    assertEquals(5, poolIds.size)
    val random = Random(42)
    val seen = mutableListOf<String>()
    repeat(poolIds.size) {
      val tip = PauseTipRotationStore.nextTip("movie-1", catalog, random = random)!!
      assertFalse(tip.tipId in seen)
      seen.add(tip.tipId)
    }
    assertEquals(poolIds, seen.toSet())

    // After full coverage, a tip may repeat — but not the one just shown.
    val after = PauseTipRotationStore.nextTip("movie-1", catalog, random = random)!!
    assertTrue(after.tipId in poolIds)
    assertNotEquals(seen.last(), after.tipId)

    // Independent title starts a fresh round.
    val other = PauseTipRotationStore.nextTip("movie-2", catalog, random = Random(1))
    assertTrue(other != null)
  }

  @Test
  fun rotationStore_includesReviewTipsWithAttribution() {
    val catalog =
      PauseTipCatalog(
        reviews =
          listOf(
            PlaybackReview(
              id = "abc",
              author = "Brett Pascoe",
              excerpt = "In my top five of all time favourite movies with a story that holds up.",
              rating = 9.0,
            ),
          ),
      )
    val tip = PauseTipRotationStore.nextTip("only-reviews", catalog, random = Random(0))
    assertTrue(tip is PauseTip.Review)
    val review = (tip as PauseTip.Review).review
    assertEquals("Brett Pascoe", review.author)
    assertEquals("Brett Pascoe · ★ 9", pauseReviewAttribution(review))
  }

  @Test
  fun rotationStore_softSkipsEmptyKindsAndReturnsNullWhenEmpty() {
    val castOnly =
      PauseTipCatalog(cast = listOf(PlaybackCastMember(name = "A", character = "Lead", order = 0)))
    assertTrue(PauseTipRotationStore.nextTip("m", castOnly, random = Random(0)) is PauseTip.Cast)
    assertNull(PauseTipRotationStore.nextTip("empty", PauseTipCatalog()))
  }

  @Test
  fun rotationStore_castOnlyShowsEachMemberBeforeRepeat() {
    val cast =
      listOf(
        PlaybackCastMember(name = "A", character = "Lead", order = 0),
        PlaybackCastMember(name = "B", character = "Friend", order = 1),
      )
    val random = Random(7)
    val first = PauseCastRotationStore.nextMember("movie-1", cast, random = random)?.name
    val second = PauseCastRotationStore.nextMember("movie-1", cast, random = random)?.name
    assertEquals(setOf("A", "B"), setOf(first, second))
    val third = PauseCastRotationStore.nextMember("movie-1", cast, random = random)?.name
    assertTrue(third == "A" || third == "B")
    assertNotEquals(second, third)
  }

  @Test
  fun triviaFacts_emitsSeparateStructuredTips() {
    val facts =
      pauseTriviaFacts(
        year = "2012",
        rating = 8.0,
        runtimeMinutes = 143,
        genres = listOf("Drama", "Thriller"),
        overview = "A long plot synopsis that would otherwise be used.",
      )
    assertEquals(
      listOf(
        PauseTriviaFact("fact:release", "Released 2012 · ★ 8.0"),
        PauseTriviaFact("fact:runtime", "Runtime 2h 23m"),
        PauseTriviaFact("fact:genres", "Drama · Thriller"),
      ),
      facts,
    )
  }

  @Test
  fun triviaSnippet_prefersStructuredFactsOverOverview() {
    assertEquals(
      "Released 2012 · ★ 8.0",
      pauseTriviaSnippet(
        year = "2012",
        rating = 8.0,
        overview = "A long plot synopsis that would otherwise be used.",
      ),
    )
  }

  @Test
  fun triviaSnippet_neverUsesMarketingTaglines() {
    assertNull(pauseTriviaSnippet(overview = "Look up in the Sky!"))
    assertNull(pauseTriviaSnippet(overview = "Assemble."))
    assertTrue(looksLikeMarketingSlogan("Look up in the Sky!"))
    assertTrue(looksLikeMarketingSlogan("HOPE IS A DANGEROUS THING"))
    assertFalse(
      looksLikeMarketingSlogan(
        "In Gotham City, a masked vigilante fights corruption while facing a rising criminal empire.",
      ),
    )
  }

  @Test
  fun triviaSnippet_fallsBackToFactualOverviewSentence() {
    assertEquals(
      "In a neon-soaked city, a detective uncovers a conspiracy that reaches the mayor's office.",
      pauseTriviaSnippet(
        overview =
          "In a neon-soaked city, a detective uncovers a conspiracy that reaches the mayor's office. Later, secrets bloom.",
      ),
    )
  }

  @Test
  fun triviaSnippet_usesBasedOnAttributionFromOverview() {
    assertEquals(
      "Based on the novel by Frank Herbert",
      pauseTriviaSnippet(
        overview = "Based on the novel by Frank Herbert. Far in the future, spice fuels an empire.",
      ),
    )
  }

  @Test
  fun structuredFactoid_runtimeGenresAndTopBilled() {
    assertEquals("Runtime 2h 23m", structuredTriviaFactoid(runtimeMinutes = 143))
    assertEquals("Drama · Thriller", structuredTriviaFactoid(genres = listOf("Drama", "Thriller")))
    assertEquals(
      "Top billed: Timothée Chalamet",
      structuredTriviaFactoid(
        topBilledName = "Timothée Chalamet",
        includeTopBilled = true,
      ),
    )
    assertNull(
      structuredTriviaFactoid(
        topBilledName = "Timothée Chalamet",
        includeTopBilled = false,
      ),
    )
    assertNull(structuredTriviaFactoid())
  }

  @Test
  fun triviaSnippet_softSkipsWhenNothingUseful() {
    assertNull(pauseTriviaSnippet())
    assertNull(pauseTriviaSnippet(overview = "   "))
    assertNull(pauseTriviaSnippet(overview = "Go!"))
  }

  @Test
  fun triviaDetail_prefersGuestBillingAndDepartment() {
    assertEquals(
      "Guest appearance · Billed #2",
      pauseCastTriviaDetail(
        PlaybackCastMember(name = "Guest", character = "Neighbor", order = 1, guest = true),
      ),
    )
    assertEquals(
      "Billed #1",
      pauseCastTriviaDetail(PlaybackCastMember(name = "Star", character = "Hero", order = 0)),
    )
    assertEquals(
      "In this title",
      pauseCastTriviaDetail(PlaybackCastMember(name = "Extra", character = "Crowd")),
    )
  }

  @Test
  fun reviewExcerpt_filtersShortAndKeepsFullBody() {
    assertNull(pauseReviewExcerpt("Too short"))
    assertNull(pauseReviewExcerpt("AMAZING MUST WATCH NOW!!!!"))
    assertTrue(looksLikeLowQualityReview("WOW BEST MOVIE EVER MADE HANDS DOWN"))
    val long =
      "This film builds a quiet, devastating portrait of friendship under pressure, " +
        "with careful pacing and performances that linger long after the credits."
    val excerpt = pauseReviewExcerpt(long)!!
    assertEquals(long, excerpt)
    assertTrue(!excerpt.endsWith("…"))
  }

  @Test
  fun reviewExcerpt_softCapsExtremeLengthWithoutEllipsis() {
    val novel = "Action, good Fx and a not-completely-absurd story. ".repeat(200)
    val excerpt = pauseReviewExcerpt(novel)!!
    assertTrue(excerpt.length <= 4_000)
    assertTrue(!excerpt.endsWith("…"))
    assertTrue(excerpt.startsWith("Action, good Fx"))
  }
}
