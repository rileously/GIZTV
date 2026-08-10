package com.giztv.tv.ui.main

import androidx.compose.animation.animateColorAsState
import androidx.compose.animation.core.animateFloatAsState
import androidx.compose.foundation.background
import androidx.compose.foundation.border
import androidx.compose.foundation.clickable
import androidx.compose.foundation.gestures.detectTapGestures
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
import androidx.compose.foundation.layout.offset
import androidx.compose.foundation.layout.padding
import androidx.compose.foundation.layout.size
import androidx.compose.foundation.layout.width
import androidx.compose.foundation.layout.widthIn
import androidx.compose.foundation.shape.CircleShape
import androidx.compose.foundation.shape.RoundedCornerShape
import androidx.compose.foundation.text.BasicTextField
import androidx.compose.foundation.text.KeyboardActions
import androidx.compose.foundation.text.KeyboardOptions
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
import androidx.compose.ui.graphics.SolidColor
import androidx.compose.ui.graphics.graphicsLayer
import androidx.compose.ui.input.pointer.pointerInput
import androidx.compose.ui.input.key.Key
import androidx.compose.ui.input.key.KeyEventType
import androidx.compose.ui.input.key.key
import androidx.compose.ui.input.key.onPreviewKeyEvent
import androidx.compose.ui.input.key.type
import androidx.compose.ui.platform.LocalFocusManager
import androidx.compose.ui.platform.LocalSoftwareKeyboardController
import androidx.compose.ui.semantics.Role
import androidx.compose.ui.semantics.contentDescription
import androidx.compose.ui.semantics.role
import androidx.compose.ui.semantics.semantics
import androidx.compose.ui.text.TextStyle
import androidx.compose.ui.text.font.FontWeight
import androidx.compose.ui.text.input.ImeAction
import androidx.compose.ui.text.input.KeyboardType
import androidx.compose.ui.text.style.TextAlign
import androidx.compose.ui.tooling.preview.Preview
import androidx.compose.ui.unit.dp
import androidx.compose.ui.unit.sp
import androidx.tv.material3.Text
import com.giztv.tv.ui.catalog.GizTvMark
import com.giztv.tv.theme.GizBlue
import com.giztv.tv.theme.GizMint
import com.giztv.tv.theme.GizTvTheme
import com.giztv.tv.theme.DeepSpace
import com.giztv.tv.theme.MutedBlue
import com.giztv.tv.theme.NightSurface
import com.giztv.tv.theme.SoftWhite
import java.net.URI

private const val SKYFLIX_URL = "https://skyflix.to/"

@Composable
fun GizTvApp(
  modifier: Modifier = Modifier,
  onOpenBrowser: (String) -> Unit = {},
  onOpenMovies: () -> Unit = {},
) {
  var urlText by remember { mutableStateOf("") }
  var validationMessage by remember { mutableStateOf<String?>(null) }
  val focusManager = LocalFocusManager.current
  val keyboardController = LocalSoftwareKeyboardController.current
  val moviesFocusRequester = remember { FocusRequester() }
  val urlFieldFocusRequester = remember { FocusRequester() }
  val visitFocusRequester = remember { FocusRequester() }
  val quickAccessFocusRequester = remember { FocusRequester() }

  fun dismissKeyboard() {
    focusManager.clearFocus()
    keyboardController?.hide()
  }

  fun visitEnteredUrl() {
    dismissKeyboard()
    val normalized = normalizeWebUrl(urlText)
    if (normalized == null) {
      validationMessage = "Enter a valid website address"
      urlFieldFocusRequester.requestFocus()
    } else {
      validationMessage = null
      onOpenBrowser(normalized)
    }
  }

  LaunchedEffect(Unit) { visitFocusRequester.requestFocus() }

  BoxWithConstraints(
    modifier =
      modifier.fillMaxSize().pointerInput(Unit) {
        detectTapGestures { dismissKeyboard() }
      }.background(
        Brush.radialGradient(
          colors = listOf(Color(0xFF17345C), DeepSpace),
          radius = 1_250f,
          center = androidx.compose.ui.geometry.Offset(1_450f, 250f),
        )
      )
  ) {
    val narrow = maxWidth < 600.dp
    val compact = maxHeight < 600.dp || narrow
    Box(
      Modifier.align(Alignment.TopEnd).offset(x = 100.dp, y = (-120).dp).size(360.dp).clip(CircleShape)
        .background(GizBlue.copy(alpha = .08f))
    )
    Box(
      Modifier.align(Alignment.BottomStart).offset(x = (-120).dp, y = 150.dp).size(320.dp).clip(CircleShape)
        .background(GizMint.copy(alpha = .06f))
    )

    Column(
      modifier =
        Modifier.fillMaxSize().padding(
          horizontal = if (compact) 24.dp else 58.dp,
          vertical = if (compact) 14.dp else 34.dp,
        ),
      horizontalAlignment = Alignment.CenterHorizontally,
    ) {
      BrandHeader(
        onOpenMovies = onOpenMovies,
        modifier =
          Modifier.focusRequester(moviesFocusRequester).focusProperties {
            down = urlFieldFocusRequester
          },
      )
      Spacer(Modifier.weight(1f))
      BrowserLauncher(
        compact = compact,
        narrow = narrow,
        urlText = urlText,
        validationMessage = validationMessage,
        onUrlChanged = {
          urlText = it
          validationMessage = null
        },
        onVisit = ::visitEnteredUrl,
        onQuickVisit = {
          dismissKeyboard()
          onOpenBrowser(SKYFLIX_URL)
        },
        moviesFocusRequester = moviesFocusRequester,
        urlFieldFocusRequester = urlFieldFocusRequester,
        visitFocusRequester = visitFocusRequester,
        quickAccessFocusRequester = quickAccessFocusRequester,
      )
      Spacer(Modifier.weight(1f))
      Text(
        "Ad blocking · Popup protection · Native HLS playback · English subtitle selection",
        color = MutedBlue,
        fontSize = 12.sp,
        textAlign = TextAlign.Center,
      )
    }
  }
}

