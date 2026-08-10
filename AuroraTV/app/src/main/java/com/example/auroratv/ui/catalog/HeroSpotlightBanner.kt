package com.example.auroratv.ui.catalog

import androidx.compose.animation.animateColorAsState
import androidx.compose.animation.core.animateFloatAsState
import androidx.compose.foundation.background
import androidx.compose.foundation.border
import androidx.compose.foundation.clickable
import androidx.compose.foundation.focusGroup
import androidx.compose.foundation.layout.Arrangement
import androidx.compose.foundation.layout.Box
import androidx.compose.foundation.layout.Column
import androidx.compose.foundation.layout.PaddingValues
import androidx.compose.foundation.layout.Row
import androidx.compose.foundation.layout.Spacer
import androidx.compose.foundation.layout.fillMaxHeight
import androidx.compose.foundation.layout.fillMaxSize
import androidx.compose.foundation.layout.fillMaxWidth
import androidx.compose.foundation.layout.height
import androidx.compose.foundation.layout.padding
import androidx.compose.foundation.layout.size
import androidx.compose.foundation.layout.width
import androidx.compose.foundation.lazy.LazyListState
import androidx.compose.foundation.lazy.LazyRow
import androidx.compose.foundation.lazy.items
import androidx.compose.foundation.shape.CircleShape
import androidx.compose.foundation.shape.RoundedCornerShape
import androidx.compose.material.icons.Icons
import androidx.compose.material.icons.filled.Close
import androidx.compose.material.icons.filled.Info
import androidx.compose.material.icons.filled.PlayArrow
import androidx.compose.material.icons.filled.Star
import androidx.compose.material3.Icon
import androidx.compose.runtime.Composable
import androidx.compose.runtime.getValue
import androidx.compose.runtime.mutableStateOf
import androidx.compose.runtime.remember
import androidx.compose.runtime.saveable.rememberSaveable
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
import androidx.compose.ui.graphics.SolidColor
import androidx.compose.ui.graphics.graphicsLayer
import androidx.compose.ui.input.key.Key
import androidx.compose.ui.input.key.KeyEventType
import androidx.compose.ui.input.key.key
import androidx.compose.ui.input.key.onPreviewKeyEvent
import androidx.compose.ui.input.key.type
import androidx.compose.ui.text.font.FontWeight
import androidx.compose.ui.text.style.TextOverflow
import androidx.compose.ui.unit.Dp
import androidx.compose.ui.unit.dp
import androidx.compose.ui.unit.sp
import androidx.tv.material3.Text
import com.example.auroratv.data.WatchHistoryEntry
import com.example.auroratv.theme.AuroraMint
import com.example.auroratv.theme.DeepSpace
import com.example.auroratv.theme.MutedBlue
import com.example.auroratv.theme.NightSurface
import com.example.auroratv.theme.SoftWhite
import java.util.Locale

/**
 * Top full-bleed Netflix/Disney+ style Hero Spotlight Banner for featured movies or shows.
 */
