package com.example.auroratv.link

import org.json.JSONArray
import org.json.JSONObject

/**
 * What a phone and a television say to each other.
 *
 * One JSON object per line over a plain socket. A television and the phone in front of it are on the
 * same network and nothing else is listening, so this carries no framing, no compression and no
 * handshake beyond the pairing below: a line is a message, and a closed socket is a hang-up.
 */
internal const val LINK_PROTOCOL_VERSION = 1

/** The service a television advertises and a phone looks for. */
internal const val LINK_SERVICE_TYPE = "_giztv._tcp."

internal sealed interface LinkCommand {
  /** First thing a paired phone says, to prove it has been let in before. */
  data class Hello(val token: String, val name: String) : LinkCommand

  /** First thing an unpaired phone says, carrying the code being shown on the television. */
  data class Pair(val code: String, val name: String) : LinkCommand

  /**
   * Handing a title over to the television.
   *
   * Carries what the television needs to show it, because the title being sent is usually one the
   * television has never seen: it was found, and watched, on the phone. The position comes with it
   * so the picture picks up where the phone left it rather than at the beginning.
   */
  data class Play(
    val pageUrl: String,
    val title: String,
    val subtitle: String?,
    val posterUrl: String?,
    val positionMs: Long,
  ) : LinkCommand

  data object Pause : LinkCommand

  data object Resume : LinkCommand

  data object Stop : LinkCommand

  data class Seek(val positionMs: Long) : LinkCommand

  /** Jumping by a relative amount, which is what a skip button on the phone sends. */
  data class SeekBy(val deltaMs: Long) : LinkCommand

  data class Volume(val delta: Int) : LinkCommand

  /** Setting the level outright, which is what dragging the phone's own slider sends. */
  data class SetVolume(val percent: Int) : LinkCommand

  /** A press of the remote's own pad, for driving the parts of the app that are not the player. */
  data class Key(val key: LinkKey) : LinkCommand

  /** Typing, so a search box on a television is filled from a real keyboard. */
  data class Text(val text: String) : LinkCommand

  /** Picking a subtitle track, an audio track, a quality or a speed from the phone. */
  data class SelectOption(val groupId: String, val itemId: String) : LinkCommand

  /** Shifting the subtitles earlier or later without leaving the sofa. */
  data class SubtitleSync(val deltaMs: Long) : LinkCommand
}

internal enum class LinkKey {
  UP,
  DOWN,
  LEFT,
  RIGHT,
  CENTER,
  BACK,

  /** Rubbing out the last letter, since a search typed from a phone is usually typed wrong once. */
  BACKSPACE,
}

internal sealed interface LinkEvent {
  /** The television does not know this phone; it is showing a code for the viewer to type in. */
  data object PairingRequired : LinkEvent

  data class Paired(val token: String) : LinkEvent

  data class Refused(val reason: String) : LinkEvent

  /**
   * The player's settings as they stand.
   *
   * Sent apart from the state and only when it changes: the tracks in a film do not move while the
   * position does, and repeating the whole list every second would be all the traffic there is.
   */
  data class Options(val groups: List<RemoteOptionGroup>, val subtitleOffsetMs: Long) : LinkEvent

  /** What the television is doing now, sent on every change and while playing. */
  data class State(
    val playing: Boolean,
    /** The catalog page behind what is playing, which is what a phone needs to take it back. */
    val pageUrl: String?,
    val title: String?,
    val subtitle: String?,
    val posterUrl: String?,
    val positionMs: Long,
    val durationMs: Long,
    /** Where the television's own volume sits, 0..100, so the phone's slider tells the truth. */
    val volume: Int = 0,
  ) : LinkEvent
}

