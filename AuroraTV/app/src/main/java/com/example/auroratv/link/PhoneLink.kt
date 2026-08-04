package com.example.auroratv.link

import android.content.Context

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
}
