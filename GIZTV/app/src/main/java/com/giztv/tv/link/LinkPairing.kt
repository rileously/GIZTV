package com.giztv.tv.link

import android.content.Context
import java.security.SecureRandom

private const val LINK_PREFERENCES = "giztv_link"
private const val KEY_TOKENS = "paired_tokens"
private const val KEY_OWN_TOKEN = "own_token"
private const val KEY_TV_HOST = "tv_host"
private const val KEY_TV_PORT = "tv_port"
private const val KEY_TV_NAME = "tv_name"
private const val KEY_OWN_PORT = "own_port"

internal const val PAIRING_CODE_LENGTH = 6

/** Long enough that guessing is not worth anyone's evening, short enough to be a single line. */
private const val TOKEN_BYTES = 24

private val secureRandom = SecureRandom()

/**
 * The number shown on the television for the viewer to type into the phone.
 *
 * Six digits, because it is read off a screen across a room and typed with a thumb. It only has to
 * survive the minute it is on screen: a wrong code closes the connection and the next attempt is
 * given a fresh one, so there is nothing to grind away at.
 */
internal fun newPairingCode(): String =
  (1..PAIRING_CODE_LENGTH).map { secureRandom.nextInt(10) }.joinToString("")

internal fun newPairingToken(): String {
  val bytes = ByteArray(TOKEN_BYTES).also(secureRandom::nextBytes)
  return bytes.joinToString("") { "%02x".format(it) }
}

/**
 * Compares two secrets without letting the clock say how much of one was right.
 *
 * A token check that stops at the first wrong character tells anyone timing it where it went wrong,
 * and a token can be guessed a character at a time from that.
 */
internal fun constantTimeEquals(a: String, b: String): Boolean {
  if (a.length != b.length) return false
  var difference = 0
  for (i in a.indices) difference = difference or (a[i].code xor b[i].code)
  return difference == 0
}

/** Which phones this television has let in, and which television this phone has been let into. */
internal class LinkStore(context: Context) {
  private val preferences =
    context.applicationContext.getSharedPreferences(LINK_PREFERENCES, Context.MODE_PRIVATE)

  // -- Television side --------------------------------------------------------------------------

  fun pairedTokens(): Set<String> = preferences.getStringSet(KEY_TOKENS, emptySet()).orEmpty()

  fun addPairedToken(token: String) {
    // The returned set must not be edited in place; SharedPreferences hands back its own instance.
    preferences.edit().putStringSet(KEY_TOKENS, pairedTokens() + token).apply()
  }

  /** The port this television listened on last time, so a phone's stored address keeps working. */
  fun lastPort(): Int? = preferences.getInt(KEY_OWN_PORT, 0).takeIf { it in 1024..65535 }

  fun rememberPort(port: Int) {
    preferences.edit().putInt(KEY_OWN_PORT, port).apply()
  }

  fun forgetPairedPhones() {
    preferences.edit().remove(KEY_TOKENS).apply()
  }

  fun isPaired(token: String): Boolean = pairedTokens().any { constantTimeEquals(it, token) }

  // -- Phone side -------------------------------------------------------------------------------

  /** The phone's own token, kept so a second run does not have to be paired all over again. */
  fun ownToken(): String? = preferences.getString(KEY_OWN_TOKEN, null)

  fun rememberTelevision(token: String, host: String, port: Int, name: String) {
    preferences
      .edit()
      .putString(KEY_OWN_TOKEN, token)
      .putString(KEY_TV_HOST, host)
      .putInt(KEY_TV_PORT, port)
      .putString(KEY_TV_NAME, name)
      .apply()
  }

  /**
   * The television this phone was last connected to.
   *
   * The address is a hint rather than a fact: a router hands out a different one often enough that
   * the phone looks the television up again and only falls back on this while it is still looking.
   */
  fun lastTelevision(): LinkTarget? {
    val host = preferences.getString(KEY_TV_HOST, null) ?: return null
    val port = preferences.getInt(KEY_TV_PORT, 0).takeIf { it > 0 } ?: return null
    return LinkTarget(name = preferences.getString(KEY_TV_NAME, null) ?: "Television", host, port)
  }

  fun forgetTelevision() {
    preferences.edit().remove(KEY_OWN_TOKEN).remove(KEY_TV_HOST).remove(KEY_TV_PORT).remove(KEY_TV_NAME).apply()
  }
}

internal data class LinkTarget(val name: String, val host: String, val port: Int)
