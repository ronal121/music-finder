package com.kafshar.musicfinder

import android.Manifest
import android.app.Activity
import android.content.Intent
import android.content.pm.PackageManager
import android.net.Uri
import android.os.Build
import android.os.Environment
import android.provider.MediaStore
import android.provider.Settings
import android.widget.Toast
import androidx.core.content.FileProvider
import org.json.JSONObject
import java.io.File
import java.io.FileOutputStream
import java.net.HttpURLConnection
import java.net.URL
import java.util.concurrent.ExecutorService
import java.util.concurrent.Executors

class InAppUpdater(private val activity: Activity) {
    private val executor: ExecutorService = Executors.newSingleThreadExecutor()
    private val releaseApi =
        "https://api.github.com/repos/ronal121/music-finder/releases/tags/ci-latest"

    fun checkAndInstall(onState: (message: String, installing: Boolean) -> Unit) {
        if (
            Build.VERSION.SDK_INT <= Build.VERSION_CODES.P &&
            activity.checkSelfPermission(Manifest.permission.WRITE_EXTERNAL_STORAGE) !=
            PackageManager.PERMISSION_GRANTED
        ) {
            onState("برای ذخیره APK در پوشه Download اجازه دسترسی به حافظه لازم است", false)
            activity.requestPermissions(
                arrayOf(Manifest.permission.WRITE_EXTERNAL_STORAGE),
                REQUEST_WRITE_STORAGE
            )
            return
        }

        onState("در حال بررسی آخرین Full CI…", false)
        executor.execute {
            try {
                val release = getJson(releaseApi)
                val assets = release.optJSONArray("assets")
                    ?: throw IllegalStateException("فایل آپدیت پیدا نشد")
                var apkUrl = ""
                var metadataUrl = ""
                for (i in 0 until assets.length()) {
                    val asset = assets.optJSONObject(i) ?: continue
                    val name = asset.optString("name")
                    val label = asset.optString("label")

                    if (
                        name == "music-finder-debug.apk" ||
                        name == "app-debug.apk" ||
                        label == "music-finder-debug.apk"
                    ) {
                        apkUrl = asset.optString("browser_download_url")
                    }

                    if (name == "update.json") {
                        metadataUrl = asset.optString("browser_download_url")
                    }
                }
                if (apkUrl.isBlank() || metadataUrl.isBlank()) {
                    throw IllegalStateException("نسخه قابل نصب پیدا نشد")
                }
                val metadata = getJson(metadataUrl)
                val remoteCommit = metadata.optString("commit").trim()
                val localCommit = BuildConfig.BUILD_COMMIT.trim()
                if (remoteCommit.isNotBlank() && remoteCommit == localCommit) {
                    activity.runOnUiThread {
                        onState("نسخه شما آخرین Full CI است", false)
                    }
                    return@execute
                }
                activity.runOnUiThread {
                    onState(
                        "آپدیت جدید ${metadata.optString("versionName").ifBlank { "موجود است" }}؛ در حال دانلود…",
                        true
                    )
                }

                val apk = downloadApkToDownloads(apkUrl)
                val packageInfo =
                    activity.packageManager.getPackageArchiveInfo(apk.file.absolutePath, 0)
                if (packageInfo == null || packageInfo.packageName != activity.packageName) {
                    deleteDownloadedApk(apk)
                    throw IllegalStateException("APK دانلودشده معتبر نیست")
                }

                activity.runOnUiThread {
                    onState("دانلود کامل شد؛ فایل در پوشه Download ذخیره شد؛ آماده نصب…", false)
                    installApk(apk.uri)
                }
            } catch (e: Exception) {
                activity.runOnUiThread {
                    onState(
                        "آپدیت انجام نشد: ${e.message ?: "خطای ناشناخته"}",
                        false
                    )
                }
            }
        }
    }

    private fun getJson(urlString: String): JSONObject {
        val connection =
            (URL(urlString).openConnection() as HttpURLConnection).apply {
                requestMethod = "GET"
                connectTimeout = 15000
                readTimeout = 20000
                setRequestProperty("Accept", "application/vnd.github+json")
                setRequestProperty("User-Agent", "Music-Finder-Updater")
            }
        return try {
            if (connection.responseCode !in 200..299) {
                throw IllegalStateException("GitHub HTTP ${connection.responseCode}")
            }
            connection.inputStream.use { input ->
                input.bufferedReader(Charsets.UTF_8).use { reader ->
                    JSONObject(reader.readText())
                }
            }
        } finally {
            connection.disconnect()
        }
    }

    private fun downloadApkToDownloads(urlString: String): DownloadedApk {
        val tempDirectory = File(activity.cacheDir, "updates").apply { mkdirs() }
        val tempFile = File(tempDirectory, "music-finder-latest.apk")
        downloadToFile(urlString, tempFile)

        return try {
            if (Build.VERSION.SDK_INT >= Build.VERSION_CODES.Q) {
                publishToPublicDownloads(tempFile)
            } else {
                val downloadsDirectory =
                    Environment.getExternalStoragePublicDirectory(Environment.DIRECTORY_DOWNLOADS)
                if (!downloadsDirectory.exists() && !downloadsDirectory.mkdirs()) {
                    throw IllegalStateException("پوشه Download ساخته نشد")
                }
                val target = File(downloadsDirectory, DOWNLOAD_FILE_NAME)
                if (target.exists() && !target.delete()) {
                    throw IllegalStateException("فایل قبلی آپدیت حذف نشد")
                }
                tempFile.copyTo(target, overwrite = true)
                DownloadedApk(
                    target,
                    FileProvider.getUriForFile(
                        activity,
                        "${activity.packageName}.fileprovider",
                        target
                    )
                )
            }
        } finally {
            tempFile.delete()
        }
    }

