package com.giztv.tv.ui.player

/**
 * Subtitle files arrive with advertising in them.
 *
 * Whoever produced the track puts a line of their own at the front, or scattered through it, and it
 * is rendered over the picture exactly like dialogue. It is not dialogue, and the viewer did not
 * ask for it.
 *
 * The filter is deliberately narrow. Dropping a line of real dialogue is worse than leaving an
 * advert on screen, so a plain web address is not enough on its own — a film can name a website —
 * and neither is a promotional-sounding word. It takes either a naked link, a known subtitle
 * house's name, or an address alongside something that is plainly selling it.
 */
private val SUBTITLE_AD_HOUSES =
  listOf(
    "opensubtitles",
    "addic7ed",
    "subscene",
    "podnapisi",
    "yifysubtitles",
    "subtitles by",
    "subtitle by",
    "subs by",
    "sync by",
    "synced by",
    "resync by",
    "corrected by",
    "advertise your product",
    "support us and become",
    "become a vip member",
  )

/**
 * Enough of a domain to be an address, not enough to catch a sentence that ran into a full stop.
 *
 * The three-character minimum is what keeps "Let's go.To the car" from reading as a .to address.
 */
private val SUBTITLE_AD_DOMAIN =
  Regex("""\b[a-z0-9][a-z0-9-]{2,}\.(com|net|org|ru|to|site|tv|me|io|pro|xyz|cc|vc|app|online|live|stream|info|biz)\b""")

/**
 * Hosts too short for the rule above to reach.
 *
 * The trailing slash is what makes them unambiguous: "t.me/" is a link, "me" on its own is a word.
 */
private val SUBTITLE_AD_SHORT_LINKS = listOf("t.me/", "bit.ly/", "goo.gl/", "is.gd/", "cutt.ly/")

/** What turns a mentioned address into an advertised one. */
private val SUBTITLE_AD_INTENT =
  listOf(
    "visit",
    "watch",
    "download",
    "stream",
    "free",
    "subtitle",
    "join",
    "support",
    "donate",
    "advertise",
    "vip",
    "telegram",
    "channel",
    "premium",
  )

/**
 * Compiled once, not per cue.
 *
 * Both braces are escaped deliberately. Android's regex engine rejects a bare closing brace where
 * the JVM accepts one, so the unescaped form passes every unit test and then crashes the player on
 * the first subtitle it sees.
 */
private val SUBTITLE_MARKUP = Regex("<[^>]*>")
private val SUBTITLE_STYLE_BLOCK = Regex("\\{\\\\[^\\}]*\\}")
private val REPEATED_WHITESPACE = Regex("\\s+")

internal fun isPromotionalSubtitleCue(text: CharSequence?): Boolean {
  val normalized =
    text
      ?.toString()
      ?.replace(SUBTITLE_MARKUP, " ")
      ?.replace(SUBTITLE_STYLE_BLOCK, " ")
      ?.lowercase()
      ?.replace(REPEATED_WHITESPACE, " ")
      ?.trim()
      .orEmpty()
  if (normalized.isEmpty()) return false
  if (SUBTITLE_AD_HOUSES.any(normalized::contains)) return true
  // A naked link is never dialogue.
  if (normalized.contains("http://") || normalized.contains("https://") || normalized.contains("www.")) {
    return true
  }
  if (SUBTITLE_AD_SHORT_LINKS.any(normalized::contains)) return true
  return SUBTITLE_AD_DOMAIN.containsMatchIn(normalized) &&
    SUBTITLE_AD_INTENT.any(normalized::contains)
}
