package com.example.auroratv.ui.link

import androidx.compose.foundation.background
import androidx.compose.foundation.clickable
import androidx.compose.foundation.layout.Arrangement
import androidx.compose.foundation.layout.ExperimentalLayoutApi
import androidx.compose.foundation.layout.FlowRow
import androidx.compose.foundation.layout.Box
import androidx.compose.foundation.layout.Column
import androidx.compose.foundation.layout.ColumnScope
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
import androidx.compose.material.icons.automirrored.filled.Backspace
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
import androidx.compose.material3.OutlinedTextFieldDefaults
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
import com.example.auroratv.link.LinkEvent
import com.example.auroratv.link.LinkKey
import com.example.auroratv.link.LINK_DEFAULT_PORT
import com.example.auroratv.link.LinkStatus
import com.example.auroratv.link.LinkTarget
import com.example.auroratv.link.PhoneLink
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
internal fun RemoteScreen(
  onBack: () -> Unit,
  /** Taking what the television is playing and carrying on with it here. */
  onPlayHere: (pageUrl: String, title: String, subtitle: String?, posterUrl: String?, positionMs: Long) -> Unit = { _, _, _, _, _ -> },
) {
  val context = LocalContext.current
  val client = remember(context) { PhoneLink.client(context) }
  val status by client.status.collectAsState()
  val found by client.found.collectAsState()

  DisposableEffect(client) {
    // Only if it is not already connected. The handover opens a connection moments before this
    // screen appears, and reconnecting on top of it tore down the one just used.
    client.ensureConnected()
    onDispose {}
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
        // Hanging up, not falling out. Forgetting the television means pairing with a code all over
        // again, which is not what someone putting their phone down is asking for.
        Text(
          "Disconnect",
          color = MutedBlue,
          fontSize = 14.sp,
          modifier = Modifier.clickable { client.disconnect() },
        )
      }
    }
    Spacer(Modifier.height(18.dp))

    when (val current = status) {
      LinkStatus.Idle,
      LinkStatus.Searching -> TelevisionPicker(found, client::connect, client::discover, client::connectTo)
      is LinkStatus.Connecting -> Notice("Connecting to ${current.target.name}…")
      is LinkStatus.AwaitingCode -> CodeEntry(current.target) { code -> client.connect(current.target, code) }
      is LinkStatus.Failed -> {
        Notice(current.reason)
        Spacer(Modifier.height(14.dp))
        WideButton("Search again", onClick = client::discover)
        Spacer(Modifier.height(22.dp))
        ManualAddress(client::connectTo)
      }
      is LinkStatus.Connected -> ConnectedRemote(current, client, onPlayHere)
    }
  }
}

@Composable
private fun TelevisionPicker(
  found: List<LinkTarget>,
  onPick: (LinkTarget) -> Unit,
  onSearchAgain: () -> Unit,
  onEnterAddress: (String, Int) -> Unit,
) {
  if (found.isEmpty()) {
    Notice("Looking for a television running GIZTV on this network…")
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
  Spacer(Modifier.height(22.dp))
  ManualAddress(onEnterAddress)
}

/**
 * Typing the television's address in, which is the way through when nothing else is.
 *
 * A television sharing this phone's own hotspot cannot be heard announcing itself — no multicast
 * crosses a tethering interface — and a locked-down network may not carry a sweep either. The
 * television shows this address on its pairing screen for exactly this moment.
 */
@Composable
private fun ManualAddress(onEnterAddress: (String, Int) -> Unit) {
  var typed by remember { mutableStateOf("") }
  val address = typed.substringBefore(':').trim()
  val port = typed.substringAfter(':', "").trim().toIntOrNull() ?: LINK_DEFAULT_PORT
  val usable = address.count { it == '.' } == 3 && address.split('.').all { part ->
    part.toIntOrNull()?.let { it in 0..255 } == true
  }

  SectionLabel("OR ENTER THE ADDRESS SHOWN ON THE TV")
  Spacer(Modifier.height(10.dp))
  OutlinedTextField(
    value = typed,
    onValueChange = { typed = it },
    placeholder = { Text("192.168.43.137", color = MutedBlue) },
    singleLine = true,
    colors = remoteFieldColours(),
    modifier = Modifier.fillMaxWidth().testTag("manual_address_field"),
  )
  Spacer(Modifier.height(10.dp))
  WideButton("Connect", enabled = usable) { onEnterAddress(address, port) }
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
    colors = remoteFieldColours(),
    modifier = Modifier.fillMaxWidth().testTag("pairing_code_field"),
  )
  Spacer(Modifier.height(14.dp))
  // Sent automatically on the last digit, because there is only one thing to do with a full code.
  LaunchedEffect(code) { if (code.length == PAIRING_CODE_LENGTH) onSubmit(code) }
  WideButton("Pair", enabled = code.length == PAIRING_CODE_LENGTH) { onSubmit(code) }
}

