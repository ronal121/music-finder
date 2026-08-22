package com.kafshar.musicfinder

import android.net.Uri

/** Declarative registry for search/playback sources and their trust policy. */
data class MusicServer(
    val domain: String,
    val priority: Int,
    val enabled: Boolean = true,
    val supportsSearch: Boolean = true,
    val supportsStreaming: Boolean = true,
    val trusted: Boolean = true,
    val parserType: String = "web",
    val mediaHosts: Set<String> = emptySet()
)

object ServerConfig {
    const val GOOGLE_HOST = "google.com"

    val SERVERS: List<MusicServer> = listOf(
        MusicServer("rozmusic.com", 100),
        MusicServer("mybia2music.com", 95),
        MusicServer("musicdel.ir", 95),
        MusicServer("musics-fa.com", 90),
        MusicServer("pro.iraniandj.ir", 90),
        MusicServer("worldofmusic.ir", 85),
        MusicServer("iranmusic.ir", 85),
        MusicServer("nicmusic.net", 80),
        MusicServer("upmusics.com", 80)
    )

    val MUSIC_HOSTS: Set<String>
        get() = SERVERS.filter { it.enabled }.flatMap { listOf(it.domain) + it.mediaHosts }.toSet()

    val MUSIC_SITES: List<String>
        get() = SERVERS.filter { it.enabled && it.supportsSearch }
            .sortedByDescending { it.priority }
            .map { it.domain }

    fun serverFor(host: String?): MusicServer? {
        val normalized = normalizeHost(host) ?: return null
        return SERVERS.firstOrNull { server ->
            normalized == server.domain ||
                normalized.endsWith(".${server.domain}") ||
                server.mediaHosts.any { normalized == it || normalized.endsWith(".$it") }
        }
    }

    fun serverForUrl(url: String?): MusicServer? = parseHttpUri(url.orEmpty())?.let { serverFor(it.host) }

    fun isMusicHost(host: String?): Boolean = serverFor(host)?.supportsStreaming == true

    fun isGoogleHost(host: String?): Boolean {
        val normalized = normalizeHost(host) ?: return false
        return normalized == GOOGLE_HOST || normalized.endsWith(".$GOOGLE_HOST")
    }

    fun isAllowedPageUrl(url: String): Boolean = parseHttpUri(url)?.let {
        isGoogleHost(it.host) || serverFor(it.host)?.enabled == true
    } == true

    fun isAllowedMediaUrl(url: String): Boolean = parseHttpUri(url)?.let {
        val server = serverFor(it.host)
        server?.enabled == true && server.supportsStreaming
    } == true

    fun searchQuery(song: String): String {
        val normalized = SearchEngine.normalizeQuery(song)
        val sites = MUSIC_SITES.joinToString(" OR ") { "site:$it" }
        return if (sites.isBlank()) normalized else "\"$normalized\" ($sites)"
    }

    fun siteName(url: String): String {
        val host = normalizeHost(Uri.parse(url).host) ?: return "Music"
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
            else -> "Music"
        }
    }

    private fun normalizeHost(host: String?): String? = host?.trim()?.lowercase()?.removePrefix("www.")?.takeIf { it.isNotBlank() }

    private fun parseHttpUri(url: String): Uri? = try {
        Uri.parse(url).takeIf { it.scheme.equals("http", true) || it.scheme.equals("https", true) }
    } catch (_: Exception) { null }
}
