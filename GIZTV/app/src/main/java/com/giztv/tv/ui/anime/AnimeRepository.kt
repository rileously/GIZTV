package com.giztv.tv.ui.anime

import android.net.Uri
import com.giztv.tv.BuildConfig
import com.giztv.tv.ui.drama.DramaBoxCache
import com.giztv.tv.ui.drama.DramaBoxRateLimiter
import java.io.IOException
import java.net.HttpURLConnection
import java.net.URL
import java.util.concurrent.TimeUnit
import kotlinx.coroutines.Dispatchers
import kotlinx.coroutines.withContext
import org.json.JSONObject

/**
 * One title in the anidb.app catalogue.
 *
 * [slug] is the site's own path segment — "vinland-saga-5999" — and [id] the number on the end of
 * it. The listing never states that number separately, but every API the watch page calls is keyed
 * on it, so reading it off the slug saves fetching the detail page just to learn it.
 */
internal data class Anime(
  val slug: String,
  val id: Int,
  val title: String,
  val posterUrl: String?,
  val kind: String?,
  val score: String?,
) {
  val pageUrl: String
    get() = "$ANIDB_ORIGIN/anime/$slug"

  /** The score is left out: the card renders it as its own badge, and twice reads as a mistake. */
  val subtitle: String
    get() = kind.orEmpty()
}

/** The facts panel and synopsis, which live on the detail page rather than in any API. */
internal data class AnimeDetails(
  val synopsis: String,
  val facts: List<Pair<String, String>>,
) {
  fun fact(name: String): String? =
    facts.firstOrNull { it.first.equals(name, ignoreCase = true) }?.second

  companion object {
    val EMPTY = AnimeDetails(synopsis = "", facts = emptyList())
  }
}

internal data class AnimeEpisode(val id: Int, val number: Int, val filler: Boolean)

/**
 * A dub or a sub, as the site files them.
 *
 * Anime is watched one way or the other and rarely both, so which one was picked last is worth
 * remembering; [AnimeLanguage.isSubtitled] is what that preference is matched on.
 */
internal data class AnimeLanguage(val code: String, val name: String, val embedUrl: String) {
  val isSubtitled: Boolean
    get() = !code.equals("eng", ignoreCase = true)
}

/** How the catalogue can be ordered. The values are the site's own `sort` parameter. */
internal enum class AnimeSort(val label: String, val value: String) {
  TRENDING("Trending", "order_trending"),
  TOP_AIRING("Top airing", "order_top_airing"),
  POPULAR("Popular", "order_popular"),
  TOP_RATED("Top rated", "order_top"),
  UPDATED("Latest", "order_updated"),
  NEWEST("Newest", "aired_start"),
}

/** The `type` parameter, plus the everything case the site expresses by leaving it off. */
internal enum class AnimeKind(val label: String, val value: String?) {
  ALL("All", null),
  TV("TV", "TV"),
  MOVIE("Movies", "Movie"),
  ONA("ONA", "ONA"),
  OVA("OVA", "OVA"),
  SPECIAL("Specials", "Special"),
}

internal const val ANIDB_HOST = "anidb.app"
internal const val ANIDB_ORIGIN = "https://$ANIDB_HOST"

/** Kept modest: a catalogue is browsed, not harvested. */
private val anidbRateLimiter = DramaBoxRateLimiter(maxRequests = 20, windowMs = 60_000L)

/**
 * The anidb.app catalogue.
 *
 * Browsing and searching are server-rendered pages and have to be read out of the markup, but
 * everything behind a title is JSON: the episode list, the dub and sub each episode carries, and —
 * one page further, in the embed the site's own player loads — a plain HLS master playlist that
 * Media3 takes directly. Nothing here needs the browser resolver.
 *
 * Answers are cached across navigation, because reopening a title should not cost a round trip.
 */
internal object AnimeRepository {
  private val listings = DramaBoxCache<List<Anime>>(TimeUnit.MINUTES.toMillis(10))
  private val details = DramaBoxCache<AnimeDetails>(TimeUnit.HOURS.toMillis(6))
  private val episodes = DramaBoxCache<List<AnimeEpisode>>(TimeUnit.MINUTES.toMillis(30))
  private val languages = DramaBoxCache<List<AnimeLanguage>>(TimeUnit.MINUTES.toMillis(30))

