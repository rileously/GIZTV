package com.giztv.tv.ui.browser

import androidx.compose.foundation.background
import androidx.compose.foundation.clickable
import androidx.compose.foundation.layout.Arrangement
import androidx.compose.foundation.layout.Box
import androidx.compose.foundation.layout.Column
import androidx.compose.foundation.layout.ExperimentalLayoutApi
import androidx.compose.foundation.layout.FlowRow
import androidx.compose.foundation.layout.Row
import androidx.compose.foundation.layout.Spacer
import androidx.compose.foundation.layout.fillMaxSize
import androidx.compose.foundation.layout.fillMaxWidth
import androidx.compose.foundation.layout.height
import androidx.compose.foundation.layout.padding
import androidx.compose.foundation.layout.size
import androidx.compose.foundation.layout.width
import androidx.compose.foundation.rememberScrollState
import androidx.compose.foundation.shape.CircleShape
import androidx.compose.foundation.shape.RoundedCornerShape
import androidx.compose.foundation.verticalScroll
import androidx.compose.material.icons.Icons
import androidx.compose.material.icons.automirrored.filled.ArrowBack
import androidx.compose.material.icons.filled.OpenInBrowser
import androidx.compose.material.icons.filled.Refresh
import androidx.compose.material3.CircularProgressIndicator
import androidx.compose.material3.Icon
import androidx.compose.material3.Text
import androidx.compose.runtime.Composable
import androidx.compose.runtime.LaunchedEffect
import androidx.compose.runtime.getValue
import androidx.compose.runtime.mutableStateOf
import androidx.compose.runtime.remember
import androidx.compose.runtime.setValue
import androidx.compose.ui.Alignment
import androidx.compose.ui.Modifier
import androidx.compose.ui.draw.clip
import androidx.compose.ui.text.font.FontWeight
import androidx.compose.ui.text.style.TextAlign
import androidx.compose.ui.text.style.TextOverflow
import androidx.compose.ui.unit.dp
import androidx.compose.ui.unit.sp
import com.giztv.tv.data.PlaybackContext
import com.giztv.tv.theme.GizMint
import com.giztv.tv.theme.DeepSpace
import com.giztv.tv.theme.MutedBlue
import com.giztv.tv.theme.NightSurface
import com.giztv.tv.theme.SoftWhite
import com.giztv.tv.ui.catalog.CatalogTarget
import com.giztv.tv.ui.catalog.STREAM_PROVIDERS
import com.giztv.tv.ui.catalog.catalogTargetOf
import com.giztv.tv.ui.catalog.providerPageUrl
import com.giztv.tv.ui.catalog.serverLabel
import com.giztv.tv.ui.player.ExternalSubtitleTrack
import com.giztv.tv.ui.player.HlsStreamRequest
import com.giztv.tv.ui.player.isEnglishSubtitleLabel
import com.giztv.tv.ui.player.isHearingImpairedSubtitleLabel
import kotlinx.coroutines.Dispatchers
import kotlinx.coroutines.delay
import kotlinx.coroutines.ensureActive
import kotlinx.coroutines.withContext
import okhttp3.OkHttpClient
import okhttp3.Request
import okhttp3.HttpUrl.Companion.toHttpUrlOrNull
import org.json.JSONArray
import org.json.JSONObject
import org.json.JSONTokener
import java.util.concurrent.TimeUnit
import java.util.regex.Pattern

private const val UA =
    "Mozilla/5.0 (Windows NT 10.0; Win64; x64) AppleWebKit/537.36 (KHTML, like Gecko) Chrome/124.0.0.0 Safari/537.36"

private val http by lazy {
    OkHttpClient.Builder()
        .connectTimeout(12, TimeUnit.SECONDS)
        .readTimeout(12, TimeUnit.SECONDS)
        .followRedirects(true)
        .followSslRedirects(true)
        .build()
}

// ── Server list ───────────────────────────────────────────────────────────

// The sites, their addresses and their order all come from STREAM_PROVIDERS — the television
// build's list, compiled straight into this module. This file used to carry a list of its own,
// which drifted the moment the shared one changed: it still offered vidlink.pro months after that
// site was dropped, and never offered cinesrc at all.

