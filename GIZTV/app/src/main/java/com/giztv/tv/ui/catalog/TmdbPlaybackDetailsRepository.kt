package com.giztv.tv.ui.catalog

import com.giztv.tv.data.PlaybackContext
import org.json.JSONArray
import org.json.JSONObject

/** One billed performer from TMDB credits, rich enough for pause trivia. */
internal data class PlaybackCastMember(
  val id: Int = -1,
  val name: String,
  val character: String? = null,
  val profilePath: String? = null,
  /** Zero-based billing order from TMDB when present. */
  val order: Int? = null,
  val knownForDepartment: String? = null,
  val guest: Boolean = false,
) {
  val photoUrl: String?
    get() = profilePath?.let { "https://image.tmdb.org/t/p/w185$it" }
}

/** Director (or co-director) from TMDB crew credits for pause tips. */
internal data class PlaybackDirector(
  val id: Int = -1,
  val name: String,
  val profilePath: String? = null,
) {
  val photoUrl: String?
    get() = profilePath?.let { "https://image.tmdb.org/t/p/w185$it" }
}

/** A TMDB user review for the pause overlay (full body; UI scrolls when tall). */
internal data class PlaybackReview(
  val id: String,
  val author: String,
  /** Full review text after light cleanup (not a short snippet). */
  val excerpt: String,
  val rating: Double? = null,
)

/** Extra title information loaded alongside stream discovery without delaying playback. */
internal data class TmdbPlaybackDetails(
  val overview: String?,
  val year: String?,
  val releaseDate: String?,
  val runtimeMinutes: Int?,
  val rating: Double?,
  val genres: List<String>,
  val cast: List<PlaybackCastMember>,
  val directors: List<PlaybackDirector> = emptyList(),
  val tagline: String? = null,
  val reviews: List<PlaybackReview> = emptyList(),
  val backdropPath: String? = null,
) {
  val castNames: List<String>
    get() = cast.map { it.name }

  val director: PlaybackDirector?
    get() = directors.firstOrNull()

  val backdropUrl: String?
    get() = backdropPath?.let { "https://image.tmdb.org/t/p/w780$it" }
}

internal class TmdbPlaybackDetailsRepository(private val apiKey: String) {
  suspend fun movieDetails(movieId: Int): TmdbPlaybackDetails? =
    runCatching {
      tmdbRequest(
        apiKey = apiKey,
        path = "movie/$movieId",
        params = mapOf("append_to_response" to "credits,reviews"),
        parse = { parseTmdbPlaybackDetails(it, episode = false) },
      )
    }.getOrNull()

  suspend fun details(playback: PlaybackContext): TmdbPlaybackDetails? {
    val path =
      if (playback.isEpisode) {
        val showId = playback.showId ?: return null
        "tv/$showId/season/${playback.seasonNumber}/episode/${playback.episodeNumber}"
      } else {
        val movieId = tmdbMovieIdFromPlaybackUrl(playback.pageUrl) ?: return null
        "movie/$movieId"
      }
    // Movies can append reviews in one hop; episode payloads do not include show reviews.
    val append = if (playback.isEpisode) "credits" else "credits,reviews"
    val parsed =
      tmdbRequest(
        apiKey = apiKey,
        path = path,
        params = mapOf("append_to_response" to append),
        parse = { parseTmdbPlaybackDetails(it, playback.isEpisode) },
      )
    val withCast =
      if (parsed.cast.isNotEmpty() || !playback.isEpisode) {
        parsed
      } else {
        // Episode credits are often thin; show-level cast still makes a useful pause tip.
        val showId = playback.showId ?: return parsed
        val showCast =
          runCatching {
              tmdbRequest(
                apiKey = apiKey,
                path = "tv/$showId/credits",
                parse = { parsePlaybackCastMembers(JSONObject(it).optJSONArray("cast"), guest = false) },
              )
            }
            .getOrDefault(emptyList())
        if (showCast.isEmpty()) parsed else parsed.copy(cast = showCast)
      }
    if (withCast.reviews.isNotEmpty() || !playback.isEpisode) return withCast
    val showId = playback.showId ?: return withCast
    val showReviews =
      runCatching {
          tmdbRequest(
            apiKey = apiKey,
            path = "tv/$showId/reviews",
            parse = { parsePlaybackReviews(JSONObject(it)) },
          )
        }
        .getOrDefault(emptyList())
    return if (showReviews.isEmpty()) withCast else withCast.copy(reviews = showReviews)
  }

