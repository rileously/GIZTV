@file:Suppress("RestrictedApi")

package com.example.auroratv.home

import android.content.Context
import android.content.Intent
import android.content.pm.PackageManager
import android.graphics.Bitmap
import android.graphics.BitmapFactory
import android.net.Uri
import android.os.Build
import android.util.Log
import androidx.annotation.RequiresApi
import androidx.tvprovider.media.tv.PreviewChannel
import androidx.tvprovider.media.tv.PreviewChannelHelper
import androidx.tvprovider.media.tv.PreviewProgram
import androidx.tvprovider.media.tv.TvContractCompat
import androidx.tvprovider.media.tv.WatchNextProgram
import com.example.auroratv.MainActivity
import com.example.auroratv.R
import com.example.auroratv.data.WatchHistoryEntry
import com.example.auroratv.data.WatchHistoryStore
import kotlinx.coroutines.Dispatchers
import kotlinx.coroutines.withContext

private const val CHANNEL_INTERNAL_ID = "giztv_continue_watching"
private const val LOG_TAG = "GizTvHome"

/**
 * A row of half-finished titles on the television's own home screen.
 *
 * This is what a widget would be if a television had anywhere to put one. The launcher owns the row:
 * the app publishes a channel, the viewer adds it once, and from then on the titles sit beside the
 * other apps' rows without GIZTV being opened at all.
 */
internal object ContinueWatchingChannel {

  /**
   * Republishes the row from the current history.
   *
   * Programs are replaced wholesale rather than reconciled one by one. The row holds at most a
   * handful of titles, and their order changes every time something is watched, so working out the
   * difference costs more than writing the lot.
   */
  suspend fun refresh(context: Context) {
    // Preview channels arrived with Android O; an older television simply has no such row.
    if (Build.VERSION.SDK_INT < Build.VERSION_CODES.O) return
    if (!context.isTelevision()) return
    withContext(Dispatchers.IO) {
      // The TV provider is absent on anything that is not a television, and a locked-down one can
      // refuse the write outright. Neither is worth taking the app down for, but a row that quietly
      // never appears is impossible to account for without being told why.
      runCatching { publish(context) }
        .onFailure { Log.w(LOG_TAG, "Continue watching row not published", it) }
    }
  }

  @RequiresApi(Build.VERSION_CODES.O)
  private fun publish(context: Context) {
    val helper = PreviewChannelHelper(context)
    val entries = WatchHistoryStore(context).continueWatchingAnywhere()
    publishWatchNext(context, helper, entries)
    val channelId = findOrCreateChannel(context, helper) ?: return

    // Emptying the channel's own collection in one call, rather than reading the programs back to
    // delete them one at a time.
    context.contentResolver.delete(
      TvContractCompat.buildPreviewProgramsUriForChannel(channelId),
      null,
      null,
    )
    entries.forEach { entry ->
      runCatching { helper.publishPreviewProgram(previewProgram(context, channelId, entry)) }
    }
  }

  /**
   * Puts the same titles into the television's own "Continue watching" row.
   *
   * Two rows, because two generations of launcher. The older Android TV home draws a channel the
   * viewer has added; Google TV dropped those in favour of one system row it fills from Watch Next,
   * and an app that publishes only a channel is invisible on it. Watch Next needs no channel and no
   * approval, so on a Google TV this is the row that actually appears.
   */
  @RequiresApi(Build.VERSION_CODES.O)
  private fun publishWatchNext(
    context: Context,
    helper: PreviewChannelHelper,
    entries: List<WatchHistoryEntry>,
  ) {
    // Titles that have since been finished must leave the row, and the row is small, so it is
    // rewritten rather than reconciled.
    context.contentResolver.delete(TvContractCompat.WatchNextPrograms.CONTENT_URI, null, null)
    entries.forEach { entry ->
      runCatching { helper.publishWatchNextProgram(watchNextProgram(context, entry)) }
    }
  }

