package com.giztv.tv.ui.player

import android.util.Log
import androidx.compose.foundation.background
import androidx.compose.foundation.border
import androidx.compose.foundation.clickable
import androidx.compose.foundation.layout.Arrangement
import androidx.compose.foundation.layout.Row
import androidx.compose.foundation.layout.padding
import androidx.compose.foundation.layout.size
import androidx.compose.foundation.shape.RoundedCornerShape
import androidx.compose.material.icons.Icons
import androidx.compose.material.icons.filled.SkipNext
import androidx.compose.material3.Icon
import androidx.compose.runtime.Composable
import androidx.compose.runtime.LaunchedEffect
import androidx.compose.runtime.getValue
import androidx.compose.runtime.mutableStateOf
import androidx.compose.runtime.remember
import androidx.compose.runtime.setValue
import androidx.compose.ui.Alignment
import androidx.compose.ui.Modifier
import androidx.compose.ui.draw.clip
import androidx.compose.ui.focus.FocusRequester
import androidx.compose.ui.focus.focusRequester
import androidx.compose.ui.focus.onFocusChanged
import androidx.compose.ui.graphics.Color
import androidx.compose.ui.semantics.Role
import androidx.compose.ui.semantics.contentDescription
import androidx.compose.ui.semantics.role
import androidx.compose.ui.semantics.semantics
import androidx.compose.ui.text.font.FontWeight
import androidx.compose.ui.unit.dp
import androidx.compose.ui.unit.sp
import androidx.tv.material3.Text
import com.giztv.tv.data.PlaybackContext
import com.giztv.tv.theme.DeepSpace
import com.giztv.tv.theme.GizMint
import com.giztv.tv.theme.SoftWhite
import com.giztv.tv.ui.anime.ANIDB_ORIGIN
import com.giztv.tv.ui.anime.animeEpisodeRef
import com.giztv.tv.ui.catalog.catalogTargetOf
import java.net.HttpURLConnection
import java.net.URL
import java.util.concurrent.TimeUnit
import kotlinx.coroutines.Dispatchers
import kotlinx.coroutines.sync.Mutex
import kotlinx.coroutines.sync.withLock
import kotlinx.coroutines.withContext
import org.json.JSONObject

/**
 * The parts of a title worth stepping over.
 *
 * Two databases answer this, because no single one covers what the app plays. Films and series are
 * keyed by TMDB id, which is how the catalog already addresses them; anime is keyed by MyAnimeList
 * id, which anidb.app happens to publish on its own pages. Neither has anything to say about short
 * dramas or live sport, and neither is asked about them.
 *
 * Both are community-submitted, so a missing answer is the normal case rather than a fault, and
 * both time against the official release while the app plays a provider's own encode. That is why
 * nothing here ever skips on its own: it offers a button, and a button that lands in the wrong
 * place costs a press rather than a scene.
 */
internal enum class SkipKind {
  INTRO,
  CREDITS,
}

/** [endMs] is null when the segment runs to the end of the episode. */
internal data class SkipSegment(val kind: SkipKind, val startMs: Long, val endMs: Long?)

internal data class SkipSegments(val intro: SkipSegment? = null, val credits: SkipSegment? = null) {
  val isEmpty: Boolean
    get() = intro == null && credits == null
}

/**
 * The segment covering [positionMs], if any.
 *
 * The intro is checked first: a title short enough for its credits to start before its intro ends
 * would be a mistake in the data, and the intro is the one a viewer is sitting through at the time.
 */
internal fun SkipSegments.at(positionMs: Long, durationMs: Long): SkipSegment? {
  val end = durationMs.takeIf { it > 0L } ?: Long.MAX_VALUE
  return listOfNotNull(intro, credits).firstOrNull { segment ->
    positionMs >= segment.startMs && positionMs < (segment.endMs ?: end)
  }
}

/** Where a press lands: the far side of the segment, or the end of the episode. */
internal fun SkipSegment.skipTargetMs(durationMs: Long): Long =
  endMs ?: durationMs.takeIf { it > 0L } ?: startMs

/**
 * What the button says.
 *
 * "Skip intro" whether the interval came from an opening theme or a cold-open title card, because
 * that is what a viewer calls it. The credits one names the next episode when there is one, since
 * pressing it then does more than move the playhead.
 */
