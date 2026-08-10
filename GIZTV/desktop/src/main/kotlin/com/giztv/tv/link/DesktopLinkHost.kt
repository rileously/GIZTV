package com.giztv.tv.link

import android.content.Context
import kotlinx.coroutines.flow.MutableStateFlow
import kotlinx.coroutines.flow.StateFlow

internal object LinkHost {
  private val idle = MutableStateFlow<String?>(null)

  val pairingCode: StateFlow<String?> get() = idle
  val address: StateFlow<String?> get() = idle

  fun start(context: Context) {}
  fun stop() {}
  fun cancelPairing() {}
  fun forgetPairedPhones() {}
}
