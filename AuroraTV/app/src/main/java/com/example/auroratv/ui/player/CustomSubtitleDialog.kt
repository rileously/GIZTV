package com.example.auroratv.ui.player

import android.net.Uri
import androidx.activity.compose.rememberLauncherForActivityResult
import androidx.activity.result.contract.ActivityResultContracts
import androidx.compose.foundation.background
import androidx.compose.foundation.border
import androidx.compose.foundation.layout.Arrangement
import androidx.compose.foundation.layout.Box
import androidx.compose.foundation.layout.Column
import androidx.compose.foundation.layout.Row
import androidx.compose.foundation.layout.Spacer
import androidx.compose.foundation.layout.fillMaxSize
import androidx.compose.foundation.layout.fillMaxWidth
import androidx.compose.foundation.layout.height
import androidx.compose.foundation.layout.heightIn
import androidx.compose.foundation.layout.padding
import androidx.compose.foundation.layout.size
import androidx.compose.foundation.layout.width
import androidx.compose.foundation.layout.widthIn
import androidx.compose.foundation.shape.RoundedCornerShape
import androidx.compose.foundation.text.BasicTextField
import androidx.compose.foundation.text.KeyboardActions
import androidx.compose.foundation.text.KeyboardOptions
import androidx.compose.material.icons.Icons
import androidx.compose.material.icons.filled.FolderOpen
import androidx.compose.material.icons.filled.Link
import androidx.compose.material3.Icon
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
import androidx.compose.ui.graphics.Color
import androidx.compose.ui.graphics.SolidColor
import androidx.compose.ui.platform.LocalContext
import androidx.compose.ui.semantics.contentDescription
import androidx.compose.ui.semantics.semantics
import androidx.compose.ui.text.TextStyle
import androidx.compose.ui.text.font.FontWeight
import androidx.compose.ui.text.input.ImeAction
import androidx.compose.ui.unit.dp
import androidx.compose.ui.unit.sp
import androidx.compose.ui.window.Dialog
import androidx.media3.common.MimeTypes
import androidx.tv.material3.Text
import com.example.auroratv.theme.AuroraBlue
import com.example.auroratv.theme.AuroraMint
import com.example.auroratv.theme.DeepSpace
import com.example.auroratv.theme.MutedBlue
import com.example.auroratv.theme.NightSurface
import com.example.auroratv.theme.SoftWhite
import com.example.auroratv.ui.catalog.CatalogActionButton
import com.example.auroratv.ui.catalog.CatalogButton

internal fun resolveSubtitleMimeType(pathOrUrl: String): String =
  if (pathOrUrl.endsWith(".srt", ignoreCase = true)) {
    MimeTypes.APPLICATION_SUBRIP
  } else {
    MimeTypes.TEXT_VTT
  }

