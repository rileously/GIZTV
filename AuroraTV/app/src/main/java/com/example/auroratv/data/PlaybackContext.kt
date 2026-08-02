package com.example.auroratv.data

/** One playable item in a season, kept in memory so the player can advance without TMDB. */
internal data class PlaylistEntry(
  val episodeNumber: Int,
  val name: String,
  val pageUrl: String,
  val overview: String? = null,
  val runtimeMinutes: Int? = null,
  val airDate: String? = null,
  val rating: Double? = null,
)

/**
 * What the catalog knows about the thing being played.
 *
 * The browser only ever discovers a bare stream URL, so this rides alongside the request to give
 * the player a title, artwork, and — for episodes — the rest of the season to roll into.
 */
internal data class PlaybackContext(
  val pageUrl: String,
  val title: String,
  val subtitle: String? = null,
  val posterUrl: String? = null,
  val year: String? = null,
  val overview: String? = null,
  val runtimeMinutes: Int? = null,
  val airDate: String? = null,
  val rating: Double? = null,
  val genres: List<String> = emptyList(),
  val castNames: List<String> = emptyList(),
  val showId: Int? = null,
  val seasonNumber: Int? = null,
  val episodeNumber: Int? = null,
  val playlist: List<PlaylistEntry> = emptyList(),
) {
  val isEpisode: Boolean
    get() = seasonNumber != null && episodeNumber != null

  private val currentIndex: Int
    get() = playlist.indexOfFirst { it.pageUrl == pageUrl }

  /** The next episode in the same season, or null at the end of it. */
  val nextEntry: PlaylistEntry?
    get() = currentIndex.takeIf { it >= 0 }?.let { playlist.getOrNull(it + 1) }

  val nextLabel: String?
    get() = nextEntry?.let { "E${it.episodeNumber} · ${it.name}" }

  /** Rebuilds this context around [entry] so the chain keeps working episode after episode. */
  fun advanceTo(entry: PlaylistEntry): PlaybackContext =
    copy(
      pageUrl = entry.pageUrl,
      subtitle = "S$seasonNumber · E${entry.episodeNumber} · ${entry.name}",
      overview = entry.overview,
      runtimeMinutes = entry.runtimeMinutes,
      airDate = entry.airDate,
      rating = entry.rating,
      genres = emptyList(),
      castNames = emptyList(),
      episodeNumber = entry.episodeNumber,
    )
}