// ── Shim for source compatibility ─────────────────────────────────────────
@Composable
internal fun StreamPrefetcher(
    target: PlaybackContext?,
    onResolved: (PlaybackContext, HlsStreamRequest) -> Unit,
) {}

// ── Main screen ───────────────────────────────────────────────────────────

@OptIn(ExperimentalLayoutApi::class)
@Composable
internal fun BrowserScreen(
    initialUrl: String,
    playback: PlaybackContext?,
    onUrlChanged: (String) -> Unit,
    onExit: () -> Unit,
    onStreamDetected: (HlsStreamRequest) -> Unit,
) {
    val target = remember(initialUrl, playback) {
        catalogTargetOf(initialUrl) ?: playback?.showId?.let { id ->
            CatalogTarget(
                tmdbId = id,
                seasonNumber = playback.seasonNumber,
                episodeNumber = playback.episodeNumber,
            )
        }
    }
    val isEpisode = target?.isEpisode == true
    val tmdbId = target?.tmdbId
    val season = target?.seasonNumber ?: 1
    val episode = target?.episodeNumber ?: 1

    // Start with the provider that usually resolves fastest. VidRock's disguised transport-stream
    // segments are unwrapped by StreamHeaderProxy, so every server in the shared picker is now a
    // valid desktop choice.
    var selectedProviderIndex by remember {
      mutableStateOf(STREAM_PROVIDERS.indexOfFirst { it.id == "cinesrc" }.coerceAtLeast(0))
    }
    var stageMessage by remember { mutableStateOf("Extracting stream…") }
    var errorMessage by remember { mutableStateOf<String?>(null) }
    var attemptKey by remember { mutableStateOf(0) }
    var attemptedProviderIndices by remember(target) { mutableStateOf(emptySet<Int>()) }

    LaunchedEffect(selectedProviderIndex, attemptKey) {
        if (tmdbId == null) { errorMessage = "Could not identify title."; return@LaunchedEffect }
        errorMessage = null
        val provider = STREAM_PROVIDERS[selectedProviderIndex.coerceIn(STREAM_PROVIDERS.indices)]
        val serverLabel = provider.label
        stageMessage = "Contacting $serverLabel…"

        withContext(Dispatchers.IO) {
            val result = extractStream(provider.id, tmdbId, isEpisode, season, episode) { msg ->
                stageMessage = msg
            }
            withContext(Dispatchers.Main) {
                if (result != null) {
                    stageMessage = "Stream found! Starting playback…"
                    onStreamDetected(result)
                } else {
                    val attempted = attemptedProviderIndices + selectedProviderIndex
                    attemptedProviderIndices = attempted
                    val nextProvider = nextDesktopProviderIndex(attempted)
                    if (nextProvider != null) {
                        selectedProviderIndex = nextProvider
                    } else {
                        errorMessage = "Couldn't extract this title from any server."
                    }
                }
            }
        }
    }

    Box(
        modifier = Modifier.fillMaxSize().background(DeepSpace),
    ) {
        // Back button
        Box(
            modifier = Modifier
                .padding(24.dp).size(44.dp)
                .clip(CircleShape).background(NightSurface)
                .clickable { onExit() },
            contentAlignment = Alignment.Center,
        ) {
            Icon(Icons.AutoMirrored.Filled.ArrowBack, "Back", tint = SoftWhite)
        }

        // Center card
        Column(
            modifier = Modifier
                .align(Alignment.Center)
                .width(520.dp)
                .clip(RoundedCornerShape(24.dp))
                .background(NightSurface)
                .padding(32.dp)
                .verticalScroll(rememberScrollState()),
            horizontalAlignment = Alignment.CenterHorizontally,
        ) {
            // Title
            if (playback?.title != null) {
                Text(
                    playback.title, color = SoftWhite, fontSize = 22.sp,
                    fontWeight = FontWeight.Bold, textAlign = TextAlign.Center,
                    maxLines = 2, overflow = TextOverflow.Ellipsis,
                )
                playback.subtitle?.let {
                    Text(it, color = MutedBlue, fontSize = 14.sp,
                        modifier = Modifier.padding(top = 4.dp))
                }
                Spacer(Modifier.height(24.dp))
            }

            if (errorMessage == null) {
                // Loading
                CircularProgressIndicator(color = GizMint, strokeWidth = 3.dp,
                    modifier = Modifier.size(52.dp))
                Spacer(Modifier.height(20.dp))
                Text(stageMessage, color = SoftWhite.copy(alpha = 0.9f), fontSize = 15.sp,
                    textAlign = TextAlign.Center)
            } else {
                // Error
                Text("Stream Not Found", color = SoftWhite, fontSize = 18.sp,
                    fontWeight = FontWeight.Bold)
                Text(errorMessage ?: "", color = MutedBlue, fontSize = 13.sp,
                    textAlign = TextAlign.Center, modifier = Modifier.padding(top = 8.dp))
                Spacer(Modifier.height(20.dp))

                // Open in browser
                val browserUrl = tmdbId?.let {
                    providerPageUrl(
                        CatalogTarget(it, if (isEpisode) season else null, if (isEpisode) episode else null),
                        selectedProviderIndex,
                    )
                } ?: initialUrl
                Box(
                    modifier = Modifier
                        .fillMaxWidth()
                        .clip(RoundedCornerShape(12.dp))
                        .background(NightSurface.copy(alpha = 0.6f))
                        .clickable {
                            runCatching {
                                if (java.awt.Desktop.isDesktopSupported())
                                    java.awt.Desktop.getDesktop().browse(java.net.URI.create(browserUrl))
                            }
                        }
                        .padding(vertical = 12.dp),
                    contentAlignment = Alignment.Center,
                ) {
                    Row(verticalAlignment = Alignment.CenterVertically) {
                        Icon(Icons.Default.OpenInBrowser, null, tint = GizMint,
                            modifier = Modifier.size(18.dp))
                        Spacer(Modifier.width(8.dp))
                        Text("Open in Browser", color = GizMint, fontSize = 14.sp,
                            fontWeight = FontWeight.SemiBold)
                    }
                }
                Spacer(Modifier.height(10.dp))

                // Retry
                Box(
                    modifier = Modifier
                        .fillMaxWidth()
                        .clip(RoundedCornerShape(12.dp))
                        .background(GizMint)
                        .clickable {
                            attemptedProviderIndices = emptySet()
                            errorMessage = null
                            attemptKey++
                        }
                        .padding(vertical = 14.dp),
                    contentAlignment = Alignment.Center,
                ) {
                    Row(verticalAlignment = Alignment.CenterVertically) {
                        Icon(Icons.Default.Refresh, null, tint = DeepSpace,
                            modifier = Modifier.size(20.dp))
                        Spacer(Modifier.width(8.dp))
                        Text("Retry", color = DeepSpace, fontSize = 15.sp,
                            fontWeight = FontWeight.Bold)
                    }
                }
            }

            // Server picker — always visible
            Spacer(Modifier.height(24.dp))
            Text("Select Server:", color = SoftWhite.copy(alpha = 0.7f), fontSize = 13.sp,
                fontWeight = FontWeight.Medium, modifier = Modifier.align(Alignment.Start))
            Spacer(Modifier.height(10.dp))
            FlowRow(
                modifier = Modifier.fillMaxWidth(),
                horizontalArrangement = Arrangement.spacedBy(8.dp),
                verticalArrangement = Arrangement.spacedBy(8.dp),
            ) {
                STREAM_PROVIDERS.forEachIndexed { index, server ->
                    val isSelected = selectedProviderIndex == index
                    Box(
                        modifier = Modifier
                            .clip(RoundedCornerShape(10.dp))
                            .background(if (isSelected) GizMint.copy(alpha = 0.2f) else DeepSpace)
                            .clickable {
                                attemptedProviderIndices = emptySet()
                                selectedProviderIndex = index
                                errorMessage = null
                                attemptKey++
                            }
                            .padding(horizontal = 14.dp, vertical = 10.dp),
                    ) {
                        Text(server.label,
                            color = if (isSelected) GizMint else SoftWhite,
                            fontSize = 13.sp, fontWeight = FontWeight.SemiBold)
                    }
                }
            }
        }
    }
}