  suspend fun browse(
    sort: AnimeSort = AnimeSort.TRENDING,
    kind: AnimeKind = AnimeKind.ALL,
    genreId: Int? = null,
    query: String = "",
  ): List<Anime> {
    val trimmed = query.trim()
    val key = "${sort.value}|${kind.value}|$genreId|${trimmed.lowercase()}"
    return listings.get(key) {
      val params = buildMap {
        put("sort", sort.value)
        kind.value?.let { put("type", it) }
        genreId?.let { put("genres", it.toString()) }
        if (trimmed.isNotEmpty()) put("q", trimmed)
      }
      parseAnimeCards(anidbPage("browse", params))
    }
  }

  suspend fun details(anime: Anime): AnimeDetails =
    details.get(anime.slug) { parseAnimeDetails(anidbPage("anime/${anime.slug}")) }

  suspend fun episodes(animeId: Int): List<AnimeEpisode> =
    episodes.get(animeId.toString()) {
      parseAnimeEpisodes(anidbPage("api/frontend/anime/$animeId/episodes", json = true))
    }

  suspend fun languages(episodeId: Int): List<AnimeLanguage> =
    languages.get(episodeId.toString()) {
      parseAnimeLanguages(anidbPage("api/frontend/episode/$episodeId/languages", json = true))
    }

  /**
   * The playlist address behind an embed.
   *
   * Deliberately uncached: the token in the address is minted per request and the page is cheap,
   * so a stale one would only ever mean a stream that has already expired.
   */
  suspend fun streamUrl(language: AnimeLanguage): String =
    parseEmbedStreamUrl(anidbUrl(language.embedUrl))
      ?: throw IOException("This episode has no playable stream right now.")
}

/** Fetches a path on the site's own host. */
private suspend fun anidbPage(path: String, params: Map<String, String> = emptyMap(), json: Boolean = false): String {
  val uri =
    Uri.Builder()
      .scheme("https")
      .authority(ANIDB_HOST)
      .apply {
        path.trim('/').split('/').forEach(::appendPath)
        params.forEach { (name, value) -> appendQueryParameter(name, value) }
      }
      .build()
  return anidbUrl(uri.toString(), json)
}

/** Fetches an absolute address, which for the embed the site hands out is what there is to go on. */
private suspend fun anidbUrl(url: String, json: Boolean = false): String =
  withContext(Dispatchers.IO) {
    anidbRateLimiter.acquire()
    val connection = (URL(url).openConnection() as HttpURLConnection)
    try {
      connection.requestMethod = "GET"
      connection.instanceFollowRedirects = true
      connection.setRequestProperty("Accept", if (json) "application/json" else "text/html,application/xhtml+xml")
      // The edge in front of this site refuses anything that does not look like a browser: the
      // same request without these two answers 403 rather than the page.
      connection.setRequestProperty("User-Agent", ANIDB_USER_AGENT)
      connection.setRequestProperty("Referer", "$ANIDB_ORIGIN/")
      connection.connectTimeout = 12_000
      connection.readTimeout = 20_000
      when (val status = connection.responseCode) {
        in 200..299 -> connection.inputStream.bufferedReader().use { it.readText() }
        HttpURLConnection.HTTP_FORBIDDEN -> throw IOException("Anime is unavailable on this network right now.")
        HttpURLConnection.HTTP_NOT_FOUND -> throw IOException("That anime is no longer listed.")
        else -> throw IOException("Anime returned HTTP $status")
      }
    } finally {
      connection.disconnect()
    }
  }

internal val ANIDB_USER_AGENT: String =
  "Mozilla/5.0 (Linux; Android 10) AppleWebKit/537.36 (KHTML, like Gecko) " +
    "Chrome/131.0.0.0 Mobile Safari/537.36 GIZTV/${BuildConfig.VERSION_NAME}"

/** The headers the stream host wants; it serves the playlist to anything that looks like a browser. */
internal fun anidbStreamHeaders(): Map<String, String> =
  mapOf("User-Agent" to ANIDB_USER_AGENT, "Referer" to "$ANIDB_ORIGIN/")

