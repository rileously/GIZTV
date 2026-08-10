package com.giztv.tv.ui.catalog

import android.util.Log
import androidx.activity.compose.BackHandler
import androidx.compose.animation.animateColorAsState
import androidx.compose.animation.core.animateFloatAsState
import androidx.compose.foundation.background
import androidx.compose.foundation.border
import androidx.compose.foundation.clickable
import androidx.compose.foundation.focusGroup
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
import androidx.compose.foundation.layout.size
import androidx.compose.foundation.layout.width
import androidx.compose.foundation.lazy.LazyColumn
import androidx.compose.foundation.lazy.LazyRow
import androidx.compose.foundation.lazy.items
import androidx.compose.foundation.lazy.rememberLazyListState
import androidx.compose.foundation.rememberScrollState
import androidx.compose.foundation.shape.RoundedCornerShape
import androidx.compose.foundation.verticalScroll
import androidx.compose.runtime.Composable
import androidx.compose.runtime.LaunchedEffect
import androidx.compose.runtime.getValue
import androidx.compose.runtime.mutableIntStateOf
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
import androidx.compose.ui.semantics.selected
import androidx.compose.ui.semantics.semantics
import androidx.compose.ui.text.font.FontWeight
import androidx.compose.ui.text.style.TextOverflow
import androidx.compose.ui.unit.dp
import androidx.compose.ui.unit.sp
import androidx.tv.material3.Text
import com.giztv.tv.BuildConfig
import com.giztv.tv.data.LibraryItem
import com.giztv.tv.data.LibraryKind
import com.giztv.tv.data.MyListStore
import com.giztv.tv.data.PlaybackContext
import com.giztv.tv.data.PlaylistEntry
import com.giztv.tv.data.UiPreferencesStore
import com.giztv.tv.data.WatchHistoryEntry
import com.giztv.tv.data.WatchHistoryStore
import com.giztv.tv.theme.GizBlue
import com.giztv.tv.theme.GizMint
import com.giztv.tv.theme.DeepSpace
import com.giztv.tv.theme.MutedBlue
import com.giztv.tv.theme.NightSurface
import com.giztv.tv.theme.SoftWhite
import java.util.Locale
import kotlinx.coroutines.launch

/**
 * Show landing page: artwork and synopsis on the left, a season rail and episode list on the right.
 *
 * Selecting a season swaps the episode list in place, so a viewer reaches any episode with at most
 * one horizontal move and one vertical move.
 */
