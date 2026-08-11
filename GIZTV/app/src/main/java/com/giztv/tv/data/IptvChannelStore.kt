package com.giztv.tv.data

import android.content.Context
import org.json.JSONArray

private const val IPTV_PREFERENCES = "giztv_iptv"
private const val KEY_FAVORITES = "favorite_channels"
private const val KEY_RECENTS = "recent_channels"
private const val MAX_RECENT_CHANNELS = 24

/**
 * The two shortcuts a live guide of this size needs: the channels worth keeping, and the ones just
 * watched. Only the stable channel key is stored, so a playlist refresh that renumbers or re-hosts
 * an entry keeps the shortcut pointing at the same channel.
 */
internal class IptvChannelStore(context: Context) {
  private val preferences =
    context.applicationContext.getSharedPreferences(IPTV_PREFERENCES, Context.MODE_PRIVATE)

  fun favorites(): List<String> = read(KEY_FAVORITES)

  fun isFavorite(channelKey: String): Boolean = channelKey in favorites()

  /** Adds or removes [channelKey], returning true when it ends up saved. */
  fun toggleFavorite(channelKey: String): Boolean {
    val current = favorites()
    val saved = channelKey !in current
    write(KEY_FAVORITES, if (saved) listOf(channelKey) + current else current - channelKey)
    return saved
  }

  fun recents(): List<String> = read(KEY_RECENTS)

  fun recordWatch(channelKey: String) {
    write(KEY_RECENTS, (listOf(channelKey) + (recents() - channelKey)).take(MAX_RECENT_CHANNELS))
  }

  private fun read(key: String): List<String> =
    runCatching {
        val stored = JSONArray(preferences.getString(key, null) ?: return emptyList())
        (0 until stored.length()).mapNotNull { stored.optString(it).takeIf(String::isNotBlank) }
      }
      .getOrDefault(emptyList())

  private fun write(key: String, values: List<String>) {
    preferences.edit().putString(key, JSONArray(values).toString()).apply()
  }
}
