package com.giztv.tv.ui.player

import androidx.compose.animation.AnimatedVisibility
import androidx.compose.animation.fadeIn
import androidx.compose.animation.fadeOut
import androidx.compose.animation.slideInHorizontally
import androidx.compose.animation.slideOutHorizontally
import androidx.compose.foundation.background
import androidx.compose.foundation.border
import androidx.compose.foundation.layout.Arrangement
import androidx.compose.foundation.layout.Column
import androidx.compose.foundation.layout.Row
import androidx.compose.foundation.layout.Spacer
import androidx.compose.foundation.layout.height
import androidx.compose.foundation.layout.heightIn
import androidx.compose.foundation.layout.padding
import androidx.compose.foundation.layout.size
import androidx.compose.foundation.layout.width
import androidx.compose.foundation.layout.widthIn
import androidx.compose.foundation.rememberScrollState
import androidx.compose.foundation.shape.RoundedCornerShape
import androidx.compose.foundation.verticalScroll
import androidx.compose.runtime.Composable
import androidx.compose.runtime.key
import androidx.compose.ui.Alignment
import androidx.compose.ui.Modifier
import androidx.compose.ui.draw.clip
import androidx.compose.ui.graphics.Brush
import androidx.compose.ui.graphics.Color
import androidx.compose.ui.platform.LocalConfiguration
import androidx.compose.ui.semantics.contentDescription
import androidx.compose.ui.semantics.semantics
import androidx.compose.ui.text.font.FontWeight
import androidx.compose.ui.text.style.TextOverflow
import androidx.compose.ui.unit.Dp
import androidx.compose.ui.unit.dp
import androidx.compose.ui.unit.sp
import androidx.tv.material3.Text
import com.giztv.tv.theme.GizMint
import com.giztv.tv.theme.DeepSpace
import com.giztv.tv.theme.MutedBlue
import com.giztv.tv.theme.SoftWhite
import com.giztv.tv.ui.catalog.PlaybackCastMember
import com.giztv.tv.ui.catalog.PlaybackDirector
import com.giztv.tv.ui.catalog.PlaybackReview
import com.giztv.tv.ui.catalog.TmdbArtwork
import kotlin.random.Random

/** Tip kinds that can appear in the pause overlay pool. */
internal enum class PauseTipKind {
  CAST,
  DIRECTOR,
  TRIVIA,
  REVIEW,
}

/** One factual “about this title” line with a stable id for anti-repeat. */
internal data class PauseTriviaFact(val id: String, val line: String)

/** Inputs available for the pause tip rotator for one title. */
internal data class PauseTipCatalog(
  val cast: List<PlaybackCastMember> = emptyList(),
  val director: PlaybackDirector? = null,
  val triviaFacts: List<PauseTriviaFact> = emptyList(),
  val reviews: List<PlaybackReview> = emptyList(),
) {
  val hasContent: Boolean
    get() = buildPauseTipPool().isNotEmpty()
}

/** One tip card shown while paused. */
internal sealed class PauseTip {
  abstract val tipId: String
  abstract val kind: PauseTipKind

  data class Cast(val member: PlaybackCastMember) : PauseTip() {
    override val tipId: String = "cast:${member.name.lowercase()}"
    override val kind: PauseTipKind = PauseTipKind.CAST
  }

  data class Director(val director: PlaybackDirector) : PauseTip() {
    override val tipId: String = "director:${director.name.lowercase()}"
    override val kind: PauseTipKind = PauseTipKind.DIRECTOR
  }

  data class Trivia(val fact: PauseTriviaFact) : PauseTip() {
    override val tipId: String = fact.id
    override val kind: PauseTipKind = PauseTipKind.TRIVIA
    val line: String
      get() = fact.line
  }

  data class Review(val review: PlaybackReview) : PauseTip() {
    override val tipId: String = "review:${review.id}"
    override val kind: PauseTipKind = PauseTipKind.REVIEW
  }
}

