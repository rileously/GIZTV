package com.example.auroratv.ui.update

import android.content.ActivityNotFoundException
import android.content.Intent
import android.net.Uri
import android.os.Build
import android.provider.Settings
import androidx.activity.compose.BackHandler
import androidx.activity.compose.rememberLauncherForActivityResult
import androidx.activity.result.contract.ActivityResultContracts
import androidx.compose.foundation.background
import androidx.compose.foundation.focusGroup
import androidx.compose.foundation.layout.Arrangement
import androidx.compose.foundation.layout.Box
import androidx.compose.foundation.layout.Column
import androidx.compose.foundation.layout.Row
import androidx.compose.foundation.layout.Spacer
import androidx.compose.foundation.layout.fillMaxSize
import androidx.compose.foundation.layout.fillMaxWidth
import androidx.compose.foundation.layout.height
import androidx.compose.foundation.layout.padding
import androidx.compose.foundation.layout.width
import androidx.compose.foundation.layout.widthIn
import androidx.compose.foundation.shape.RoundedCornerShape
import androidx.compose.material3.LinearProgressIndicator
import androidx.compose.runtime.Composable
import androidx.compose.runtime.LaunchedEffect
import androidx.compose.runtime.getValue
import androidx.compose.runtime.mutableStateOf
import androidx.compose.runtime.remember
import androidx.compose.runtime.rememberCoroutineScope
import androidx.compose.runtime.setValue
import androidx.compose.ui.Alignment
import androidx.compose.ui.Modifier
import androidx.compose.ui.focus.FocusRequester
import androidx.compose.ui.focus.focusRequester
import androidx.compose.ui.platform.LocalContext
import androidx.compose.ui.platform.testTag
import androidx.compose.ui.text.font.FontWeight
import androidx.compose.ui.text.style.TextOverflow
import androidx.compose.ui.unit.dp
import androidx.compose.ui.unit.sp
import androidx.tv.material3.Text
import com.example.auroratv.BuildConfig
import com.example.auroratv.theme.AuroraBlue
import com.example.auroratv.theme.AuroraMint
import com.example.auroratv.theme.DeepSpace
import com.example.auroratv.theme.MutedBlue
import com.example.auroratv.theme.NightSurface
import com.example.auroratv.theme.SoftWhite
import com.example.auroratv.ui.catalog.CatalogButton
import com.example.auroratv.ui.catalog.remoteFocusNavigation
import com.example.auroratv.update.AppUpdateInfo
import com.example.auroratv.update.AppUpdateService
import com.example.auroratv.update.buildInstallIntent
import java.io.File
import kotlinx.coroutines.delay
import kotlinx.coroutines.launch

internal sealed interface UpdateUiState {
  data object Hidden : UpdateUiState

  data class Available(val update: AppUpdateInfo) : UpdateUiState

  data class Downloading(val update: AppUpdateInfo, val progress: Int?) : UpdateUiState

  data class Ready(val update: AppUpdateInfo, val file: File) : UpdateUiState

  data class Failed(val update: AppUpdateInfo, val message: String) : UpdateUiState
}

