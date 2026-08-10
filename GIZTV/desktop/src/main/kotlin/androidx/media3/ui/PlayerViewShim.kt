package androidx.media3.ui

import android.content.Context
import androidx.media3.common.Player

class AspectRatioFrameLayout {
    companion object {
        const val RESIZE_MODE_FIT = 0
        const val RESIZE_MODE_FIXED_WIDTH = 1
        const val RESIZE_MODE_FIXED_HEIGHT = 2
        const val RESIZE_MODE_FILL = 3
        const val RESIZE_MODE_ZOOM = 4
    }
}

class CaptionStyleCompat {
    companion object {
        const val EDGE_TYPE_OUTLINE = 1
    }
}

class SubtitleView(context: Context) {
    fun setStyle(style: Any) {}
    fun setFixedTextSize(unit: Int, size: Float) {}
    fun setBottomPaddingFraction(fraction: Float) {}
    fun setCues(cues: List<Any>?) {}
}

class PlayerView(context: Context) {
    var player: Player? = null
    var useController: Boolean = false
    var resizeMode: Int = AspectRatioFrameLayout.RESIZE_MODE_FIT
    val subtitleView: SubtitleView? = SubtitleView(context)
}