  /** Cast list for pause trivia; empty when TMDB has nothing usable. */
  suspend fun castMembers(playback: PlaybackContext): List<PlaybackCastMember> =
    runCatching { details(playback)?.cast.orEmpty() }.getOrDefault(emptyList())

  /** Cast, director, facts, and reviews for the pause tip rotator. Soft-fails to empty catalog. */
  suspend fun pauseTipDetails(playback: PlaybackContext): TmdbPlaybackDetails? =
    runCatching { details(playback) }.getOrNull()
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
  val credits = root.optJSONObject("credits")
  val cast =
    buildList {
        addAll(parsePlaybackCastMembers(credits?.optJSONArray("cast"), guest = false))
        if (episode) {
          addAll(parsePlaybackCastMembers(root.optJSONArray("guest_stars"), guest = true))
        }
      }
      .distinctBy { it.name.lowercase() }
      .take(16)
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
    cast = cast,
    directors = parsePlaybackDirectors(credits?.optJSONArray("crew")),
    tagline = root.optString("tagline").trim().takeIf(String::isNotBlank),
    reviews = parsePlaybackReviews(root.optJSONObject("reviews")),
    backdropPath = root.optString("backdrop_path").trim().takeIf { it.isNotBlank() && it != "null" },
  )
}

internal fun parsePlaybackCastMembers(items: JSONArray?, guest: Boolean): List<PlaybackCastMember> {
  if (items == null) return emptyList()
  return buildList {
    for (index in 0 until items.length()) {
      val person = items.optJSONObject(index) ?: continue
      val name = person.optString("name").trim()
      if (name.isBlank()) continue
      val department = person.optString("known_for_department").trim().takeIf(String::isNotBlank)
      // Skip non-acting credits that sometimes top billing lists.
      if (department != null && !department.equals("Acting", ignoreCase = true) && !guest) continue
      val profile =
        person.optString("profile_path").trim().takeIf { it.isNotBlank() && it != "null" }
      val id = person.optInt("id", -1)
      val character = person.optString("character").trim().takeIf(String::isNotBlank)
      val order = if (person.has("order")) person.optInt("order", -1).takeIf { it >= 0 } else null
      add(
        PlaybackCastMember(
          id = id,
          name = name,
          character = character,
          profilePath = profile,
          order = order ?: index,
          knownForDepartment = department,
          guest = guest,
        ),
      )
    }
  }
}

/** Directors from TMDB crew, first-billed order preserved, de-duplicated by name. */
internal fun parsePlaybackDirectors(items: JSONArray?): List<PlaybackDirector> {
  if (items == null) return emptyList()
  return buildList {
      for (index in 0 until items.length()) {
        val person = items.optJSONObject(index) ?: continue
        val job = person.optString("job").trim()
        if (!job.equals("Director", ignoreCase = true)) continue
        val name = person.optString("name").trim()
        if (name.isBlank()) continue
        val id = person.optInt("id", -1)
        val profile =
          person.optString("profile_path").trim().takeIf { it.isNotBlank() && it != "null" }
        add(PlaybackDirector(id = id, name = name, profilePath = profile))
      }
    }
    .distinctBy { it.name.lowercase() }
    .take(3)
}

/**
 * Parses TMDB review pages (standalone or `append_to_response=reviews`).
 *
 * Soft-filters empty / tiny / non-English / low-signal blurbs. Keeps full review text for the
 * overlay (UI scrolls when content is tall).
 */
internal fun parsePlaybackReviews(reviewsRoot: JSONObject?): List<PlaybackReview> {
  val items = reviewsRoot?.optJSONArray("results") ?: return emptyList()
  val english = ArrayList<PlaybackReview>(8)
  val other = ArrayList<PlaybackReview>(4)
  for (index in 0 until items.length()) {
    val item = items.optJSONObject(index) ?: continue
    val parsed = parsePlaybackReview(item) ?: continue
    val lang = item.optString("iso_639_1").trim().lowercase()
    when {
      lang.isEmpty() || lang == "en" -> english.add(parsed)
      else -> other.add(parsed)
    }
    if (english.size >= 8) break
  }
  // Prefer English when any usable English reviews exist; otherwise soft-fall back.
  return if (english.isNotEmpty()) english.take(8) else other.take(8)
}