  @RequiresApi(Build.VERSION_CODES.O)
  private fun watchNextProgram(context: Context, entry: WatchHistoryEntry): WatchNextProgram =
    WatchNextProgram.Builder()
      .setType(
        if (entry.episodeNumber != null) TvContractCompat.WatchNextPrograms.TYPE_TV_EPISODE
        else TvContractCompat.WatchNextPrograms.TYPE_MOVIE
      )
      // "Continue" is the half-finished pile, as opposed to the next episode of something finished
      // or something the viewer has only added to a list.
      .setWatchNextType(TvContractCompat.WatchNextPrograms.WATCH_NEXT_TYPE_CONTINUE)
      .setLastEngagementTimeUtcMillis(entry.updatedAtMs)
      .setTitle(entry.title)
      .setDescription(entry.subtitle.orEmpty())
      .setPosterArtUri(entry.posterUrl?.let(Uri::parse))
      .setPosterArtAspectRatio(TvContractCompat.WatchNextPrograms.ASPECT_RATIO_2_3)
      .setDurationMillis(entry.durationMs.toInt())
      .setLastPlaybackPositionMillis(entry.positionMs.toInt())
      .setInternalProviderId(entry.pageUrl)
      .setIntent(resumeIntent(context, entry))
      .build()

  @RequiresApi(Build.VERSION_CODES.O)
  private fun findOrCreateChannel(context: Context, helper: PreviewChannelHelper): Long? {
    helper.allChannels.firstOrNull { it.internalProviderId == CHANNEL_INTERNAL_ID }?.let {
      return it.id
    }
    val channel =
      PreviewChannel.Builder()
        .setDisplayName(context.getString(R.string.continue_watching_channel_name))
        .setDescription(context.getString(R.string.continue_watching_channel_description))
        .setInternalProviderId(CHANNEL_INTERNAL_ID)
        .setAppLinkIntent(Intent(context, MainActivity::class.java))
        // A channel with no logo is still a perfectly good channel, so a mark that will not decode
        // is not worth abandoning the row over.
        .apply { context.channelLogo()?.let(::setLogo) }
        .build()
    // The first channel an app publishes is offered to the launcher's home row automatically;
    // any further one has to be added by the viewer from the launcher's own settings.
    return runCatching { helper.publishDefaultChannel(channel) }.getOrNull()
  }

  @RequiresApi(Build.VERSION_CODES.O)
  private fun previewProgram(
    context: Context,
    channelId: Long,
    entry: WatchHistoryEntry,
  ): PreviewProgram =
    PreviewProgram.Builder()
      .setChannelId(channelId)
      .setType(
        if (entry.episodeNumber != null) TvContractCompat.PreviewPrograms.TYPE_TV_EPISODE
        else TvContractCompat.PreviewPrograms.TYPE_MOVIE
      )
      .setTitle(entry.title)
      .setDescription(entry.subtitle.orEmpty())
      .setPosterArtUri(entry.posterUrl?.let(Uri::parse))
      .setPosterArtAspectRatio(TvContractCompat.PreviewPrograms.ASPECT_RATIO_2_3)
      // The launcher draws its own progress line from these, so the row shows how far in the
      // viewer got without the app having to write it into the description.
      .setDurationMillis(entry.durationMs.toInt())
      .setLastPlaybackPositionMillis(entry.positionMs.toInt())
      .setInternalProviderId(entry.pageUrl)
      .setIntent(resumeIntent(context, entry))
      .build()

  /** The launcher wants a square mark for the row's own heading. */
  private fun Context.channelLogo(): Bitmap? =
    runCatching { BitmapFactory.decodeResource(resources, R.drawable.giztv_mark) }.getOrNull()
}

internal fun Context.isTelevision(): Boolean =
  packageManager.hasSystemFeature(PackageManager.FEATURE_LEANBACK)
