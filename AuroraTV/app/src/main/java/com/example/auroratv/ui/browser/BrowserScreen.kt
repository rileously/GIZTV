package com.example.auroratv.ui.browser

import android.annotation.SuppressLint
import android.graphics.Color as AndroidColor
import android.os.Build
import android.os.Handler
import android.os.Looper
import android.os.SystemClock
import android.util.Log
import android.webkit.CookieManager
import android.webkit.JsResult
import android.webkit.PermissionRequest
import android.webkit.RenderProcessGoneDetail
import android.webkit.SslErrorHandler
import android.webkit.WebChromeClient
import android.webkit.WebResourceError
import android.webkit.WebResourceRequest
import android.webkit.WebResourceResponse
import android.webkit.WebSettings
import android.webkit.WebView
import android.webkit.WebViewClient
import androidx.activity.compose.BackHandler
import androidx.compose.animation.animateColorAsState
import androidx.compose.animation.core.LinearEasing
import androidx.compose.animation.core.RepeatMode
import androidx.compose.animation.core.animateFloat
import androidx.compose.animation.core.animateFloatAsState
import androidx.compose.animation.core.infiniteRepeatable
import androidx.compose.animation.core.rememberInfiniteTransition
import androidx.compose.animation.core.tween
import androidx.compose.foundation.background
import androidx.compose.foundation.border
import androidx.compose.foundation.clickable
import androidx.compose.foundation.focusGroup
import androidx.compose.foundation.layout.Arrangement
import androidx.compose.foundation.layout.Box
import androidx.compose.foundation.layout.BoxWithConstraints
import androidx.compose.foundation.layout.Column
import androidx.compose.foundation.layout.Row
import androidx.compose.foundation.layout.Spacer
import androidx.compose.foundation.layout.aspectRatio
import androidx.compose.foundation.layout.fillMaxSize
import androidx.compose.foundation.layout.fillMaxWidth
import androidx.compose.foundation.layout.height
import androidx.compose.foundation.layout.padding
import androidx.compose.foundation.layout.size
import androidx.compose.foundation.layout.width
import androidx.compose.foundation.layout.widthIn
import androidx.compose.foundation.shape.RoundedCornerShape
import androidx.compose.runtime.Composable
import androidx.compose.runtime.DisposableEffect
import androidx.compose.runtime.LaunchedEffect
import androidx.compose.runtime.getValue
import androidx.compose.runtime.key
import androidx.compose.runtime.mutableIntStateOf
import androidx.compose.runtime.mutableStateOf
import androidx.compose.runtime.remember
import androidx.compose.runtime.rememberUpdatedState
import androidx.compose.runtime.setValue
import androidx.compose.ui.Alignment
import androidx.compose.ui.Modifier
import androidx.compose.ui.draw.clip
import androidx.compose.ui.focus.FocusRequester
import androidx.compose.ui.focus.focusRequester
import androidx.compose.ui.focus.onFocusChanged
import androidx.compose.ui.graphics.Brush
import androidx.compose.ui.graphics.Color
import androidx.compose.ui.graphics.graphicsLayer
import androidx.compose.ui.platform.LocalContext
import androidx.compose.ui.semantics.Role
import androidx.compose.ui.semantics.contentDescription
import androidx.compose.ui.semantics.role
import androidx.compose.ui.semantics.semantics
import androidx.compose.ui.text.font.FontWeight
import androidx.compose.ui.text.style.TextOverflow
import androidx.compose.ui.unit.dp
import androidx.compose.ui.unit.sp
import androidx.compose.ui.viewinterop.AndroidView
import androidx.core.net.toUri
import androidx.media3.common.MimeTypes
import androidx.tv.material3.Text
import com.example.auroratv.BuildConfig
import com.example.auroratv.data.PlaybackContext
import com.example.auroratv.theme.AuroraBlue
import com.example.auroratv.theme.AuroraMint
import com.example.auroratv.theme.DeepSpace
import com.example.auroratv.theme.MutedBlue
import com.example.auroratv.theme.NightSurface
import com.example.auroratv.theme.SoftWhite
import com.example.auroratv.ui.catalog.CatalogButton
import com.example.auroratv.ui.catalog.GizTvMark
import com.example.auroratv.ui.catalog.TmdbArtwork
import com.example.auroratv.ui.catalog.TmdbPlaybackDetails
import com.example.auroratv.ui.catalog.TmdbPlaybackDetailsRepository
import com.example.auroratv.ui.catalog.remoteFocusNavigation
import com.example.auroratv.ui.player.ExternalSubtitleTrack
import com.example.auroratv.ui.player.HlsStreamRequest
import java.io.ByteArrayInputStream
import java.net.HttpURLConnection
import java.net.URL
import java.util.Locale
import java.util.concurrent.ConcurrentHashMap
import java.util.concurrent.Executors
import java.util.concurrent.atomic.AtomicBoolean
import java.util.concurrent.atomic.AtomicInteger
import kotlinx.coroutines.delay
import org.json.JSONArray
import org.json.JSONObject
import org.json.JSONTokener

private const val HOME_URL = "https://skyflix.to/"
private const val SUBTITLE_DISCOVERY_DELAY_MS = 2_500L
private const val MAX_SUBTITLE_CATALOG_WAIT_MS = 8_000L
private const val SUBTITLE_CATALOG_POLL_MS = 250L
private const val PREPARE_TIMEOUT_MS = 30_000L

private val blockedHosts =
  setOf(
    "2mdn.net",
    "adcolony.com",
    "adform.net",
    "adgrx.com",
    "admaven.com",
    "adnxs.com",
    "adsterra.com",
    "advertising.com",
    "amazon-adsystem.com",
    "appsflyer.com",
    "clickadu.com",
    "criteo.com",
    "criteo.net",
    "doubleclick.net",
    "exoclick.com",
    "googleadservices.com",
    "googlesyndication.com",
    "hilltopads.net",
    "monetag.com",
    "onclicka.com",
    "outbrain.com",
    "popads.net",
    "popcash.net",
    "propellerads.com",
    "pushground.com",
    "quantserve.com",
    "scorecardresearch.com",
    "taboola.com",
    "trafficjunky.net",
    "zedo.com",
  )

private val blockedUrlFragments =
  listOf(
    "/ads/",
    "/advertising/",
    "/popunder/",
    "adservice=",
    "googleads",
    "pagead2.",
    "popup_ad",
  )

