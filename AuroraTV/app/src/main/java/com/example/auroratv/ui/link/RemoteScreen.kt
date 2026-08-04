package com.example.auroratv.ui.link

import androidx.compose.foundation.background
import androidx.compose.foundation.clickable
import androidx.compose.foundation.layout.Arrangement
import androidx.compose.foundation.layout.Box
import androidx.compose.foundation.layout.Column
import androidx.compose.foundation.layout.Row
import androidx.compose.foundation.layout.Spacer
import androidx.compose.foundation.layout.fillMaxSize
import androidx.compose.foundation.layout.fillMaxWidth
import androidx.compose.foundation.layout.height
import androidx.compose.foundation.layout.padding
import androidx.compose.foundation.layout.size
import androidx.compose.foundation.layout.width
import androidx.compose.foundation.rememberScrollState
import androidx.compose.foundation.shape.CircleShape
import androidx.compose.foundation.shape.RoundedCornerShape
import androidx.compose.foundation.verticalScroll
import androidx.compose.material.icons.Icons
import androidx.compose.material.icons.automirrored.filled.ArrowBack
import androidx.compose.material.icons.automirrored.filled.KeyboardArrowLeft
import androidx.compose.material.icons.automirrored.filled.KeyboardArrowRight
import androidx.compose.material.icons.automirrored.filled.VolumeDown
import androidx.compose.material.icons.automirrored.filled.VolumeUp
import androidx.compose.material.icons.filled.Forward10
import androidx.compose.material.icons.filled.KeyboardArrowDown
import androidx.compose.material.icons.filled.KeyboardArrowUp
import androidx.compose.material.icons.filled.Pause
import androidx.compose.material.icons.filled.PlayArrow
import androidx.compose.material.icons.filled.Refresh
import androidx.compose.material.icons.filled.Replay10
import androidx.compose.material.icons.filled.Stop
import androidx.compose.material3.Icon
import androidx.compose.material3.OutlinedTextField
import androidx.compose.material3.Slider
import androidx.compose.material3.SliderDefaults
import androidx.compose.material3.Text
import androidx.compose.runtime.Composable
import androidx.compose.runtime.DisposableEffect
import androidx.compose.runtime.LaunchedEffect
import androidx.compose.runtime.collectAsState
import androidx.compose.runtime.getValue
import androidx.compose.runtime.mutableStateOf
import androidx.compose.runtime.remember
import androidx.compose.runtime.setValue
import androidx.compose.ui.Alignment
import androidx.compose.ui.Modifier
import androidx.compose.ui.graphics.Color
import androidx.compose.ui.graphics.vector.ImageVector
import androidx.compose.ui.layout.ContentScale
import androidx.compose.ui.platform.LocalContext
import androidx.compose.ui.platform.testTag
import androidx.compose.ui.text.font.FontWeight
import androidx.compose.ui.text.style.TextOverflow
import androidx.compose.ui.unit.dp
import androidx.compose.ui.unit.sp
import com.example.auroratv.data.formatWatchTime
import com.example.auroratv.link.LinkClient
import com.example.auroratv.link.LinkCommand
import com.example.auroratv.link.LinkKey
import com.example.auroratv.link.LinkStatus
import com.example.auroratv.link.LinkTarget
import com.example.auroratv.link.PAIRING_CODE_LENGTH
import com.example.auroratv.theme.AuroraMint
import com.example.auroratv.theme.DeepSpace
import com.example.auroratv.theme.MutedBlue
import com.example.auroratv.theme.NightSurface
import com.example.auroratv.theme.SoftWhite
import com.example.auroratv.ui.catalog.rememberTmdbImage

private const val SKIP_MS = 10_000L

/**
 * The phone's remote for a television running GIZTV.
 *
 * Everything here is a message down one socket; nothing is done locally. What the television is
 * playing comes back the same way, so the progress bar below is the television's own position rather
 * than this screen's guess at it.
 */
@Composable
internal fun RemoteScreen(onBack: () -> Unit) {
  val context = LocalContext.current
  val client = remember { LinkClient(context) }
  val status by client.status.collectAsState()
  val found by client.found.collectAsState()

  DisposableEffect(client) {
    // A television this phone already knows is worth trying before making the viewer pick one.
    client.pairedTelevision()?.let(client::connect) ?: client.discover()
    onDispose(client::disconnect)
  }

  Column(
    modifier =
      Modifier.fillMaxSize().background(DeepSpace).padding(20.dp).verticalScroll(rememberScrollState())
        .testTag("remote_screen")
  ) {
    Row(verticalAlignment = Alignment.CenterVertically) {
      RoundButton(Icons.AutoMirrored.Filled.ArrowBack, "Back", onClick = onBack)
      Spacer(Modifier.width(12.dp))
      Text("Remote", color = SoftWhite, fontSize = 22.sp, fontWeight = FontWeight.Black)
      Spacer(Modifier.weight(1f))
      if (status is LinkStatus.Connected) {
        Text(
          "Disconnect",
          color = MutedBlue,
          fontSize = 14.sp,
          modifier = Modifier.clickable { client.forget() },
        )
      }
    }
    Spacer(Modifier.height(18.dp))

    when (val current = status) {
      LinkStatus.Idle,
      LinkStatus.Searching -> TelevisionPicker(found, client::connect, client::discover)
      is LinkStatus.Connecting -> Notice("Connecting to ${current.target.name}…")
      is LinkStatus.AwaitingCode -> CodeEntry(current.target) { code -> client.connect(current.target, code) }
      is LinkStatus.Failed -> {
        Notice(current.reason)
        Spacer(Modifier.height(14.dp))
        WideButton("Search again", onClick = client::discover)
      }
      is LinkStatus.Connected -> ConnectedRemote(current, client)
    }
  }
}

