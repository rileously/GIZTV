package com.giztv.tv.ui.player

import androidx.compose.animation.AnimatedVisibility
import androidx.compose.animation.fadeIn
import androidx.compose.animation.fadeOut
import androidx.compose.animation.slideInHorizontally
import androidx.compose.animation.slideOutHorizontally
import androidx.compose.foundation.background
import androidx.compose.foundation.border
import androidx.compose.foundation.clickable
import androidx.compose.foundation.gestures.detectHorizontalDragGestures
import androidx.compose.foundation.gestures.detectTapGestures
import androidx.compose.foundation.layout.Arrangement
import androidx.compose.foundation.layout.Box
import androidx.compose.foundation.layout.Column
import androidx.compose.foundation.layout.PaddingValues
import androidx.compose.foundation.layout.Row
import androidx.compose.foundation.layout.Spacer
import androidx.compose.foundation.layout.aspectRatio
import androidx.compose.foundation.layout.fillMaxHeight
import androidx.compose.foundation.layout.fillMaxSize
import androidx.compose.foundation.layout.fillMaxWidth
import androidx.compose.foundation.layout.height
import androidx.compose.foundation.layout.heightIn
import androidx.compose.foundation.layout.padding
import androidx.compose.foundation.layout.size
import androidx.compose.foundation.layout.width
import androidx.compose.foundation.layout.widthIn
import androidx.compose.foundation.lazy.grid.GridCells
import androidx.compose.foundation.lazy.grid.LazyVerticalGrid
import androidx.compose.foundation.lazy.grid.items
import androidx.compose.foundation.lazy.grid.rememberLazyGridState
import androidx.compose.foundation.rememberScrollState
import androidx.compose.foundation.shape.RoundedCornerShape
import androidx.compose.foundation.verticalScroll
import androidx.compose.material.icons.Icons
import androidx.compose.material.icons.automirrored.filled.KeyboardArrowRight
import androidx.compose.material.icons.filled.Close
import androidx.compose.material.icons.filled.KeyboardArrowDown
import androidx.compose.material.icons.filled.KeyboardArrowUp
import androidx.compose.material3.Icon
import androidx.compose.runtime.Composable
import androidx.compose.runtime.LaunchedEffect
import androidx.compose.runtime.remember
import androidx.compose.ui.Alignment
import androidx.compose.ui.Modifier
import androidx.compose.ui.draw.clip
import androidx.compose.ui.graphics.Brush
import androidx.compose.ui.graphics.Color
import androidx.compose.ui.input.pointer.pointerInput
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
import com.giztv.tv.data.ReelTitle
import com.giztv.tv.data.WatchHistoryStore
import com.giztv.tv.theme.DeepSpace
import com.giztv.tv.theme.GizMint
import com.giztv.tv.theme.MutedBlue
import com.giztv.tv.theme.NightSurface
import com.giztv.tv.theme.SoftWhite
import com.giztv.tv.ui.catalog.TmdbArtwork
import kotlin.math.abs

/**
 * Reel navigation: what a phone-sized short drama does with a swipe.
 *
 * A short drama is browsed the way a feed is, so the player itself offers the next drama and the
 * rest of this one's episodes. Everything here is phone-only and short-form only — a remote has no
 * swipe, and a two-hour film has neither a reel to sit in nor eighty episodes to riffle through.
 *
 * The gestures the player already had are kept rather than replaced: the outer edges still own
 * brightness and volume, so only the middle of the picture navigates.
 */
internal enum class ReelGestureZone {
  BRIGHTNESS,
  NAVIGATE,
  VOLUME,
}

/**
 * How much of each side keeps its existing job.
 *
 * Narrower than the half-and-half split a full-length title uses, because reel navigation is the
 * gesture a viewer reaches for most here and it needs the middle of the screen to land in.
 */
private const val REEL_EDGE_FRACTION = .22f

/** How far up or down the picture a swipe has to travel before it counts as one. */
private const val REEL_TITLE_SWIPE_FRACTION = .16f

/** The same, across. Shorter, because there is less width than height on a phone in portrait. */
private const val REEL_PANEL_SWIPE_FRACTION = .18f

/**
 * How much a drag moves the picture when there is nothing that way, and how far it may get.
 *
 * The end of a reel should feel like the end of one rather than like a swipe that went unnoticed,
 * so the screen gives a little and springs back instead of either following the finger into
 * nothing or refusing to move at all.
 */
private const val REEL_RUBBER_BAND = .32f

private const val REEL_RUBBER_BAND_LIMIT = .1f

/** How long the first-run gesture hint stays up. */
internal const val REEL_HINT_VISIBLE_MS = 5_000L

