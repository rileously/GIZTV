package com.example.auroratv.data

import android.content.Context
import androidx.core.net.toUri

private const val STREAM_RESOLUTION_PREFERENCES = "giztv_stream_resolution"
private const val KEY_LAST_SUCCESS_PREFIX = "resolved_ms_"
private const val KEY_HOST_SUCCESS_PREFIX = "resolved_host_ms_"

/** Below this a recorded time is noise rather than a measurement worth keeping. */
private const val MINIMUM_RECORDED_MS = 250L
internal const val DEFAULT_RESOLUTION_TIMEOUT_MS = 30_000L
internal const val MAXIMUM_RESOLUTION_TIMEOUT_MS = 60_000L

/**
 * The least time any title gets, however quick its site has been before.
 *
 * A measurement is an average of good nights, and giving up on a page that is merely having a slow
 * one would turn a wait into a failure. Nothing is cut off before this.
 */
internal const val MINIMUM_RESOLUTION_TIMEOUT_MS = 15_000L

/**
 * How long each title has taken to give up a stream.
 *
 * Some pages hand one over in three seconds and some grind through a dozen redirects; one fixed
 * limit for both means the slow ones are declared broken while they are still working. What is
 * remembered here is only ever used to wait longer, never to give up sooner.
 */
internal class StreamResolutionStore(context: Context) {
  private val preferences =
    context.applicationContext.getSharedPreferences(
      STREAM_RESOLUTION_PREFERENCES,
      Context.MODE_PRIVATE,
    )

  /**
   * How long this title took last time, or failing that how long its site usually takes.
   *
   * The page is the better answer where there is one. There often is not: a live fixture has a new
   * address every match, so nothing is ever learned about it and every one waited the full default
   * — including the ones that were never going to answer at all. What the site itself has done
   * before stands in.
   */
  fun lastSuccessMs(pageUrl: String): Long {
    val forPage = preferences.getLong(KEY_LAST_SUCCESS_PREFIX + watchHistoryKey(pageUrl), 0L)
    if (forPage > 0L) return forPage
    val host = hostOf(pageUrl) ?: return 0L
    return preferences.getLong(KEY_HOST_SUCCESS_PREFIX + host, 0L)
  }

  fun recordSuccess(pageUrl: String, elapsedMs: Long) {
    if (elapsedMs < MINIMUM_RECORDED_MS) return
    val editor = preferences.edit().putLong(KEY_LAST_SUCCESS_PREFIX + watchHistoryKey(pageUrl), elapsedMs)
    hostOf(pageUrl)?.let { host ->
      // Eased towards rather than replaced, so one unlucky night does not become the expectation.
      val known = preferences.getLong(KEY_HOST_SUCCESS_PREFIX + host, 0L)
      val blended = if (known <= 0L) elapsedMs else (known * 2 + elapsedMs) / 3
      editor.putLong(KEY_HOST_SUCCESS_PREFIX + host, blended)
    }
    editor.apply()
  }
}

/**
 * How long to wait for this title before deciding nothing is coming.
 *
 * A title that has taken twenty seconds before is given room to do it again, with half as much
 * again for a slower night, and everything is capped so a hung page still ends.
 */
internal fun streamResolutionTimeoutMs(lastSuccessMs: Long): Long =
  when {
    lastSuccessMs <= 0L -> DEFAULT_RESOLUTION_TIMEOUT_MS
    // Three times what it has taken before, which is generous enough that a slow night still
    // finishes, and short enough that a page which is never going to answer says so sooner. A site
    // that answers in three seconds no longer keeps anyone waiting thirty.
    else ->
      (lastSuccessMs * 3)
        .coerceAtLeast(MINIMUM_RESOLUTION_TIMEOUT_MS)
        .coerceAtMost(MAXIMUM_RESOLUTION_TIMEOUT_MS)
  }

private fun hostOf(pageUrl: String): String? =
  runCatching { pageUrl.toUri().host?.lowercase() }.getOrNull()?.takeIf(String::isNotBlank)