internal fun parsePlaybackReview(item: JSONObject): PlaybackReview? {
  val id = item.optString("id").trim().takeIf(String::isNotBlank) ?: return null
  val authorDetails = item.optJSONObject("author_details")
  val author =
    listOf(
        item.optString("author").trim(),
        authorDetails?.optString("name").orEmpty().trim(),
        authorDetails?.optString("username").orEmpty().trim(),
      )
      .firstOrNull { it.isNotBlank() }
      ?: return null
  val excerpt = pauseReviewExcerpt(item.optString("content")) ?: return null
  val rating =
    when {
      authorDetails == null || authorDetails.isNull("rating") -> null
      else -> authorDetails.optDouble("rating", Double.NaN).takeIf { !it.isNaN() && it > 0.0 }
    }
  return PlaybackReview(id = id, author = author, excerpt = excerpt, rating = rating)
}

/**
 * Normalized review body for the pause tip; null when empty, tiny, or low-signal.
 *
 * Keeps the full text for display. Only soft-caps extreme novel-length payloads at a word
 * boundary (no mid-sentence "…" ellipsis) so memory stays bounded.
 */
internal fun pauseReviewExcerpt(raw: String?, maxLen: Int = REVIEW_BODY_MAX_LEN): String? {
  val cleaned = normalizeReviewContent(raw) ?: return null
  if (cleaned.length < MIN_REVIEW_CONTENT_LENGTH) return null
  if (looksLikeLowQualityReview(cleaned)) return null
  if (cleaned.length <= maxLen) return cleaned
  return cleaned
    .take(maxLen)
    .substringBeforeLast(' ')
    .trimEnd(',', ';', ':', '-', '—', ' ', '.')
    .takeIf { it.length >= MIN_REVIEW_CONTENT_LENGTH }
}

internal fun normalizeReviewContent(raw: String?): String? {
  val text =
    raw
      ?.replace("\r\n", "\n")
      ?.replace('\r', '\n')
      ?.replace(Regex("\\*\\*|__"), "")
      ?.replace(Regex("[*_`]"), "")
      ?.replace(Regex("<[^>]+>"), " ")
      ?.replace(Regex("[ \\t\\x0B\\f]+"), " ")
      ?.replace(Regex(" *\n *"), "\n")
      ?.replace(Regex("\n{3,}"), "\n\n")
      ?.trim()
      ?.trim('"', '\'', '“', '”', '«', '»')
      ?.takeIf { it.isNotBlank() }
      ?: return null
  return text
}

/** Heuristic for spammy / shouty / non-prose review blurbs. */
internal fun looksLikeLowQualityReview(line: String): Boolean {
  val trimmed = line.trim()
  if (trimmed.length < MIN_REVIEW_CONTENT_LENGTH) return true
  val letters = trimmed.filter { it.isLetter() }
  if (letters.isEmpty()) return true
  if (letters.all { it.isUpperCase() } && trimmed.length <= 80) return true
  val upperRatio = letters.count { it.isUpperCase() }.toDouble() / letters.length
  if (upperRatio > 0.78 && trimmed.length <= 90) return true
  // Tiny all-emoji / symbol blobs after letter filter already handled; reject few-word hype.
  if (trimmed.split(Regex("\\s+")).size < 6 && trimmed.length < 60) return true
  return false
}

private fun JSONArray?.stringValues(key: String): List<String> {
  if (this == null) return emptyList()
  return buildList {
    for (index in 0 until length()) {
      optJSONObject(index)?.optString(key)?.trim()?.takeIf(String::isNotBlank)?.let(::add)
    }
  }
}

internal const val MIN_REVIEW_CONTENT_LENGTH = 48
/** Soft upper bound for a single review body (UI scrolls; avoids storing novel-length blobs). */
internal const val REVIEW_BODY_MAX_LEN = 4_000
/** @deprecated Use [REVIEW_BODY_MAX_LEN]; kept so older call sites compile during transition. */
@Deprecated("Use REVIEW_BODY_MAX_LEN", ReplaceWith("REVIEW_BODY_MAX_LEN"))
internal const val REVIEW_EXCERPT_MAX_LEN = REVIEW_BODY_MAX_LEN
