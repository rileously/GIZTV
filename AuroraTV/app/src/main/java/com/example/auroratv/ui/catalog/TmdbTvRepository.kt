package com.example.auroratv.ui.catalog

import org.json.JSONObject

internal data class TmdbShow(
  val id: Int,
  val name: String,
  val firstAirDate: String?,
  val voteAverage: Double,
  val overview: String,
  val posterPath: String?,
) {
  val year: String?
    get() = firstAirDate?.takeIf { it.length >= 4 }?.take(4)

  val posterUrl: String?
    get() = posterPath?.let { "https://image.tmdb.org/t/p/w500$it" }
}

internal data class TmdbSeason(
  val seasonNumber: Int,
  val name: String,
  val episodeCount: Int,
  val overview: String,
  val airDate: String?,
) {
  val year: String?
    get() = airDate?.takeIf { it.length >= 4 }?.take(4)

  /** Short label for the season rail, e.g. "S1" or "SP" for specials. */
  val shortLabel: String
    get() = if (seasonNumber == 0) "SP" else "S$seasonNumber"
}

internal data class TmdbEpisode(
  val seasonNumber: Int,
  val episodeNumber: Int,
  val name: String,
  val overview: String,
  val stillPath: String?,
  val runtimeMinutes: Int?,
  val airDate: String?,
  val voteAverage: Double,
) {
  val stillUrl: String?
    get() = stillPath?.let { "https://image.tmdb.org/t/p/w300$it" }

  val runtimeLabel: String?
    get() = runtimeMinutes?.takeIf { it > 0 }?.let { "$it min" }
}

internal class TmdbTvRepository(private val apiKey: String) {
  suspend fun shows(category: CatalogCategory): List<TmdbShow> =
    tmdbRequest(apiKey, category.showPath, mapOf("page" to "1"), ::parseTmdbShows)

  /** What TMDB says goes with a show the viewer already got through. */
  suspend fun recommendations(showId: Int): List<TmdbShow> =
    tmdbRequest(apiKey, "tv/$showId/recommendations", mapOf("page" to "1"), ::parseTmdbShows)

  suspend fun searchShows(query: String): List<TmdbShow> =
    tmdbRequest(
      apiKey = apiKey,
      path = "search/tv",
      params = mapOf("page" to "1", "query" to query.trim(), "include_adult" to "false"),
      parse = ::parseTmdbShows,
    )

  suspend fun seasons(showId: Int): List<TmdbSeason> =
    tmdbRequest(apiKey, "tv/$showId", parse = ::parseTmdbSeasons)

  suspend fun episodes(showId: Int, seasonNumber: Int): List<TmdbEpisode> =
    tmdbRequest(apiKey, "tv/$showId/season/$seasonNumber", parse = ::parseTmdbEpisodes)
}

internal fun parseTmdbShows(json: String): List<TmdbShow> {
  val results = JSONObject(json).optJSONArray("results") ?: return emptyList()
  return buildList {
    for (index in 0 until results.length()) {
      val item = results.optJSONObject(index) ?: continue
      val id = item.optInt("id", -1)
      val name = item.optString("name").trim()
      if (id <= 0 || name.isBlank()) continue
      add(
        TmdbShow(
          id = id,
          name = name,
          firstAirDate = item.optString("first_air_date").trim().takeIf(String::isNotBlank),
          voteAverage = item.optDouble("vote_average", 0.0),
          overview = item.optString("overview").trim(),
          posterPath = item.optString("poster_path").trim().takeIf { it.isNotBlank() && it != "null" },
        )
      )
    }
  }
}

internal fun parseTmdbSeasons(json: String): List<TmdbSeason> {
  val seasons = JSONObject(json).optJSONArray("seasons") ?: return emptyList()
  return buildList {
    for (index in 0 until seasons.length()) {
      val item = seasons.optJSONObject(index) ?: continue
      val seasonNumber = item.optInt("season_number", -1)
      val episodeCount = item.optInt("episode_count", 0)
      // Announced-but-empty seasons cannot be played, so they never reach the season rail.
      if (seasonNumber < 0 || episodeCount <= 0) continue
      add(
        TmdbSeason(
          seasonNumber = seasonNumber,
          name = item.optString("name").trim().takeIf(String::isNotBlank) ?: "Season $seasonNumber",
          episodeCount = episodeCount,
          overview = item.optString("overview").trim(),
          airDate = item.optString("air_date").trim().takeIf { it.isNotBlank() && it != "null" },
        )
      )
    }
  }
    // Specials are season 0 on TMDB but belong after the numbered seasons.
    .sortedWith(compareBy({ it.seasonNumber == 0 }, { it.seasonNumber }))
}

internal fun parseTmdbEpisodes(json: String): List<TmdbEpisode> {
  val root = JSONObject(json)
  val episodes = root.optJSONArray("episodes") ?: return emptyList()
  val seasonNumber = root.optInt("season_number", 0)
  return buildList {
    for (index in 0 until episodes.length()) {
      val item = episodes.optJSONObject(index) ?: continue
      val episodeNumber = item.optInt("episode_number", -1)
      if (episodeNumber <= 0) continue
      val name = item.optString("name").trim().takeIf(String::isNotBlank) ?: "Episode $episodeNumber"
      add(
        TmdbEpisode(
          seasonNumber = item.optInt("season_number", seasonNumber),
          episodeNumber = episodeNumber,
          name = name,
          overview = item.optString("overview").trim(),
          stillPath = item.optString("still_path").trim().takeIf { it.isNotBlank() && it != "null" },
          runtimeMinutes = item.optInt("runtime", 0).takeIf { it > 0 },
          airDate = item.optString("air_date").trim().takeIf { it.isNotBlank() && it != "null" },
          voteAverage = item.optDouble("vote_average", 0.0),
        )
      )
    }
  }
    .sortedBy { it.episodeNumber }
}

internal fun vidfastEpisodeUrl(showId: Int, seasonNumber: Int, episodeNumber: Int): String {
  require(showId > 0) { "A valid TMDB show ID is required" }
  require(seasonNumber >= 0) { "A valid season number is required" }
  require(episodeNumber > 0) { "A valid episode number is required" }
  return "https://vidfast.vc/tv/$showId/$seasonNumber/$episodeNumber" +
    "?autoPlay=true&sub=en&chromecast=false"
}
