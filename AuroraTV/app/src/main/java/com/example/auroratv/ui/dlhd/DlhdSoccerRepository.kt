package com.example.auroratv.ui.dlhd

import com.example.auroratv.BuildConfig
import com.example.auroratv.data.PlaybackContext
import java.io.IOException
import java.net.HttpURLConnection
import java.net.SocketTimeoutException
import java.net.URL
import java.net.UnknownHostException
import java.text.SimpleDateFormat
import java.util.Calendar
import java.util.Date
import java.util.Locale
import java.util.TimeZone
import java.util.concurrent.TimeUnit
import java.util.regex.Pattern
import javax.net.ssl.SSLException
import kotlinx.coroutines.Dispatchers
import kotlinx.coroutines.async
import kotlinx.coroutines.sync.Mutex
import kotlinx.coroutines.sync.withLock
import kotlinx.coroutines.withContext
import org.json.JSONObject

/**
 * One soccer fixture from DaddyLive's schedule, with the channel pages that carry it.
 *
 * [watchUrl] is the first channel's player page — enough for the browser/player path every other
 * title uses. Extra channels stay on the card so a viewer can pick a backup without leaving.
 */
internal data class DlhdSoccerEvent(
  val id: String,
  val match: String,
  val league: String,
  val category: String,
  val channels: List<DlhdSoccerChannel>,
  val ukTime: String,
  val startAtMs: Long?,
  val kickOffLabel: String?,
) {
  val watchUrl: String
    get() = channels.first().watchUrl

  val primaryChannel: String
    get() = channels.first().name

  val channelLabel: String
    get() =
      when (channels.size) {
        1 -> primaryChannel
        else -> "$primaryChannel · +${channels.size - 1}"
      }
}

internal data class DlhdSoccerChannel(
  val id: String,
  val name: String,
  val watchUrl: String,
)

internal fun DlhdSoccerEvent.toPlayback(channel: DlhdSoccerChannel = channels.first()): PlaybackContext =
  PlaybackContext(
    pageUrl = channel.watchUrl,
    title = match,
    subtitle =
      listOfNotNull(
          league.takeIf(String::isNotBlank),
          channel.name,
          kickOffLabel,
        )
        .joinToString(" · "),
    genres = listOf("Soccer"),
    kindLabel = kickOffLabel ?: "Soccer",
  )

internal const val DLHD_ORIGIN = "https://dlhd.st"
internal const val DLHD_CATEGORY_ALL = "All"
internal const val DLHD_CATEGORY_SOCCER = "All Soccer"
internal const val DLHD_CATEGORY_CLUB_FRIENDLY = "Club Friendly"

/** Homepage still embeds today's schedule; filtered `?cat=` pages are often empty shells. */
private const val DLHD_HOME_SCHEDULE_URL = "$DLHD_ORIGIN/"
/** Secondary football board DaddyLive loads through JS (`schedule-api.php`). */
private const val DLHD_EXTRA_SCHEDULE_API = "$DLHD_ORIGIN/schedule-api.php?source=extra"

/** How long a fetched schedule stays good before the page asks again. */
private const val FEED_TTL_MS = 60_000L

internal val DLHD_REFRESH_INTERVAL_MS = TimeUnit.SECONDS.toMillis(60)

internal object DlhdSoccerRepository {
  private val feed = DlhdSoccerFeedCache(FEED_TTL_MS)

  suspend fun events(): List<DlhdSoccerEvent> = feed.get { requestEvents() }

  suspend fun refresh(): List<DlhdSoccerEvent> {
    feed.clear()
    return events()
  }
}