/** Where a vertical drag began decides what it does for the whole gesture. */
internal fun reelGestureZone(
  startX: Float,
  widthPx: Int,
  edgeFraction: Float = REEL_EDGE_FRACTION,
): ReelGestureZone {
  val width = widthPx.coerceAtLeast(1).toFloat()
  val edge = width * edgeFraction.coerceIn(0f, .5f)
  return when {
    startX < edge -> ReelGestureZone.BRIGHTNESS
    startX > width - edge -> ReelGestureZone.VOLUME
    else -> ReelGestureZone.NAVIGATE
  }
}

internal enum class ReelSwipe {
  NEXT_TITLE,
  PREVIOUS_TITLE,
}

/**
 * Whether a finished drag was a swipe to another drama.
 *
 * Up brings the next one, the way every feed works. A drag that travelled further across than it
 * did up or down was aimed at the episodes panel instead, so it is left alone here — a swipe rarely
 * runs perfectly straight, and the axis it leans on is the one the viewer meant.
 */
internal fun reelTitleSwipe(
  totalDragX: Float,
  totalDragY: Float,
  heightPx: Int,
  triggerFraction: Float = REEL_TITLE_SWIPE_FRACTION,
): ReelSwipe? {
  if (heightPx <= 0) return null
  if (abs(totalDragY) <= abs(totalDragX)) return null
  if (abs(totalDragY) < heightPx * triggerFraction) return null
  return if (totalDragY < 0f) ReelSwipe.NEXT_TITLE else ReelSwipe.PREVIOUS_TITLE
}

/** Where a swipe would land, or null when the reel has nothing that way. */
internal fun reelSwipeTarget(playback: PlaybackContext?, swipe: ReelSwipe): ReelTitle? =
  when (swipe) {
    ReelSwipe.NEXT_TITLE -> playback?.nextReelTitle
    ReelSwipe.PREVIOUS_TITLE -> playback?.previousReelTitle
  }

/**
 * How far the incoming drama has been pulled across the picture, in pixels.
 *
 * The swipe has to be visible while the finger is still down — a gesture that does nothing until it
 * is released cannot be learned, and cannot be abandoned half way either. Negative is upward, which
 * is the direction the next drama arrives from.
 */
internal fun reelSlideOffset(totalDragY: Float, heightPx: Int, hasTarget: Boolean): Float {
  if (heightPx <= 0) return 0f
  val limit = heightPx.toFloat()
  if (!hasTarget) {
    val give = limit * REEL_RUBBER_BAND_LIMIT
    return (totalDragY * REEL_RUBBER_BAND).coerceIn(-give, give)
  }
  return totalDragY.coerceIn(-limit, limit)
}

/**
 * Where the top of the incoming cover sits, given how far the drag has got.
 *
 * It starts a whole screen away in the direction it is coming from and arrives at zero, so that
 * releasing a completed swipe leaves it exactly filling the screen.
 */
internal fun reelCoverTopOffset(swipe: ReelSwipe, slidePx: Float, heightPx: Int): Float =
  when (swipe) {
    ReelSwipe.NEXT_TITLE -> heightPx + slidePx
    ReelSwipe.PREVIOUS_TITLE -> -heightPx + slidePx
  }

internal enum class ReelPanelSwipe {
  OPEN,
  CLOSE,
}

/** Left pulls the episodes panel in from the right edge; right pushes it back out. */
internal fun reelPanelSwipe(
  totalDragX: Float,
  totalDragY: Float,
  widthPx: Int,
  triggerFraction: Float = REEL_PANEL_SWIPE_FRACTION,
): ReelPanelSwipe? {
  if (widthPx <= 0) return null
  if (abs(totalDragX) <= abs(totalDragY)) return null
  if (abs(totalDragX) < widthPx * triggerFraction) return null
  return if (totalDragX < 0f) ReelPanelSwipe.OPEN else ReelPanelSwipe.CLOSE
}

/**
 * The standing caption, as a feed has.
 *
 * Shown while the controls are down, so there is always something saying what this is and how far
 * through it the viewer has got. Tapping it opens the panel, which is the same thing the left swipe
 * does — a gesture nobody has been told about should not be the only way in.
 */