/** Expands a catalog into individual tips (each cast / fact / review is its own pool entry). */
internal fun PauseTipCatalog.buildPauseTipPool(): List<PauseTip> =
  buildList {
    cast.forEach { add(PauseTip.Cast(it)) }
    director?.let { add(PauseTip.Director(it)) }
    triviaFacts
      .filter { it.line.isNotBlank() && it.id.isNotBlank() }
      .distinctBy { it.id }
      .forEach { add(PauseTip.Trivia(it)) }
    reviews
      .filter { it.excerpt.isNotBlank() && it.author.isNotBlank() }
      .distinctBy { it.id }
      .forEach { add(PauseTip.Review(it)) }
  }

/**
 * Picks the next tip id: shuffle among never-shown this round; once the pool is covered, start a
 * new round and prefer least-recently-shown (avoiding an immediate repeat when possible).
 *
 * Returns the chosen id, or null when [poolIds] is empty.
 */
internal fun selectNextPauseTipId(
  poolIds: List<String>,
  shownInRound: Set<String>,
  lastShownId: String?,
  lastShownAt: Map<String, Long>,
  random: Random = Random.Default,
): String? {
  if (poolIds.isEmpty()) return null
  val poolSet = poolIds.toSet()
  val covered = poolSet.all { it in shownInRound }
  val eligible =
    if (covered) {
      poolIds
    } else {
      poolIds.filter { it !in shownInRound }
    }
  val avoidRepeat = eligible.filter { it != lastShownId }.ifEmpty { eligible }
  if (avoidRepeat.isEmpty()) return null
  return if (covered) {
    // New round: least-recently-shown among candidates.
    avoidRepeat.minBy { id -> lastShownAt[id] ?: Long.MIN_VALUE }
  } else {
    avoidRepeat.shuffled(random).first()
  }
}

/**
 * Advances through billed cast on each cast tip so consecutive cast pauses teach something new.
 *
 * Kept for cast-only call sites/tests. Prefer pool-based [PauseTipRotationStore] for the overlay.
 */
internal fun nextPauseCastIndex(lastShownIndex: Int?, castSize: Int): Int {
  if (castSize <= 0) return -1
  return ((lastShownIndex ?: -1) + 1).floorMod(castSize)
}

/** Short secondary line under the role — billing order, guest note, or department. */
internal fun pauseCastTriviaDetail(member: PlaybackCastMember): String {
  val parts =
    buildList {
      if (member.guest) add("Guest appearance")
      member.order?.takeIf { it >= 0 }?.let { add("Billed #${it + 1}") }
      member.knownForDepartment
        ?.takeIf { it.isNotBlank() && !it.equals("Acting", ignoreCase = true) }
        ?.let(::add)
    }
  return parts.joinToString(" · ").ifBlank {
    if (member.character != null) "In this title" else "Cast"
  }
}

/**
 * Multiple factual tips for the pause “about this title” pool.
 *
 * Each structured field is its own tip when present (year/rating, runtime, genres). Falls back to
 * a carefully filtered overview sentence. Marketing taglines are never used.
 */
internal fun pauseTriviaFacts(
  year: String? = null,
  rating: Double? = null,
  runtimeMinutes: Int? = null,
  genres: List<String> = emptyList(),
  overview: String? = null,
  topBilledName: String? = null,
  includeTopBilled: Boolean = false,
): List<PauseTriviaFact> =
  buildList {
    val releaseAndScore =
      buildList {
        year
          ?.trim()
          ?.takeIf { it.length >= 4 && it.take(4).all(Char::isDigit) }
          ?.take(4)
          ?.let { add("Released $it") }
        rating?.takeIf { it > 0.0 }?.let { score ->
          add("★ ${String.format(java.util.Locale.US, "%.1f", score)}")
        }
      }
    if (releaseAndScore.isNotEmpty()) {
      add(PauseTriviaFact(id = "fact:release", line = releaseAndScore.joinToString(" · ")))
    }

    runtimeMinutes?.takeIf { it > 0 }?.let { minutes ->
      add(PauseTriviaFact(id = "fact:runtime", line = "Runtime ${formatPauseRuntime(minutes)}"))
    }

    val genreLine = genres.map { it.trim() }.filter { it.isNotBlank() }.distinct().take(3)
    if (genreLine.isNotEmpty()) {
      add(PauseTriviaFact(id = "fact:genres", line = genreLine.joinToString(" · ")))
    }

    if (includeTopBilled) {
      topBilledName
        ?.trim()
        ?.takeIf { it.isNotBlank() }
        ?.let { add(PauseTriviaFact(id = "fact:top-billed", line = "Top billed: $it")) }
    }

    val basedOn = basedOnFactoid(overview)
    basedOn?.let { add(PauseTriviaFact(id = "fact:based-on", line = it)) }

    // Overview only when structured metadata is thin — and never when “Based on …” already covers it.
    if (size < 2 && basedOn == null) {
      factualOverviewSentence(overview)?.let {
        add(PauseTriviaFact(id = "fact:overview", line = it))
      }
    }
  }