@Composable
private fun TelevisionPicker(
  found: List<LinkTarget>,
  onPick: (LinkTarget) -> Unit,
  onSearchAgain: () -> Unit,
) {
  if (found.isEmpty()) {
    Notice("Looking for a television running GIZTV on this Wi-Fi…")
  } else {
    found.forEach { target ->
      Row(
        modifier =
          Modifier.fillMaxWidth().padding(vertical = 5.dp)
            .background(NightSurface, RoundedCornerShape(14.dp))
            .clickable { onPick(target) }
            .padding(16.dp),
        verticalAlignment = Alignment.CenterVertically,
      ) {
        Text(target.name, color = SoftWhite, fontSize = 16.sp, fontWeight = FontWeight.Medium)
        Spacer(Modifier.weight(1f))
        Text(target.host, color = MutedBlue, fontSize = 12.sp)
      }
    }
  }
  Spacer(Modifier.height(16.dp))
  WideButton("Search again", onClick = onSearchAgain)
}

@Composable
private fun CodeEntry(target: LinkTarget, onSubmit: (String) -> Unit) {
  var code by remember(target) { mutableStateOf("") }
  Notice("${target.name} is showing a code. Type it here.")
  Spacer(Modifier.height(16.dp))
  OutlinedTextField(
    value = code,
    onValueChange = { entered -> code = entered.filter(Char::isDigit).take(PAIRING_CODE_LENGTH) },
    label = { Text("Pairing code") },
    singleLine = true,
    modifier = Modifier.fillMaxWidth().testTag("pairing_code_field"),
  )
  Spacer(Modifier.height(14.dp))
  // Sent automatically on the last digit, because there is only one thing to do with a full code.
  LaunchedEffect(code) { if (code.length == PAIRING_CODE_LENGTH) onSubmit(code) }
  WideButton("Pair", enabled = code.length == PAIRING_CODE_LENGTH) { onSubmit(code) }
}

@Composable
private fun ConnectedRemote(connected: LinkStatus.Connected, client: LinkClient) {
  val state = connected.state
  val nothingPlaying = state.title == null

  Text(connected.target.name, color = AuroraMint, fontSize = 12.sp, fontWeight = FontWeight.Black)
  Spacer(Modifier.height(12.dp))

  Row(
    modifier = Modifier.fillMaxWidth().background(NightSurface, RoundedCornerShape(18.dp)).padding(14.dp),
    verticalAlignment = Alignment.CenterVertically,
  ) {
    val poster = rememberTmdbImage(state.posterUrl)
    Box(modifier = Modifier.size(48.dp, 68.dp).background(DeepSpace, RoundedCornerShape(10.dp))) {
      poster?.let {
        androidx.compose.foundation.Image(
          bitmap = it,
          contentDescription = null,
          contentScale = ContentScale.Crop,
          modifier = Modifier.fillMaxSize(),
        )
      }
    }
    Spacer(Modifier.width(14.dp))
    Column(Modifier.weight(1f)) {
      Text(
        state.title ?: "Nothing playing",
        color = SoftWhite,
        fontSize = 16.sp,
        fontWeight = FontWeight.Bold,
        maxLines = 1,
        overflow = TextOverflow.Ellipsis,
      )
      state.subtitle?.let { Text(it, color = MutedBlue, fontSize = 13.sp, maxLines = 1) }
    }
  }

  Spacer(Modifier.height(16.dp))

  if (!nothingPlaying && state.durationMs > 0L) {
    Slider(
      value = (state.positionMs.toFloat() / state.durationMs.toFloat()).coerceIn(0f, 1f),
      onValueChange = { client.send(LinkCommand.Seek((it * state.durationMs).toLong())) },
      colors = SliderDefaults.colors(thumbColor = AuroraMint, activeTrackColor = AuroraMint),
    )
    Row(Modifier.fillMaxWidth()) {
      Text(formatWatchTime(state.positionMs), color = MutedBlue, fontSize = 12.sp)
      Spacer(Modifier.weight(1f))
      Text(formatWatchTime(state.durationMs), color = MutedBlue, fontSize = 12.sp)
    }
    Spacer(Modifier.height(10.dp))
  }

  Row(horizontalArrangement = Arrangement.spacedBy(16.dp), verticalAlignment = Alignment.CenterVertically) {
    RoundButton(Icons.Filled.Replay10, "Back ten seconds") { client.send(LinkCommand.SeekBy(-SKIP_MS)) }
    RoundButton(
      if (state.playing) Icons.Filled.Pause else Icons.Filled.PlayArrow,
      if (state.playing) "Pause" else "Play",
      large = true,
    ) {
      client.send(if (state.playing) LinkCommand.Pause else LinkCommand.Resume)
    }
    RoundButton(Icons.Filled.Forward10, "Forward ten seconds") { client.send(LinkCommand.SeekBy(SKIP_MS)) }
    Spacer(Modifier.weight(1f))
    RoundButton(Icons.Filled.Stop, "Stop") { client.send(LinkCommand.Stop) }
  }

  Spacer(Modifier.height(18.dp))
  Row(horizontalArrangement = Arrangement.spacedBy(16.dp), verticalAlignment = Alignment.CenterVertically) {
    RoundButton(Icons.AutoMirrored.Filled.VolumeDown, "Quieter") { client.send(LinkCommand.Volume(-1)) }
    RoundButton(Icons.AutoMirrored.Filled.VolumeUp, "Louder") { client.send(LinkCommand.Volume(1)) }
  }

  Spacer(Modifier.height(22.dp))
  DirectionPad(client)

  Spacer(Modifier.height(22.dp))
  TypeOnTelevision(client)
}

