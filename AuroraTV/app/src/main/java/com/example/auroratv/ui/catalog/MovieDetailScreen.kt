package com.example.auroratv.ui.catalog

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
import androidx.compose.material.icons.filled.Add
import androidx.compose.material.icons.filled.Check
import androidx.compose.material.icons.filled.PlayArrow
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
import androidx.compose.ui.text.font.FontStyle
import androidx.compose.ui.text.font.FontWeight
import androidx.compose.ui.text.style.TextOverflow
import androidx.compose.ui.unit.dp
import androidx.compose.ui.unit.sp
import androidx.compose.ui.window.Dialog
import androidx.tv.material3.Text
import com.example.auroratv.BuildConfig
import com.example.auroratv.data.LibraryItem
import com.example.auroratv.data.LibraryKind
import com.example.auroratv.data.MyListStore
import com.example.auroratv.data.PlaybackContext
import com.example.auroratv.theme.AuroraBlue
import com.example.auroratv.theme.AuroraMint
import com.example.auroratv.theme.DeepSpace
import com.example.auroratv.theme.MutedBlue
import com.example.auroratv.theme.NightSurface
import com.example.auroratv.theme.SoftWhite
import java.util.Locale
import kotlinx.coroutines.launch

/**
 * Netflix-styled Movie Detail screen:
 * - Backdrop hero artwork with gradient overlays
 * - Prominent Play button & My List action toggle
 * - Movie metadata, tagline, and synopsis
 * - Cast & crew profile cards
 * - User reviews with full text dialog viewer
 * - "More Like This" recommended movies rail
 */