@Composable
internal fun ReelCaption(
  title: String,
  episodeNumber: Int?,
  episodeCount: Int,
  genres: List<String>,
  onOpenEpisodes: () -> Unit,
  modifier: Modifier = Modifier,
) {
  Column(
    modifier =
      modifier.widthIn(max = 320.dp).clip(RoundedCornerShape(14.dp))
        .background(Color.Black.copy(alpha = .42f))
        .clickable(onClick = onOpenEpisodes)
        .padding(horizontal = 14.dp, vertical = 10.dp)
        .semantics {
          role = Role.Button
          contentDescription = "$title. Open episodes and details"
        }
  ) {
    Text(
      title,
      color = SoftWhite,
      fontWeight = FontWeight.Black,
      fontSize = 15.sp,
      maxLines = 2,
      overflow = TextOverflow.Ellipsis,
      lineHeight = 18.sp,
    )
    if (genres.isNotEmpty()) {
      Spacer(Modifier.height(3.dp))
      Text(
        genres.take(3).joinToString(" · "),
        color = MutedBlue,
        fontSize = 10.sp,
        maxLines = 1,
        overflow = TextOverflow.Ellipsis,
      )
    }
    Spacer(Modifier.height(6.dp))
    Row(verticalAlignment = Alignment.CenterVertically) {
      Text(
        episodeLabel(episodeNumber, episodeCount),
        color = GizMint,
        fontWeight = FontWeight.Bold,
        fontSize = 11.sp,
      )
      Icon(
        Icons.AutoMirrored.Filled.KeyboardArrowRight,
        contentDescription = null,
        tint = GizMint,
        modifier = Modifier.size(15.dp),
      )
    }
  }
}

private fun episodeLabel(episodeNumber: Int?, episodeCount: Int): String =
  when {
    episodeNumber == null -> "Episodes"
    episodeCount > 1 -> "Episode $episodeNumber of $episodeCount"
    else -> "Episode $episodeNumber"
  }

/**
 * The drama being swiped onto, following the finger across the picture.
 *
 * Its cover is what a viewer sees for the whole swipe and for as long afterwards as the stream
 * takes to open, which is why the same cover fills the preparing page: nothing flashes, nothing is
 * replaced, and the swipe reads as instant even when the resolve is not.
 */
@Composable
internal fun ReelSwipeCover(
  title: ReelTitle,
  /** Where this drama will pick up, which is not episode one once it has been started. */
  episode: Int,
  modifier: Modifier = Modifier,
) {
  Box(modifier = modifier.background(Color.Black)) {
    TmdbArtwork(
      url = title.posterUrl,
      contentDescription = "${title.title} cover",
      modifier = Modifier.fillMaxSize(),
      fallbackLabel = title.title,
    )
    // The art is a portrait cover cropped to a portrait screen, so the words need their own ground
    // to stand on rather than whatever happens to be behind them.
    Box(
      Modifier.align(Alignment.BottomCenter).fillMaxWidth().fillMaxHeight(.42f)
        .background(
          Brush.verticalGradient(listOf(Color.Transparent, Color.Black.copy(alpha = .85f)))
        )
    )
    Column(
      modifier = Modifier.align(Alignment.BottomStart).padding(start = 20.dp, end = 20.dp, bottom = 92.dp)
    ) {
      Text(
        title.title,
        color = SoftWhite,
        fontWeight = FontWeight.Black,
        fontSize = 22.sp,
        maxLines = 3,
        overflow = TextOverflow.Ellipsis,
        lineHeight = 26.sp,
      )
      Spacer(Modifier.height(4.dp))
      Text(
        // Saying "Resume" is the point: a drama that is being come back to should say so before it
        // opens, not surprise the viewer by starting in the middle.
        if (episode > 1) "Resume · ${episodeLabel(episode, title.episodeCount)}"
        else episodeLabel(1, title.episodeCount),
        color = if (episode > 1) GizMint else MutedBlue,
        fontWeight = if (episode > 1) FontWeight.Bold else FontWeight.Normal,
        fontSize = 12.sp,
      )
    }
  }
}

/**
 * What letting go would do, said while the swipe is still under way.
 *
 * Anchored to the screen rather than to the cover: the cover's own words are at its foot, which for
 * most of an upward swipe is still below the bottom of the screen, and a cue nobody can see during
 * the gesture it describes is no cue at all.
 */
@Composable
internal fun ReelSwipeCue(swipe: ReelSwipe, committed: Boolean, modifier: Modifier = Modifier) {
  Row(
    modifier =
      modifier.clip(RoundedCornerShape(20.dp))
        .background(if (committed) GizMint else Color.Black.copy(alpha = .66f))
        .padding(horizontal = 14.dp, vertical = 7.dp),
    verticalAlignment = Alignment.CenterVertically,
    horizontalArrangement = Arrangement.spacedBy(4.dp),
  ) {
    Icon(
      if (swipe == ReelSwipe.NEXT_TITLE) Icons.Filled.KeyboardArrowUp
      else Icons.Filled.KeyboardArrowDown,
      contentDescription = null,
      tint = if (committed) DeepSpace else GizMint,
      modifier = Modifier.size(16.dp),
    )
    Text(
      if (committed) "Release to play" else "Keep pulling",
      color = if (committed) DeepSpace else SoftWhite,
      fontWeight = FontWeight.Black,
      fontSize = 11.sp,
      letterSpacing = 1.sp,
    )
  }
}

