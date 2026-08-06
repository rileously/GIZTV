package com.example.auroratv.link

/** One choice inside a group, named by the label the television itself shows for it. */
internal data class RemoteOptionItem(val id: String, val label: String, val selected: Boolean)

/** A row of choices on the phone: the subtitle track, the audio track, the quality, the speed. */
internal data class RemoteOptionGroup(val id: String, val label: String, val items: List<RemoteOptionItem>)

internal const val GROUP_SUBTITLE = "subtitle"
internal const val GROUP_AUDIO = "audio"
internal const val GROUP_QUALITY = "quality"
internal const val GROUP_SPEED = "speed"
internal const val GROUP_RESIZE = "resize"
internal const val GROUP_SERVER = "server"

/**
 * The player's own settings, offered to whatever is holding the remote.
 *
 * The player screen owns all of this state and knows what applying a choice involves — moving the
 * subtitle renderer's clock, writing a preference for an audio track. Rather than move any
 * of that, the screen registers itself here and the phone's picks are handed straight back to the
 * same code the television's own dialog calls.
 */
internal interface RemotePlayerOptions {
  fun groups(): List<RemoteOptionGroup>

  fun select(groupId: String, itemId: String)

  /** Nudging the subtitles earlier or later, which is a number rather than a choice from a list. */
  fun nudgeSubtitleSync(deltaMs: Long)

  fun subtitleOffsetMs(): Long
}