@Composable
internal fun TvShowDetailScreen(
  show: TmdbShow,
  onPlayEpisode: (PlaybackContext) -> Unit,
  onConsidering: (PlaybackContext) -> Unit = {},
  onBack: () -> Unit,
  modifier: Modifier = Modifier,
) {
  val context = LocalContext.current
  val repository = remember { TmdbTvRepository(BuildConfig.TMDB_API_KEY) }
  val historyStore = remember(context) { WatchHistoryStore(context) }
  val myListStore = remember(context) { MyListStore(context) }
  val uiPreferences = remember(context) { UiPreferencesStore(context) }
  val scope = rememberCoroutineScope()
  val backFocusRequester = remember { FocusRequester() }
  val seasonRailFocusRequester = remember { FocusRequester() }
  val firstEpisodeFocusRequester = remember { FocusRequester() }
  val episodeListState = rememberLazyListState()
  var seasons by remember(show.id) { mutableStateOf<List<TmdbSeason>>(emptyList()) }
  var selectedSeason by remember(show.id) { mutableIntStateOf(-1) }
  var episodes by remember(show.id) { mutableStateOf<List<TmdbEpisode>>(emptyList()) }
  var loadingSeasons by remember(show.id) { mutableStateOf(true) }
  var loadingEpisodes by remember(show.id) { mutableStateOf(false) }
  var errorMessage by remember(show.id) { mutableStateOf<String?>(null) }
  var saved by remember(show.id) { mutableStateOf(myListStore.contains(LibraryKind.SHOW, show.id)) }

  fun loadEpisodes(seasonNumber: Int) {
    selectedSeason = seasonNumber
    uiPreferences.setLastSeason(show.id, seasonNumber)
    scope.launch {
      loadingEpisodes = true
      errorMessage = null
      runCatching { repository.episodes(show.id, seasonNumber) }
        .onSuccess { episodes = it }
        .onFailure {
          Log.e("GizTvTmdb", "Episode load failed for ${show.id} S$seasonNumber", it)
          errorMessage = friendlyCatalogError(it)
        }
      loadingEpisodes = false
    }
  }

  LaunchedEffect(show.id) {
    onConsidering(
      PlaybackContext(
        title = show.name,
        pageUrl = vidfastEpisodeUrl(show.id, 1, 1),
        posterUrl = show.posterUrl,
      )
    )
    loadingSeasons = true
    errorMessage = null
    runCatching { repository.seasons(show.id) }
      .onSuccess { loaded ->
        seasons = loaded
        // Drop back into whichever season was last opened for this show.
        val remembered = uiPreferences.lastSeason(show.id)
        val target = loaded.firstOrNull { it.seasonNumber == remembered } ?: loaded.firstOrNull()
        target?.let { loadEpisodes(it.seasonNumber) }
      }
      .onFailure {
        Log.e("GizTvTmdb", "Season load failed for ${show.id}", it)
        errorMessage = friendlyCatalogError(it)
      }
    loadingSeasons = false
  }

  // Reset the scroll only once the list is actually laid out; scrolling a detached
  // LazyListState suspends forever and would strand the loading state.
  LaunchedEffect(selectedSeason, loadingEpisodes) {
    if (!loadingEpisodes && episodes.isNotEmpty()) episodeListState.scrollToItem(0)
  }

  LaunchedEffect(Unit) { backFocusRequester.requestFocus() }

  BackHandler(onBack = onBack)

  val playlist =
    remember(episodes) {
      episodes.map {
        PlaylistEntry(
          episodeNumber = it.episodeNumber,
          name = it.name,
          pageUrl = vidfastEpisodeUrl(show.id, it.seasonNumber, it.episodeNumber),
          overview = it.overview,
          runtimeMinutes = it.runtimeMinutes,
          airDate = it.airDate,
          rating = it.voteAverage.takeIf { rating -> rating > 0.0 },
        )
      }
    }

  BoxWithConstraints(
    modifier =
      modifier.fillMaxSize()
        .background(
          Brush.radialGradient(
            colors = listOf(Color(0xFF17345C), DeepSpace),
            radius = 1_400f,
            center = androidx.compose.ui.geometry.Offset(400f, 200f),
          )
        )
  ) {
    val narrow = maxWidth < 720.dp
    val compact = maxHeight < 600.dp
    val currentSeason = seasons.firstOrNull { it.seasonNumber == selectedSeason }

    val seasonRail: @Composable () -> Unit = {
      SeasonRail(
        seasons = seasons,
        selectedSeason = selectedSeason,
        onSelect = ::loadEpisodes,
        railFocusRequester = seasonRailFocusRequester,
        backFocusRequester = backFocusRequester,
        firstEpisodeFocusRequester = firstEpisodeFocusRequester,
        hasEpisodes = episodes.isNotEmpty(),
      )
    }

    val episodeSection: @Composable (Modifier) -> Unit = { sectionModifier ->
      when {
        loadingEpisodes -> StatusPanel("Loading episodes…", sectionModifier, loading = true)
        errorMessage != null ->
          StatusPanel(
            message = errorMessage ?: "Episodes could not be loaded",
            modifier = sectionModifier,
            actionLabel = "Try again",
            onAction = { if (selectedSeason >= 0) loadEpisodes(selectedSeason) },
          )
        episodes.isEmpty() -> StatusPanel("No episodes listed for this season.", sectionModifier)
        else ->
          LazyColumn(
            state = episodeListState,
            modifier = sectionModifier.fillMaxWidth(),
            contentPadding = PaddingValues(bottom = 24.dp),
            verticalArrangement = Arrangement.spacedBy(10.dp),
          ) {
            items(items = episodes, key = { "${it.seasonNumber}-${it.episodeNumber}" }) { episode ->
              val pageUrl = vidfastEpisodeUrl(show.id, episode.seasonNumber, episode.episodeNumber)
              EpisodeRow(
                episode = episode,
                history = historyStore.find(pageUrl),
                narrow = narrow,
                onClick = {
                  onPlayEpisode(
                    PlaybackContext(
                      pageUrl = pageUrl,
                      title = show.name,
                      subtitle = "S${episode.seasonNumber} · E${episode.episodeNumber} · ${episode.name}",
                      posterUrl = show.posterUrl,
                      year = show.year,
                      overview = episode.overview,
                      runtimeMinutes = episode.runtimeMinutes,
                      airDate = episode.airDate,
                      rating = episode.voteAverage.takeIf { it > 0.0 },
                      showId = show.id,
                      seasonNumber = episode.seasonNumber,
                      episodeNumber = episode.episodeNumber,
                      playlist = playlist,
                    )
                  )
                },
                modifier =
                  if (episode.episodeNumber == episodes.first().episodeNumber) {
                    Modifier.focusRequester(firstEpisodeFocusRequester).focusProperties {
                      up = seasonRailFocusRequester
                    }
                  } else {
                    Modifier
                  },
              )
            }
          }
      }
    }

    val header: @Composable (Boolean) -> Unit = { isNarrow ->
      ShowHeader(
        show = show,
        seasonCount = seasons.size,
        narrow = isNarrow,
        saved = saved,
        onBack = onBack,
        onToggleSaved = { saved = myListStore.toggle(show.toLibraryItem()) },
        backFocusRequester = backFocusRequester,
        seasonRailFocusRequester = seasonRailFocusRequester,
      )
    }

    if (narrow) {
      Column(
        modifier =
          Modifier.fillMaxSize().padding(horizontal = 18.dp, vertical = if (compact) 14.dp else 22.dp)
      ) {
        header(true)
        Spacer(Modifier.height(14.dp))
        if (loadingSeasons) {
          StatusPanel("Loading seasons…", Modifier.weight(1f), loading = true)
        } else {
          seasonRail()
          Spacer(Modifier.height(10.dp))
          EpisodeSectionLabel(currentSeason, episodes.size)
          Spacer(Modifier.height(8.dp))
          episodeSection(Modifier.weight(1f))
        }
      }
    } else {
      Row(
        modifier =
          Modifier.fillMaxSize().padding(horizontal = 42.dp, vertical = if (compact) 16.dp else 26.dp)
      ) {
        Column(
          modifier = Modifier.width(300.dp).fillMaxHeight().verticalScroll(rememberScrollState())
        ) {
          header(false)
        }
        Spacer(Modifier.width(30.dp))
        Column(modifier = Modifier.weight(1f).fillMaxHeight()) {
          if (loadingSeasons) {
            StatusPanel("Loading seasons…", Modifier.weight(1f), loading = true)
          } else {
            seasonRail()
            Spacer(Modifier.height(14.dp))
            EpisodeSectionLabel(currentSeason, episodes.size)
            Spacer(Modifier.height(10.dp))
            episodeSection(Modifier.weight(1f))
          }
        }
      }
    }
  }
}

