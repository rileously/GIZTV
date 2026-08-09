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
 * Ordered by what they were measured doing, not by what they advertise.
 *
 * vidrock leads: it hands out an HLS playlist with a full set of subtitle tracks, so the player has
 * real renditions to adapt between. It spent a release demoted for appearing to fail on every
 * title; it was not failing, the resolver was taking the placeholder its player loads before it
 * has resolved anything and discarding the real playlist that followed. See `isDecoyMediaUrl`.
 *
 * cinesrc sits second, in the slot vidlink held. It is the only one whose episode address carries
 * the season and episode as query parameters rather than path segments, which is why the readers
 * below accept both shapes.
 *
 * vidfast trails on measured resolution time, not reliability. It serves either shape depending on
 * the title — a `master.m3u8` from moon.ironwallnet.net for some, a single progressive file for
 * others, sometimes 2160p with no ladder to adapt down — so neither shape can be assumed of it. The
 * player caps oversized progressive filenames (see preferProgressivePlaybackUrl) and gives those a
 * shorter buffer profile so one huge file does not stall the way an HLS cushion would.
 *
 * Its page used to spend around fifteen seconds working before it emitted any address, and most of
 * that was not the page: the resolver refused the HTTP cache outright, so every attempt fetched its
 * entire script bundle again before it could begin. That was the wait a fast connection could not
 * shorten. Resolving now reads ordinary cache rules and falls back to the network on any attempt
 * that finds nothing, and what it gives up is remembered per site so choosing SR3 twice only costs
 * the search once.
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
      id = "cinesrc",
      label = "CineSrc",
      host = "cinesrc.st",
      // Its own page documents the movie form as an /embed/ path taking a bare TMDB id, and nothing
      // else: no autoplay or overlay parameters are offered, and inventing some only risks a 404.
      movieUrl = { "https://cinesrc.st/embed/movie/$it" },
      // Episodes are the one shape here that does not spell the season and episode into the path.
      // Measured: the three-segment form every other provider uses 404s, `/embed/tv/{id}` alone
      // answers, and the two parameters below come back rendered into the page.
      episodeUrl = { show, season, episode ->
        "https://cinesrc.st/embed/tv/$show?season=$season&episode=$episode"
      },
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
  )

/** The provider serving a page, or null for anything outside the list. */
internal fun providerFor(pageUrl: String?): StreamProvider? =
  providerIndexOf(pageUrl)?.let(STREAM_PROVIDERS::get)

/** The site a page belongs to, in the form a per-provider cache entry is keyed by. */
internal fun providerIdOf(pageUrl: String?): String? = providerFor(pageUrl)?.id


// VidSrc (vsembed.ru) is deliberately absent. Its player is a nested iframe that assembles the
// stream address inside the page from an obfuscated payload, next to a script whose whole job is
// to stop that being observed. Measured against it, the page loads and nothing follows: no address
// is ever requested, so there is nothing for the resolver to find. Left in the list it would only
// add a dead attempt to the end of every failover. Its URL shapes are in the git history if the
// site ever changes.

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
private val SHOW_PATH = Regex("/(?:embed/)?tv/(\\d+)", RegexOption.IGNORE_CASE)
// Anchored on a parameter boundary rather than a bare name, so `season=` is not found inside
// something like `preseason=`. The query has already had its leading `?` removed, so the start of
// the string counts as one.
private val SEASON_QUERY = Regex("(?:^|[?&])season=(\\d+)", RegexOption.IGNORE_CASE)
private val EPISODE_QUERY = Regex("(?:^|[?&])episode=(\\d+)", RegexOption.IGNORE_CASE)

/**
 * Reads a provider address back into the title it stands for.
 *
 * Watch history, Continue watching and the handover to a television are all keyed on the address a
 * title was first opened from. Rotating providers must therefore not rotate that address: the
 * catalog keeps handing out the same canonical one, and this turns it back into a title whenever a
 * different provider needs to be asked the same question.
 *
 * Both episode shapes are read, because cinesrc puts the season and episode in the query string
 * where the others put them in the path. Only the path form is ever the canonical address, so this
 * matters for reading a provider's own address rather than for anything keyed on one.
 */
