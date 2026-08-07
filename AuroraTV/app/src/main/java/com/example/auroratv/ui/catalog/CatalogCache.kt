package com.example.auroratv.ui.catalog

import android.os.SystemClock
import kotlinx.coroutines.CoroutineScope
import kotlinx.coroutines.Deferred
import kotlinx.coroutines.Dispatchers
import kotlinx.coroutines.SupervisorJob
import kotlinx.coroutines.async

/** A listing held in memory, and whether it is recent enough to stand on its own. */
internal class CachedListing<out T>(val value: T, val fresh: Boolean)

/**
 * The listings this session has already fetched, kept so the catalog does not ask for them twice.
 *
 * The disk cache underneath `HttpURLConnection` already stops the bytes being fetched again, but a
 * cache hit is still a connection, a read and a parse per rail — enough, eighteen rails at a time,
 * to hold the screen on "Loading…". This sits in front of it and answers from memory.
 *
 * Two things beyond a plain map:
 *
 *  * **Staleness rather than expiry.** A copy past [FRESH_MS] is still handed over immediately and
 *    refreshed behind the viewer, so a rail never goes back to being empty while it reloads.
 *  * **One request per key.** Flipping between Movies and TV Shows while the first load is still
 *    running used to start the whole set over; now the second caller waits on the first's request.
 */
internal object CatalogCache {
  /** How long a listing stands without being checked. Rails move over days, not minutes. */
  private const val FRESH_MS = 15 * 60 * 1000L

  /**
   * Past this a copy is dropped rather than shown.
   *
   * Bounded because the rails are not all timeless: "New in 2026" and "Trending this week" read as
   * a fault when they are yesterday's, and a process left running overnight would show exactly that.
   */
  private const val KEEP_MS = 12 * 60 * 60 * 1000L

  private class Entry(val value: Any, val storedAt: Long)

  private val entries = HashMap<String, Entry>()
  private val inFlight = HashMap<String, Deferred<Any>>()

  /**
   * Requests outlive the screen that asked for them.
   *
   * Leaving the catalog mid-load and coming straight back finds the answer waiting rather than
   * starting the same fetch over, which is the case that used to cost the most.
   */
  private val scope = CoroutineScope(SupervisorJob() + Dispatchers.IO)

  /** What can be shown for [key] right now, without waiting on anything. */
  @Suppress("UNCHECKED_CAST")
  fun <T : Any> peek(key: String): CachedListing<T>? {
    val entry = synchronized(entries) { entries[key] } ?: return null
    val age = now() - entry.storedAt
    if (age > KEEP_MS) {
      synchronized(entries) { entries.remove(key) }
      return null
    }
    return CachedListing(entry.value as T, fresh = age <= FRESH_MS)
  }

  /**
   * Runs [load] for [key], or joins the run already under way for it.
   *
   * The result is stored on the way back, so [peek] can answer for it from then on.
   */
  @Suppress("UNCHECKED_CAST")
  suspend fun <T : Any> fetch(key: String, load: suspend () -> T): T = shared(key, load).await() as T

  /** Forgets everything, so the next look at a rail goes back to TMDB. */
  fun clear() {
    synchronized(entries) { entries.clear() }
  }

  private fun <T : Any> shared(key: String, load: suspend () -> T): Deferred<Any> =
    synchronized(inFlight) {
      inFlight[key]
        ?: scope
          .async {
            val loaded = load()
            synchronized(entries) { entries[key] = Entry(loaded, now()) }
            loaded
          }
          .also { started ->
            inFlight[key] = started
            started.invokeOnCompletion {
              synchronized(inFlight) { if (inFlight[key] === started) inFlight.remove(key) }
            }
          }
    }

  /** Elapsed rather than wall-clock time, so a clock correction cannot age a copy out or in. */
  private fun now(): Long = SystemClock.elapsedRealtime()
}

/** Names one rail's listing in the cache; the tab matters because Movies and TV Shows differ. */
internal fun catalogCacheKey(tab: CatalogTab, category: CatalogCategory): String =
  "${tab.name}:${category.id}"
