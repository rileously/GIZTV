package com.example.auroratv.home

import android.content.Context
import android.content.Intent
import android.graphics.Bitmap
import androidx.compose.ui.unit.dp
import androidx.compose.ui.unit.sp
import androidx.glance.GlanceId
import androidx.glance.GlanceModifier
import androidx.glance.GlanceTheme
import androidx.glance.Image
import androidx.glance.ImageProvider
import androidx.glance.action.clickable
import androidx.glance.appwidget.GlanceAppWidget
import androidx.glance.appwidget.GlanceAppWidgetReceiver
import androidx.glance.appwidget.LinearProgressIndicator
import androidx.glance.appwidget.SizeMode
import androidx.glance.appwidget.action.actionStartActivity
import androidx.glance.appwidget.cornerRadius
import androidx.glance.appwidget.provideContent
import androidx.glance.appwidget.updateAll
import androidx.glance.background
import androidx.glance.layout.Alignment
import androidx.glance.layout.Box
import androidx.glance.layout.Column
import androidx.glance.layout.ContentScale
import androidx.glance.layout.Row
import androidx.glance.layout.Spacer
import androidx.glance.layout.fillMaxSize
import androidx.glance.layout.fillMaxWidth
import androidx.glance.layout.height
import androidx.glance.layout.padding
import androidx.glance.layout.size
import androidx.glance.layout.width
import androidx.glance.text.FontWeight
import androidx.glance.text.Text
import androidx.glance.text.TextStyle
import androidx.glance.unit.ColorProvider
import com.example.auroratv.MainActivity
import com.example.auroratv.data.WatchHistoryEntry
import com.example.auroratv.data.WatchHistoryStore
import com.example.auroratv.theme.AuroraMint
import com.example.auroratv.theme.DeepSpace
import com.example.auroratv.theme.MutedBlue
import com.example.auroratv.theme.NightSurface
import com.example.auroratv.theme.SoftWhite

/** Four rows fill the tallest sensible widget; the store is asked for no more than are drawn. */
private const val WIDGET_ROW_LIMIT = 4

private val POSTER_WIDTH = 42.dp
private val POSTER_HEIGHT = 60.dp

/**
 * The phone's home screen showing what the viewer has half-finished.
 *
 * A television has no widget host to place this on, and its own home screen carries the same titles
 * as a channel instead; see [ContinueWatchingChannel].
 */
internal class ContinueWatchingWidget : GlanceAppWidget() {
  // The row count is fixed and the layout stretches, so one composition serves every size the
  // launcher offers and the launcher is not asked to keep several around.
  override val sizeMode: SizeMode = SizeMode.Single

  override suspend fun provideGlance(context: Context, id: GlanceId) {
    val entries = WatchHistoryStore(context).continueWatchingAnywhere(limit = WIDGET_ROW_LIMIT)
    // Fetched before the content is provided, because Glance composes the whole widget into a
    // bundle in one pass and has nowhere to put an image that arrives afterwards.
    val posters = entries.mapNotNull { entry -> entry.posterUrl?.let { it to loadWidgetPoster(it) } }
    val postersByUrl = posters.mapNotNull { (url, bitmap) -> bitmap?.let { url to it } }.toMap()

    provideContent { GlanceTheme { WidgetBody(context, entries, postersByUrl) } }
  }
}

internal class ContinueWatchingWidgetReceiver : GlanceAppWidgetReceiver() {
  override val glanceAppWidget: GlanceAppWidget = ContinueWatchingWidget()
}

/** Redraws every placed widget. A device with none placed does nothing and costs nothing. */
internal suspend fun refreshContinueWatchingWidget(context: Context) {
  runCatching { ContinueWatchingWidget().updateAll(context) }
}

@androidx.compose.runtime.Composable
private fun WidgetBody(
  context: Context,
  entries: List<WatchHistoryEntry>,
  posters: Map<String, Bitmap>,
) {
  Column(
    modifier =
      GlanceModifier.fillMaxSize()
        .background(ColorProvider(DeepSpace))
        .cornerRadius(20.dp)
        .padding(horizontal = 14.dp, vertical = 12.dp)
        // Tapping the widget's own background opens the app rather than doing nothing.
        .clickable(actionStartActivity(Intent(context, MainActivity::class.java))),
  ) {
    Text(
      text = "CONTINUE WATCHING",
      style =
        TextStyle(
          color = ColorProvider(AuroraMint),
          fontSize = 11.sp,
          fontWeight = FontWeight.Bold,
        ),
    )
    Spacer(GlanceModifier.height(10.dp))

    if (entries.isEmpty()) {
      Box(modifier = GlanceModifier.fillMaxSize(), contentAlignment = Alignment.Center) {
        Text(
          text = "Nothing started yet — open GIZTV to find something.",
          style = TextStyle(color = ColorProvider(MutedBlue), fontSize = 13.sp),
        )
      }
      return@Column
    }

    entries.forEach { entry ->
      ContinueWatchingRow(context, entry, entry.posterUrl?.let(posters::get))
      Spacer(GlanceModifier.height(8.dp))
    }
  }
}

@androidx.compose.runtime.Composable
private fun ContinueWatchingRow(context: Context, entry: WatchHistoryEntry, poster: Bitmap?) {
  Row(
    modifier =
      GlanceModifier.fillMaxWidth()
        .background(ColorProvider(NightSurface))
        .cornerRadius(12.dp)
        .padding(8.dp)
        .clickable(actionStartActivity(resumeIntent(context, entry))),
    verticalAlignment = Alignment.CenterVertically,
  ) {
    Box(
      modifier =
        GlanceModifier.size(POSTER_WIDTH, POSTER_HEIGHT)
          .background(ColorProvider(DeepSpace))
          .cornerRadius(8.dp)
    ) {
      if (poster != null) {
        Image(
          provider = ImageProvider(poster),
          contentDescription = null,
          contentScale = ContentScale.Crop,
          modifier = GlanceModifier.fillMaxSize().cornerRadius(8.dp),
        )
      }
    }
    Spacer(GlanceModifier.width(10.dp))

    Column(modifier = GlanceModifier.defaultWeight()) {
      Text(
        text = entry.title,
        maxLines = 1,
        style =
          TextStyle(
            color = ColorProvider(SoftWhite),
            fontSize = 14.sp,
            fontWeight = FontWeight.Medium,
          ),
      )
      entry.subtitle?.takeIf(String::isNotBlank)?.let {
        Text(
          text = it,
          maxLines = 1,
          style = TextStyle(color = ColorProvider(MutedBlue), fontSize = 12.sp),
        )
      }
      Spacer(GlanceModifier.height(6.dp))
      ProgressBar(entry.progressFraction)
      entry.resumeLabel?.let {
        Spacer(GlanceModifier.height(4.dp))
        Text(text = it, style = TextStyle(color = ColorProvider(MutedBlue), fontSize = 11.sp))
      }
    }
  }
}

/** How far through the title the viewer is. */
@androidx.compose.runtime.Composable
private fun ProgressBar(fraction: Float) {
  LinearProgressIndicator(
    progress = fraction.coerceIn(0f, 1f),
    modifier = GlanceModifier.fillMaxWidth().height(3.dp),
    color = ColorProvider(AuroraMint),
    backgroundColor = ColorProvider(MutedBlue),
  )
}
