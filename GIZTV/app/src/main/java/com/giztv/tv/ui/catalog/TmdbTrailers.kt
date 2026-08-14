package com.giztv.tv.ui.catalog

import org.json.JSONObject

/**
 * A promotional video TMDB holds for a title.
 *
 * Only the YouTube-hosted ones are kept. Every other site TMDB lists is either dead or has no
 * embeddable player, and a Trailer button that opens nothing is worse than no button.
 */
internal data class TmdbTrailer(
  val key: String,
  val name: String,
  val type: String,
  val official: Boolean,
  val publishedAt: String?,
  val language: String? = null,
) {
  /** Where the trailer lives, and the only address this app ever needs for one. */
  val watchUrl: String
    get() = "https://www.youtube.com/watch?v=$key"
}

/** Video kinds worth offering, best first; anything else TMDB lists is not a trailer. */
private val TRAILER_TYPE_ORDER = listOf("trailer", "teaser", "clip")

internal class TmdbTrailerRepository(private val apiKey: String) {
  suspend fun movieTrailer(movieId: Int): TmdbTrailer? = trailer("movie/$movieId/videos")

  suspend fun showTrailer(showId: Int): TmdbTrailer? = trailer("tv/$showId/videos")

  /**
   * The one video to play, or null when TMDB has nothing usable.
   *
   * Asked in English first, then again without a language at all. TMDB filters `/videos` by the
   * language it is given rather than ranking by it, so a title whose trailer was only ever
   * registered against its own country — most of world cinema, and a good deal of television — has
   * an empty English answer and a full unfiltered one. Soft-fails to null throughout: a missing
   * trailer hides a button, it does not break a page.
   */
  private suspend fun trailer(path: String): TmdbTrailer? {
    val english = runCatching { request(path, language = "en-US") }.getOrNull().orEmpty()
    val videos = english.ifEmpty { runCatching { request(path, language = "") }.getOrNull().orEmpty() }
    return preferredTrailer(videos)
  }

  private suspend fun request(path: String, language: String): List<TmdbTrailer> =
    tmdbRequest(apiKey = apiKey, path = path, language = language, parse = ::parseTmdbTrailers)
}

internal fun parseTmdbTrailers(json: String): List<TmdbTrailer> {
  val results = JSONObject(json).optJSONArray("results") ?: return emptyList()
  return buildList {
    for (index in 0 until results.length()) {
      val item = results.optJSONObject(index) ?: continue
      // Everything else TMDB lists — Vimeo, dead hosts — has no player this app can open.
      if (!item.optString("site").trim().equals("YouTube", ignoreCase = true)) continue
      val key = item.optString("key").trim()
      if (key.isBlank() || key == "null") continue
      val type = item.optString("type").trim()
      if (TRAILER_TYPE_ORDER.none { it.equals(type, ignoreCase = true) }) continue
      add(
        TmdbTrailer(
          key = key,
          name = item.optString("name").trim().takeIf(String::isNotBlank) ?: type.ifBlank { "Trailer" },
          type = type,
          official = item.optBoolean("official", false),
          publishedAt = item.optString("published_at").trim().takeIf(String::isNotBlank),
          language = item.optString("iso_639_1").trim().takeIf(String::isNotBlank),
        )
      )
    }
  }
}

/**
 * The single video a Trailer button should open.
 *
 * A studio trailer beats a teaser beats a clip, an official upload beats a fan re-post at the same
 * kind, and English beats everything else at the same standing — the unfiltered second request
 * exists to find a video at all, not to hand back a dub nobody asked for. Newest wins the last tie,
 * which for a re-released or re-cut title is the trailer that matches what is actually streaming.
 */
internal fun preferredTrailer(videos: List<TmdbTrailer>): TmdbTrailer? =
  videos.minWithOrNull(
    compareBy<TmdbTrailer> { video ->
      TRAILER_TYPE_ORDER.indexOfFirst { it.equals(video.type, ignoreCase = true) }
        .takeIf { it >= 0 } ?: TRAILER_TYPE_ORDER.size
    }
      .thenBy { if (it.official) 0 else 1 }
      .thenBy { if (it.language == null || it.language.equals("en", ignoreCase = true)) 0 else 1 }
      .thenByDescending { it.publishedAt.orEmpty() }
  )