// ── Extractor dispatcher ──────────────────────────────────────────────────

private val DESKTOP_PROVIDER_ORDER = listOf("cinesrc", "vidfast", "vidrock")

private fun nextDesktopProviderIndex(attempted: Set<Int>): Int? =
    DESKTOP_PROVIDER_ORDER.asSequence()
        .map { id -> STREAM_PROVIDERS.indexOfFirst { it.id == id } }
        .firstOrNull { it >= 0 && it !in attempted }

private suspend fun extractStream(
    serverId: String,
    tmdbId: Int,
    isEpisode: Boolean,
    season: Int,
    episode: Int,
    onStatus: suspend (String) -> Unit,
): HlsStreamRequest? {
    val target = CatalogTarget(tmdbId, if (isEpisode) season else null, if (isEpisode) episode else null)
    val index = STREAM_PROVIDERS.indexOfFirst { it.id == serverId }.takeIf { it >= 0 } ?: return null
    val pageUrl = providerPageUrl(target, index) ?: return null

    // Cheap first: some pages really do carry the address in their markup.
    // Do not take MP4s from the markup: these providers place advertising there before their
    // JavaScript asks for the actual title. The browser path below waits specifically for HLS.

    // Then the browser. All three of these sites assemble the address in JavaScript, so the fetch
    // above can never see it — this is the whole reason the television build drives a browser rather than an HTTP client.
    onStatus("Running browser to extract stream…")
    var media =
        ChromeStreamExtractor.extract(
            pageUrl,
            if (serverId == "vidfast") VIDFAST_PRIMARY_TIMEOUT_MS else WEBVIEW_TIMEOUT_MS,
            onStatus,
        )
    // Keep trying VidFast's real page first so recovery is automatic. Its published proxy is
    // currently offline and redirects media to a placeholder; use the now desktop-compatible
    // VidRock route as continuity until the primary service recovers.
    if (media == null && serverId == "vidfast") {
        onStatus("VidFast is unavailable; connecting through its backup…")
        media =
            ChromeStreamExtractor.extract(
                vidFastBackupPageUrl(tmdbId, isEpisode, season, episode),
                WEBVIEW_TIMEOUT_MS,
                onStatus,
            )
    }
    media ?: return null
    onStatus("Adding English subtitles…")
    val subtitles = downloadEnglishSubtitles(tmdbId, isEpisode, season, episode)
    return HlsStreamRequest(
        url = media.url,
        // CDNs distinguish the actual player page from this catalog URL. Keep the request identity
        // Chrome really used instead of rebuilding it from the catalog address.
        headers = media.headers,
        subtitles = subtitles,
        sourcePageUrl = pageUrl,
    )
}