private suspend fun requestEvents(): List<DlhdSoccerEvent> =
  withContext(Dispatchers.IO) {
    // Category URLs like `?cat=All+Soccer+Events` no longer ship fixtures inline — the browser fills
    // Extra boards over JSON, and the homepage still has the main All Soccer / Club Friendly blocks.
    val homeHtml = async { fetchScheduleHtml(DLHD_HOME_SCHEDULE_URL) }
    val extraHtml =
      async {
        runCatching { fetchScheduleApiHtml(DLHD_EXTRA_SCHEDULE_API) }.getOrDefault("")
      }
    val home = homeHtml.await()
    val soccer =
      extractDlhdCategoryBody(home, "All Soccer")?.let { body ->
        parseDlhdSoccerSchedule(
          withScheduleDayContext(home, body),
          category = DLHD_CATEGORY_SOCCER,
        )
      }
        .orEmpty()
    val friendlies =
      extractDlhdCategoryBody(home, "Club Friendly")?.let { body ->
        parseDlhdSoccerSchedule(
          withScheduleDayContext(home, body),
          category = DLHD_CATEGORY_CLUB_FRIENDLY,
        )
      }
        .orEmpty()
    val extra =
      parseDlhdSoccerSchedule(
          withScheduleDayContext(home, extraHtml.await()),
          category = DLHD_CATEGORY_SOCCER,
        )
        .map(::retagFriendlyExtraEvent)
        .filter(::isSoccerRelatedEvent)
    val merged = mergeDlhdSchedules(soccer, friendlies, extra)
    if (merged.isEmpty()) {
      throw IOException("Soccer schedule came back empty. Try again in a moment.")
    }
    merged
  }

private fun fetchScheduleHtml(url: String): String {
  val connection = (URL(url).openConnection() as HttpURLConnection)
  try {
    connection.requestMethod = "GET"
    connection.setRequestProperty("Accept", "text/html,application/xhtml+xml")
    connection.setRequestProperty("Referer", "$DLHD_ORIGIN/")
    connection.setRequestProperty("User-Agent", dlhdUserAgent())
    connection.connectTimeout = 12_000
    connection.readTimeout = 20_000
    val status = connection.responseCode
    if (status == HttpURLConnection.HTTP_UNAVAILABLE || status == 429) {
      throw IOException("Soccer schedule is busy right now. Try again in a moment.")
    }
    if (status !in 200..299) throw IOException("Soccer schedule returned HTTP $status")
    return connection.inputStream.bufferedReader().use { it.readText() }
  } finally {
    connection.disconnect()
  }
}

private fun fetchScheduleApiHtml(url: String): String {
  val connection = (URL(url).openConnection() as HttpURLConnection)
  try {
    connection.requestMethod = "GET"
    connection.setRequestProperty("Accept", "application/json")
    connection.setRequestProperty("Referer", "$DLHD_ORIGIN/")
    connection.setRequestProperty("User-Agent", dlhdUserAgent())
    connection.connectTimeout = 12_000
    connection.readTimeout = 20_000
    val status = connection.responseCode
    if (status !in 200..299) throw IOException("Soccer schedule API returned HTTP $status")
    return parseDlhdScheduleApiHtml(connection.inputStream.bufferedReader().use { it.readText() })
  } finally {
    connection.disconnect()
  }
}

internal fun dlhdUserAgent(): String =
  "Mozilla/5.0 (Linux; Android 10) AppleWebKit/537.36 (KHTML, like Gecko) " +
    "Chrome/131.0.0.0 Mobile Safari/537.36 GIZTV/${BuildConfig.VERSION_NAME}"

private val DAY_TITLE =
  Pattern.compile(
    """class="schedule__dayTitle"[^>]*>([^<]+)""",
    Pattern.CASE_INSENSITIVE,
  )
/** One fixture: its header (time + title) and the channel list that follows. */
private val EVENT_BLOCK =
  Pattern.compile(
    """class="schedule__eventHeader"[^>]*>([\s\S]*?)</div>\s*<div class="schedule__channels"[^>]*>([\s\S]*?)</div>""",
    Pattern.CASE_INSENSITIVE,
  )
private val EVENT_TIME =
  Pattern.compile(
    """data-time="(\d{1,2}:\d{2})"""",
    Pattern.CASE_INSENSITIVE,
  )
private val EVENT_TITLE =
  Pattern.compile(
    """class="schedule__eventTitle"[^>]*>([\s\S]*?)</span>""",
    Pattern.CASE_INSENSITIVE,
  )
