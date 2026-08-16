package com.giztv.tv

import com.giztv.tv.link.pairingUri
import com.giztv.tv.link.parsePairingUri
import com.giztv.tv.ui.link.qrModules
import org.junit.Assert.assertEquals
import org.junit.Assert.assertNotNull
import org.junit.Assert.assertNull
import org.junit.Assert.assertTrue
import org.junit.Test

class PairingLinkTest {
  @Test
  fun whatTheTelevisionDrawsIsWhatThePhoneReadsBack() {
    val uri = pairingUri(address = "192.168.1.20:8421", code = "418209")

    assertNotNull(uri)
    val request = parsePairingUri(uri)

    assertNotNull(request)
    assertEquals("192.168.1.20", request!!.host)
    assertEquals(8421, request.port)
    assertEquals("418209", request.code)
  }

  @Test
  fun nothingIsDrawnBeforeTheServerHasAnAddress() {
    assertNull(pairingUri(address = null, code = "418209"))
    assertNull(pairingUri(address = "", code = "418209"))
    assertNull(pairingUri(address = "192.168.1.20:8421", code = ""))
  }

  @Test
  fun anAddressWithoutAUsablePortIsNotWorthDrawing() {
    assertNull(pairingUri(address = "192.168.1.20", code = "418209"))
    assertNull(pairingUri(address = "192.168.1.20:not-a-port", code = "418209"))
    assertNull(pairingUri(address = "192.168.1.20:99999", code = "418209"))
  }

  @Test
  fun anythingOtherThanOurOwnCodeIsRefused() {
    // This arrives from outside the app: a camera pointed at whatever happened to be on a screen.
    assertNull(parsePairingUri(null))
    assertNull(parsePairingUri("https://example.com/pair?h=10.0.0.1&p=8421&c=418209"))
    assertNull(parsePairingUri("giztv://play?h=10.0.0.1&p=8421&c=418209"))
    assertNull(parsePairingUri("giztv://pair?p=8421&c=418209"))
    assertNull(parsePairingUri("giztv://pair?h=10.0.0.1&c=418209"))
    assertNull(parsePairingUri("giztv://pair?h=10.0.0.1&p=8421"))
  }

  @Test
  fun aCodeThatIsNotSixDigitsIsRefused() {
    assertNull(parsePairingUri("giztv://pair?h=10.0.0.1&p=8421&c=4182"))
    assertNull(parsePairingUri("giztv://pair?h=10.0.0.1&p=8421&c=41820a"))
    assertNull(parsePairingUri("giztv://pair?h=10.0.0.1&p=8421&c=4182099"))
  }

  @Test
  fun aPortOutsideTheRangeIsRefused() {
    assertNull(parsePairingUri("giztv://pair?h=10.0.0.1&p=0&c=418209"))
    assertNull(parsePairingUri("giztv://pair?h=10.0.0.1&p=70000&c=418209"))
  }

  @Test
  fun aScannerReadingTheSquareGetsBackTheAddressItWasGiven() {
    // The optical half needs a camera and a television; this is the half that can be proved here —
    // what was drawn decodes to exactly what pairing needs, rather than merely being square.
    val uri = requireNotNull(pairingUri(address = "192.168.1.20:8421", code = "418209"))
    val modules = requireNotNull(qrModules(uri))

    // The decoder wants the symbol alone, so the quiet border drawn around it comes off first.
    val margin = modules.indexOfFirst { row -> row.any { it } }
    val side = modules.size - margin * 2
    val symbol = com.google.zxing.common.BitMatrix(side, side)
    for (y in 0 until side) {
      for (x in 0 until side) {
        if (modules[y + margin][x + margin]) symbol.set(x, y)
      }
    }
    val decoded = com.google.zxing.qrcode.decoder.Decoder().decode(symbol)

    assertEquals(uri, decoded.text)
    assertEquals(parsePairingUri(uri), parsePairingUri(decoded.text))
  }

  @Test
  fun theSquareIsActuallyDrawable() {
    val modules = qrModules("giztv://pair?h=192.168.1.20&p=8421&c=418209")

    assertNotNull(modules)
    assertTrue(modules!!.size >= 21)
    assertEquals(modules.size, modules.first().size)

    // The fixed square every scanner hunts for in the corner: seven dark, five light, seven dark
    // down its left edge. Found relative to the quiet border rather than assuming where it starts.
    val top = modules.indexOfFirst { row -> row.any { it } }
    val left = modules[top].indexOfFirst { it }
    assertTrue(top > 0 && left > 0)
    assertTrue(modules[top].subList(left, left + 7).all { it })
    assertTrue(modules[top + 6].subList(left, left + 7).all { it })
    assertTrue((top until top + 7).all { modules[it][left] })
    assertTrue(modules[top + 1].subList(left + 1, left + 6).none { it })
  }
}