internal fun SkipSegment.label(hasNextEpisode: Boolean): String =
  when {
    kind == SkipKind.INTRO -> "Skip intro"
    hasNextEpisode -> "Next episode"
    else -> "Skip credits"
  }

internal const val THE_INTRO_DB_ORIGIN = "https://api.theintrodb.org/v3"
internal const val ANISKIP_ORIGIN = "https://api.aniskip.com/v2"

/**
 * The offer, sitting above the controls where it does not cover the picture.
 *
 * On a television it takes focus while it is up, so the select button presses it — a button a
 * remote cannot reach without hunting for it is not worth showing. It gives focus back when the
 * segment ends.
 */
@Composable
internal fun SkipSegmentButton(
  label: String,
  isTelevision: Boolean,
  onClick: () -> Unit,
  modifier: Modifier = Modifier,
) {
  val focusRequester = remember { FocusRequester() }
  var focused by remember { mutableStateOf(false) }
  LaunchedEffect(isTelevision) {
    if (isTelevision) runCatching { focusRequester.requestFocus() }
  }
  Row(
    modifier =
      modifier
        .focusRequester(focusRequester)
        .onFocusChanged { focused = it.isFocused }
        .clip(RoundedCornerShape(10.dp))
        .background(if (focused) GizMint else Color.Black.copy(alpha = .72f))
        .border(
          if (focused) 0.dp else 1.5.dp,
          SoftWhite.copy(alpha = .55f),
          RoundedCornerShape(10.dp),
        )
        .clickable(onClick = onClick)
        .padding(horizontal = 20.dp, vertical = 11.dp)
        .semantics {
          role = Role.Button
          contentDescription = label
        },
    verticalAlignment = Alignment.CenterVertically,
    horizontalArrangement = Arrangement.spacedBy(7.dp),
  ) {
    Icon(
      Icons.Filled.SkipNext,
      contentDescription = null,
      tint = if (focused) DeepSpace else SoftWhite,
      modifier = Modifier.size(18.dp),
    )
    Text(
      label,
      color = if (focused) DeepSpace else SoftWhite,
      fontWeight = FontWeight.Black,
      fontSize = 14.sp,
    )
  }
}

/**
 * Reads TheIntroDB's answer.
 *
 * Times arrive in milliseconds, with null meaning "the edge of the episode" at either end — a null
 * start is the very beginning, a null end runs to the finish.
 */
internal fun parseIntroDbSegments(json: String): SkipSegments {
  val root = runCatching { JSONObject(json) }.getOrNull() ?: return SkipSegments()

  fun segment(name: String, kind: SkipKind): SkipSegment? {
    val entry = root.optJSONArray(name)?.optJSONObject(0) ?: return null
    val start = if (entry.isNull("start_ms")) 0L else entry.optLong("start_ms", 0L)
    val end = if (entry.isNull("end_ms")) null else entry.optLong("end_ms")
    if (end != null && end <= start) return null
    return SkipSegment(kind, start.coerceAtLeast(0L), end)
  }

  return SkipSegments(intro = segment("intro", SkipKind.INTRO), credits = segment("credits", SkipKind.CREDITS))
}

/**
 * Reads AniSkip's answer.
 *
 * Times are in seconds here rather than milliseconds. "op" is the opening and "ed" the ending; the
 * mixed variants are the same thing over the first or last scene and are taken when the plain ones
 * are absent, since a viewer wanting past the opening wants past it either way.
 */
internal fun parseAniSkipSegments(json: String): SkipSegments {
  val root = runCatching { JSONObject(json) }.getOrNull() ?: return SkipSegments()
  if (!root.optBoolean("found", false)) return SkipSegments()
  val results = root.optJSONArray("results") ?: return SkipSegments()

  fun segment(types: List<String>, kind: SkipKind): SkipSegment? {
    for (type in types) {
      for (index in 0 until results.length()) {
        val entry = results.optJSONObject(index) ?: continue
        if (!entry.optString("skipType").equals(type, ignoreCase = true)) continue
        val interval = entry.optJSONObject("interval") ?: continue
        val start = (interval.optDouble("startTime", -1.0) * 1_000).toLong()
        val end = (interval.optDouble("endTime", -1.0) * 1_000).toLong()
        if (start < 0L || end <= start) continue
        return SkipSegment(kind, start, end)
      }
    }
    return null
  }

  return SkipSegments(
    intro = segment(listOf("op", "mixed-op"), SkipKind.INTRO),
    credits = segment(listOf("ed", "mixed-ed"), SkipKind.CREDITS),
  )
}