private val CHANNEL_LINK =
  Pattern.compile(
    """href="(/watch\.php\?id=(\d+))"[^>]*>([^<]+)</a>""",
    Pattern.CASE_INSENSITIVE,
  )

/**
 * Reads DaddyLive's soccer schedule HTML into fixtures the player can open.
 *
 * The page is markup, not JSON, so this is deliberately tolerant: a day title that cannot be
 * dated still yields events with clock times, and an event with no channel is dropped rather than
 * offered as something that cannot play.
 */
internal fun parseDlhdSoccerSchedule(
  html: String,
  zone: TimeZone = TimeZone.getDefault(),
  nowMs: Long = System.currentTimeMillis(),
  category: String = DLHD_CATEGORY_SOCCER,
): List<DlhdSoccerEvent> {
  val dayStartUk = parseScheduleDayStartUk(html, nowMs)
  val events = ArrayList<DlhdSoccerEvent>()
  val seen = HashSet<String>()
  val matcher = EVENT_BLOCK.matcher(html)
  while (matcher.find()) {
    val header = matcher.group(1) ?: continue
    val channelHtml = matcher.group(2) ?: continue
    val event = parseEventBlock(header, channelHtml, dayStartUk, zone, category) ?: continue
    // One card per fixture: later duplicates (backup schedule columns) are ignored.
    if (!seen.add(event.id)) continue
    events.add(event)
  }
  return sortDlhdEvents(events)
}

/**
 * Combines category pages without doubling a fixture that appears on more than one.
 *
 * First listing wins, so All Soccer keeps its card when Club Friendly repeats the same kick-off.
 */
internal fun mergeDlhdSchedules(vararg lists: List<DlhdSoccerEvent>): List<DlhdSoccerEvent> {
  val merged = LinkedHashMap<String, DlhdSoccerEvent>()
  for (list in lists) {
    for (event in list) {
      merged.putIfAbsent(event.id, event)
    }
  }
  return sortDlhdEvents(merged.values.toList())
}

internal fun dlhdCategoryFilters(events: List<DlhdSoccerEvent>): List<String> {
  if (events.isEmpty()) return emptyList()
  val filters = mutableListOf(DLHD_CATEGORY_ALL)
  if (events.any { it.category == DLHD_CATEGORY_SOCCER }) filters.add(DLHD_CATEGORY_SOCCER)
  if (events.any { it.category == DLHD_CATEGORY_CLUB_FRIENDLY }) {
    filters.add(DLHD_CATEGORY_CLUB_FRIENDLY)
  }
  return filters
}

internal fun eventsForDlhdCategory(
  events: List<DlhdSoccerEvent>,
  category: String?,
): List<DlhdSoccerEvent> =
  when (category) {
    null, DLHD_CATEGORY_ALL -> events
    else -> events.filter { it.category == category }
  }

/**
 * Pulls one schedule category body out of the homepage markup.
 *
 * DaddyLive groups fixtures under `card__meta` labels like "All Soccer Events" — the filtered
 * `?cat=` pages no longer contain those bodies, so the app reads them from `/` instead.
 */
internal fun extractDlhdCategoryBody(html: String, nameContains: String): String? {
  val needle = nameContains.trim()
  if (needle.isEmpty()) return null
  val meta =
    Pattern.compile(
      """class="card__meta">([\s\S]*?)</div>""",
      Pattern.CASE_INSENSITIVE,
    )
  val matcher = meta.matcher(html)
  while (matcher.find()) {
    val label =
      decodeHtml(stripTags(matcher.group(1).orEmpty()))
        .replace(Regex("\\s+"), " ")
        .trim()
    if (!label.contains(needle, ignoreCase = true)) continue
    val after = html.substring(matcher.end())
    val bodyOpen =
      Regex("""class="schedule__categoryBody"[^>]*>""", RegexOption.IGNORE_CASE).find(after)
        ?: continue
    // Nav / promo cards also use card__meta; the category body must sit right under the header.
    if (bodyOpen.range.first > 500) continue
    val rest = after.substring(bodyOpen.range.last + 1)
    val nextCategory =
      Regex("""class="schedule__category[\s"]""", RegexOption.IGNORE_CASE).find(rest)
    return rest.substring(0, nextCategory?.range?.first ?: rest.length)
  }
  return null
}

