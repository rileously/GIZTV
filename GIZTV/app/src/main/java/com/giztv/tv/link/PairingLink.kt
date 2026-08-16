package com.giztv.tv.link

/** The address a phone's camera can hand straight to this app. */
internal const val PAIRING_URI_SCHEME = "giztv"
internal const val PAIRING_URI_HOST = "pair"

/** Everything a phone needs to let itself in, as carried by the code on the television. */
internal data class PairingRequest(
  val host: String,
  val port: Int,
  val code: String,
)

/**
 * What the television draws for a camera to read.
 *
 * It carries the address as well as the code, which matters more than the convenience: a phone
 * sharing its own connection with the television, or on a network that will not pass the
 * announcements televisions make about themselves, cannot find the thing it is being paired to. The
 * viewer's only way through was to read an address off the screen and type it in. A camera does not
 * mind how long the address is.
 *
 * [address] is what [LinkHost.address] reports — `host:port` — and null until the server is up.
 */
internal fun pairingUri(address: String?, code: String): String? {
  val trimmed = address?.trim().orEmpty()
  if (trimmed.isEmpty() || code.isBlank()) return null
  val host = trimmed.substringBeforeLast(':', "").takeIf { it.isNotBlank() } ?: return null
  val port = trimmed.substringAfterLast(':', "").toIntOrNull()?.takeIf { it in 1..65_535 } ?: return null
  return "$PAIRING_URI_SCHEME://$PAIRING_URI_HOST?h=$host&p=$port&c=$code"
}

/**
 * Reads back what [pairingUri] wrote, and refuses anything else.
 *
 * This arrives from outside the app — a camera, a browser, whatever the viewer pointed at the
 * screen — so nothing in it is trusted beyond its shape. A malformed one pairs with nothing.
 */
internal fun parsePairingUri(uri: String?): PairingRequest? {
  // Deliberately java.net rather than android.net: the same parser then runs in a plain unit test,
  // and what this has to be is correct about refusing things rather than convenient to write.
  val parsed = runCatching { java.net.URI(uri.orEmpty()) }.getOrNull() ?: return null
  if (!parsed.scheme.equals(PAIRING_URI_SCHEME, ignoreCase = true)) return null
  if (!parsed.host.equals(PAIRING_URI_HOST, ignoreCase = true)) return null
  val fields =
    parsed.query.orEmpty().split('&').mapNotNull { pair ->
      val name = pair.substringBefore('=', "")
      val value = pair.substringAfter('=', "")
      if (name.isBlank()) null else name to value
    }.toMap()
  val host = fields["h"]?.trim().orEmpty()
  val port = fields["p"]?.toIntOrNull()
  val code = fields["c"]?.trim().orEmpty()
  if (host.isEmpty() || host.any { it.isWhitespace() || it == '/' }) return null
  if (port == null || port !in 1..65_535) return null
  if (code.length != PAIRING_CODE_LENGTH || !code.all(Char::isDigit)) return null
  return PairingRequest(host = host, port = port, code = code)
}
