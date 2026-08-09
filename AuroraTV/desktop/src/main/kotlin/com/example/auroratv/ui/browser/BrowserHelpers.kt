package com.example.auroratv.ui.browser

internal fun streamMimeType(url: String, headers: Map<String, String> = emptyMap()): String? {
    val path = url.substringBefore('?').lowercase()
    return when {
        path.endsWith(".m3u8") -> "application/x-mpegURL"
        path.endsWith(".mpd") -> "application/dash+xml"
        path.endsWith(".mp4") -> "video/mp4"
        path.endsWith(".mkv") -> "video/x-matroska"
        else -> null
    }
}
