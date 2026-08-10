package com.example.auroratv.ui.catalog

import com.example.auroratv.data.WatchHistoryEntry

/** Titles watched past this are a real opinion rather than a look at the first minute. */
private const val MEANINGFUL_PROGRESS = .2f
private const val MAX_SEEDS = 3
internal const val RECOMMENDATION_LIMIT = 20

/** A title the viewer got through, used to ask TMDB what goes with it. */
internal data class RecommendationSeed(val id: Int, val title: String)

/**
 * What to base recommendations on, most recently watched first.
 *
 * A title someone abandoned two minutes in says nothing worth acting on, and short dramas are
 * their own page with their own catalogue, so neither is asked about. Only a few seeds are taken:
 * one request each, and a rail built from ten different tastes is a rail about nobody.
 */
internal fun recommendationSeeds(
  entries: List<WatchHistoryEntry>,
  forShows: Boolean,
  limit: Int = MAX_SEEDS,
): List<RecommendationSeed> =
  entries
    .asSequence()
    .filter { !it.shortForm }
    .filter { it.completed || it.progressFraction >= MEANINGFUL_PROGRESS }
    .sortedByDescending { it.updatedAtMs }
    .mapNotNull { entry ->
      val id = if (forShows) entry.showId else tmdbMovieIdFromPageUrl(entry.pageUrl)
      id?.takeIf { it > 0 }?.let { RecommendationSeed(it, entry.title) }
    }
    .distinctBy { it.id }
    .take(limit)
    .toList()

/**
 * The movie a catalog page is for.
 *
 * Watch history is keyed by page URL rather than by TMDB id, and the id is right there in the page
 * the catalog built, so nothing extra has to be stored to find it again.
 */
internal fun tmdbMovieIdFromPageUrl(pageUrl: String): Int? {
  val path = pageUrl.substringBefore('#').substringBefore('?')
  val segments = path.split('/').filter { it.isNotBlank() }
  val movieIndex = segments.indexOf("movie").takeIf { it >= 0 } ?: return null
  return segments.getOrNull(movieIndex + 1)?.toIntOrNull()
}

/**
 * One rail out of several answers.
 *
 * Recommendations from different seeds overlap heavily — that overlap is the strongest signal
 * there is, so a title suggested twice is kept once and kept early. Anything already watched is
 * dropped: it is a rail for what to watch next.
 */
internal fun <T> mergeRecommendations(
  answers: List<List<T>>,
  id: (T) -> Int,
  watchedIds: Set<Int>,
  limit: Int = RECOMMENDATION_LIMIT,
): List<T> {
  val timesSuggested = mutableMapOf<Int, Int>()
  answers.forEach { answer ->
    answer.distinctBy(id).forEach { item ->
      val itemId = id(item)
      timesSuggested[itemId] = (timesSuggested[itemId] ?: 0) + 1
    }
  }
  return answers
    .flatten()
    .distinctBy(id)
    .filterNot { id(it) in watchedIds }
    .sortedByDescending { timesSuggested[id(it)] ?: 0 }
    .take(limit)
}

/** What to call the rail, which depends on whether it is about one title or a taste. */
internal fun recommendationHeading(seeds: List<RecommendationSeed>): String =
  when (seeds.size) {
    0 -> "For you"
    1 -> "Because you watched ${seeds.first().title}"
    else -> "For you"
  }