/**
 * Fetches the same subtitle catalog used by the television player.
 *
 * The catalog is keyed by TMDB id, so it remains correct when desktop playback falls through to a
 * different stream provider. Only English tracks are offered, with ordinary dialogue subtitles
 * before hearing-impaired variants.
 */
private fun downloadEnglishSubtitles(
    tmdbId: Int,
    isEpisode: Boolean,
    season: Int,
    episode: Int,
): List<ExternalSubtitleTrack> {
    val catalogUrl =
        if (isEpisode) {
            "https://sub.vdrk.site/v1/tv/$tmdbId/$season/$episode"
        } else {
            "https://sub.vdrk.site/v1/movie/$tmdbId"
        }
    val json = fetchStr(catalogUrl, referer = "https://sub.vdrk.site/") ?: return emptyList()
    return parseDesktopSubtitleCatalog(json)
}

internal fun parseDesktopSubtitleCatalog(json: String): List<ExternalSubtitleTrack> {
    val root = runCatching { JSONTokener(json).nextValue() }.getOrNull()
    val entries = findDesktopSubtitleEntries(root)
    return buildList {
        for (index in 0 until entries.length()) {
            val item = entries.optJSONObject(index) ?: continue
            val label =
                listOf("label", "name", "language")
                    .firstNotNullOfOrNull { key -> item.optString(key).trim().takeIf(String::isNotBlank) }
            val language =
                listOf("lang", "language", "srclang", "languageCode")
                    .firstNotNullOfOrNull { key -> item.optString(key).trim().takeIf(String::isNotBlank) }
            if (!isEnglishSubtitleLabel(label, language)) continue
            val rawUrl =
                listOf("file", "url", "src")
                    .firstNotNullOfOrNull { key -> item.optString(key).trim().takeIf(String::isNotBlank) }
                    ?: continue
            val url = rawUrl.toHttpUrlOrNull()?.toString() ?: continue
            val mimeType = desktopSubtitleMimeType(url) ?: continue
            add(
                ExternalSubtitleTrack(
                    url = url,
                    label = label?.take(48) ?: "English",
                    language = language ?: "en",
                    mimeType = mimeType,
                )
            )
        }
    }
        .distinctBy { it.url }
        .sortedWith(
            compareBy<ExternalSubtitleTrack>(
                { if (it.label.equals("English", ignoreCase = true)) 0 else 1 },
                { if (isHearingImpairedSubtitleLabel(it.label)) 1 else 0 },
                { it.label.lowercase() },
            )
        )
}

