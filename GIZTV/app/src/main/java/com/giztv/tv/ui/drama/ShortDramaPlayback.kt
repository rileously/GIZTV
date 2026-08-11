package com.giztv.tv.ui.drama

import com.giztv.tv.data.PlaybackContext
import com.giztv.tv.data.PlaylistEntry
import com.giztv.tv.data.ReelTitle
import com.giztv.tv.data.WatchHistoryEntry

/**
 * Turning a drama from the listing into something the player can hold.
 *
 * The listing is the only thing that knows what the titles either side of one are, so the run being
 * browsed travels with the drama that was opened out of it. That is what lets a swipe in the player
 * reach the next drama without coming back here for it.
 */
internal fun ShortDrama.toReelTitle(): ReelTitle =
  ReelTitle(
    id = slug,
    title = title,
    posterUrl = coverUrl,
    overview = synopsis.takeIf(String::isNotBlank),
    genres = tags,
    episodeCount = episodeCount,
  )

/**
 * The reel a drama sits in, with the drama itself guaranteed to be part of it.
 *
 * A drama reached from somewhere other than the grid it appears in — a resumed episode, say — would
 * otherwise be missing from its own reel, and a reel that does not contain what is playing has no
 * next title to offer.
 */
internal fun reelAround(drama: ShortDrama, listing: List<ShortDrama>): List<ReelTitle> {
  val titles = listing.map(ShortDrama::toReelTitle)
  return if (titles.any { it.id == drama.slug }) titles else listOf(drama.toReelTitle()) + titles
}

/**
 * Everything the player needs for one episode of a short drama.
 *
 * The whole run is listed up front rather than fetched: chartdrama numbers its episodes straight
 * through from one, and the listing already said how far the drama has got, so every episode's
 * address is known without asking anything.
 */
internal fun ReelTitle.shortDramaPlayback(
  reel: List<ReelTitle>,
  episode: Int = 1,
): PlaybackContext {
  val lastEpisode = episodeCount.coerceAtLeast(1)
  val number = episode.coerceIn(1, lastEpisode)
  return PlaybackContext(
    pageUrl = chartDramaEpisodeUrl(id, number),
    title = title,
    subtitle = "Episode $number",
    posterUrl = posterUrl,
    overview = overview,
    genres = genres,
    // A chartdrama slug is not a TMDB id, so it never becomes a showId; short dramas have no
    // seasons either, which is why only the episode number is carried.
    episodeNumber = number,
    playlist =
      (1..lastEpisode).map {
        PlaylistEntry(episodeNumber = it, name = "Episode $it", pageUrl = chartDramaEpisodeUrl(id, it))
      },
    shortForm = true,
    reel = reel,
    reelId = id,
  )
}

/**
 * The episode a drama should open on, given what has already been watched of it.
 *
 * A reel swipe onto something half-watched should carry on from where it was left, not start the
 * run again — the whole point of the gesture is that it costs nothing, and being put back at
 * episode one costs the viewer everything they had got through. An episode that was finished hands
 * over to the one after it; one that was stopped part way is returned to, and the player's own
 * saved position takes it from there.
 *
 * [history] is expected most-recent-first, as [com.giztv.tv.data.WatchHistoryStore.all] returns it.
 */
internal fun resumeEpisodeFor(
  slug: String,
  episodeCount: Int,
  history: List<WatchHistoryEntry>,
): Int {
  val lastEpisode = episodeCount.coerceAtLeast(1)
  val latest =
    history.firstOrNull { entry ->
      entry.shortForm && entry.episodeNumber != null && chartDramaSlugOf(entry.pageUrl) == slug
    } ?: return 1
  val episode = latest.episodeNumber ?: return 1
  return (if (latest.completed) episode + 1 else episode).coerceIn(1, lastEpisode)
}

private val chartDramaSlugPattern = Regex("""/p/([^?#]+)""")

/** The drama an episode address belongs to, or null when the address is not one of chartdrama's. */
internal fun chartDramaSlugOf(pageUrl: String): String? {
  if (!pageUrl.startsWith(CHART_DRAMA_ORIGIN)) return null
  return chartDramaSlugPattern.find(pageUrl)?.groupValues?.get(1)?.trim('/')?.takeIf(String::isNotBlank)
}
