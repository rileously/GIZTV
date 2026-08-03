package com.example.auroratv.data

import android.content.Context

private const val STREAM_RESOLUTION_PREFERENCES = "giztv_stream_resolution"
private const val KEY_LAST_SUCCESS_PREFIX = "resolved_ms_"

/** Below this a recorded time is noise rather than a measurement worth keeping. */
private const val MINIMUM_RECORDED_MS = 250L
internal const val DEFAULT_RESOLUTION_TIMEOUT_MS = 30_000L
internal const val MAXIMUM_RESOLUTION_TIMEOUT_MS = 60_000L

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

  fun lastSuccessMs(pageUrl: String): Long =
    preferences.getLong(KEY_LAST_SUCCESS_PREFIX + watchHistoryKey(pageUrl), 0L)

  fun recordSuccess(pageUrl: String, elapsedMs: Long) {
    if (elapsedMs < MINIMUM_RECORDED_MS) return
    preferences.edit()
      .putLong(KEY_LAST_SUCCESS_PREFIX + watchHistoryKey(pageUrl), elapsedMs)
      .apply()
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
    else ->
      (lastSuccessMs * 3 / 2)
        .coerceAtLeast(DEFAULT_RESOLUTION_TIMEOUT_MS)
        .coerceAtMost(MAXIMUM_RESOLUTION_TIMEOUT_MS)
  }
