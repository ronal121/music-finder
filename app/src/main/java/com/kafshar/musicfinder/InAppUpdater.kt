package com.kafshar.musicfinder

import android.app.Activity
import android.content.Intent
import android.net.Uri
import android.os.Build
import android.provider.Settings
import android.widget.Toast
import androidx.core.content.FileProvider
import org.json.JSONObject
import java.io.File
import java.io.FileOutputStream
import java.util.zip.ZipInputStream
import java.net.HttpURLConnection
import java.net.URL
import java.util.concurrent.ExecutorService
import java.util.concurrent.Executors

class InAppUpdater(private val activity: Activity) {
    private val executor: ExecutorService = Executors.newSingleThreadExecutor()
    private val releaseApi =
        "https://api.github.com/repos/ronal121/music-finder/releases/tags/ci-latest"

    fun checkAndInstall(onState: (message: String, installing: Boolean) -> Unit) {
        onState("در حال بررسی آخرین Full CI…", false)
        executor.execute {
            try {
                val release = getJson(releaseApi)
                val assets = release.optJSONArray("assets")
                    ?: throw IllegalStateException("فایل آپدیت پیدا نشد")
                var apkUrl = ""
                var zipUrl = ""
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

                    if (name == "music-finder-full-ci.zip") {
                        zipUrl = asset.optString("browser_download_url")
                    }

                    if (name == "update.json") {
                        metadataUrl = asset.optString("browser_download_url")
                    }
                }
                if (apkUrl.isBlank() && zipUrl.isBlank()) {
                    throw IllegalStateException("نسخه قابل نصب Full CI پیدا نشد")
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
                val apkFile =
                    try {
                        downloadApk(apkUrl)
                    } catch (directError: Exception) {
                        if (zipUrl.isBlank()) {
                            throw directError
                        }

                        val zipFile = downloadFile(zipUrl, "music-finder-full-ci.zip")
                        try {
                            extractApk(zipFile)
                        } finally {
                            zipFile.delete()
                        }
                    }

                val packageInfo =
                    activity.packageManager.getPackageArchiveInfo(apkFile.absolutePath, 0)
                if (packageInfo == null || packageInfo.packageName != activity.packageName) {
                    apkFile.delete()
                    throw IllegalStateException("APK دانلودشده معتبر نیست")
                }
                activity.runOnUiThread {
                    onState("دانلود کامل شد؛ آماده نصب…", false)
                    installApk(apkFile)
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

    private fun downloadApk(urlString: String): File {
        val directory = File(activity.cacheDir, "updates").apply { mkdirs() }
        val target = File(directory, "music-finder-latest.apk")
        return downloadFile(urlString, target.name)
    }

    private fun downloadFile(urlString: String, fileName: String): File {
        if (urlString.isBlank()) {
            throw IllegalStateException("لینک دانلود Full CI خالی است")
        }

        val directory = File(activity.cacheDir, "updates").apply { mkdirs() }
        val target = File(directory, fileName)
        target.delete()

        val connection =
            (URL(urlString).openConnection() as HttpURLConnection).apply {
                requestMethod = "GET"
                connectTimeout = 15000
                readTimeout = 90000
                instanceFollowRedirects = true
                setRequestProperty("User-Agent", "Music-Finder-Updater")
            }

        try {
            if (connection.responseCode !in 200..299) {
                throw IllegalStateException(
                    "دانلود Full CI خطا داد: HTTP ${connection.responseCode}"
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
                throw IllegalStateException("فایل دانلودشده خالی است")
            }

            return target
        } catch (e: Exception) {
            target.delete()
            throw e
        } finally {
            connection.disconnect()
        }
    }

    private fun extractApk(zipFile: File): File {
        val directory = File(activity.cacheDir, "updates").apply { mkdirs() }
        val apkFile = File(directory, "music-finder-latest.apk")
        apkFile.delete()

        ZipInputStream(zipFile.inputStream().buffered()).use { zip ->
            var entry = zip.nextEntry
            while (entry != null) {
                if (!entry.isDirectory && entry.name.endsWith(".apk", ignoreCase = true)) {
                    FileOutputStream(apkFile).use { output ->
                        val buffer = ByteArray(64 * 1024)
                        while (true) {
                            val count = zip.read(buffer)
                            if (count <= 0) break
                            output.write(buffer, 0, count)
                        }
                        output.flush()
                    }
                    break
                }
                zip.closeEntry()
                entry = zip.nextEntry
            }
        }

        if (!apkFile.exists() || apkFile.length() <= 0L) {
            throw IllegalStateException("داخل Full CI ZIP فایل APK پیدا نشد")
        }

        return apkFile
    }

    private fun installApk(apkFile: File) {
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

        val uri = FileProvider.getUriForFile(
            activity,
            "${activity.packageName}.fileprovider",
            apkFile
        )
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
}