/** Checks silently on launch and only takes focus when a newer, verified release is available. */
@Composable
internal fun AppUpdateController(manifestUrl: String = BuildConfig.UPDATE_MANIFEST_URL) {
  val context = LocalContext.current
  val scope = rememberCoroutineScope()
  var state by remember(manifestUrl) { mutableStateOf<UpdateUiState>(UpdateUiState.Hidden) }
  var pendingInstall by remember { mutableStateOf<File?>(null) }

  fun openInstaller(file: File) {
    try {
      context.startActivity(buildInstallIntent(context, file))
    } catch (_: ActivityNotFoundException) {
      val update = (state as? UpdateUiState.Ready)?.update ?: return
      state = UpdateUiState.Failed(update, "Android could not open the app installer on this device.")
    }
  }

  val unknownSourcesLauncher =
    rememberLauncherForActivityResult(ActivityResultContracts.StartActivityForResult()) {
      val file = pendingInstall ?: return@rememberLauncherForActivityResult
      if (Build.VERSION.SDK_INT < Build.VERSION_CODES.O || context.packageManager.canRequestPackageInstalls()) {
        openInstaller(file)
      }
    }

  fun install(file: File) {
    pendingInstall = file
    if (Build.VERSION.SDK_INT < Build.VERSION_CODES.O || context.packageManager.canRequestPackageInstalls()) {
      openInstaller(file)
      return
    }
    try {
      unknownSourcesLauncher.launch(
        Intent(
          Settings.ACTION_MANAGE_UNKNOWN_APP_SOURCES,
          Uri.parse("package:${context.packageName}"),
        )
      )
    } catch (_: ActivityNotFoundException) {
      val update = (state as? UpdateUiState.Ready)?.update ?: return
      state =
        UpdateUiState.Failed(
          update,
          "Allow GIZTV to install apps in Android Settings, then try again.",
        )
    }
  }

  fun download(update: AppUpdateInfo) {
    state = UpdateUiState.Downloading(update, progress = null)
    scope.launch {
      val result =
        AppUpdateService.downloadAndVerify(context, update) { progress ->
          state = UpdateUiState.Downloading(update, progress)
        }
      result.fold(
        onSuccess = { file -> state = UpdateUiState.Ready(update, file) },
        onFailure = { error ->
          state =
            UpdateUiState.Failed(
              update,
              error.message ?: "The update could not be downloaded. Check the connection and try again.",
            )
        },
      )
    }
  }

  LaunchedEffect(manifestUrl) {
    if (manifestUrl.isBlank()) return@LaunchedEffect
    // A failed background check is intentionally quiet; the current version remains fully usable.
    AppUpdateService.checkForUpdate(manifestUrl, BuildConfig.VERSION_CODE.toLong()).getOrNull()?.let {
      state = UpdateUiState.Available(it)
    }
  }

  AppUpdateOverlay(
    state = state,
    onUpdate = ::download,
    onInstall = ::install,
    onDismiss = { state = UpdateUiState.Hidden },
  )
}