internal fun LinkCommand.encode(): String {
  val json = JSONObject().put("v", LINK_PROTOCOL_VERSION)
  when (this) {
    is LinkCommand.Hello -> json.put("cmd", "hello").put("token", token).put("name", name)
    is LinkCommand.Pair -> json.put("cmd", "pair").put("code", code).put("name", name)
    is LinkCommand.Play ->
      json
        .put("cmd", "play")
        .put("pageUrl", pageUrl)
        .put("title", title)
        .put("subtitle", subtitle ?: JSONObject.NULL)
        .put("posterUrl", posterUrl ?: JSONObject.NULL)
        .put("positionMs", positionMs)
    LinkCommand.Pause -> json.put("cmd", "pause")
    LinkCommand.Resume -> json.put("cmd", "resume")
    LinkCommand.Stop -> json.put("cmd", "stop")
    is LinkCommand.Seek -> json.put("cmd", "seek").put("positionMs", positionMs)
    is LinkCommand.SeekBy -> json.put("cmd", "seekBy").put("deltaMs", deltaMs)
    is LinkCommand.Volume -> json.put("cmd", "volume").put("delta", delta)
    is LinkCommand.SetVolume -> json.put("cmd", "setVolume").put("percent", percent)
    is LinkCommand.Key -> json.put("cmd", "key").put("key", key.name.lowercase())
    is LinkCommand.Text -> json.put("cmd", "text").put("text", text)
    is LinkCommand.SelectOption ->
      json.put("cmd", "selectOption").put("groupId", groupId).put("itemId", itemId)
    is LinkCommand.SubtitleSync -> json.put("cmd", "subtitleSync").put("deltaMs", deltaMs)
  }
  return json.toString()
}

/** Anything unrecognised decodes to null: a newer phone must not be able to crash a television. */
internal fun decodeCommand(line: String): LinkCommand? =
  runCatching {
      val json = JSONObject(line)
      if (json.optInt("v") != LINK_PROTOCOL_VERSION) return@runCatching null
      when (json.optString("cmd")) {
        "hello" ->
          LinkCommand.Hello(
            token = json.optNonBlank("token") ?: return@runCatching null,
            name = json.optString("name").ifBlank { "A phone" },
          )
        "pair" ->
          LinkCommand.Pair(
            code = json.optNonBlank("code") ?: return@runCatching null,
            name = json.optString("name").ifBlank { "A phone" },
          )
        "play" -> {
          val pageUrl = json.optNonBlank("pageUrl") ?: return@runCatching null
          LinkCommand.Play(
            pageUrl = pageUrl,
            title = json.optNonBlank("title") ?: pageUrl,
            subtitle = json.optNonBlank("subtitle"),
            posterUrl = json.optNonBlank("posterUrl"),
            positionMs = json.optLong("positionMs").coerceAtLeast(0L),
          )
        }
        "pause" -> LinkCommand.Pause
        "resume" -> LinkCommand.Resume
        "stop" -> LinkCommand.Stop
        "seek" -> LinkCommand.Seek(positionMs = json.optLong("positionMs").coerceAtLeast(0L))
        "seekBy" -> LinkCommand.SeekBy(deltaMs = json.optLong("deltaMs"))
        "volume" -> LinkCommand.Volume(delta = json.optInt("delta").coerceIn(-10, 10))
        "setVolume" -> LinkCommand.SetVolume(percent = json.optInt("percent").coerceIn(0, 100))
        "key" ->
          LinkKey.entries
            .firstOrNull { it.name.equals(json.optString("key"), ignoreCase = true) }
            ?.let(LinkCommand::Key)
        "text" -> LinkCommand.Text(text = json.optString("text").take(MAX_TEXT_LENGTH))
        "selectOption" ->
          LinkCommand.SelectOption(
            groupId = json.optNonBlank("groupId") ?: return@runCatching null,
            itemId = json.optNonBlank("itemId") ?: return@runCatching null,
          )
        "subtitleSync" ->
          LinkCommand.SubtitleSync(deltaMs = json.optLong("deltaMs").coerceIn(-MAX_SYNC_MS, MAX_SYNC_MS))
        else -> null
      }
    }
    .getOrNull()

