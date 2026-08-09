package com.example.auroratv.data

import android.content.Context
import androidx.core.net.toUri
import org.json.JSONArray
import org.json.JSONObject

private const val STREAM_CACHE_PREFERENCES = "giztv_stream_cache"

/**
 * How long a stream is trusted when its own address will not say.
 *
 * Short, because the cost of being wrong is asymmetric: a stale address turns a slow success into an
 * instant failure, which is the worse of the two by a distance.
 */
private const val FALLBACK_TTL_MS = 20L * 60L * 1000L

/** Nothing is kept beyond this even if the address claims a week, because the claim may be wrong. */
private const val MAXIMUM_TTL_MS = 6L * 60L * 60L * 1000L

/** A subtitle track as it was found, kept whole so a cached play is not a lesser one. */
internal data class CachedSubtitle(
  val url: String,
  val label: String,
  val language: String?,
  val mimeType: String,
)

/** A stream found earlier, and how long it is worth believing in. */
internal data class CachedStream(
  val url: String,
  val headers: Map<String, String>,
  val subtitles: List<CachedSubtitle>,
  val sourcePageUrl: String?,
  val expiresAtMs: Long,
)

/**
 * Remembers where a title's video was, so a second play does not go looking again.
 *
 * Finding a stream means loading the page it hides behind and waiting for its player to ask for the
 * video, which is the whole wait before a film starts. The answer stays good for a while — these
 * addresses are signed with an expiry — so the second play can skip the search entirely.
 *
 * What is stored is only ever a shortcut. Every entry is checked before it is used and thrown away
 * the moment it fails, because a wrong answer here costs more than no answer at all.
 */
internal class StreamCacheStore(context: Context) {
  private val preferences =
    context.applicationContext.getSharedPreferences(
      STREAM_CACHE_PREFERENCES,
      Context.MODE_PRIVATE,
    )

  /**
   * The stream last found for a title, or the one a named site last gave up for it.
   *
   * Asking for a particular [providerId] is how a hand-picked server stops costing a full search
   * every time it is chosen. The unnamed entry is whoever answered most recently, which is what
   * pressing play wants; the named ones survive a switch away and back.
   */
  fun find(pageUrl: String, providerId: String? = null): CachedStream? {
    val key = cacheKey(pageUrl, providerId)
    val raw = preferences.getString(key, null) ?: return null
    val cached = runCatching { decode(raw) }.getOrNull() ?: return null
    if (System.currentTimeMillis() >= cached.expiresAtMs) {
      preferences.edit().remove(key).apply()
      return null
    }
    return cached
  }

  fun remember(
    pageUrl: String,
    url: String,
    headers: Map<String, String>,
    subtitles: List<CachedSubtitle>,
    sourcePageUrl: String?,
    /** The site that produced it, so choosing that server again need not go looking. */
    providerId: String? = null,
  ) {
    val entry =
      CachedStream(
        url = url,
        headers = headers,
        subtitles = subtitles,
        sourcePageUrl = sourcePageUrl,
        expiresAtMs = expiryOf(url),
      )
    val encoded = encode(entry)
    val editor = preferences.edit().putString(cacheKey(pageUrl, null), encoded)
    providerId?.let { editor.putString(cacheKey(pageUrl, it), encoded) }
    editor.apply()
  }

  /**
   * Called the moment a remembered stream fails, so it is never offered twice.
   *
   * With no [providerId] only the most-recent entry goes: the others belong to sites that have not
   * been asked and may still be perfectly good. Naming one throws that site's answer away too,
   * which is what a stream failing under playback means.
   */
  fun forget(pageUrl: String, providerId: String? = null) {
    val editor = preferences.edit().remove(cacheKey(pageUrl, null))
    providerId?.let { editor.remove(cacheKey(pageUrl, it)) }
    editor.apply()
  }

  private fun cacheKey(pageUrl: String, providerId: String?): String =
    watchHistoryKey(pageUrl) + if (providerId == null) "" else "@$providerId"

  private fun encode(entry: CachedStream): String =
    JSONObject()
      .put("url", entry.url)
      .put("headers", JSONObject(entry.headers))
      .put(
        "subtitles",
        JSONArray().apply {
          entry.subtitles.forEach { track ->
            put(
              JSONObject()
                .put("url", track.url)
                .put("label", track.label)
                .put("language", track.language ?: JSONObject.NULL)
                .put("mimeType", track.mimeType)
            )
          }
        },
      )
      .put("sourcePageUrl", entry.sourcePageUrl ?: JSONObject.NULL)
      .put("expiresAtMs", entry.expiresAtMs)
      .toString()

  private fun decode(raw: String): CachedStream {
    val json = JSONObject(raw)
    val headers = json.optJSONObject("headers") ?: JSONObject()
    val subtitles = json.optJSONArray("subtitles") ?: JSONArray()
    return CachedStream(
      url = json.getString("url"),
      headers = headers.keys().asSequence().associateWith { headers.optString(it) },
      subtitles =
        (0 until subtitles.length()).mapNotNull { index ->
          val item = subtitles.optJSONObject(index) ?: return@mapNotNull null
          val url = item.optString("url").takeIf(String::isNotBlank) ?: return@mapNotNull null
          CachedSubtitle(
            url = url,
            label = item.optString("label"),
            language = item.optString("language").takeIf { it.isNotBlank() && it != "null" },
            mimeType = item.optString("mimeType").ifBlank { "text/vtt" },
          )
        },
      sourcePageUrl = json.optString("sourcePageUrl").takeIf { it.isNotBlank() && it != "null" },
      expiresAtMs = json.optLong("expiresAtMs"),
    )
  }
}

/**
 * When a signed address stops working.
 *
 * These are handed out with the expiry written into them, under one of a handful of names and in
 * either seconds or milliseconds. Where one can be read it is trusted, less a minute's grace so a
 * stream is never handed over with seconds left on it. Where none can be read, a short guess.
 */
internal fun expiryOf(url: String, nowMs: Long = System.currentTimeMillis()): Long {
  val stated = statedExpiryMs(url, nowMs)
  val fallback = nowMs + FALLBACK_TTL_MS
  val ceiling = nowMs + MAXIMUM_TTL_MS
  if (stated == null) return fallback
  val withGrace = stated - 60_000L
  // An expiry already past, or one implausibly far off, says the guess was wrong rather than that
  // the stream is dead: fall back rather than refuse to cache at all.
  if (withGrace <= nowMs) return fallback
  return withGrace.coerceAtMost(ceiling)
}

private val EXPIRY_KEYS = setOf("expires", "expire", "exp", "e", "st", "validto", "valid_to", "ttl")

private fun statedExpiryMs(url: String, nowMs: Long): Long? {
  val uri = runCatching { url.toUri() }.getOrNull() ?: return null
  val names = runCatching { uri.queryParameterNames }.getOrNull().orEmpty()
  for (name in names) {
    if (name.lowercase() !in EXPIRY_KEYS) continue
    val value = runCatching { uri.getQueryParameter(name) }.getOrNull()?.toLongOrNull() ?: continue
    return asEpochMillis(value, nowMs)
  }
  return null
}

/**
 * A number that might be seconds, milliseconds, or a lifetime rather than a moment.
 *
 * Told apart by size: anything under a year is a duration counted from now, anything under a
 * hundred billion is seconds since the epoch, and the rest is already milliseconds.
 */
private fun asEpochMillis(value: Long, nowMs: Long): Long? =
  when {
    value <= 0L -> null
    value < 31_536_000L -> nowMs + value * 1000L
    value < 100_000_000_000L -> value * 1000L
    else -> value
  }
