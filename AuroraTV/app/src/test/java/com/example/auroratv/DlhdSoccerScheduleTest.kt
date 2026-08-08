package com.example.auroratv

import com.example.auroratv.ui.dlhd.DLHD_CATEGORY_ALL
import com.example.auroratv.ui.dlhd.DLHD_CATEGORY_CLUB_FRIENDLY
import com.example.auroratv.ui.dlhd.DLHD_CATEGORY_SOCCER
import com.example.auroratv.ui.dlhd.DlhdSoccerEvent
import com.example.auroratv.ui.dlhd.dlhdCategoryFilters
import com.example.auroratv.ui.dlhd.eventsForDlhdCategory
import com.example.auroratv.ui.dlhd.extractDlhdCategoryBody
import com.example.auroratv.ui.dlhd.isSoccerRelatedEvent
import com.example.auroratv.ui.dlhd.mergeDlhdSchedules
import com.example.auroratv.ui.dlhd.parseDlhdScheduleApiHtml
import com.example.auroratv.ui.dlhd.parseDlhdSoccerSchedule
import com.example.auroratv.ui.dlhd.parseScheduleDayStartUk
import com.example.auroratv.ui.dlhd.searchDlhdEvents
import com.example.auroratv.ui.dlhd.splitLeagueAndMatch
import com.example.auroratv.ui.dlhd.stripEmojiNoise
import com.example.auroratv.ui.dlhd.toPlayback
import com.example.auroratv.ui.dlhd.ukKickOffMs
import com.example.auroratv.ui.dlhd.withScheduleDayContext
import com.example.auroratv.ui.dlhd.DlhdSoccerChannel
import java.util.TimeZone
import org.junit.Assert.assertEquals
import org.junit.Assert.assertFalse
import org.junit.Assert.assertTrue
import org.junit.Test

/** DaddyLive soccer schedule parsing and listing helpers. */
class DlhdSoccerScheduleTest {
  @Test
  fun parser_readsFixturesAndChannelPages() {
    val events =
      parseDlhdSoccerSchedule(
        SAMPLE_HTML,
        TimeZone.getTimeZone("GMT"),
        NOW_MS,
        category = DLHD_CATEGORY_SOCCER,
      )

    assertEquals(2, events.size)
    val chelsea = events.first()
    assertEquals("Chelsea vs Auckland FC", chelsea.match)
    assertEquals("Pre Season Friendly (Women) 2026", chelsea.league)
    assertEquals(DLHD_CATEGORY_SOCCER, chelsea.category)
    assertEquals("04:00", chelsea.ukTime)
    assertEquals("https://dlhd.st/watch.php?id=712", chelsea.watchUrl)
    assertEquals(listOf("beIN Sports Malaysia"), chelsea.channels.map { it.name })
  }

  @Test
  fun parser_keepsMultipleChannelsOnOneFixture() {
    val portugal = parseDlhdSoccerSchedule(SAMPLE_HTML, TimeZone.getTimeZone("GMT"), NOW_MS).last()

    assertEquals("Chaves vs Académica", portugal.match)
    assertEquals(
      listOf("Sport TV1 Portugal", "Sport TV1 Portugal Backup", "Backup Stream"),
      portugal.channels.map { it.name },
    )
    assertEquals("https://dlhd.st/watch.php?id=49", portugal.watchUrl)
  }

  @Test
  fun parser_skipsEventsWithNoChannelToOpen() {
    assertTrue(parseDlhdSoccerSchedule(NO_CHANNEL_HTML, TimeZone.getTimeZone("GMT"), NOW_MS).isEmpty())
  }

  @Test
  fun title_splitsLeagueFromMatchAndStripsFlags() {
    assertEquals(
      "Romania Liga II" to "Concordia Chiajna vs FC Bihor Oradea",
      splitLeagueAndMatch("⚽ 🇷🇴 Romania Liga II : Concordia Chiajna 🇷🇴 vs FC Bihor Oradea 🇷🇴"),
    )
    assertEquals(
      "Germany Bundesliga 2" to "Multiview",
      splitLeagueAndMatch("⚽ 🇩🇪 Germany Bundesliga 2 : Multiview"),
    )
    assertEquals("ball gone", stripEmojiNoise("⚽ ball gone"))
  }

