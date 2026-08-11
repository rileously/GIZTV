package com.giztv.tv.ui.anime

import android.util.Log
import androidx.compose.animation.animateColorAsState
import androidx.compose.animation.core.animateFloatAsState
import androidx.compose.foundation.background
import androidx.compose.foundation.border
import androidx.compose.foundation.clickable
import androidx.compose.foundation.layout.Arrangement
import androidx.compose.foundation.layout.Box
import androidx.compose.foundation.layout.BoxWithConstraints
import androidx.compose.foundation.layout.Column
import androidx.compose.foundation.layout.PaddingValues
import androidx.compose.foundation.layout.Row
import androidx.compose.foundation.layout.Spacer
import androidx.compose.foundation.layout.aspectRatio
import androidx.compose.foundation.layout.fillMaxHeight
import androidx.compose.foundation.layout.fillMaxSize
import androidx.compose.foundation.layout.fillMaxWidth
import androidx.compose.foundation.layout.height
import androidx.compose.foundation.layout.padding
import androidx.compose.foundation.layout.width
import androidx.compose.foundation.lazy.grid.GridCells
import androidx.compose.foundation.lazy.grid.LazyVerticalGrid
import androidx.compose.foundation.lazy.grid.items
import androidx.compose.foundation.lazy.grid.rememberLazyGridState
import androidx.compose.foundation.rememberScrollState
import androidx.compose.foundation.shape.RoundedCornerShape
import androidx.compose.foundation.verticalScroll
import androidx.compose.runtime.Composable
import androidx.compose.runtime.LaunchedEffect
import androidx.compose.runtime.getValue
import androidx.compose.runtime.mutableStateOf
import androidx.compose.runtime.remember
import androidx.compose.runtime.rememberCoroutineScope
import androidx.compose.runtime.setValue
import androidx.compose.ui.Alignment
import androidx.compose.ui.Modifier
import androidx.compose.ui.draw.clip
import androidx.compose.ui.focus.FocusRequester
import androidx.compose.ui.focus.focusProperties
import androidx.compose.ui.focus.focusRequester
import androidx.compose.ui.focus.onFocusChanged
import androidx.compose.ui.graphics.Brush
import androidx.compose.ui.graphics.Color
import androidx.compose.ui.graphics.graphicsLayer
import androidx.compose.ui.platform.LocalContext
import androidx.compose.ui.semantics.Role
import androidx.compose.ui.semantics.contentDescription
import androidx.compose.ui.semantics.role
import androidx.compose.ui.semantics.semantics
import androidx.compose.ui.text.font.FontWeight
import androidx.compose.ui.text.style.TextOverflow
import androidx.compose.ui.unit.dp
import androidx.compose.ui.unit.sp
import androidx.tv.material3.Text
import com.giztv.tv.data.PlaybackContext
import com.giztv.tv.data.PlaylistEntry
import com.giztv.tv.data.UiPreferencesStore
import com.giztv.tv.data.WatchHistoryEntry
import com.giztv.tv.data.WatchHistoryStore
import com.giztv.tv.theme.GizMint
import com.giztv.tv.theme.DeepSpace
import com.giztv.tv.theme.MutedBlue
import com.giztv.tv.theme.NightSurface
import com.giztv.tv.theme.SoftWhite
import com.giztv.tv.ui.catalog.CatalogButton
import com.giztv.tv.ui.catalog.ChipRow
import com.giztv.tv.ui.catalog.StatusPanel
import com.giztv.tv.ui.catalog.TmdbArtwork
import com.giztv.tv.ui.player.HlsStreamRequest
import kotlinx.coroutines.launch

/**
 * How an episode is identified for watch history.
 *
 * The site's own watch address carries no episode, so this is the app's own stable form of one. It
 * has to stay stable across releases: the continue-watching row is keyed on it.
 */
