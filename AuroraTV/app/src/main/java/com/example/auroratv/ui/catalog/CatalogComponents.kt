package com.example.auroratv.ui.catalog

import androidx.compose.animation.animateColorAsState
import androidx.compose.animation.core.LinearEasing
import androidx.compose.animation.core.RepeatMode
import androidx.compose.animation.core.animateFloat
import androidx.compose.animation.core.animateFloatAsState
import androidx.compose.animation.core.infiniteRepeatable
import androidx.compose.animation.core.rememberInfiniteTransition
import androidx.compose.animation.core.tween
import androidx.compose.foundation.Image
import androidx.compose.foundation.background
import androidx.compose.foundation.border
import androidx.compose.foundation.clickable
import androidx.compose.foundation.layout.Box
import androidx.compose.foundation.layout.Column
import androidx.compose.foundation.layout.Spacer
import androidx.compose.foundation.layout.fillMaxSize
import androidx.compose.foundation.layout.fillMaxWidth
import androidx.compose.foundation.layout.height
import androidx.compose.foundation.layout.padding
import androidx.compose.foundation.layout.size
import androidx.compose.foundation.shape.CircleShape
import androidx.compose.foundation.shape.RoundedCornerShape
import androidx.compose.runtime.Composable
import androidx.compose.runtime.getValue
import androidx.compose.runtime.mutableStateOf
import androidx.compose.runtime.remember
import androidx.compose.runtime.setValue
import androidx.compose.ui.Alignment
import androidx.compose.ui.Modifier
import androidx.compose.ui.draw.clip
import androidx.compose.ui.focus.FocusRequester
import androidx.compose.ui.focus.onFocusChanged
import androidx.compose.ui.graphics.Color
import androidx.compose.ui.graphics.graphicsLayer
import androidx.compose.ui.input.key.Key
import androidx.compose.ui.input.key.KeyEventType
import androidx.compose.ui.input.key.key
import androidx.compose.ui.input.key.onPreviewKeyEvent
import androidx.compose.ui.input.key.type
import androidx.compose.ui.layout.ContentScale
import androidx.compose.ui.res.painterResource
import androidx.compose.ui.semantics.Role
import androidx.compose.ui.semantics.contentDescription
import androidx.compose.ui.semantics.role
import androidx.compose.ui.semantics.semantics
import androidx.compose.ui.text.font.FontWeight
import androidx.compose.ui.text.style.TextAlign
import androidx.compose.ui.unit.Dp
import androidx.compose.ui.unit.dp
import androidx.compose.ui.unit.sp
import androidx.tv.material3.Text
import com.example.auroratv.R
import com.example.auroratv.theme.AuroraBlue
import com.example.auroratv.theme.AuroraMint
import com.example.auroratv.theme.DeepSpace
import com.example.auroratv.theme.MutedBlue
import com.example.auroratv.theme.SoftWhite

/** The GIZTV mark, used for branding and as the placeholder while artwork loads. */
@Composable
internal fun GizTvMark(
  modifier: Modifier = Modifier,
  cornerRadius: Dp = 10.dp,
  alpha: Float = 1f,
) {
  Image(
    painter = painterResource(R.drawable.giztv_mark),
    contentDescription = null,
    contentScale = ContentScale.Crop,
    alpha = alpha,
    modifier = modifier.clip(RoundedCornerShape(cornerRadius)),
  )
}

/** Poster, still, or backdrop artwork with a GIZTV placeholder while it loads. */
@Composable
internal fun TmdbArtwork(
  url: String?,
  contentDescription: String,
  modifier: Modifier = Modifier,
  compact: Boolean = false,
  fallbackLabel: String = "No image",
) {
  val bitmap = rememberTmdbImage(url)
  Box(modifier = modifier.background(Color(0xFF172A45)), contentAlignment = Alignment.Center) {
    val loaded = bitmap
    if (loaded != null) {
      Image(
        bitmap = loaded,
        contentDescription = contentDescription,
        modifier = Modifier.fillMaxSize(),
        contentScale = ContentScale.Crop,
      )
    } else {
      Column(horizontalAlignment = Alignment.CenterHorizontally) {
        GizTvMark(
          modifier = Modifier.size(if (compact) 28.dp else 46.dp),
          cornerRadius = if (compact) 7.dp else 11.dp,
          alpha = .55f,
        )
        if (!compact) {
          Spacer(Modifier.height(8.dp))
          Text(fallbackLabel, color = MutedBlue, fontSize = 11.sp)
        }
      }
    }
  }
}

@Composable
internal fun CatalogButton(label: String, onClick: () -> Unit, modifier: Modifier = Modifier) {
  var focused by remember { mutableStateOf(false) }
  val scale by animateFloatAsState(if (focused) 1.05f else 1f, label = "$label scale")
  val background by animateColorAsState(if (focused) AuroraMint else SoftWhite, label = "$label background")
  Box(
    modifier =
      modifier.height(38.dp).graphicsLayer { scaleX = scale; scaleY = scale }.clip(RoundedCornerShape(19.dp))
        .background(background).border(2.dp, if (focused) SoftWhite else Color.Transparent, RoundedCornerShape(19.dp))
        .onFocusChanged { focused = it.isFocused }.clickable(onClick = onClick)
        .semantics { role = Role.Button; contentDescription = label }.padding(horizontal = 18.dp),
    contentAlignment = Alignment.Center,
  ) {
    Text(label, color = DeepSpace, fontWeight = FontWeight.Black, fontSize = 12.sp, textAlign = TextAlign.Center)
  }
}

@Composable
internal fun StatusPanel(
  message: String,
  modifier: Modifier,
  actionLabel: String? = null,
  loading: Boolean = false,
  onAction: () -> Unit = {},
) {
  // While loading the mark breathes, so the panel reads as busy rather than stuck.
  val pulse = rememberInfiniteTransition(label = "status pulse")
  val markAlpha by
    pulse.animateFloat(
      initialValue = if (loading) .35f else .7f,
      targetValue = if (loading) .9f else .7f,
      animationSpec =
        infiniteRepeatable(tween(durationMillis = 900, easing = LinearEasing), RepeatMode.Reverse),
      label = "status mark alpha",
    )
  Box(modifier = modifier.fillMaxWidth(), contentAlignment = Alignment.Center) {
    Column(horizontalAlignment = Alignment.CenterHorizontally) {
      GizTvMark(modifier = Modifier.size(56.dp), cornerRadius = 14.dp, alpha = markAlpha)
      Spacer(Modifier.height(14.dp))
      Text(message, color = MutedBlue, fontSize = 15.sp, textAlign = TextAlign.Center)
      if (actionLabel != null) {
        Spacer(Modifier.height(14.dp))
        CatalogButton(actionLabel, onAction)
      }
    }
  }
}

/**
 * Moves focus explicitly on D-pad presses.
 *
 * Compose's own focus search struggles across the lazy lists and grids used by the catalog, so
 * screens hand it the exact neighbour for each direction.
 */
internal fun Modifier.remoteFocusNavigation(
  up: FocusRequester? = null,
  down: FocusRequester? = null,
  left: FocusRequester? = null,
  right: FocusRequester? = null,
): Modifier =
  onPreviewKeyEvent { event ->
    val destination =
      when (event.key) {
        Key.DirectionUp -> up
        Key.DirectionDown -> down
        Key.DirectionLeft -> left
        Key.DirectionRight -> right
        else -> null
      }
    if (destination == null) {
      false
    } else {
      if (event.type == KeyEventType.KeyDown) destination.requestFocus()
      true
    }
  }