private fun TmdbShow.toLibraryItem(): LibraryItem =
  LibraryItem(
    id = id,
    kind = LibraryKind.SHOW,
    title = name,
    date = firstAirDate,
    voteAverage = voteAverage,
    overview = overview,
    posterPath = posterPath,
  )

@Composable
private fun ShowHeader(
  show: TmdbShow,
  seasonCount: Int,
  narrow: Boolean,
  saved: Boolean,
  onBack: () -> Unit,
  onToggleSaved: () -> Unit,
  backFocusRequester: FocusRequester,
  seasonRailFocusRequester: FocusRequester,
) {
  val backModifier =
    Modifier.focusRequester(backFocusRequester).focusProperties { down = seasonRailFocusRequester }
      .remoteFocusNavigation(down = seasonRailFocusRequester)
  val saveLabel = if (saved) "✓ Saved" else "+ My List"
  if (narrow) {
    Column {
      Row(verticalAlignment = Alignment.CenterVertically) {
        TmdbArtwork(
          url = show.posterUrl,
          contentDescription = "${show.name} poster",
          modifier = Modifier.width(78.dp).aspectRatio(2f / 3f).clip(RoundedCornerShape(12.dp)),
          compact = true,
        )
        Spacer(Modifier.width(14.dp))
        Column(Modifier.weight(1f)) {
          Text(
            show.name,
            color = SoftWhite,
            fontWeight = FontWeight.Black,
            fontSize = 20.sp,
            maxLines = 2,
            overflow = TextOverflow.Ellipsis,
            lineHeight = 23.sp,
          )
          Spacer(Modifier.height(4.dp))
          Text(showMetaLine(show, seasonCount), color = GizMint, fontSize = 11.sp)
        }
        Spacer(Modifier.width(12.dp))
        CatalogButton("Back", onBack, backModifier)
      }
      Spacer(Modifier.height(10.dp))
      CatalogButton(saveLabel, onToggleSaved)
    }
  } else {
    Column {
      Row(horizontalArrangement = Arrangement.spacedBy(10.dp)) {
        CatalogButton("Back", onBack, backModifier)
        CatalogButton(saveLabel, onToggleSaved)
      }
      Spacer(Modifier.height(18.dp))
      // Sized so the title, rating and synopsis stay above the fold on a 1080p panel.
      TmdbArtwork(
        url = show.posterUrl,
        contentDescription = "${show.name} poster",
        modifier = Modifier.width(196.dp).aspectRatio(2f / 3f).clip(RoundedCornerShape(16.dp)),
        fallbackLabel = "No poster",
      )
      Spacer(Modifier.height(14.dp))
      Text(
        show.name,
        color = SoftWhite,
        fontWeight = FontWeight.Black,
        fontSize = 23.sp,
        lineHeight = 27.sp,
        maxLines = 3,
        overflow = TextOverflow.Ellipsis,
      )
      Spacer(Modifier.height(6.dp))
      Text(showMetaLine(show, seasonCount), color = GizMint, fontSize = 12.sp, fontWeight = FontWeight.Bold)
      if (show.overview.isNotBlank()) {
        Spacer(Modifier.height(12.dp))
        Text(
          show.overview,
          color = MutedBlue,
          fontSize = 12.sp,
          lineHeight = 18.sp,
          maxLines = 6,
          overflow = TextOverflow.Ellipsis,
        )
      }
    }
  }
}