@Composable
internal fun MovieDetailScreen(
  movie: TmdbMovie,
  onPlay: (PlaybackContext) -> Unit,
  onOpenMovie: (TmdbMovie) -> Unit,
  onOpenPerson: (personId: Int, name: String, isDirector: Boolean) -> Unit = { _, _, _ -> },
  onBack: () -> Unit,
  modifier: Modifier = Modifier,
) {
  val context = LocalContext.current
  val detailsRepository = remember { TmdbPlaybackDetailsRepository(BuildConfig.TMDB_API_KEY) }
  val movieRepository = remember { TmdbMovieRepository(BuildConfig.TMDB_API_KEY) }
  val myListStore = remember(context) { MyListStore(context) }
  val scope = rememberCoroutineScope()

  val backFocusRequester = remember { FocusRequester() }
  val playFocusRequester = remember { FocusRequester() }

  var details by remember(movie.id) { mutableStateOf<TmdbPlaybackDetails?>(null) }
  var recommendations by remember(movie.id) { mutableStateOf<List<TmdbMovie>>(emptyList()) }
  var loadingDetails by remember(movie.id) { mutableStateOf(true) }
  var saved by remember(movie.id) { mutableStateOf(myListStore.contains(LibraryKind.MOVIE, movie.id)) }
  var selectedReview by remember { mutableStateOf<PlaybackReview?>(null) }

  LaunchedEffect(movie.id) {
    loadingDetails = true
    scope.launch {
      runCatching { detailsRepository.movieDetails(movie.id) }
        .onSuccess { details = it }
        .onFailure { Log.e("GizTvMovieDetail", "Failed loading movie details for ${movie.id}", it) }
      
      runCatching { movieRepository.recommendations(movie.id) }
        .onSuccess { recommendations = it }
        .onFailure { Log.e("GizTvMovieDetail", "Failed loading recommendations for ${movie.id}", it) }

      loadingDetails = false
    }
  }

  LaunchedEffect(Unit) {
    playFocusRequester.requestFocus()
  }

  BackHandler(onBack = onBack)

  val backdropUrl = details?.backdropUrl ?: movie.backdropUrl ?: movie.posterUrl
  val runtime = details?.runtimeMinutes?.let { mins ->
    val hours = mins / 60
    val remMins = mins % 60
    if (hours > 0) "${hours}h ${remMins}m" else "${mins}m"
  }
  val genres = details?.genres ?: emptyList()
  val cast = details?.cast ?: emptyList()
  val reviews = details?.reviews ?: emptyList()
  val director = details?.director

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
      // Hero Header with Backdrop Image & Controls
      Box(
        modifier = Modifier
          .fillMaxWidth()
          .height(if (narrow) 300.dp else 420.dp)
      ) {
        TmdbArtwork(
          url = backdropUrl,
          contentDescription = "${movie.title} backdrop",
          modifier = Modifier.fillMaxSize(),
          fallbackLabel = movie.title,
        )

        // Dark overlay gradients for text contrast
        Box(
          modifier = Modifier
            .fillMaxSize()
            .background(
              Brush.verticalGradient(
                colors = listOf(
                  DeepSpace.copy(alpha = 0.85f),
                  Color.Transparent,
                  DeepSpace.copy(alpha = 0.95f),
                  DeepSpace
                )
              )
            )
        )

        // Back button on top-left
        Box(
          modifier = Modifier
            .padding(top = 16.dp, start = 16.dp)
            .align(Alignment.TopStart)
        ) {
          BackIconButton(
            onBack = onBack,
            focusRequester = backFocusRequester,
          )
        }

        // Hero Info Overlay
        Column(
          modifier = Modifier
            .align(Alignment.BottomStart)
            .padding(horizontal = if (narrow) 18.dp else 36.dp, vertical = 16.dp)
        ) {
          Text(
            text = movie.title,
            color = SoftWhite,
            fontWeight = FontWeight.Black,
            fontSize = if (narrow) 26.sp else 36.sp,
            lineHeight = if (narrow) 30.sp else 42.sp,
            maxLines = 2,
            overflow = TextOverflow.Ellipsis,
          )

          details?.tagline?.let { tagline ->
            Spacer(Modifier.height(4.dp))
            Text(
              text = "\"$tagline\"",
              color = MutedBlue,
              fontStyle = FontStyle.Italic,
              fontSize = 13.sp,
            )
          }

          Spacer(Modifier.height(8.dp))

          // Meta Row: Year · Rating · Runtime · HD · Genres
          Row(
            verticalAlignment = Alignment.CenterVertically,
            horizontalArrangement = Arrangement.spacedBy(8.dp)
          ) {
            movie.year?.let { y ->
              Text(y, color = SoftWhite.copy(alpha = 0.9f), fontSize = 13.sp, fontWeight = FontWeight.SemiBold)
            }

            val score = details?.rating ?: movie.voteAverage
            if (score > 0.0) {
              Box(
                modifier = Modifier
                  .clip(RoundedCornerShape(4.dp))
                  .background(AuroraMint.copy(alpha = 0.2f))
                  .padding(horizontal = 6.dp, vertical = 2.dp)
              ) {
                Row(verticalAlignment = Alignment.CenterVertically, horizontalArrangement = Arrangement.spacedBy(2.dp)) {
                  Icon(Icons.Filled.Star, contentDescription = null, tint = AuroraMint, modifier = Modifier.size(12.dp))
                  Text(
                    text = String.format(Locale.US, "%.1f", score),
                    color = AuroraMint,
                    fontWeight = FontWeight.Bold,
                    fontSize = 12.sp
                  )
                }
              }
            }

            runtime?.let { r ->
              Text(r, color = SoftWhite.copy(alpha = 0.8f), fontSize = 13.sp)
            }

            Box(
              modifier = Modifier
                .border(1.dp, SoftWhite.copy(alpha = 0.4f), RoundedCornerShape(3.dp))
                .padding(horizontal = 5.dp, vertical = 1.dp)
            ) {
              Text("HD", color = SoftWhite, fontSize = 10.sp, fontWeight = FontWeight.Bold)
            }
          }

          if (genres.isNotEmpty()) {
            Spacer(Modifier.height(6.dp))
            Text(
              text = genres.joinToString(" • "),
              color = MutedBlue,
              fontSize = 12.sp,
              maxLines = 1,
              overflow = TextOverflow.Ellipsis
            )
          }

          Spacer(Modifier.height(16.dp))

          // Action Buttons: Netflix Play Button & My List Button
          Row(
            verticalAlignment = Alignment.CenterVertically,
            horizontalArrangement = Arrangement.spacedBy(14.dp)
          ) {
            NetflixPlayButton(
              onClick = { onPlay(movie.toPlaybackContext()) },
              focusRequester = playFocusRequester,
              upFocus = backFocusRequester,
            )

            MyListButton(
              saved = saved,
              onToggle = { saved = myListStore.toggle(movie.toLibraryItem()) },
              upFocus = backFocusRequester,
            )
          }
        }
      }

      // Content Section: Overview, Cast, Reviews, Details, Recommendations
      Column(
        modifier = Modifier.padding(horizontal = if (narrow) 18.dp else 36.dp, vertical = 16.dp),
        verticalArrangement = Arrangement.spacedBy(24.dp)
      ) {
        // Synopsis & Director
        Column {
          Text(
            text = movie.overview.ifBlank { details?.overview.orEmpty() },
            color = SoftWhite.copy(alpha = 0.9f),
            fontSize = 14.sp,
            lineHeight = 20.sp,
          )

          director?.let { d ->
            Spacer(Modifier.height(8.dp))
            Row(
              verticalAlignment = Alignment.CenterVertically,
              modifier = Modifier
                .clip(RoundedCornerShape(6.dp))
                .clickable { if (d.id > 0) onOpenPerson(d.id, d.name, true) }
                .padding(vertical = 2.dp, horizontal = 4.dp)
            ) {
              Text(
                text = "Director: ",
                color = MutedBlue,
                fontWeight = FontWeight.Medium,
                fontSize = 13.sp
              )
              Text(
                text = d.name,
                color = AuroraMint,
                fontWeight = FontWeight.Bold,
                fontSize = 13.sp
              )
            }
          }
        }

        // Cast & Crew Section
        if (cast.isNotEmpty()) {
          Column {
            Text(
              text = "Cast & Crew",
              color = SoftWhite,
              fontWeight = FontWeight.Bold,
              fontSize = 17.sp,
            )
            Spacer(Modifier.height(10.dp))
            LazyRow(
              horizontalArrangement = Arrangement.spacedBy(14.dp),
              modifier = Modifier.focusGroup()
            ) {
              items(cast, key = { it.name + it.character }) { member ->
                CastMemberCard(
                  member = member,
                  onClick = { if (member.id > 0) onOpenPerson(member.id, member.name, false) }
                )
              }
            }
          }
        }

        // User Reviews Section
        if (reviews.isNotEmpty()) {
          Column {
            Text(
              text = "User Reviews",
              color = SoftWhite,
              fontWeight = FontWeight.Bold,
              fontSize = 17.sp,
            )
            Spacer(Modifier.height(10.dp))
            LazyRow(
              horizontalArrangement = Arrangement.spacedBy(14.dp),
              modifier = Modifier.focusGroup()
            ) {
              items(reviews, key = { it.id }) { review ->
                ReviewCard(
                  review = review,
                  onClick = { selectedReview = review }
                )
              }
            }
          }
        }

        // Movie Details Metadata Grid
        Column(
          modifier = Modifier
            .fillMaxWidth()
            .clip(RoundedCornerShape(12.dp))
            .background(NightSurface)
            .padding(16.dp)
        ) {
          Text(
            text = "Movie Details",
            color = SoftWhite,
            fontWeight = FontWeight.Bold,
            fontSize = 15.sp
          )
          Spacer(Modifier.height(10.dp))
          Row(
            modifier = Modifier.fillMaxWidth(),
            horizontalArrangement = Arrangement.SpaceBetween
          ) {
            DetailMetaItem(label = "Release Date", value = movie.releaseDate ?: details?.releaseDate ?: "N/A")
            DetailMetaItem(label = "Runtime", value = runtime ?: "N/A")
            DetailMetaItem(label = "TMDB Rating", value = if (movie.voteAverage > 0) "★ ${String.format(Locale.US, "%.1f", movie.voteAverage)}" else "N/A")
          }
        }

        // Recommendations ("More Like This")
        if (recommendations.isNotEmpty()) {
          Column {
            Text(
              text = "More Like This",
              color = SoftWhite,
              fontWeight = FontWeight.Bold,
              fontSize = 17.sp,
            )
            Spacer(Modifier.height(10.dp))
            LazyRow(
              horizontalArrangement = Arrangement.spacedBy(14.dp),
              modifier = Modifier.focusGroup()
            ) {
              items(recommendations, key = { it.id }) { rec ->
                Box(modifier = Modifier.width(if (narrow) 130.dp else 155.dp)) {
                  PosterCard(
                    title = rec.title,
                    subtitle = rec.year.orEmpty(),
                    rating = rec.voteAverage,
                    posterUrl = rec.posterUrl,
                    actionLabel = "Open ${rec.title}",
                    onClick = { onOpenMovie(rec) }
                  )
                }
              }
            }
          }
        }

        Spacer(Modifier.height(32.dp))
      }
    }
  }

  // Full Review Dialog Modal
  selectedReview?.let { review ->
    FullReviewDialog(
      review = review,
      onDismiss = { selectedReview = null }
    )
  }
}

