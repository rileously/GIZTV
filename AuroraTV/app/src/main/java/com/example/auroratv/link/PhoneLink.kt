package com.example.auroratv.link

import android.content.Context
import kotlinx.coroutines.CoroutineScope
import kotlinx.coroutines.Dispatchers
import kotlinx.coroutines.Job
import kotlinx.coroutines.SupervisorJob
import kotlinx.coroutines.launch

/**
 * The phone's one connection to its television.
 *
 * Shared because two screens need it: the remote, and the player handing a film over. A second
 * client would mean a second socket and a television that thinks two phones are talking to it.
 */
internal object PhoneLink {
  @Volatile private var client: LinkClient? = null

  @Synchronized
  fun client(context: Context): LinkClient =
    client ?: LinkClient(context.applicationContext).also { client = it }

  /** Whether there is a television worth offering to send a film to. */
  fun hasTelevision(context: Context): Boolean = client(context).pairedTelevision() != null

  /**
   * Keeps a media session alive for as long as the television is playing something.
   *
   * Watched here rather than from a screen: the whole point is to still be able to pause from a
   * watch once the phone has gone back in a pocket and every screen is gone.
   */
  @Synchronized
  fun watchForMediaSession(context: Context) {
    if (mediaWatcher != null) return
    val app = context.applicationContext
    mediaWatcher =
      scope.launch {
        client(app).status.collect { status ->
          RemoteMediaService.sync(app, (status as? LinkStatus.Connected)?.state?.title != null)
        }
      }
  }

  private var mediaWatcher: Job? = null
  private val scope = CoroutineScope(SupervisorJob() + Dispatchers.Main.immediate)
}
