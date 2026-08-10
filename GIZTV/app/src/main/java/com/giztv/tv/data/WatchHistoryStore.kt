package com.giztv.tv.data

import android.content.Context
import androidx.core.net.toUri
import java.util.Locale
import org.json.JSONObject

private const val WATCH_HISTORY_PREFERENCES = "giztv_watch_history"
private const val CONTINUE_WATCHING_LIMIT = 12

/** A title the viewer has started, with enough detail to show it again without another lookup. */
internal data class WatchHistoryEntry(
  val pageUrl: String,
  val title: String,
  val subtitle: String?,
  val posterUrl: String?,
  val positionMs: Long,
  val durationMs: Long,
  val completed: Boolean,
  val updatedAtMs: Long,
  val showId: Int? = null,
  val seasonNumber: Int? = null,
  val episodeNumber: Int? = null,
  /** A short drama episode, which belongs to the short drama page rather than the film catalog. */
  val shortForm: Boolean = false,
) {
  /** How far through the title the viewer got, as 0f..1f. */
  val progressFraction: Float
    get() =
      when {
        completed -> 1f
        durationMs <= 0L -> 0f
        else -> (positionMs.toFloat() / durationMs.toFloat()).coerceIn(0f, 1f)
      }

  val started: Boolean
    get() = completed || positionMs > 0L

  val resumeLabel: String?
    get() = if (completed || positionMs <= 0L) null else "Resume ${formatWatchTime(positionMs)}"
}

/**
 * Remembers what has been watched, keyed by the catalog page URL that started it.
 *
 * [com.giztv.tv.ui.player.PlaybackProgressStore] still owns plain resume positions for
 * anything found by browsing; this store only covers titles opened from the catalog, where there is
 * a title and artwork worth showing back to the viewer.
 */
internal class WatchHistoryStore(context: Context) {
  private val preferences =
    context.applicationContext.getSharedPreferences(WATCH_HISTORY_PREFERENCES, Context.MODE_PRIVATE)

  fun find(pageUrl: String): WatchHistoryEntry? =
    preferences.getString(watchHistoryKey(pageUrl), null)?.let(::decodeEntry)

  fun save(entry: WatchHistoryEntry) {
    preferences.edit().putString(watchHistoryKey(entry.pageUrl), encodeEntry(entry)).apply()
  }

  /** Records progress for [context], keeping any previously stored completion. */
  fun record(context: PlaybackContext, positionMs: Long, durationMs: Long, completed: Boolean) {
    val existing = find(context.pageUrl)
    save(
      WatchHistoryEntry(
        pageUrl = context.pageUrl,
        title = context.title,
        subtitle = context.subtitle,
        posterUrl = context.posterUrl,
        positionMs = positionMs.coerceAtLeast(0L),
        durationMs = durationMs.coerceAtLeast(0L),
        completed = completed || (existing?.completed == true && positionMs <= 0L),
        updatedAtMs = System.currentTimeMillis(),
        showId = context.showId,
        seasonNumber = context.seasonNumber,
        episodeNumber = context.episodeNumber,
        shortForm = context.shortForm,
      )
    )
  }

  fun markCompleted(context: PlaybackContext, durationMs: Long) {
    record(context, positionMs = 0L, durationMs = durationMs, completed = true)
  }

  fun remove(pageUrl: String) {
    preferences.edit().remove(watchHistoryKey(pageUrl)).apply()
  }

  fun clear() {
    preferences.edit().clear().apply()
  }

  /**
   * Unfinished titles of one kind, most recently watched first.
   *
   * Short dramas are kept apart from films and shows: a run of two-minute episodes would crowd the
   * catalog's row out within an evening, and it belongs beside the dramas it came from anyway.
   */
  fun continueWatching(
    shortForm: Boolean = false,
    limit: Int = CONTINUE_WATCHING_LIMIT,
  ): List<WatchHistoryEntry> =
    all().filter { !it.completed && it.positionMs > 0L && it.shortForm == shortForm }.take(limit)

  /** Everything watched, finished or not, most recent first. */
  fun all(): List<WatchHistoryEntry> =
    preferences.all.values
      .filterIsInstance<String>()
      .mapNotNull(::decodeEntry)
      .sortedByDescending { it.updatedAtMs }
}

/** Page URLs carry throwaway fragments, so they are normalised before being used as a key. */
internal fun watchHistoryKey(pageUrl: String): String =
  runCatching { pageUrl.toUri().buildUpon().fragment(null).build().toString() }
    .getOrDefault(pageUrl.substringBefore('#'))

internal fun formatWatchTime(positionMs: Long): String {
  val totalSeconds = (positionMs / 1_000L).coerceAtLeast(0L)
  val hours = totalSeconds / 3_600L
  val minutes = (totalSeconds % 3_600L) / 60L
  val seconds = totalSeconds % 60L
  return if (hours > 0L) {
    String.format(Locale.US, "%d:%02d:%02d", hours, minutes, seconds)
  } else {
    String.format(Locale.US, "%d:%02d", minutes, seconds)
  }
}

private fun encodeEntry(entry: WatchHistoryEntry): String =
  JSONObject()
    .put("pageUrl", entry.pageUrl)
    .put("title", entry.title)
    .put("subtitle", entry.subtitle ?: JSONObject.NULL)
    .put("posterUrl", entry.posterUrl ?: JSONObject.NULL)
    .put("positionMs", entry.positionMs)
    .put("durationMs", entry.durationMs)
    .put("completed", entry.completed)
    .put("updatedAtMs", entry.updatedAtMs)
    .put("showId", entry.showId ?: JSONObject.NULL)
    .put("seasonNumber", entry.seasonNumber ?: JSONObject.NULL)
    .put("episodeNumber", entry.episodeNumber ?: JSONObject.NULL)
    .put("shortForm", entry.shortForm)
    .toString()

internal fun decodeEntry(json: String): WatchHistoryEntry? =
  runCatching {
      val item = JSONObject(json)
      val pageUrl = item.optString("pageUrl").takeIf { it.isNotBlank() } ?: return@runCatching null
      WatchHistoryEntry(
        pageUrl = pageUrl,
        title = item.optString("title").takeIf { it.isNotBlank() } ?: pageUrl,
        subtitle = item.optStringOrNull("subtitle"),
        posterUrl = item.optStringOrNull("posterUrl"),
        positionMs = item.optLong("positionMs", 0L),
        durationMs = item.optLong("durationMs", 0L),
        completed = item.optBoolean("completed", false),
        updatedAtMs = item.optLong("updatedAtMs", 0L),
        showId = item.optIntOrNull("showId"),
        seasonNumber = item.optIntOrNull("seasonNumber"),
        episodeNumber = item.optIntOrNull("episodeNumber"),
        shortForm = item.optBoolean("shortForm", false),
      )
    }
    .getOrNull()

private fun JSONObject.optStringOrNull(name: String): String? =
  if (isNull(name)) null else optString(name).takeIf { it.isNotBlank() }

private fun JSONObject.optIntOrNull(name: String): Int? = if (isNull(name)) null else optInt(name)