private fun findDesktopSubtitleEntries(value: Any?, depth: Int = 0): JSONArray {
    if (depth > 2) return JSONArray()
    if (value is JSONArray) return value
    if (value !is JSONObject) return JSONArray()
    listOf("subtitles", "captions", "tracks", "results", "items", "data").forEach { key ->
        value.optJSONArray(key)?.let { return it }
    }
    listOf("data", "result", "payload").forEach { key ->
        value.optJSONObject(key)?.let { nested ->
            findDesktopSubtitleEntries(nested, depth + 1).takeIf { it.length() > 0 }?.let { return it }
        }
    }
    return JSONArray()
}

private fun desktopSubtitleMimeType(url: String): String? {
    val normalized = url.substringBefore('#').lowercase()
    return when {
        normalized.substringBefore('?').endsWith(".vtt") || normalized.contains("format=vtt") -> "text/vtt"
        normalized.substringBefore('?').endsWith(".srt") || normalized.contains("format=srt") -> "application/x-subrip"
        normalized.substringBefore('?').endsWith(".ass") || normalized.substringBefore('?').endsWith(".ssa") -> "text/x-ssa"
        normalized.substringBefore('?').endsWith(".ttml") || normalized.substringBefore('?').endsWith(".dfxp") -> "application/ttml+xml"
        else -> null
    }
}

private fun vidFastBackupPageUrl(
    tmdbId: Int,
    isEpisode: Boolean,
    season: Int,
    episode: Int,
): String {
    val vidRockIndex = STREAM_PROVIDERS.indexOfFirst { it.id == "vidrock" }
    return providerPageUrl(
        CatalogTarget(
            tmdbId,
            if (isEpisode) season else null,
            if (isEpisode) episode else null,
        ),
        vidRockIndex,
    ) ?: "https://vidrock.ru/movie/$tmdbId"
}

/** Long enough for a page that works slowly; short enough that a dead one moves on. */
private const val WEBVIEW_TIMEOUT_MS = 30_000L
private const val VIDFAST_PRIMARY_TIMEOUT_MS = 10_000L

private fun provider(id: String) = STREAM_PROVIDERS.firstOrNull { it.id == id }

// ── VidLink JSON API ──────────────────────────────────────────────────────
// vidlink.pro has an undocumented JSON API that returns HLS stream URLs.

private suspend fun extractVidLink(
    tmdbId: Int,
    isEpisode: Boolean,
    season: Int,
    episode: Int,
    onStatus: suspend (String) -> Unit,
): HlsStreamRequest? {
    onStatus("Querying VidLink API…")
    return runCatching {
        // Try the API endpoint
        val apiUrl = if (isEpisode)
            "https://vidlink.pro/api/b/tv/$tmdbId/$season/$episode"
        else
            "https://vidlink.pro/api/b/movie/$tmdbId"

        val json = fetchStr(apiUrl, referer = "https://vidlink.pro/") ?: return null
        val obj = JSONObject(json)

        // Response shape: { stream: { playlist: "...", captions: [...] } }
        val streamUrl = obj.optJSONObject("stream")?.optString("playlist")
            ?: obj.optString("url").takeIf { it.isNotBlank() }
            ?: findMediaUrl(json)
            ?: return null

        if (!isValidUrl(streamUrl)) return null
        HlsStreamRequest(
            url = streamUrl,
            headers = mapOf(
                "Referer" to "https://vidlink.pro/",
                "Origin"  to "https://vidlink.pro",
                "User-Agent" to UA,
            ),
        )
    }.getOrNull()
}