internal fun LinkEvent.encode(): String {
  val json = JSONObject().put("v", LINK_PROTOCOL_VERSION)
  when (this) {
    LinkEvent.PairingRequired -> json.put("evt", "needPair")
    is LinkEvent.Paired -> json.put("evt", "paired").put("token", token)
    is LinkEvent.Refused -> json.put("evt", "refused").put("reason", reason)
    is LinkEvent.Options ->
      json
        .put("evt", "options")
        .put("subtitleOffsetMs", subtitleOffsetMs)
        .put(
          "groups",
          JSONArray().apply {
            groups.forEach { group ->
              put(
                JSONObject()
                  .put("id", group.id)
                  .put("label", group.label)
                  .put(
                    "items",
                    JSONArray().apply {
                      group.items.forEach { item ->
                        put(
                          JSONObject()
                            .put("id", item.id)
                            .put("label", item.label)
                            .put("selected", item.selected)
                        )
                      }
                    },
                  )
              )
            }
          },
        )
    is LinkEvent.State ->
      json
        .put("evt", "state")
        .put("playing", playing)
        .put("pageUrl", pageUrl ?: JSONObject.NULL)
        .put("title", title ?: JSONObject.NULL)
        .put("subtitle", subtitle ?: JSONObject.NULL)
        .put("posterUrl", posterUrl ?: JSONObject.NULL)
        .put("positionMs", positionMs)
        .put("durationMs", durationMs)
        .put("volume", volume)
  }
  return json.toString()
}

internal fun decodeEvent(line: String): LinkEvent? =
  runCatching {
      val json = JSONObject(line)
      if (json.optInt("v") != LINK_PROTOCOL_VERSION) return@runCatching null
      when (json.optString("evt")) {
        "needPair" -> LinkEvent.PairingRequired
        "paired" -> LinkEvent.Paired(token = json.optNonBlank("token") ?: return@runCatching null)
        "refused" -> LinkEvent.Refused(reason = json.optString("reason").ifBlank { "Refused" })
        "options" -> {
          val groups = json.optJSONArray("groups") ?: JSONArray()
          LinkEvent.Options(
            groups =
              (0 until groups.length()).mapNotNull { index ->
                val group = groups.optJSONObject(index) ?: return@mapNotNull null
                val items = group.optJSONArray("items") ?: JSONArray()
                RemoteOptionGroup(
                  id = group.optString("id"),
                  label = group.optString("label"),
                  items =
                    (0 until items.length()).mapNotNull { itemIndex ->
                      val item = items.optJSONObject(itemIndex) ?: return@mapNotNull null
                      RemoteOptionItem(
                        id = item.optString("id"),
                        label = item.optString("label"),
                        selected = item.optBoolean("selected"),
                      )
                    },
                )
              },
            subtitleOffsetMs = json.optLong("subtitleOffsetMs"),
          )
        }
        "state" ->
          LinkEvent.State(
            playing = json.optBoolean("playing"),
            pageUrl = json.optNonBlank("pageUrl"),
            title = json.optNonBlank("title"),
            subtitle = json.optNonBlank("subtitle"),
            posterUrl = json.optNonBlank("posterUrl"),
            positionMs = json.optLong("positionMs"),
            durationMs = json.optLong("durationMs"),
            volume = json.optInt("volume").coerceIn(0, 100),
          )
        else -> null
      }
    }
    .getOrNull()

/** A search box is not a document; a phone that sends a novel is not obliged to be listened to. */
private const val MAX_TEXT_LENGTH = 256

/** Subtitles that are a minute out are not out of sync; they belong to a different film. */
private const val MAX_SYNC_MS = 60_000L

private fun JSONObject.optNonBlank(name: String): String? =
  if (isNull(name)) null else optString(name).takeIf { it.isNotBlank() }
