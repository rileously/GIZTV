package com.giztv.tv

import com.giztv.tv.data.PlaybackContext
import com.giztv.tv.ui.sports.ALL_SPORTS
import com.giztv.tv.ui.sports.FOOTBALL
import com.giztv.tv.ui.sports.SportEvent
import com.giztv.tv.ui.sports.SportsFeedCache
import com.giztv.tv.ui.sports.eventStartMs
import com.giztv.tv.ui.sports.eventsForSport
import com.giztv.tv.ui.sports.parseSportEvents
import com.giztv.tv.ui.sports.searchEvents
import com.giztv.tv.ui.sports.sortedForViewing
import com.giztv.tv.ui.sports.sportFilters
import com.giztv.tv.ui.sports.toPlayback
import java.io.IOException
import java.util.TimeZone
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

/** What the sports page shows, and the order it decides to show it in. */
class SportsEventsTest {
  @Test
  fun parser_readsTheFeedAndSkipsRowsWithNothingToOpen() {
    val events = parseSportEvents(FEED, TimeZone.getTimeZone("UTC"))

    // Three usable rows: the one with no page and the one with no match are both unopenable.
    assertEquals(listOf("Platense vs Talleres", "Celtic vs Dundee", "Fritz vs Jodar"), events.map { it.match })
    val platense = events.first()
    assertEquals("399459272", platense.id)
    assertEquals("Argentinian Primera Division", platense.league)
    assertEquals(FOOTBALL, platense.sport)
    assertEquals("Round 3", platense.round)
    // The feed names its own host, so the player page arrives absolute.
    assertEquals("https://hoofoot.ru/iptv/live-player?id=399459272", platense.watchUrl)
    assertEquals("Platense", platense.homeTeam)
    assertEquals("Talleres", platense.awayTeam)
  }

  @Test
  fun parser_treatsAJsonNullAsNothingRatherThanTheWordNull() {
    // `optString` hands back the literal "null" for a JSON null, which would reach the card as text.
    val tennis = parseSportEvents(FEED, TimeZone.getTimeZone("UTC")).last()

    assertNull(tennis.round)
    assertNull(tennis.location)
    assertNull(tennis.thumbUrl)
    assertEquals("Tennis", tennis.sport)
  }

  @Test
  fun parser_returnsNothingForAPayloadItCannotRead() {
    assertTrue(parseSportEvents("""{"error":"forbidden"}""").isEmpty())
    assertTrue(parseSportEvents("not json at all").isEmpty())
  }

  @Test
  fun startTime_isReadAsUtcWhateverTheDeviceIsSetTo() {
    // An hour of drift would put a kick-off on the wrong side of "now" and sort the page wrongly.
    assertEquals(1_785_794_400_000L, eventStartMs("2026-08-03", "22:00"))
    assertEquals(1_785_786_300_000L, eventStartMs("2026-08-03", "19:45"))
    assertNull(eventStartMs(null, "19:45"))
    assertNull(eventStartMs("2026-08-03", null))
    assertNull(eventStartMs("2026-08-03", "not a time"))
    assertNull(eventStartMs("2026-13-45", "19:45"))
  }

  @Test
  fun kickOff_isWordedInTheDeviceOwnZone() {
    // 22:00 UTC is the next morning in Tokyo, so the day is named as well as the time.
    val tokyo = parseSportEvents(FEED, TimeZone.getTimeZone("Asia/Tokyo")).first()
    assertTrue("was ${tokyo.kickOffLabel}", tokyo.kickOffLabel.orEmpty().endsWith("07:00"))

    val utc = parseSportEvents(FEED, TimeZone.getTimeZone("UTC")).first()
    assertTrue("was ${utc.kickOffLabel}", utc.kickOffLabel.orEmpty().endsWith("22:00"))
  }

  @Test
  fun order_putsFootballFirstAndAMatchInPlayAboveOneStillToCome() {
    val ordered =
      sortedForViewing(
        listOf(
          event(id = "1", sport = "Tennis", status = "Live"),
          event(id = "2", sport = FOOTBALL, status = "Ended"),
          event(id = "3", sport = FOOTBALL, status = "NS", startAtMs = 200),
          event(id = "4", sport = FOOTBALL, status = "Live"),
          event(id = "5", sport = "Basketball", status = "NS"),
        )
      )

    // Every football fixture comes before every other sport, in play before still to come, and a
    // finished match sinks below both without dropping off the page.
    assertEquals(listOf("4", "3", "2", "1", "5"), ordered.map { it.id })
  }

