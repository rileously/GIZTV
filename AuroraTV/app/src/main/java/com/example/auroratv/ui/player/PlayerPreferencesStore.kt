package com.example.auroratv.ui.player

import android.content.Context

private const val PLAYER_PREFERENCES = "giztv_player_preferences"
private const val KEY_SUBTITLE_TRACK = "subtitle_track"
private const val KEY_AUDIO_TRACK = "audio_track"
private const val KEY_PLAYBACK_SPEED = "playback_speed"
private const val KEY_SUBTITLE_SIZE = "subtitle_size"
private const val KEY_SUBTITLE_POSITION = "subtitle_position"
private const val KEY_SUBTITLE_STYLE = "subtitle_style"
private const val KEY_STABLE_PLAYBACK = "stable_playback"

internal const val SUBTITLES_OFF_LABEL = "Off"
internal const val AUTO_SUBTITLE_LABEL = "Auto English"
internal const val AUTO_AUDIO_LABEL = "Auto English"

/**
 * How the viewer likes to watch, kept between videos.
 *
 * Someone who picks Spanish audio, larger captions or three-quarter speed means it for the next
 * episode too, and re-picking it every time is the sort of thing that makes an app feel dim. These
 * are held for the app rather than per title, unlike [SubtitleSyncStore], because they describe the
 * viewer and not the subtitle file.
 *
 * Tracks are remembered by their label, since the override that selects one only means anything
 * inside the stream it came from. A stream without that label simply falls back to automatic.
 */
internal class PlayerPreferencesStore(context: Context) {
  private val preferences =
    context.applicationContext.getSharedPreferences(PLAYER_PREFERENCES, Context.MODE_PRIVATE)

  fun subtitleTrack(): SubtitleTrackOption {
    val label = preferences.getString(KEY_SUBTITLE_TRACK, null) ?: AUTO_SUBTITLE_LABEL
    return SubtitleTrackOption(label = label, disabled = label == SUBTITLES_OFF_LABEL)
  }

  fun setSubtitleTrack(option: SubtitleTrackOption) {
    preferences.edit().putString(KEY_SUBTITLE_TRACK, option.label).apply()
  }

  fun audioTrack(): AudioTrackOption =
    AudioTrackOption(preferences.getString(KEY_AUDIO_TRACK, null) ?: AUTO_AUDIO_LABEL)

  fun setAudioTrack(option: AudioTrackOption) {
    preferences.edit().putString(KEY_AUDIO_TRACK, option.label).apply()
  }

  fun playbackSpeed(): Float = preferences.getFloat(KEY_PLAYBACK_SPEED, 1f)

  fun setPlaybackSpeed(speed: Float) {
    preferences.edit().putFloat(KEY_PLAYBACK_SPEED, speed).apply()
  }

  fun stablePlayback(): Boolean = preferences.getBoolean(KEY_STABLE_PLAYBACK, false)

  fun setStablePlayback(enabled: Boolean) {
    preferences.edit().putBoolean(KEY_STABLE_PLAYBACK, enabled).apply()
  }

  fun subtitleSize(): SubtitleSizeOption =
    read(KEY_SUBTITLE_SIZE, SubtitleSizeOption.NORMAL, SubtitleSizeOption::valueOf)

  fun setSubtitleSize(option: SubtitleSizeOption) = write(KEY_SUBTITLE_SIZE, option.name)

  fun subtitlePosition(): SubtitlePositionOption =
    read(KEY_SUBTITLE_POSITION, SubtitlePositionOption.BOTTOM, SubtitlePositionOption::valueOf)

  fun setSubtitlePosition(option: SubtitlePositionOption) = write(KEY_SUBTITLE_POSITION, option.name)

  fun subtitleStyle(): SubtitleStyleOption =
    read(KEY_SUBTITLE_STYLE, SubtitleStyleOption.OUTLINE, SubtitleStyleOption::valueOf)

  fun setSubtitleStyle(option: SubtitleStyleOption) = write(KEY_SUBTITLE_STYLE, option.name)

  // A saved name outliving the choice it named must not take the player down with it.
  private fun <T> read(key: String, fallback: T, parse: (String) -> T): T {
    val stored = preferences.getString(key, null) ?: return fallback
    return runCatching { parse(stored) }.getOrDefault(fallback)
  }

  private fun write(key: String, value: String) {
    preferences.edit().putString(key, value).apply()
  }
}
