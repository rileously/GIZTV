package com.giztv.tv.crash

import android.content.Context
import android.os.Build
import com.giztv.tv.BuildConfig
import com.giztv.tv.home.isTelevision
import java.io.PrintWriter
import java.io.StringWriter
import java.text.SimpleDateFormat
import java.util.Date
import java.util.Locale
import java.util.concurrent.atomic.AtomicBoolean

/**
 * Writes down what went wrong on the way out, so the next launch can offer to pass it on.
 *
 * Nothing is sent anywhere. The report is a file in the app's own storage until a viewer chooses to
 * share it, which keeps a media app that knows what somebody watches from quietly posting anything
 * about them, and keeps the whole thing working with no account and no server behind it.
 */
internal object CrashReporter {
  private val installed = AtomicBoolean(false)

  fun install(context: Context) {
    if (!installed.compareAndSet(false, true)) return
    val store = CrashReportStore(context)
    val television = runCatching { context.isTelevision() }.getOrDefault(false)
    val previous = Thread.getDefaultUncaughtExceptionHandler()
    Thread.setDefaultUncaughtExceptionHandler { thread, error ->
      // A failure in here must not replace the crash the viewer actually hit, and must not stop
      // the handler that was already in place from doing whatever it was going to do.
      runCatching { store.save(describeCrash(thread, error, television)) }
      previous?.uncaughtException(thread, error)
    }
  }
}

/** The facts worth having beside a stack trace, and nothing that identifies anybody. */
internal fun describeCrash(thread: Thread, error: Throwable, isTelevision: Boolean): String {
  val stamp =
    runCatching {
        SimpleDateFormat("yyyy-MM-dd HH:mm:ss z", Locale.US).format(Date(System.currentTimeMillis()))
      }
      .getOrDefault(System.currentTimeMillis().toString())
  val trace =
    runCatching {
        StringWriter().also { writer -> error.printStackTrace(PrintWriter(writer)) }.toString()
      }
      .getOrDefault(error.toString())
  return buildString {
    appendLine("GIZTV ${BuildConfig.VERSION_NAME} (${BuildConfig.VERSION_CODE})")
    appendLine("When      $stamp")
    appendLine("Android   ${Build.VERSION.RELEASE} (API ${Build.VERSION.SDK_INT})")
    appendLine("Device    ${Build.MANUFACTURER} ${Build.MODEL}")
    appendLine("Form      ${if (isTelevision) "television" else "phone"}")
    appendLine("Thread    ${thread.name}")
    appendLine()
    append(trace)
  }
}

/** Every report the viewer is sending, in one block of text. */
internal fun buildCrashReportBody(reports: List<CrashReport>): String =
  reports.joinToString(separator = "\n\n${"-".repeat(60)}\n\n") { it.text }

/** The one line a viewer is shown, rather than the whole trace. */
internal fun crashHeadline(report: CrashReport): String {
  val line =
    report.text
      .lineSequence()
      .dropWhile { !it.startsWith("Thread ") }
      .drop(2)
      .firstOrNull { it.isNotBlank() }
      ?.trim()
  return line?.takeIf { it.isNotEmpty() } ?: "GIZTV closed unexpectedly."
}