@Composable
internal fun HeroSpotlightBanner(
  title: String,
  subtitle: String?,
  overview: String?,
  rating: Double?,
  posterUrl: String?,
  backdropUrl: String?,
  onPlay: () -> Unit,
  onOpenDetails: () -> Unit,
  firstCardFocusRequester: FocusRequester? = null,
  up: FocusRequester? = null,
  down: FocusRequester? = null,
  onFocused: (FocusRequester) -> Unit = {},
  edge: Dp = 42.dp,
  narrow: Boolean = false,
  modifier: Modifier = Modifier,
) {
  var playButtonFocused by remember { mutableStateOf(false) }
  var detailsButtonFocused by remember { mutableStateOf(false) }
  val localPlayFocusRequester = remember { FocusRequester() }
  val playFocusRequester = firstCardFocusRequester ?: localPlayFocusRequester
  val detailsFocusRequester = remember { FocusRequester() }
  val heroFocused = playButtonFocused || detailsButtonFocused
  val heroScale by animateFloatAsState(if (heroFocused) 1.008f else 1f, label = "hero focus scale")
  val playScale by animateFloatAsState(if (playButtonFocused) 1.05f else 1f, label = "hero play scale")
  val detailsScale by
    animateFloatAsState(if (detailsButtonFocused) 1.05f else 1f, label = "hero details scale")

  Box(
    modifier =
      modifier
        .padding(horizontal = edge, vertical = 4.dp)
        .fillMaxWidth()
        .height(if (narrow) 258.dp else 206.dp)
        .graphicsLayer { scaleX = heroScale; scaleY = heroScale }
        .clip(RoundedCornerShape(20.dp))
        .border(
          width = if (heroFocused) 2.dp else 0.dp,
          brush = if (heroFocused) SolidColor(AuroraMint) else SolidColor(Color.Transparent),
          shape = RoundedCornerShape(20.dp),
        ),
  ) {
    TmdbArtwork(
      url = backdropUrl ?: posterUrl,
      contentDescription = "$title backdrop",
      modifier = Modifier.fillMaxSize(),
      fallbackLabel = "Featured title",
    )
    Box(
      Modifier.fillMaxSize().background(
        Brush.horizontalGradient(
          listOf(
            Color(0xFF03070E).copy(alpha = .98f),
            Color(0xFF03070E).copy(alpha = .82f),
            Color(0xFF03070E).copy(alpha = .20f),
          )
        )
      )
    )
    Box(
      Modifier.fillMaxSize().background(
        Brush.verticalGradient(
          listOf(Color.Transparent, Color(0xFF03070E).copy(alpha = .76f))
        )
      )
    )

    Column(
      modifier =
        Modifier.fillMaxHeight().fillMaxWidth(if (narrow) .86f else .64f)
          .padding(horizontal = if (narrow) 18.dp else 26.dp, vertical = if (narrow) 18.dp else 20.dp),
      verticalArrangement = Arrangement.SpaceBetween,
    ) {
      Column {
        Row(verticalAlignment = Alignment.CenterVertically) {
          GizTvMark(
            modifier = Modifier.size(if (narrow) 22.dp else 24.dp),
            cornerRadius = 6.dp,
          )
          Spacer(Modifier.width(8.dp))
          Text(
            "FEATURED",
            color = AuroraMint,
            fontWeight = FontWeight.Black,
            fontSize = 10.sp,
            letterSpacing = 1.6.sp,
          )
          if (rating != null && rating > 0) {
            Spacer(modifier = Modifier.width(12.dp))
            Icon(
              Icons.Filled.Star,
              contentDescription = null,
              tint = Color(0xFFFFC857),
              modifier = Modifier.size(14.dp),
            )
            Spacer(modifier = Modifier.width(4.dp))
            Text(
              String.format(Locale.US, "%.1f", rating),
              color = SoftWhite,
              fontWeight = FontWeight.Bold,
              fontSize = 12.sp,
            )
          }
          if (!subtitle.isNullOrBlank()) {
            Spacer(modifier = Modifier.width(10.dp))
            Text(subtitle, color = SoftWhite.copy(alpha = .72f), fontSize = 12.sp)
          }
        }

        Spacer(modifier = Modifier.height(if (narrow) 12.dp else 9.dp))
        Text(
          title,
          color = SoftWhite,
          fontWeight = FontWeight.Black,
          fontSize = if (narrow) 25.sp else 29.sp,
          maxLines = if (narrow) 2 else 1,
          overflow = TextOverflow.Ellipsis,
          lineHeight = if (narrow) 29.sp else 32.sp,
        )

        if (!overview.isNullOrBlank()) {
          Spacer(modifier = Modifier.height(7.dp))
          Text(
            overview,
            color = SoftWhite.copy(alpha = .82f),
            fontSize = if (narrow) 13.sp else 12.sp,
            maxLines = if (narrow) 3 else 2,
            overflow = TextOverflow.Ellipsis,
            lineHeight = 17.sp,
          )
        }
      }

      Row(
        horizontalArrangement = Arrangement.spacedBy(10.dp),
        verticalAlignment = Alignment.CenterVertically,
      ) {
        val playModifier =
          Modifier.focusRequester(playFocusRequester).focusProperties {
            if (up != null) this.up = up
            if (down != null) this.down = down
            right = detailsFocusRequester
          }
            .remoteFocusNavigation(up = up, down = down, right = detailsFocusRequester)

        Row(
          modifier =
            playModifier.graphicsLayer { scaleX = playScale; scaleY = playScale }
              .onFocusChanged {
                playButtonFocused = it.isFocused
                if (it.isFocused) onFocused(playFocusRequester)
              }
              .clip(RoundedCornerShape(10.dp))
              .background(SoftWhite)
              .border(
                if (playButtonFocused) 3.dp else 0.dp,
                if (playButtonFocused) AuroraMint else Color.Transparent,
                RoundedCornerShape(10.dp),
              )
              .clickable { onPlay() }
              .padding(horizontal = if (narrow) 18.dp else 20.dp, vertical = 9.dp),
          verticalAlignment = Alignment.CenterVertically,
        ) {
          Icon(Icons.Filled.PlayArrow, null, tint = Color.Black, modifier = Modifier.size(18.dp))
          Spacer(modifier = Modifier.width(6.dp))
          Text("Play", color = Color.Black, fontWeight = FontWeight.Black, fontSize = 13.sp)
        }

        Row(
          modifier =
            Modifier.focusRequester(detailsFocusRequester)
              .focusProperties {
                if (up != null) this.up = up
                if (down != null) this.down = down
                left = playFocusRequester
              }
              .remoteFocusNavigation(up = up, down = down, left = playFocusRequester)
              .graphicsLayer { scaleX = detailsScale; scaleY = detailsScale }
              .onFocusChanged {
                detailsButtonFocused = it.isFocused
                if (it.isFocused) onFocused(detailsFocusRequester)
              }
              .clip(RoundedCornerShape(10.dp))
              .background(
                if (detailsButtonFocused) AuroraMint else Color(0xFF5A626E).copy(alpha = .74f)
              )
              .border(
                if (detailsButtonFocused) 2.dp else 1.dp,
                if (detailsButtonFocused) SoftWhite else SoftWhite.copy(alpha = .28f),
                RoundedCornerShape(10.dp),
              )
              .clickable { onOpenDetails() }
              .padding(horizontal = 16.dp, vertical = 9.dp),
          verticalAlignment = Alignment.CenterVertically,
        ) {
          Icon(
            Icons.Filled.Info,
            contentDescription = null,
            tint = if (detailsButtonFocused) DeepSpace else SoftWhite,
            modifier = Modifier.size(16.dp),
          )
          Spacer(modifier = Modifier.width(6.dp))
          Text(
            "More Info",
            color = if (detailsButtonFocused) DeepSpace else SoftWhite,
            fontWeight = FontWeight.Bold,
            fontSize = 13.sp,
          )
        }
      }
    }
  }
}

