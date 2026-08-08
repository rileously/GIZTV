package com.example.auroratv.data

import android.content.Context
import androidx.core.net.toUri
import org.json.JSONObject

private const val BOOKMARK_PREFERENCES = "giztv_bookmarks"

/** A page the viewer saved from the browser, so it is one click away next time. */
internal data class Bookmark(
  val url: String,
  val title: String,
  val savedAtMs: Long = System.currentTimeMillis(),
) {
  /** The host alone, which is what fits under the title on a card. */
  val host: String
    get() = url.toUri().host?.removePrefix("www.") ?: url
}

/**
 * The viewer's saved pages, newest first.
 *
 * Ships empty and stays that way until the viewer saves something: nothing here is seeded by the
 * app, so the list is only ever the pages they chose to keep.
 */
internal class BookmarkStore(context: Context) {
  private val preferences =
    context.applicationContext.getSharedPreferences(BOOKMARK_PREFERENCES, Context.MODE_PRIVATE)

  fun contains(url: String): Boolean = preferences.contains(url.trim())

  fun add(bookmark: Bookmark) {
    val key = bookmark.url.trim()
    if (key.isEmpty()) return
    preferences.edit().putString(key, encodeBookmark(bookmark.copy(url = key))).apply()
  }

  fun remove(url: String) {
    preferences.edit().remove(url.trim()).apply()
  }

  /** Adds or removes the page, returning true when it ends up saved. */
  fun toggle(bookmark: Bookmark): Boolean {
    val saved = contains(bookmark.url)
    if (saved) remove(bookmark.url) else add(bookmark)
    return !saved
  }

  fun all(): List<Bookmark> =
    preferences.all.values
      .filterIsInstance<String>()
      .mapNotNull(::decodeBookmark)
      .sortedByDescending { it.savedAtMs }
}

private fun encodeBookmark(bookmark: Bookmark): String =
  JSONObject()
    .put("url", bookmark.url)
    .put("title", bookmark.title)
    .put("savedAtMs", bookmark.savedAtMs)
    .toString()

internal fun decodeBookmark(json: String): Bookmark? =
  runCatching {
      val saved = JSONObject(json)
      val url = saved.optString("url").takeIf { it.isNotBlank() } ?: return@runCatching null
      Bookmark(
        url = url,
        // An untitled page still deserves a row, and the host reads better than a blank one.
        title = saved.optString("title").takeIf { it.isNotBlank() } ?: url,
        savedAtMs = saved.optLong("savedAtMs", 0L),
      )
    }
    .getOrNull()
