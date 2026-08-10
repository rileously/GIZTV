package com.giztv.tv.ui.sports

import com.giztv.tv.BuildConfig
import com.giztv.tv.data.PlaybackContext
import java.io.IOException
import java.net.HttpURLConnection
import java.net.SocketTimeoutException
import java.net.URL
import java.net.UnknownHostException
import java.text.SimpleDateFormat
import java.util.Date
import java.util.Locale
import java.util.TimeZone
import java.util.concurrent.TimeUnit
import javax.net.ssl.SSLException
import kotlinx.coroutines.Dispatchers
import kotlinx.coroutines.sync.Mutex
import kotlinx.coroutines.sync.withLock
import kotlinx.coroutines.withContext
import org.json.JSONObject

/**
 * One fixture in the hoofoot listing.
 *
 * [watchUrl] is the site's own player page, already absolute, so it goes to the browser exactly as
 * every other title does and the stream is discovered the same way.
 */
internal data class SportEvent(
  val id: String,
  val match: String,
  val league: String,
  val sport: String,
  val status: String,
  val popular: Boolean,
  val ranking: Int,
  val score: String?,
  val round: String?,
  val location: String?,
  val homeBadgeUrl: String?,
  val awayBadgeUrl: String?,
  val thumbUrl: String?,
  val startAtMs: Long?,
  /** Kick-off in the device's own timezone; the feed itself is UTC throughout. */
  val kickOffLabel: String?,
  val watchUrl: String,
) {
  val live: Boolean
    get() = status.equals("Live", ignoreCase = true)

  val ended: Boolean
    get() = status.equals("Ended", ignoreCase = true)

  val homeTeam: String
    get() = match.substringBefore(TEAM_SEPARATOR, match).trim()

  val awayTeam: String
    get() = match.substringAfter(TEAM_SEPARATOR, "").trim()

  /**
   * Whether this is two sides against each other at all.
   *
   * Most of the feed is, but a tournament session or a race is listed under one name, and lining
   * that up against an empty second row would invent an opponent that does not exist.
   */
  val isPairing: Boolean
    get() = awayTeam.isNotBlank()

  /**
   * The pill in the corner of the card.
   *
   * The score is not repeated here: it sits against the teams, where a scoreboard puts it, so this
   * only has to say whether the match is on now, over, or still to come.
   */
  val statusLabel: String
    get() =
      when {
        live -> "LIVE"
        ended -> "FT"
        else -> kickOffLabel ?: "Scheduled"
      }

  /** The two halves of a "2:1", or nulls until the feed states a score at all. */
  val homeScore: String?
    get() = scorePart(0)

  val awayScore: String?
    get() = scorePart(1)

  private fun scorePart(index: Int): String? =
    score?.split(':')?.getOrNull(index)?.trim()?.takeIf(String::isNotBlank)

  /** The competition, and the stage of it when the sport has stages at all. */
  val subtitle: String
    get() = listOfNotNull(league.takeIf(String::isNotBlank), round?.takeIf(String::isNotBlank)).joinToString(" · ")
}

/**
 * What the player is told about a fixture.
 *
 * The page is the site's own player, so it goes to the browser exactly as a film does; the match
 * and competition ride along so the player has something to name while it loads. The badge is told
 * outright what this is, because the loading page would otherwise call a live match a movie.
 */
internal fun SportEvent.toPlayback(): PlaybackContext =
  PlaybackContext(
    pageUrl = watchUrl,
    title = match,
    subtitle = listOfNotNull(subtitle.takeIf(String::isNotBlank), statusLabel).joinToString(" · "),
    posterUrl = thumbUrl ?: homeBadgeUrl,
    overview = location?.takeIf(String::isNotBlank),
    genres = listOf(sport),
    kindLabel = statusLabel,
  )

/** Football leads the page, so it is named once and compared against everywhere. */
internal const val FOOTBALL = "Football"

/** The last chip: every other sport the feed is carrying, football still first within it. */
internal const val ALL_SPORTS = "All sports"

private const val TEAM_SEPARATOR = " vs "

/** What the feed prints where it has no competition to name. */
private const val UNKNOWN_LEAGUE = "Unknown League"

/**
 * The hoofoot listing.
 *
 * The whole schedule arrives in one payload with nothing to ask it — no query parameters, no key —
 * so there is exactly one request to make and the work is all in ordering what comes back. Scores
 * move while a viewer is on the page, so the answer is held for [FEED_TTL_MS] rather than the ten
 * minutes a catalogue would get.
 */
internal object SportsRepository {
  private val feed = SportsFeedCache(FEED_TTL_MS)

  suspend fun events(): List<SportEvent> = feed.get { requestEvents() }

  /** Drops the stored answer so the next call goes back to the feed for fresh scores. */
  suspend fun refresh(): List<SportEvent> {
    feed.clear()
    return events()
  }
}

/** Live scores age badly; a schedule does not, so this is the shorter of the two lifetimes. */
private const val FEED_TTL_MS = 45_000L

