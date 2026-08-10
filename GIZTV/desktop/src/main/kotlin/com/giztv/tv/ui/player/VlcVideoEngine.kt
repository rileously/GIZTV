package com.giztv.tv.ui.player

import androidx.compose.ui.graphics.ImageBitmap
import androidx.compose.ui.graphics.asComposeImageBitmap
import java.nio.ByteBuffer
import org.jetbrains.skia.Bitmap
import org.jetbrains.skia.ColorAlphaType
import org.jetbrains.skia.ColorType
import org.jetbrains.skia.ImageInfo
import uk.co.caprica.vlcj.factory.MediaPlayerFactory
import uk.co.caprica.vlcj.player.base.MediaPlayer
import uk.co.caprica.vlcj.player.embedded.EmbeddedMediaPlayer
import uk.co.caprica.vlcj.player.embedded.videosurface.callback.BufferFormat
import uk.co.caprica.vlcj.player.embedded.videosurface.callback.BufferFormatCallback
import uk.co.caprica.vlcj.player.embedded.videosurface.callback.RenderCallback
import uk.co.caprica.vlcj.player.embedded.videosurface.callback.format.RV32BufferFormat

/**
 * Why the picture is copied into Compose rather than handed to a Swing panel.
 *
 * vlcj will happily draw straight into a heavyweight AWT component, and embedding one of those is
 * the shorter road. It is also a dead end here: a heavyweight component paints over the Compose
 * canvas rather than inside it, so every control this player draws — the seek bar, the transport
 * row, the title — would sit behind the video instead of on top of it. Taking the frames as pixels
 * and drawing them ourselves keeps the whole screen in one rendering world, and overlays behave.
 *
 * The cost is one copy per frame out of the native buffer. At film frame rates that is a few
 * hundred megabytes a second of plain memcpy, which is nothing, and both the byte array and the
 * bitmap are reused so it produces no garbage.
 */
internal class VlcVideoEngine {

  /** The most recent frame, or null before the first one arrives. */
  @Volatile private var frame: ImageBitmap? = null
    private set

  /** Bumped on every frame, so a reader can tell a repaint is due. */
  @Volatile var frameCount: Long = 0L
    private set

  @Volatile private var pixels: ByteArray = ByteArray(0)
  @Volatile private var bitmap: Bitmap? = null
  @Volatile private var imageInfo: ImageInfo? = null
  private val frameLock = Any()

  private var factory: MediaPlayerFactory? = null
  private var player: EmbeddedMediaPlayer? = null

  /**
   * What went wrong setting the engine up, if anything.
   *
   * Almost always a missing libVLC: vlcj is a binding, not a decoder, so without VLC installed
   * there is nothing behind it. Kept as a message rather than thrown, because the player screen can
   * say so far more usefully than a crash can.
   */
  var startupError: String? = null
    private set

  val isAvailable: Boolean get() = player != null

  fun start(url: String, headers: Map<String, String>) {
    val created =
      runCatching {
          val mediaPlayerFactory = MediaPlayerFactory()
          val embedded = mediaPlayerFactory.mediaPlayers().newEmbeddedMediaPlayer()
          embedded.videoSurface().set(
            mediaPlayerFactory.videoSurfaces().newVideoSurface(
              SurfaceFormat(),
              FrameSink(),
              // Locked buffers: the frame is not overwritten while it is being copied out.
              true,
            )
          )
          mediaPlayerFactory to embedded
        }
        .getOrElse { error ->
          startupError = describeStartupFailure(error)
          return
        }
    factory = created.first
    player = created.second
    // These addresses are refused without the headers the page would have sent, and VLC drops them
    // for everything its adaptive demuxer fetches after the first request — so the whole stream is
    // routed through a local proxy that puts them back on every one. See StreamHeaderProxy.
    val options = mediaOptionsFor(headers)
    player?.media()?.play(StreamHeaderProxy.proxied(url, headers), *options.toTypedArray())
  }

  fun play() {
    player?.controls()?.play()
  }

  fun pause() {
    player?.controls()?.pause()
  }

  fun setPlaying(playing: Boolean) {
    if (playing) play() else pause()
  }

  /** Where playback is now, in milliseconds; negative until the stream reports a position. */
  fun timeMs(): Long = player?.status()?.time() ?: -1L

  /** How long the stream is, or a non-positive number while it is still being worked out. */
  fun durationMs(): Long = player?.status()?.length() ?: -1L

  fun isPlaying(): Boolean = player?.status()?.isPlaying == true

  fun seekTo(positionMs: Long) {
    val player = player ?: return
    val duration = durationMs()
    val target = if (duration > 0L) positionMs.coerceIn(0L, duration) else positionMs.coerceAtLeast(0L)
    player.controls().setTime(target)
  }