private fun showMetaLine(show: TmdbShow, seasonCount: Int): String =
  buildList {
      show.year?.let(::add)
      if (show.voteAverage > 0) add("★ ${String.format(Locale.US, "%.1f", show.voteAverage)}")
      if (seasonCount > 0) add(if (seasonCount == 1) "1 season" else "$seasonCount seasons")
    }
    .joinToString("  ·  ")

@Composable
private fun EpisodeSectionLabel(season: TmdbSeason?, episodeCount: Int) {
  Row(verticalAlignment = Alignment.CenterVertically) {
    Text(
      season?.name ?: "Episodes",
      color = SoftWhite,
      fontWeight = FontWeight.Black,
      fontSize = 18.sp,
    )
    Spacer(Modifier.weight(1f))
    if (episodeCount > 0) {
      Text(
        if (episodeCount == 1) "1 episode" else "$episodeCount episodes",
        color = GizMint,
        fontSize = 12.sp,
      )
    }
  }
}

@Composable
private fun SeasonRail(
  seasons: List<TmdbSeason>,
  selectedSeason: Int,
  onSelect: (Int) -> Unit,
  railFocusRequester: FocusRequester,
  backFocusRequester: FocusRequester,
  firstEpisodeFocusRequester: FocusRequester,
  hasEpisodes: Boolean,
) {
  if (seasons.isEmpty()) {
    Text("No seasons listed for this show.", color = MutedBlue, fontSize = 13.sp)
    return
  }
  LazyRow(
    modifier = Modifier.fillMaxWidth().focusGroup(),
    horizontalArrangement = Arrangement.spacedBy(10.dp),
    contentPadding = PaddingValues(vertical = 4.dp),
  ) {
    items(items = seasons, key = { it.seasonNumber }) { season ->
      val isFirst = season.seasonNumber == seasons.first().seasonNumber
      SeasonChip(
        season = season,
        selected = season.seasonNumber == selectedSeason,
        onSelect = { onSelect(season.seasonNumber) },
        modifier =
          if (isFirst) {
            Modifier.focusRequester(railFocusRequester).focusProperties {
              up = backFocusRequester
              down = if (hasEpisodes) firstEpisodeFocusRequester else FocusRequester.Default
            }
          } else {
            Modifier.focusProperties {
              up = backFocusRequester
              down = if (hasEpisodes) firstEpisodeFocusRequester else FocusRequester.Default
            }
          },
      )
    }
  }
}

