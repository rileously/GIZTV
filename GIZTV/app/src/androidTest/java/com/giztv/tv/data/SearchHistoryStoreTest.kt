package com.giztv.tv.data

import androidx.test.core.app.ApplicationProvider
import org.junit.Assert.assertEquals
import org.junit.Assert.assertTrue
import org.junit.Before
import org.junit.Test

class SearchHistoryStoreTest {
  private val context = ApplicationProvider.getApplicationContext<android.content.Context>()
  private val store = SearchHistoryStore(context)

  @Before
  fun clearStore() {
    store.clearAll()
  }

  @Test
  fun recent_returnsNewestFirst() {
    store.record(SearchSection.MOVIES, "Dune")
    store.record(SearchSection.MOVIES, "Arrival")

    assertEquals(listOf("Arrival", "Dune"), store.recent(SearchSection.MOVIES))
  }

  @Test
  fun sectionsAreKeptApart() {
    store.record(SearchSection.MOVIES, "Dune")
    store.record(SearchSection.SPORTS, "Arsenal")

    assertEquals(listOf("Dune"), store.recent(SearchSection.MOVIES))
    assertEquals(listOf("Arsenal"), store.recent(SearchSection.SPORTS))
    assertTrue(store.recent(SearchSection.ANIME).isEmpty())
  }

  @Test
  fun repeatingAQueryMovesItToTheFrontRatherThanAddingIt() {
    store.record(SearchSection.TV_SHOWS, "Severance")
    store.record(SearchSection.TV_SHOWS, "Andor")
    store.record(SearchSection.TV_SHOWS, "severance")

    assertEquals(listOf("severance", "Andor"), store.recent(SearchSection.TV_SHOWS))
  }

  /** Typing a search leaves every prefix of it behind; only what was settled on is worth keeping. */
  @Test
  fun aQueryReplacesThePrefixesItWasTypedThrough() {
    store.record(SearchSection.SHORT_DRAMAS, "b")
    store.record(SearchSection.SHORT_DRAMAS, "bi")
    store.record(SearchSection.SHORT_DRAMAS, "billionaire")

    assertEquals(listOf("billionaire"), store.recent(SearchSection.SHORT_DRAMAS))
  }

  @Test
  fun unrelatedQueriesSurviveALongerOne() {
    store.record(SearchSection.IPTV, "sky sports")
    store.record(SearchSection.IPTV, "bbc")
    store.record(SearchSection.IPTV, "bbc one")

    assertEquals(listOf("bbc one", "sky sports"), store.recent(SearchSection.IPTV))
  }

  @Test
  fun blankQueriesAreIgnored() {
    store.record(SearchSection.ANIME, "   ")

    assertTrue(store.recent(SearchSection.ANIME).isEmpty())
  }

  @Test
  fun queriesAreTrimmed() {
    store.record(SearchSection.ANIME, "  frieren  ")

    assertEquals(listOf("frieren"), store.recent(SearchSection.ANIME))
  }

  @Test
  fun onlyTheTenMostRecentAreKept() {
    // Unrelated to each other, so none of them is dropped as a prefix of the next.
    (1..12).forEach { store.record(SearchSection.SOCCER, "team-$it") }

    val recent = store.recent(SearchSection.SOCCER)
    assertEquals(10, recent.size)
    assertEquals("team-12", recent.first())
    assertEquals("team-3", recent.last())
  }

  @Test
  fun clear_emptiesOneSectionAndLeavesTheRest() {
    store.record(SearchSection.MOVIES, "Dune")
    store.record(SearchSection.SPORTS, "Arsenal")

    store.clear(SearchSection.MOVIES)

    assertTrue(store.recent(SearchSection.MOVIES).isEmpty())
    assertEquals(listOf("Arsenal"), store.recent(SearchSection.SPORTS))
  }

  @Test
  fun remove_dropsOneQueryAndKeepsTheOthers() {
    store.record(SearchSection.MOVIES, "Dune")
    store.record(SearchSection.MOVIES, "Arrival")

    store.remove(SearchSection.MOVIES, "dune")

    assertEquals(listOf("Arrival"), store.recent(SearchSection.MOVIES))
  }

  @Test
  fun clearAll_emptiesEverySection() {
    SearchSection.entries.forEach { store.record(it, "anything") }

    store.clearAll()

    SearchSection.entries.forEach { assertTrue(store.recent(it).isEmpty()) }
  }
}