@Composable
private fun BrandHeader(onOpenMovies: () -> Unit, modifier: Modifier = Modifier) {
  Row(modifier = Modifier.fillMaxWidth(), verticalAlignment = Alignment.CenterVertically) {
    GizTvMark(modifier = Modifier.size(42.dp), cornerRadius = 12.dp)
    Spacer(Modifier.width(13.dp))
    Text("GIZTV", color = SoftWhite, fontWeight = FontWeight.Black, letterSpacing = 3.sp, fontSize = 21.sp)
    Spacer(Modifier.weight(1f))
    HeaderNavigationButton(label = "Movies", onClick = onOpenMovies, modifier = modifier)
  }
}

@Composable
private fun BrowserLauncher(
  compact: Boolean,
  narrow: Boolean,
  urlText: String,
  validationMessage: String?,
  onUrlChanged: (String) -> Unit,
  onVisit: () -> Unit,
  onQuickVisit: () -> Unit,
  moviesFocusRequester: FocusRequester,
  urlFieldFocusRequester: FocusRequester,
  visitFocusRequester: FocusRequester,
  quickAccessFocusRequester: FocusRequester,
) {
  Column(
    modifier =
      Modifier.widthIn(max = 760.dp).fillMaxWidth().clip(RoundedCornerShape(28.dp))
        .background(NightSurface.copy(alpha = .96f)).border(1.dp, SoftWhite.copy(alpha = .09f), RoundedCornerShape(28.dp))
        .padding(
          horizontal = if (narrow) 20.dp else if (compact) 28.dp else 44.dp,
          vertical = if (compact) 20.dp else 38.dp,
        ),
  ) {
    Text("Browse the web with GIZTV", color = SoftWhite, fontWeight = FontWeight.Black, fontSize = if (compact) 26.sp else 32.sp)
    Spacer(Modifier.height(if (compact) 5.dp else 8.dp))
    Text(
      "Enter a website URL, then GIZTV will open it with ad and popup protection.",
      color = MutedBlue,
      fontSize = if (compact) 13.sp else 15.sp,
      lineHeight = if (compact) 17.sp else 21.sp,
    )
    Spacer(Modifier.height(if (compact) 15.dp else 28.dp))
    Text("WEB ADDRESS", color = GizMint, fontWeight = FontWeight.Bold, letterSpacing = 1.5.sp, fontSize = 11.sp)
    Spacer(Modifier.height(8.dp))
    if (narrow) {
      Column(modifier = Modifier.focusGroup(), verticalArrangement = Arrangement.spacedBy(12.dp)) {
        UrlInput(
          value = urlText,
          onValueChange = onUrlChanged,
          onGo = onVisit,
          hasError = validationMessage != null,
          modifier =
            Modifier.focusRequester(urlFieldFocusRequester).focusProperties {
              up = moviesFocusRequester
              down = visitFocusRequester
            }.remoteFocusNavigation(up = moviesFocusRequester, down = visitFocusRequester).fillMaxWidth(),
        )
        TvActionButton(
          label = "Visit Website",
          primary = true,
          onClick = onVisit,
          modifier =
            Modifier.focusRequester(visitFocusRequester).focusProperties {
              up = urlFieldFocusRequester
              down = quickAccessFocusRequester
            }.remoteFocusNavigation(up = urlFieldFocusRequester, down = quickAccessFocusRequester).fillMaxWidth(),
        )
      }
    } else {
      Row(
        modifier = Modifier.focusGroup(),
        horizontalArrangement = Arrangement.spacedBy(12.dp),
        verticalAlignment = Alignment.CenterVertically,
      ) {
        UrlInput(
          value = urlText,
          onValueChange = onUrlChanged,
          onGo = onVisit,
          hasError = validationMessage != null,
          modifier =
            Modifier.focusRequester(urlFieldFocusRequester).focusProperties {
              up = moviesFocusRequester
              down = visitFocusRequester
              right = visitFocusRequester
            }.remoteFocusNavigation(up = moviesFocusRequester, down = visitFocusRequester).weight(1f),
        )
        TvActionButton(
          label = "Visit Website",
          primary = true,
          onClick = onVisit,
          modifier =
            Modifier.focusRequester(visitFocusRequester).focusProperties {
              up = urlFieldFocusRequester
              left = urlFieldFocusRequester
              down = quickAccessFocusRequester
            }.remoteFocusNavigation(
              up = urlFieldFocusRequester,
              down = quickAccessFocusRequester,
              left = urlFieldFocusRequester,
            ),
        )
      }
    }
    if (validationMessage != null) {
      Text(validationMessage, color = Color(0xFFFF9A9A), fontSize = 12.sp, modifier = Modifier.padding(top = 8.dp))
    }
    Spacer(Modifier.height(if (compact) 16.dp else 30.dp))
    Text("QUICK ACCESS", color = MutedBlue, fontWeight = FontWeight.Bold, letterSpacing = 1.5.sp, fontSize = 11.sp)
    Spacer(Modifier.height(10.dp))
    QuickWebsiteButton(
      onClick = onQuickVisit,
      modifier =
        Modifier.focusRequester(quickAccessFocusRequester).focusProperties {
          up = visitFocusRequester
        }.remoteFocusNavigation(up = visitFocusRequester),
    )
  }
}

