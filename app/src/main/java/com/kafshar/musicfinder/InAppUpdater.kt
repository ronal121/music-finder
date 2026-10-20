package com.kafshar.musicfinder

import android.app.Activity
import android.content.Intent
import android.content.pm.PackageInfo
import android.content.pm.PackageManager
import android.net.Uri
import android.os.Build
import android.provider.Settings
import android.util.Base64
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
        onState("در حال بررسی آخرین نسخه امضاشده…", false)
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
                    if (name == "music-finder.apk") apkUrl = asset.optString("browser_download_url")
                    if (name == "update.json") metadataUrl = asset.optString("browser_download_url")
                }
                if (apkUrl.isBlank() || metadataUrl.isBlank()) {
                    throw IllegalStateException(
                        "نسخهٔ امضاشده برای آپدیت منتشر نشده است؛ تنظیم امضای پایدار در GitHub لازم است"
                    )
                }

                val metadata = getJson(metadataUrl)
                val remoteCommit = metadata.optString("commit").trim()
                if (remoteCommit.isNotBlank() && remoteCommit == BuildConfig.BUILD_COMMIT.trim()) {
                    activity.runOnUiThread { onState("نسخه شما آخرین نسخه است", false) }
                    return@execute
                }

                activity.runOnUiThread {
                    onState(
                        "آپدیت جدید ${metadata.optString("versionName").ifBlank { "موجود است" }}؛ در حال دانلود…",
                        true
                    )
                }

                val apkFile = downloadApk(apkUrl)
                try {
                    validateUpgrade(apkFile)
                } catch (e: Exception) {
                    apkFile.delete()
                    throw e
                }

                activity.runOnUiThread {
                    onState("دانلود و بررسی کامل شد؛ نصب‌کنندهٔ اندروید باز می‌شود…", false)
                    installApk(apkFile)
                }
            } catch (e: Exception) {
                activity.runOnUiThread {
                    onState("آپدیت انجام نشد: ${e.message ?: "خطای ناشناخته"}", false)
                }
            }
        }
    }

    private fun validateUpgrade(apkFile: File) {
        val flags = if (Build.VERSION.SDK_INT >= Build.VERSION_CODES.P) {
            PackageManager.GET_SIGNING_CERTIFICATES
        } else {
            @Suppress("DEPRECATION")
            PackageManager.GET_SIGNATURES
        }
        val archiveInfo = activity.packageManager.getPackageArchiveInfo(apkFile.absolutePath, flags)
            ?: throw IllegalStateException("APK دانلودشده معتبر نیست")
        if (archiveInfo.packageName != activity.packageName) {
            throw IllegalStateException("شناسهٔ برنامه در APK با برنامهٔ نصب‌شده یکسان نیست")
        }
        val installedInfo = try {
            activity.packageManager.getPackageInfo(activity.packageName, flags)
        } catch (_: PackageManager.NameNotFoundException) {
            throw IllegalStateException("نسخهٔ نصب‌شده پیدا نشد")
        }

        if (archiveInfo.versionCode.toLong() <= installedInfo.versionCode.toLong()) {
            throw IllegalStateException(
                "این APK نسخهٔ جدیدتری نیست (نصب‌شده: ${installedInfo.versionCode}، دانلودشده: ${archiveInfo.versionCode})"
            )
        }

        val installedSigners = signerCertificates(installedInfo)
        val downloadedSigners = signerCertificates(archiveInfo)
        if (installedSigners.isEmpty() || downloadedSigners.isEmpty()) {
            throw IllegalStateException("بررسی گواهی امضای APK ممکن نشد؛ نصب برای ایمنی متوقف شد")
        }
        if (installedSigners != downloadedSigners) {
            throw IllegalStateException(
                "امضای APK با نسخهٔ نصب‌شده فرق دارد. نصب متوقف شد. برای آپدیت بدون حذف برنامه، نسخهٔ جدید باید با همان کلید امضای نسخهٔ فعلی ساخته شود."
            )
        }
    }

    @Suppress("DEPRECATION")
    private fun signerCertificates(packageInfo: PackageInfo): Set<String> {
        val signatures = if (Build.VERSION.SDK_INT >= Build.VERSION_CODES.P) {
            packageInfo.signingInfo?.apkContentsSigners
        } else {
            packageInfo.signatures
        }
        return signatures?.map { Base64.encodeToString(it.toByteArray(), Base64.NO_WRAP) }?.toSet().orEmpty()
    }

    private fun getJson(urlString: String): JSONObject {
        val connection = (URL(urlString).openConnection() as HttpURLConnection).apply {
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
                input.bufferedReader(Charsets.UTF_8).use { reader -> JSONObject(reader.readText()) }
            }
        } finally {
            connection.disconnect()
        }
    }

    private fun downloadApk(urlString: String): File {
        val directory = File(activity.cacheDir, "updates").apply { mkdirs() }
        val target = File(directory, "music-finder-latest.apk")
        val connection = (URL(urlString).openConnection() as HttpURLConnection).apply {
            requestMethod = "GET"
            connectTimeout = 15000
            readTimeout = 60000
            instanceFollowRedirects = true
            setRequestProperty("User-Agent", "Music-Finder-Updater")
        }
        try {
            if (connection.responseCode !in 200..299) {
                throw IllegalStateException("دانلود APK خطا داد: HTTP ${connection.responseCode}")
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
            if (!target.exists() || target.length() <= 0L) throw IllegalStateException("فایل APK خالی است")
            return target
        } catch (e: Exception) {
            target.delete()
            throw e
        } finally {
            connection.disconnect()
        }
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

        val uri = FileProvider.getUriForFile(activity, "${activity.packageName}.fileprovider", apkFile)
        val intent = Intent(Intent.ACTION_VIEW).apply {
            setDataAndType(uri, "application/vnd.android.package-archive")
            addFlags(Intent.FLAG_GRANT_READ_URI_PERMISSION)
            addFlags(Intent.FLAG_ACTIVITY_NEW_TASK)
        }
        try {
            activity.startActivity(intent)
        } catch (_: Exception) {
            Toast.makeText(activity, "باز کردن نصب‌کننده اندروید ممکن نشد", Toast.LENGTH_LONG).show()
        }
    }

    fun shutdown() {
        executor.shutdownNow()
    }
}