// ── MoviesAPI (JWPlayer embed, URL in HTML) ───────────────────────────────

private suspend fun extractMoviesApi(
    tmdbId: Int,
    isEpisode: Boolean,
    season: Int,
    episode: Int,
    onStatus: suspend (String) -> Unit,
): HlsStreamRequest? {
    onStatus("Fetching MoviesAPI page…")
    val url = if (isEpisode)
        "https://moviesapi.club/tv/$tmdbId-$season-$episode"
    else
        "https://moviesapi.club/movie/$tmdbId"
    return extractHtmlPage(url, onStatus)
}

// ── VidSrc.icu ────────────────────────────────────────────────────────────

private suspend fun extractVidSrcIcu(
    tmdbId: Int,
    isEpisode: Boolean,
    season: Int,
    episode: Int,
    onStatus: suspend (String) -> Unit,
): HlsStreamRequest? {
    onStatus("Fetching VidSrc page…")
    val embedUrl = if (isEpisode)
        "https://vidsrc.icu/embed/tv/$tmdbId/$season/$episode"
    else
        "https://vidsrc.icu/embed/movie/$tmdbId"
    return extractHtmlPage(embedUrl, onStatus)
}

// ── Generic HTML page extractor ───────────────────────────────────────────
// Works for providers that embed the stream URL directly in the HTML/JS source.

private suspend fun extractHtmlPage(
    url: String,
    onStatus: suspend (String) -> Unit,
): HlsStreamRequest? = runCatching {
    onStatus("Fetching page…")
    val html = fetchStr(url, referer = url) ?: return null

    // 1. Direct media URL in HTML
    findMediaUrl(html)?.let { return it.toRequest(url) }
    // 2. JWPlayer/Video.js JSON source field
    extractJsonSource(html)?.let { return it.toRequest(url) }
    // 3. JS variable assignment
    extractJsVar(html)?.let { return it.toRequest(url) }

    // 4. Follow iframes one level
    onStatus("Checking iframes…")
    for (iframeUrl in findIframeUrls(html, url).take(5)) {
        val iframeHtml = fetchStr(iframeUrl, referer = url) ?: continue
        val media = findMediaUrl(iframeHtml)
            ?: extractJsonSource(iframeHtml)
            ?: extractJsVar(iframeHtml)
        if (media != null) {
            return HlsStreamRequest(
                url = media,
                headers = mapOf(
                    "Referer" to iframeUrl,
                    "Origin"  to iframeUrl.toOrigin(),
                    "User-Agent" to UA,
                ),
            )
        }
        // One more level deep
        for (nested in findIframeUrls(iframeHtml, iframeUrl).take(3)) {
            val nestedHtml = fetchStr(nested, referer = iframeUrl) ?: continue
            val nestedMedia = findMediaUrl(nestedHtml)
                ?: extractJsonSource(nestedHtml)
                ?: extractJsVar(nestedHtml)
            if (nestedMedia != null) {
                return HlsStreamRequest(
                    url = nestedMedia,
                    headers = mapOf(
                        "Referer" to nested,
                        "Origin"  to nested.toOrigin(),
                        "User-Agent" to UA,
                    ),
                )
            }
        }
    }
    null
}.getOrNull()

private fun String.toRequest(referer: String) = HlsStreamRequest(
    url = this,
    headers = mapOf("Referer" to referer, "Origin" to referer.toOrigin(), "User-Agent" to UA),
)

// ── HTTP fetch ────────────────────────────────────────────────────────────

