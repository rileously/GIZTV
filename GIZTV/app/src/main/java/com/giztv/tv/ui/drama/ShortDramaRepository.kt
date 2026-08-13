package com.giztv.tv.ui.drama

import android.net.Uri
import com.giztv.tv.BuildConfig
import java.io.IOException
import java.net.HttpURLConnection
import java.net.URL
import java.util.concurrent.TimeUnit
import kotlinx.coroutines.Dispatchers
import kotlinx.coroutines.delay
import kotlinx.coroutines.sync.Mutex
import kotlinx.coroutines.sync.withLock
import kotlinx.coroutines.withContext
import org.json.JSONObject

/**
 * One vertical short drama, as chartdrama's own catalogue describes it.
 *
 * [slug] is the site's identifier — "42000012638/the-one-you-love-is-her-twin-sister" — and is
 * taken verbatim rather than rebuilt from the title, so punctuation in a name is never a question.
 */
internal data class ShortDrama(
  val slug: String,
  val title: String,
  val coverUrl: String?,
  val episodeCount: Int,
  val synopsis: String,
  val starring: String?,
  val tags: List<String>,
) {
  /**
   * The card subtitle.
   *
   * A genre reads best and the cast next, but the listing carries neither — only the trending feed
   * does — so the run length stands in, which is the thing a viewer weighs up anyway.
   */
  /** The address opening this drama uses, and so the identity a recorded failure is stored under. */
  val playablePageUrl: String
    get() = chartDramaEpisodeUrl(slug, 1)

  val subtitle: String
    get() =
      tags.firstOrNull()?.takeIf(String::isNotBlank)
        ?: starring?.takeIf { it.isNotBlank() && it != "-" }
        ?: if (episodeCount == 1) "1 episode" else "$episodeCount episodes"
}

/**
 * The chartdrama catalogue.
 *
 * The site is both the listing and the player, so anything it returns can be watched — there is
 * nothing to verify separately — and one search carries the artwork, the episode count and the
 * exact watch URL. Answers are still cached across navigation and requests still paced, because a
 * catalogue is not a thing to ask about repeatedly.
 */
internal object ShortDramaRepository {
  private val searches = DramaBoxCache<List<ShortDrama>>(TimeUnit.MINUTES.toMillis(10))

  suspend fun search(query: String): List<ShortDrama> {
    val trimmed = query.trim()
    if (trimmed.isBlank()) return emptyList()
    return searches.get(trimmed.lowercase()) {
      chartDramaRequest(
        path = "series",
        // Every provider chartdrama carries rather than DramaBox alone: the same endpoint on the
        // main host answers across all of them, and the entries are shaped identically. The
        // listing still refuses to answer without something to search for.
        params = mapOf("q" to trimmed, "limit" to "24"),
        parse = ::parseShortDramas,
      )
    }
  }
}

/**
 * The categories offered above the grid.
 *
 * These are the genres of the form rather than a taxonomy the source publishes, because it does not
 * publish one. `/api/categories` and `/api/genres` are not there, `/api/tags` answers empty, and the
 * listing carries no tags on its entries. Real genre labels exist only on `/api/trending`, whose
 * entries come from other sources and whose pages 404 on this one — a category row built from them
 * would be a row of titles that cannot play.
 *
 * So each of these is matched against title text, which for short drama is closer to a genre than
 * it sounds: the form names its own tropes on the cover. Chosen for what the listing actually
 * returns, measured rather than guessed — every one answers with dozens of titles, and they are
 * ordered roughly by how much there is behind each.
 *
 * Six of them, because the row shares its line with the search field and a seventh squeezes the
 * placeholder onto two lines. Heiress and Boss are the next two worth having if that row ever gets
 * its own line.
 */
internal val DEFAULT_DRAMA_KEYWORDS =
  listOf("Love", "Billionaire", "Revenge", "Secret", "CEO", "Marriage")

private const val CHART_DRAMA_HOST = "chartdrama.com"
internal const val CHART_DRAMA_ORIGIN = "https://$CHART_DRAMA_HOST"