/**
 * One factual line for the pause “about this title” tip (first available fact).
 *
 * Prefer structured metadata. Marketing taglines are never used — soft-skip (null) when nothing
 * informative remains.
 */
internal fun pauseTriviaSnippet(
  year: String? = null,
  rating: Double? = null,
  runtimeMinutes: Int? = null,
  genres: List<String> = emptyList(),
  overview: String? = null,
  topBilledName: String? = null,
  includeTopBilled: Boolean = false,
): String? =
  pauseTriviaFacts(
      year = year,
      rating = rating,
      runtimeMinutes = runtimeMinutes,
      genres = genres,
      overview = overview,
      topBilledName = topBilledName,
      includeTopBilled = includeTopBilled,
    )
    .firstOrNull()
    ?.line

/** Year · rating, runtime, genres, or top-billed — never slogans. First match only. */
internal fun structuredTriviaFactoid(
  year: String? = null,
  rating: Double? = null,
  runtimeMinutes: Int? = null,
  genres: List<String> = emptyList(),
  topBilledName: String? = null,
  includeTopBilled: Boolean = false,
): String? =
  pauseTriviaFacts(
      year = year,
      rating = rating,
      runtimeMinutes = runtimeMinutes,
      genres = genres,
      topBilledName = topBilledName,
      includeTopBilled = includeTopBilled,
      overview = null,
    )
    .firstOrNull()
    ?.line

private fun formatPauseRuntime(minutes: Int): String {
  val hours = minutes / 60
  val mins = minutes % 60
  return when {
    hours > 0 && mins > 0 -> "${hours}h ${mins}m"
    hours > 0 -> "${hours}h"
    else -> "${mins}m"
  }
}

/** Opening “Based on …” clause from overview when it reads like attribution, not a slogan. */
private fun basedOnFactoid(overview: String?): String? {
  val text = overview?.trim()?.takeIf { it.isNotBlank() } ?: return null
  val match =
    Regex(
        """^(Based (?:on|upon)(?:\s+the)?\s+[^.]{8,120}?)(?:[.!?]|\z)""",
        RegexOption.IGNORE_CASE,
      )
      .find(text)
      ?: return null
  val line = match.groupValues[1].trim().trimEnd(',', ';', ':', '-', '—')
  // Attribution lines are often short; only reject shouty / exclamatory forms.
  if (line.endsWith('!') || line.endsWith('?')) return null
  val letters = line.filter { it.isLetter() }
  if (letters.isNotEmpty() && letters.all { it.isUpperCase() }) return null
  return normalizeTipLine(line, maxLen = 140)
}

/**
 * First overview sentence that looks informative — rejects short exclamatory / all-caps
 * tagline-like lines.
 */
private fun factualOverviewSentence(overview: String?): String? {
  val text = overview?.trim()?.takeIf { it.isNotBlank() } ?: return null
  val match = Regex("""[.!?](?:\s|$)""").find(text)
  val sentence =
    if (match != null) {
      text.substring(0, match.range.first + 1).trim()
    } else {
      text
    }
  if (looksLikeMarketingSlogan(sentence)) return null
  return normalizeTipLine(sentence, maxLen = 140)
}

