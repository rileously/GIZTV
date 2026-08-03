package com.example.auroratv.ui.drama

import android.net.Uri
import java.io.IOException
import java.net.HttpURLConnection
import java.net.URL
import java.util.concurrent.TimeUnit
import kotlinx.coroutines.Dispatchers
import kotlinx.coroutines.delay
import kotlinx.coroutines.sync.Mutex
import kotlinx.coroutines.sync.withLock
import kotlinx.coroutines.withContext
import org.json.JSONArray
import org.json.JSONObject

/** One vertical short drama as the DramaBox search endpoint describes it. */
internal data class ShortDrama(
  val bookId: String,
  val bookName: String,
  val introduction: String,
  val coverUrl: String?,
  val protagonist: String?,
  val tags: List<String>,
) {
  /** The card subtitle: a genre reads better than the cast, but either beats an empty line. */
  val subtitle: String?
    get() = tags.firstOrNull()?.takeIf(String::isNotBlank) ?: protagonist?.takeIf(String::isNotBlank)
}

internal data class ShortDramaDetail(
  val bookId: String,
  val bookName: String,
  val introduction: String,
  val coverUrl: String?,
  val chapterCount: Int,
  val tags: List<String>,
)

/**
 * The public DramaBox listing.
 *
 * The API only answers searches — there is no browsable listing — so the screen seeds itself with
 * [DEFAULT_DRAMA_KEYWORDS]. It is also unauthenticated and capped at ten requests a minute per
 * address, a budget small enough that a few minutes of ordinary browsing would exhaust it. Three
 * things keep it intact, and they are why this is an object rather than something a screen builds:
 * answers are cached across navigation, concurrent callers asking the same thing share one request,
 * and requests are paced so the cap is approached rather than hit.
 */
internal object ShortDramaRepository {
  // A listing shifts over a session; an episode count is all but immutable, so it is held for a day.
  private val searches = DramaBoxCache<List<ShortDrama>>(TimeUnit.MINUTES.toMillis(10))
  private val details = DramaBoxCache<ShortDramaDetail>(TimeUnit.HOURS.toMillis(24))

  suspend fun search(query: String): List<ShortDrama> {
    val trimmed = query.trim()
    return searches.get(trimmed.lowercase()) {
      dramaBoxRequest("search", mapOf("query" to trimmed), ::parseShortDramas)
    }
  }

  suspend fun detail(bookId: String): ShortDramaDetail =
    details.get(bookId) {
      dramaBoxRequest("detail", mapOf("bookId" to bookId), ::parseShortDramaDetail)
    }
}

/** Thrown when the shared request budget is spent, carrying how long the caller must wait. */
internal class DramaBoxBusyException(val retryAfterMs: Long) :
  IOException(
    "DramaBox is busy right now. Try again in ${
      TimeUnit.MILLISECONDS.toSeconds(retryAfterMs).coerceAtLeast(1)
    } seconds."
  )

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

/** Seed searches for the chip row, standing in for the listing endpoint the API lacks. */
internal val DEFAULT_DRAMA_KEYWORDS =
  listOf("Love", "CEO", "Revenge", "Billionaire", "Marriage", "Werewolf")

private const val DRAMA_BOX_HOST = "api.sansekai.my.id"

/** The published cap is ten a minute; the app claims eight so a stray request is never the one. */
private val dramaBoxRateLimiter = DramaBoxRateLimiter(maxRequests = 8, windowMs = 60_000L)

private const val DEFAULT_RETRY_AFTER_MS = 60_000L

