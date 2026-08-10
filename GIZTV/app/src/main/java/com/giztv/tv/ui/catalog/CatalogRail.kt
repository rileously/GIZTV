package com.giztv.tv.ui.catalog

import androidx.compose.foundation.focusGroup
import androidx.compose.foundation.layout.Arrangement
import androidx.compose.foundation.layout.Column
import androidx.compose.foundation.layout.PaddingValues
import androidx.compose.foundation.layout.fillMaxWidth
import androidx.compose.foundation.layout.padding
import androidx.compose.foundation.layout.width
import androidx.compose.foundation.lazy.LazyListState
import androidx.compose.foundation.lazy.LazyRow
import androidx.compose.foundation.lazy.items
import androidx.compose.runtime.Composable
import androidx.compose.runtime.remember
import androidx.compose.runtime.saveable.rememberSaveable
import androidx.compose.ui.Modifier
import androidx.compose.ui.focus.FocusRequester
import androidx.compose.ui.focus.focusProperties
import androidx.compose.ui.focus.focusRequester
import androidx.compose.ui.focus.onFocusChanged
import androidx.compose.ui.input.key.Key
import androidx.compose.ui.input.key.KeyEventType
import androidx.compose.ui.input.key.key
import androidx.compose.ui.input.key.onPreviewKeyEvent
import androidx.compose.ui.input.key.type
import androidx.compose.ui.unit.dp
import com.giztv.tv.data.LibraryItem
import com.giztv.tv.data.LibraryKind
import com.giztv.tv.data.PlaybackContext
import com.giztv.tv.data.WatchHistoryEntry
import java.net.SocketTimeoutException
import java.net.UnknownHostException
import javax.net.ssl.SSLException

@Composable
internal fun gridEntryModifier(
  isFirst: Boolean,
  gridFocusRequester: FocusRequester,
  onFocused: (FocusRequester) -> Unit,
): Modifier {
  val ownFocusRequester = remember { FocusRequester() }
  val itemFocusRequester = if (isFirst) gridFocusRequester else ownFocusRequester
  return Modifier.focusRequester(itemFocusRequester)
    .onFocusChanged { if (it.isFocused) onFocused(itemFocusRequester) }
}

internal fun TmdbMovie.toPlaybackContext(): PlaybackContext =
  PlaybackContext(
    pageUrl = vidfastMovieUrl(id),
    title = title,
    subtitle = year,
    posterUrl = posterUrl,
    year = year,
    overview = overview,
    rating = voteAverage.takeIf { it > 0.0 },
  )

internal fun TmdbMovie.toLibraryItem(): LibraryItem =
  LibraryItem(
    id = id,
    kind = LibraryKind.MOVIE,
    title = title,
    date = releaseDate,
    voteAverage = voteAverage,
    overview = overview,
    posterPath = posterPath,
  )

internal fun LibraryItem.toPlaybackContext(): PlaybackContext =
  PlaybackContext(
    pageUrl = vidfastMovieUrl(id),
    title = title,
    subtitle = year,
    posterUrl = posterUrl,
    year = year,
    overview = overview,
    rating = voteAverage.takeIf { it > 0.0 },
  )

internal fun LibraryItem.toShow(): TmdbShow =
  TmdbShow(
    id = id,
    name = title,
    firstAirDate = date,
    voteAverage = voteAverage,
    overview = overview,
    posterPath = posterPath,
  )

internal fun LibraryItem.toMovie(): TmdbMovie =
  TmdbMovie(
    id = id,
    title = title,
    releaseDate = date,
    voteAverage = voteAverage,
    overview = overview,
    posterPath = posterPath,
  )

internal fun WatchHistoryEntry.toPlaybackContext(): PlaybackContext =
  PlaybackContext(
    pageUrl = pageUrl,
    title = title,
    subtitle = subtitle,
    posterUrl = posterUrl,
    showId = showId,
    seasonNumber = seasonNumber,
    episodeNumber = episodeNumber,
  )

/** Maps transport failures onto wording a viewer can act on, keeping the raw cause in logcat. */
internal fun friendlyCatalogError(error: Throwable): String =
  when (error) {
    is SSLException ->
      "Secure connection to TMDB failed. Check this device's date and time, then try again."
    is UnknownHostException -> "No internet connection. Check the network and try again."
    is SocketTimeoutException -> "TMDB took too long to respond. Try again."
    else -> error.message ?: "Content could not be loaded"
  }

/**
 * One listing rail: a heading and a row of posters.
 */
@Composable
internal fun <T> CatalogRail(
  heading: String,
  items: List<T>,
  key: (T) -> Any,
  narrow: Boolean,
  attribution: Boolean,
  firstCardFocusRequester: FocusRequester,
  up: FocusRequester?,
  down: FocusRequester?,
  onMoveDown: (() -> Unit)?,
  onMoveUp: (() -> Unit)?,
  onCardFocused: (FocusRequester) -> Unit = {},
  card: @Composable (T, Modifier) -> Unit,
) {
  if (items.isEmpty()) return
  val firstKey = key(items.first())
  Column(
    modifier =
      Modifier.onPreviewKeyEvent { event ->
        if (event.type != KeyEventType.KeyDown) return@onPreviewKeyEvent false
        when (event.key) {
          Key.DirectionDown -> onMoveDown?.let { it(); true } ?: false
          Key.DirectionUp -> onMoveUp?.let { it(); true } ?: false
          else -> false
        }
      }
  ) {
    val edge = if (narrow) 18.dp else 42.dp
    SectionHeading(
      heading = heading,
      itemCount = items.size,
      narrow = narrow,
      attribution = attribution,
      modifier = Modifier.padding(horizontal = edge),
    )
    val railListState = rememberSaveable(saver = LazyListState.Saver) { LazyListState() }
    LazyRow(
      state = railListState,
      modifier = Modifier.fillMaxWidth().focusGroup(),
      contentPadding = PaddingValues(horizontal = edge),
      horizontalArrangement = Arrangement.spacedBy(if (narrow) 12.dp else 18.dp),
    ) {
      items(items = items, key = key) { item ->
        val ownFocusRequester = remember { FocusRequester() }
        val itemFocusRequester =
          if (key(item) == firstKey) firstCardFocusRequester else ownFocusRequester
        val cardModifier =
          Modifier.width(if (narrow) 132.dp else 158.dp)
            .focusRequester(itemFocusRequester)
            .onFocusChanged { if (it.isFocused) onCardFocused(itemFocusRequester) }
            .focusProperties {
              if (up != null) this.up = up
              if (down != null) this.down = down
            }
        card(item, cardModifier)
      }
    }
  }
}