internal fun catalogTargetOf(pageUrl: String): CatalogTarget? {
  val withoutFragment = pageUrl.substringBefore('#')
  val path = withoutFragment.substringBefore('?')
  EPISODE_PATH.find(path)?.let { match ->
    val (show, season, episode) = match.destructured
    val showId = show.toIntOrNull() ?: return null
    return CatalogTarget(showId, season.toIntOrNull() ?: return null, episode.toIntOrNull() ?: return null)
  }
  MOVIE_PATH.find(path)?.let { match ->
    return CatalogTarget(match.groupValues[1].toIntOrNull() ?: return null)
  }
  // A show address with its season and episode alongside rather than inside it. A bare show address
  // with neither is not an episode and cannot be played, so it is not a target.
  SHOW_PATH.find(path)?.let { match ->
    val showId = match.groupValues[1].toIntOrNull() ?: return null
    val query = withoutFragment.substringAfter('?', "")
    val season = SEASON_QUERY.find(query)?.groupValues?.get(1)?.toIntOrNull() ?: return null
    val episode = EPISODE_QUERY.find(query)?.groupValues?.get(1)?.toIntOrNull() ?: return null
    return CatalogTarget(showId, season, episode)
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

/** Which provider an address belongs to, or null for anything outside the list. */
internal fun providerIndexOf(pageUrl: String?): Int? {
  val url = pageUrl ?: return null
  val host =
    url.substringAfter("://", "").substringBefore('/').substringBefore(':').lowercase()
  if (host.isEmpty()) return null
  val index = STREAM_PROVIDERS.indexOfFirst { host == it.host || host.endsWith(".${it.host}") }
  return index.takeIf { it >= 0 }
}

/**
 * What to call a provider on screen.
 *
 * Numbered rather than named: which site is serving a title is the useful thing to know when one
 * of them is having a bad day, and the number says where it sits in the running order without
 * putting a brand in front of the viewer.
 */
internal fun serverLabel(index: Int): String = "SR${index + 1}"

/** The label for whichever provider served this address, or null if it was not one of ours. */
internal fun serverLabelFor(pageUrl: String?): String? = providerIndexOf(pageUrl)?.let(::serverLabel)

/**
 * One entry in the player's Server picker.
 *
 * [index] is either a [STREAM_PROVIDERS] position or an IPTV backup-link position — whichever list
 * [playbackServerOptions] built the choice from.
 */
internal data class PlaybackServerOption(val index: Int, val label: String)

/**
 * Servers the viewer can ask for by hand while a title is playing.
 *
 * Catalog titles expose every measured provider (SR1…). IPTV channels expose every backup link
 * when there is more than one. Everything else — sports pages, short dramas, a bare browser find —
 * has nothing further to try, so the control stays hidden.
 */
internal fun playbackServerOptions(
  catalogPageUrl: String?,
  sourceCount: Int,
): List<PlaybackServerOption> {
  if (sourceCount > 1) {
    return (0 until sourceCount).map { index ->
      PlaybackServerOption(index = index, label = "Link ${index + 1}")
    }
  }
  if (catalogPageUrl == null || catalogTargetOf(catalogPageUrl) == null) return emptyList()
  return STREAM_PROVIDERS.indices.map { index ->
    PlaybackServerOption(index = index, label = serverLabel(index))
  }
}

/** Which picker entry matches what is playing now. */
internal fun selectedPlaybackServerIndex(
  sourcePageUrl: String?,
  sourceIndex: Int,
  sourceCount: Int,
): Int =
  if (sourceCount > 1) {
    sourceIndex.coerceIn(0, sourceCount - 1)
  } else {
    providerIndexOf(sourcePageUrl) ?: 0
  }