private const val HOOFOOT_ORIGIN = "https://hoofoot.ru"
private const val EVENTS_URL = "$HOOFOOT_ORIGIN/api/embed/events"

private suspend fun requestEvents(): List<SportEvent> =
  withContext(Dispatchers.IO) {
    val connection = (URL(EVENTS_URL).openConnection() as HttpURLConnection)
    try {
      connection.requestMethod = "GET"
      connection.setRequestProperty("Accept", "application/json")
      connection.setRequestProperty("Referer", "$HOOFOOT_ORIGIN/")
      connection.setRequestProperty("User-Agent", sportsUserAgent())
      connection.connectTimeout = 12_000
      connection.readTimeout = 15_000
      val status = connection.responseCode
      if (status == HttpURLConnection.HTTP_UNAVAILABLE || status == 429) {
        throw IOException("Live sports are busy right now. Try again in a moment.")
      }
      if (status !in 200..299) throw IOException("Live sports returned HTTP $status")
      parseSportEvents(connection.inputStream.bufferedReader().use { it.readText() })
    } finally {
      connection.disconnect()
    }
  }

internal fun sportsUserAgent(): String =
  "Mozilla/5.0 (Linux; Android 10) AppleWebKit/537.36 (KHTML, like Gecko) " +
    "Chrome/131.0.0.0 Mobile Safari/537.36 GIZTV/${BuildConfig.VERSION_NAME}"

/**
 * Reads the feed.
 *
 * [zone] is the device's own, and only decides how kick-off is worded — the feed states every time
 * in UTC, so the moment itself is the same wherever it is read.
 */
internal fun parseSportEvents(json: String, zone: TimeZone = TimeZone.getDefault()): List<SportEvent> {
  val payload = runCatching { JSONObject(json) }.getOrNull() ?: return emptyList()
  val events = payload.optJSONArray("events") ?: return emptyList()
  // The feed names its own host, so a move would not strand the player pages.
  val embedBase = payload.optString("embed_base_url").trim().trimEnd('/').ifBlank { HOOFOOT_ORIGIN }
  val clock = SimpleDateFormat("HH:mm", Locale.getDefault()).apply { timeZone = zone }
  val day = SimpleDateFormat("d MMM", Locale.getDefault()).apply { timeZone = zone }
  val todayStamp = SimpleDateFormat("yyyy-MM-dd", Locale.US).apply { timeZone = zone }
  val today = todayStamp.format(Date())

  return buildList {
    for (index in 0 until events.length()) {
      val event = events.optJSONObject(index) ?: continue
      val id = event.optString("id").trim()
      val match = event.optString("Match").trim()
      val embedPath = event.optString("embed_url").trim()
      // Without a match to name or a page to open there is nothing to put on a card.
      if (id.isBlank() || match.isBlank() || embedPath.isBlank()) continue
      val startAtMs = eventStartMs(event.optStringOrNull("Date"), event.optStringOrNull("Time"))
      add(
        SportEvent(
          id = id,
          match = match,
          // The feed says "Unknown League" where it has nothing; printing that verbatim would put
          // an admission of ignorance on the card, so it is treated as nothing said.
          league = event.optString("League").trim().takeUnless { it.equals(UNKNOWN_LEAGUE, ignoreCase = true) }.orEmpty(),
          sport = event.optString("Sport").trim().ifBlank { "Other" },
          status = event.optString("Status").trim(),
          popular = event.optBoolean("Popular"),
          // Only the highlighted fixtures are ranked at all; the rest sort behind them.
          ranking = event.optInt("Ranking", Int.MAX_VALUE).takeIf { it > 0 } ?: Int.MAX_VALUE,
          score = event.optStringOrNull("Score"),
          round = event.optStringOrNull("Round"),
          location = event.optStringOrNull("Location"),
          homeBadgeUrl = event.optStringOrNull("HomeTeamImage"),
          awayBadgeUrl = event.optStringOrNull("AwayTeamImage"),
          thumbUrl = event.optStringOrNull("ThumbURL"),
          startAtMs = startAtMs,
          kickOffLabel =
            startAtMs?.let { moment ->
              val time = clock.format(Date(moment))
              // A day is only worth naming when it is not this one.
              if (todayStamp.format(Date(moment)) == today) time else "${day.format(Date(moment))} · $time"
            },
          watchUrl = embedBase + embedPath,
        )
      )
    }
  }
}

/**
 * Turns the feed's date and time into a moment.
 *
 * Both are stated in UTC, so they are read that way and never in the device's own zone — an hour's
 * drift would put a kick-off on the wrong side of "now" and sort the page wrongly.
 */
internal fun eventStartMs(date: String?, time: String?): Long? {
  if (date.isNullOrBlank() || time.isNullOrBlank()) return null
  val format =
    SimpleDateFormat("yyyy-MM-dd HH:mm", Locale.US).apply {
      timeZone = TimeZone.getTimeZone("UTC")
      isLenient = false
    }
  return runCatching { format.parse("${date.trim()} ${time.trim()}")?.time }.getOrNull()
}