/** Said once, the first time a short drama is played on a phone. */
@Composable
internal fun ReelGestureHint(modifier: Modifier = Modifier) {
  Column(
    modifier =
      modifier.clip(RoundedCornerShape(16.dp)).background(Color.Black.copy(alpha = .72f))
        .padding(horizontal = 18.dp, vertical = 12.dp),
    horizontalAlignment = Alignment.CenterHorizontally,
  ) {
    Text("Swipe up for another drama", color = SoftWhite, fontWeight = FontWeight.Black, fontSize = 13.sp)
    Spacer(Modifier.height(4.dp))
    Text("Swipe left for episodes and details", color = MutedBlue, fontSize = 12.sp)
  }
}

/**
 * Everything about the drama being watched, and every episode of it.
 *
 * The panel is what the left swipe is for: the details a listing would have shown, and a numbered
 * grid to jump around the run with, without leaving the picture playing behind it.
 */
@Composable
internal fun ReelDetailsPanel(
  playback: PlaybackContext,
  visible: Boolean,
  onSelectEpisode: (PlaylistEntry) -> Unit,
  onSelectTitle: (ReelTitle) -> Unit,
  onClose: () -> Unit,
  modifier: Modifier = Modifier,
) {
  AnimatedVisibility(
    visible = visible,
    enter = slideInHorizontally { it } + fadeIn(),
    exit = slideOutHorizontally { it } + fadeOut(),
    modifier = modifier,
  ) {
    val context = LocalContext.current
    val historyStore = remember(context) { WatchHistoryStore(context) }
    val gridState = rememberLazyGridState()
    val details = playback.currentReelTitle
    val episodes = playback.playlist
    val currentIndex = episodes.indexOfFirst { it.pageUrl == playback.pageUrl }
    val nextTitle = playback.nextReelTitle

    // Eighty episodes in and the grid should open where the viewer is, not back at the beginning.
    LaunchedEffect(visible, currentIndex) {
      if (visible && currentIndex > 0) gridState.scrollToItem(currentIndex)
    }

    Column(
      modifier =
        Modifier.fillMaxHeight().widthIn(max = 400.dp).fillMaxWidth(.88f)
          .background(DeepSpace.copy(alpha = .97f))
          // The panel keeps its own touches: a tap inside it is never a tap on the picture.
          .pointerInput(Unit) { detectTapGestures {} }
          .pointerInput(Unit) {
            var totalDragX = 0f
            var totalDragY = 0f
            detectHorizontalDragGestures(
              onDragStart = {
                totalDragX = 0f
                totalDragY = 0f
              },
              onHorizontalDrag = { change, dragAmount ->
                change.consume()
                totalDragX += dragAmount
                totalDragY += change.position.y - change.previousPosition.y
              },
              onDragEnd = {
                if (reelPanelSwipe(totalDragX, totalDragY, size.width) == ReelPanelSwipe.CLOSE) {
                  onClose()
                }
              },
            )
          }
          .padding(horizontal = 16.dp, vertical = 18.dp)
    ) {
      Row(verticalAlignment = Alignment.Top) {
        TmdbArtwork(
          url = details?.posterUrl ?: playback.posterUrl,
          contentDescription = "${playback.title} cover",
          modifier = Modifier.width(66.dp).aspectRatio(2f / 3f).clip(RoundedCornerShape(10.dp)),
          compact = true,
          fallbackLabel = "No cover",
        )
        Spacer(Modifier.width(12.dp))
        Column(Modifier.weight(1f)) {
          Text(
            playback.title,
            color = SoftWhite,
            fontWeight = FontWeight.Black,
            fontSize = 17.sp,
            maxLines = 3,
            overflow = TextOverflow.Ellipsis,
            lineHeight = 21.sp,
          )
          val genres = details?.genres.orEmpty().ifEmpty { playback.genres }
          if (genres.isNotEmpty()) {
            Spacer(Modifier.height(5.dp))
            Text(
              genres.take(4).joinToString(" · "),
              color = GizMint,
              fontWeight = FontWeight.Bold,
              fontSize = 10.sp,
              maxLines = 2,
              overflow = TextOverflow.Ellipsis,
            )
          }
          Spacer(Modifier.height(5.dp))
          Text(
            episodeLabel(playback.episodeNumber, episodes.size),
            color = MutedBlue,
            fontSize = 11.sp,
          )
        }
        Spacer(Modifier.width(6.dp))
        Icon(
          Icons.Filled.Close,
          contentDescription = "Close details",
          tint = SoftWhite,
          modifier =
            Modifier.size(30.dp).clip(RoundedCornerShape(15.dp))
              .background(NightSurface)
              .clickable(onClick = onClose)
              .padding(6.dp),
        )
      }

      val synopsis = details?.overview ?: playback.overview
      if (!synopsis.isNullOrBlank()) {
        Spacer(Modifier.height(12.dp))
        Text(
          synopsis,
          color = MutedBlue,
          fontSize = 12.sp,
          lineHeight = 18.sp,
          modifier = Modifier.heightIn(max = 116.dp).verticalScroll(rememberScrollState()),
        )
      }

      if (episodes.isNotEmpty()) {
        Spacer(Modifier.height(14.dp))
        Text("Episodes", color = SoftWhite, fontWeight = FontWeight.Black, fontSize = 13.sp)
        Spacer(Modifier.height(8.dp))
        LazyVerticalGrid(
          state = gridState,
          columns = GridCells.Adaptive(minSize = 58.dp),
          modifier = Modifier.fillMaxWidth().weight(1f),
          contentPadding = PaddingValues(bottom = 12.dp),
          horizontalArrangement = Arrangement.spacedBy(7.dp),
          verticalArrangement = Arrangement.spacedBy(7.dp),
        ) {
          items(items = episodes, key = { it.pageUrl }) { entry ->
            val history = historyStore.find(entry.pageUrl)
            ReelEpisodeTile(
              episode = entry.episodeNumber,
              playing = entry.pageUrl == playback.pageUrl,
              watched = history?.completed == true,
              onClick = { onSelectEpisode(entry) },
            )
          }
        }
      } else {
        Spacer(Modifier.weight(1f))
      }

      // The same swing the up-swipe makes, for anyone who would rather press it.
      nextTitle?.let { next ->
        Spacer(Modifier.height(8.dp))
        Row(
          modifier =
            Modifier.fillMaxWidth().clip(RoundedCornerShape(12.dp)).background(NightSurface)
              .clickable { onSelectTitle(next) }
              .padding(horizontal = 12.dp, vertical = 10.dp)
              .semantics {
                role = Role.Button
                contentDescription = "Play next drama, ${next.title}"
              },
          verticalAlignment = Alignment.CenterVertically,
        ) {
          Icon(
            Icons.Filled.KeyboardArrowUp,
            contentDescription = null,
            tint = GizMint,
            modifier = Modifier.size(18.dp),
          )
          Spacer(Modifier.width(8.dp))
          Column(Modifier.weight(1f)) {
            Text("Next drama", color = MutedBlue, fontWeight = FontWeight.Bold, fontSize = 10.sp)
            Text(
              next.title,
              color = SoftWhite,
              fontWeight = FontWeight.Black,
              fontSize = 13.sp,
              maxLines = 1,
              overflow = TextOverflow.Ellipsis,
            )
          }
        }
      }
    }
  }
}

