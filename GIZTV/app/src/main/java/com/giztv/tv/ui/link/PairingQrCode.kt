package com.giztv.tv.ui.link

import androidx.compose.foundation.Canvas
import androidx.compose.foundation.background
import androidx.compose.foundation.layout.Box
import androidx.compose.foundation.layout.padding
import androidx.compose.foundation.layout.size
import androidx.compose.foundation.shape.RoundedCornerShape
import androidx.compose.runtime.Composable
import androidx.compose.runtime.remember
import androidx.compose.ui.Modifier
import androidx.compose.ui.geometry.Offset
import androidx.compose.ui.geometry.Size
import androidx.compose.ui.graphics.Color
import androidx.compose.ui.unit.Dp
import androidx.compose.ui.unit.dp
import com.google.zxing.BarcodeFormat
import com.google.zxing.EncodeHintType
import com.google.zxing.qrcode.QRCodeWriter
import com.google.zxing.qrcode.decoder.ErrorCorrectionLevel

/**
 * The pairing details as a square a phone's camera can read.
 *
 * Drawn rather than photographed: the modules are laid out as rectangles on a canvas at whatever
 * size the television gives them, so the edges stay hard at any distance across the room. A bitmap
 * scaled up to a ten-foot screen would not.
 *
 * Black on white regardless of the app's own palette, because that is what a camera expects and a
 * tinted code is a code that takes three attempts to read. The quiet border around it is part of
 * the specification, not padding: without it the scanner cannot find the edges.
 */
@Composable
internal fun PairingQrCode(content: String, modifier: Modifier = Modifier, size: Dp = 168.dp) {
  val modules = remember(content) { qrModules(content) } ?: return
  Box(
    modifier = modifier.background(Color.White, RoundedCornerShape(10.dp)).padding(10.dp),
  ) {
    Canvas(modifier = Modifier.size(size)) {
      val side = this.size.minDimension / modules.size
      modules.forEachIndexed { y, row ->
        row.forEachIndexed { x, dark ->
          if (dark) {
            drawRect(
              color = Color.Black,
              topLeft = Offset(x * side, y * side),
              // A whisker over one module, so neighbours meet rather than leave a seam a scanner
              // has to guess across.
              size = Size(side + 0.5f, side + 0.5f),
            )
          }
        }
      }
    }
  }
}

/**
 * The code as rows of dark and light, or null if it cannot be made.
 *
 * Encoding can refuse — content too long for the chosen correction level, most often — and a
 * television that cannot draw the square should simply show the digits, which it does anyway.
 */
internal fun qrModules(content: String): List<List<Boolean>>? =
  runCatching {
      val hints =
        mapOf(
          // A screen is a clean surface at a fixed distance; the lowest correction that survives it
          // keeps the modules large, which is what actually matters across a room.
          EncodeHintType.ERROR_CORRECTION to ErrorCorrectionLevel.L,
          EncodeHintType.MARGIN to 1,
          EncodeHintType.CHARACTER_SET to "UTF-8",
        )
      val matrix = QRCodeWriter().encode(content, BarcodeFormat.QR_CODE, 0, 0, hints)
      (0 until matrix.height).map { y -> (0 until matrix.width).map { x -> matrix.get(x, y) } }
    }
    .onFailure { android.util.Log.w("GizTvLink", "Pairing code could not be drawn", it) }
    .getOrNull()