/** Keeps the UK day heading so kick-off parsing still works on a category fragment. */
internal fun withScheduleDayContext(pageHtml: String, fragmentHtml: String): String {
  val matcher = DAY_TITLE.matcher(pageHtml)
  if (!matcher.find()) return fragmentHtml
  val title = matcher.group(1)?.trim().orEmpty()
  if (title.isBlank()) return fragmentHtml
  return """<div class="schedule__dayTitle">$title</div>""" + fragmentHtml
}

/** Reads `{"success":true,"html":"..."}` payloads from DaddyLive's remote schedule boards. */
internal fun parseDlhdScheduleApiHtml(json: String): String {
  val payload = runCatching { JSONObject(json) }.getOrNull() ?: return ""
  if (!payload.optBoolean("success")) return ""
  return payload.optString("html").orEmpty()
}

private fun retagFriendlyExtraEvent(event: DlhdSoccerEvent): DlhdSoccerEvent {
  val blob = "${event.league} ${event.match}"
  return if (blob.contains("Club Friendly", ignoreCase = true) ||
      blob.contains("Friendly", ignoreCase = true)
  ) {
    event.copy(category = DLHD_CATEGORY_CLUB_FRIENDLY)
  } else {
    event
  }
}

/** Extra boards mix sports; keep football/soccer and drop rugby, boxing, IndyCar, CFL, etc. */
internal fun isSoccerRelatedEvent(event: DlhdSoccerEvent): Boolean {
  val blob = "${event.league} ${event.match}".lowercase(Locale.US)
  if (
    blob.contains("am. football") ||
      blob.contains("american football") ||
      blob.contains("canadian football") ||
      blob.contains(" rugby") ||
      blob.startsWith("rugby") ||
      blob.contains("boxing") ||
      blob.contains("basketball") ||
      blob.contains("wnba") ||
      blob.contains("motorsport") ||
      blob.contains("indycar") ||
      " cfl" in " $blob" ||
      blob.endsWith(" cfl") ||
      blob.contains(" - cfl")
  ) {
    return false
  }
  return blob.contains("football") ||
    blob.contains("soccer") ||
    blob.contains("friendly") ||
    blob.contains("mls") ||
    blob.contains("premier league") ||
    blob.contains("liga") ||
    blob.contains("efl") ||
    blob.contains("bundesliga") ||
    blob.contains("eredivisie") ||
    blob.contains("serie a") ||
    blob.contains("la liga") ||
    blob.contains("brasileir") ||
    blob.contains("championship") ||
    blob.contains("league cup") ||
    blob.contains("champions league") ||
    blob.contains("europa")
}

private fun sortDlhdEvents(events: List<DlhdSoccerEvent>): List<DlhdSoccerEvent> =
  events.sortedWith(
    compareBy<DlhdSoccerEvent> { it.startAtMs ?: Long.MAX_VALUE }.thenBy { it.match },
  )

private fun parseEventBlock(
  header: String,
  channelHtml: String,
  dayStartUk: Long?,
  zone: TimeZone,
  category: String,
): DlhdSoccerEvent? {
  val timeMatcher = EVENT_TIME.matcher(header)
  if (!timeMatcher.find()) return null
  val ukTime = timeMatcher.group(1)?.trim().orEmpty()
  if (ukTime.isBlank()) return null

  val titleMatcher = EVENT_TITLE.matcher(header)
  if (!titleMatcher.find()) return null
  val rawTitle = decodeHtml(stripTags(titleMatcher.group(1).orEmpty())).trim()
  if (rawTitle.isBlank()) return null

  val channels = parseChannels(channelHtml)
  if (channels.isEmpty()) return null

  val (league, match) = splitLeagueAndMatch(rawTitle)
  val startAtMs = dayStartUk?.let { ukKickOffMs(it, ukTime) }
  val id = channels.first().id + "|" + match.lowercase(Locale.US).take(48)
  return DlhdSoccerEvent(
    id = id,
    match = match,
    league = league,
    category = category,
    channels = channels,
    ukTime = ukTime,
    startAtMs = startAtMs,
    kickOffLabel = startAtMs?.let { formatKickOff(it, zone) } ?: ukTime,
  )
}

