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
  val backdropPath: String? = null,
) {
  val year: String?
    get() = releaseDate?.takeIf { it.length >= 4 }?.take(4)

  val posterUrl: String?
    get() = posterPath?.let { "https://image.tmdb.org/t/p/w500$it" }

  val backdropUrl: String?
    get() = backdropPath?.let { "https://image.tmdb.org/t/p/w780$it" } ?: posterUrl
}

/**
 * A browsable listing.
 *
 * Held as data rather than an enum because the interesting ones are not fixed endpoints: a decade
 * rail has to be worked out from the current year, and a rating rail is a set of discover
 * parameters rather than a path of its own.
 */
internal data class CatalogCategory(
  val id: String,
  val label: String,
  val moviePath: String,
  val showPath: String,
  val movieParams: Map<String, String> = emptyMap(),
  val showParams: Map<String, String> = emptyMap(),
  /**
   * Whether the heading still needs "movies" or "TV shows" after it.
   *
   * "Trending" does; "Best of the 1990s" does not, and reads badly when it gets it anyway. The tab
   * the viewer is on already says which of the two they are looking at.
   */
  val standaloneLabel: Boolean = false,
)

/**
 * Enough votes that an average means something.
 *
 * Sorting by rating without a floor returns films nobody has seen, rated ten by the four people
 * who have. Shows are held to a lower bar simply because far fewer people rate them.
 */
private const val MOVIE_VOTE_FLOOR = "1500"
private const val SHOW_VOTE_FLOOR = "300"

/** Rails that are always offered, in the order they are shown. */
internal fun catalogCategories(currentYear: Int = currentCalendarYear()): List<CatalogCategory> =
  buildList {
    add(CatalogCategory("trending", "Trending", "trending/movie/week", "trending/tv/week"))
    add(CatalogCategory("popular", "Popular", "movie/popular", "tv/popular"))
    add(
      CatalogCategory(
        id = "acclaimed",
        label = "Highly rated",
        moviePath = "discover/movie",
        showPath = "discover/tv",
        movieParams =
          mapOf(
            "sort_by" to "vote_average.desc",
            "vote_count.gte" to MOVIE_VOTE_FLOOR,
            "vote_average.gte" to "7.5",
          ),
        showParams =
          mapOf(
            "sort_by" to "vote_average.desc",
            "vote_count.gte" to SHOW_VOTE_FLOOR,
            "vote_average.gte" to "7.5",
          ),
      )
    )
    add(
      CatalogCategory(
        id = "this-year",
        label = "New in $currentYear",
        standaloneLabel = true,
        moviePath = "discover/movie",
        showPath = "discover/tv",
        movieParams =
          mapOf(
            "sort_by" to "popularity.desc",
            "primary_release_year" to currentYear.toString(),
            "vote_count.gte" to "50",
          ),
        showParams =
          mapOf(
            "sort_by" to "popularity.desc",
            "first_air_date_year" to currentYear.toString(),
            "vote_count.gte" to "20",
          ),
      )
    )
    // The decade in progress first, then back through the completed ones.
    decadeStarts(currentYear).forEach { add(decadeCategory(it, currentYear)) }
    // Top of each genre, after the broad rails so the home screen opens on discovery first.
    genreTopRatedCategories().forEach { add(it) }
  }

/**
 * TMDB movie genre ids paired with the closest TV genre where the catalogs diverge.
 *
 * TV has no Horror / Romance / Thriller slots of its own, so those rails use Mystery or Drama
 * for shows rather than asking for an id that discover/tv will ignore.
 */
