package com.giztv.tv.ui.anime

import android.content.Context
import android.util.Log
import com.giztv.tv.data.PlaybackContext
import com.giztv.tv.data.UiPreferencesStore
import com.giztv.tv.ui.player.HlsStreamRequest

/**
 * How an episode is identified for watch history.
 *
 * The site's own watch address carries no episode — episodes are switched inside its player — so
 * this is the app's own stable form of one. It has to stay stable across releases: the
 * continue-watching row is keyed on it, and [animeEpisodeRef] reads it back.
 */
internal fun animeEpisodeIdentity(slug: String, episodeNumber: Int): String =
  "$ANIDB_ORIGIN/anime/$slug?ep=$episodeNumber"

/** What an identity minted by [animeEpisodeIdentity] refers to. */
internal data class AnimeEpisodeRef(val slug: String, val animeId: Int, val episodeNumber: Int)

private val episodeIdentityPattern =
  Regex("""^https://\Q$ANIDB_HOST\E/anime/([^/?#]+)\?ep=(\d+)$""")

/** Reads an identity back, or null when the address belongs to something else entirely. */
internal fun animeEpisodeRef(pageUrl: String): AnimeEpisodeRef? {
  val match = episodeIdentityPattern.find(pageUrl.trim()) ?: return null
  val slug = match.groupValues[1]
  val animeId = Regex("""-(\d+)$""").find(slug)?.groupValues?.get(1)?.toIntOrNull() ?: return null
  val episodeNumber = match.groupValues[2].toIntOrNull()?.takeIf { it >= 1 } ?: return null
  return AnimeEpisodeRef(slug = slug, animeId = animeId, episodeNumber = episodeNumber)
}

/**
 * The dub or sub to play, given what the viewer last chose.
 *
 * Falling back to a subtitled track rather than simply the first one matters: the site lists
 * English first, so "whatever came first" is a dub, and a viewer who has never touched the chips
 * would be given one.
 */
internal fun List<AnimeLanguage>.preferring(code: String?): AnimeLanguage? =
  firstOrNull { it.code.equals(code, ignoreCase = true) }
    ?: firstOrNull(AnimeLanguage::isSubtitled)
    ?: firstOrNull()

internal fun animePlaybackRequest(
  streamUrl: String,
  slug: String,
  title: String,
  episodeNumber: Int,
  language: AnimeLanguage,
  playbackContext: PlaybackContext,
): HlsStreamRequest =
  HlsStreamRequest(
    url = streamUrl,
    headers = anidbStreamHeaders(),
    sourcePageUrl = "$ANIDB_ORIGIN/anime/$slug",
    title = title,
    subtitle = "Episode $episodeNumber · ${language.name}",
    context = playbackContext,
  )

/**
 * Resolves an episode the player is rolling into, or being resumed at, back to a stream.
 *
 * The address such an episode arrives under is an identity this app minted rather than a page any
 * provider can be asked about, so the ordinary resolve path cannot serve it — and going through
 * the site's own player instead hands back whichever language that page defaults to, which is the
 * dub. Everything needed is derivable from the identity: the number on the end of the slug is the
 * anime, and the episode number picks the episode out of its list.
 *
 * Returns null when [playback] is not an anime episode at all, which is how the caller tells this
 * apart from a resolution that failed.
 */
internal suspend fun resolveAnimeEpisode(
  appContext: Context,
  playback: PlaybackContext,
): HlsStreamRequest? {
  val ref = animeEpisodeRef(playback.pageUrl) ?: return null
  val episode =
    AnimeRepository.episodes(ref.animeId).firstOrNull { it.number == ref.episodeNumber }
      ?: run {
        Log.w("GizTvAnime", "Episode ${ref.episodeNumber} is not listed for ${ref.slug}")
        return null
      }
  val preferred = UiPreferencesStore(appContext).animeLanguage()
  val language =
    AnimeRepository.languages(episode.id).preferring(preferred)
      ?: run {
        Log.w("GizTvAnime", "Episode ${ref.episodeNumber} of ${ref.slug} carries no language")
        return null
      }
  return animePlaybackRequest(
    streamUrl = AnimeRepository.streamUrl(language),
    slug = ref.slug,
    title = playback.title,
    episodeNumber = ref.episodeNumber,
    language = language,
    playbackContext = playback,
  )
}