private val adCleanupScript =
  """
  (function () {
    if (window.__auroraCleanupInstalled) return;
    window.__auroraCleanupInstalled = true;

    const adHosts = ${blockedHosts.joinToString(prefix = "[", postfix = "]") { "'${it}'" }};
    const selectors = [
      '.adsbygoogle', '[data-ad]', '[data-ad-slot]', '[aria-label="Advertisement"]',
      '[id^="google_ads"]', '[id^="ad-"]', '[class^="ad-"]', '[class*=" ad-"]',
      '[id*="popunder"]', '[class*="popunder"]', '[id*="popup-ad"]', '[class*="popup-ad"]',
      '.sharethis-inline-share-buttons', '.st-inline-share-buttons',
      '[id*="sharethis"]', '[class*="sharethis"]', '[aria-label$="sharing button"]'
    ];

    function isAdUrl(value) {
      if (!value) return false;
      try {
        const host = new URL(value, location.href).hostname.toLowerCase();
        return adHosts.some(item => host === item || host.endsWith('.' + item));
      } catch (_) { return false; }
    }

    function clean() {
      selectors.forEach(selector => {
        document.querySelectorAll(selector).forEach(node => node.remove());
      });
      document.querySelectorAll('iframe, script, img').forEach(node => {
        if (isAdUrl(node.src)) node.remove();
      });
      document.querySelectorAll('a[target="_blank"]').forEach(link => {
        link.target = '_self';
        link.rel = 'noopener noreferrer';
      });
    }

    window.open = function () { return null; };
    document.addEventListener('click', function (event) {
      const link = event.target && event.target.closest ? event.target.closest('a') : null;
      if (link && isAdUrl(link.href)) {
        event.preventDefault();
        event.stopImmediatePropagation();
      }
    }, true);

    clean();
    new MutationObserver(clean).observe(document.documentElement, { childList: true, subtree: true });
  })();
  """.trimIndent()

@SuppressLint("SetJavaScriptEnabled")
@Composable
internal fun BrowserScreen(
  initialUrl: String,
  playback: PlaybackContext?,
  onUrlChanged: (String) -> Unit,
  onExit: () -> Unit,
  onStreamDetected: (HlsStreamRequest) -> Unit,
) {
  val context = LocalContext.current
  val latestUrlChanged = rememberUpdatedState(onUrlChanged)
  val latestStreamDetected = rememberUpdatedState(onStreamDetected)
  var webView by remember { mutableStateOf<WebView?>(null) }
  var activeWebViewClient by remember { mutableStateOf<AdBlockingWebViewClient?>(null) }
  var title by remember { mutableStateOf("Skyflix") }
  var currentUrl by remember { mutableStateOf(initialUrl) }
  var progress by remember { mutableIntStateOf(0) }
  var canGoBack by remember { mutableStateOf(false) }
  var rendererGeneration by remember { mutableIntStateOf(0) }
  var status by remember { mutableStateOf("Secure browsing") }
  var stage by remember { mutableStateOf(PreparationStage.OPENING) }
  var preparationFailed by remember { mutableStateOf(false) }
  var preparationAttempt by remember { mutableIntStateOf(0) }
  // The viewer can drop out of the branded wait and drive the page by hand.
  var pageRevealed by remember { mutableStateOf(false) }
  val showChrome = playback == null || pageRevealed

  // Time out only while opening/searching. Once a stream is found, subtitle finalization has its own
  // bounded wait and must not briefly show an error before handing off to the native player.
  LaunchedEffect(playback, preparationAttempt, stage) {
    if (playback == null || !stage.canTimeOut) return@LaunchedEffect
    delay(PREPARE_TIMEOUT_MS)
    activeWebViewClient?.suspendStreamDispatch()
    preparationFailed = true
  }

  BackHandler {
    val browser = webView
    when {
      playback != null && !pageRevealed -> onExit()
      browser?.canGoBack() == true -> browser.goBack()
      else -> onExit()
    }
  }

  Box(Modifier.fillMaxSize().background(DeepSpace)) {
  Column(Modifier.fillMaxSize()) {
    if (showChrome) {
      BrowserToolbar(
        title = title,
        url = currentUrl,
        status = status,
        canGoBack = canGoBack,
        onExit = onExit,
        onBack = { webView?.goBack() },
        onReload = { webView?.reload() },
        onHome = { webView?.loadUrl(initialUrl) },
      )
      if (progress in 1..99) {
        Box(Modifier.fillMaxWidth().height(3.dp).background(NightSurface)) {
          Box(Modifier.fillMaxWidth(progress / 100f).height(3.dp).background(AuroraBlue))
        }
      }
    }

    key(rendererGeneration) {
      AndroidView(
        factory = {
          WebView(context).apply webView@ {
            setBackgroundColor(AndroidColor.rgb(7, 17, 31))
            isFocusable = true
            isFocusableInTouchMode = true
            settings.apply {
              javaScriptEnabled = true
              domStorageEnabled = true
              allowFileAccess = false
              allowContentAccess = false
              javaScriptCanOpenWindowsAutomatically = false
              setSupportMultipleWindows(false)
              mediaPlaybackRequiresUserGesture = true
              useWideViewPort = true
              loadWithOverviewMode = true
              builtInZoomControls = false
              displayZoomControls = false
              cacheMode = WebSettings.LOAD_DEFAULT
              mixedContentMode = WebSettings.MIXED_CONTENT_NEVER_ALLOW
              setGeolocationEnabled(false)
              if (Build.VERSION.SDK_INT >= Build.VERSION_CODES.O) safeBrowsingEnabled = true
            }
            CookieManager.getInstance().apply {
              setAcceptCookie(true)
              setAcceptThirdPartyCookies(this@webView, true)
            }
            WebView.setWebContentsDebuggingEnabled(false)

            val userAgent = settings.userAgentString
            val client =
              AdBlockingWebViewClient(
                userAgent = userAgent,
                onPageState = { pageTitle, pageUrl, pageProgress, historyAvailable ->
                  title = pageTitle.ifBlank { "Skyflix" }
                  currentUrl = pageUrl
                  progress = pageProgress
                  canGoBack = historyAvailable
                  latestUrlChanged.value(pageUrl)
                },
                onStatus = { status = it },
                onStage = { stage = it },
                onStreamDetected = { latestStreamDetected.value(it) },
                onRendererGone = { rendererGeneration += 1 },
              )
            webViewClient = client
            activeWebViewClient = client
            webChromeClient =
              PopupBlockingChromeClient(
                onProgress = { progress = it },
                onTitle = { if (!it.isNullOrBlank()) title = it },
              )
            setDownloadListener { url, _, _, mimeType, _ ->
              if (url != null) {
                val headers = buildMap {
                  put("User-Agent", userAgent)
                  put("Referer", this@webView.url ?: HOME_URL)
                  CookieManager.getInstance().getCookie(url)?.let { put("Cookie", it) }
                }
                when {
                  isExternalSubtitleUrl(url) -> client.captureSubtitle(url)
                  isHlsUrl(url) || mimeType?.contains("mpegurl", ignoreCase = true) == true ->
                    client.queueStream(this@webView, url, headers)
                }
              }
            }
            loadUrl(initialUrl)
            requestFocus()
            webView = this
          }
        },
        modifier = Modifier.fillMaxSize(),
      )
    }
  }

    // The page still has to load and run its scripts, but the viewer never has to look at it.
    if (playback != null && !pageRevealed) {
      PreparingOverlay(
        playback = playback,
        stage = stage,
        failed = preparationFailed,
        onRetry = {
          preparationFailed = false
          preparationAttempt += 1
          stage = PreparationStage.OPENING
          webView?.loadUrl(initialUrl)
        },
        onShowPage = { pageRevealed = true },
        onCancel = onExit,
      )
    }
  }

  DisposableEffect(rendererGeneration) {
    onDispose {
      activeWebViewClient?.close()
      activeWebViewClient = null
      webView?.apply {
        stopLoading()
        loadUrl("about:blank")
        clearHistory()
        removeAllViews()
        destroy()
      }
      webView = null
    }
  }
}

