package android.media

open class AudioManager {
    open fun adjustStreamVolume(streamType: Int, direction: Int, flags: Int) {}

    companion object {
        const val STREAM_MUSIC = 3
        const val ADJUST_RAISE = 1
        const val ADJUST_LOWER = -1
        const val FLAG_SHOW_UI = 1
    }
}