/** Heuristic for TMDB-style marketing blurbs that should never surface as “trivia”. */
internal fun looksLikeMarketingSlogan(line: String): Boolean {
  val trimmed = line.trim().trim('"', '\'', '“', '”', '«', '»')
  if (trimmed.isBlank()) return true
  if (trimmed.endsWith('!')) return true
  // Short punchy lines without much information (typical taglines).
  if (trimmed.length < 36) return true
  val letters = trimmed.filter { it.isLetter() }
  if (letters.isNotEmpty() && letters.all { it.isUpperCase() }) return true
  val upperRatio =
    if (letters.isEmpty()) 0.0 else letters.count { it.isUpperCase() }.toDouble() / letters.length
  // “ALL CAPS ENERGY” or title-case shouts with little prose.
  if (upperRatio > 0.72 && trimmed.length <= 60) return true
  // Rhetorical / poster questions.
  if (trimmed.endsWith('?') && trimmed.length < 56) return true
  return false
}

private fun normalizeTipLine(raw: String?, maxLen: Int): String? {
  val cleaned =
    raw
      ?.trim()
      ?.trim('"', '\'', '“', '”', '«', '»')
      ?.takeIf { it.isNotBlank() }
      ?: return null
  if (cleaned.length <= maxLen) return cleaned
  val cut =
    cleaned
      .take(maxLen)
      .substringBeforeLast(' ')
      .trimEnd(',', ';', ':', '-', '—', ' ')
      .takeIf { it.length >= 24 }
      ?: return null
  return "$cut…"
}

internal fun pauseReviewAttribution(review: PlaybackReview): String {
  val rating =
    review.rating?.takeIf { it > 0.0 }?.let { score ->
      "★ ${String.format(java.util.Locale.US, "%.0f", score)}"
    }
  return listOfNotNull(review.author.takeIf(String::isNotBlank), rating).joinToString(" · ")
}

/**
 * Remembers shown tip ids per title so pause → play → pause walks a large pool without
 * repeating the same tip until the round is well covered.
 */
internal object PauseTipRotationStore {
  private data class State(
    val shownInRound: MutableSet<String> = mutableSetOf(),
    val lastShownAt: MutableMap<String, Long> = mutableMapOf(),
    var lastShownId: String? = null,
    var clock: Long = 0L,
  )

  private val stateByTitle = mutableMapOf<String, State>()

  fun nextTip(
    titleKey: String,
    catalog: PauseTipCatalog,
    random: Random = Random.Default,
  ): PauseTip? {
    val pool = catalog.buildPauseTipPool()
    if (pool.isEmpty()) return null
    val byId = pool.associateBy { it.tipId }
    val poolIds = pool.map { it.tipId }
    val state = stateByTitle.getOrPut(titleKey) { State() }
    // Drop stale ids if the catalog shrank (e.g. soft-fail cleared reviews).
    state.shownInRound.retainAll(poolIds.toSet())
    state.lastShownAt.keys.retainAll(poolIds.toSet())

    val pickId =
      selectNextPauseTipId(
        poolIds = poolIds,
        shownInRound = state.shownInRound,
        lastShownId = state.lastShownId,
        lastShownAt = state.lastShownAt,
        random = random,
      )
        ?: return null

    // Round just completed: clear before recording the new pick so the next pause starts fresh.
    if (poolIds.toSet().all { it in state.shownInRound }) {
      state.shownInRound.clear()
    }
    state.shownInRound.add(pickId)
    state.lastShownId = pickId
    state.clock += 1
    state.lastShownAt[pickId] = state.clock
    return byId[pickId]
  }

  /** Test seam — clears remembered cursors. */
  internal fun clear() {
    stateByTitle.clear()
  }
}

/** @deprecated Prefer [PauseTipRotationStore]; kept for cast-only call sites/tests during transition. */
internal object PauseCastRotationStore {
  fun nextMember(
    titleKey: String,
    cast: List<PlaybackCastMember>,
    random: Random = Random.Default,
  ): PlaybackCastMember? {
    val tip =
      PauseTipRotationStore.nextTip(titleKey, PauseTipCatalog(cast = cast), random = random)
        as? PauseTip.Cast
    return tip?.member
  }

  internal fun clear() {
    PauseTipRotationStore.clear()
  }
}