/**
 * Covers the working page with the title being opened.
 *
 * The web page underneath still has to load and run its scripts — that is the only way the stream
 * URL is ever discovered — but none of that is the viewer's business.
 */
@Composable
private fun PreparingOverlay(
  playback: PlaybackContext,
  stage: PreparationStage,
  failed: Boolean,
  onRetry: () -> Unit,
  onShowPage: () -> Unit,
  onCancel: () -> Unit,
) {
  val detailsRepository = remember { TmdbPlaybackDetailsRepository(BuildConfig.TMDB_API_KEY) }
  var loadedDetails by remember(playback) { mutableStateOf<TmdbPlaybackDetails?>(null) }
  LaunchedEffect(playback) {
    loadedDetails = runCatching { detailsRepository.details(playback) }.getOrNull()
  }
  val details = remember(playback, loadedDetails) { preparationDetails(playback, loadedDetails) }
  val displaySubtitle =
    playback.subtitle?.takeIf {
      it.isNotBlank() && !it.equals(loadedDetails?.year ?: playback.year, ignoreCase = true)
    }
  val pulse = rememberInfiniteTransition(label = "prepare pulse")
  val sweep by
    pulse.animateFloat(
      initialValue = 0f,
      targetValue = 1f,
      animationSpec = infiniteRepeatable(tween(1_400, easing = LinearEasing), RepeatMode.Restart),
      label = "prepare sweep",
    )
  BoxWithConstraints(
    modifier =
      Modifier.fillMaxSize()
        .background(
          Brush.radialGradient(
            colors = listOf(Color(0xFF17345C), DeepSpace),
            radius = 1_500f,
            center = androidx.compose.ui.geometry.Offset(900f, 320f),
          )
        ),
  ) {
    val compact = maxHeight < 600.dp || maxWidth < 900.dp
    val posterWidth = if (compact) 150.dp else 196.dp
    val contentGap = if (compact) 28.dp else 42.dp
    Row(
      verticalAlignment = Alignment.CenterVertically,
      modifier = Modifier.align(Alignment.Center).widthIn(max = 1_120.dp).padding(horizontal = 48.dp),
    ) {
      Box {
        TmdbArtwork(
          url = playback.posterUrl,
          contentDescription = "${playback.title} poster",
          modifier =
            Modifier.width(posterWidth).aspectRatio(2f / 3f).clip(RoundedCornerShape(20.dp))
              .border(1.dp, SoftWhite.copy(alpha = .16f), RoundedCornerShape(20.dp)),
        )
        Text(
          if (playback.isEpisode) "EPISODE" else "MOVIE",
          color = DeepSpace,
          fontWeight = FontWeight.Black,
          fontSize = 10.sp,
          letterSpacing = 1.sp,
          modifier =
            Modifier.align(Alignment.TopStart).padding(12.dp).clip(RoundedCornerShape(99.dp))
              .background(AuroraMint).padding(horizontal = 10.dp, vertical = 5.dp),
        )
      }
      Spacer(Modifier.width(contentGap))
      Column(modifier = Modifier.weight(1f).widthIn(max = 760.dp)) {
        Text(
          if (failed) "Couldn't start this title" else "GETTING IT READY",
          color = AuroraMint,
          fontWeight = FontWeight.Black,
          letterSpacing = 2.sp,
          fontSize = 11.sp,
        )
        Spacer(Modifier.height(8.dp))
        Text(
          playback.title,
          color = SoftWhite,
          fontWeight = FontWeight.Black,
          fontSize = if (compact) 30.sp else 38.sp,
          maxLines = 2,
          overflow = TextOverflow.Ellipsis,
          lineHeight = if (compact) 35.sp else 43.sp,
        )
        displaySubtitle?.let {
          Spacer(Modifier.height(5.dp))
          Text(it, color = AuroraMint, fontWeight = FontWeight.Bold, fontSize = 15.sp, maxLines = 1)
        }
        if (details.infoChips.isNotEmpty()) {
          Spacer(Modifier.height(14.dp))
          Row(horizontalArrangement = Arrangement.spacedBy(8.dp)) {
            details.infoChips.forEach { PreparationInfoChip(it) }
          }
        }
        details.overview?.let {
          Spacer(Modifier.height(16.dp))
          Text(
            it,
            color = SoftWhite.copy(alpha = .82f),
            fontSize = 13.sp,
            lineHeight = 19.sp,
            maxLines = if (compact) 2 else 3,
            overflow = TextOverflow.Ellipsis,
          )
        }
        if (details.castNames.isNotEmpty()) {
          Spacer(Modifier.height(14.dp))
          Text("STARRING", color = AuroraMint, fontWeight = FontWeight.Black, fontSize = 10.sp, letterSpacing = 1.4.sp)
          Spacer(Modifier.height(4.dp))
          Text(
            details.castNames.joinToString("  •  "),
            color = MutedBlue,
            fontWeight = FontWeight.Bold,
            fontSize = 12.sp,
            lineHeight = 17.sp,
            maxLines = 2,
            overflow = TextOverflow.Ellipsis,
          )
        }
        Spacer(Modifier.height(if (compact) 18.dp else 24.dp))
        if (failed) {
          Text(
            "No playable stream turned up for this one. It may not be available right now.",
            color = MutedBlue,
            fontSize = 13.sp,
          )
          Spacer(Modifier.height(16.dp))
          PreparationFailureActions(onRetry, onShowPage, onCancel)
        } else {
          Column(
            modifier =
              Modifier.fillMaxWidth().clip(RoundedCornerShape(16.dp))
                .background(NightSurface.copy(alpha = .78f))
                .border(1.dp, SoftWhite.copy(alpha = .09f), RoundedCornerShape(16.dp))
                .padding(horizontal = 16.dp, vertical = 14.dp)
          ) {
            Row(verticalAlignment = Alignment.CenterVertically) {
              GizTvMark(modifier = Modifier.size(28.dp), cornerRadius = 8.dp, alpha = .9f)
              Spacer(Modifier.width(11.dp))
              Column(Modifier.weight(1f)) {
                Text(stage.label, color = SoftWhite, fontWeight = FontWeight.Black, fontSize = 14.sp)
                Text("Secure playback · ads blocked · English subtitles preferred", color = MutedBlue, fontSize = 10.sp)
              }
            }
            Spacer(Modifier.height(12.dp))
            PreparationStageTrack(stage = stage, sweep = sweep)
          }
          Spacer(Modifier.height(12.dp))
          Text("Press Back to cancel", color = MutedBlue.copy(alpha = .8f), fontSize = 11.sp)
        }
      }
    }
  }
}