    private fun downloadToFile(urlString: String, target: File) {
        val connection =
            (URL(urlString).openConnection() as HttpURLConnection).apply {
                requestMethod = "GET"
                connectTimeout = 15000
                readTimeout = 60000
                instanceFollowRedirects = true
                setRequestProperty("User-Agent", "Music-Finder-Updater")
            }
        try {
            if (connection.responseCode !in 200..299) {
                throw IllegalStateException(
                    "دانلود APK خطا داد: HTTP ${connection.responseCode}"
                )
            }
            connection.inputStream.use { input ->
                FileOutputStream(target).use { output ->
                    val buffer = ByteArray(64 * 1024)
                    while (true) {
                        val count = input.read(buffer)
                        if (count <= 0) break
                        output.write(buffer, 0, count)
                    }
                    output.flush()
                }
            }
            if (!target.exists() || target.length() <= 0L) {
                throw IllegalStateException("فایل APK خالی است")
            }
        } catch (e: Exception) {
            target.delete()
            throw e
        } finally {
            connection.disconnect()
        }
    }

    private fun publishToPublicDownloads(tempFile: File): DownloadedApk {
        val resolver = activity.contentResolver
        val downloadsUri = MediaStore.Downloads.EXTERNAL_CONTENT_URI
        val relativePath = Environment.DIRECTORY_DOWNLOADS + "/"

        resolver.query(
            downloadsUri,
            arrayOf(MediaStore.MediaColumns._ID),
            "${MediaStore.MediaColumns.DISPLAY_NAME} = ? AND " +
                "${MediaStore.MediaColumns.RELATIVE_PATH} = ?",
            arrayOf(DOWNLOAD_FILE_NAME, relativePath),
            null
        )?.use { cursor ->
            while (cursor.moveToNext()) {
                val id = cursor.getLong(0)
                resolver.delete(Uri.withAppendedPath(downloadsUri, id.toString()), null, null)
            }
        }

        val values = android.content.ContentValues().apply {
            put(MediaStore.MediaColumns.DISPLAY_NAME, DOWNLOAD_FILE_NAME)
            put(MediaStore.MediaColumns.MIME_TYPE, "application/vnd.android.package-archive")
            put(MediaStore.MediaColumns.RELATIVE_PATH, relativePath)
            put(MediaStore.MediaColumns.IS_PENDING, 1)
        }

        val uri = resolver.insert(downloadsUri, values)
            ?: throw IllegalStateException("ذخیره APK در پوشه Download ممکن نشد")

        try {
            resolver.openOutputStream(uri)?.use { output ->
                tempFile.inputStream().use { input ->
                    input.copyTo(output, 64 * 1024)
                }
            } ?: throw IllegalStateException("باز کردن فایل Download ممکن نشد")

            values.clear()
            values.put(MediaStore.MediaColumns.IS_PENDING, 0)
            resolver.update(uri, values, null, null)

            val localCopy = File(activity.cacheDir, "updates/$DOWNLOAD_FILE_NAME")
            tempFile.copyTo(localCopy, overwrite = true)
            return DownloadedApk(localCopy, uri)
        } catch (e: Exception) {
            resolver.delete(uri, null, null)
            throw e
        }
    }

    private fun deleteDownloadedApk(apk: DownloadedApk) {
        if (Build.VERSION.SDK_INT >= Build.VERSION_CODES.Q) {
            activity.contentResolver.delete(apk.uri, null, null)
        } else {
            apk.file.delete()
        }
    }

    private fun installApk(uri: Uri) {
        if (Build.VERSION.SDK_INT >= Build.VERSION_CODES.O &&
            !activity.packageManager.canRequestPackageInstalls()
        ) {
            Toast.makeText(
                activity,
                "اجازه نصب از این منبع را فعال کن و دوباره آپدیت را بزن",
                Toast.LENGTH_LONG
            ).show()
            activity.startActivity(
                Intent(
                    Settings.ACTION_MANAGE_UNKNOWN_APP_SOURCES,
                    Uri.parse("package:${activity.packageName}")
                )
            )
            return
        }

        val intent = Intent(Intent.ACTION_VIEW).apply {
            setDataAndType(uri, "application/vnd.android.package-archive")
            addFlags(Intent.FLAG_GRANT_READ_URI_PERMISSION)
            addFlags(Intent.FLAG_ACTIVITY_NEW_TASK)
        }
        try {
            activity.startActivity(intent)
        } catch (_: Exception) {
            Toast.makeText(
                activity,
                "باز کردن نصب‌کننده اندروید ممکن نشد",
                Toast.LENGTH_LONG
            ).show()
        }
    }

    fun shutdown() {
        executor.shutdownNow()
    }

    private data class DownloadedApk(
        val file: File,
        val uri: Uri
    )

    companion object {
        private const val REQUEST_WRITE_STORAGE = 7401
        private const val DOWNLOAD_FILE_NAME = "MusicFinder-update.apk"
    }
}
