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
import androidx.compose.foundation.layout.Row
import androidx.compose.foundation.layout.Spacer
import androidx.compose.foundation.layout.fillMaxSize
import androidx.compose.foundation.layout.fillMaxWidth
import androidx.compose.foundation.layout.height
import androidx.compose.foundation.layout.padding
import androidx.compose.foundation.layout.size
import androidx.compose.foundation.layout.width
import androidx.compose.foundation.lazy.LazyRow
import androidx.compose.foundation.lazy.items
import androidx.compose.foundation.rememberScrollState
import androidx.compose.foundation.shape.CircleShape
import androidx.compose.foundation.shape.RoundedCornerShape
import androidx.compose.foundation.verticalScroll
import androidx.compose.material.icons.Icons
import androidx.compose.material.icons.automirrored.filled.ArrowBack
import androidx.compose.material.icons.filled.Movie
import androidx.compose.material.icons.filled.Star
import androidx.compose.material3.Icon
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
import androidx.compose.ui.focus.focusRequester
import androidx.compose.ui.focus.onFocusChanged
import androidx.compose.ui.graphics.Brush
import androidx.compose.ui.graphics.Color
import androidx.compose.ui.graphics.graphicsLayer
import androidx.compose.ui.semantics.Role
import androidx.compose.ui.semantics.contentDescription
import androidx.compose.ui.semantics.role
import androidx.compose.ui.semantics.semantics
import androidx.compose.ui.text.font.FontWeight
import androidx.compose.ui.text.style.TextOverflow
import androidx.compose.ui.unit.dp
import androidx.compose.ui.unit.sp
import androidx.tv.material3.Text
import com.giztv.tv.BuildConfig
import com.giztv.tv.theme.GizMint
import com.giztv.tv.theme.DeepSpace
import com.giztv.tv.theme.MutedBlue
import com.giztv.tv.theme.NightSurface
import com.giztv.tv.theme.SoftWhite
import kotlinx.coroutines.launch

/**
 * Profile detail screen for an Actor, Actress, or Director:
 * - Profile artwork & personal metadata (birth date, place of birth, role)
 * - Detailed biography
 * - Best Rated Movies rail (sorted by vote average)
 * - Full Filmography rail (all movies directed or acted in)
 */
