package com.kafshar.musicfinder

import android.net.Uri

object ServerConfig {
    const val GOOGLE_HOST = "google.com"

    val MUSIC_SITES = listOf(
        "rozmusic.com",
        "mybia2music.com",
        "musicdel.ir",
        "musics-fa.com",
        "pro.iraniandj.ir",
        "worldofmusic.ir",
        "iranmusic.ir",
        "nicmusic.net",
        "upmusics.com"
    )

    val MUSIC_HOSTS = MUSIC_SITES.toSet()

    fun normalizeHost(host: String?): String = host.orEmpty().lowercase().removePrefix("www.")

    fun isMusicHost(host: String?): Boolean {
        val normalized = normalizeHost(host)
        return MUSIC_HOSTS.any { normalized == it || normalized.endsWith(".$it") }
    }

    fun isGoogleHost(host: String?): Boolean {
        val normalized = normalizeHost(host)
        return normalized == GOOGLE_HOST || normalized.endsWith(".$GOOGLE_HOST")
    }

    fun isAllowedPageUrl(url: String): Boolean = try {
        val uri = Uri.parse(url)
        uri.scheme?.lowercase() in setOf("http", "https") &&
                (isGoogleHost(uri.host) || isMusicHost(uri.host))
    } catch (_: Exception) { false }

    fun isAllowedMediaUrl(url: String): Boolean = try {
        val uri = Uri.parse(url)
        uri.scheme?.lowercase() in setOf("http", "https") && isMusicHost(uri.host)
    } catch (_: Exception) { false }

    fun searchQuery(input: String): String {
        val q = input.trim().replace(Regex("\\s+"), " ")
        val sites = MUSIC_SITES.joinToString(" OR ") { "site:$it" }
        return "\"$q\" ($sites)"
    }

    fun siteName(url: String): String {
        val host = normalizeHost(runCatching { Uri.parse(url).host }.getOrNull())
        return when {
            host == "rozmusic.com" || host.endsWith(".rozmusic.com") -> "RozMusic"
            host == "mybia2music.com" || host.endsWith(".mybia2music.com") -> "Bia2Music"
            host == "musicdel.ir" || host.endsWith(".musicdel.ir") -> "Musicdel"
            host == "musics-fa.com" || host.endsWith(".musics-fa.com") -> "Musics-FA"
            host == "pro.iraniandj.ir" || host.endsWith(".pro.iraniandj.ir") -> "IranianDJ Pro"
            host == "worldofmusic.ir" || host.endsWith(".worldofmusic.ir") -> "World of Music"
            host == "iranmusic.ir" || host.endsWith(".iranmusic.ir") -> "IranMusic"
            host == "nicmusic.net" || host.endsWith(".nicmusic.net") -> "NicMusic"
            host == "upmusics.com" || host.endsWith(".upmusics.com") -> "UpMusics"
            else -> "سایت موسیقی"
        }
    }
}
