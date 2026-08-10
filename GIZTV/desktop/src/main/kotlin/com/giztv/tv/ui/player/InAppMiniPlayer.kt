package com.giztv.tv.ui.player

import androidx.compose.runtime.Composable
import androidx.compose.ui.unit.Dp
import androidx.compose.ui.unit.dp
import com.giztv.tv.data.PlaybackContext

@Composable
internal fun InAppMiniPlayer(
  activeRequest: HlsStreamRequest?,
  minimized: Boolean,
  miniPlayerBottomPadding: Dp = 16.dp,
  onExit: () -> Unit,
  onMinimize: () -> Unit,
  onExpand: () -> Unit,
  onDismissMini: () -> Unit,
  onPlayNext: (PlaybackContext) -> Unit,
  onPrepareNext: (PlaybackContext) -> Unit,
  onHandedOver: () -> Unit,
  onSwitchServer: (Int) -> Unit,
  onPlaybackFailed: () -> Boolean,
  onPlaybackStable: () -> Unit,
) {
  if (activeRequest != null) {
    HlsPlayerScreen(
      request = activeRequest,
      onExit = onExit,
      minimized = minimized,
      miniPlayerBottomPadding = miniPlayerBottomPadding,
      onMinimize = onMinimize,
      onExpand = onExpand,
      onDismissMini = onDismissMini,
      onPlayNext = onPlayNext,
      onPrepareNext = onPrepareNext,
      onHandedOver = onHandedOver,
      onSwitchServer = onSwitchServer,
      onPlaybackFailed = onPlaybackFailed,
      onPlaybackStable = onPlaybackStable,
    )
  }
}