  @Test
  fun order_prefersTheHighlightedFixturesAndThenTheEarliestKickOff() {
    val ordered =
      sortedForViewing(
        listOf(
          event(id = "late", sport = FOOTBALL, status = "NS", startAtMs = 900),
          event(id = "early", sport = FOOTBALL, status = "NS", startAtMs = 100),
          event(id = "ranked", sport = FOOTBALL, status = "NS", startAtMs = 900, popular = true, ranking = 2),
          event(id = "top", sport = FOOTBALL, status = "NS", startAtMs = 900, popular = true, ranking = 1),
          // Nothing said about when it starts, so it waits behind everything that has a time.
          event(id = "undated", sport = FOOTBALL, status = "NS", startAtMs = null),
        )
      )

    assertEquals(listOf("top", "ranked", "early", "late", "undated"), ordered.map { it.id })
  }

  @Test
  fun chips_leadWithFootballAndThenTheBusiestSportsTheFeedIsCarrying() {
    val events =
      listOf(
        event(id = "1", sport = "Tennis"),
        event(id = "2", sport = FOOTBALL),
        event(id = "3", sport = "Cricket"),
        event(id = "4", sport = "Tennis"),
        event(id = "5", sport = FOOTBALL),
      )

    assertEquals(listOf(FOOTBALL, "Tennis", "Cricket", ALL_SPORTS), sportFilters(events))
    // A night with only football on it has nothing to filter, so it offers no chips at all.
    assertEquals(listOf(FOOTBALL), sportFilters(listOf(event(id = "1", sport = FOOTBALL))))
    assertTrue(sportFilters(emptyList()).isEmpty())
  }

  @Test
  fun chips_stillLeadWithFootballWhenItIsNotTheBusiestSport() {
    val events =
      listOf(
        event(id = "1", sport = "Tennis"),
        event(id = "2", sport = "Tennis"),
        event(id = "3", sport = "Tennis"),
        event(id = "4", sport = FOOTBALL),
      )

    assertEquals(listOf(FOOTBALL, "Tennis", ALL_SPORTS), sportFilters(events))
  }

  @Test
  fun sportFilter_keepsOneSportAndTheLastChipKeepsEverything() {
    val events =
      listOf(
        event(id = "1", sport = "Tennis", status = "Live"),
        event(id = "2", sport = FOOTBALL, status = "NS"),
      )

    assertEquals(listOf("2"), eventsForSport(events, FOOTBALL).map { it.id })
    // Everything at once is still read football first.
    assertEquals(listOf("2", "1"), eventsForSport(events, ALL_SPORTS).map { it.id })
    assertEquals(listOf("2", "1"), eventsForSport(events, null).map { it.id })
    assertTrue(eventsForSport(events, "Handball").isEmpty())
  }

  @Test
  fun search_looksAtTheWholeScheduleRatherThanTheChosenSport() {
    val events =
      listOf(
        event(id = "1", sport = "Tennis", match = "Taylor Fritz vs Rafael Jodar"),
        event(id = "2", sport = FOOTBALL, match = "Celtic vs Dundee", league = "Scottish Premiership"),
        event(id = "3", sport = FOOTBALL, match = "Platense vs Talleres"),
      )

    assertEquals(listOf("2"), searchEvents(events, "celtic").map { it.id })
    // A competition is as good a thing to search for as a team.
    assertEquals(listOf("2"), searchEvents(events, "Scottish").map { it.id })
    assertEquals(listOf("1"), searchEvents(events, "tennis").map { it.id })
    // Nothing typed is not a search: the whole schedule comes back in its usual order.
    assertEquals(listOf("2", "3", "1"), searchEvents(events, "   ").map { it.id })
  }

  @Test
  fun card_saysWhetherAMatchIsOnNowAndCarriesTheScoreToTheTeams() {
    val live = event(id = "1", status = "Live", score = "2:1")
    assertTrue(live.live)
    assertEquals("LIVE", live.statusLabel)
    assertEquals("2", live.homeScore)
    assertEquals("1", live.awayScore)

    val ended = event(id = "2", status = "Ended", score = "0:0")
    assertTrue(ended.ended)
    assertEquals("FT", ended.statusLabel)

    // Nothing has been played yet, so the corner of the card is worth the kick-off time instead.
    val upcoming = event(id = "3", status = "NS", kickOffLabel = "19:45")
    assertNull(upcoming.homeScore)
    assertEquals("19:45", upcoming.statusLabel)
    assertEquals("Scheduled", event(id = "4", status = "NS", kickOffLabel = null).statusLabel)
  }