@Composable
private fun SeasonChip(
  season: TmdbSeason,
  selected: Boolean,
  onSelect: () -> Unit,
  modifier: Modifier = Modifier,
) {
  var focused by remember { mutableStateOf(false) }
  val scale by animateFloatAsState(if (focused) 1.07f else 1f, label = "season scale")
  val background by
    animateColorAsState(
      when {
        selected -> GizMint
        focused -> SoftWhite.copy(alpha = .18f)
        else -> NightSurface
      },
      label = "season background",
    )
  val outline by
    animateColorAsState(if (focused) SoftWhite else SoftWhite.copy(alpha = .12f), label = "season outline")
  Column(
    modifier =
      modifier.graphicsLayer { scaleX = scale; scaleY = scale }.clip(RoundedCornerShape(14.dp))
        .background(background).border(if (focused) 2.dp else 1.dp, outline, RoundedCornerShape(14.dp))
        .onFocusChanged { focused = it.isFocused }.clickable(onClick = onSelect)
        .semantics {
          role = Role.Tab
          this.selected = selected
          contentDescription = "${season.name}, ${season.episodeCount} episodes"
        }
        .padding(horizontal = 18.dp, vertical = 10.dp),
    horizontalAlignment = Alignment.CenterHorizontally,
  ) {
    Text(
      season.shortLabel,
      color = if (selected) DeepSpace else SoftWhite,
      fontWeight = FontWeight.Black,
      fontSize = 15.sp,
    )
    Text(
      "${season.episodeCount} eps",
      color = if (selected) DeepSpace.copy(alpha = .7f) else MutedBlue,
      fontSize = 10.sp,
    )
  }
}

