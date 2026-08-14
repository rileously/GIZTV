package com.giztv.tv.ui.crash

import android.content.ClipData
import android.content.ClipboardManager
import android.content.Context
import android.content.Intent
import androidx.activity.compose.BackHandler
import androidx.compose.foundation.background
import androidx.compose.foundation.focusGroup
import androidx.compose.foundation.layout.Arrangement
import androidx.compose.foundation.layout.Box
import androidx.compose.foundation.layout.Column
import androidx.compose.foundation.layout.Row
import androidx.compose.foundation.layout.Spacer
import androidx.compose.foundation.layout.fillMaxSize
import androidx.compose.foundation.layout.fillMaxWidth
import androidx.compose.foundation.layout.height
import androidx.compose.foundation.layout.padding
import androidx.compose.foundation.layout.widthIn
import androidx.compose.foundation.shape.RoundedCornerShape
import androidx.compose.runtime.Composable
import androidx.compose.runtime.LaunchedEffect
import androidx.compose.runtime.getValue
import androidx.compose.runtime.mutableStateOf
import androidx.compose.runtime.remember
import androidx.compose.runtime.setValue
import androidx.compose.ui.Alignment
import androidx.compose.ui.Modifier
import androidx.compose.ui.focus.FocusRequester
import androidx.compose.ui.focus.focusRequester
import androidx.compose.ui.platform.LocalContext
import androidx.compose.ui.platform.testTag
import androidx.compose.ui.text.font.FontWeight
import androidx.compose.ui.text.style.TextOverflow
import androidx.compose.ui.unit.dp
import androidx.compose.ui.unit.sp
import androidx.tv.material3.Text
import com.giztv.tv.crash.CrashReport
import com.giztv.tv.crash.CrashReportStore
import com.giztv.tv.crash.buildCrashReportBody
import com.giztv.tv.crash.crashHeadline
import com.giztv.tv.theme.DeepSpace
import com.giztv.tv.theme.GizMint
import com.giztv.tv.theme.MutedBlue
import com.giztv.tv.theme.NightSurface
import com.giztv.tv.theme.SoftWhite
import com.giztv.tv.ui.catalog.CatalogButton
import com.giztv.tv.ui.catalog.remoteFocusNavigation
import kotlinx.coroutines.Dispatchers
import kotlinx.coroutines.delay
import kotlinx.coroutines.withContext

/**
 * Offers the last crash to whoever is in a position to report it.
 *
 * Shown once per crash and then cleared either way: a viewer who is not interested should not meet
 * the same panel every time they open the app, and one who sends it in has already done the useful
 * thing. Reading from disk happens off the main thread so a launch is never held up by it.
 */
@Composable
internal fun CrashReportController() {
  val context = LocalContext.current
  val store = remember(context) { CrashReportStore(context) }
  var report by remember { mutableStateOf<CrashReport?>(null) }

  LaunchedEffect(store) {
    // A crash panel arriving over the top of a catalog that is still settling reads as a second
    // fault rather than a report of the first, so it waits for the screen to finish appearing.
    delay(600)
    report = withContext(Dispatchers.IO) { store.latest() }
  }

  val current = report ?: return
  CrashReportOverlay(
    report = current,
    onShare = {
      shareCrashReports(context, store.all().ifEmpty { listOf(current) })
      store.clear()
      report = null
    },
    onDismiss = {
      store.clear()
      report = null
    },
  )
}

@Composable
internal fun CrashReportOverlay(
  report: CrashReport,
  onShare: () -> Unit,
  onDismiss: () -> Unit,
) {
  val primaryFocus = remember { FocusRequester() }
  val secondaryFocus = remember { FocusRequester() }
  BackHandler { onDismiss() }

  LaunchedEffect(report) {
    delay(180)
    runCatching { primaryFocus.requestFocus() }
  }

  Box(
    modifier =
      Modifier.fillMaxSize()
        .focusGroup()
        .background(DeepSpace.copy(alpha = .96f))
        .padding(horizontal = 40.dp, vertical = 28.dp)
        .testTag("crash_overlay"),
    contentAlignment = Alignment.Center,
  ) {
    Column(
      modifier =
        Modifier.widthIn(max = 760.dp)
          .fillMaxWidth()
          .background(NightSurface, RoundedCornerShape(26.dp))
          .padding(horizontal = 42.dp, vertical = 34.dp),
    ) {
      Text(
        text = "GIZTV CLOSED UNEXPECTEDLY",
        color = GizMint,
        fontSize = 13.sp,
        fontWeight = FontWeight.Black,
        letterSpacing = 2.sp,
      )
      Spacer(Modifier.height(8.dp))
      Text(
        "Sorry about that",
        color = SoftWhite,
        fontSize = 32.sp,
        fontWeight = FontWeight.Black,
      )
      Spacer(Modifier.height(14.dp))
      Text(
        "Last time GIZTV was open it stopped with the fault below. Nothing has been sent " +
          "anywhere. Sending it in is what makes it fixable — it carries the fault, this app's " +
          "version and this device's model, and nothing about what you watch.",
        color = MutedBlue,
        fontSize = 15.sp,
        lineHeight = 22.sp,
      )
      Spacer(Modifier.height(18.dp))
      Text(
        crashHeadline(report),
        color = SoftWhite,
        fontSize = 15.sp,
        lineHeight = 22.sp,
        maxLines = 3,
        overflow = TextOverflow.Ellipsis,
      )

      Spacer(Modifier.height(26.dp))
      Row(
        horizontalArrangement = Arrangement.spacedBy(16.dp),
        verticalAlignment = Alignment.CenterVertically,
      ) {
        CatalogButton(
          label = "Send report",
          onClick = onShare,
          modifier =
            Modifier.focusRequester(primaryFocus)
              .remoteFocusNavigation(
                up = primaryFocus,
                down = primaryFocus,
                left = primaryFocus,
                right = secondaryFocus,
              )
              .testTag("crash_primary"),
        )
        CatalogButton(
          label = "No thanks",
          onClick = onDismiss,
          modifier =
            Modifier.focusRequester(secondaryFocus)
              .remoteFocusNavigation(
                up = secondaryFocus,
                down = secondaryFocus,
                left = primaryFocus,
                right = secondaryFocus,
              )
              .testTag("crash_dismiss"),
        )
      }
    }
  }
}

/**
 * Hands the reports to whatever the viewer wants to send them with.
 *
 * A television usually has nothing that answers a share, so the clipboard is the fallback rather
 * than the button doing nothing at all — the text can then be pasted wherever it is being reported.
 */
internal fun shareCrashReports(context: Context, reports: List<CrashReport>) {
  val body = buildCrashReportBody(reports)
  val intent =
    Intent(Intent.ACTION_SEND).apply {
      type = "text/plain"
      putExtra(Intent.EXTRA_SUBJECT, "GIZTV crash report")
      putExtra(Intent.EXTRA_TEXT, body)
    }
  val chooser = Intent.createChooser(intent, "Send crash report").apply {
    addFlags(Intent.FLAG_ACTIVITY_NEW_TASK)
  }
  val shared = runCatching { context.startActivity(chooser) }.isSuccess
  if (!shared) copyToClipboard(context, body)
}

private fun copyToClipboard(context: Context, body: String) {
  runCatching {
    val clipboard = context.getSystemService(Context.CLIPBOARD_SERVICE) as? ClipboardManager
    clipboard?.setPrimaryClip(ClipData.newPlainText("GIZTV crash report", body))
  }
}