/** The pad, for everything on the television that is not the player. */
@Composable
private fun DirectionPad(client: LinkClient) {
  fun press(key: LinkKey) = client.send(LinkCommand.Key(key))

  Column(horizontalAlignment = Alignment.CenterHorizontally, modifier = Modifier.fillMaxWidth()) {
    RoundButton(Icons.Filled.KeyboardArrowUp, "Up") { press(LinkKey.UP) }
    Row(verticalAlignment = Alignment.CenterVertically, horizontalArrangement = Arrangement.spacedBy(18.dp)) {
      RoundButton(Icons.AutoMirrored.Filled.KeyboardArrowLeft, "Left") { press(LinkKey.LEFT) }
      Box(
        modifier =
          Modifier.size(64.dp).background(AuroraMint, CircleShape).clickable { press(LinkKey.CENTER) },
        contentAlignment = Alignment.Center,
      ) {
        Text("OK", color = DeepSpace, fontWeight = FontWeight.Black, fontSize = 16.sp)
      }
      RoundButton(Icons.AutoMirrored.Filled.KeyboardArrowRight, "Right") { press(LinkKey.RIGHT) }
    }
    RoundButton(Icons.Filled.KeyboardArrowDown, "Down") { press(LinkKey.DOWN) }
    Spacer(Modifier.height(12.dp))
    RoundButton(Icons.AutoMirrored.Filled.ArrowBack, "Back on the television") { press(LinkKey.BACK) }
  }
}

/**
 * A real keyboard for the television's search box.
 *
 * The single worst thing about any television app is spelling a film out one letter at a time on a
 * pad, and this is the whole reason a phone is worth pairing at all.
 */
@Composable
private fun TypeOnTelevision(client: LinkClient) {
  var text by remember { mutableStateOf("") }
  OutlinedTextField(
    value = text,
    onValueChange = { text = it },
    label = { Text("Type on the television") },
    singleLine = true,
    modifier = Modifier.fillMaxWidth().testTag("remote_text_field"),
  )
  Spacer(Modifier.height(10.dp))
  WideButton("Send", enabled = text.isNotBlank()) {
    client.send(LinkCommand.Text(text))
    text = ""
  }
}

@Composable
private fun Notice(message: String) {
  Text(message, color = MutedBlue, fontSize = 15.sp, modifier = Modifier.fillMaxWidth())
}

@Composable
private fun WideButton(
  label: String,
  enabled: Boolean = true,
  onClick: () -> Unit,
) {
  Box(
    modifier =
      Modifier.fillMaxWidth()
        .background(if (enabled) AuroraMint else NightSurface, RoundedCornerShape(14.dp))
        .clickable(enabled = enabled, onClick = onClick)
        .padding(vertical = 14.dp),
    contentAlignment = Alignment.Center,
  ) {
    Text(
      label,
      color = if (enabled) DeepSpace else MutedBlue,
      fontWeight = FontWeight.Bold,
      fontSize = 15.sp,
    )
  }
}

@Composable
private fun RoundButton(
  icon: ImageVector,
  description: String,
  large: Boolean = false,
  onClick: () -> Unit,
) {
  val size = if (large) 64.dp else 48.dp
  Box(
    modifier = Modifier.size(size).background(NightSurface, CircleShape).clickable(onClick = onClick),
    contentAlignment = Alignment.Center,
  ) {
    Icon(icon, contentDescription = description, tint = SoftWhite, modifier = Modifier.size(if (large) 32.dp else 24.dp))
  }
}