/** Gives the failure actions an explicit TV focus path instead of leaving focus in the WebView. */
@Composable
internal fun PreparationFailureActions(
  onRetry: () -> Unit,
  onShowPage: () -> Unit,
  onCancel: () -> Unit,
) {
  val retryFocusRequester = remember { FocusRequester() }
  val pageFocusRequester = remember { FocusRequester() }
  val backFocusRequester = remember { FocusRequester() }

  LaunchedEffect(Unit) { retryFocusRequester.requestFocus() }

  Row(modifier = Modifier.focusGroup(), horizontalArrangement = Arrangement.spacedBy(10.dp)) {
    CatalogButton(
      label = "Try again",
      onClick = onRetry,
      modifier =
        Modifier.focusRequester(retryFocusRequester)
          .remoteFocusNavigation(right = pageFocusRequester),
    )
    CatalogButton(
      label = "Open the page",
      onClick = onShowPage,
      modifier =
        Modifier.focusRequester(pageFocusRequester)
          .remoteFocusNavigation(left = retryFocusRequester, right = backFocusRequester),
    )
    CatalogButton(
      label = "Back",
      onClick = onCancel,
      modifier =
        Modifier.focusRequester(backFocusRequester)
          .remoteFocusNavigation(left = pageFocusRequester),
    )
  }
}

private data class PreparationDetails(
  val overview: String?,
  val infoChips: List<String>,
  val castNames: List<String>,
)

private fun preparationDetails(playback: PlaybackContext, remote: TmdbPlaybackDetails?): PreparationDetails {
  val releaseDate = remote?.releaseDate ?: playback.airDate
  val year = remote?.year ?: playback.year
  val runtime = remote?.runtimeMinutes ?: playback.runtimeMinutes
  val rating = remote?.rating ?: playback.rating
  val genres = remote?.genres?.takeIf { it.isNotEmpty() } ?: playback.genres
  val chips = buildList {
    if (playback.isEpisode) add("S${playback.seasonNumber} · E${playback.episodeNumber}")
    (releaseDate ?: year)?.takeIf(String::isNotBlank)?.let(::add)
    runtime?.takeIf { it > 0 }?.let { add("$it min") }
    rating?.takeIf { it > 0.0 }?.let { add("★ ${String.format(Locale.US, "%.1f", it)}") }
    genres.firstOrNull()?.let(::add)
  }.distinct().take(5)
  return PreparationDetails(
    overview = remote?.overview ?: playback.overview?.takeIf(String::isNotBlank),
    infoChips = chips,
    castNames = remote?.castNames?.takeIf { it.isNotEmpty() } ?: playback.castNames.take(6),
  )
}

@Composable
private fun PreparationInfoChip(label: String) {
  Text(
    label,
    color = SoftWhite,
    fontWeight = FontWeight.Bold,
    fontSize = 11.sp,
    modifier =
      Modifier.clip(RoundedCornerShape(99.dp)).background(SoftWhite.copy(alpha = .09f))
        .border(1.dp, SoftWhite.copy(alpha = .11f), RoundedCornerShape(99.dp))
        .padding(horizontal = 11.dp, vertical = 6.dp),
  )
}

@Composable
private fun PreparationStageTrack(stage: PreparationStage, sweep: Float) {
  Row(Modifier.fillMaxWidth(), horizontalArrangement = Arrangement.spacedBy(7.dp)) {
    PreparationStage.entries.forEachIndexed { index, item ->
      Column(Modifier.weight(1f)) {
        Box(
          Modifier.fillMaxWidth().height(4.dp).clip(RoundedCornerShape(99.dp))
            .background(if (index <= stage.ordinal) AuroraMint.copy(alpha = if (index == stage.ordinal) .38f else 1f) else SoftWhite.copy(alpha = .12f))
        ) {
          if (index == stage.ordinal) {
            Box(
              Modifier.fillMaxWidth(.38f).height(4.dp)
                .graphicsLayer { translationX = (180.dp.toPx() * 1.38f) * sweep - 180.dp.toPx() * .38f }
                .clip(RoundedCornerShape(99.dp)).background(AuroraMint)
            )
          }
        }
        Spacer(Modifier.height(5.dp))
        Text(
          item.shortLabel,
          color = if (index <= stage.ordinal) SoftWhite else MutedBlue.copy(alpha = .65f),
          fontWeight = FontWeight.Black,
          fontSize = 9.sp,
        )
      }
    }
  }
}

/** The three things a viewer actually cares about while a title is being prepared. */
internal enum class PreparationStage(val label: String, val shortLabel: String) {
  OPENING("Opening the player…", "OPEN PLAYER"),
  FINDING_STREAM("Finding the best stream…", "FIND STREAM"),
  LOADING_SUBTITLES("Loading subtitles…", "ADD SUBTITLES"),

  ;

  val canTimeOut: Boolean get() = this != LOADING_SUBTITLES
}

