package com.example.auroratv.ui.catalog

/**
 * A site that will play a TMDB title in an embedded player.
 *
 * They all take the same two identifiers and differ only in how they spell the address, so a title
 * that one of them cannot serve today can be asked of the next one without the catalog knowing or
 * caring which is answering.
 */
internal data class StreamProvider(
  val id: String,
  val label: String,
  val host: String,
  val movieUrl: (movieId: Int) -> String,
  val episodeUrl: (showId: Int, seasonNumber: Int, episodeNumber: Int) -> String,
)

/**
 * Tried in order; the first that produces a playable stream wins.
 *
 * vidrock leads because it hands out an HLS playlist with a full set of subtitle tracks, which
 * gives the player real quality options to adapt between. vidfast currently serves a single
 * progressive file: one quality, no subtitles of its own, nothing to adapt.
 */
internal val STREAM_PROVIDERS: List<StreamProvider> =
  listOf(
    StreamProvider(
      id = "vidrock",
      label = "VidRock",
      host = "vidrock.ru",
      movieUrl = { "https://vidrock.ru/movie/$it" },
      episodeUrl = { show, season, episode -> "https://vidrock.ru/tv/$show/$season/$episode" },
    ),
    StreamProvider(
      id = "vidfast",
      label = "VidFast",
      host = "vidfast.vc",
      movieUrl = { "https://vidfast.vc/movie/$it?autoPlay=true&sub=en&chromecast=false" },
      episodeUrl = { show, season, episode ->
        "https://vidfast.vc/tv/$show/$season/$episode?autoPlay=true&sub=en&chromecast=false"
      },
    ),
    StreamProvider(
      id = "vidlink",
      label = "VidLink",
      host = "vidlink.pro",
      // title=false keeps the site's own overlay off a picture this app draws its own controls on.
      movieUrl = { "https://vidlink.pro/movie/$it?autoplay=true&title=false" },
      episodeUrl = { show, season, episode ->
        "https://vidlink.pro/tv/$show/$season/$episode?autoplay=true&title=false"
      },
    ),
    StreamProvider(
      id = "vsembed",
      label = "VidSrc",
      host = "vsembed.ru",
      movieUrl = { "https://vsembed.ru/embed/movie/$it?autoplay=1&ds_lang=en" },
      episodeUrl = { show, season, episode ->
        "https://vsembed.ru/embed/tv/$show/$season/$episode?autoplay=1&ds_lang=en"
      },
    ),
  )

/** Every host the resolver may legitimately end up on, for the checks that gate on one. */
internal val STREAM_PROVIDER_HOSTS: List<String> = STREAM_PROVIDERS.map(StreamProvider::host)

/**
 * The provider whose addresses a title is remembered by.
 *
 * Deliberately named rather than taken from the head of the list. Watch history, Continue watching
 * and the handover to a television are all keyed on the address a title was first opened from, so
 * this must not move when the resolution order is reconsidered — reordering [STREAM_PROVIDERS]
 * should be free, and it stops being free the moment identity rides on position.
 */
internal val CANONICAL_PROVIDER: StreamProvider = STREAM_PROVIDERS.first { it.id == "vidfast" }

/** What is actually being asked for, independent of who is being asked. */
internal data class CatalogTarget(
  val tmdbId: Int,
  val seasonNumber: Int? = null,
  val episodeNumber: Int? = null,
) {
  val isEpisode: Boolean get() = seasonNumber != null && episodeNumber != null
}

private val MOVIE_PATH = Regex("/(?:embed/)?movie/(\\d+)", RegexOption.IGNORE_CASE)
private val EPISODE_PATH = Regex("/(?:embed/)?tv/(\\d+)/(\\d+)/(\\d+)", RegexOption.IGNORE_CASE)

/**
 * Reads a provider address back into the title it stands for.
 *
 * Watch history, Continue watching and the handover to a television are all keyed on the address a
 * title was first opened from. Rotating providers must therefore not rotate that address: the
 * catalog keeps handing out the same canonical one, and this turns it back into a title whenever a
 * different provider needs to be asked the same question.
 */
internal fun catalogTargetOf(pageUrl: String): CatalogTarget? {
  val path = pageUrl.substringBefore('#').substringBefore('?')
  EPISODE_PATH.find(path)?.let { match ->
    val (show, season, episode) = match.destructured
    val showId = show.toIntOrNull() ?: return null
    return CatalogTarget(showId, season.toIntOrNull() ?: return null, episode.toIntOrNull() ?: return null)
  }
  MOVIE_PATH.find(path)?.let { match ->
    return CatalogTarget(match.groupValues[1].toIntOrNull() ?: return null)
  }
  return null
}

/** The address to try on a given attempt, or null once every provider has been asked. */
internal fun providerPageUrl(target: CatalogTarget, attempt: Int): String? {
  val provider = STREAM_PROVIDERS.getOrNull(attempt) ?: return null
  return if (target.isEpisode) {
    provider.episodeUrl(target.tmdbId, requireNotNull(target.seasonNumber), requireNotNull(target.episodeNumber))
  } else {
    provider.movieUrl(target.tmdbId)
  }
}

/**
 * The next provider to try after one has failed, given the address the catalog knows this title by.
 *
 * Returns null when the title cannot be read as a catalog one, or when the list is exhausted —
 * both cases mean there is nothing further to try automatically.
 */
internal fun nextProviderPageUrl(canonicalPageUrl: String, attempt: Int): String? {
  val target = catalogTargetOf(canonicalPageUrl) ?: return null
  return providerPageUrl(target, attempt)
}

/** How many automatic attempts a catalog title is worth: one per provider. */
internal val STREAM_PROVIDER_COUNT: Int = STREAM_PROVIDERS.size