@Composable
private fun ConnectedRemote(
  connected: LinkStatus.Connected,
  client: LinkClient,
  onPlayHere: (String, String, String?, String?, Long) -> Unit,
) {
  val state = connected.state
  val options by client.options.collectAsState()
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

  // The bar keeps its place whether or not anything is playing, so the buttons below never jump
  // under a thumb that is already reaching for them.
  // Reserved only while something is playing: holding the space open with nothing in it left a
  // hole between the title and the controls.
  if (!nothingPlaying && state.durationMs > 0L) {
    Box(modifier = Modifier.fillMaxWidth().height(56.dp), contentAlignment = Alignment.TopStart) {
      Column {
        Slider(
          value = (state.positionMs.toFloat() / state.durationMs.toFloat()).coerceIn(0f, 1f),
          onValueChange = { client.send(LinkCommand.Seek((it * state.durationMs).toLong())) },
          colors = remoteSliderColours(),
        )
        Row(Modifier.fillMaxWidth()) {
          Text(formatWatchTime(state.positionMs), color = MutedBlue, fontSize = 12.sp)
          Spacer(Modifier.weight(1f))
          Text(formatWatchTime(state.durationMs), color = MutedBlue, fontSize = 12.sp)
        }
      }
    }
  }

  // The other direction. A title started on the television can be picked up here — on the way to
  // bed, or when the room is wanted for something else — and it carries on from the same second.
  state.pageUrl?.let { pageUrl ->
    Spacer(Modifier.height(12.dp))
    WideButton("Watch this on my phone") {
      client.send(LinkCommand.Stop)
      onPlayHere(pageUrl, state.title.orEmpty(), state.subtitle, state.posterUrl, state.positionMs)
    }
  }

  Spacer(Modifier.height(18.dp))
  RemoteSection("PLAYBACK") {
    Row(
      modifier = Modifier.fillMaxWidth(),
      horizontalArrangement = Arrangement.SpaceEvenly,
      verticalAlignment = Alignment.CenterVertically,
    ) {
      RoundButton(Icons.Filled.Replay10, "Back ten seconds") { client.send(LinkCommand.SeekBy(-SKIP_MS)) }
      RoundButton(
        if (state.playing) Icons.Filled.Pause else Icons.Filled.PlayArrow,
        if (state.playing) "Pause" else "Play",
        large = true,
      ) {
        client.send(if (state.playing) LinkCommand.Pause else LinkCommand.Resume)
      }
      RoundButton(Icons.Filled.Forward10, "Forward ten seconds") { client.send(LinkCommand.SeekBy(SKIP_MS)) }
      RoundButton(Icons.Filled.Stop, "Stop") { client.send(LinkCommand.Stop) }
    }
  }

  RemoteSection("VOLUME") { VolumeControls(state.volume, client) }

  RemoteSection("NAVIGATE") { DirectionPad(client) }

  RemoteSection("TYPE ON THE TELEVISION") { TypeOnTelevision(client) }

  if (!nothingPlaying) PlayerSettings(options, client)

  Spacer(Modifier.height(22.dp))
  Text(
    "Forget this television",
    color = MutedBlue,
    fontSize = 13.sp,
    modifier = Modifier.clickable { client.forget() },
  )
  Spacer(Modifier.height(28.dp))
}

/**
 * One titled block of the remote.
 *
 * The controls were a single column of loose rows, which on a phone reads as one long list of
 * unrelated buttons. Boxing each group gives the eye somewhere to stop.
 */
@Composable
private fun RemoteSection(title: String, content: @Composable ColumnScope.() -> Unit) {
  Spacer(Modifier.height(14.dp))
  Column(
    modifier =
      Modifier.fillMaxWidth()
        .background(NightSurface.copy(alpha = .55f), RoundedCornerShape(18.dp))
        .padding(horizontal = 16.dp, vertical = 14.dp)
  ) {
    SectionLabel(title)
    Spacer(Modifier.height(12.dp))
    content()
  }
}

/**
 * The television's own level, dragged rather than nudged.
 *
 * The slider shows the television's actual volume rather than this screen's idea of it, so a change
 * made with the television's own remote turns up here too.
 */
@Composable
private fun VolumeControls(volume: Int, client: LinkClient) {
  Row(verticalAlignment = Alignment.CenterVertically, modifier = Modifier.fillMaxWidth()) {
    RoundButton(Icons.AutoMirrored.Filled.VolumeDown, "Quieter") { client.send(LinkCommand.Volume(-1)) }
    Slider(
      value = volume / 100f,
      onValueChange = { client.send(LinkCommand.SetVolume((it * 100).toInt())) },
      colors = remoteSliderColours(),
      modifier = Modifier.weight(1f).padding(horizontal = 12.dp),
    )
    RoundButton(Icons.AutoMirrored.Filled.VolumeUp, "Louder") { client.send(LinkCommand.Volume(1)) }
  }
}