private class AdBlockingWebViewClient(
  private val userAgent: String,
  private val onPageState: (String, String, Int, Boolean) -> Unit,
  private val onStatus: (String) -> Unit,
  private val onStage: (PreparationStage) -> Unit,
  private val onStreamDetected: (HlsStreamRequest) -> Unit,
  private val onRendererGone: () -> Unit,
) : WebViewClient() {
  private val mainHandler = Handler(Looper.getMainLooper())
  private val streamReported = AtomicBoolean(false)
  private val streamDispatchEnabled = AtomicBoolean(true)
  private val closed = AtomicBoolean(false)
  private val blockedRequestCount = AtomicInteger(0)
  private val pageGeneration = AtomicInteger(0)
  private val subtitleCatalogFetches = AtomicInteger(0)
  private val subtitles = ConcurrentHashMap<String, ExternalSubtitleTrack>()
  private val fetchedSubtitleCatalogs = ConcurrentHashMap<String, Boolean>()
  private val subtitleExecutor =
    Executors.newSingleThreadExecutor { runnable ->
      Thread(runnable, "AuroraSubtitleCatalog").apply { isDaemon = true }
    }
  @Volatile private var pendingStream: HlsStreamRequest? = null
  @Volatile private var streamQueuedAtMs = 0L
  @Volatile private var currentPageUrl = HOME_URL
  @Volatile private var currentPageTitle = ""
  private val dispatchPendingStream =
    object : Runnable {
      override fun run() {
        if (!streamDispatchEnabled.get()) return
        val stream = pendingStream ?: return
        val elapsedMs = SystemClock.elapsedRealtime() - streamQueuedAtMs
        if (subtitleCatalogFetches.get() > 0 && elapsedMs < MAX_SUBTITLE_CATALOG_WAIT_MS) {
          onStatus("Video found · loading English subtitle catalog…")
          mainHandler.postDelayed(this, SUBTITLE_CATALOG_POLL_MS)
          return
        }
        val discovered =
          subtitles.values.sortedWith(
            compareBy<ExternalSubtitleTrack>({ englishSubtitleRank(it.label) }, { it.label.lowercase() })
          )
        Log.i("AuroraHls", "Opening native player with ${discovered.size} separate subtitle track(s)")
        onStreamDetected(stream.copy(subtitles = discovered))
      }
  }

  override fun onPageStarted(view: WebView, url: String, favicon: android.graphics.Bitmap?) {
    currentPageUrl = url
    currentPageTitle = view.title.orEmpty()
    pageGeneration.incrementAndGet()
    streamDispatchEnabled.set(true)
    streamReported.set(false)
    blockedRequestCount.set(0)
    subtitles.clear()
    fetchedSubtitleCatalogs.clear()
    pendingStream = null
    mainHandler.removeCallbacks(dispatchPendingStream)
    onStatus("Blocking ads and popups")
    onStage(PreparationStage.OPENING)
    onPageState(view.title.orEmpty(), url, 1, view.canGoBack())
    subtitleCatalogUrlsForPage(url).forEach { catalogUrl ->
      captureSubtitleCatalog(
        url = catalogUrl,
        requestHeaders =
          mapOf(
            "User-Agent" to userAgent,
            "Referer" to url,
            "Origin" to url.toUri().let { "${it.scheme}://${it.host}" },
          ),
      )
    }
  }

  override fun onPageFinished(view: WebView, url: String) {
    currentPageTitle = view.title.orEmpty()
    view.evaluateJavascript(adCleanupScript, null)
    onStatus("${blockedRequestCount.get()} ads blocked · Popups off")
    if (!streamReported.get()) onStage(PreparationStage.FINDING_STREAM)
    onPageState(view.title.orEmpty(), url, 100, view.canGoBack())
  }

  override fun shouldOverrideUrlLoading(view: WebView, request: WebResourceRequest): Boolean {
    val url = request.url.toString()
    if (!isWebUrl(url) || isBlockedUrl(url)) {
      onStatus("Blocked unwanted navigation")
      return true
    }
    if (isHlsUrl(url)) {
      reportStream(view, request)
      return true
    }
    return false
  }

  override fun shouldInterceptRequest(view: WebView, request: WebResourceRequest): WebResourceResponse? {
    val url = request.url.toString()
    if (isBlockedUrl(url)) {
      val blocked = blockedRequestCount.incrementAndGet()
      if (blocked == 1 || blocked % 5 == 0) mainHandler.post { onStatus("$blocked ads blocked · Popups off") }
      return emptyResponse()
    }
    if (isSupportedSubtitleCatalogUrl(url)) captureSubtitleCatalog(url, request.requestHeaders)
    if (isExternalSubtitleUrl(url)) captureSubtitle(url)
    if (isHlsUrl(url)) reportStream(view, request)
    return null
  }

  override fun onReceivedError(view: WebView, request: WebResourceRequest, error: WebResourceError) {
    if (request.isForMainFrame) onStatus("Page error · Press reload")
  }

  override fun onReceivedSslError(view: WebView, handler: SslErrorHandler, error: android.net.http.SslError) {
    handler.cancel()
    // Players pull in dozens of third-party subresources; one failing TLS handshake is routine and
    // says nothing about the page the viewer asked for. Only the main document is worth reporting.
    if (error.url == currentPageUrl) onStatus("Unsafe connection blocked") else Log.i("AuroraHls", "Blocked unsafe subresource: ${error.url}")
  }

  override fun onRenderProcessGone(view: WebView, detail: RenderProcessGoneDetail): Boolean {
    mainHandler.post {
      onStatus("Browser restarted")
      onRendererGone()
    }
    return true
  }

  private fun reportStream(view: WebView?, request: WebResourceRequest) {
    val url = request.url.toString()
    val headers =
      completeStreamRequestHeaders(
        requestHeaders = request.requestHeaders,
        userAgent = userAgent,
        fallbackReferer = currentPageUrl,
        cookie = CookieManager.getInstance().getCookie(url),
      )
    queueStream(view, url, headers)
  }

  fun queueStream(view: WebView?, url: String, headers: Map<String, String>) {
    if (closed.get() || !streamDispatchEnabled.get()) return
    if (!streamReported.compareAndSet(false, true)) return
    pendingStream =
      HlsStreamRequest(
        url = url,
        headers = headers,
        sourcePageUrl = currentPageUrl,
        title = currentPageTitle.takeIf { it.isNotBlank() },
      )
    streamQueuedAtMs = SystemClock.elapsedRealtime()
    Log.i("AuroraHls", "Detected HLS request: $url")
    mainHandler.post {
      if (!streamDispatchEnabled.get()) return@post
      onStage(PreparationStage.LOADING_SUBTITLES)
      onStatus("Video found · collecting separate subtitles…")
      collectPageSubtitles(view)
      mainHandler.removeCallbacks(dispatchPendingStream)
      mainHandler.postDelayed(dispatchPendingStream, SUBTITLE_DISCOVERY_DELAY_MS)
    }
  }

  fun captureSubtitle(url: String, label: String? = null, language: String? = null) {
    if (closed.get()) return
    val track = createExternalSubtitleTrack(url, label, language) ?: return
    if (subtitles.putIfAbsent(track.url, track) == null) {
      Log.i("AuroraHls", "Detected external subtitle: ${track.label} ${track.url}")
      mainHandler.post {
        if (pendingStream != null) onStatus("Video found · ${subtitles.size} separate subtitle(s)")
      }
    }
  }

  /** Prevents a genuinely timed-out attempt from navigating to playback several seconds later. */
  fun suspendStreamDispatch() {
    streamDispatchEnabled.set(false)
    pendingStream = null
    mainHandler.removeCallbacks(dispatchPendingStream)
  }

  fun close() {
    if (!closed.compareAndSet(false, true)) return
    pendingStream = null
    mainHandler.removeCallbacks(dispatchPendingStream)
    subtitleExecutor.shutdownNow()
  }

  private fun captureSubtitleCatalog(url: String, requestHeaders: Map<String, String>) {
    if (closed.get() || fetchedSubtitleCatalogs.putIfAbsent(url, true) != null) return
    val generation = pageGeneration.get()
    subtitleCatalogFetches.incrementAndGet()
    subtitleExecutor.execute {
      try {
        val json = downloadSubtitleCatalog(url, requestHeaders, userAgent)
        val tracks = parseSubtitleCatalog(json)
        if (!closed.get() && generation == pageGeneration.get()) {
          tracks.forEach { captureSubtitle(it.url, it.label, it.language) }
          Log.i("AuroraHls", "Loaded ${tracks.size} English subtitle option(s) from ${url.toUri().host}")
        }
      } catch (error: Exception) {
        Log.w("AuroraHls", "Could not load subtitle catalog from ${url.toUri().host}", error)
      } finally {
        subtitleCatalogFetches.decrementAndGet()
      }
    }
  }

  private fun collectPageSubtitles(view: WebView?) {
    view ?: return
    view.evaluateJavascript(
      """
      (function () {
        var tracks = Array.from(document.querySelectorAll('track[src]')).map(function(track) {
          return { url: track.src, label: track.label || '', language: track.srclang || '' };
        });
        var frames = Array.from(document.querySelectorAll('iframe[src]')).map(function(frame) {
          return frame.src || '';
        });
        return JSON.stringify({ tracks: tracks, frames: frames });
      })();
      """.trimIndent()
    ) { encoded ->
      val json = runCatching { JSONTokener(encoded).nextValue() as? String }.getOrNull() ?: return@evaluateJavascript
      val payload = runCatching { JSONObject(json) }.getOrNull() ?: return@evaluateJavascript
      val tracks = payload.optJSONArray("tracks") ?: JSONArray()
      for (index in 0 until tracks.length()) {
        val item = tracks.optJSONObject(index) ?: continue
        captureSubtitle(item.optString("url"), item.optString("label"), item.optString("language"))
      }
      val pageUrl = view.url.orEmpty()
      val catalogHeaders =
        mapOf(
          "User-Agent" to userAgent,
          "Referer" to pageUrl,
          "Origin" to pageUrl.toUri().let { "${it.scheme}://${it.host}" },
        )
      val frames = payload.optJSONArray("frames") ?: JSONArray()
      for (index in 0 until frames.length()) {
        subtitleCatalogUrlsForEmbeddedPlayer(frames.optString(index)).forEach { catalogUrl ->
          captureSubtitleCatalog(catalogUrl, catalogHeaders)
        }
      }
    }
  }
}