@Composable
private fun EpisodeRow(
  episode: TmdbEpisode,
  history: WatchHistoryEntry?,
  narrow: Boolean,
  onClick: () -> Unit,
  modifier: Modifier = Modifier,
) {
  var focused by remember { mutableStateOf(false) }
  val scale by animateFloatAsState(if (focused) 1.015f else 1f, label = "episode scale")
  val background by
    animateColorAsState(
      if (focused) SoftWhite.copy(alpha = .12f) else NightSurface,
      label = "episode background",
    )
  val outline by
    animateColorAsState(if (focused) GizMint else SoftWhite.copy(alpha = .07f), label = "episode outline")
  val watched = history?.completed == true
  val resumeLabel = history?.resumeLabel
  Row(
    modifier =
      modifier.fillMaxWidth().graphicsLayer { scaleX = scale; scaleY = scale }
        .clip(RoundedCornerShape(14.dp)).background(background)
        .border(if (focused) 2.dp else 1.dp, outline, RoundedCornerShape(14.dp))
        .onFocusChanged { focused = it.isFocused }.clickable(onClick = onClick)
        .semantics {
          role = Role.Button
          contentDescription =
            buildString {
              append(if (resumeLabel != null) "Resume" else "Play")
              append(" episode ${episode.episodeNumber}, ${episode.name}")
              if (watched) append(", watched")
            }
        }
        .padding(10.dp),
    verticalAlignment = Alignment.CenterVertically,
  ) {
    Box(
      modifier =
        Modifier.width(if (narrow) 108.dp else 148.dp).aspectRatio(16f / 9f)
          .clip(RoundedCornerShape(10.dp)),
      contentAlignment = Alignment.BottomStart,
    ) {
      TmdbArtwork(
        url = episode.stillUrl,
        contentDescription = "${episode.name} still",
        modifier = Modifier.matchParentSize(),
        compact = true,
      )
      Box(
        modifier =
          Modifier.padding(6.dp).size(24.dp).clip(RoundedCornerShape(8.dp))
            .background(if (watched) GizMint else DeepSpace.copy(alpha = .82f)),
        contentAlignment = Alignment.Center,
      ) {
        Text(
          if (watched) "✓" else "${episode.episodeNumber}",
          color = if (watched) DeepSpace else GizMint,
          fontWeight = FontWeight.Black,
          fontSize = 12.sp,
        )
      }
      // How far into this episode the viewer already got.
      if (history != null && history.progressFraction > 0f) {
        Box(
          modifier = Modifier.fillMaxWidth().height(4.dp).background(DeepSpace.copy(alpha = .7f))
        ) {
          Box(Modifier.fillMaxWidth(history.progressFraction).fillMaxHeight().background(GizMint))
        }
      }
    }
    Spacer(Modifier.width(14.dp))
    Column(Modifier.weight(1f)) {
      Row(verticalAlignment = Alignment.CenterVertically) {
        Text(
          episode.name,
          color = if (watched) MutedBlue else SoftWhite,
          fontWeight = FontWeight.Bold,
          fontSize = if (narrow) 14.sp else 15.sp,
          maxLines = 1,
          overflow = TextOverflow.Ellipsis,
          modifier = Modifier.weight(1f, fill = false),
        )
        episode.runtimeLabel?.let {
          Spacer(Modifier.width(10.dp))
          Text(it, color = MutedBlue, fontSize = 11.sp)
        }
      }
      if (resumeLabel != null) {
        Spacer(Modifier.height(3.dp))
        Text(resumeLabel, color = GizMint, fontWeight = FontWeight.Bold, fontSize = 11.sp)
      }
      if (episode.overview.isNotBlank()) {
        Spacer(Modifier.height(4.dp))
        Text(
          episode.overview,
          color = MutedBlue,
          fontSize = 11.sp,
          lineHeight = 16.sp,
          maxLines = 2,
          overflow = TextOverflow.Ellipsis,
        )
      }
    }
    if (!narrow) {
      Spacer(Modifier.width(12.dp))
      Box(
        modifier =
          Modifier.size(38.dp).clip(RoundedCornerShape(19.dp))
            .background(if (focused) GizMint else GizBlue.copy(alpha = .35f)),
        contentAlignment = Alignment.Center,
      ) {
        Text("▶", color = if (focused) DeepSpace else SoftWhite, fontSize = 15.sp)
      }
    }
  }
}