/** Kept modest: a catalogue is browsed, not harvested. */
private val chartDramaRateLimiter = DramaBoxRateLimiter(maxRequests = 20, windowMs = 60_000L)

private const val DEFAULT_RETRY_AFTER_MS = 60_000L

/** Fetches a chartdrama endpoint off the main thread and parses it with [parse]. */
internal suspend fun <T> chartDramaRequest(
  path: String,
  params: Map<String, String>,
  parse: (String) -> T,
): T =
  withContext(Dispatchers.IO) {
    chartDramaRateLimiter.acquire()
    val uri =
      Uri.Builder()
        .scheme("https")
        .authority(CHART_DRAMA_HOST)
        .appendPath("api")
        .appendPath(path)
        .apply { params.forEach { (name, value) -> appendQueryParameter(name, value) } }
        .build()

    val connection = (URL(uri.toString()).openConnection() as HttpURLConnection)
    try {
      connection.requestMethod = "GET"
      connection.setRequestProperty("Accept", "application/json")
      // The listing answers its own site; a request that does not look like one is turned away.
      connection.setRequestProperty("Referer", "$CHART_DRAMA_ORIGIN/")
      connection.setRequestProperty("User-Agent", chartDramaUserAgent())
      connection.connectTimeout = 12_000
      connection.readTimeout = 15_000
      val status = connection.responseCode
      if (status == HttpURLConnection.HTTP_UNAVAILABLE || status == 429) {
        val retryAfterMs = retryAfterMs(connection.getHeaderField("Retry-After"))
        chartDramaRateLimiter.backOff(retryAfterMs)
        throw DramaBoxBusyException(retryAfterMs)
      }
      if (status == HttpURLConnection.HTTP_FORBIDDEN) {
        throw IOException("Short dramas are unavailable on this network right now.")
      }
      if (status !in 200..299) throw IOException("Short dramas returned HTTP $status")
      parse(connection.inputStream.bufferedReader().use { it.readText() })
    } finally {
      connection.disconnect()
    }
  }

internal fun chartDramaUserAgent(): String =
  "Mozilla/5.0 (Linux; Android 10) AppleWebKit/537.36 (KHTML, like Gecko) " +
    "Chrome/131.0.0.0 Mobile Safari/537.36 GIZTV/${BuildConfig.VERSION_NAME}"

/** Thrown when the listing asks to be left alone for a while. */
internal class DramaBoxBusyException(val retryAfterMs: Long) :
  IOException(
    "Short dramas are busy right now. Try again in ${
      TimeUnit.MILLISECONDS.toSeconds(retryAfterMs).coerceAtLeast(1)
    } seconds."
  )

/**
 * Reads the listing, skipping rows that could not be opened and keeping one card per drama.
 *
 * Distinct by slug because the listing spans every provider chartdrama carries rather than one
 * corner of it, and the same drama is carried by more than one of them — a broad word like
 * "Marriage" is where that shows. The grid keys its cards on the slug, and a lazy grid handed the
 * same key twice brings the screen down, so a repeat has to be dropped before it ever gets there.
 * It is the same drama either way: the reel a swipe travels along would otherwise pass through it
 * twice.
 */
internal fun parseShortDramas(json: String): List<ShortDrama> {
  val items = JSONObject(json).optJSONArray("items") ?: return emptyList()
  return buildList {
    for (index in 0 until items.length()) {
      val item = items.optJSONObject(index) ?: continue
      val slug = item.optString("slug").trim().trim('/')
      val title = item.optString("title").trim()
      if (slug.isBlank() || !slug.contains('/') || title.isBlank()) continue
      add(
        ShortDrama(
          slug = slug,
          title = title,
          coverUrl = item.optString("cover").trim().takeIf { it.isNotBlank() && it != "null" },
          episodeCount = episodeCountFromLabel(item.optString("latestEpisodeLabel")),
          synopsis = item.optString("synopsis").trim(),
          starring = item.optString("starring").trim().takeIf(String::isNotBlank),
          tags = item.optJSONArray("tags").toStringList(),
        )
      )
    }
  }
    .distinctBy(ShortDrama::slug)
}