@Composable
internal fun PauseTipOverlay(
  tip: PauseTip,
  visible: Boolean,
  modifier: Modifier = Modifier,
) {
  val tipKey =
    when (tip) {
      is PauseTip.Cast ->
        listOf(tip.member.name, tip.member.profilePath, tip.member.character, tip.member.order)
      is PauseTip.Director -> listOf("director", tip.director.name, tip.director.profilePath)
      is PauseTip.Trivia -> listOf("trivia", tip.fact.id, tip.line)
      is PauseTip.Review -> listOf("review", tip.review.id, tip.review.excerpt)
    }
  key(tipKey) {
    AnimatedVisibility(
      visible = visible,
      enter = fadeIn() + slideInHorizontally { -it / 4 },
      exit = fadeOut() + slideOutHorizontally { -it / 5 },
      modifier = modifier,
    ) {
      when (tip) {
        is PauseTip.Cast -> PauseCastTipBody(tip.member)
        is PauseTip.Director -> PauseDirectorTipBody(tip.director)
        is PauseTip.Trivia -> PauseTriviaTipBody(tip.line)
        is PauseTip.Review -> PauseReviewTipBody(tip.review)
      }
    }
  }
}

/** Cast-only entry point used by older call sites; prefers [PauseTipOverlay]. */
@Composable
internal fun PauseCastTriviaOverlay(
  member: PlaybackCastMember,
  visible: Boolean,
  modifier: Modifier = Modifier,
) {
  PauseTipOverlay(tip = PauseTip.Cast(member), visible = visible, modifier = modifier)
}

@Composable
private fun PauseCastTipBody(member: PlaybackCastMember) {
  val role = member.character?.takeIf(String::isNotBlank)
  val detail = pauseCastTriviaDetail(member)
  val description =
    buildString {
      append("Paused tip: ")
      append(member.name)
      if (role != null) append(" as ").append(role)
      append(". ").append(detail)
    }
  PauseTipChrome(description = description) {
    // Stable key forces a fresh image slot when the tip advances (name + photo stay one member).
    key(member.photoUrl, member.name) {
      TmdbArtwork(
        url = member.photoUrl,
        contentDescription = member.name,
        compact = true,
        fallbackLabel = "Cast",
        modifier =
          Modifier
            .size(64.dp)
            .clip(RoundedCornerShape(12.dp))
            .border(1.dp, SoftWhite.copy(alpha = .14f), RoundedCornerShape(12.dp)),
      )
    }
    PauseTipTextColumn(
      title = member.name,
      subtitle = role,
      detail = detail,
      titleMaxLines = 1,
    )
  }
}

@Composable
private fun PauseDirectorTipBody(director: PlaybackDirector) {
  val description = "Paused tip: Directed by ${director.name}"
  PauseTipChrome(description = description) {
    key(director.photoUrl, director.name) {
      TmdbArtwork(
        url = director.photoUrl,
        contentDescription = director.name,
        compact = true,
        fallbackLabel = "Dir",
        modifier =
          Modifier
            .size(64.dp)
            .clip(RoundedCornerShape(12.dp))
            .border(1.dp, SoftWhite.copy(alpha = .14f), RoundedCornerShape(12.dp)),
      )
    }
    PauseTipTextColumn(
      title = director.name,
      subtitle = "Directed by",
      detail = "Crew",
      titleMaxLines = 1,
    )
  }
}

@Composable
private fun PauseTriviaTipBody(line: String) {
  val description = "About this title: $line"
  PauseTipChrome(description = description) {
    key("trivia", line) {
      TmdbArtwork(
        url = null,
        contentDescription = "About this title",
        compact = true,
        fallbackLabel = "Info",
        modifier =
          Modifier
            .size(64.dp)
            .clip(RoundedCornerShape(12.dp))
            .border(1.dp, SoftWhite.copy(alpha = .14f), RoundedCornerShape(12.dp)),
      )
    }
    PauseTipTextColumn(
      title = line,
      subtitle = null,
      detail = null,
      titleMaxLines = 2,
      eyebrow = "About this title",
    )
  }
}