internal fun genreTopRatedCategories(): List<CatalogCategory> =
  listOf(
    genreTopRatedCategory("genre-action", "Top Rated Action", movieGenreId = 28, showGenreId = 10759),
    genreTopRatedCategory("genre-comedy", "Top Rated Comedy", movieGenreId = 35),
    genreTopRatedCategory("genre-horror", "Top Rated Horror", movieGenreId = 27, showGenreId = 9648),
    genreTopRatedCategory("genre-romance", "Top Rated Romance", movieGenreId = 10749, showGenreId = 18),
    genreTopRatedCategory("genre-thriller", "Top Rated Thriller", movieGenreId = 53, showGenreId = 9648),
    genreTopRatedCategory("genre-scifi", "Top Rated Sci-Fi", movieGenreId = 878, showGenreId = 10765),
    genreTopRatedCategory("genre-animation", "Top Rated Animation", movieGenreId = 16),
    genreTopRatedCategory("genre-drama", "Top Rated Drama", movieGenreId = 18),
    genreTopRatedCategory("genre-fantasy", "Top Rated Fantasy", movieGenreId = 14, showGenreId = 10765),
    genreTopRatedCategory("genre-crime", "Top Rated Crime", movieGenreId = 80),
  )

/** Highest-rated titles in one genre, vote floor kept so obscure tens do not crowd the rail. */
internal fun genreTopRatedCategory(
  id: String,
  label: String,
  movieGenreId: Int,
  showGenreId: Int = movieGenreId,
): CatalogCategory =
  CatalogCategory(
    id = id,
    label = label,
    standaloneLabel = true,
    moviePath = "discover/movie",
    showPath = "discover/tv",
    movieParams =
      mapOf(
        "with_genres" to movieGenreId.toString(),
        "sort_by" to "vote_average.desc",
        "vote_count.gte" to MOVIE_VOTE_FLOOR,
      ),
    showParams =
      mapOf(
        "with_genres" to showGenreId.toString(),
        "sort_by" to "vote_average.desc",
        "vote_count.gte" to SHOW_VOTE_FLOOR,
      ),
  )

/** The decade now and the three before it, newest first. */
internal fun decadeStarts(currentYear: Int, count: Int = 4): List<Int> {
  val thisDecade = (currentYear / 10) * 10
  return (0 until count).map { thisDecade - it * 10 }
}

/** "Best of the 2010s", and for the decade still running, "Best of the 2020s so far". */
internal fun decadeCategory(decadeStart: Int, currentYear: Int): CatalogCategory {
  val inProgress = currentYear < decadeStart + 10
  val lastYear = if (inProgress) currentYear else decadeStart + 9
  val label = "Best of the ${decadeStart}s" + if (inProgress) " so far" else ""
  val from = "$decadeStart-01-01"
  val to = "$lastYear-12-31"
  return CatalogCategory(
    id = "decade-$decadeStart",
    label = label,
    standaloneLabel = true,
    moviePath = "discover/movie",
    showPath = "discover/tv",
    movieParams =
      mapOf(
        "sort_by" to "vote_average.desc",
        "vote_count.gte" to MOVIE_VOTE_FLOOR,
        "primary_release_date.gte" to from,
        "primary_release_date.lte" to to,
      ),
    showParams =
      mapOf(
        "sort_by" to "vote_average.desc",
        "vote_count.gte" to SHOW_VOTE_FLOOR,
        "first_air_date.gte" to from,
        "first_air_date.lte" to to,
      ),
  )
}

internal fun currentCalendarYear(): Int =
  java.util.Calendar.getInstance().get(java.util.Calendar.YEAR)

internal class TmdbMovieRepository(private val apiKey: String) {
  suspend fun movies(category: CatalogCategory): List<TmdbMovie> =
    requestMovies(path = category.moviePath, extra = category.movieParams)

  /**
   * The copy of this rail the last run left on disk, or null when there is none.
   *
   * Answered without a socket, so it goes on screen while the request that will replace it is
   * still connecting. On a slow network that is the difference between a rail of posters and a
   * blank strip for the twelve seconds the connection is allowed to take.
   */
  suspend fun storedMovies(category: CatalogCategory): List<TmdbMovie>? =
    tmdbStored(
      apiKey = apiKey,
      path = category.moviePath,
      params = movieParams(extra = category.movieParams),
      parse = ::parseTmdbMovies,
    )

  suspend fun searchMovies(query: String): List<TmdbMovie> =
    requestMovies(path = "search/movie", query = query.trim())