private val malIdPattern = Regex("""myanimelist\.net/anime/(\d+)""", RegexOption.IGNORE_CASE)

/**
 * The MyAnimeList id an anidb.app page links out to.
 *
 * The number on the end of an anidb.app slug is the site's own id and is nothing like a MAL one —
 * One Piece is 3880 there and 21 on MAL — so the link is the only honest route between them.
 */
internal fun parseMalId(html: String): Int? =
  malIdPattern.find(html)?.groupValues?.get(1)?.toIntOrNull()

/** Where a title's segments come from, and what they are asked for. */
internal object SkipSegmentRepository {
  private val cache = mutableMapOf<String, SkipSegments>()
  private val malIds = mutableMapOf<String, Int?>()
  private val lock = Mutex()

  /**
   * Segments for what is playing, or none.
   *
   * Never throws: a database being down, rate-limiting, or simply not knowing this title are all
   * the same thing to the player, which is that there is no button to offer.
   */
  suspend fun forPlayback(playback: PlaybackContext?, durationMs: Long): SkipSegments {
    val context = playback ?: return SkipSegments()
    val key = "${context.pageUrl}|${context.episodeNumber}"
    lock.withLock { cache[key] }?.let { return it }
    val found =
      runCatching { resolve(context, durationMs) }
        .onFailure { Log.w("GizTvSkip", "No skip times for ${context.pageUrl}", it) }
        .getOrDefault(SkipSegments())
    lock.withLock { cache[key] = found }
    return found
  }

  private suspend fun resolve(context: PlaybackContext, durationMs: Long): SkipSegments {
    animeEpisodeRef(context.pageUrl)?.let { ref ->
      val malId = malIdFor(ref.slug) ?: return SkipSegments()
      val seconds = TimeUnit.MILLISECONDS.toSeconds(durationMs.coerceAtLeast(0L))
      return parseAniSkipSegments(
        get("$ANISKIP_ORIGIN/skip-times/$malId/${ref.episodeNumber}?types=op&types=ed&types=mixed-op&types=mixed-ed&episodeLength=$seconds")
      )
    }
    val target = catalogTargetOf(context.pageUrl) ?: return SkipSegments()
    val query = buildString {
      append("?tmdb_id=").append(target.tmdbId)
      if (target.isEpisode) {
        append("&season=").append(target.seasonNumber)
        append("&episode=").append(target.episodeNumber)
      }
    }
    return parseIntroDbSegments(get("$THE_INTRO_DB_ORIGIN/media$query"))
  }

  /** Looked up once per anime and kept, since a title's MAL id does not change between episodes. */
  private suspend fun malIdFor(slug: String): Int? {
    lock.withLock { if (malIds.containsKey(slug)) return malIds[slug] }
    val found = parseMalId(get("$ANIDB_ORIGIN/anime/$slug"))
    lock.withLock { malIds[slug] = found }
    return found
  }

  private suspend fun get(url: String): String =
    withContext(Dispatchers.IO) {
      val connection = (URL(url).openConnection() as HttpURLConnection).apply {
        requestMethod = "GET"
        connectTimeout = 8_000
        readTimeout = 8_000
        // anidb.app refuses anything that does not look like a browser, and neither skip database
        // minds being told who is asking.
        setRequestProperty(
          "User-Agent",
          "Mozilla/5.0 (Linux; Android 14) AppleWebKit/537.36 (KHTML, like Gecko) Chrome/120.0 Mobile Safari/537.36",
        )
        setRequestProperty("Accept", "application/json, text/html;q=.9")
      }
      try {
        // A 404 from either database is "nobody has submitted this yet" and its body still parses
        // to nothing, so it is read rather than thrown.
        val stream = if (connection.responseCode in 200..299) connection.inputStream else connection.errorStream
        stream?.bufferedReader()?.use { it.readText() }.orEmpty()
      } finally {
        connection.disconnect()
      }
    }
}
