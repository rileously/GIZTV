package com.giztv.tv.data

import android.content.Context

private const val UI_PREFERENCES = "giztv_ui_preferences"
private const val KEY_LAST_TAB = "last_tab"
private const val KEY_LAST_CATEGORY_PREFIX = "last_category_"
private const val KEY_LAST_SEASON_PREFIX = "last_season_"
private const val KEY_ANIME_LANGUAGE = "anime_language"

/** Small bits of "where was I" state: the tab, the category, and each show's season. */
internal class UiPreferencesStore(context: Context) {
  private val preferences =
    context.applicationContext.getSharedPreferences(UI_PREFERENCES, Context.MODE_PRIVATE)

  fun lastTab(): String? = preferences.getString(KEY_LAST_TAB, null)

  fun setLastTab(tab: String) {
    preferences.edit().putString(KEY_LAST_TAB, tab).apply()
  }

  fun lastCategory(tab: String): String? = preferences.getString(KEY_LAST_CATEGORY_PREFIX + tab, null)

  fun setLastCategory(tab: String, category: String) {
    preferences.edit().putString(KEY_LAST_CATEGORY_PREFIX + tab, category).apply()
  }

  fun lastSeason(showId: Int): Int? =
    preferences.getInt(KEY_LAST_SEASON_PREFIX + showId, -1).takeIf { it >= 0 }

  fun setLastSeason(showId: Int, seasonNumber: Int) {
    preferences.edit().putInt(KEY_LAST_SEASON_PREFIX + showId, seasonNumber).apply()
  }

  /**
   * Dub or sub, as a language code.
   *
   * Anime is watched one way or the other and almost never both, so the choice is remembered across
   * titles rather than asked again on each one.
   */
  fun animeLanguage(): String? = preferences.getString(KEY_ANIME_LANGUAGE, null)

  fun setAnimeLanguage(code: String) {
    preferences.edit().putString(KEY_ANIME_LANGUAGE, code).apply()
  }
}