@Composable
private fun UrlInput(
  value: String,
  onValueChange: (String) -> Unit,
  onGo: () -> Unit,
  hasError: Boolean,
  modifier: Modifier = Modifier,
) {
  var focused by remember { mutableStateOf(false) }
  val borderColor by animateColorAsState(
    when {
      hasError -> Color(0xFFFF8C8C)
      focused -> GizBlue
      else -> SoftWhite.copy(alpha = .16f)
    },
    label = "URL field border",
  )

  BasicTextField(
    value = value,
    onValueChange = onValueChange,
    modifier =
      modifier.height(58.dp).clip(RoundedCornerShape(13.dp)).background(DeepSpace)
        .border(2.dp, borderColor, RoundedCornerShape(13.dp)).onFocusChanged { focused = it.isFocused }
        .semantics { contentDescription = "Website URL" },
    textStyle = TextStyle(color = SoftWhite, fontSize = 16.sp, fontWeight = FontWeight.Medium),
    singleLine = true,
    cursorBrush = SolidColor(GizMint),
    keyboardOptions = KeyboardOptions(keyboardType = KeyboardType.Uri, imeAction = ImeAction.Go),
    keyboardActions = KeyboardActions(onGo = { onGo() }),
    decorationBox = { innerTextField ->
      Box(Modifier.fillMaxSize().padding(horizontal = 18.dp), contentAlignment = Alignment.CenterStart) {
        if (value.isBlank()) Text("https://example.com", color = MutedBlue.copy(alpha = .7f), fontSize = 16.sp)
        innerTextField()
      }
    },
  )
}