private fun parseChannels(block: String): List<DlhdSoccerChannel> {
  val channels = ArrayList<DlhdSoccerChannel>()
  val seen = HashSet<String>()
  val matcher = CHANNEL_LINK.matcher(block)
  while (matcher.find()) {
    val path = matcher.group(1)?.trim().orEmpty()
    val channelId = matcher.group(2)?.trim().orEmpty()
    val name = decodeHtml(matcher.group(3).orEmpty()).trim()
    if (path.isBlank() || channelId.isBlank() || name.isBlank()) continue
    if (!seen.add(channelId)) continue
    channels.add(
      DlhdSoccerChannel(
        id = channelId,
        name = name,
        watchUrl = DLHD_ORIGIN + path,
      ),
    )
  }
  return channels
}

/**
 * Splits "League : Home vs Away" titles. Events without a colon (Multiview, Simulcast) keep the
 * whole string as the match name.
 */
internal fun splitLeagueAndMatch(rawTitle: String): Pair<String, String> {
  val cleaned = stripEmojiNoise(rawTitle).replace(Regex("\\s+"), " ").trim()
  val parts = cleaned.split(" : ", limit = 2)
  return if (parts.size == 2) {
    parts[0].trim() to parts[1].trim().ifBlank { cleaned }
  } else {
    "" to cleaned
  }
}

/** Drops flag and ball emoji the schedule uses as decoration, keeping the readable names. */
internal fun stripEmojiNoise(value: String): String {
  val cleaned = StringBuilder(value.length)
  var index = 0
  while (index < value.length) {
    val codePoint = value.codePointAt(index)
    if (
      codePoint != 0x26BD &&
        codePoint != 0x1F3F4 &&
        codePoint !in 0x1F1E6..0x1F1FF
    ) {
      cleaned.appendCodePoint(codePoint)
    } else {
      cleaned.append(' ')
    }
    index += Character.charCount(codePoint)
  }
  return cleaned.toString().replace(Regex("\\s+"), " ").trim()
}

/**
 * The UK midnight of the schedule day, or null when the heading cannot be read.
 *
 * Kick-off times on the page are UK GMT (`data-time`), so the day has to be read the same way or
 * every fixture drifts by the device's offset.
 */
internal fun parseScheduleDayStartUk(html: String, nowMs: Long = System.currentTimeMillis()): Long? {
  val matcher = DAY_TITLE.matcher(html)
  if (!matcher.find()) return null
  val title = matcher.group(1)?.trim().orEmpty()
  // "Saturday 08th Aug 2026 - Schedule Time UK GMT"
  val dayMatch =
    Regex(
        """(\d{1,2})(?:st|nd|rd|th)?\s+([A-Za-z]{3})\s+(\d{4})""",
        RegexOption.IGNORE_CASE,
      )
      .find(title)
      ?: return null
  val day = dayMatch.groupValues[1].toIntOrNull() ?: return null
  val month = monthIndex(dayMatch.groupValues[2]) ?: return null
  val year = dayMatch.groupValues[3].toIntOrNull() ?: return null
  val calendar =
    Calendar.getInstance(TimeZone.getTimeZone("GMT")).apply {
      clear()
      set(Calendar.YEAR, year)
      set(Calendar.MONTH, month)
      set(Calendar.DAY_OF_MONTH, day)
    }
  // Guard against absurd parses from a mangled heading.
  val start = calendar.timeInMillis
  return start.takeIf { kotlin.math.abs(it - nowMs) < TimeUnit.DAYS.toMillis(14) }
}

