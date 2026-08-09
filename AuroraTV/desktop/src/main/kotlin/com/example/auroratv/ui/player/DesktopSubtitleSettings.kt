package com.example.auroratv.ui.player

import androidx.compose.foundation.background
import androidx.compose.foundation.clickable
import androidx.compose.foundation.layout.Arrangement
import androidx.compose.foundation.layout.Box
import androidx.compose.foundation.layout.BoxWithConstraints
import androidx.compose.foundation.layout.Column
import androidx.compose.foundation.layout.ExperimentalLayoutApi
import androidx.compose.foundation.layout.FlowRow
import androidx.compose.foundation.layout.Row
import androidx.compose.foundation.layout.Spacer
import androidx.compose.foundation.layout.fillMaxSize
import androidx.compose.foundation.layout.fillMaxWidth
import androidx.compose.foundation.layout.height
import androidx.compose.foundation.layout.padding
import androidx.compose.foundation.layout.width
import androidx.compose.foundation.layout.widthIn
import androidx.compose.foundation.rememberScrollState
import androidx.compose.foundation.shape.RoundedCornerShape
import androidx.compose.foundation.verticalScroll
import androidx.compose.material3.DropdownMenu
import androidx.compose.material3.DropdownMenuItem
import androidx.compose.material3.Text
import androidx.compose.runtime.Composable
import androidx.compose.runtime.getValue
import androidx.compose.runtime.mutableStateOf
import androidx.compose.runtime.remember
import androidx.compose.runtime.setValue
import androidx.compose.ui.Alignment
import androidx.compose.ui.Modifier
import androidx.compose.ui.draw.clip
import androidx.compose.ui.geometry.Offset
import androidx.compose.ui.graphics.Color
import androidx.compose.ui.graphics.Shadow
import androidx.compose.ui.text.TextStyle
import androidx.compose.ui.text.font.FontWeight
import androidx.compose.ui.text.style.TextAlign
import androidx.compose.ui.unit.dp
import androidx.compose.ui.unit.sp
import com.example.auroratv.theme.AuroraMint
import com.example.auroratv.theme.MutedBlue
import com.example.auroratv.theme.NightSurface
import com.example.auroratv.theme.SoftWhite

/** Caption visible at [playbackPositionMs], after applying the viewer's timing adjustment. */
internal fun desktopSubtitleTextAt(
  cues: List<SubtitleCue>,
  playbackPositionMs: Long,
  offsetMs: Long,
): String? {
  if (playbackPositionMs < 0L || cues.isEmpty()) return null
  val sourcePosition = playbackPositionMs - offsetMs
  return cues
    .asSequence()
    .filter { sourcePosition in it.startMs..it.endMs }
    .map { it.text }
    .distinct()
    .joinToString("\n")
    .takeIf(String::isNotBlank)
}

@Composable
internal fun DesktopSubtitleCueOverlay(
  text: String,
  size: SubtitleSizeOption,
  position: SubtitlePositionOption,
  style: SubtitleStyleOption,
) {
  BoxWithConstraints(modifier = Modifier.fillMaxSize()) {
    val textModifier =
      Modifier
        .align(Alignment.BottomCenter)
        .padding(start = 48.dp, end = 48.dp, bottom = maxHeight * position.bottomPadding)
        .widthIn(max = 1080.dp)
        .then(
          if (style == SubtitleStyleOption.DARK_BOX) {
            Modifier
              .clip(RoundedCornerShape(8.dp))
              .background(Color.Black.copy(alpha = 0.72f))
              .padding(horizontal = 14.dp, vertical = 7.dp)
          } else {
            Modifier
          }
        )
    val fontSize = 29.sp * size.scale
    Text(
      text = text,
      modifier = textModifier,
      color = Color.White,
      fontSize = fontSize,
      lineHeight = 35.sp * size.scale,
      fontWeight = FontWeight.SemiBold,
      textAlign = TextAlign.Center,
      style =
        TextStyle(
          shadow =
            if (style == SubtitleStyleOption.OUTLINE) {
              Shadow(color = Color.Black, offset = Offset(2f, 2f), blurRadius = 5f)
            } else {
              null
            }
        ),
    )
  }
}