internal fun animeEpisodeIdentity(slug: String, episodeNumber: Int): String =
  "$ANIDB_ORIGIN/anime/$slug?ep=$episodeNumber"

/**
 * Anime landing page: artwork and facts on the left, every episode on the right.
 *
 * Unlike a TMDB show, an episode here is a number and nothing else — the site publishes no titles,
 * stills or summaries per episode — so they are a dense numbered grid, the way short dramas are.
 * The dub and sub of an episode are separate streams rather than tracks inside one, which is why
 * the choice sits on this page and not in the player's audio menu.
 */
@Composable
internal fun AnimeDetailScreen(
  anime: Anime,
  onPlayEpisode: (HlsStreamRequest) -> Unit,
  onBack: () -> Unit,
  modifier: Modifier = Modifier,
) {
  val context = LocalContext.current
  val scope = rememberCoroutineScope()
  val historyStore = remember(context) { WatchHistoryStore(context) }
  val uiPreferences = remember(context) { UiPreferencesStore(context) }
  val backFocusRequester = remember { FocusRequester() }
  val languageFocusRequester = remember { FocusRequester() }
  val firstEpisodeFocusRequester = remember { FocusRequester() }
  val episodeGridState = rememberLazyGridState()

  var details by remember(anime.slug) { mutableStateOf(AnimeDetails.EMPTY) }
  var episodes by remember(anime.slug) { mutableStateOf<List<AnimeEpisode>>(emptyList()) }
  var languages by remember(anime.slug) { mutableStateOf<List<AnimeLanguage>>(emptyList()) }
  var selectedLanguage by remember(anime.slug) { mutableStateOf<String?>(uiPreferences.animeLanguage()) }
  var loading by remember(anime.slug) { mutableStateOf(true) }
  var errorMessage by remember(anime.slug) { mutableStateOf<String?>(null) }
  /** The episode whose stream is being resolved, so the tile can say so and refuse a second tap. */
  var preparing by remember(anime.slug) { mutableStateOf<Int?>(null) }

  LaunchedEffect(Unit) { backFocusRequester.requestFocus() }

  LaunchedEffect(anime.slug) {
    loading = true
    errorMessage = null
    // The facts panel is worth having but never worth blocking the episode list for.
    runCatching { AnimeRepository.details(anime) }
      .onSuccess { details = it }
      .onFailure { Log.w("GizTvAnime", "Anime details unavailable for ${anime.slug}", it) }
    runCatching { AnimeRepository.episodes(anime.id) }
      .onSuccess { episodes = it }
      .onFailure {
        Log.e("GizTvAnime", "Anime episodes failed for ${anime.slug}", it)
        errorMessage = it.message ?: "The episode list could not be loaded."
      }
    loading = false
  }

  // Which languages exist is a property of the episode, but it does not vary within a title in
  // practice, so the first one answers for the page and saves a request per tile.
  LaunchedEffect(episodes.firstOrNull()?.id) {
    val first = episodes.firstOrNull() ?: return@LaunchedEffect
    runCatching { AnimeRepository.languages(first.id) }
      .onSuccess { available ->
        languages = available
        if (available.none { it.code == selectedLanguage }) {
          selectedLanguage = available.firstOrNull { it.isSubtitled }?.code ?: available.firstOrNull()?.code
        }
      }
      .onFailure { Log.w("GizTvAnime", "Anime languages unavailable for ${anime.slug}", it) }
  }

  val playlist =
    remember(anime.slug, episodes) {
      episodes.map { episode ->
        PlaylistEntry(
          episodeNumber = episode.number,
          name = "Episode ${episode.number}",
          pageUrl = animeEpisodeIdentity(anime.slug, episode.number),
        )
      }
    }

  fun play(episode: AnimeEpisode) {
    if (preparing != null) return
    preparing = episode.number
    scope.launch {
      runCatching {
          val available =
            languages.takeIf { it.isNotEmpty() } ?: AnimeRepository.languages(episode.id)
          val language =
            available.firstOrNull { it.code == selectedLanguage }
              ?: available.firstOrNull { it.isSubtitled }
              ?: available.firstOrNull()
              ?: throw IllegalStateException("This episode has no stream to play.")
          language to AnimeRepository.streamUrl(language)
        }
        .onSuccess { (language, streamUrl) ->
          errorMessage = null
          onPlayEpisode(
            HlsStreamRequest(
              url = streamUrl,
              headers = anidbStreamHeaders(),
              sourcePageUrl = anime.pageUrl,
              title = anime.title,
              subtitle = "Episode ${episode.number} · ${language.name}",
              context =
                PlaybackContext(
                  pageUrl = animeEpisodeIdentity(anime.slug, episode.number),
                  title = anime.title,
                  subtitle = "Episode ${episode.number}",
                  posterUrl = anime.posterUrl,
                  year = details.fact("Season"),
                  overview = details.synopsis.takeIf(String::isNotBlank),
                  rating = details.fact("Score")?.toDoubleOrNull() ?: anime.score?.toDoubleOrNull(),
                  genres = listOfNotNull(details.fact("Demographic"), details.fact("Studios")),
                  episodeNumber = episode.number,
                  playlist = playlist,
                  kindLabel = "ANIME",
                ),
            )
          )
        }
        .onFailure {
          Log.e("GizTvAnime", "Anime episode ${episode.number} failed to resolve", it)
          errorMessage = it.message ?: "That episode could not be started."
        }
      preparing = null
    }
  }

  BoxWithConstraints(
    modifier =
      modifier.fillMaxSize()
        .background(
          Brush.radialGradient(
            colors = listOf(Color(0xFF2A1E52), DeepSpace),
            radius = 1_400f,
            center = androidx.compose.ui.geometry.Offset(400f, 200f),
          )
        )
  ) {
    val narrow = maxWidth < 720.dp
    val compact = maxHeight < 600.dp

    val episodeSection: @Composable (Modifier) -> Unit = { sectionModifier ->
      when {
        loading -> StatusPanel("Loading episodes…", sectionModifier, loading = true)
        // Reached only when the listing filter missed it, which means the title is not filed under
        // an adult genre but states the rating itself. Nothing is offered to play.
        details.isAdult ->
          StatusPanel("This title is adult-rated and is not available here.", sectionModifier)
        episodes.isEmpty() ->
          StatusPanel(
            message = errorMessage ?: "No episodes listed for this title.",
            modifier = sectionModifier,
          )
        else ->
          Column(modifier = sectionModifier) {
            Row(verticalAlignment = Alignment.CenterVertically) {
              Text("Episodes", color = SoftWhite, fontWeight = FontWeight.Black, fontSize = 16.sp)
              Spacer(Modifier.width(10.dp))
              Text(
                "${episodes.size} total",
                color = GizMint,
                fontWeight = FontWeight.Bold,
                fontSize = 11.sp,
              )
            }
            // Only worth a row when there is a choice; plenty of titles carry one language.
            if (languages.size > 1) {
              Spacer(Modifier.height(8.dp))
              ChipRow(
                labels = languages.map(AnimeLanguage::name),
                selectedIndex = languages.indexOfFirst { it.code == selectedLanguage },
                onSelect = { index ->
                  languages.getOrNull(index)?.let {
                    selectedLanguage = it.code
                    uiPreferences.setAnimeLanguage(it.code)
                  }
                },
                firstChipFocusRequester = languageFocusRequester,
                semanticsRole = Role.RadioButton,
                up = backFocusRequester,
                down = firstEpisodeFocusRequester,
                compactChips = true,
              )
            }
            errorMessage?.takeIf { episodes.isNotEmpty() }?.let { message ->
              Spacer(Modifier.height(8.dp))
              Text(message, color = Color(0xFFFF8A80), fontSize = 11.sp, fontWeight = FontWeight.Bold)
            }
            Spacer(Modifier.height(8.dp))
            LazyVerticalGrid(
              state = episodeGridState,
              columns = GridCells.Adaptive(minSize = if (narrow) 66.dp else 78.dp),
              modifier = Modifier.fillMaxWidth().weight(1f),
              contentPadding = PaddingValues(bottom = 24.dp),
              horizontalArrangement = Arrangement.spacedBy(8.dp),
              verticalArrangement = Arrangement.spacedBy(8.dp),
            ) {
              items(items = episodes, key = AnimeEpisode::id) { episode ->
                AnimeEpisodeTile(
                  episode = episode,
                  history = historyStore.find(animeEpisodeIdentity(anime.slug, episode.number)),
                  preparing = preparing == episode.number,
                  onClick = { play(episode) },
                  modifier =
                    if (episode.id == episodes.first().id) {
                      Modifier.focusRequester(firstEpisodeFocusRequester).focusProperties {
                        up = if (languages.size > 1) languageFocusRequester else backFocusRequester
                      }
                    } else {
                      Modifier
                    },
                )
              }
            }
          }
      }
    }

    val summary: @Composable (Modifier) -> Unit = { summaryModifier ->
      Column(modifier = summaryModifier) {
        Row(verticalAlignment = Alignment.CenterVertically) {
          TmdbArtwork(
            url = anime.posterUrl,
            contentDescription = "${anime.title} artwork",
            modifier =
              Modifier.width(if (narrow) 78.dp else 122.dp).aspectRatio(2f / 3f)
                .clip(RoundedCornerShape(12.dp)),
            compact = narrow,
            fallbackLabel = "No artwork",
          )
          Spacer(Modifier.width(14.dp))
          Column(Modifier.weight(1f)) {
            Text(
              anime.title,
              color = SoftWhite,
              fontWeight = FontWeight.Black,
              fontSize = if (narrow) 18.sp else 23.sp,
              maxLines = 3,
              overflow = TextOverflow.Ellipsis,
              lineHeight = if (narrow) 22.sp else 27.sp,
            )
            val headline =
              listOfNotNull(
                details.fact("Type") ?: anime.kind,
                details.fact("Season"),
                details.fact("Status"),
              )
            if (headline.isNotEmpty()) {
              Spacer(Modifier.height(6.dp))
              Text(
                headline.joinToString(" · "),
                color = MutedBlue,
                fontSize = 12.sp,
                maxLines = 2,
                overflow = TextOverflow.Ellipsis,
              )
            }
            val accents =
              listOfNotNull(
                (details.fact("Score") ?: anime.score)?.let { "★ $it" },
                details.fact("Studios"),
                details.fact("Duration"),
              )
            if (accents.isNotEmpty()) {
              Spacer(Modifier.height(8.dp))
              Text(
                accents.joinToString(" · "),
                color = GizMint,
                fontWeight = FontWeight.Bold,
                fontSize = 11.sp,
                maxLines = 2,
                overflow = TextOverflow.Ellipsis,
              )
            }
          }
        }
        if (details.synopsis.isNotBlank() && !compact) {
          Spacer(Modifier.height(14.dp))
          Text(
            details.synopsis,
            color = MutedBlue,
            fontSize = 13.sp,
            lineHeight = 19.sp,
            modifier = Modifier.verticalScroll(rememberScrollState()),
          )
        }
      }
    }

    Column(
      modifier =
        Modifier.fillMaxSize().padding(
          horizontal = if (narrow) 18.dp else 42.dp,
          vertical = if (compact) 14.dp else 22.dp,
        )
    ) {
      Row(modifier = Modifier.fillMaxWidth(), verticalAlignment = Alignment.CenterVertically) {
        CatalogButton(
          label = "Back",
          onClick = onBack,
          modifier =
            Modifier.focusRequester(backFocusRequester).focusProperties {
              down = if (languages.size > 1) languageFocusRequester else firstEpisodeFocusRequester
            },
        )
        Spacer(Modifier.width(14.dp))
        Text("ANIME", color = MutedBlue, fontWeight = FontWeight.Bold, letterSpacing = 2.sp, fontSize = 11.sp)
      }
      Spacer(Modifier.height(if (compact) 10.dp else 18.dp))
      if (narrow) {
        summary(Modifier.fillMaxWidth())
        Spacer(Modifier.height(14.dp))
        episodeSection(Modifier.weight(1f))
      } else {
        Row(Modifier.weight(1f)) {
          summary(Modifier.weight(.42f).fillMaxHeight())
          Spacer(Modifier.width(26.dp))
          episodeSection(Modifier.weight(.58f).fillMaxHeight())
        }
      }
    }
  }
}