@Composable
private fun ReelEpisodeTile(
  episode: Int,
  playing: Boolean,
  watched: Boolean,
  onClick: () -> Unit,
) {
  val background =
    when {
      playing -> GizMint
      watched -> SoftWhite.copy(alpha = .16f)
      else -> NightSurface
    }
  Box(
    modifier =
      Modifier.height(42.dp).clip(RoundedCornerShape(10.dp)).background(background)
        .border(
          if (playing) 0.dp else 1.dp,
          SoftWhite.copy(alpha = .1f),
          RoundedCornerShape(10.dp),
        )
        .clickable(onClick = onClick)
        .semantics {
          role = Role.Button
          contentDescription =
            "Play episode $episode" +
              when {
                playing -> ", playing now"
                watched -> ", watched"
                else -> ""
              }
        },
    contentAlignment = Alignment.Center,
  ) {
    Row(verticalAlignment = Alignment.CenterVertically) {
      Text(
        "$episode",
        color = if (playing) DeepSpace else SoftWhite,
        fontWeight = FontWeight.Black,
        fontSize = 14.sp,
      )
      if (watched && !playing) {
        Spacer(Modifier.width(3.dp))
        Text("✓", color = GizMint, fontWeight = FontWeight.Black, fontSize = 10.sp)
      }
    }
  }
}