/**
 * Reads the episode count out of a label like "EP71 TV".
 *
 * The listing states how far a drama has run rather than counting its episodes, which for a
 * completed short drama is the same number. A drama always has at least the episode about to open.
 */
internal fun episodeCountFromLabel(label: String?): Int =
  Regex("(\\d+)").find(label.orEmpty())?.groupValues?.get(1)?.toIntOrNull()?.coerceAtLeast(1) ?: 1

private fun org.json.JSONArray?.toStringList(): List<String> {
  val array = this ?: return emptyList()
  return buildList {
    for (index in 0 until array.length()) {
      array.optString(index).trim().takeIf(String::isNotBlank)?.let(::add)
    }
  }
}

internal fun chartDramaEpisodeUrl(slug: String, episode: Int): String {
  require(slug.isNotBlank()) { "A valid chartdrama slug is required" }
  require(episode >= 1) { "A valid episode number is required" }
  return "$CHART_DRAMA_ORIGIN/p/${slug.trim('/')}?ep=$episode"
}

/**
 * Remembers answers for [ttlMs] and lets only one caller per key fetch at a time.
 *
 * The second caller for a key waits on that key's lock rather than opening its own connection, so
 * pressing a chip twice or reopening a drama costs nothing. Failures are never stored.
 */
internal class DramaBoxCache<T>(private val ttlMs: Long, private val now: () -> Long = System::currentTimeMillis) {
  private class Entry<T>(val storedAtMs: Long, val value: T)

  private val guard = Mutex()
  private val entries = mutableMapOf<String, Entry<T>>()
  private val keyLocks = mutableMapOf<String, Mutex>()

  suspend fun get(key: String, load: suspend () -> T): T {
    fresh(key)?.let { return it }
    val keyLock = guard.withLock { keyLocks.getOrPut(key) { Mutex() } }
    return keyLock.withLock {
      // Whoever held the lock may have just filled the cache for us.
      fresh(key) ?: load().also { value -> guard.withLock { entries[key] = Entry(now(), value) } }
    }
  }

  private suspend fun fresh(key: String): T? =
    guard.withLock { entries[key]?.takeIf { now() - it.storedAtMs < ttlMs }?.value }
}

/**
 * Spends the request budget deliberately.
 *
 * Callers past the allowance wait for the window to roll rather than firing and being refused, so
 * an eager viewer sees a moment of loading instead of an error they have to retry. [backOff] folds
 * in whatever the server asks for when the budget is misjudged.
 */
internal class DramaBoxRateLimiter(
  private val maxRequests: Int,
  private val windowMs: Long,
  private val now: () -> Long = System::currentTimeMillis,
) {
  private val guard = Mutex()
  private val spent = ArrayDeque<Long>()
  private var cooldownUntilMs = 0L

  suspend fun acquire() {
    while (true) {
      val waitMs =
        guard.withLock {
          val moment = now()
          while (spent.isNotEmpty() && moment - spent.first() >= windowMs) spent.removeFirst()
          val cooldownWait = (cooldownUntilMs - moment).coerceAtLeast(0L)
          val windowWait =
            if (spent.size < maxRequests) 0L else windowMs - (moment - spent.first())
          val wait = maxOf(cooldownWait, windowWait)
          if (wait <= 0L) spent.addLast(moment)
          wait
        }
      if (waitMs <= 0L) return
      delay(waitMs)
    }
  }

  suspend fun backOff(retryAfterMs: Long) {
    guard.withLock { cooldownUntilMs = maxOf(cooldownUntilMs, now() + retryAfterMs) }
  }
}

/**
 * Reads a `Retry-After` header, which is a whole number of seconds when it is present at all.
 *
 * A missing or unreadable value means the server said nothing useful, so the full window is
 * assumed rather than guessed short.
 */
internal fun retryAfterMs(header: String?): Long {
  val seconds = header?.trim()?.toLongOrNull() ?: return DEFAULT_RETRY_AFTER_MS
  return TimeUnit.SECONDS.toMillis(seconds).coerceIn(1_000L, TimeUnit.MINUTES.toMillis(5))
}
