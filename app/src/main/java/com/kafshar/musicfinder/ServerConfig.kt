package com.kafshar.musicfinder

import java.net.URI

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
        get() = SERVERS.asSequence()
            .filter { it.enabled }
            .flatMap { sequenceOf(it.domain) + it.mediaHosts.asSequence() }
            .mapNotNull(::normalizeHost)
            .toSet()

    val MUSIC_SITES: List<String>
        get() = SERVERS.asSequence()
            .filter { it.enabled && it.supportsSearch }
            .sortedByDescending { it.priority }
            .map { it.domain }
            .toList()

    fun serverFor(host: String?): MusicServer? {
        val normalized = normalizeHost(host) ?: return null

        return SERVERS.firstOrNull { server ->
            val domain = normalizeHost(server.domain) ?: return@firstOrNull false
            hostMatchesDomain(normalized, domain) ||
                server.mediaHosts.any { mediaHost ->
                    normalizeHost(mediaHost)?.let { hostMatchesDomain(normalized, it) } == true
                }
        }
    }

    fun serverForUrl(url: String?): MusicServer? =
        extractHttpHost(url)?.let(::serverFor)

    fun isMusicHost(host: String?): Boolean =
        serverFor(host)?.supportsStreaming == true

    fun isGoogleHost(host: String?): Boolean {
        val normalized = normalizeHost(host) ?: return false
        return hostMatchesDomain(normalized, GOOGLE_HOST)
    }

    fun isAllowedPageUrl(url: String): Boolean {
        val host = extractHttpHost(url) ?: return false
        return isGoogleHost(host) || serverFor(host)?.enabled == true
    }

    fun isAllowedMediaUrl(url: String): Boolean {
        val host = extractHttpHost(url) ?: return false
        val server = serverFor(host) ?: return false
        return server.enabled && server.supportsStreaming
    }

    fun searchQuery(song: String): String {
        val normalized = SearchEngine.normalizeQuery(song)
        val sites = MUSIC_SITES.joinToString(" OR ") { "site:$it" }
        return if (sites.isBlank()) normalized else "\"$normalized\" ($sites)"
    }

    fun siteName(url: String): String {
        val host = extractHttpHost(url) ?: return "Music"
        return when {
            hostMatchesDomain(host, "rozmusic.com") -> "RozMusic"
            hostMatchesDomain(host, "mybia2music.com") -> "Bia2Music"
            hostMatchesDomain(host, "musicdel.ir") -> "Musicdel"
            hostMatchesDomain(host, "musics-fa.com") -> "Musics-FA"
            hostMatchesDomain(host, "pro.iraniandj.ir") -> "IranianDJ Pro"
            hostMatchesDomain(host, "worldofmusic.ir") -> "World of Music"
            hostMatchesDomain(host, "iranmusic.ir") -> "IranMusic"
            hostMatchesDomain(host, "nicmusic.net") -> "NicMusic"
            hostMatchesDomain(host, "upmusics.com") -> "UpMusics"
            hostMatchesDomain(host, GOOGLE_HOST) -> "Google"
            else -> "Music"
        }
    }

    private fun hostMatchesDomain(host: String, domain: String): Boolean {
        val normalizedHost = normalizeHost(host) ?: return false
        val normalizedDomain = normalizeHost(domain) ?: return false
        return normalizedHost == normalizedDomain || normalizedHost.endsWith(".$normalizedDomain")
    }

    private fun normalizeHost(host: String?): String? = host
        ?.trim()
        ?.lowercase()
        ?.removePrefix("www.")
        ?.trimEnd('.')
        ?.takeIf { it.isNotBlank() }

    /**
     * Parses only HTTP(S) URLs and returns a normalized hostname.
     * Credentials, unsupported schemes, missing authorities and malformed
     * URLs are rejected before whitelist matching.
     */
    private fun extractHttpHost(url: String?): String? {
        if (url.isNullOrBlank()) return null

        val uri = try {
            URI(url.trim())
        } catch (_: Exception) {
            return null
        }

        val scheme = uri.scheme?.lowercase() ?: return null
        if (scheme != "http" && scheme != "https") return null
        if (uri.userInfo != null || uri.rawAuthority.isNullOrBlank()) return null

        val host = uri.host?.takeIf { it.isNotBlank() }
            ?: fallbackHost(uri.rawAuthority)
            ?: return null

        return normalizeHost(host)
    }

    private fun fallbackHost(authority: String): String? {
        val withoutCredentials = authority.substringAfterLast('@')

        if (withoutCredentials.startsWith("[")) {
            val closingBracket = withoutCredentials.indexOf(']')
            if (closingBracket > 1) {
                return withoutCredentials.substring(1, closingBracket)
            }
            return null
        }

        return withoutCredentials.substringBeforeLast(':')
            .takeIf { it.isNotBlank() }
    }
}
