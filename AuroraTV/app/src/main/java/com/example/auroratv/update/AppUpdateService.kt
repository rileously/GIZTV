package com.example.auroratv.update

import android.content.Context
import android.content.Intent
import android.content.pm.PackageInfo
import android.content.pm.PackageManager
import android.net.Uri
import android.os.Build
import androidx.core.content.FileProvider
import com.example.auroratv.BuildConfig
import java.io.ByteArrayOutputStream
import java.io.File
import java.net.HttpURLConnection
import java.net.URL
import java.nio.charset.StandardCharsets
import java.security.MessageDigest
import kotlinx.coroutines.Dispatchers
import kotlinx.coroutines.withContext
import org.json.JSONObject

internal data class AppUpdateInfo(
  val versionCode: Long,
  val versionName: String,
  val apkUrl: String,
  val sha256: String,
  val releaseNotes: String,
)

/** Downloads only signed, same-package upgrades published by the configured release feed. */
internal object AppUpdateService {
  private const val MAX_MANIFEST_BYTES = 512 * 1024
  private const val MAX_APK_BYTES = 750L * 1024L * 1024L
  private const val CONNECT_TIMEOUT_MS = 15_000
  private const val READ_TIMEOUT_MS = 30_000

  suspend fun checkForUpdate(manifestUrl: String, currentVersionCode: Long): Result<AppUpdateInfo?> =
    withContext(Dispatchers.IO) {
      try {
        val manifest = readManifest(manifestUrl)
        Result.success(manifest.takeIf { it.versionCode > currentVersionCode })
      } catch (error: Throwable) {
        Result.failure(error)
      }
    }

  suspend fun downloadAndVerify(
    context: Context,
    update: AppUpdateInfo,
    onProgress: suspend (Int?) -> Unit,
  ): Result<File> =
    withContext(Dispatchers.IO) {
      val updateDirectory = File(context.cacheDir, "updates")
      val partialFile = File(updateDirectory, "giztv-${update.versionCode}.part.apk")
      val finalFile = File(updateDirectory, "giztv-${update.versionCode}.apk")
      try {
        check(updateDirectory.exists() || updateDirectory.mkdirs()) {
          "The update folder could not be created."
        }
        partialFile.delete()
        finalFile.delete()

        val connection = openConnection(update.apkUrl)
        try {
          val responseCode = connection.responseCode
          check(responseCode in 200..299) { "The update server returned HTTP $responseCode." }
          val expectedLength = connection.getHeaderField("Content-Length")?.toLongOrNull()
          if (expectedLength != null) {
            check(expectedLength in 1..MAX_APK_BYTES) { "The update file size is invalid." }
          }

          val digest = MessageDigest.getInstance("SHA-256")
          var copied = 0L
          var lastProgress = -1
          connection.inputStream.buffered().use { input ->
            partialFile.outputStream().buffered().use { output ->
              val buffer = ByteArray(DEFAULT_BUFFER_SIZE)
              while (true) {
                val count = input.read(buffer)
                if (count == -1) break
                copied += count
                check(copied <= MAX_APK_BYTES) { "The update file is too large." }
                output.write(buffer, 0, count)
                digest.update(buffer, 0, count)
                val progress =
                  expectedLength?.takeIf { it > 0 }?.let { ((copied * 100L) / it).toInt().coerceIn(0, 100) }
                if (progress != lastProgress) {
                  lastProgress = progress ?: -1
                  withContext(Dispatchers.Main.immediate) { onProgress(progress) }
                }
              }
            }
          }
          check(copied > 0L) { "The update download was empty." }
          val actualSha256 = digest.digest().toHexString()
          check(actualSha256.equals(update.sha256, ignoreCase = true)) {
            "The downloaded update failed its checksum check."
          }
        } finally {
          connection.disconnect()
        }

        verifyDownloadedApk(context, partialFile, update)
        check(partialFile.renameTo(finalFile)) { "The verified update could not be prepared." }
        withContext(Dispatchers.Main.immediate) { onProgress(100) }
        Result.success(finalFile)
      } catch (error: Throwable) {
        partialFile.delete()
        finalFile.delete()
        Result.failure(error)
      }
    }