internal fun ukKickOffMs(dayStartUk: Long, ukTime: String): Long? {
  val parts = ukTime.split(':')
  if (parts.size != 2) return null
  val hour = parts[0].toIntOrNull() ?: return null
  val minute = parts[1].toIntOrNull() ?: return null
  if (hour !in 0..23 || minute !in 0..59) return null
  return dayStartUk + TimeUnit.HOURS.toMillis(hour.toLong()) + TimeUnit.MINUTES.toMillis(minute.toLong())
}

private fun formatKickOff(momentMs: Long, zone: TimeZone): String {
  val clock = SimpleDateFormat("HH:mm", Locale.getDefault()).apply { timeZone = zone }
  val day = SimpleDateFormat("d MMM", Locale.getDefault()).apply { timeZone = zone }
  val todayStamp = SimpleDateFormat("yyyy-MM-dd", Locale.US).apply { timeZone = zone }
  val time = clock.format(Date(momentMs))
  return if (todayStamp.format(Date(momentMs)) == todayStamp.format(Date())) {
    time
  } else {
    "${day.format(Date(momentMs))} · $time"
  }
}

private fun monthIndex(token: String): Int? =
  when (token.lowercase(Locale.US).take(3)) {
    "jan" -> Calendar.JANUARY
    "feb" -> Calendar.FEBRUARY
    "mar" -> Calendar.MARCH
    "apr" -> Calendar.APRIL
    "may" -> Calendar.MAY
    "jun" -> Calendar.JUNE
    "jul" -> Calendar.JULY
    "aug" -> Calendar.AUGUST
    "sep" -> Calendar.SEPTEMBER
    "oct" -> Calendar.OCTOBER
    "nov" -> Calendar.NOVEMBER
    "dec" -> Calendar.DECEMBER
    else -> null
  }

private fun stripTags(value: String): String = value.replace(Regex("<[^>]+>"), " ")

private fun decodeHtml(value: String): String =
  value
    .replace("&amp;", "&")
    .replace("&lt;", "<")
    .replace("&gt;", ">")
    .replace("&quot;", "\"")
    .replace("&#39;", "'")
    .replace("&nbsp;", " ")

internal fun searchDlhdEvents(events: List<DlhdSoccerEvent>, query: String): List<DlhdSoccerEvent> {
  val needle = query.trim()
  if (needle.isBlank()) return events
  return events.filter {
    it.match.contains(needle, ignoreCase = true) ||
      it.league.contains(needle, ignoreCase = true) ||
      it.channels.any { channel -> channel.name.contains(needle, ignoreCase = true) }
  }
}

internal fun friendlyDlhdError(error: Throwable): String =
  when (error) {
    is SSLException ->
      "Secure connection to the soccer schedule failed. Check this device's date and time, then try again."
    is UnknownHostException -> "No internet connection. Check the network and try again."
    is SocketTimeoutException -> "The soccer schedule took too long to respond. Try again."
    else -> error.message ?: "Soccer schedule could not be loaded"
  }

/**
 * Holds one schedule answer for [ttlMs].
 *
 * Same shape as the hoofoot cache: one payload, one lock, failures never stored.
 */
internal class DlhdSoccerFeedCache(
  private val ttlMs: Long,
  private val now: () -> Long = System::currentTimeMillis,
) {
  private val guard = Mutex()
  private var storedAtMs = 0L
  private var events: List<DlhdSoccerEvent>? = null

  suspend fun get(load: suspend () -> List<DlhdSoccerEvent>): List<DlhdSoccerEvent> {
    fresh()?.let { return it }
    return guard.withLock {
      freshLocked()
        ?: load().also { loaded ->
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

  private suspend fun fresh(): List<DlhdSoccerEvent>? = guard.withLock { freshLocked() }

  private fun freshLocked(): List<DlhdSoccerEvent>? =
    events?.takeIf { storedAtMs != 0L && now() - storedAtMs < ttlMs }
}