  @Test
  fun card_readsTheCompetitionAndItsStageAsOneLine() {
    assertEquals(
      "Argentinian Primera Division · Round 3",
      event(id = "1", league = "Argentinian Primera Division", round = "Round 3").subtitle,
    )
    // Tennis has no rounds to name, so nothing is left dangling behind a separator.
    assertEquals("ATP Toronto", event(id = "2", league = "ATP Toronto", round = null).subtitle)
  }

  @Test
  fun playback_carriesTheFixtureAndTellsTheLoadingPageWhatItIs() {
    val live =
      event(
          id = "1",
          match = "Taylor Fritz vs Rafael Jodar",
          league = "ATP. Washington",
          sport = "Tennis",
          status = "Live",
          score = "1:0",
        )
        .toPlayback()

    assertEquals("https://hoofoot.ru/iptv/live-player?id=1", live.pageUrl)
    assertEquals("Taylor Fritz vs Rafael Jodar", live.title)
    assertEquals("ATP. Washington · LIVE", live.subtitle)
    // Without this the loading page calls a live match a movie.
    assertEquals("LIVE", live.kindLabel)
    // A fixture is neither a film nor an episode, so nothing here should look like a season.
    assertFalse(live.isEpisode)
    assertTrue(live.playlist.isEmpty())
    assertFalse(live.shortForm)

    // One still to come is badged with its kick-off rather than a "LIVE" that is not true yet.
    assertEquals("19:45", event(id = "2", status = "NS", kickOffLabel = "19:45").toPlayback().kindLabel)
    assertEquals("FT", event(id = "3", status = "Ended").toPlayback().kindLabel)
  }

  @Test
  fun playback_ofAFilmStillNamesItselfWithoutBeingTold() {
    // The badge falls back to what the context already knows, so nothing else has to change.
    assertNull(PlaybackContext(pageUrl = "https://example.com/movie/1", title = "A Film").kindLabel)
  }

  @Test
  fun feedCache_servesAStoredAnswerWithoutAskingAgain() = runBlocking {
    var loads = 0
    val cache = SportsFeedCache(ttlMs = 60_000)
    val load: suspend () -> List<SportEvent> = { loads += 1; listOf(event(id = "1")) }

    assertEquals(1, cache.get(load).size)
    assertEquals(1, cache.get(load).size)
    assertEquals(1, loads)
  }

  @Test
  fun feedCache_goesBackForFreshScoresOnceTheAnswerHasAged() = runBlocking {
    var loads = 0
    var clock = 1_000L
    val cache = SportsFeedCache(ttlMs = 45_000, now = { clock })
    val load: suspend () -> List<SportEvent> = { loads += 1; listOf(event(id = "1")) }

    cache.get(load)
    clock += 44_999
    cache.get(load)
    assertEquals(1, loads)
    clock += 1
    cache.get(load)
    assertEquals(2, loads)
  }

  @Test
  fun feedCache_dropsWhatItHeldWhenTheViewerAsksForFreshScores() = runBlocking {
    var loads = 0
    val cache = SportsFeedCache(ttlMs = 60_000)
    val load: suspend () -> List<SportEvent> = { loads += 1; listOf(event(id = "1")) }

    cache.get(load)
    cache.clear()
    cache.get(load)
    assertEquals(2, loads)
  }

  @Test
  fun feedCache_neverStoresAFailure() = runBlocking {
    val cache = SportsFeedCache(ttlMs = 60_000)
    runCatching { cache.get { throw IOException("offline") } }

    var loads = 0
    assertEquals(1, cache.get { loads += 1; listOf(event(id = "1")) }.size)
    assertEquals(1, loads)
  }

  @Test
  fun feedCache_letsOnlyOneConcurrentCallerGoToTheFeed() = runBlocking {
    var loads = 0
    val cache = SportsFeedCache(ttlMs = 60_000)
    val answers =
      (1..5)
        .map {
          async(Dispatchers.Default) { cache.get { loads += 1; delay(50); listOf(event(id = "1")) } }
        }
        .awaitAll()

    assertTrue(answers.all { it.size == 1 })
    assertEquals(1, loads)
  }