/**
 * Filter bar for catalog rails (All, Top Rated, Action, Comedy, Drama, Sci-Fi, etc.).
 */
@Composable
internal fun CatalogFilterRow(
  filters: List<String>,
  selectedFilter: String,
  onSelectFilter: (String) -> Unit,
  firstFilterFocusRequester: FocusRequester,
  up: FocusRequester?,
  down: FocusRequester?,
  onMoveUp: (() -> Unit)? = null,
  onMoveDown: (() -> Unit)? = null,
  edge: Dp = 42.dp,
  modifier: Modifier = Modifier,
) {
  val filterListState = rememberSaveable(saver = LazyListState.Saver) { LazyListState() }
  Row(
    modifier =
      modifier.fillMaxWidth().onPreviewKeyEvent { event ->
        if (event.type != KeyEventType.KeyDown) return@onPreviewKeyEvent false
        when (event.key) {
          Key.DirectionUp -> onMoveUp?.let { it(); true } ?: false
          Key.DirectionDown -> onMoveDown?.let { it(); true } ?: false
          else -> false
        }
      },
    verticalAlignment = Alignment.CenterVertically,
  ) {
    LazyRow(
      state = filterListState,
      modifier = Modifier.fillMaxWidth().focusGroup(),
      contentPadding = PaddingValues(horizontal = edge),
      horizontalArrangement = Arrangement.spacedBy(8.dp),
    ) {
      items(items = filters, key = { it }) { filter ->
        val selected = filter == selectedFilter
        var focused by remember { mutableStateOf(false) }
        val scale by animateFloatAsState(if (focused) 1.06f else 1f, label = "filter chip scale")
        Box(
          modifier =
            Modifier
              .let {
                if (filter == selectedFilter) it.focusRequester(firstFilterFocusRequester)
                else it
              }
              .focusProperties {
                if (up != null) this.up = up
                if (down != null) this.down = down
              }
              .remoteFocusNavigation(up = up)
              .graphicsLayer { scaleX = scale; scaleY = scale }
              .onFocusChanged { focused = it.isFocused }
              .clip(RoundedCornerShape(20.dp))
              .background(
                if (selected) AuroraMint else if (focused) SoftWhite.copy(alpha = 0.22f) else NightSurface.copy(alpha = 0.65f)
              )
              .border(
                width = if (focused) 1.5.dp else 1.dp,
                color = if (selected) AuroraMint else if (focused) SoftWhite.copy(alpha = 0.4f) else SoftWhite.copy(alpha = 0.12f),
                shape = RoundedCornerShape(20.dp),
              )
              .clickable { onSelectFilter(filter) }
              .padding(horizontal = 14.dp, vertical = 7.dp),
        ) {
          Text(
            text = filter,
            color = if (selected) Color.Black else SoftWhite,
            fontWeight = if (selected || focused) FontWeight.Black else FontWeight.Medium,
            fontSize = 12.sp,
          )
        }
      }
    }
  }
}