@Composable
private fun BackIconButton(
  onBack: () -> Unit,
  focusRequester: FocusRequester,
) {
  var focused by remember { mutableStateOf(false) }
  val scale by animateFloatAsState(if (focused) 1.1f else 1f, label = "back scale")
  val bg by animateColorAsState(if (focused) AuroraMint else NightSurface.copy(alpha = 0.85f), label = "back bg")

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

@Composable
private fun NetflixPlayButton(
  onClick: () -> Unit,
  focusRequester: FocusRequester,
  upFocus: FocusRequester? = null,
) {
  var focused by remember { mutableStateOf(false) }
  val scale by animateFloatAsState(if (focused) 1.08f else 1f, label = "play scale")
  val bg by animateColorAsState(if (focused) AuroraMint else SoftWhite, label = "play bg")

  Row(
    modifier = Modifier
      .height(44.dp)
      .graphicsLayer { scaleX = scale; scaleY = scale }
      .clip(RoundedCornerShape(22.dp))
      .background(bg)
      .border(if (focused) 3.dp else 0.dp, SoftWhite, RoundedCornerShape(22.dp))
      .focusRequester(focusRequester)
      .focusProperties { if (upFocus != null) up = upFocus }
      .onFocusChanged { focused = it.isFocused }
      .clickable(onClick = onClick)
      .semantics { role = Role.Button; contentDescription = "Play Movie" }
      .padding(horizontal = 24.dp),
    verticalAlignment = Alignment.CenterVertically,
    horizontalArrangement = Arrangement.spacedBy(6.dp)
  ) {
    Icon(
      imageVector = Icons.Filled.PlayArrow,
      contentDescription = null,
      tint = DeepSpace,
      modifier = Modifier.size(26.dp)
    )
    Text(
      text = "Play",
      color = DeepSpace,
      fontWeight = FontWeight.Black,
      fontSize = 16.sp
    )
  }
}

@Composable
private fun MyListButton(
  saved: Boolean,
  onToggle: () -> Unit,
  upFocus: FocusRequester? = null,
) {
  var focused by remember { mutableStateOf(false) }
  val scale by animateFloatAsState(if (focused) 1.08f else 1f, label = "list scale")
  val bg by animateColorAsState(
    when {
      focused -> AuroraMint.copy(alpha = 0.3f)
      saved -> AuroraMint.copy(alpha = 0.15f)
      else -> NightSurface.copy(alpha = 0.8f)
    },
    label = "list bg"
  )
  val border by animateColorAsState(
    if (focused) SoftWhite else SoftWhite.copy(alpha = 0.3f),
    label = "list border"
  )

  Row(
    modifier = Modifier
      .height(44.dp)
      .graphicsLayer { scaleX = scale; scaleY = scale }
      .clip(RoundedCornerShape(22.dp))
      .background(bg)
      .border(if (focused) 2.dp else 1.dp, border, RoundedCornerShape(22.dp))
      .focusProperties { if (upFocus != null) up = upFocus }
      .onFocusChanged { focused = it.isFocused }
      .clickable(onClick = onToggle)
      .semantics { role = Role.Button; contentDescription = "Toggle My List" }
      .padding(horizontal = 18.dp),
    verticalAlignment = Alignment.CenterVertically,
    horizontalArrangement = Arrangement.spacedBy(6.dp)
  ) {
    Icon(
      imageVector = if (saved) Icons.Filled.Check else Icons.Filled.Add,
      contentDescription = null,
      tint = if (saved) AuroraMint else SoftWhite,
      modifier = Modifier.size(18.dp)
    )
    Text(
      text = if (saved) "In My List" else "My List",
      color = if (saved) AuroraMint else SoftWhite,
      fontWeight = FontWeight.Bold,
      fontSize = 14.sp
    )
  }
}

@Composable
private fun CastMemberCard(
  member: PlaybackCastMember,
  onClick: () -> Unit = {}
) {
  var focused by remember { mutableStateOf(false) }
  val scale by animateFloatAsState(if (focused) 1.06f else 1f, label = "cast scale")

  Column(
    modifier = Modifier
      .width(105.dp)
      .graphicsLayer { scaleX = scale; scaleY = scale }
      .clip(RoundedCornerShape(12.dp))
      .background(NightSurface)
      .border(if (focused) 2.dp else 0.dp, AuroraMint, RoundedCornerShape(12.dp))
      .onFocusChanged { focused = it.isFocused }
      .clickable(onClick = onClick)
      .semantics { role = Role.Button; contentDescription = "Open ${member.name}" }
      .padding(8.dp),
    horizontalAlignment = Alignment.CenterHorizontally
  ) {
    TmdbArtwork(
      url = member.photoUrl,
      contentDescription = member.name,
      modifier = Modifier
        .size(68.dp)
        .clip(CircleShape),
      compact = true,
      fallbackLabel = member.name.take(1)
    )
    Spacer(Modifier.height(6.dp))
    Text(
      text = member.name,
      color = SoftWhite,
      fontWeight = FontWeight.Bold,
      fontSize = 12.sp,
      maxLines = 1,
      overflow = TextOverflow.Ellipsis
    )
    member.character?.let { char ->
      Text(
        text = char,
        color = MutedBlue,
        fontSize = 10.sp,
        maxLines = 1,
        overflow = TextOverflow.Ellipsis
      )
    }
  }
}

@Composable
private fun ReviewCard(
  review: PlaybackReview,
  onClick: () -> Unit
) {
  var focused by remember { mutableStateOf(false) }
  val scale by animateFloatAsState(if (focused) 1.04f else 1f, label = "review scale")
  val border by animateColorAsState(if (focused) AuroraMint else SoftWhite.copy(alpha = 0.1f), label = "review border")

  Column(
    modifier = Modifier
      .width(260.dp)
      .height(130.dp)
      .graphicsLayer { scaleX = scale; scaleY = scale }
      .clip(RoundedCornerShape(14.dp))
      .background(NightSurface)
      .border(if (focused) 2.dp else 1.dp, border, RoundedCornerShape(14.dp))
      .onFocusChanged { focused = it.isFocused }
      .clickable(onClick = onClick)
      .semantics { role = Role.Button; contentDescription = "Review by ${review.author}" }
      .padding(12.dp),
    verticalArrangement = Arrangement.SpaceBetween
  ) {
    Column {
      Row(
        modifier = Modifier.fillMaxWidth(),
        horizontalArrangement = Arrangement.SpaceBetween,
        verticalAlignment = Alignment.CenterVertically
      ) {
        Text(
          text = review.author,
          color = SoftWhite,
          fontWeight = FontWeight.Bold,
          fontSize = 13.sp,
          maxLines = 1,
          overflow = TextOverflow.Ellipsis,
          modifier = Modifier.weight(1f)
        )
        review.rating?.let { r ->
          Row(verticalAlignment = Alignment.CenterVertically, horizontalArrangement = Arrangement.spacedBy(2.dp)) {
            Icon(Icons.Filled.Star, contentDescription = null, tint = AuroraMint, modifier = Modifier.size(12.dp))
            Text(
              text = String.format(Locale.US, "%.1f", r),
              color = AuroraMint,
              fontSize = 11.sp,
              fontWeight = FontWeight.Bold
            )
          }
        }
      }
      Spacer(Modifier.height(6.dp))
      Text(
        text = review.excerpt,
        color = SoftWhite.copy(alpha = 0.8f),
        fontSize = 12.sp,
        maxLines = 3,
        overflow = TextOverflow.Ellipsis,
        lineHeight = 16.sp
      )
    }

    Text(
      text = "Read full review →",
      color = AuroraBlue,
      fontSize = 11.sp,
      fontWeight = FontWeight.Bold
    )
  }
}

@Composable
private fun DetailMetaItem(label: String, value: String) {
  Column {
    Text(label, color = MutedBlue, fontSize = 11.sp)
    Spacer(Modifier.height(2.dp))
    Text(value, color = SoftWhite, fontWeight = FontWeight.SemiBold, fontSize = 13.sp)
  }
}

@Composable
private fun FullReviewDialog(
  review: PlaybackReview,
  onDismiss: () -> Unit
) {
  val closeFocusRequester = remember { FocusRequester() }
  LaunchedEffect(Unit) { closeFocusRequester.requestFocus() }

  Dialog(onDismissRequest = onDismiss) {
    Box(
      modifier = Modifier
        .fillMaxWidth(0.92f)
        .clip(RoundedCornerShape(18.dp))
        .background(NightSurface)
        .border(1.dp, SoftWhite.copy(alpha = 0.2f), RoundedCornerShape(18.dp))
        .padding(20.dp)
    ) {
      Column(modifier = Modifier.verticalScroll(rememberScrollState())) {
        Row(
          modifier = Modifier.fillMaxWidth(),
          horizontalArrangement = Arrangement.SpaceBetween,
          verticalAlignment = Alignment.CenterVertically
        ) {
          Column(modifier = Modifier.weight(1f)) {
            Text("Review by ${review.author}", color = SoftWhite, fontWeight = FontWeight.Bold, fontSize = 16.sp)
            review.rating?.let { r ->
              Text("Rating: ★ ${String.format(Locale.US, "%.1f", r)} / 10", color = AuroraMint, fontSize = 12.sp)
            }
          }
        }
        Spacer(Modifier.height(14.dp))
        Text(
          text = review.excerpt,
          color = SoftWhite.copy(alpha = 0.9f),
          fontSize = 13.sp,
          lineHeight = 19.sp
        )
        Spacer(Modifier.height(20.dp))
        CatalogButton(
          label = "Close",
          onClick = onDismiss,
          modifier = Modifier
            .align(Alignment.End)
            .focusRequester(closeFocusRequester)
        )
      }
    }
  }
}