  private fun readManifest(manifestUrl: String): AppUpdateInfo {
    val connection = openConnection(manifestUrl)
    try {
      val responseCode = connection.responseCode
      check(responseCode in 200..299) { "The update server returned HTTP $responseCode." }
      val bytes =
        connection.inputStream.buffered().use { input ->
          val output = ByteArrayOutputStream()
          val buffer = ByteArray(DEFAULT_BUFFER_SIZE)
          var total = 0
          while (true) {
            val count = input.read(buffer)
            if (count == -1) break
            total += count
            check(total <= MAX_MANIFEST_BYTES) { "The update information is too large." }
            output.write(buffer, 0, count)
          }
          output.toByteArray()
        }
      return parseUpdateManifest(String(bytes, StandardCharsets.UTF_8))
    } finally {
      connection.disconnect()
    }
  }

  private fun openConnection(urlText: String): HttpURLConnection {
    val url = URL(urlText)
    require(url.protocol.equals("https", ignoreCase = true)) { "Updates require HTTPS." }
    return (url.openConnection() as HttpURLConnection).apply {
      instanceFollowRedirects = true
      connectTimeout = CONNECT_TIMEOUT_MS
      readTimeout = READ_TIMEOUT_MS
      requestMethod = "GET"
      setRequestProperty("Accept", "application/json, application/vnd.android.package-archive")
      setRequestProperty("User-Agent", "GIZTV/${BuildConfig.VERSION_NAME}")
    }
  }

  @Suppress("DEPRECATION")
  internal fun verifyDownloadedApk(context: Context, file: File, update: AppUpdateInfo) {
    val packageManager = context.packageManager
    val signingFlag =
      if (Build.VERSION.SDK_INT >= Build.VERSION_CODES.P) {
        PackageManager.GET_SIGNING_CERTIFICATES
      } else {
        PackageManager.GET_SIGNATURES
      }
    val archive =
      packageManager.getPackageArchiveInfo(file.absolutePath, signingFlag)
        ?: error("The downloaded file is not a valid Android app.")
    check(archive.packageName == context.packageName) {
      "The downloaded update belongs to a different app."
    }
    check(archive.longVersionCodeCompat() == update.versionCode) {
      "The downloaded update has an unexpected version."
    }
    val installed = packageManager.getPackageInfo(context.packageName, signingFlag)
    val installedCertificates = installed.signingCertificateDigests()
    val archiveCertificates = archive.signingCertificateDigests()
    check(installedCertificates.isNotEmpty() && archiveCertificates.isNotEmpty()) {
      "The update signature could not be verified."
    }
    check(installedCertificates.any(archiveCertificates::contains)) {
      "The downloaded update is not signed by this app."
    }
  }
}

internal fun parseUpdateManifest(jsonText: String): AppUpdateInfo {
  val json = JSONObject(jsonText)
  val versionCode = json.getLong("versionCode")
  val versionName = json.getString("versionName").trim()
  val apkUrl = json.getString("apkUrl").trim()
  val sha256 = json.getString("sha256").trim().lowercase()
  require(versionCode > 0) { "The update version is invalid." }
  require(versionName.isNotEmpty()) { "The update version name is missing." }
  require(Uri.parse(apkUrl).scheme.equals("https", ignoreCase = true)) { "The APK URL must use HTTPS." }
  require(sha256.matches(Regex("[0-9a-f]{64}"))) { "The update checksum is invalid." }
  return AppUpdateInfo(
    versionCode = versionCode,
    versionName = versionName,
    apkUrl = apkUrl,
    sha256 = sha256,
    releaseNotes = json.optString("releaseNotes").trim(),
  )
}

internal fun buildInstallIntent(context: Context, apkFile: File): Intent {
  val uri =
    FileProvider.getUriForFile(
      context,
      "${BuildConfig.APPLICATION_ID}.updates",
      apkFile,
    )
  return Intent(Intent.ACTION_VIEW).apply {
    setDataAndType(uri, "application/vnd.android.package-archive")
    addFlags(Intent.FLAG_GRANT_READ_URI_PERMISSION)
  }
}

@Suppress("DEPRECATION")
private fun PackageInfo.longVersionCodeCompat(): Long =
  if (Build.VERSION.SDK_INT >= Build.VERSION_CODES.P) longVersionCode else versionCode.toLong()

@Suppress("DEPRECATION")
private fun PackageInfo.signingCertificateDigests(): Set<String> {
  val signatures =
    if (Build.VERSION.SDK_INT >= Build.VERSION_CODES.P) {
      val info = signingInfo ?: return emptySet()
      if (info.hasMultipleSigners()) info.apkContentsSigners else info.signingCertificateHistory
    } else {
      signatures
    }
  return signatures.orEmpty().mapTo(mutableSetOf()) {
    MessageDigest.getInstance("SHA-256").digest(it.toByteArray()).toHexString()
  }
}

private fun ByteArray.toHexString(): String = joinToString(separator = "") { "%02x".format(it) }
