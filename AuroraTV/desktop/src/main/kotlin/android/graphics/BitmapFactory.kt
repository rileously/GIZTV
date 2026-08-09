package android.graphics

import androidx.compose.ui.graphics.ImageBitmap
import androidx.compose.ui.graphics.toComposeImageBitmap
import org.jetbrains.skia.Image
import java.io.InputStream

class Bitmap(val skiaImage: Image)

fun Bitmap.asImageBitmap(): ImageBitmap = skiaImage.toComposeImageBitmap()

object BitmapFactory {
    @JvmStatic
    fun decodeStream(stream: InputStream): Bitmap? {
        val bytes = runCatching { stream.readBytes() }.getOrNull() ?: return null
        if (bytes.isEmpty()) return null
        val skia = runCatching { Image.makeFromEncoded(bytes) }.getOrNull() ?: return null
        return Bitmap(skia)
    }
}