  @Test
  fun kickOff_isReadAsUkGmt() {
    val dayStart = parseScheduleDayStartUk(SAMPLE_HTML, NOW_MS)!!
    assertEquals(dayStart + 4L * 60L * 60L * 1000L, ukKickOffMs(dayStart, "04:00"))
    assertEquals(
      "09:00",
      parseDlhdSoccerSchedule(SAMPLE_HTML, TimeZone.getTimeZone("GMT+5"), NOW_MS).first().kickOffLabel,
    )
  }

  @Test
  fun playback_opensTheChosenChannelPage() {
    val event = parseDlhdSoccerSchedule(SAMPLE_HTML, TimeZone.getTimeZone("GMT"), NOW_MS).last()
    val backup = event.channels.last()
    val playback = event.toPlayback(backup)

    assertEquals("https://dlhd.st/watch.php?id=4118", playback.pageUrl)
    assertEquals("Chaves vs Académica", playback.title)
    assertTrue(playback.subtitle!!.contains("Backup Stream"))
    assertEquals("Soccer", playback.genres.single())
  }

  @Test
  fun search_matchesTeamLeagueOrChannel() {
    val events = parseDlhdSoccerSchedule(SAMPLE_HTML, TimeZone.getTimeZone("GMT"), NOW_MS)

    assertEquals(listOf("Chelsea vs Auckland FC"), searchDlhdEvents(events, "chelsea").map { it.match })
    assertEquals(listOf("Chaves vs Académica"), searchDlhdEvents(events, "segunda").map { it.match })
    assertEquals(listOf("Chelsea vs Auckland FC"), searchDlhdEvents(events, "bein").map { it.match })
  }

  @Test
  fun merge_keepsClubFriendliesAndDedupesOverlaps() {
    val soccer =
      parseDlhdSoccerSchedule(
        SAMPLE_HTML,
        TimeZone.getTimeZone("GMT"),
        NOW_MS,
        category = DLHD_CATEGORY_SOCCER,
      )
    val friendlies =
      parseDlhdSoccerSchedule(
        CLUB_FRIENDLY_HTML,
        TimeZone.getTimeZone("GMT"),
        NOW_MS,
        category = DLHD_CATEGORY_CLUB_FRIENDLY,
      )
    val merged = mergeDlhdSchedules(soccer, friendlies)

    assertEquals(3, merged.size)
    assertEquals(
      listOf("Chelsea vs Auckland FC", "Chelsea vs Milan", "Chaves vs Académica"),
      merged.map { it.match },
    )
    assertEquals(DLHD_CATEGORY_CLUB_FRIENDLY, merged.first { it.match == "Chelsea vs Milan" }.category)
    // Same id already present in All Soccer wins over a Club Friendly repeat.
    assertEquals(
      DLHD_CATEGORY_SOCCER,
      merged.first { it.match == "Chelsea vs Auckland FC" }.category,
    )
  }

  @Test
  fun filters_offerAllSoccerAndClubFriendly() {
    val soccer =
      parseDlhdSoccerSchedule(SAMPLE_HTML, TimeZone.getTimeZone("GMT"), NOW_MS, DLHD_CATEGORY_SOCCER)
    val friendlies =
      parseDlhdSoccerSchedule(
        CLUB_FRIENDLY_HTML,
        TimeZone.getTimeZone("GMT"),
        NOW_MS,
        DLHD_CATEGORY_CLUB_FRIENDLY,
      )
    val merged = mergeDlhdSchedules(soccer, friendlies)

    assertEquals(
      listOf(DLHD_CATEGORY_ALL, DLHD_CATEGORY_SOCCER, DLHD_CATEGORY_CLUB_FRIENDLY),
      dlhdCategoryFilters(merged),
    )
    assertEquals(
      listOf("Chelsea vs Milan"),
      eventsForDlhdCategory(merged, DLHD_CATEGORY_CLUB_FRIENDLY).map { it.match },
    )
  }