private class PopupBlockingChromeClient(
  private val onProgress: (Int) -> Unit,
  private val onTitle: (String?) -> Unit,
) : WebChromeClient() {
  override fun onProgressChanged(view: WebView?, newProgress: Int) = onProgress(newProgress)

  override fun onReceivedTitle(view: WebView?, title: String?) = onTitle(title)

  override fun onCreateWindow(view: WebView?, isDialog: Boolean, isUserGesture: Boolean, resultMsg: android.os.Message?): Boolean = false

  override fun onJsAlert(view: WebView?, url: String?, message: String?, result: JsResult): Boolean {
    result.cancel()
    return true
  }

  override fun onJsConfirm(view: WebView?, url: String?, message: String?, result: JsResult): Boolean {
    result.cancel()
    return true
  }

  override fun onPermissionRequest(request: PermissionRequest) = request.deny()

  override fun onGeolocationPermissionsShowPrompt(origin: String?, callback: android.webkit.GeolocationPermissions.Callback) {
    callback.invoke(origin, false, false)
  }
}

@Composable
private fun BrowserToolbar(
  title: String,
  url: String,
  status: String,
  canGoBack: Boolean,
  onExit: () -> Unit,
  onBack: () -> Unit,
  onReload: () -> Unit,
  onHome: () -> Unit,
) {
  BoxWithConstraints(Modifier.fillMaxWidth().background(NightSurface)) {
    if (maxWidth < 600.dp) {
      Column(Modifier.fillMaxWidth().height(112.dp).padding(horizontal = 10.dp, vertical = 8.dp)) {
        Row(horizontalArrangement = Arrangement.spacedBy(6.dp), verticalAlignment = Alignment.CenterVertically) {
          BrowserToolbarButton("App", onExit)
          BrowserToolbarButton("Back", onBack, enabled = canGoBack)
          BrowserToolbarButton("Home", onHome)
          BrowserToolbarButton("Reload", onReload)
        }
        Spacer(Modifier.height(5.dp))
        Text(title, color = SoftWhite, fontWeight = FontWeight.Bold, fontSize = 14.sp, maxLines = 1, overflow = TextOverflow.Ellipsis)
        Text(url, color = MutedBlue, fontSize = 10.sp, maxLines = 1, overflow = TextOverflow.Ellipsis)
        Text(status, color = AuroraMint, fontWeight = FontWeight.Bold, fontSize = 10.sp, maxLines = 1, overflow = TextOverflow.Ellipsis)
      }
    } else {
      Row(
    modifier = Modifier.fillMaxWidth().height(78.dp).background(NightSurface).padding(horizontal = 22.dp),
    verticalAlignment = Alignment.CenterVertically,
    horizontalArrangement = Arrangement.spacedBy(10.dp),
  ) {
    BrowserToolbarButton("← App", onExit)
    BrowserToolbarButton("‹ Back", onBack, enabled = canGoBack)
    BrowserToolbarButton("⌂ Home", onHome)
    BrowserToolbarButton("↻ Reload", onReload)
    Spacer(Modifier.width(8.dp))
    Column(Modifier.weight(1f)) {
      Text(title, color = SoftWhite, fontWeight = FontWeight.Bold, fontSize = 16.sp, maxLines = 1, overflow = TextOverflow.Ellipsis)
      Text(url, color = MutedBlue, fontSize = 11.sp, maxLines = 1, overflow = TextOverflow.Ellipsis)
    }
    Text("◆  $status", color = AuroraMint, fontWeight = FontWeight.Bold, fontSize = 12.sp)
      }
    }
  }
}

