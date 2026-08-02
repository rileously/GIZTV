package com.example.auroratv.ui.catalog

import android.net.Uri
import java.io.IOException
import java.net.HttpURLConnection
import java.net.URL
import kotlinx.coroutines.Dispatchers
import kotlinx.coroutines.withContext
import org.json.JSONObject

internal data class TmdbMovie(
  val id: Int,
  val title: String,
  val releaseDate: String?,
  val voteAverage: Double,
  val overview: String,
  val posterPath: String?,
) {
  val year: String?
    get() = releaseDate?.takeIf { it.length >= 4 }?.take(4)

  val posterUrl: String?
    get() = posterPath?.let { "https://image.tmdb.org/t/p/w500$it" }
}

/** The browsable listings offered for each tab. */
internal enum class CatalogCategory(val label: String, val moviePath: String, val showPath: String) {
  POPULAR("Popular", "movie/popular", "tv/popular"),
  TRENDING("Trending", "trending/movie/week", "trending/tv/week"),
  TOP_RATED("Top rated", "movie/top_rated", "tv/top_rated"),
}

internal class TmdbMovieRepository(private val apiKey: String) {
  suspend fun movies(category: CatalogCategory): List<TmdbMovie> = requestMovies(path = category.moviePath)

  suspend fun searchMovies(query: String): List<TmdbMovie> =
    requestMovies(path = "search/movie", query = query.trim())

  private suspend fun requestMovies(path: String, query: String? = null): List<TmdbMovie> =
    tmdbRequest(
      apiKey = apiKey,
      path = path,
      params =
        buildMap {
          put("page", "1")
          if (query != null) {
            put("query", query)
            put("include_adult", "false")
          }
        },
      parse = ::parseTmdbMovies,
    )
}

/** Fetches a TMDB endpoint off the main thread and parses it with [parse]. */
internal suspend fun <T> tmdbRequest(
  apiKey: String,
  path: String,
  params: Map<String, String> = emptyMap(),
  parse: (String) -> T,
): T =
  withContext(Dispatchers.IO) {
    if (apiKey.isBlank()) throw IOException("TMDB API key is not configured")
    val uri =
      Uri.Builder()
        .scheme("https")
        .authority("api.themoviedb.org")
        .appendPath("3")
        .apply { path.split('/').forEach(::appendPath) }
        .appendQueryParameter("api_key", apiKey)
        .appendQueryParameter("language", "en-US")
        .apply { params.forEach { (name, value) -> appendQueryParameter(name, value) } }
        .build()

    val connection = (URL(uri.toString()).openConnection() as HttpURLConnection)
    try {
      connection.requestMethod = "GET"
      connection.setRequestProperty("Accept", "application/json")
      connection.connectTimeout = 12_000
      connection.readTimeout = 15_000
      val status = connection.responseCode
      if (status !in 200..299) throw IOException("TMDB returned HTTP $status")
      parse(connection.inputStream.bufferedReader().use { it.readText() })
    } finally {
      connection.disconnect()
    }
  }

internal fun parseTmdbMovies(json: String): List<TmdbMovie> {
  val results = JSONObject(json).optJSONArray("results") ?: return emptyList()
  return buildList {
    for (index in 0 until results.length()) {
      val item = results.optJSONObject(index) ?: continue
      val id = item.optInt("id", -1)
      val title = item.optString("title").trim()
      if (id <= 0 || title.isBlank()) continue
      add(
        TmdbMovie(
          id = id,
          title = title,
          releaseDate = item.optString("release_date").trim().takeIf(String::isNotBlank),
          voteAverage = item.optDouble("vote_average", 0.0),
          overview = item.optString("overview").trim(),
          posterPath = item.optString("poster_path").trim().takeIf { it.isNotBlank() && it != "null" },
        )
      )
    }
  }
}

internal fun vidfastMovieUrl(movieId: Int): String {
  require(movieId > 0) { "A valid TMDB movie ID is required" }
  return "https://vidfast.vc/movie/$movieId?autoPlay=true&sub=en&chromecast=false"
}
