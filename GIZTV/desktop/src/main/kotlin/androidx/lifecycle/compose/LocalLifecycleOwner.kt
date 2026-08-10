package androidx.lifecycle.compose

import androidx.compose.runtime.staticCompositionLocalOf

class Lifecycle {
    enum class Event { ON_PAUSE, ON_RESUME, ON_STOP, ON_START, ON_DESTROY }
    fun addObserver(observer: Any) {}
    fun removeObserver(observer: Any) {}
}

interface LifecycleOwner {
    val lifecycle: Lifecycle get() = Lifecycle()
}

object DesktopLifecycleOwner : LifecycleOwner

val LocalLifecycleOwner = staticCompositionLocalOf<LifecycleOwner> { DesktopLifecycleOwner }