/** The unfilled part of a slider defaults to near-white, which glares on this screen. */
@Composable
private fun remoteSliderColours() =
  SliderDefaults.colors(
    thumbColor = AuroraMint,
    activeTrackColor = AuroraMint,
    inactiveTrackColor = MutedBlue.copy(alpha = .35f),
  )

@Composable
private fun SectionLabel(text: String) {
  Text(text, color = MutedBlue, fontSize = 11.sp, fontWeight = FontWeight.Black, letterSpacing = 1.5.sp)
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
    placeholder = { Text("Search on the television", color = MutedBlue) },
    singleLine = true,
    colors = remoteFieldColours(),
    modifier = Modifier.fillMaxWidth().testTag("remote_text_field"),
  )
  Spacer(Modifier.height(10.dp))
  Row(verticalAlignment = Alignment.CenterVertically, horizontalArrangement = Arrangement.spacedBy(12.dp)) {
    Box(Modifier.weight(1f)) {
      WideButton("Send", enabled = text.isNotBlank()) {
        client.send(LinkCommand.Text(text))
        text = ""
      }
    }
    // Rubs out on the television rather than here: what is wrong is what is already up there.
    RoundButton(Icons.AutoMirrored.Filled.Backspace, "Backspace on the television") {
      client.send(LinkCommand.Key(LinkKey.BACKSPACE))
    }
  }
}

/**
 * Text that can actually be read.
 *
 * A default field paints its text in the Material scheme's onSurface, which against this screen's
 * near-black background is very nearly the background.
 */
@Composable
private fun remoteFieldColours() =
  OutlinedTextFieldDefaults.colors(
    focusedTextColor = SoftWhite,
    unfocusedTextColor = SoftWhite,
    cursorColor = AuroraMint,
    focusedBorderColor = AuroraMint,
    unfocusedBorderColor = MutedBlue,
    focusedContainerColor = NightSurface,
    unfocusedContainerColor = NightSurface,
    focusedLabelColor = AuroraMint,
    unfocusedLabelColor = MutedBlue,
  )

/**
 * The player's own settings, one titled row per thing that can be changed.
 *
 * The groups are whatever the television says they are, so a stream with no subtitles simply has no
 * subtitle row rather than an empty one.
 */
@OptIn(ExperimentalLayoutApi::class)
@Composable
private fun PlayerSettings(options: LinkEvent.Options, client: LinkClient) {
  if (options.groups.isEmpty()) return

  RemoteSection("PLAYER") {
    options.groups.forEachIndexed { index, group ->
      if (group.items.isEmpty()) return@forEachIndexed
      if (index > 0) Spacer(Modifier.height(16.dp))
      Text(group.label, color = SoftWhite, fontSize = 14.sp, fontWeight = FontWeight.Medium)
      Spacer(Modifier.height(8.dp))
      FlowRow(
        horizontalArrangement = Arrangement.spacedBy(8.dp),
        verticalArrangement = Arrangement.spacedBy(8.dp),
      ) {
        group.items.forEach { item ->
          OptionChip(item.label, item.selected) {
            client.send(LinkCommand.SelectOption(group.id, item.id))
          }
        }
      }
    }

    Spacer(Modifier.height(16.dp))
    Text("Subtitle sync", color = SoftWhite, fontSize = 14.sp, fontWeight = FontWeight.Medium)
    Spacer(Modifier.height(8.dp))
    Row(
      verticalAlignment = Alignment.CenterVertically,
      horizontalArrangement = Arrangement.spacedBy(12.dp),
    ) {
      OptionChip("−0.5s", false) { client.send(LinkCommand.SubtitleSync(-500L)) }
      Text(
        subtitleOffsetLabel(options.subtitleOffsetMs),
        color = AuroraMint,
        fontSize = 14.sp,
        fontWeight = FontWeight.Bold,
      )
      OptionChip("+0.5s", false) { client.send(LinkCommand.SubtitleSync(500L)) }
    }
  }
}

private fun subtitleOffsetLabel(offsetMs: Long): String =
  when {
    offsetMs == 0L -> "In sync"
    offsetMs > 0L -> "+%.1fs".format(offsetMs / 1000f)
    else -> "%.1fs".format(offsetMs / 1000f)
  }

@Composable
private fun OptionChip(label: String, selected: Boolean, onClick: () -> Unit) {
  Box(
    modifier =
      Modifier.background(
          if (selected) AuroraMint else NightSurface,
          RoundedCornerShape(10.dp),
        )
        .clickable(onClick = onClick)
        .padding(horizontal = 14.dp, vertical = 9.dp)
  ) {
    Text(
      label,
      color = if (selected) DeepSpace else SoftWhite,
      fontSize = 13.sp,
      fontWeight = if (selected) FontWeight.Bold else FontWeight.Normal,
    )
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
