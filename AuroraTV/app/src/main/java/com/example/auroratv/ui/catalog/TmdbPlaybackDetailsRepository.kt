package com.example.auroratv.ui.catalog

import com.example.auroratv.data.PlaybackContext
import org.json.JSONArray
import org.json.JSONObject

/** Extra title information loaded alongside stream discovery without delaying playback. */
internal data class TmdbPlaybackDetails(
  val overview: String?,
  val year: String?,
  val releaseDate: String?,
  val runtimeMinutes: Int?,
  val rating: Double?,
  val genres: List<String>,
  val castNames: List<String>,
)

internal class TmdbPlaybackDetailsRepository(private val apiKey: String) {
  suspend fun details(playback: PlaybackContext): TmdbPlaybackDetails? {
    val path =
      if (playback.isEpisode) {
        val showId = playback.showId ?: return null
        "tv/$showId/season/${playback.seasonNumber}/episode/${playback.episodeNumber}"
      } else {
        val movieId = tmdbMovieIdFromPlaybackUrl(playback.pageUrl) ?: return null
        "movie/$movieId"
      }
    return tmdbRequest(
      apiKey = apiKey,
      path = path,
      params = mapOf("append_to_response" to "credits"),
      parse = { parseTmdbPlaybackDetails(it, playback.isEpisode) },
    )
  }
}

internal fun tmdbMovieIdFromPlaybackUrl(url: String): Int? =
  Regex("(?:^|/)movie/(\\d+)(?:/|[?#]|$)", RegexOption.IGNORE_CASE)
    .find(url)
    ?.groupValues
    ?.get(1)
    ?.toIntOrNull()

internal fun parseTmdbPlaybackDetails(json: String, episode: Boolean): TmdbPlaybackDetails {
  val root = JSONObject(json)
  val releaseDate =
    listOf("air_date", "release_date", "first_air_date")
      .firstNotNullOfOrNull { key -> root.optString(key).trim().takeIf(String::isNotBlank) }
  val cast = buildList {
    addCastNames(root.optJSONObject("credits")?.optJSONArray("cast"))
    if (episode) addCastNames(root.optJSONArray("guest_stars"))
  }.distinct().take(6)
  val genres =
    root.optJSONArray("genres").stringValues("name").ifEmpty {
      root.optJSONObject("show")?.optJSONArray("genres").stringValues("name")
    }
  return TmdbPlaybackDetails(
    overview = root.optString("overview").trim().takeIf(String::isNotBlank),
    year = releaseDate?.takeIf { it.length >= 4 }?.take(4),
    releaseDate = releaseDate,
    runtimeMinutes = root.optInt("runtime", 0).takeIf { it > 0 },
    rating = root.optDouble("vote_average", 0.0).takeIf { it > 0.0 },
    genres = genres.take(3),
    castNames = cast,
  )
}

private fun MutableList<String>.addCastNames(items: JSONArray?) {
  if (items == null) return
  for (index in 0 until items.length()) {
    val person = items.optJSONObject(index) ?: continue
    val name = person.optString("name").trim()
    if (name.isNotBlank()) add(name)
  }
}

private fun JSONArray?.stringValues(key: String): List<String> {
  if (this == null) return emptyList()
  return buildList {
    for (index in 0 until length()) {
      optJSONObject(index)?.optString(key)?.trim()?.takeIf(String::isNotBlank)?.let(::add)
    }
  }
}
