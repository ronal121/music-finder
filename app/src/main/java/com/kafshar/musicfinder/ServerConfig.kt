package com.kafshar.musicfinder

import java.net.InetAddress
import java.net.URI

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
    private val youtubeDomains = setOf("youtube.com", "m.youtube.com", "youtu.be")
    private val audioExtensions = setOf(".mp3", ".m4a", ".aac", ".ogg", ".opus", ".wav", ".flac", ".webm")
    private val obviousPageExtensions = setOf(".html", ".htm", ".json", ".xml", ".css", ".js", ".jpg", ".jpeg", ".png", ".gif", ".webp", ".svg", ".ico")

    val SERVERS: List<MusicServer> = emptyList()
    val MUSIC_HOSTS: Set<String> get() = emptySet()
    val MUSIC_SITES: List<String> get() = emptyList()
    val PRIMARY_SEARCH_SITES: List<String> get() = emptyList()

    fun serverFor(host: String?): MusicServer? = null
    fun serverForUrl(url: String?): MusicServer? = null
    fun isMusicHost(host: String?): Boolean = false

    fun isGoogleHost(host: String?): Boolean = hostMatchesDomain(normalizeHost(host).orEmpty(), GOOGLE_HOST)
    fun isYouTubeUrl(url: String?): Boolean = extractHttpHost(url)?.let(::isYouTubeHost) == true
    fun isYouTubeHost(host: String?): Boolean = youtubeDomains.any { hostMatchesDomain(normalizeHost(host).orEmpty(), it) }

    fun isAllowedPageUrl(url: String): Boolean = isPublicWebUrl(url)

    fun isPublicWebUrl(url: String): Boolean {
        val host = extractHttpHost(url) ?: return false
        return !isPrivateOrLocalHost(host)
    }

    fun isAllowedMediaUrl(url: String, pageUrl: String? = null): Boolean {
        val host = extractHttpHost(url) ?: return false
        if (isPrivateOrLocalHost(host) || isYouTubeHost(host)) return false
        if (isObviousNonMediaUrl(url)) return false
        if (pageUrl != null && isPublicWebUrl(pageUrl)) return true
        return looksLikeAudioUrl(url)
    }

    fun isObviousNonMediaUrl(url: String): Boolean {
        val path = url.substringBefore('?').substringBefore('#').lowercase()
        return obviousPageExtensions.any { path.endsWith(it) }
    }

    fun hasAudioExtension(url: String): Boolean {
        val path = url.substringBefore('?').substringBefore('#').lowercase()
        return audioExtensions.any { path.endsWith(it) }
    }

    fun looksLikeAudioUrl(url: String): Boolean {
        val l = url.lowercase()
        return hasAudioExtension(url) || listOf(
            "audio/", "/download", "/dl/", "download.php", "getfile", "mediafile",
            "/stream", "/audio/", "/media/", "mime=audio", "type=audio", ".m3u8", ".mpd"
        ).any { l.contains(it) }
    }

    fun searchQuery(song: String): String = SearchEngine.correctedQuery(song).trim().ifBlank { "music" }

    fun siteName(url: String): String {
        val host = extractHttpHost(url) ?: return "Music"
        if (isYouTubeHost(host)) return "YouTube"
        return host.removePrefix("www.")
    }

    private fun hostMatchesDomain(host: String, domain: String): Boolean {
        val h = normalizeHost(host) ?: return false
        val d = normalizeHost(domain) ?: return false
        return h == d || h.endsWith(".$d")
    }

    private fun normalizeHost(host: String?): String? = host?.trim()?.lowercase()?.removePrefix("www.")?.trimEnd('.')?.takeIf { it.isNotBlank() }

    private fun extractHttpHost(url: String?): String? {
        if (url.isNullOrBlank()) return null
        val uri = try { URI(url.trim()) } catch (_: Exception) { return null }
        val scheme = uri.scheme?.lowercase() ?: return null
        if (scheme != "http" && scheme != "https") return null
        if (uri.userInfo != null || uri.rawAuthority.isNullOrBlank()) return null
        val host = uri.host?.takeIf { it.isNotBlank() } ?: fallbackHost(uri.rawAuthority) ?: return null
        return normalizeHost(host)
    }

    private fun fallbackHost(authority: String): String? {
        val a = authority.substringAfterLast('@')
        if (a.startsWith("[")) {
            val end = a.indexOf(']')
            return if (end > 1) a.substring(1, end) else null
        }
        return a.substringBeforeLast(':').takeIf { it.isNotBlank() }
    }

    private fun isPrivateOrLocalHost(host: String): Boolean {
        val normalized = host.lowercase().trimEnd('.')
        if (normalized == "localhost" || normalized.endsWith(".localhost") || normalized == "broadcasthost") return true
        if (normalized == "0.0.0.0" || normalized == "::" || normalized == "[::1]" || normalized == "127.0.0.1") return true
        return try {
            InetAddress.getAllByName(normalized).any { address ->
                address.isLoopbackAddress || address.isAnyLocalAddress || address.isLinkLocalAddress || address.isSiteLocalAddress || isPrivateIpv4(address.hostAddress)
            }
        } catch (_: Exception) {
            false
        }
    }

    private fun isPrivateIpv4(value: String?): Boolean {
        val parts = value?.split('.') ?: return false
        if (parts.size != 4) return false
        val a = parts.mapNotNull { it.toIntOrNull() }
        if (a.size != 4) return false
        return a[0] == 10 || (a[0] == 172 && a[1] in 16..31) || (a[0] == 192 && a[1] == 168) || (a[0] == 169 && a[1] == 254)
    }
}