private val cardPattern =
  Regex("""<a\s+href="[^"]*?/anime/([^"/?#]+)"[^>]*class="anime-card[^"]*"[^>]*title="([^"]*)"""")
private val posterPattern = Regex("""<img\s+src="([^"]+)"""")
private val kindBadgePattern = Regex("""badge-orange[^>]*>\s*([^<]{1,16}?)\s*</span>""")
private val scoreBadgePattern = Regex("""</svg>\s*([0-9]+(?:\.[0-9]+)?)\s*</span>""")
private val trailingIdPattern = Regex("""-(\d+)$""")

/**
 * Reads the grid out of a browse or search page.
 *
 * Each card is an anchor carrying the slug and the full title, and the fields worth showing sit in
 * the markup between it and the next one, so the page is walked card to card rather than matched
 * against one expression spanning all of it.
 */
internal fun parseAnimeCards(html: String): List<Anime> {
  val cards = cardPattern.findAll(html).toList()
  return cards.mapIndexedNotNull { index, card ->
    val slug = card.groupValues[1]
    val id = trailingIdPattern.find(slug)?.groupValues?.get(1)?.toIntOrNull() ?: return@mapIndexedNotNull null
    val title = decodeHtml(card.groupValues[2]).trim().ifBlank { return@mapIndexedNotNull null }
    val body = html.substring(card.range.last, cards.getOrNull(index + 1)?.range?.first ?: html.length)
    Anime(
      slug = slug,
      id = id,
      title = title,
      posterUrl = posterPattern.find(body)?.groupValues?.get(1)?.takeIf { it.startsWith("http") },
      kind = kindBadgePattern.find(body)?.groupValues?.get(1)?.let(::decodeHtml)?.takeIf(String::isNotBlank),
      score = scoreBadgePattern.find(body)?.groupValues?.get(1),
    )
  }
    .distinctBy(Anime::slug)
}

private val synopsisPattern = Regex("""<p class="text-sm text-faint leading-relaxed">(.*?)</p>""", RegexOption.DOT_MATCHES_ALL)
private val factPattern = Regex("""<dt[^>]*>(.*?)</dt>\s*<dd[^>]*>(.*?)</dd>""", RegexOption.DOT_MATCHES_ALL)

internal fun parseAnimeDetails(html: String): AnimeDetails =
  AnimeDetails(
    synopsis = synopsisPattern.find(html)?.groupValues?.get(1)?.let(::stripHtml).orEmpty(),
    facts =
      factPattern.findAll(html)
        .map { stripHtml(it.groupValues[1]) to stripHtml(it.groupValues[2]) }
        .filter { it.first.isNotBlank() && it.second.isNotBlank() }
        .distinctBy { it.first }
        .toList(),
  )

internal fun parseAnimeEpisodes(json: String): List<AnimeEpisode> {
  val items = JSONObject(json).optJSONArray("episodes") ?: return emptyList()
  return buildList {
    for (index in 0 until items.length()) {
      val item = items.optJSONObject(index) ?: continue
      val id = item.optInt("id", -1)
      if (id <= 0) continue
      add(
        AnimeEpisode(
          id = id,
          number = item.optInt("number", index + 1),
          filler = item.optBoolean("filler", false),
        )
      )
    }
  }
}

internal fun parseAnimeLanguages(json: String): List<AnimeLanguage> {
  val items = JSONObject(json).optJSONArray("languages") ?: return emptyList()
  return buildList {
    for (index in 0 until items.length()) {
      val item = items.optJSONObject(index) ?: continue
      val embed = item.optString("embed_url").trim()
      if (!embed.startsWith("http")) continue
      add(
        AnimeLanguage(
          code = item.optString("code").trim().ifBlank { "und" },
          name = item.optString("name").trim().ifBlank { "Unknown" },
          embedUrl = embed,
        )
      )
    }
  }
}

private val embedSourcePattern = Regex("""file:\s*['"](https?://[^'"]+?\.m3u8[^'"]*)['"]""")

/** The embed is the site's own player page, and it states the playlist address in plain sight. */
internal fun parseEmbedStreamUrl(html: String): String? =
  embedSourcePattern.find(html)?.groupValues?.get(1)

private val tagPattern = Regex("""<[^>]+>""")
private val whitespacePattern = Regex("""\s+""")

private fun stripHtml(value: String): String =
  decodeHtml(tagPattern.replace(value, " ")).replace(whitespacePattern, " ").trim()

/** The named and numeric entities this site's markup actually uses. */
internal fun decodeHtml(value: String): String {
  if ('&' !in value) return value
  return Regex("""&(#x?[0-9A-Fa-f]+|[A-Za-z]+);""").replace(value) { match ->
    val body = match.groupValues[1]
    when {
      body.startsWith("#x", ignoreCase = true) ->
        body.drop(2).toIntOrNull(16)?.let(Character::toChars)?.concatToString() ?: match.value
      body.startsWith("#") -> body.drop(1).toIntOrNull()?.let(Character::toChars)?.concatToString() ?: match.value
      else ->
        when (body.lowercase()) {
          "amp" -> "&"
          "lt" -> "<"
          "gt" -> ">"
          "quot" -> "\""
          "apos" -> "'"
          "nbsp" -> " "
          "hellip" -> "…"
          "mdash" -> "—"
          "ndash" -> "–"
          else -> match.value
        }
    }
  }
}