@Composable
private fun BrowserToolbarButton(label: String, onClick: () -> Unit, enabled: Boolean = true) {
  var focused by remember { mutableStateOf(false) }
  val scale by animateFloatAsState(if (focused) 1.06f else 1f, label = "browser button scale")
  val background by animateColorAsState(
    if (focused) SoftWhite else DeepSpace.copy(alpha = if (enabled) .78f else .35f),
    label = "browser button background",
  )
  val foreground = if (focused) DeepSpace else if (enabled) SoftWhite else MutedBlue.copy(alpha = .5f)
  Box(
    modifier =
      Modifier.graphicsLayer { scaleX = scale; scaleY = scale }.clip(RoundedCornerShape(10.dp)).background(background)
        .border(1.dp, if (focused) AuroraBlue else Color.Transparent, RoundedCornerShape(10.dp))
        .onFocusChanged { focused = it.isFocused }.clickable(enabled = enabled, onClick = onClick)
        .semantics { role = Role.Button; contentDescription = label }.padding(horizontal = 15.dp, vertical = 10.dp),
    contentAlignment = Alignment.Center,
  ) {
    Text(label, color = foreground, fontWeight = FontWeight.Bold, fontSize = 13.sp)
  }
}

private fun isWebUrl(url: String): Boolean {
  val scheme = url.toUri().scheme?.lowercase()
  return scheme == "http" || scheme == "https" || scheme == "about"
}

internal fun isHlsUrl(url: String): Boolean {
  val normalized = url.lowercase()
  if (
    normalized.contains(".m3u8") ||
      normalized.contains("format=m3u8") ||
      normalized.contains("mime=application%2fx-mpegurl")
  ) {
    return true
  }

  val parsed = runCatching { url.toUri() }.getOrNull() ?: return false
  val host = parsed.host?.lowercase().orEmpty()
  val path = parsed.path.orEmpty().lowercase()
  val renditionType = runCatching { parsed.getQueryParameter("type") }.getOrNull()

  // VixCloud serves its HLS master without a .m3u8 extension. Only capture the
  // master; its type=video/audio/subtitle children must stay inside Media3.
  return (host == "vixsrc.to" || host.endsWith(".vixsrc.to")) &&
    path.startsWith("/playlist/") && renditionType.isNullOrBlank()
}

private fun isExternalSubtitleUrl(url: String): Boolean = subtitleMimeType(url) != null

private fun isSupportedSubtitleCatalogUrl(url: String): Boolean {
  val parsed = runCatching { url.toUri() }.getOrNull() ?: return false
  val host = parsed.host?.lowercase().orEmpty()
  val path = parsed.path.orEmpty().lowercase()
  val knownCatalog =
    (host.endsWith("sub.vdrk.site") && (path.startsWith("/v1/movie/") || path.startsWith("/v1/tv/"))) ||
      (host == "api.bingr.one" && path.startsWith("/api/subtitles/"))
  val genericCatalog =
    (host.startsWith("api.") || path.contains("/api/")) &&
      (path.contains("subtitle") || path.contains("caption")) && !isExternalSubtitleUrl(url)
  return knownCatalog || genericCatalog
}

internal fun subtitleCatalogUrlsForPage(pageUrl: String): List<String> {
  subtitleCatalogUrlsForEmbeddedPlayer(pageUrl).takeIf { it.isNotEmpty() }?.let { return it }

  val page = runCatching { pageUrl.toUri() }.getOrNull() ?: return emptyList()
  if (page.host?.lowercase()?.let { it == "bingr.one" || it.endsWith(".bingr.one") } != true) return emptyList()
  val match = Regex("^/watch/(movie|tv)/(\\d+)", RegexOption.IGNORE_CASE).find(page.path.orEmpty()) ?: return emptyList()
  val type = match.groupValues[1].lowercase()
  val id = match.groupValues[2]
  val query =
    if (type == "tv") {
      val season = page.getQueryParameter("season")?.takeIf(String::isNotBlank)
      val episode =
        page.getQueryParameter("ep")?.takeIf(String::isNotBlank)
          ?: page.getQueryParameter("episode")?.takeIf(String::isNotBlank)
      listOfNotNull(season?.let { "season=${android.net.Uri.encode(it)}" }, episode?.let { "ep=${android.net.Uri.encode(it)}" })
        .joinToString("&").takeIf(String::isNotBlank)?.let { "?$it" }.orEmpty()
    } else {
      ""
    }
  return listOf("https://api.bingr.one/api/subtitles/vdrk/$type/$id$query")
}

internal fun subtitleCatalogUrlsForEmbeddedPlayer(playerUrl: String): List<String> {
  val player = runCatching { playerUrl.toUri() }.getOrNull() ?: return emptyList()
  val host = player.host?.lowercase().orEmpty()
  val supportedHost =
    host == "vidfast.vc" || host.endsWith(".vidfast.vc") ||
      host == "vixsrc.to" || host.endsWith(".vixsrc.to")
  if (!supportedHost) return emptyList()

  val path = player.path.orEmpty()
  Regex("^/movie/(\\d+)(?:/|$)", RegexOption.IGNORE_CASE).find(path)?.let { match ->
    return listOf("https://sub.vdrk.site/v1/movie/${match.groupValues[1]}")
  }
  Regex("^/tv/(\\d+)/(\\d+)/(\\d+)(?:/|$)", RegexOption.IGNORE_CASE).find(path)?.let { match ->
    val (_, showId, seasonNumber, episodeNumber) = match.groupValues
    return listOf("https://sub.vdrk.site/v1/tv/$showId/$seasonNumber/$episodeNumber")
  }
  return emptyList()
}