/**
 * The order the page is read in: football first, then whatever is actually playing.
 *
 * Within a sport a match in play outranks one about to start, and a finished one sinks to the
 * bottom — it is still worth listing, because a viewer who missed it may want the replay, but it is
 * never what the page opens on.
 */
internal fun sortedForViewing(events: List<SportEvent>): List<SportEvent> =
  events.sortedWith(
    compareBy(
      { if (it.sport.equals(FOOTBALL, ignoreCase = true)) 0 else 1 },
      { statusRank(it) },
      { if (it.popular) 0 else 1 },
      { it.ranking },
      // A fixture with no stated time waits behind every fixture that has one.
      { it.startAtMs ?: Long.MAX_VALUE },
      { it.match },
    )
  )

private fun statusRank(event: SportEvent): Int =
  when {
    event.live -> 0
    event.ended -> 2
    else -> 1
  }

/**
 * The chip row: football, then the other sports the feed is carrying, then everything at once.
 *
 * Sports are named by the feed rather than fixed here, so a night with cricket on it says cricket
 * and a night without it does not offer an empty chip. The busiest sports come first, because a
 * chip leading to two fixtures is worth less than one leading to ten.
 */
internal fun sportFilters(events: List<SportEvent>): List<String> {
  if (events.isEmpty()) return emptyList()
  val bySize =
    events
      .filterNot { it.sport.equals(FOOTBALL, ignoreCase = true) }
      .groupingBy { it.sport }
      .eachCount()
      .entries
      .sortedWith(compareByDescending<Map.Entry<String, Int>> { it.value }.thenBy { it.key })
      .map { it.key }
  val football = events.filter { it.sport.equals(FOOTBALL, ignoreCase = true) }
  return buildList {
    if (football.isNotEmpty()) add(FOOTBALL)
    addAll(bySize)
    if (size > 1) add(ALL_SPORTS)
  }
}

/** Everything in [sport], or everything at all when the last chip is the selected one. */
internal fun eventsForSport(events: List<SportEvent>, sport: String?): List<SportEvent> =
  when (sport) {
    null, ALL_SPORTS -> sortedForViewing(events)
    else -> sortedForViewing(events.filter { it.sport.equals(sport, ignoreCase = true) })
  }

/**
 * Searches the whole schedule rather than the chosen sport.
 *
 * A viewer typing a team name is asking where that team is playing, not whether it is playing in
 * the sport currently on screen.
 */
internal fun searchEvents(events: List<SportEvent>, query: String): List<SportEvent> {
  val needle = query.trim()
  if (needle.isBlank()) return sortedForViewing(events)
  return sortedForViewing(
    events.filter {
      it.match.contains(needle, ignoreCase = true) ||
        it.league.contains(needle, ignoreCase = true) ||
        it.sport.contains(needle, ignoreCase = true)
    }
  )
}

internal fun friendlySportsError(error: Throwable): String =
  when (error) {
    is SSLException ->
      "Secure connection to the sports listing failed. Check this device's date and time, then try again."
    is UnknownHostException -> "No internet connection. Check the network and try again."
    is SocketTimeoutException -> "The sports listing took too long to respond. Try again."
    else -> error.message ?: "Live sports could not be loaded"
  }

/** `optString` returns the literal "null" for a JSON null, which is never a value worth keeping. */
private fun JSONObject.optStringOrNull(name: String): String? =
  optString(name).trim().takeIf { it.isNotBlank() && it != "null" }

/**
 * Holds the one answer this feed has for [ttlMs], and lets one caller at a time go and get it.
 *
 * There is a single payload with nothing to key on, so this is deliberately simpler than the
 * catalogue's cache: a second caller arriving mid-fetch waits on the same lock and is served what
 * the first one brought back, and a failure is never stored.
 */
internal class SportsFeedCache(
  private val ttlMs: Long,
  private val now: () -> Long = System::currentTimeMillis,
) {
  private val guard = Mutex()
  private var storedAtMs = 0L
  private var events: List<SportEvent>? = null

  suspend fun get(load: suspend () -> List<SportEvent>): List<SportEvent> {
    fresh()?.let { return it }
    return guard.withLock {
      // Whoever held the lock may have just filled the cache for us.
      freshLocked() ?: load().also { loaded ->
        events = loaded
        storedAtMs = now()
      }
    }
  }

  suspend fun clear() {
    guard.withLock {
      events = null
      storedAtMs = 0L
    }
  }

  private suspend fun fresh(): List<SportEvent>? = guard.withLock { freshLocked() }

  private fun freshLocked(): List<SportEvent>? =
    events?.takeIf { storedAtMs != 0L && now() - storedAtMs < ttlMs }
}

/** How long the page waits before asking the feed for fresh scores while a viewer is on it. */
internal val LIVE_REFRESH_INTERVAL_MS = TimeUnit.SECONDS.toMillis(45)