  fun skip(deltaMs: Long) {
    val now = timeMs()
    if (now < 0L) return
    seekTo(now + deltaMs)
  }

  /**
   * Draw access to the current frame.
   *
   * Compose converts the backing bitmap to a Skia image during [block]. VLC replaces that same
   * bitmap's pixels from its callback thread, so those operations must be mutually exclusive. A
   * race here used to surface as `Failed to Image.makeFromBitmap` as soon as video arrived.
   */
  fun withFrame(block: (ImageBitmap) -> Unit): Boolean =
    synchronized(frameLock) {
      val current = frame ?: return@synchronized false
      block(current)
      true
    }

  /** 0f..1f, mapped onto the 0..100 libVLC works in. */
  fun setVolume(level: Float) {
    player?.audio()?.setVolume((level.coerceIn(0f, 1f) * 100f).toInt())
  }

  fun release() {
    runCatching { player?.controls()?.stop() }
    runCatching { player?.release() }
    runCatching { factory?.release() }
    player = null
    factory = null
    synchronized(frameLock) {
      frame = null
      bitmap = null
      imageInfo = null
      pixels = ByteArray(0)
    }
  }

  /**
   * Tells libVLC what shape of pixels to hand over, and sizes the buffers to match.
   *
   * RV32 is 32-bit little-endian BGRA, which is what Skia calls BGRA_8888 — so the bytes go in
   * without any per-pixel work on our side.
   */
  private inner class SurfaceFormat : BufferFormatCallback {
    override fun getBufferFormat(sourceWidth: Int, sourceHeight: Int): BufferFormat {
      val info =
        ImageInfo(sourceWidth, sourceHeight, ColorType.BGRA_8888, ColorAlphaType.PREMUL)
      synchronized(frameLock) {
        imageInfo = info
        pixels = ByteArray(sourceWidth * sourceHeight * BYTES_PER_PIXEL)
        bitmap = Bitmap().apply { allocPixels(info) }
        frame = bitmap?.asComposeImageBitmap()
      }
      return RV32BufferFormat(sourceWidth, sourceHeight)
    }

    override fun allocatedBuffers(buffers: Array<ByteBuffer>) = Unit
  }

  /** Called by libVLC on its own thread for every frame it has finished decoding. */
  private inner class FrameSink : RenderCallback {
    override fun display(
      mediaPlayer: MediaPlayer,
      nativeBuffers: Array<ByteBuffer>,
      bufferFormat: BufferFormat,
    ) {
      synchronized(frameLock) {
        val info = imageInfo ?: return
        val target = bitmap ?: return
        val buffer = nativeBuffers.firstOrNull() ?: return
        val scratch = pixels
        if (scratch.size < info.width * info.height * BYTES_PER_PIXEL) return
        buffer.rewind()
        buffer.get(scratch, 0, scratch.size)
        if (!target.installPixels(info, scratch, info.width * BYTES_PER_PIXEL)) return
        target.notifyPixelsChanged()
        frameCount++
      }
    }
  }
}

private const val BYTES_PER_PIXEL = 4

/**
 * The request headers libVLC understands, in the form it wants them.
 *
 * Only referer and user agent have options of their own. A cookie cannot be passed this way, so a
 * source that signs on one will refuse the stream however well the address was resolved.
 */
internal fun mediaOptionsFor(headers: Map<String, String>): List<String> = buildList {
  // Starting at the lowest HLS rendition avoids downloading a multi-megabyte 1080p fragment before
  // the first frame. VLC may promote later; the important part is that playback becomes visible.
  add(":adaptive-logic=lowest")
  add(":network-caching=3000")
  headers.entries
    .firstOrNull { it.key.equals("referer", ignoreCase = true) }
    ?.let { add(":http-referrer=${it.value}") }
  headers.entries
    .firstOrNull { it.key.equals("user-agent", ignoreCase = true) }
    ?.let { add(":http-user-agent=${it.value}") }
}

private fun describeStartupFailure(error: Throwable): String {
  val detail = error.message?.takeIf(String::isNotBlank)
  return buildString {
    append("VLC could not be started on this computer.")
    append("\n\nThis player uses VLC to decode video. Install the 64-bit VLC for Windows and open ")
    append("the app again.")
    if (detail != null) append("\n\n$detail")
  }
}

/** `h:mm:ss` once a film is an hour long, `m:ss` before that; a dash while nothing is known. */
internal fun formatPlaybackTime(millis: Long): String {
  if (millis < 0L) return "--:--"
  val totalSeconds = millis / 1000L
  val hours = totalSeconds / 3600L
  val minutes = (totalSeconds % 3600L) / 60L
  val seconds = totalSeconds % 60L
  return if (hours > 0L) {
    "%d:%02d:%02d".format(hours, minutes, seconds)
  } else {
    "%d:%02d".format(minutes, seconds)
  }
}
