package com.kafshar.musicfinder

import android.net.Uri

object ServerConfig {

    const val GOOGLE_HOST = "google.com"

    val MUSIC_HOSTS = setOf(
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

    fun isMusicHost(host: String?): Boolean {
        if (host.isNullOrBlank()) return false
        val normalized = host.lowercase().removePrefix("www.")
        return MUSIC_HOSTS.any { allowed ->
            normalized == allowed || normalized.endsWith(".$allowed")
        }
    }

    fun isGoogleHost(host: String?): Boolean {
        if (host.isNullOrBlank()) return false
        val normalized = host.lowercase().removePrefix("www.")
        return normalized == GOOGLE_HOST || normalized.endsWith(".$GOOGLE_HOST")
    }

    fun isAllowedPageUrl(url: String): Boolean {
        return try {
            val uri = Uri.parse(url)
            val scheme = uri.scheme?.lowercase()
            if (scheme != "http" && scheme != "https") return false
            isGoogleHost(uri.host) || isMusicHost(uri.host)
        } catch (_: Exception) {
            false
        }
    }

    fun isAllowedMediaUrl(url: String): Boolean {
        return try {
            val uri = Uri.parse(url)
            val scheme = uri.scheme?.lowercase()
            if (scheme != "http" && scheme != "https") return false
            isMusicHost(uri.host)
        } catch (_: Exception) {
            false
        }
    }

    fun searchQuery(song: String): String {
        val normalized = song.trim().replace(Regex("\\s+"), " ")
        val sites = MUSIC_SITES.joinToString(" OR ") { "site:$it" }
        return "\"$normalized\" ($sites)"
    }

    fun siteName(url: String): String {
        val host = try {
            Uri.parse(url).host?.lowercase()?.removePrefix("www.") ?: ""
        } catch (_: Exception) { "" }

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