@Composable
private fun QuickWebsiteButton(onClick: () -> Unit, modifier: Modifier = Modifier) {
  var focused by remember { mutableStateOf(false) }
  val scale by animateFloatAsState(if (focused) 1.035f else 1f, label = "quick site scale")
  val background by animateColorAsState(
    if (focused) Color(0xFF253F64) else DeepSpace,
    label = "quick site background",
  )

  Row(
    modifier =
      modifier.fillMaxWidth().graphicsLayer { scaleX = scale; scaleY = scale }.clip(RoundedCornerShape(14.dp))
        .background(background).border(1.dp, if (focused) GizBlue else SoftWhite.copy(alpha = .1f), RoundedCornerShape(14.dp))
        .onFocusChanged { focused = it.isFocused }.clickable(onClick = onClick)
        .semantics { role = Role.Button; contentDescription = "Open skyflix.to" }.padding(horizontal = 18.dp, vertical = 14.dp),
    verticalAlignment = Alignment.CenterVertically,
  ) {
    Box(Modifier.size(11.dp).clip(CircleShape).background(GizMint))
    Spacer(Modifier.width(12.dp))
    Column {
      Text("skyflix.to", color = SoftWhite, fontWeight = FontWeight.Bold, fontSize = 16.sp)
      Text("Open with protected browser", color = MutedBlue, fontSize = 11.sp)
    }
    Spacer(Modifier.weight(1f))
    Text("OPEN  ›", color = GizMint, fontWeight = FontWeight.Bold, fontSize = 12.sp)
  }
}

@Composable
private fun HeaderNavigationButton(label: String, onClick: () -> Unit, modifier: Modifier = Modifier) {
  var focused by remember { mutableStateOf(false) }
  val background by animateColorAsState(if (focused) GizMint else SoftWhite.copy(alpha = .10f), label = "$label header background")
  Box(
    modifier =
      modifier.height(42.dp).clip(RoundedCornerShape(12.dp)).background(background)
        .border(2.dp, if (focused) SoftWhite else SoftWhite.copy(alpha = .12f), RoundedCornerShape(12.dp))
        .onFocusChanged { focused = it.isFocused }.clickable(onClick = onClick)
        .semantics { role = Role.Button; contentDescription = label }.padding(horizontal = 18.dp),
    contentAlignment = Alignment.Center,
  ) {
    Text(label, color = if (focused) DeepSpace else GizMint, fontWeight = FontWeight.Bold, fontSize = 12.sp)
  }
}

private fun Modifier.remoteFocusNavigation(
  up: FocusRequester? = null,
  down: FocusRequester? = null,
  left: FocusRequester? = null,
  right: FocusRequester? = null,
): Modifier =
  onPreviewKeyEvent { event ->
    if (event.type != KeyEventType.KeyDown) return@onPreviewKeyEvent false
    val destination =
      when (event.key) {
        Key.DirectionUp -> up
        Key.DirectionDown -> down
        Key.DirectionLeft -> left
        Key.DirectionRight -> right
        else -> null
      } ?: return@onPreviewKeyEvent false
    val handled: Boolean = runCatching { destination.requestFocus(); true }.getOrElse { false }
    handled
  }

@Composable
private fun TvActionButton(
  label: String,
  primary: Boolean,
  onClick: () -> Unit,
  modifier: Modifier = Modifier,
) {
  var focused by remember { mutableStateOf(false) }
  val scale by animateFloatAsState(if (focused) 1.06f else 1f, label = "action scale")
  val background by animateColorAsState(
    if (primary || focused) SoftWhite else SoftWhite.copy(alpha = .12f),
    label = "action background",
  )
  val foreground = if (primary || focused) DeepSpace else SoftWhite

  Box(
    modifier =
      modifier.height(58.dp).graphicsLayer { scaleX = scale; scaleY = scale }.clip(RoundedCornerShape(13.dp))
        .background(background).border(2.dp, if (focused) GizMint else Color.Transparent, RoundedCornerShape(13.dp))
        .onFocusChanged { focused = it.isFocused }.clickable(onClick = onClick)
        .semantics { role = Role.Button; contentDescription = label }.padding(horizontal = 22.dp),
    contentAlignment = Alignment.Center,
  ) {
    Text(label, color = foreground, fontWeight = FontWeight.Bold, fontSize = 14.sp)
  }
}

internal fun normalizeWebUrl(input: String): String? {
  val trimmed = input.trim()
  if (trimmed.isBlank() || trimmed.any(Char::isWhitespace)) return null
  val candidate = if (trimmed.contains("://")) trimmed else "https://$trimmed"
  val uri = runCatching { URI(candidate) }.getOrNull() ?: return null
  if (uri.scheme?.lowercase() !in setOf("http", "https") || uri.host.isNullOrBlank()) return null
  return candidate
}

@Preview(widthDp = 960, heightDp = 540, showBackground = true)
@Composable
private fun GizTvPreview() {
  GizTvTheme { GizTvApp() }
}
