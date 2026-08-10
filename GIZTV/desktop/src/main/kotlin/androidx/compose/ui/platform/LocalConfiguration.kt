package androidx.compose.ui.platform

import androidx.compose.runtime.staticCompositionLocalOf

class Configuration {
    val screenWidthDp: Int = 1280
    val screenHeightDp: Int = 720
    val orientation: Int = 2 // ORIENTATION_LANDSCAPE
}

val LocalConfiguration = staticCompositionLocalOf<Configuration> { Configuration() }
