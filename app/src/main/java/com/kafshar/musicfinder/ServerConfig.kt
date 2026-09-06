package com.kafshar.musicfinder

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

    // Discovery is intentionally not limited to a hard-coded music-site list.
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

    /** Any normal HTTP(S) page discovered by the search engine may be inspected. */
    fun isAllowedPageUrl(url: String): Boolean = extractHttpHost(url) != null

    /**
     * Candidate URLs are intentionally permissive when they came from a page.
     * Actual reachability/type checks belong to probeMediaUrl(). This prevents
     * legitimate extensionless streams and application/octet-stream responses
     * from being discarded before probing.
     */
    fun isAllowedMediaUrl(url: String, pageUrl: String? = null): Boolean {
        val host = extractHttpHost(url) ?: return false
        if (isYouTubeHost(host)) return false
        if (isObviousNonMediaUrl(url)) return false
        if (pageUrl != null && extractHttpHost(pageUrl) != null) return true
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
            ".mp4", "/stream", "/audio/", "/media/", "mime=audio", "type=audio"
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
}
