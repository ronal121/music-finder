package com.kafshar.musicfinder

import java.io.BufferedInputStream
import java.net.HttpURLConnection
import java.net.URI
import java.net.URL

object MediaProbe {
    enum class Type { DIRECT_AUDIO, HLS, DASH, VIDEO, HTML, REDIRECT, UNKNOWN }

    data class Result(
        val url: String,
        val finalUrl: String,
        val type: Type,
        val mime: String,
        val status: Int,
        val contentLength: Long = -1L,
        val playable: Boolean = false
    )

    fun probe(url: String, pageUrl: String? = null): Result {
        if (!ServerConfig.isAllowedMediaUrl(url, pageUrl)) return Result(url, url, Type.UNKNOWN, "", 0)
        val head = request(url, pageUrl, "HEAD")
        if (head != null && isUseful(head)) return head
        val range = request(url, pageUrl, "GET")
        return range ?: Result(url, url, Type.UNKNOWN, "", 0)
    }

    private fun isUseful(result: Result): Boolean = result.playable || result.type == Type.HTML || result.type == Type.VIDEO

    private fun request(url: String, pageUrl: String?, method: String): Result? {
        var connection: HttpURLConnection? = null
        return try {
            connection = (URL(url).openConnection() as HttpURLConnection).apply {
                requestMethod = method
                instanceFollowRedirects = true
                connectTimeout = 4000
                readTimeout = 5000
                useCaches = false
                setRequestProperty("User-Agent", SearchNetwork.USER_AGENT)
                setRequestProperty("Accept", "audio/*,application/vnd.apple.mpegurl,application/dash+xml,application/octet-stream,*/*;q=0.4")
                pageUrl?.takeIf { it.isNotBlank() }?.let { setRequestProperty("Referer", it) }
                android.webkit.CookieManager.getInstance().getCookie(url)?.takeIf { it.isNotBlank() }?.let { setRequestProperty("Cookie", it) }
                if (method == "GET") setRequestProperty("Range", "bytes=0-4095")
            }
            val code = connection.responseCode
            if (code !in 200..399) return null
            val finalUrl = connection.url?.toString().orEmpty().ifBlank { url }
            if (!ServerConfig.isPublicWebUrl(finalUrl)) return null
            val mime = connection.contentType?.substringBefore(';')?.trim()?.lowercase().orEmpty()
            val length = connection.contentLengthLong
            val sniff = if (method == "GET") sniff(connection) else ByteArray(0)
            classify(url, finalUrl, mime, sniff, code, length)
        } catch (_: Exception) {
            null
        } finally {
            try { connection?.disconnect() } catch (_: Exception) { }
        }
    }

    private fun sniff(connection: HttpURLConnection): ByteArray {
        return try {
            BufferedInputStream(connection.inputStream).use { input ->
                val buffer = ByteArray(4096)
                val count = input.read(buffer)
                if (count <= 0) ByteArray(0) else buffer.copyOf(count)
            }
        } catch (_: Exception) { ByteArray(0) }
    }

    private fun classify(url: String, finalUrl: String, mime: String, bytes: ByteArray, status: Int, length: Long): Result {
        val m = mime.lowercase()
        val text = bytes.toString(Charsets.UTF_8).trimStart().lowercase()
        val html = m == "text/html" || m.contains("xhtml") || text.startsWith("<!doctype html") || text.startsWith("<html") || text.startsWith("<head")
        if (html) return Result(url, finalUrl, Type.HTML, m, status, length, false)
        val hls = m == "application/vnd.apple.mpegurl" || m == "application/x-mpegurl" || text.startsWith("#extm3u")
        if (hls) return Result(url, finalUrl, Type.HLS, m, status, length, true)
        val dash = m == "application/dash+xml" || finalUrl.substringBefore('?').endsWith(".mpd", true)
        if (dash) return Result(url, finalUrl, Type.DASH, m, status, length, true)
        val video = m.startsWith("video/") || m == "application/x-mpegurl+video"
        if (video) return Result(url, finalUrl, Type.VIDEO, m, status, length, false)
        val audioMime = m.startsWith("audio/") || m == "application/octet-stream" || m == "binary/octet-stream"
        val signature = isAudioSignature(bytes)
        val extension = ServerConfig.hasAudioExtension(finalUrl)
        if (audioMime || signature || extension) return Result(url, finalUrl, Type.DIRECT_AUDIO, m, status, length, true)
        return Result(url, finalUrl, Type.UNKNOWN, m, status, length, false)
    }

    private fun isAudioSignature(bytes: ByteArray): Boolean {
        if (bytes.size >= 3 && bytes[0] == 'I'.code.toByte() && bytes[1] == 'D'.code.toByte() && bytes[2] == '3'.code.toByte()) return true
        if (bytes.size >= 4 && bytes[0] == 'f'.code.toByte() && bytes[1] == 'L'.code.toByte() && bytes[2] == 'a'.code.toByte() && bytes[3] == 'C'.code.toByte()) return true
        if (bytes.size >= 4 && bytes[0] == 'O'.code.toByte() && bytes[1] == 'g'.code.toByte() && bytes[2] == 'g'.code.toByte() && bytes[3] == 'S'.code.toByte()) return true
        if (bytes.size >= 4 && bytes[0] == 'R'.code.toByte() && bytes[1] == 'I'.code.toByte() && bytes[2] == 'F'.code.toByte() && bytes[3] == 'F'.code.toByte()) return true
        return bytes.size >= 2 && bytes[0] == 0xFF.toByte() && (bytes[1].toInt() and 0xE0) == 0xE0
    }
}