  @Test
  fun homepageCategories_areExtractedWhenCatPagesAreEmpty() {
    val soccerBody = extractDlhdCategoryBody(HOMEPAGE_HTML, "All Soccer")!!
    val friendlyBody = extractDlhdCategoryBody(HOMEPAGE_HTML, "Club Friendly")!!
    val soccer =
      parseDlhdSoccerSchedule(
        withScheduleDayContext(HOMEPAGE_HTML, soccerBody),
        TimeZone.getTimeZone("GMT"),
        NOW_MS,
        DLHD_CATEGORY_SOCCER,
      )
    val friendlies =
      parseDlhdSoccerSchedule(
        withScheduleDayContext(HOMEPAGE_HTML, friendlyBody),
        TimeZone.getTimeZone("GMT"),
        NOW_MS,
        DLHD_CATEGORY_CLUB_FRIENDLY,
      )

    assertEquals(listOf("Chelsea vs Auckland FC"), soccer.map { it.match })
    assertEquals(listOf("Chelsea vs Milan"), friendlies.map { it.match })
  }

  @Test
  fun scheduleApi_htmlPayloadIsUnwrapped() {
    val html =
      parseDlhdScheduleApiHtml(
        """{"success":true,"html":"<div class=\"schedule__eventHeader\">ok</div>"}""",
      )
    assertTrue(html.contains("schedule__eventHeader"))
    assertEquals("", parseDlhdScheduleApiHtml("""{"success":false,"error":"nope"}"""))
  }

  @Test
  fun extraBoard_keepsFootballAndDropsOtherSports() {
    assertTrue(
      isSoccerRelatedEvent(
        DlhdSoccerEvent(
          id = "1",
          match = "United vs PSG",
          league = "Football",
          category = DLHD_CATEGORY_SOCCER,
          channels = listOf(DlhdSoccerChannel("1", "Sky", "https://dlhd.st/watch.php?id=1")),
          ukTime = "15:00",
          startAtMs = null,
          kickOffLabel = null,
        ),
      ),
    )
    assertFalse(
      isSoccerRelatedEvent(
        DlhdSoccerEvent(
          id = "2",
          match = "Sharks vs Boland",
          league = "Rugby",
          category = DLHD_CATEGORY_SOCCER,
          channels = listOf(DlhdSoccerChannel("2", "Sky", "https://dlhd.st/watch.php?id=2")),
          ukTime = "15:00",
          startAtMs = null,
          kickOffLabel = null,
        ),
      ),
    )
  }