private fun fetchStr(url: String, referer: String): String? = runCatching {
    http.newCall(
        Request.Builder()
            .url(url)
            .header("User-Agent", UA)
            .header("Referer", referer)
            .header("Accept", "text/html,application/xhtml+xml,application/json,*/*;q=0.8")
            .header("Accept-Language", "en-US,en;q=0.9")
            .build()
    ).execute().use { it.body?.string() }
}.getOrNull()

// ── URL extraction helpers ────────────────────────────────────────────────

private fun findMediaUrl(content: String): String? {
    val pats = listOf(
        Pattern.compile("""https?://[^\s"'<>,\\]+\.m3u8[^\s"'<>,\\]*"""),
        Pattern.compile("""https?://[^\s"'<>,\\]{10,}\.mp4[^\s"'<>,\\]*"""),
    )
    for (pat in pats) {
        val m = pat.matcher(content)
        while (m.find()) {
            val found = m.group().trimEnd('\\', '"', '\'', ',', ')', ';')
            if (isValidUrl(found)) return found
        }
    }
    return null
}

/** Extracts stream URL from JWPlayer/Video.js JSON: "file":"https://..." */
private fun extractJsonSource(content: String): String? {
    val p = Pattern.compile(""""(?:file|src|source)"\s*:\s*"(https?://[^"]{8,})"""")
    val m = p.matcher(content)
    while (m.find()) {
        val url = m.group(1) ?: continue
        if (isValidUrl(url) && (url.contains(".m3u8") || url.contains(".mp4"))) return url
    }
    return null
}

/** Extracts stream URL from JS variable: file: "...", source: '...' etc. */
private fun extractJsVar(content: String): String? {
    val p = Pattern.compile(
        """(?:file|source|src|url|stream|hlsUrl|streamUrl|videoUrl|m3u8)\s*[:=]\s*['"]?(https?://[^\s'"<>,;]{10,})['"]?""",
        Pattern.CASE_INSENSITIVE,
    )
    val m = p.matcher(content)
    while (m.find()) {
        val url = m.group(1)?.trimEnd('\\', '"', '\'', ',', ')', ';') ?: continue
        if (isValidUrl(url) && (url.contains(".m3u8") || url.contains(".mp4"))) return url
    }
    return null
}

private fun findIframeUrls(content: String, pageUrl: String): List<String> {
    val result = mutableListOf<String>()
    val m = Pattern.compile("""<iframe[^>]+src=["']([^"']+)["']""", Pattern.CASE_INSENSITIVE).matcher(content)
    while (m.find()) {
        var src = m.group(1) ?: continue
        src = when {
            src.startsWith("//")   -> "https:$src"
            src.startsWith("/")    -> "${pageUrl.toOrigin()}$src"
            src.startsWith("http") -> src
            else -> continue
        }
        if (src !in result) result.add(src)
    }
    return result
}

/**
 * Names a page uses for a video that is not the film.
 *
 * Matched on the filename itself rather than anywhere in the address, so a real delivery path that
 * happens to sit under a folder of one of these names is not thrown away with them.
 */
private val DECOY_FILE_STEMS =
    listOf("demo-video", "demo", "sample", "preview", "placeholder", "decoy", "intro", "trailer")

private val DECORATIVE_MARKERS =
    listOf("logo", "thumbnail", "storyboard", "sprite", "poster", "banner", "background",
        "loading", "spinner")

private fun isValidUrl(url: String): Boolean {
    if (!url.startsWith("http")) return false
    val lower = url.lowercase()
    val path = lower.substringBefore('#').substringBefore('?')
    if (DECORATIVE_MARKERS.any { path.contains(it) }) return false
    // vidrock loads a demo-video.mp4 before it has resolved anything. Taking it is what put a file
    // with no moov atom in front of VLC — "Could not open demo-video.mp4" — while the real playlist
    // arrived seconds later and was never looked at. The television build has rejected this since
    // its own first version; the desktop extractor never learned to.
    val stem = path.substringAfterLast('/').substringBeforeLast('.')
    return DECOY_FILE_STEMS.none { stem == it || stem.startsWith("$it-") || stem.startsWith("${it}_") }
}

private fun String.toOrigin(): String {
    val proto = substringBefore("://")
    val host  = substringAfter("://").substringBefore('/')
    return if (host.isNotEmpty()) "$proto://$host" else this
}
