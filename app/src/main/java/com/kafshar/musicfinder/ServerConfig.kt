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

    // Discovery is intentionally not limited to a hard-coded music-site list.
    // These legacy properties stay empty for source compatibility only.
    val SERVERS: List<MusicServer> = emptyList()
    val MUSIC_HOSTS: Set<String> get() = emptySet()
    val MUSIC_SITES: List<String> get() = emptyList()
    val PRIMARY_SEARCH_SITES: List<String> get() = emptyList()

    fun serverFor(host: String?): MusicServer? = null
    fun serverForUrl(url: String?): MusicServer? = null
    fun isMusicHost(host: String?): Boolean = false
    fun isGoogleHost(host: String?): Boolean =
        hostMatchesDomain(normalizeHost(host).orEmpty(), GOOGLE_HOST)
    fun isYouTubeUrl(url: String?): Boolean =
        extractHttpHost(url)?.let(::isYouTubeHost) == true
    fun isYouTubeHost(host: String?): Boolean =
        youtubeDomains.any { hostMatchesDomain(normalizeHost(host).orEmpty(), it) }

    // Any normal HTTP(S) page may be inspected. Source filtering is done by
    // media validation, not by a whitelist of music domains.
    fun isAllowedPageUrl(url: String): Boolean = extractHttpHost(url) != null

    // Candidate URLs discovered inside a page may have no file extension.
    // Obvious HTML/document URLs are rejected early; ambiguous URLs are passed
    // to probeMediaUrl(), which verifies the actual Content-Type before use.
    fun isAllowedMediaUrl(url: String, pageUrl: String? = null): Boolean {
        val host = extractHttpHost(url) ?: return false
        if (isYouTubeHost(host)) return false
        val lower = url.lowercase()
        val obviousNonMedia = listOf(
            ".html", ".htm", ".php", ".asp", ".aspx", ".jsp", ".json", ".xml",
            ".txt", ".css", ".js", ".jpg", ".jpeg", ".png", ".gif", ".webp", ".svg"
        ).any { lower.substringBefore('?').substringBefore('#').endsWith(it) }
        if (obviousNonMedia) return false
        return pageUrl != null || looksLikeAudioUrl(url)
    }

    fun looksLikeAudioUrl(url: String): Boolean {
        val l = url.lowercase()
        return listOf(
            ".mp3", ".m4a", ".aac", ".ogg", ".opus", ".wav", ".flac", ".webm",
            "audio/", "/download", "/dl/", "download.php", "getfile", "mediafile", ".mp4",
            "/stream", "/audio/", "/media/"
        ).any { l.contains(it) }
    }

    fun searchQuery(song: String): String =
        SearchEngine.correctedQuery(song).trim().ifBlank { "music" }

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

    private fun normalizeHost(host: String?): String? =
        host?.trim()?.lowercase()?.removePrefix("www.")?.trimEnd('.')
            ?.takeIf { it.isNotBlank() }

    private fun extractHttpHost(url: String?): String? {
        if (url.isNullOrBlank()) return null
        val uri = try { URI(url.trim()) } catch (_: Exception) { return null }
        val scheme = uri.scheme?.lowercase() ?: return null
        if (scheme != "http" && scheme != "https") return null
        if (uri.userInfo != null || uri.rawAuthority.isNullOrBlank()) return null
        val host = uri.host?.takeIf { it.isNotBlank() }
            ?: fallbackHost(uri.rawAuthority) ?: return null
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
