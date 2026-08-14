package com.giztv.tv.crash

import android.content.Context
import java.io.File

/** How many crashes are kept. The newest is the one shown; the rest are for a bug report. */
private const val MAX_REPORTS = 5

/** A report larger than this is a runaway trace rather than something anyone will read. */
private const val MAX_REPORT_BYTES = 128 * 1024

internal data class CrashReport(val savedAtMs: Long, val text: String)

/**
 * The crashes this device has seen, written where the next launch can find them.
 *
 * GIZTV is not on the Play Store and has no console behind it, so a crash that is not written down
 * here leaves no trace at all: the viewer sees the app close and the person who could fix it never
 * hears anything more precise than "it stopped working".
 */
internal class CrashReportStore(context: Context) {
  private val directory = File(context.applicationContext.filesDir, "crashes")

  /** Called from a dying process, so it does the least it can and never throws. */
  fun save(text: String) {
    runCatching {
      if (!directory.exists() && !directory.mkdirs()) return
      File(directory, "crash-${System.currentTimeMillis()}.txt")
        .writeText(text.take(MAX_REPORT_BYTES))
      prune()
    }
  }

  /** The most recent crash, or null when this device has not had one. */
  fun latest(): CrashReport? =
    runCatching {
        reportFiles().lastOrNull()?.let { file ->
          CrashReport(savedAtMs = file.lastModified(), text = file.readText())
        }
      }
      .getOrNull()

  /** Every crash still on disk, oldest first, for a viewer sending the whole lot in. */
  fun all(): List<CrashReport> =
    runCatching {
        reportFiles().map { CrashReport(savedAtMs = it.lastModified(), text = it.readText()) }
      }
      .getOrDefault(emptyList())

  fun clear() {
    runCatching { directory.listFiles()?.forEach(File::delete) }
  }

  private fun reportFiles(): List<File> =
    directory
      .listFiles()
      .orEmpty()
      .filter { it.isFile && it.name.startsWith("crash-") }
      .sortedBy { it.lastModified() }

  private fun prune() {
    val files = reportFiles()
    if (files.size <= MAX_REPORTS) return
    files.dropLast(MAX_REPORTS).forEach(File::delete)
  }
}