@Composable
internal fun PersonDetailScreen(
  personId: Int,
  personName: String,
  isDirector: Boolean = false,
  onOpenMovie: (TmdbMovie) -> Unit,
  onBack: () -> Unit,
  modifier: Modifier = Modifier,
) {
  val movieRepository = remember { TmdbMovieRepository(BuildConfig.TMDB_API_KEY) }
  val scope = rememberCoroutineScope()
  val backFocusRequester = remember { FocusRequester() }

  var details by remember(personId) { mutableStateOf<TmdbPersonDetails?>(null) }
  var bestRatedMovies by remember(personId) { mutableStateOf<List<TmdbMovie>>(emptyList()) }
  var allMovies by remember(personId) { mutableStateOf<List<TmdbMovie>>(emptyList()) }
  var loading by remember(personId) { mutableStateOf(true) }
  var bioExpanded by remember { mutableStateOf(false) }

  LaunchedEffect(personId) {
    loading = true
    scope.launch {
      runCatching { movieRepository.personDetails(personId) }
        .onSuccess { details = it }
        .onFailure { Log.e("GizTvPersonDetail", "Failed loading details for person $personId", it) }

      runCatching { movieRepository.personMovieCredits(personId, isDirector) }
        .onSuccess { (best, all) ->
          bestRatedMovies = best
          allMovies = all
        }
        .onFailure { Log.e("GizTvPersonDetail", "Failed loading credits for person $personId", it) }

      loading = false
    }
  }

  LaunchedEffect(Unit) {
    backFocusRequester.requestFocus()
  }

  BackHandler(onBack = onBack)

  val photoUrl = details?.photoUrl
  val roleLabel = when {
    isDirector -> "Director"
    details?.knownForDepartment?.equals("Directing", ignoreCase = true) == true -> "Director"
    details?.gender == 1 -> "Actress"
    else -> "Actor"
  }

  BoxWithConstraints(
    modifier = modifier
      .fillMaxSize()
      .background(DeepSpace)
  ) {
    val narrow = maxWidth < 720.dp
    val scrollState = rememberScrollState()

    Column(
      modifier = Modifier
        .fillMaxSize()
        .verticalScroll(scrollState)
    ) {
      // Header Section
      Box(
        modifier = Modifier
          .fillMaxWidth()
          .background(
            Brush.verticalGradient(
              colors = listOf(Color(0xFF17345C), DeepSpace)
            )
          )
          .padding(horizontal = if (narrow) 18.dp else 36.dp, vertical = 20.dp)
      ) {
        Column {
          // Back button
          BackIconButton(onBack = onBack, focusRequester = backFocusRequester)
          Spacer(Modifier.height(16.dp))

          // Profile Row
          Row(
            verticalAlignment = Alignment.CenterVertically,
            horizontalArrangement = Arrangement.spacedBy(20.dp)
          ) {
            TmdbArtwork(
              url = photoUrl,
              contentDescription = personName,
              modifier = Modifier
                .size(if (narrow) 100.dp else 130.dp)
                .clip(CircleShape)
                .border(2.dp, GizMint, CircleShape),
              compact = true,
              fallbackLabel = personName.take(1)
            )

            Column(modifier = Modifier.weight(1f)) {
              Text(
                text = details?.name ?: personName,
                color = SoftWhite,
                fontWeight = FontWeight.Black,
                fontSize = if (narrow) 24.sp else 32.sp,
                lineHeight = if (narrow) 28.sp else 38.sp
              )
              Spacer(Modifier.height(6.dp))

              // Role Pill Badge
              Box(
                modifier = Modifier
                  .clip(RoundedCornerShape(6.dp))
                  .background(GizMint.copy(alpha = 0.2f))
                  .padding(horizontal = 8.dp, vertical = 3.dp)
              ) {
                Text(
                  text = roleLabel,
                  color = GizMint,
                  fontWeight = FontWeight.Bold,
                  fontSize = 12.sp
                )
              }

              details?.birthday?.let { bday ->
                Spacer(Modifier.height(6.dp))
                Text(
                  text = "Born: $bday" + (details?.placeOfBirth?.let { " in $it" } ?: ""),
                  color = MutedBlue,
                  fontSize = 12.sp,
                  maxLines = 2,
                  overflow = TextOverflow.Ellipsis
                )
              }
            }
          }
        }
      }

      // Content Body
      Column(
        modifier = Modifier.padding(horizontal = if (narrow) 18.dp else 36.dp, vertical = 16.dp),
        verticalArrangement = Arrangement.spacedBy(24.dp)
      ) {
        // Biography
        details?.biography?.let { bio ->
          if (bio.isNotBlank()) {
            Column(
              modifier = Modifier
                .fillMaxWidth()
                .clip(RoundedCornerShape(14.dp))
                .background(NightSurface)
                .clickable { bioExpanded = !bioExpanded }
                .padding(16.dp)
            ) {
              Text(
                text = "Biography",
                color = SoftWhite,
                fontWeight = FontWeight.Bold,
                fontSize = 16.sp
              )
              Spacer(Modifier.height(8.dp))
              Text(
                text = bio,
                color = SoftWhite.copy(alpha = 0.85f),
                fontSize = 13.sp,
                lineHeight = 19.sp,
                maxLines = if (bioExpanded) Int.MAX_VALUE else 4,
                overflow = TextOverflow.Ellipsis
              )
              if (bio.length > 220) {
                Spacer(Modifier.height(6.dp))
                Text(
                  text = if (bioExpanded) "Show less ▲" else "Read more ▼",
                  color = GizMint,
                  fontSize = 12.sp,
                  fontWeight = FontWeight.Bold
                )
              }
            }
          }
        }

        // Best Rated Movies Section
        if (bestRatedMovies.isNotEmpty()) {
          Column {
            Row(verticalAlignment = Alignment.CenterVertically, horizontalArrangement = Arrangement.spacedBy(6.dp)) {
              Icon(Icons.Filled.Star, contentDescription = null, tint = GizMint, modifier = Modifier.size(18.dp))
              Text(
                text = if (isDirector) "Best Rated Directed Movies" else "Best Rated Movies",
                color = SoftWhite,
                fontWeight = FontWeight.Bold,
                fontSize = 17.sp,
              )
            }
            Spacer(Modifier.height(10.dp))
            LazyRow(
              horizontalArrangement = Arrangement.spacedBy(14.dp),
              modifier = Modifier.focusGroup()
            ) {
              items(bestRatedMovies, key = { it.id }) { movie ->
                Box(modifier = Modifier.width(if (narrow) 130.dp else 155.dp)) {
                  PosterCard(
                    title = movie.title,
                    subtitle = movie.year.orEmpty(),
                    rating = movie.voteAverage,
                    posterUrl = movie.posterUrl,
                    actionLabel = "Open ${movie.title}",
                    onClick = { onOpenMovie(movie) }
                  )
                }
              }
            }
          }
        }

        // All Movies / Filmography Section
        if (allMovies.isNotEmpty()) {
          Column {
            Row(verticalAlignment = Alignment.CenterVertically, horizontalArrangement = Arrangement.spacedBy(6.dp)) {
              Icon(Icons.Filled.Movie, contentDescription = null, tint = MutedBlue, modifier = Modifier.size(18.dp))
              Text(
                text = if (isDirector) "Filmography (${allMovies.size} Directed Movies)" else "Filmography (${allMovies.size} Movies)",
                color = SoftWhite,
                fontWeight = FontWeight.Bold,
                fontSize = 17.sp,
              )
            }
            Spacer(Modifier.height(10.dp))
            LazyRow(
              horizontalArrangement = Arrangement.spacedBy(14.dp),
              modifier = Modifier.focusGroup()
            ) {
              items(allMovies, key = { it.id }) { movie ->
                Box(modifier = Modifier.width(if (narrow) 130.dp else 155.dp)) {
                  PosterCard(
                    title = movie.title,
                    subtitle = movie.year.orEmpty(),
                    rating = movie.voteAverage,
                    posterUrl = movie.posterUrl,
                    actionLabel = "Open ${movie.title}",
                    onClick = { onOpenMovie(movie) }
                  )
                }
              }
            }
          }
        }

        if (loading) {
          StatusPanel("Loading profile & filmography…", Modifier.height(180.dp), loading = true)
        }

        Spacer(Modifier.height(32.dp))
      }
    }
  }
}

@Composable
private fun BackIconButton(
  onBack: () -> Unit,
  focusRequester: FocusRequester,
) {
  var focused by remember { mutableStateOf(false) }
  val scale by animateFloatAsState(if (focused) 1.1f else 1f, label = "back scale")
  val bg by animateColorAsState(if (focused) GizMint else NightSurface.copy(alpha = 0.85f), label = "back bg")

  Box(
    modifier = Modifier
      .size(40.dp)
      .graphicsLayer { scaleX = scale; scaleY = scale }
      .clip(CircleShape)
      .background(bg)
      .border(if (focused) 2.dp else 0.dp, SoftWhite, CircleShape)
      .focusRequester(focusRequester)
      .onFocusChanged { focused = it.isFocused }
      .clickable(onClick = onBack)
      .semantics { role = Role.Button; contentDescription = "Go back" },
    contentAlignment = Alignment.Center
  ) {
    Icon(
      imageVector = Icons.AutoMirrored.Filled.ArrowBack,
      contentDescription = null,
      tint = if (focused) DeepSpace else SoftWhite,
      modifier = Modifier.size(20.dp)
    )
  }
}
