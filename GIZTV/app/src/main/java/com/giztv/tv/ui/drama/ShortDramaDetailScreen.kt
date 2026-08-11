package com.giztv.tv.ui.drama

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
import com.giztv.tv.data.WatchHistoryEntry
import com.giztv.tv.data.WatchHistoryStore
import com.giztv.tv.theme.GizMint
import com.giztv.tv.theme.DeepSpace
import com.giztv.tv.theme.MutedBlue
import com.giztv.tv.theme.NightSurface
import com.giztv.tv.theme.SoftWhite
import com.giztv.tv.ui.catalog.CatalogButton
import com.giztv.tv.ui.catalog.StatusPanel
import com.giztv.tv.ui.catalog.TmdbArtwork

/**
 * Drama landing page: cover and synopsis on the left, every episode on the right.
 *
 * A short drama is one unbroken run of numbered episodes — often seventy or more — and chartdrama
 * publishes no per-episode titles or stills, so the episodes are a dense numbered grid rather than
 * the season rail and description rows a TMDB show gets.
 */
@Composable
internal fun ShortDramaDetailScreen(
  drama: ShortDrama,
  onPlayEpisode: (PlaybackContext) -> Unit,
  onBack: () -> Unit,
  /**
   * The run this drama was picked out of, so the player can swipe on to the next one.
   *
   * Empty is fine: a drama on its own is simply a reel of one and the swipe finds nothing.
   */
  listing: List<ShortDrama> = emptyList(),
  modifier: Modifier = Modifier,
) {
  val context = LocalContext.current
  val historyStore = remember(context) { WatchHistoryStore(context) }
  val backFocusRequester = remember { FocusRequester() }
  val firstEpisodeFocusRequester = remember { FocusRequester() }
  val episodeGridState = rememberLazyGridState()
  LaunchedEffect(Unit) { backFocusRequester.requestFocus() }

  // Back is handled by GizTvRoot; see the note in ShortDramaScreen.
  // The listing already carries the episode count, so opening a drama costs no request at all.
  val episodeCount = drama.episodeCount
  val episodes = remember(drama.slug, episodeCount) { (1..episodeCount).toList() }
  val tags = drama.tags
  val synopsis = drama.synopsis
  val coverUrl = drama.coverUrl

  // Built once so the player's next-episode prompt can roll through the whole drama, and so a reel
  // swipe has the neighbouring dramas to hand without coming back here for them.
  val reel = remember(drama.slug, listing) { reelAround(drama, listing) }
  val self = remember(drama.slug, episodeCount) { drama.toReelTitle() }

  fun playbackContextFor(episode: Int): PlaybackContext = self.shortDramaPlayback(reel, episode)

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

    val episodeSection: @Composable (Modifier) -> Unit = { sectionModifier ->
      when {
        episodes.isEmpty() -> StatusPanel("No episodes listed for this drama.", sectionModifier)
        else ->
          Column(modifier = sectionModifier) {
            Row(verticalAlignment = Alignment.CenterVertically) {
              Text("Episodes", color = SoftWhite, fontWeight = FontWeight.Black, fontSize = 16.sp)
              Spacer(Modifier.width(10.dp))
              Text(
                "$episodeCount total",
                color = GizMint,
                fontWeight = FontWeight.Bold,
                fontSize = 11.sp,
              )
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
              items(items = episodes, key = { it }) { episode ->
                EpisodeTile(
                  episode = episode,
                  history = historyStore.find(chartDramaEpisodeUrl(drama.slug, episode)),
                  onClick = { onPlayEpisode(playbackContextFor(episode)) },
                  modifier =
                    if (episode == 1) {
                      Modifier.focusRequester(firstEpisodeFocusRequester).focusProperties {
                        up = backFocusRequester
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
            url = coverUrl,
            contentDescription = "${drama.title} cover",
            modifier = Modifier.width(if (narrow) 78.dp else 122.dp).aspectRatio(2f / 3f).clip(RoundedCornerShape(12.dp)),
            compact = narrow,
            fallbackLabel = "No cover",
          )
          Spacer(Modifier.width(14.dp))
          Column(Modifier.weight(1f)) {
            Text(
              drama.title,
              color = SoftWhite,
              fontWeight = FontWeight.Black,
              fontSize = if (narrow) 18.sp else 23.sp,
              maxLines = 3,
              overflow = TextOverflow.Ellipsis,
              lineHeight = if (narrow) 22.sp else 27.sp,
            )
            drama.starring?.takeIf { it.isNotBlank() && it != "-" }?.let {
              Spacer(Modifier.height(6.dp))
              Text(it, color = MutedBlue, fontSize = 12.sp, maxLines = 2, overflow = TextOverflow.Ellipsis)
            }
            if (tags.isNotEmpty()) {
              Spacer(Modifier.height(8.dp))
              Text(
                tags.take(4).joinToString(" · "),
                color = GizMint,
                fontWeight = FontWeight.Bold,
                fontSize = 11.sp,
                maxLines = 2,
                overflow = TextOverflow.Ellipsis,
              )
            }
          }
        }
        if (synopsis.isNotBlank() && !compact) {
          Spacer(Modifier.height(14.dp))
          Text(
            synopsis,
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
              down = firstEpisodeFocusRequester
            },
        )
        Spacer(Modifier.width(14.dp))
        Text("SHORT DRAMA", color = MutedBlue, fontWeight = FontWeight.Bold, letterSpacing = 2.sp, fontSize = 11.sp)
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

/** One numbered episode, marked when it has already been watched or started. */
@Composable
private fun EpisodeTile(
  episode: Int,
  history: WatchHistoryEntry?,
  onClick: () -> Unit,
  modifier: Modifier = Modifier,
) {
  var focused by remember { mutableStateOf(false) }
  val scale by animateFloatAsState(if (focused) 1.08f else 1f, label = "episode scale")
  val background by
    animateColorAsState(
      when {
        focused -> GizMint
        history?.completed == true -> SoftWhite.copy(alpha = .16f)
        else -> NightSurface
      },
      label = "episode background",
    )
  val outline by
    animateColorAsState(if (focused) SoftWhite else SoftWhite.copy(alpha = .1f), label = "episode outline")
  Column(
    modifier =
      modifier.height(52.dp).graphicsLayer { scaleX = scale; scaleY = scale }
        .clip(RoundedCornerShape(12.dp)).background(background)
        .border(if (focused) 2.dp else 1.dp, outline, RoundedCornerShape(12.dp))
        .onFocusChanged { focused = it.isFocused }.clickable(onClick = onClick)
        .semantics {
          role = Role.Button
          contentDescription =
            "Play episode $episode" + if (history?.completed == true) ", watched" else ""
        },
    horizontalAlignment = Alignment.CenterHorizontally,
    verticalArrangement = Arrangement.Center,
  ) {
    Row(verticalAlignment = Alignment.CenterVertically) {
      Text(
        "$episode",
        color = if (focused) DeepSpace else SoftWhite,
        fontWeight = FontWeight.Black,
        fontSize = 15.sp,
      )
      if (history?.completed == true) {
        Spacer(Modifier.width(4.dp))
        Text("✓", color = if (focused) DeepSpace else GizMint, fontWeight = FontWeight.Black, fontSize = 11.sp)
      }
    }
    // A part-watched episode keeps its progress visible, the way the catalog's continue row does.
    if (history != null && history.started && !history.completed) {
      Spacer(Modifier.height(5.dp))
      Box(Modifier.width(30.dp).height(3.dp).clip(RoundedCornerShape(2.dp)).background(DeepSpace.copy(alpha = .7f))) {
        Box(Modifier.fillMaxWidth(history.progressFraction).fillMaxHeight().background(GizMint))
      }
    }
  }
}