  @Test
  fun teams_surviveANameThatIsNotAPairing() {
    // A tournament session or a race is listed under one name, and the card must not line it up
    // against an opponent the feed never named.
    val solo = event(id = "1", match = "ATP World Tour 1000: Montreal 2026")
    assertEquals("ATP World Tour 1000: Montreal 2026", solo.homeTeam)
    assertTrue(solo.awayTeam.isEmpty())
    assertFalse(solo.isPairing)
    assertFalse(solo.live)

    val pairing = event(id = "2", match = "Jessica Pegula (W) vs Alexandra Eala (W)")
    assertTrue(pairing.isPairing)
    assertEquals("Jessica Pegula (W)", pairing.homeTeam)
    assertEquals("Alexandra Eala (W)", pairing.awayTeam)
  }

  @Test
  fun parser_treatsTheFeedsOwnPlaceholderLeagueAsNothingSaid() {
    // "Unknown League" is the feed admitting it has no competition to name; printed verbatim it
    // would put that admission on the card.
    val events =
      parseSportEvents(
        """
        {
          "embed_base_url": "https://hoofoot.ru",
          "events": [
            {
              "id": "1",
              "Match": "ATP World Tour 1000: Montreal 2026",
              "League": "Unknown League",
              "Sport": "Tennis",
              "Status": "Live",
              "embed_url": "/iptv/live-player?id=1"
            }
          ]
        }
        """.trimIndent()
      )

    assertEquals("", events.single().league)
    assertEquals("", events.single().subtitle)
  }

  private fun event(
    id: String,
    match: String = "Home vs Away",
    league: String = "A League",
    sport: String = FOOTBALL,
    status: String = "NS",
    popular: Boolean = false,
    ranking: Int = Int.MAX_VALUE,
    score: String? = null,
    round: String? = null,
    startAtMs: Long? = 0L,
    kickOffLabel: String? = null,
  ) =
    SportEvent(
      id = id,
      match = match,
      league = league,
      sport = sport,
      status = status,
      popular = popular,
      ranking = ranking,
      score = score,
      round = round,
      location = null,
      homeBadgeUrl = null,
      awayBadgeUrl = null,
      thumbUrl = null,
      startAtMs = startAtMs,
      kickOffLabel = kickOffLabel,
      watchUrl = "https://hoofoot.ru/iptv/live-player?id=$id",
    )

  private companion object {
    /** A trimmed copy of the live payload, keeping the shapes that need handling. */
    const val FEED = """
      {
        "total": 5,
        "total_popular": 1,
        "embed_base_url": "https://hoofoot.ru",
        "timezone": "UTC",
        "events": [
          {
            "id": "399459272",
            "Match": "Platense vs Talleres",
            "League": "Argentinian Primera Division",
            "Sport": "Football",
            "Date": "2026-08-03",
            "Time": "22:00",
            "Status": "NS",
            "Popular": true,
            "Ranking": 1,
            "Score": null,
            "Round": "Round 3",
            "Location": "Estadio Ciudad de Vicente Lopez",
            "HomeTeamImage": "https://r2.thesportsdb.com/home.png",
            "AwayTeamImage": "https://r2.thesportsdb.com/away.png",
            "ThumbURL": "https://r2.thesportsdb.com/thumb.jpg",
            "embed_url": "/iptv/live-player?id=399459272"
          },
          {
            "id": "742488420",
            "Match": "Celtic vs Dundee",
            "League": "Scottish Premiership",
            "Sport": "Football",
            "Date": "2026-08-03",
            "Time": "18:30",
            "Status": "NS",
            "Popular": false,
            "Round": "Round 1",
            "embed_url": "/iptv/live-player?id=742488420"
          },
          {
            "id": "556677",
            "Match": "Fritz vs Jodar",
            "League": "ATP Toronto",
            "Sport": "Tennis",
            "Date": "2026-08-03",
            "Time": "18:40",
            "Status": "Live",
            "Popular": false,
            "Score": "0:0",
            "Round": null,
            "Location": null,
            "ThumbURL": null,
            "embed_url": "/iptv/live-player?id=556677"
          },
          {
            "id": "998877",
            "Match": "No page vs Nowhere",
            "Sport": "Football",
            "Status": "NS",
            "embed_url": ""
          },
          {
            "id": "112233",
            "Match": "   ",
            "Sport": "Football",
            "Status": "NS",
            "embed_url": "/iptv/live-player?id=112233"
          }
        ]
      }
    """
  }
}