  /** What TMDB says goes with a title the viewer already got through. */
  suspend fun recommendations(movieId: Int): List<TmdbMovie> =
    requestMovies(path = "movie/$movieId/recommendations")

  /** Whoever is top of the billing on a title, so the catalog can offer their other work. */
  suspend fun topBilledActor(movieId: Int): TmdbActor? =
    tmdbRequest(apiKey = apiKey, path = "movie/$movieId/credits", parse = ::parseTopBilledActor)

  suspend fun moviesWithActor(personId: Int): List<TmdbMovie> =
    requestMovies(
      path = "discover/movie",
      extra =
        mapOf(
          "with_cast" to personId.toString(),
          "sort_by" to "popularity.desc",
          "vote_count.gte" to "100",
        ),
    )

  suspend fun personDetails(personId: Int): TmdbPersonDetails? =
    tmdbRequest(
      apiKey = apiKey,
      path = "person/$personId",
      parse = ::parseTmdbPersonDetails,
    )

  suspend fun personMovieCredits(
    personId: Int,
    isDirector: Boolean = false,
  ): Pair<List<TmdbMovie>, List<TmdbMovie>> =
    tmdbRequest(
      apiKey = apiKey,
      path = "person/$personId/movie_credits",
      parse = { json -> parsePersonMovieCredits(json, isDirector) },
    )

  private suspend fun requestMovies(
    path: String,
    query: String? = null,
    extra: Map<String, String> = emptyMap(),
  ): List<TmdbMovie> =
    tmdbRequest(
      apiKey = apiKey,
      path = path,
      params = movieParams(query, extra),
      parse = ::parseTmdbMovies,
    )

  /**
   * Built in one place because the cache is keyed on the whole address.
   *
   * A stored read that spelled its parameters differently from the live one would look up a URL
   * nothing had ever written, and quietly never hit.
   */
  private fun movieParams(query: String? = null, extra: Map<String, String> = emptyMap()) =
    buildMap {
      put("page", "1")
      put("include_adult", "false")
      if (query != null) put("query", query)
      putAll(extra)
    }
}

/** A billed performer, kept only so the catalog can ask what else they are in. */
internal data class TmdbActor(val id: Int, val name: String)

internal data class TmdbPersonDetails(
  val id: Int,
  val name: String,
  val biography: String?,
  val birthday: String?,
  val placeOfBirth: String?,
  val profilePath: String?,
  val knownForDepartment: String?,
  val popularity: Double,
  val gender: Int = 0,
) {
  val photoUrl: String?
    get() = profilePath?.let { "https://image.tmdb.org/t/p/w500$it" }
}

internal fun parseTmdbPersonDetails(json: String): TmdbPersonDetails {
  val root = JSONObject(json)
  return TmdbPersonDetails(
    id = root.optInt("id", -1),
    name = root.optString("name").trim(),
    biography = root.optString("biography").trim().takeIf(String::isNotBlank),
    birthday = root.optString("birthday").trim().takeIf(String::isNotBlank),
    placeOfBirth = root.optString("place_of_birth").trim().takeIf(String::isNotBlank),
    profilePath = root.optString("profile_path").trim().takeIf { it.isNotBlank() && it != "null" },
    knownForDepartment = root.optString("known_for_department").trim().takeIf(String::isNotBlank),
    popularity = root.optDouble("popularity", 0.0),
    gender = root.optInt("gender", 0),
  )
}

internal fun parsePersonMovieCredits(json: String, isDirector: Boolean): Pair<List<TmdbMovie>, List<TmdbMovie>> {
  val root = JSONObject(json)
  val arrayKey = if (isDirector) "crew" else "cast"
  val items = root.optJSONArray(arrayKey) ?: return Pair(emptyList(), emptyList())
  val movies = buildList {
    for (i in 0 until items.length()) {
      val item = items.optJSONObject(i) ?: continue
      if (isDirector) {
        val job = item.optString("job").trim()
        if (!job.equals("Director", ignoreCase = true)) continue
      }
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
          backdropPath = item.optString("backdrop_path").trim().takeIf { it.isNotBlank() && it != "null" },
        )
      )
    }
  }.distinctBy { it.id }

  val bestRated = movies.filter { it.voteAverage > 0.0 }.sortedByDescending { it.voteAverage }.take(12)
  val allMovies = movies.sortedByDescending { it.releaseDate.orEmpty() }
  return Pair(bestRated, allMovies)
}

