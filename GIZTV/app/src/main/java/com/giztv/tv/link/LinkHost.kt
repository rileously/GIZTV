package com.giztv.tv.link

import android.content.Context
import com.giztv.tv.home.isTelevision
import kotlinx.coroutines.flow.MutableStateFlow
import kotlinx.coroutines.flow.StateFlow

/**
 * Owns the television's remote-control server for as long as the app is running.
 *
 * Kept beside the process rather than inside a screen: the socket should outlive the catalog being
 * replaced by the player, and a phone holding a connection should not be dropped because the viewer
 * opened something.
 */
internal object LinkHost {
  private var server: LinkServer? = null
  private val idle = MutableStateFlow<String?>(null)

  /** The pairing code the television should be showing, or null when it is not asking for one. */
  val pairingCode: StateFlow<String?>
    get() = server?.pairingCode ?: idle

  /** Where this television can be reached, for a phone that has to be told rather than find it. */
  val address: StateFlow<String?>
    get() = server?.address ?: idle

  @Synchronized
  fun start(context: Context) {
    // A phone is what holds the remote; only a television is the thing being pointed at.
    if (!context.isTelevision()) return
    if (server != null) return
    server = LinkServer(context.applicationContext).also(LinkServer::start)
  }

  @Synchronized
  fun stop() {
    server?.stop()
    server = null
  }

  /** Dismisses the code the television is showing, without turning away phones already let in. */
  fun cancelPairing() {
    server?.cancelPairing()
  }

  fun forgetPairedPhones() {
    server?.forgetPairedPhones()
  }
}