@OptIn(ExperimentalLayoutApi::class)
@Composable
internal fun DesktopSubtitleSettingsPanel(
  tracks: List<ExternalSubtitleTrack>,
  selectedTrackIndex: Int?,
  size: SubtitleSizeOption,
  position: SubtitlePositionOption,
  style: SubtitleStyleOption,
  offsetMs: Long,
  loading: Boolean,
  loadError: String?,
  onTrackSelected: (Int?) -> Unit,
  onSizeSelected: (SubtitleSizeOption) -> Unit,
  onPositionSelected: (SubtitlePositionOption) -> Unit,
  onStyleSelected: (SubtitleStyleOption) -> Unit,
  onOffsetSelected: (Long) -> Unit,
  onClose: () -> Unit,
) {
  Box(
    modifier = Modifier.fillMaxSize().background(Color.Black.copy(alpha = 0.62f)),
    contentAlignment = Alignment.Center,
  ) {
    Column(
      modifier =
        Modifier
          .width(620.dp)
          .clip(RoundedCornerShape(20.dp))
          .background(NightSurface)
          .padding(24.dp)
          .verticalScroll(rememberScrollState()),
    ) {
      Row(
        modifier = Modifier.fillMaxWidth(),
        verticalAlignment = Alignment.CenterVertically,
        horizontalArrangement = Arrangement.SpaceBetween,
      ) {
        Column {
          Text("Subtitle settings", color = SoftWhite, fontSize = 22.sp, fontWeight = FontWeight.Black)
          Text("Choose a track and tune how captions appear", color = MutedBlue, fontSize = 13.sp)
        }
        SettingChoice(text = "Close", selected = false, onClick = onClose)
      }

      Spacer(Modifier.height(22.dp))
      SettingSectionTitle("Track")
      SubtitleTrackPicker(
        tracks = tracks,
        selectedTrackIndex = selectedTrackIndex,
        onTrackSelected = onTrackSelected,
      )
      when {
        loading -> Text("Loading captions…", color = AuroraMint, fontSize = 12.sp, modifier = Modifier.padding(top = 7.dp))
        loadError != null -> Text(loadError, color = Color(0xFFFF8A80), fontSize = 12.sp, modifier = Modifier.padding(top = 7.dp))
      }

      Spacer(Modifier.height(20.dp))
      SettingSectionTitle("Timing")
      Row(
        modifier = Modifier.fillMaxWidth(),
        horizontalArrangement = Arrangement.spacedBy(9.dp),
        verticalAlignment = Alignment.CenterVertically,
      ) {
        SettingChoice(
          text = "Earlier 0.5s",
          selected = false,
          onClick = { onOffsetSelected((offsetMs - SUBTITLE_SYNC_STEP_MS).coerceAtLeast(-MAX_SUBTITLE_SYNC_MS)) },
        )
        SettingChoice(text = desktopSubtitleOffsetLabel(offsetMs), selected = offsetMs != 0L, onClick = { onOffsetSelected(0L) })
        SettingChoice(
          text = "Later 0.5s",
          selected = false,
          onClick = { onOffsetSelected((offsetMs + SUBTITLE_SYNC_STEP_MS).coerceAtMost(MAX_SUBTITLE_SYNC_MS)) },
        )
      }
      Text(
        text = desktopSubtitleOffsetDescription(offsetMs),
        color = MutedBlue,
        fontSize = 12.sp,
        modifier = Modifier.padding(top = 7.dp),
      )

      Spacer(Modifier.height(20.dp))
      SettingSectionTitle("Size")
      FlowRow(horizontalArrangement = Arrangement.spacedBy(8.dp), verticalArrangement = Arrangement.spacedBy(8.dp)) {
        SubtitleSizeOption.entries.forEach { option ->
          SettingChoice(option.label, option == size) { onSizeSelected(option) }
        }
      }

      Spacer(Modifier.height(20.dp))
      SettingSectionTitle("Position")
      FlowRow(horizontalArrangement = Arrangement.spacedBy(8.dp), verticalArrangement = Arrangement.spacedBy(8.dp)) {
        SubtitlePositionOption.entries.forEach { option ->
          SettingChoice(option.label, option == position) { onPositionSelected(option) }
        }
      }

      Spacer(Modifier.height(20.dp))
      SettingSectionTitle("Style")
      FlowRow(horizontalArrangement = Arrangement.spacedBy(8.dp), verticalArrangement = Arrangement.spacedBy(8.dp)) {
        SubtitleStyleOption.entries.forEach { option ->
          SettingChoice(option.label, option == style) { onStyleSelected(option) }
        }
      }
    }
  }
}