@Composable
internal fun AppUpdateOverlay(
  state: UpdateUiState,
  onUpdate: (AppUpdateInfo) -> Unit,
  onInstall: (File) -> Unit,
  onDismiss: () -> Unit,
) {
  if (state is UpdateUiState.Hidden) return

  val primaryFocus = remember { FocusRequester() }
  val secondaryFocus = remember { FocusRequester() }
  val isDownloading = state is UpdateUiState.Downloading
  BackHandler { if (!isDownloading) onDismiss() }

  LaunchedEffect(state) {
    if (!isDownloading) {
      // Catalog rails may restore their own focus in the same frame. Let them settle, then make
      // the modal the only active remote-control surface.
      delay(180)
      primaryFocus.requestFocus()
    }
  }

  val update =
    when (state) {
      is UpdateUiState.Available -> state.update
      is UpdateUiState.Downloading -> state.update
      is UpdateUiState.Ready -> state.update
      is UpdateUiState.Failed -> state.update
      UpdateUiState.Hidden -> return
    }

  Box(
    modifier =
      Modifier.fillMaxSize().focusGroup().background(DeepSpace.copy(alpha = .96f)).padding(horizontal = 40.dp, vertical = 28.dp)
        .testTag("update_overlay"),
    contentAlignment = Alignment.Center,
  ) {
    Column(
      modifier =
        Modifier.widthIn(max = 760.dp).fillMaxWidth().background(NightSurface, RoundedCornerShape(26.dp))
          .padding(horizontal = 42.dp, vertical = 34.dp),
    ) {
      Text(
        text =
          when (state) {
            is UpdateUiState.Available -> "UPDATE AVAILABLE"
            is UpdateUiState.Downloading -> "DOWNLOADING UPDATE"
            is UpdateUiState.Ready -> "READY TO INSTALL"
            is UpdateUiState.Failed -> "UPDATE NEEDS ATTENTION"
            UpdateUiState.Hidden -> ""
          },
        color = AuroraMint,
        fontSize = 13.sp,
        fontWeight = FontWeight.Black,
        letterSpacing = 2.sp,
      )
      Spacer(Modifier.height(8.dp))
      Text("GIZTV ${update.versionName}", color = SoftWhite, fontSize = 32.sp, fontWeight = FontWeight.Black)
      Text(
        "Installed ${BuildConfig.VERSION_NAME}  •  New ${update.versionName}",
        color = MutedBlue,
        fontSize = 14.sp,
      )

      if (update.releaseNotes.isNotBlank() && state !is UpdateUiState.Failed) {
        Spacer(Modifier.height(18.dp))
        Text(
          update.releaseNotes,
          color = SoftWhite,
          fontSize = 16.sp,
          lineHeight = 23.sp,
          maxLines = 4,
          overflow = TextOverflow.Ellipsis,
        )
      }

      when (state) {
        is UpdateUiState.Downloading -> {
          Spacer(Modifier.height(26.dp))
          val progress = state.progress
          if (progress == null) {
            LinearProgressIndicator(
              modifier = Modifier.fillMaxWidth().height(7.dp),
              color = AuroraMint,
              trackColor = AuroraBlue.copy(alpha = .2f),
            )
          } else {
            LinearProgressIndicator(
              progress = { progress / 100f },
              modifier = Modifier.fillMaxWidth().height(7.dp),
              color = AuroraMint,
              trackColor = AuroraBlue.copy(alpha = .2f),
            )
          }
          Spacer(Modifier.height(12.dp))
          Text(
            progress?.let { "$it% downloaded — verifying before install" }
              ?: "Connecting to the release server…",
            color = MutedBlue,
            fontSize = 14.sp,
          )
        }
        is UpdateUiState.Failed -> {
          Spacer(Modifier.height(18.dp))
          Text(state.message, color = SoftWhite, fontSize = 16.sp, lineHeight = 23.sp)
        }
        is UpdateUiState.Ready -> {
          Spacer(Modifier.height(18.dp))
          Text(
            "The download and app signature have been verified. Android will ask you to confirm installation.",
            color = MutedBlue,
            fontSize = 15.sp,
            lineHeight = 22.sp,
          )
        }
        is UpdateUiState.Available -> Unit
        UpdateUiState.Hidden -> Unit
      }

      if (!isDownloading) {
        Spacer(Modifier.height(26.dp))
        Row(horizontalArrangement = Arrangement.spacedBy(16.dp), verticalAlignment = Alignment.CenterVertically) {
          when (state) {
            is UpdateUiState.Available ->
              CatalogButton(
                label = "Update now",
                onClick = { onUpdate(state.update) },
                modifier =
                  Modifier.focusRequester(primaryFocus)
                    .remoteFocusNavigation(
                      up = primaryFocus,
                      down = primaryFocus,
                      left = primaryFocus,
                      right = secondaryFocus,
                    )
                    .testTag("update_primary"),
              )
            is UpdateUiState.Ready ->
              CatalogButton(
                label = "Install now",
                onClick = { onInstall(state.file) },
                modifier =
                  Modifier.focusRequester(primaryFocus)
                    .remoteFocusNavigation(
                      up = primaryFocus,
                      down = primaryFocus,
                      left = primaryFocus,
                      right = secondaryFocus,
                    )
                    .testTag("update_primary"),
              )
            is UpdateUiState.Failed ->
              CatalogButton(
                label = "Try again",
                onClick = { onUpdate(state.update) },
                modifier =
                  Modifier.focusRequester(primaryFocus)
                    .remoteFocusNavigation(
                      up = primaryFocus,
                      down = primaryFocus,
                      left = primaryFocus,
                      right = secondaryFocus,
                    )
                    .testTag("update_primary"),
              )
            UpdateUiState.Hidden, is UpdateUiState.Downloading -> Unit
          }
          Spacer(Modifier.width(2.dp))
          CatalogButton(
            label = "Later",
            onClick = onDismiss,
            modifier =
              Modifier.focusRequester(secondaryFocus)
                .remoteFocusNavigation(
                  up = secondaryFocus,
                  down = secondaryFocus,
                  left = primaryFocus,
                  right = secondaryFocus,
                )
                .testTag("update_later"),
          )
        }
      }
    }
  }
}
