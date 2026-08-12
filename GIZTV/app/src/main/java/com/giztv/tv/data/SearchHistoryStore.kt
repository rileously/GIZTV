package com.giztv.tv.data

import android.content.Context
import org.json.JSONArray

private const val SEARCH_HISTORY_PREFERENCES = "giztv_search_history"
private const val SEARCH_HISTORY_LIMIT = 10

/**
 * The searchable listings, each remembering its own queries.
 *
 * Kept apart rather than pooled: a viewer looking for a football fixture is not helped by the anime
 * they searched for last night, and the sections have nothing in common but the shape of the box
 * they are typed into. [key] is what the queries are stored under and must not move once shipped —
 * renaming one would silently lose that section's history.
 */
internal enum class SearchSection(val key: String, val label: String) {
  MOVIES("movies", "movies"),
  TV_SHOWS("tv_shows", "shows"),
  SPORTS("sports", "sports"),
  SOCCER("soccer", "soccer"),
  SHORT_DRAMAS("short_dramas", "short dramas"),
  ANIME("anime", "anime"),
  IPTV("iptv", "channels"),
}

/**
 * What each listing has been searched for, most recent first.
 *
 * Two rules keep the list worth reading. Queries repeat constantly — the same show is looked up
 * across weeks — so a repeat moves to the front rather than being added again. And several of these
 * screens search as the viewer types, which would otherwise record "b", "br", "bre" alongside
 * "breaking bad"; anything the new query merely extends is dropped, so only what was actually
 * settled on survives.
 */
internal class SearchHistoryStore(context: Context) {
  private val preferences =
    context.applicationContext.getSharedPreferences(
      SEARCH_HISTORY_PREFERENCES,
      Context.MODE_PRIVATE,
    )

  fun recent(section: SearchSection): List<String> = decode(preferences.getString(section.key, null))

  /** Records [query], ignoring blanks and collapsing the prefixes a typed search leaves behind. */
  fun record(section: SearchSection, query: String) {
    val trimmed = query.trim()
    if (trimmed.isBlank()) return
    val kept =
      recent(section).filterNot { existing ->
        existing.equals(trimmed, ignoreCase = true) || trimmed.startsWith(existing, ignoreCase = true)
      }
    write(section, (listOf(trimmed) + kept).take(SEARCH_HISTORY_LIMIT))
  }

  /** Drops one remembered query, for the viewer who wants a single search forgotten. */
  fun remove(section: SearchSection, query: String) {
    write(section, recent(section).filterNot { it.equals(query.trim(), ignoreCase = true) })
  }

  fun clear(section: SearchSection) {
    preferences.edit().remove(section.key).apply()
  }

  /** Every section at once, for a viewer clearing the lot rather than one listing's worth. */
  fun clearAll() {
    preferences.edit().clear().apply()
  }

  private fun write(section: SearchSection, queries: List<String>) {
    if (queries.isEmpty()) {
      clear(section)
      return
    }
    preferences.edit().putString(section.key, encode(queries)).apply()
  }
}

private fun encode(queries: List<String>): String =
  JSONArray().apply { queries.forEach(::put) }.toString()

private fun decode(stored: String?): List<String> {
  val json = stored?.takeIf { it.isNotBlank() } ?: return emptyList()
  return runCatching {
      val array = JSONArray(json)
      (0 until array.length()).mapNotNull { array.optString(it).takeIf(String::isNotBlank) }
    }
    .getOrDefault(emptyList())
}