/**
 * Prominent Quick Resume floating action card for re-opening app to last watched movie/show.
 */
@Composable
internal fun QuickResumeBanner(
  entry: WatchHistoryEntry,
  onResume: () -> Unit,
  onDismiss: () -> Unit,
  edge: Dp = 42.dp,
  modifier: Modifier = Modifier,
) {
  val progressFraction =
    if (entry.durationMs > 0L) (entry.positionMs.toFloat() / entry.durationMs.toFloat()).coerceIn(0f, 1f)
    else 0.5f

  var focused by remember { mutableStateOf(false) }
  val scale by animateFloatAsState(if (focused) 1.02f else 1f, label = "quick resume scale")

  Box(
    modifier =
      modifier
        .padding(horizontal = edge, vertical = 6.dp)
        .fillMaxWidth()
        .graphicsLayer { scaleX = scale; scaleY = scale }
        .onFocusChanged { focused = it.isFocused }
        .clip(RoundedCornerShape(16.dp))
        .background(
          Brush.horizontalGradient(
            colors = listOf(Color(0xFF132A3B), Color(0xFF1E3A52))
          )
        )
        .border(
          width = if (focused) 2.dp else 1.dp,
          color = if (focused) AuroraMint else AuroraMint.copy(alpha = 0.35f),
          shape = RoundedCornerShape(16.dp),
        )
        .clickable { onResume() }
        .padding(horizontal = 14.dp, vertical = 10.dp),
  ) {
    Row(
      modifier = Modifier.fillMaxWidth(),
      verticalAlignment = Alignment.CenterVertically,
      horizontalArrangement = Arrangement.SpaceBetween,
    ) {
      Row(verticalAlignment = Alignment.CenterVertically, modifier = Modifier.weight(1f)) {
        Box(
          modifier =
            Modifier
              .size(36.dp)
              .clip(CircleShape)
              .background(AuroraMint),
          contentAlignment = Alignment.Center,
        ) {
          Icon(
            Icons.Filled.PlayArrow,
            contentDescription = null,
            tint = Color.Black,
            modifier = Modifier.size(20.dp),
          )
        }
        Spacer(modifier = Modifier.width(12.dp))
        Column(modifier = Modifier.weight(1f)) {
          Row(verticalAlignment = Alignment.CenterVertically) {
            Text(
              "RESUME WATCHING",
              color = AuroraMint,
              fontWeight = FontWeight.Black,
              fontSize = 10.sp,
              letterSpacing = 1.2.sp,
            )
            Spacer(modifier = Modifier.width(8.dp))
            Text(
              "${(progressFraction * 100).toInt()}%",
              color = SoftWhite.copy(alpha = 0.7f),
              fontSize = 10.sp,
              fontWeight = FontWeight.Bold,
            )
          }
          Text(
            entry.title,
            color = SoftWhite,
            fontWeight = FontWeight.Bold,
            fontSize = 13.sp,
            maxLines = 1,
            overflow = TextOverflow.Ellipsis,
          )
        }
      }
      Row(verticalAlignment = Alignment.CenterVertically) {
        Box(
          modifier =
            Modifier
              .size(28.dp)
              .clip(CircleShape)
              .background(NightSurface.copy(alpha = 0.6f))
              .clickable { onDismiss() },
          contentAlignment = Alignment.Center,
        ) {
          Icon(
            Icons.Filled.Close,
            contentDescription = "Dismiss quick resume",
            tint = MutedBlue,
            modifier = Modifier.size(16.dp),
          )
        }
      }
    }
  }
}