@Composable
private fun SubtitleTrackPicker(
  tracks: List<ExternalSubtitleTrack>,
  selectedTrackIndex: Int?,
  onTrackSelected: (Int?) -> Unit,
) {
  var expanded by remember { mutableStateOf(false) }
  val label = selectedTrackIndex?.let(tracks::getOrNull)?.label ?: "Off"
  Box(modifier = Modifier.fillMaxWidth()) {
    Row(
      modifier =
        Modifier
          .fillMaxWidth()
          .clip(RoundedCornerShape(10.dp))
          .background(Color.Black.copy(alpha = 0.3f))
          .clickable { expanded = true }
          .padding(horizontal = 14.dp, vertical = 12.dp),
      horizontalArrangement = Arrangement.SpaceBetween,
      verticalAlignment = Alignment.CenterVertically,
    ) {
      Text(label, color = SoftWhite, fontSize = 14.sp, fontWeight = FontWeight.Bold)
      Text("▾", color = AuroraMint, fontSize = 16.sp)
    }
    DropdownMenu(
      expanded = expanded,
      onDismissRequest = { expanded = false },
      modifier = Modifier.width(560.dp).background(NightSurface),
    ) {
      DropdownMenuItem(
        text = { Text("Off", color = if (selectedTrackIndex == null) AuroraMint else SoftWhite) },
        onClick = {
          onTrackSelected(null)
          expanded = false
        },
      )
      tracks.forEachIndexed { index, track ->
        DropdownMenuItem(
          text = { Text(track.label, color = if (index == selectedTrackIndex) AuroraMint else SoftWhite) },
          onClick = {
            onTrackSelected(index)
            expanded = false
          },
        )
      }
    }
  }
}

@Composable
private fun SettingSectionTitle(text: String) {
  Text(
    text = text,
    color = SoftWhite.copy(alpha = 0.72f),
    fontSize = 12.sp,
    fontWeight = FontWeight.Bold,
    modifier = Modifier.padding(bottom = 8.dp),
  )
}

@Composable
private fun SettingChoice(text: String, selected: Boolean, onClick: () -> Unit) {
  Box(
    modifier =
      Modifier
        .clip(RoundedCornerShape(9.dp))
        .background(if (selected) AuroraMint else Color.Black.copy(alpha = 0.3f))
        .clickable(onClick = onClick)
        .padding(horizontal = 12.dp, vertical = 9.dp),
    contentAlignment = Alignment.Center,
  ) {
    Text(
      text = text,
      color = if (selected) Color.Black else SoftWhite,
      fontSize = 12.sp,
      fontWeight = FontWeight.Bold,
    )
  }
}

private fun desktopSubtitleOffsetLabel(offsetMs: Long): String =
  when {
    offsetMs == 0L -> "Reset 0.0s"
    offsetMs > 0L -> "Reset +${offsetMs / 1_000f}s"
    else -> "Reset ${offsetMs / 1_000f}s"
  }

private fun desktopSubtitleOffsetDescription(offsetMs: Long): String =
  when {
    offsetMs == 0L -> "Captions use the source timing"
    offsetMs < 0L -> "Captions appear ${kotlin.math.abs(offsetMs) / 1_000f}s earlier"
    else -> "Captions appear ${offsetMs / 1_000f}s later"
  }

private const val SUBTITLE_SYNC_STEP_MS = 500L
