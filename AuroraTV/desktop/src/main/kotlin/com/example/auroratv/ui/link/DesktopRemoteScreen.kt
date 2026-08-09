package com.example.auroratv.ui.link

import androidx.compose.runtime.Composable

@Composable
internal fun RemoteScreen(
  onBack: () -> Unit,
  onPlayHere: (pageUrl: String, title: String, subtitle: String?, posterUrl: String?, positionMs: Long) -> Unit = { _, _, _, _, _ -> },
) {}

@Composable
internal fun PairingCodeOverlay() {}