  private companion object {
    // Midday on the schedule day so the day-start guard accepts it.
    const val NOW_MS = 1_786_190_400_000L // 2026-08-08 12:00 GMT

    val SAMPLE_HTML =
      """
      <div class="schedule__dayTitle">Saturday 08th Aug 2026 - Schedule Time UK GMT</div>
      <div class="schedule__event">
        <div class="schedule__eventHeader" data-title="chelsea">
          <span class="schedule__time" data-time="04:00">09:00</span>
          <span class="schedule__eventTitle">⚽ 🇬🇧 Pre Season Friendly (Women) 2026 : Chelsea 🇬🇧 vs Auckland FC 🇳🇿</span>
        </div>
        <div class="schedule__channels" style="display: none;">
          <a target="_blank" href="/watch.php?id=712" title="beIN Sports Malaysia">beIN Sports Malaysia</a>
        </div>
      </div>
      <div class="schedule__event">
        <div class="schedule__eventHeader" data-title="chaves">
          <span class="schedule__time" data-time="10:00">15:00</span>
          <span class="schedule__eventTitle">⚽ 🇵🇹 Portugal Segunda Liga : Chaves 🇵🇹 vs Académica 🇵🇹</span>
        </div>
        <div class="schedule__channels" style="display: none;">
          <a target="_blank" href="/watch.php?id=49" title="Sport TV1 Portugal">Sport TV1 Portugal</a>
          <a target="_blank" href="/watch.php?id=3021" title="Sport TV1 Portugal Backup">Sport TV1 Portugal Backup</a>
          <a target="_blank" href="/watch.php?id=4118" title="Backup Stream">Backup Stream</a>
        </div>
      </div>
      """
        .trimIndent()

    val CLUB_FRIENDLY_HTML =
      """
      <div class="schedule__dayTitle">Saturday 08th Aug 2026 - Schedule Time UK GMT</div>
      <div class="schedule__event">
        <div class="schedule__eventHeader" data-title="milan">
          <span class="schedule__time" data-time="06:00">11:00</span>
          <span class="schedule__eventTitle">⚽ 🏴 Club Friendly : Chelsea 🏴 vs Milan 🇮🇹</span>
        </div>
        <div class="schedule__channels" style="display: none;">
          <a target="_blank" href="/watch.php?id=801" title="Sky Sports Main Event">Sky Sports Main Event</a>
        </div>
      </div>
      <div class="schedule__event">
        <div class="schedule__eventHeader" data-title="auckland-repeat">
          <span class="schedule__time" data-time="04:00">09:00</span>
          <span class="schedule__eventTitle">⚽ 🇬🇧 Pre Season Friendly (Women) 2026 : Chelsea 🇬🇧 vs Auckland FC 🇳🇿</span>
        </div>
        <div class="schedule__channels" style="display: none;">
          <a target="_blank" href="/watch.php?id=712" title="beIN Sports Malaysia">beIN Sports Malaysia</a>
        </div>
      </div>
      """
        .trimIndent()

    val NO_CHANNEL_HTML =
      """
      <div class="schedule__dayTitle">Saturday 08th Aug 2026 - Schedule Time UK GMT</div>
      <div class="schedule__event">
        <div class="schedule__eventHeader">
          <span class="schedule__time" data-time="12:00">17:00</span>
          <span class="schedule__eventTitle">⚽ Ghost Match : Nowhere vs Nobody</span>
        </div>
        <div class="schedule__channels" style="display: none;"></div>
      </div>
      """
        .trimIndent()

    val HOMEPAGE_HTML =
      """
      <div class="schedule__dayTitle">Saturday 08th Aug 2026 - Schedule Time UK GMT</div>
      <a href="/index.php?cat=All+Soccer+Events">All Soccer Events</a>
      <div class="schedule__category is-expanded">
        <div class="schedule__catHeader">
          <div class="card__meta">Tennis</div>
        </div>
        <div class="schedule__categoryBody">
          <div class="schedule__event">
            <div class="schedule__eventHeader">
              <span class="schedule__time" data-time="11:00">11:00</span>
              <span class="schedule__eventTitle">Tennis : Someone vs Else</span>
            </div>
            <div class="schedule__channels">
              <a href="/watch.php?id=9">Tennis TV</a>
            </div>
          </div>
        </div>
      </div>
      <div class="schedule__category is-expanded">
        <div class="schedule__catHeader">
          <div class="card__meta">All Soccer Events ⚽</div>
        </div>
        <div class="schedule__categoryBody">
          <div class="schedule__event">
            <div class="schedule__eventHeader">
              <span class="schedule__time" data-time="04:00">04:00</span>
              <span class="schedule__eventTitle">⚽ Pre Season Friendly (Women) 2026 : Chelsea vs Auckland FC</span>
            </div>
            <div class="schedule__channels">
              <a href="/watch.php?id=712">beIN Sports Malaysia</a>
            </div>
          </div>
        </div>
      </div>
      <div class="schedule__category is-expanded">
        <div class="schedule__catHeader">
          <div class="card__meta">Club Friendly ⚽</div>
        </div>
        <div class="schedule__categoryBody">
          <div class="schedule__event">
            <div class="schedule__eventHeader">
              <span class="schedule__time" data-time="06:00">06:00</span>
              <span class="schedule__eventTitle">⚽ Club Friendly : Chelsea vs Milan</span>
            </div>
            <div class="schedule__channels">
              <a href="/watch.php?id=801">Sky Sports Main Event</a>
            </div>
          </div>
        </div>
      </div>
      """
        .trimIndent()
  }
}