@Composable
internal fun CustomSubtitleDialog(
  onDismiss: () -> Unit,
  onAddSubtitle: (ExternalSubtitleTrack) -> Unit,
) {
  val context = LocalContext.current
  var urlText by remember { mutableStateOf("") }
  var errorText by remember { mutableStateOf<String?>(null) }
  val inputFocusRequester = remember { FocusRequester() }
  val pickFileFocusRequester = remember { FocusRequester() }
  val addFocusRequester = remember { FocusRequester() }
  val cancelFocusRequester = remember { FocusRequester() }

  val filePickerLauncher =
    rememberLauncherForActivityResult(contract = ActivityResultContracts.GetContent()) { uri: Uri? ->
      if (uri != null) {
        val filename = uri.lastPathSegment?.substringAfterLast('/') ?: "Local Subtitle"
        val mime = resolveSubtitleMimeType(filename)
        val track =
          ExternalSubtitleTrack(
            url = uri.toString(),
            label = "File: $filename",
            language = "custom",
            mimeType = mime,
          )
        onAddSubtitle(track)
        onDismiss()
      }
    }

  Dialog(onDismissRequest = onDismiss) {
    Column(
      modifier =
        Modifier.fillMaxWidth(.95f).widthIn(max = 520.dp).heightIn(max = 440.dp)
          .clip(RoundedCornerShape(20.dp)).background(NightSurface)
          .border(1.dp, SoftWhite.copy(alpha = .16f), RoundedCornerShape(20.dp))
          .padding(20.dp),
    ) {
      Text(
        "ADD CUSTOM SUBTITLE",
        color = SoftWhite,
        fontWeight = FontWeight.Black,
        fontSize = 17.sp,
        letterSpacing = 1.2.sp,
      )
      Spacer(Modifier.height(10.dp))
      Text(
        "Paste a direct URL to a .srt or .vtt file, or pick a file from device storage.",
        color = MutedBlue,
        fontSize = 13.sp,
        lineHeight = 18.sp,
      )
      Spacer(Modifier.height(16.dp))

      var inputFocused by remember { mutableStateOf(false) }
      val inputBorder = if (inputFocused) AuroraBlue else SoftWhite.copy(alpha = .14f)

      BasicTextField(
        value = urlText,
        onValueChange = {
          urlText = it
          errorText = null
        },
        modifier =
          Modifier.fillMaxWidth().height(42.dp).clip(RoundedCornerShape(12.dp))
            .background(DeepSpace.copy(alpha = .55f))
            .border(if (inputFocused) 2.dp else 1.dp, inputBorder, RoundedCornerShape(12.dp))
            .onFocusChanged { inputFocused = it.isFocused }
            .focusRequester(inputFocusRequester)
            .focusProperties { down = pickFileFocusRequester }
            .semantics { contentDescription = "Subtitle URL input" },
        textStyle = TextStyle(color = SoftWhite, fontSize = 13.sp, fontWeight = FontWeight.Medium),
        singleLine = true,
        cursorBrush = SolidColor(AuroraMint),
        keyboardOptions = KeyboardOptions(imeAction = ImeAction.Done),
        keyboardActions = KeyboardActions(onDone = {
          if (urlText.isNotBlank()) {
            val mime = resolveSubtitleMimeType(urlText.trim())
            onAddSubtitle(
              ExternalSubtitleTrack(
                url = urlText.trim(),
                label = "URL: ${urlText.trim().takeLast(24)}",
                language = "custom",
                mimeType = mime,
              )
            )
            onDismiss()
          } else {
            errorText = "Please enter a valid URL or select a local file"
          }
        }),
        decorationBox = { inner ->
          Row(
            modifier = Modifier.fillMaxSize().padding(horizontal = 14.dp),
            verticalAlignment = Alignment.CenterVertically,
          ) {
            Icon(
              Icons.Filled.Link,
              contentDescription = null,
              tint = if (inputFocused) AuroraBlue else MutedBlue,
              modifier = Modifier.size(18.dp),
            )
            Spacer(Modifier.width(10.dp))
            Box(Modifier.weight(1f), contentAlignment = Alignment.CenterStart) {
              if (urlText.isBlank()) Text("https://example.com/subtitles.vtt", color = MutedBlue, fontSize = 13.sp)
              inner()
            }
          }
        },
      )

      errorText?.let { err ->
        Spacer(Modifier.height(6.dp))
        Text(err, color = Color(0xFFFFB4AB), fontSize = 11.sp)
      }

      Spacer(Modifier.height(14.dp))

      CatalogActionButton(
        label = "Pick Local File (.srt / .vtt)",
        icon = Icons.Filled.FolderOpen,
        showLabel = true,
        onClick = { filePickerLauncher.launch("*/*") },
        modifier =
          Modifier.fillMaxWidth().focusRequester(pickFileFocusRequester).focusProperties {
            up = inputFocusRequester
            down = addFocusRequester
          },
      )

      Spacer(Modifier.height(18.dp))

      Row(
        modifier = Modifier.fillMaxWidth(),
        horizontalArrangement = Arrangement.End,
        verticalAlignment = Alignment.CenterVertically,
      ) {
        CatalogButton(
          label = "Cancel",
          onClick = onDismiss,
          modifier =
            Modifier.focusRequester(cancelFocusRequester).focusProperties {
              right = addFocusRequester
              up = pickFileFocusRequester
            },
        )
        Spacer(Modifier.width(12.dp))
        CatalogButton(
          label = "Add Subtitle",
          onClick = {
            if (urlText.isNotBlank()) {
              val mime = resolveSubtitleMimeType(urlText.trim())
              onAddSubtitle(
                ExternalSubtitleTrack(
                  url = urlText.trim(),
                  label = "URL: ${urlText.trim().takeLast(24)}",
                  language = "custom",
                  mimeType = mime,
                )
              )
              onDismiss()
            } else {
              errorText = "Please enter a URL or choose a local file"
            }
          },
          modifier =
            Modifier.focusRequester(addFocusRequester).focusProperties {
              left = cancelFocusRequester
              up = pickFileFocusRequester
            },
        )
      }
    }
  }

  LaunchedEffect(Unit) {
    inputFocusRequester.requestFocus()
  }
}