internal fun parseSubtitleCatalog(json: String): List<ExternalSubtitleTrack> {
  val root = runCatching { JSONTokener(json).nextValue() }.getOrNull()
  val entries =
    findSubtitleEntries(root)
  return buildList {
    for (index in 0 until entries.length()) {
      val item = entries.optJSONObject(index) ?: continue
      val label =
        listOf("label", "name", "language")
          .firstNotNullOfOrNull { key -> item.optString(key).trim().takeIf(String::isNotBlank) }
          .orEmpty()
      val language =
        listOf("lang", "language", "srclang", "languageCode")
          .firstNotNullOfOrNull { key -> item.optString(key).trim().takeIf(String::isNotBlank) }
      val isEnglish =
        label.contains("english", ignoreCase = true) || language.equals("en", ignoreCase = true) ||
          language.equals("eng", ignoreCase = true) || language?.startsWith("en-", ignoreCase = true) == true
      if (!isEnglish) continue
      val url =
        listOf("file", "url", "src")
          .firstNotNullOfOrNull { key -> item.optString(key).trim().takeIf(String::isNotBlank) }
          .orEmpty()
      createExternalSubtitleTrack(url, label.ifBlank { "English" }, "en")?.let(::add)
    }
  }.distinctBy { it.url }.sortedWith(compareBy({ englishSubtitleRank(it.label) }, { it.label.lowercase() }))
}

private fun findSubtitleEntries(value: Any?, depth: Int = 0): JSONArray {
  if (depth > 2) return JSONArray()
  if (value is JSONArray) return value
  if (value !is JSONObject) return JSONArray()
  listOf("subtitles", "captions", "tracks", "results", "items", "data").forEach { key ->
    value.optJSONArray(key)?.let { return it }
  }
  listOf("data", "result", "payload").forEach { key ->
    value.optJSONObject(key)?.let { nested ->
      findSubtitleEntries(nested, depth + 1).takeIf { it.length() > 0 }?.let { return it }
    }
  }
  return JSONArray()
}

internal fun downloadSubtitleCatalog(url: String, headers: Map<String, String>, userAgent: String): String {
  val connection = URL(url).openConnection() as HttpURLConnection
  return try {
    connection.requestMethod = "GET"
    connection.instanceFollowRedirects = true
    connection.connectTimeout = 10_000
    connection.readTimeout = 15_000
    connection.setRequestProperty("Accept", "application/json")
    connection.setRequestProperty("User-Agent", headers.headerValue("User-Agent") ?: userAgent)
    headers.headerValue("Referer")?.let { connection.setRequestProperty("Referer", it) }
    headers.headerValue("Origin")?.let { connection.setRequestProperty("Origin", it) }
    headers.headerValue("Cookie")?.let { connection.setRequestProperty("Cookie", it) }
    if (connection.responseCode !in 200..299) error("Subtitle catalog HTTP ${connection.responseCode}")
    connection.inputStream.bufferedReader(Charsets.UTF_8).use { it.readText() }
  } finally {
    connection.disconnect()
  }
}

private fun Map<String, String>.headerValue(name: String): String? =
  entries.firstOrNull { it.key.equals(name, ignoreCase = true) }?.value

internal fun completeStreamRequestHeaders(
  requestHeaders: Map<String, String>,
  userAgent: String,
  fallbackReferer: String,
  cookie: String?,
): Map<String, String> =
  requestHeaders.toMutableMap().apply {
    if (headerValue("User-Agent") == null) put("User-Agent", userAgent)
    if (headerValue("Referer") == null) put("Referer", fallbackReferer.ifBlank { HOME_URL })
    if (headerValue("Cookie") == null && !cookie.isNullOrBlank()) put("Cookie", cookie)
  }

private fun englishSubtitleRank(label: String): Int =
  when {
    label.equals("English", ignoreCase = true) -> 0
    label.startsWith("English", ignoreCase = true) && !label.contains("hi", ignoreCase = true) -> 1
    label.startsWith("English", ignoreCase = true) -> 2
    else -> 3
  }

private fun createExternalSubtitleTrack(url: String, label: String?, language: String?): ExternalSubtitleTrack? {
  val encodedUrl = android.net.Uri.encode(url, ":/?&=#%+;,")
  val mimeType = subtitleMimeType(encodedUrl) ?: return null
  val parsed = runCatching { encodedUrl.toUri() }.getOrNull() ?: return null
  val languageHint =
    language?.takeIf(String::isNotBlank)
      ?: listOf("lang", "language", "srclang").firstNotNullOfOrNull { key ->
        runCatching { parsed.getQueryParameter(key) }.getOrNull()?.takeIf(String::isNotBlank)
      }
  val normalized = encodedUrl.lowercase()
  val isEnglish =
    languageHint.equals("en", true) || languageHint.equals("eng", true) ||
      languageHint?.startsWith("en-", true) == true ||
      normalized.contains("english") || Regex("(^|[/_.?&=-])en(g)?([/_.?&=-]|$)").containsMatchIn(normalized)
  val resolvedLanguage = if (isEnglish) "en" else languageHint
  val filename = parsed.lastPathSegment?.substringBefore('?')?.take(48).orEmpty()
  val resolvedLabel = label?.takeIf(String::isNotBlank)?.take(48) ?: if (isEnglish) "English" else filename.ifBlank { "External subtitles" }
  return ExternalSubtitleTrack(url = encodedUrl, label = resolvedLabel, language = resolvedLanguage, mimeType = mimeType)
}

private fun subtitleMimeType(url: String): String? {
  val normalized = url.substringBefore('#').lowercase()
  return when {
    normalized.substringBefore('?').endsWith(".vtt") || normalized.contains("format=vtt") -> MimeTypes.TEXT_VTT
    normalized.substringBefore('?').endsWith(".srt") || normalized.contains("format=srt") -> MimeTypes.APPLICATION_SUBRIP
    normalized.substringBefore('?').endsWith(".ass") || normalized.substringBefore('?').endsWith(".ssa") -> MimeTypes.TEXT_SSA
    normalized.substringBefore('?').endsWith(".ttml") || normalized.substringBefore('?').endsWith(".dfxp") -> MimeTypes.APPLICATION_TTML
    else -> null
  }
}

private fun isBlockedUrl(url: String): Boolean {
  val parsed = runCatching { url.toUri() }.getOrNull() ?: return true
  val host = parsed.host?.lowercase().orEmpty()
  if (blockedHosts.any { host == it || host.endsWith(".$it") }) return true
  val normalized = url.lowercase()
  return blockedUrlFragments.any(normalized::contains)
}

private fun emptyResponse(): WebResourceResponse =
  WebResourceResponse("text/plain", "utf-8", ByteArrayInputStream(ByteArray(0)))
