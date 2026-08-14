package android.os

object Build {
    const val MODEL = "Windows Desktop"
    const val MANUFACTURER = "PC"
    object VERSION {
        const val SDK_INT = 34
        val RELEASE: String = System.getProperty("os.version") ?: "unknown"
    }

    /** The levels the shared code gates on. SDK_INT above sits past all of them. */
    object VERSION_CODES {
        const val N = 24
        const val O = 26
        const val P = 28
        const val Q = 29
        const val S = 31
        const val TIRAMISU = 33
    }
}