internal fun parseTopBilledActor(json: String): TmdbActor? {
  val cast = JSONObject(json).optJSONArray("cast") ?: return null
  for (index in 0 until cast.length()) {
    val item = cast.optJSONObject(index) ?: continue
    // Acting credits only: a director topping the list would make a rail nobody expects.
    if (item.optString("known_for_department").let { it.isNotBlank() && it != "Acting" }) continue
    val id = item.optInt("id", -1)
    val name = item.optString("name").trim()
    if (id > 0 && name.isNotBlank()) return TmdbActor(id, name)
  }
  return null
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
    val url = tmdbUrl(apiKey, path, params)
    runCatching { readTmdb(url, parse) ?: throw IOException("TMDB returned no body") }
      // Nothing on the wire, so the last copy of this listing is better than an empty rail. A
      // listing a few days old still opens the right title.
      .getOrElse { error -> readTmdb(url, parse, cacheOnly = true) ?: throw error }
  }

/**
 * Whatever the response cache already holds for this endpoint, without reaching the network.
 *
 * Unlike the fallback inside [tmdbRequest], this is asked *first* rather than after a failure: a
 * stored listing shown at once and replaced a moment later reads as a fast app, where the same
 * listing shown only once the network has given up reads as a broken one. Null when nothing is
 * stored, which is the ordinary answer on a first run.
 */
internal suspend fun <T> tmdbStored(
  apiKey: String,
  path: String,
  params: Map<String, String> = emptyMap(),
  parse: (String) -> T,
): T? =
  withContext(Dispatchers.IO) {
    if (apiKey.isBlank()) return@withContext null
    runCatching { readTmdb(tmdbUrl(apiKey, path, params), parse, cacheOnly = true) }.getOrNull()
  }

private fun tmdbUrl(apiKey: String, path: String, params: Map<String, String>): String =
  Uri.Builder()
    .scheme("https")
    .authority("api.themoviedb.org")
    .appendPath("3")
    .apply { path.split('/').forEach(::appendPath) }
    .appendQueryParameter("api_key", apiKey)
    .appendQueryParameter("language", "en-US")
    .apply { params.forEach { (name, value) -> appendQueryParameter(name, value) } }
    .build()
    .toString()

private fun <T> readTmdb(url: String, parse: (String) -> T, cacheOnly: Boolean = false): T? {
  val connection = (URL(url).openConnection() as HttpURLConnection)
  try {
    connection.requestMethod = "GET"
    connection.setRequestProperty("Accept", "application/json")
    if (cacheOnly) {
      connection.setRequestProperty("Cache-Control", "only-if-cached, max-stale=$TMDB_MAX_STALE_SECONDS")
    }
    connection.connectTimeout = 12_000
    connection.readTimeout = 15_000
    val status = connection.responseCode
    // A cache-only request with nothing stored answers 504 rather than reaching the network.
    if (cacheOnly && status !in 200..299) return null
    if (status !in 200..299) throw IOException("TMDB returned HTTP $status")
    return parse(connection.inputStream.bufferedReader().use { it.readText() })
  } finally {
    connection.disconnect()
  }
}

private const val TMDB_MAX_STALE_SECONDS = 60 * 60 * 24 * 7

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
          backdropPath = item.optString("backdrop_path").trim().takeIf { it.isNotBlank() && it != "null" },
        )
      )
    }
  }
}

/**
 * The address the catalog knows a film by.
 *
 * It is the first provider's, and it stays that whichever provider ends up serving the film: watch
 * history, Continue watching and the handover to a television are all keyed on it.
 */
internal fun vidfastMovieUrl(movieId: Int): String {
  require(movieId > 0) { "A valid TMDB movie ID is required" }
  return CANONICAL_PROVIDER.movieUrl(movieId)
}