/** One numbered episode, marked when watched and while its stream is being fetched. */
@Composable
private fun AnimeEpisodeTile(
  episode: AnimeEpisode,
  history: WatchHistoryEntry?,
  preparing: Boolean,
  onClick: () -> Unit,
  modifier: Modifier = Modifier,
) {
  var focused by remember { mutableStateOf(false) }
  val scale by animateFloatAsState(if (focused) 1.08f else 1f, label = "episode scale")
  val background by
    animateColorAsState(
      when {
        preparing -> GizMint.copy(alpha = .45f)
        focused -> GizMint
        history?.completed == true -> SoftWhite.copy(alpha = .16f)
        else -> NightSurface
      },
      label = "episode background",
    )
  val outline by
    animateColorAsState(
      if (focused) SoftWhite else SoftWhite.copy(alpha = .1f),
      label = "episode outline",
    )
  Column(
    modifier =
      modifier.height(52.dp).graphicsLayer { scaleX = scale; scaleY = scale }
        .clip(RoundedCornerShape(12.dp)).background(background)
        .border(if (focused) 2.dp else 1.dp, outline, RoundedCornerShape(12.dp))
        .onFocusChanged { focused = it.isFocused }.clickable(onClick = onClick)
        .semantics {
          role = Role.Button
          contentDescription =
            buildString {
              append(if (preparing) "Starting episode " else "Play episode ")
              append(episode.number)
              if (episode.filler) append(", filler")
              if (history?.completed == true) append(", watched")
            }
        },
    horizontalAlignment = Alignment.CenterHorizontally,
    verticalArrangement = Arrangement.Center,
  ) {
    Row(verticalAlignment = Alignment.CenterVertically) {
      Text(
        "${episode.number}",
        color = if (focused) DeepSpace else SoftWhite,
        fontWeight = FontWeight.Black,
        fontSize = 15.sp,
      )
      if (history?.completed == true) {
        Spacer(Modifier.width(4.dp))
        Text("✓", color = if (focused) DeepSpace else GizMint, fontWeight = FontWeight.Black, fontSize = 11.sp)
      }
    }
    when {
      // Resolving takes a round trip through the embed, which is long enough to need saying.
      preparing -> {
        Spacer(Modifier.height(3.dp))
        Text("…", color = DeepSpace, fontWeight = FontWeight.Black, fontSize = 12.sp)
      }
      // Filler is worth flagging: it is the one thing anime viewers routinely skip.
      episode.filler ->
        Text(
          "FILLER",
          color = if (focused) DeepSpace else MutedBlue,
          fontWeight = FontWeight.Black,
          fontSize = 7.sp,
        )
      history != null && history.started && !history.completed -> {
        Spacer(Modifier.height(5.dp))
        Box(
          Modifier.width(30.dp).height(3.dp).clip(RoundedCornerShape(2.dp))
            .background(DeepSpace.copy(alpha = .7f))
        ) {
          Box(Modifier.fillMaxWidth(history.progressFraction).fillMaxHeight().background(GizMint))
        }
      }
    }
  }
}
