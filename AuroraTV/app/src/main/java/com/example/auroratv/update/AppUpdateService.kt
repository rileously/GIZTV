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
      val updateDirectory = updateDirectory(context)
      val partialFile = File(updateDirectory, "giztv-${update.versionCode}.part.apk")
      val finalFile = verifiedUpdateFile(context, update.versionCode)
      try {
        check(updateDirectory.exists() || updateDirectory.mkdirs()) {
          "The update folder could not be created."
        }
        partialFile.delete()

        findVerifiedDownloadOnDisk(context, update)?.let { cachedFile ->
          withContext(Dispatchers.Main.immediate) { onProgress(100) }
          return@withContext Result.success(cachedFile)
        }

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
        Result.failure(error)
      }
    }

  /** Returns a previously downloaded update only after repeating every integrity check. */
  suspend fun findVerifiedDownload(context: Context, update: AppUpdateInfo): File? =
    withContext(Dispatchers.IO) { findVerifiedDownloadOnDisk(context, update) }

  /** Removes APKs that have already been installed, while keeping every newer retry candidate. */
  suspend fun removeInstalledDownloads(context: Context, currentVersionCode: Long) =
    withContext(Dispatchers.IO) {
      updateDirectory(context).listFiles().orEmpty().forEach { file ->
        val savedVersion =
          Regex("giztv-(\\d+)\\.apk").matchEntire(file.name)?.groupValues?.get(1)?.toLongOrNull()
        if (savedVersion != null && savedVersion <= currentVersionCode) file.delete()
        if (file.name.endsWith(".part.apk")) file.delete()
      }
    }

  private fun findVerifiedDownloadOnDisk(context: Context, update: AppUpdateInfo): File? {
    val file = restoreLegacyCachedUpdate(context, update.versionCode)
    if (!file.exists()) return null
    return runCatching {
        check(file.isFile && file.length() in 1..MAX_APK_BYTES) {
          "The saved update file size is invalid."
        }
        check(file.sha256().equals(update.sha256, ignoreCase = true)) {
          "The saved update failed its checksum check."
        }
        verifyDownloadedApk(context, file, update)
        file
      }
      .getOrElse {
        file.delete()
        null
      }
  }

  private fun restoreLegacyCachedUpdate(context: Context, versionCode: Long): File {
    val persistentFile = verifiedUpdateFile(context, versionCode)
    if (persistentFile.exists()) return persistentFile
    val legacyFile = File(File(context.cacheDir, "updates"), persistentFile.name)
    if (!legacyFile.isFile) return persistentFile
    return runCatching {
        check(persistentFile.parentFile?.let { it.exists() || it.mkdirs() } == true) {
          "The update folder could not be created."
        }
        legacyFile.copyTo(persistentFile, overwrite = true)
        legacyFile.delete()
        persistentFile
      }
      .getOrDefault(persistentFile)
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
    require(isAllowedUpdateUrl(urlText)) { "Updates require HTTPS." }
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

private fun updateDirectory(context: Context): File = File(context.filesDir, "updates")

private fun verifiedUpdateFile(context: Context, versionCode: Long): File =
  File(updateDirectory(context), "giztv-$versionCode.apk")

private fun File.sha256(): String {
  val digest = MessageDigest.getInstance("SHA-256")
  inputStream().buffered().use { input ->
    val buffer = ByteArray(DEFAULT_BUFFER_SIZE)
    while (true) {
      val count = input.read(buffer)
      if (count == -1) break
      digest.update(buffer, 0, count)
    }
  }
  return digest.digest().toHexString()
}

internal fun parseUpdateManifest(jsonText: String): AppUpdateInfo {
  val json = JSONObject(jsonText)
  val versionCode = json.getLong("versionCode")
  val versionName = json.getString("versionName").trim()
  val apkUrl = json.getString("apkUrl").trim()
  val sha256 = json.getString("sha256").trim().lowercase()
  require(versionCode > 0) { "The update version is invalid." }
  require(versionName.isNotEmpty()) { "The update version name is missing." }
  require(isAllowedUpdateUrl(apkUrl)) { "The APK URL must use HTTPS." }
  require(sha256.matches(Regex("[0-9a-f]{64}"))) { "The update checksum is invalid." }
  return AppUpdateInfo(
    versionCode = versionCode,
    versionName = versionName,
    apkUrl = apkUrl,
    sha256 = sha256,
    releaseNotes = json.optString("releaseNotes").trim(),
  )
}

/** Production feeds must use HTTPS; debug builds may use the emulator's host loopback for tests. */
private fun isAllowedUpdateUrl(urlText: String): Boolean {
  val uri = Uri.parse(urlText)
  if (uri.scheme.equals("https", ignoreCase = true)) return true
  return BuildConfig.DEBUG &&
    uri.scheme.equals("http", ignoreCase = true) &&
    (uri.host == "10.0.2.2" || uri.host == "localhost" || uri.host == "127.0.0.1")
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