@Composable
private fun PauseReviewTipBody(review: PlaybackReview) {
  val attribution = pauseReviewAttribution(review)
  val description = "Review by $attribution: ${review.excerpt}"
  val maxBodyHeight = (LocalConfiguration.current.screenHeightDp * 0.4f).dp
  PauseTipChrome(
    description = description,
    maxWidth = 520.dp,
    verticalAlignment = Alignment.Top,
  ) {
    key("review", review.id) {
      TmdbArtwork(
        url = null,
        contentDescription = "Review",
        compact = true,
        fallbackLabel = "Rev",
        modifier =
          Modifier
            .size(64.dp)
            .clip(RoundedCornerShape(12.dp))
            .border(1.dp, SoftWhite.copy(alpha = .14f), RoundedCornerShape(12.dp)),
      )
    }
    Column(modifier = Modifier.widthIn(max = 420.dp)) {
      Text(
        "Review",
        color = GizMint,
        fontWeight = FontWeight.Black,
        letterSpacing = 1.6.sp,
        fontSize = 10.sp,
      )
      Spacer(modifier = Modifier.height(4.dp))
      Column(
        modifier =
          Modifier
            .heightIn(max = maxBodyHeight)
            .verticalScroll(rememberScrollState()),
      ) {
        Text(
          review.excerpt,
          color = SoftWhite,
          fontWeight = FontWeight.Bold,
          fontSize = 15.sp,
          lineHeight = 20.sp,
        )
      }
      if (attribution.isNotBlank()) {
        Spacer(modifier = Modifier.height(6.dp))
        Text(
          attribution,
          color = SoftWhite.copy(alpha = .88f),
          fontWeight = FontWeight.Medium,
          fontSize = 13.sp,
          maxLines = 1,
          overflow = TextOverflow.Ellipsis,
        )
      }
    }
  }
}

@Composable
private fun PauseTipChrome(
  description: String,
  maxWidth: Dp = 360.dp,
  verticalAlignment: Alignment.Vertical = Alignment.CenterVertically,
  content: @Composable () -> Unit,
) {
  Row(
    modifier =
      Modifier
        .widthIn(max = maxWidth)
        .clip(RoundedCornerShape(16.dp))
        .background(
          Brush.horizontalGradient(
            colors =
              listOf(
                DeepSpace.copy(alpha = .92f),
                DeepSpace.copy(alpha = .78f),
                Color.Transparent,
              ),
          ),
        )
        .border(1.dp, SoftWhite.copy(alpha = .10f), RoundedCornerShape(16.dp))
        .padding(10.dp)
        .semantics { contentDescription = description },
    verticalAlignment = verticalAlignment,
    horizontalArrangement = Arrangement.spacedBy(12.dp),
  ) {
    content()
    Spacer(modifier = Modifier.width(8.dp))
  }
}

@Composable
private fun PauseTipTextColumn(
  title: String,
  subtitle: String?,
  detail: String?,
  titleMaxLines: Int,
  eyebrow: String = "WHILE PAUSED",
) {
  Column(modifier = Modifier.widthIn(max = 260.dp)) {
    Text(
      eyebrow,
      color = GizMint,
      fontWeight = FontWeight.Black,
      letterSpacing = 1.6.sp,
      fontSize = 10.sp,
    )
    Spacer(modifier = Modifier.height(4.dp))
    Text(
      title,
      color = SoftWhite,
      fontWeight = FontWeight.Bold,
      fontSize = 16.sp,
      maxLines = titleMaxLines,
      overflow = TextOverflow.Ellipsis,
    )
    if (subtitle != null) {
      Spacer(modifier = Modifier.height(2.dp))
      Text(
        subtitle,
        color = SoftWhite.copy(alpha = .88f),
        fontWeight = FontWeight.Medium,
        fontSize = 13.sp,
        maxLines = 1,
        overflow = TextOverflow.Ellipsis,
      )
    }
    if (detail != null) {
      Spacer(modifier = Modifier.height(3.dp))
      Text(
        detail,
        color = MutedBlue,
        fontSize = 12.sp,
        maxLines = 1,
        overflow = TextOverflow.Ellipsis,
      )
    }
  }
}

private fun Int.floorMod(modulus: Int): Int {
  val r = this % modulus
  return if (r < 0) r + modulus else r
}