/** Fetches a DramaBox endpoint off the main thread and parses it with [parse]. */
internal suspend fun <T> dramaBoxRequest(
  path: String,
  params: Map<String, String>,
  parse: (String) -> T,
): T =
  withContext(Dispatchers.IO) {
    dramaBoxRateLimiter.acquire()
    val uri =
      Uri.Builder()
        .scheme("https")
        .authority(DRAMA_BOX_HOST)
        .appendPath("api")
        .appendPath("dramabox")
        .appendPath(path)
        .apply { params.forEach { (name, value) -> appendQueryParameter(name, value) } }
        .build()

    val connection = (URL(uri.toString()).openConnection() as HttpURLConnection)
    try {
      connection.requestMethod = "GET"
      connection.setRequestProperty("Accept", "application/json")
      connection.connectTimeout = 12_000
      connection.readTimeout = 15_000
      val status = connection.responseCode
      // The listing is shared and unauthenticated, so a busy minute is expected rather than broken.
      // Whatever budget the server says is left becomes the limiter's, so the next call waits it out
      // instead of spending a request to be refused again.
      if (status == HttpURLConnection.HTTP_UNAVAILABLE || status == 429) {
        val retryAfterMs = retryAfterMs(connection.getHeaderField("Retry-After"))
        dramaBoxRateLimiter.backOff(retryAfterMs)
        throw DramaBoxBusyException(retryAfterMs)
      }
      if (status !in 200..299) throw IOException("DramaBox returned HTTP $status")
      parse(connection.inputStream.bufferedReader().use { it.readText() })
    } finally {
      connection.disconnect()
    }
  }

/**
 * Reads a `Retry-After` header, which is a whole number of seconds when it is present at all.
 *
 * HTTP-date form is legal but unused here, and a missing or unreadable value means the server said
 * nothing useful, so the full window is assumed rather than guessed short.
 */
internal fun retryAfterMs(header: String?): Long {
  val seconds = header?.trim()?.toLongOrNull() ?: return DEFAULT_RETRY_AFTER_MS
  return TimeUnit.SECONDS.toMillis(seconds).coerceIn(1_000L, TimeUnit.MINUTES.toMillis(5))
}

internal fun parseShortDramas(json: String): List<ShortDrama> {
  val results = runCatching { JSONArray(json) }.getOrNull() ?: return emptyList()
  return buildList {
    for (index in 0 until results.length()) {
      val item = results.optJSONObject(index) ?: continue
      val bookId = item.optString("bookId").trim()
      val bookName = item.optString("bookName").trim()
      if (bookId.isBlank() || bookName.isBlank()) continue
      add(
        ShortDrama(
          bookId = bookId,
          bookName = bookName,
          introduction = item.optString("introduction").trim(),
          coverUrl = item.optString("cover").trim().takeIf { it.isNotBlank() && it != "null" },
          protagonist = item.optString("protagonist").trim().takeIf(String::isNotBlank),
          tags = item.optJSONArray("tagNames").toStringList(),
        )
      )
    }
  }
}

internal fun parseShortDramaDetail(json: String): ShortDramaDetail {
  val root = JSONObject(json)
  val bookId = root.optString("bookId").trim()
  if (bookId.isBlank()) throw IOException("DramaBox returned no drama for this title")
  return ShortDramaDetail(
    bookId = bookId,
    bookName = root.optString("bookName").trim(),
    introduction = root.optString("introduction").trim(),
    coverUrl = root.optString("coverWap").trim().takeIf { it.isNotBlank() && it != "null" },
    // A drama always has at least the episode the viewer is about to open.
    chapterCount = root.optInt("chapterCount", 1).coerceAtLeast(1),
    tags = root.optJSONArray("tags").toStringList(),
  )
}

private fun JSONArray?.toStringList(): List<String> {
  val array = this ?: return emptyList()
  return buildList {
    for (index in 0 until array.length()) {
      array.optString(index).trim().takeIf(String::isNotBlank)?.let(::add)
    }
  }
}

/**
 * The watch page slug, e.g. "The One You Love is Her Twin Sister" →
 * "the-one-you-love-is-her-twin-sister".
 */
internal fun chartDramaSlug(bookName: String): String =
  bookName.lowercase().replace(Regex("[^a-z0-9]+"), "-").trim('-')

internal fun chartDramaEpisodeUrl(bookId: String, bookName: String, episode: Int): String {
  require(bookId.isNotBlank()) { "A valid DramaBox book ID is required" }
  require(episode >= 1) { "A valid episode number is required" }
  return "https://dramabox.chartdrama.com/p/$bookId/${chartDramaSlug(bookName)}?ep=$episode"
}
