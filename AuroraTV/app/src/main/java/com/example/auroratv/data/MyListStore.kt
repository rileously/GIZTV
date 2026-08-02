package com.example.auroratv.data

import android.content.Context
import org.json.JSONObject

private const val MY_LIST_PREFERENCES = "giztv_my_list"

internal enum class LibraryKind {
  MOVIE,
  SHOW,
}

/** A saved title, holding the same fields the catalog models expose so either can be rebuilt. */
internal data class LibraryItem(
  val id: Int,
  val kind: LibraryKind,
  val title: String,
  val date: String?,
  val voteAverage: Double,
  val overview: String,
  val posterPath: String?,
  val addedAtMs: Long = System.currentTimeMillis(),
) {
  val year: String?
    get() = date?.takeIf { it.length >= 4 }?.take(4)

  val posterUrl: String?
    get() = posterPath?.let { "https://image.tmdb.org/t/p/w500$it" }
}

/** The viewer's saved titles, newest first. */
internal class MyListStore(context: Context) {
  private val preferences =
    context.applicationContext.getSharedPreferences(MY_LIST_PREFERENCES, Context.MODE_PRIVATE)

  fun contains(kind: LibraryKind, id: Int): Boolean = preferences.contains(itemKey(kind, id))

  fun add(item: LibraryItem) {
    preferences.edit().putString(itemKey(item.kind, item.id), encodeItem(item)).apply()
  }

  fun remove(kind: LibraryKind, id: Int) {
    preferences.edit().remove(itemKey(kind, id)).apply()
  }

  /** Adds or removes [item], returning true when it ends up saved. */
  fun toggle(item: LibraryItem): Boolean {
    val saved = contains(item.kind, item.id)
    if (saved) remove(item.kind, item.id) else add(item)
    return !saved
  }

  fun all(): List<LibraryItem> =
    preferences.all.values
      .filterIsInstance<String>()
      .mapNotNull(::decodeItem)
      .sortedByDescending { it.addedAtMs }
}

private fun itemKey(kind: LibraryKind, id: Int): String = "${kind.name}:$id"

private fun encodeItem(item: LibraryItem): String =
  JSONObject()
    .put("id", item.id)
    .put("kind", item.kind.name)
    .put("title", item.title)
    .put("date", item.date ?: JSONObject.NULL)
    .put("voteAverage", item.voteAverage)
    .put("overview", item.overview)
    .put("posterPath", item.posterPath ?: JSONObject.NULL)
    .put("addedAtMs", item.addedAtMs)
    .toString()

internal fun decodeItem(json: String): LibraryItem? =
  runCatching {
      val item = JSONObject(json)
      val id = item.optInt("id", -1)
      if (id <= 0) return@runCatching null
      val kind = LibraryKind.entries.firstOrNull { it.name == item.optString("kind") } ?: return@runCatching null
      LibraryItem(
        id = id,
        kind = kind,
        title = item.optString("title").takeIf { it.isNotBlank() } ?: return@runCatching null,
        date = if (item.isNull("date")) null else item.optString("date").takeIf { it.isNotBlank() },
        voteAverage = item.optDouble("voteAverage", 0.0),
        overview = item.optString("overview"),
        posterPath =
          if (item.isNull("posterPath")) null else item.optString("posterPath").takeIf { it.isNotBlank() },
        addedAtMs = item.optLong("addedAtMs", 0L),
      )
    }
    .getOrNull()
