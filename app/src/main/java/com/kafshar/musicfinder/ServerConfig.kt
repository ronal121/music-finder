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

    // Keep the verified Run #33727462525 sources and add a wider set of
    // Iranian music/media sites plus Iranian sites that publish foreign music.
    val SERVERS: List<MusicServer> = listOf(
        MusicServer("beroosic.ir", 110), MusicServer("rozmusic.com", 100), MusicServer("nex1music.com", 99),
        MusicServer("musicbaran.ir", 98), MusicServer("mymusicbaran.ir", 98), MusicServer("musicviral.ir", 97),
        MusicServer("musicdel.ir", 96), MusicServer("songsara.net", 95), MusicServer("radiojavan.com", 94),
        MusicServer("musicete.com", 93), MusicServer("musicetu.com", 93), MusicServer("musicsweb.ir", 92),
        MusicServer("melovy.ir", 91), MusicServer("jenab-music.com", 90), MusicServer("fazamusic.com", 89),
        MusicServer("360bikalam.com", 88), MusicServer("dtaraneh.net", 87), MusicServer("ahangirani.ir", 86),
        MusicServer("upmusics.com", 85), MusicServer("nicmusic.net", 84), MusicServer("vmusic.ir", 83),
        MusicServer("sakhamusic.ir", 82), MusicServer("ganja2music.com", 81), MusicServer("iran-music.net", 80),
        MusicServer("silamusic.ir", 79), MusicServer("bibakmusic.com", 78), MusicServer("beeptunes.com", 77),
        MusicServer("blogmusic.ir", 76), MusicServer("pop-music.ir", 75), MusicServer("behmusic.com", 74),
        MusicServer("irmp3.ir", 73), MusicServer("next1.ir", 72), MusicServer("mytehranmusic.com", 71),
        MusicServer("mybia2music.com", 70), MusicServer("musics-fa.com", 69), MusicServer("pro.iraniandj.ir", 68),
        MusicServer("worldofmusic.ir", 67), MusicServer("iranmusic.ir", 66), MusicServer("sahand-music.ir", 65),
        MusicServer("nakaman-music.ir", 64), MusicServer("mokhtalefmusic.com", 63), MusicServer("joyamusic.ir", 62),
        MusicServer("gisomusic.com", 61), MusicServer("melomusic.ir", 60),

        MusicServer("songsun.ir", 59), MusicServer("musicito.com", 58), MusicServer("sound98.com", 57),
        MusicServer("farskids.com", 56), MusicServer("upsong.ir", 55), MusicServer("musictarin.com", 54),
        MusicServer("instamusic.ir", 53), MusicServer("musicmodern.ir", 52), MusicServer("takmusics.com", 51),
        MusicServer("musicfeed.ir", 50), MusicServer("mediak.ir", 49), MusicServer("kjmusic.ir", 48),
        MusicServer("enfamusic.ir", 47), MusicServer("ritmapp.ir", 46), MusicServer("musicbazz.ir", 45),
        MusicServer("faazmusic.com", 44), MusicServer("azlyrics.ir", 43), MusicServer("roohmusic.ir", 42),
        MusicServer("ahaang.com", 41), MusicServer("topseda.ir", 40), MusicServer("ir-music.ir", 39),
        MusicServer("dreamusic.ir", 38), MusicServer("ahang-baz.ir", 37), MusicServer("toptunes.ir", 36),
        MusicServer("beepmusic.org", 35), MusicServer("itarane.com", 34), MusicServer("musicmedia.ir", 33),
        MusicServer("musicsbaran.ir", 32), MusicServer("melovaz.net", 31), MusicServer("mp3lyric.us", 30)
    )

    val MUSIC_HOSTS: Set<String>
        get() = SERVERS.filter { it.enabled }
            .flatMap { listOf(it.domain) + it.mediaHosts }
            .mapNotNull(::normalizeHost)
            .toSet()

    val MUSIC_SITES: List<String>
        get() = SERVERS.filter { it.enabled && it.supportsSearch }
            .sortedByDescending { it.priority }
            .map { it.domain }

    val PRIMARY_SEARCH_SITES: List<String>
        get() = SERVERS.filter { it.enabled && it.supportsSearch }
            .sortedByDescending { it.priority }
            .take(16)
            .map { it.domain }

    fun serverFor(host: String?): MusicServer? {
        val h = normalizeHost(host) ?: return null
        return SERVERS.firstOrNull { s ->
            hostMatchesDomain(h, s.domain) || s.mediaHosts.any { hostMatchesDomain(h, it) }
        }
    }

    fun serverForUrl(url: String?): MusicServer? = extractHttpHost(url)?.let(::serverFor)
    fun isMusicHost(host: String?): Boolean = serverFor(host)?.supportsStreaming == true
    fun isGoogleHost(host: String?): Boolean = hostMatchesDomain(normalizeHost(host).orEmpty(), GOOGLE_HOST)
    fun isYouTubeUrl(url: String?): Boolean = extractHttpHost(url)?.let(::isYouTubeHost) == true
    fun isYouTubeHost(host: String?): Boolean = youtubeDomains.any { hostMatchesDomain(normalizeHost(host).orEmpty(), it) }

    fun isAllowedPageUrl(url: String): Boolean {
        val host = extractHttpHost(url) ?: return false
        return isGoogleHost(host) || isYouTubeHost(host) || serverFor(host) != null
    }

    fun isAllowedMediaUrl(url: String, pageUrl: String? = null): Boolean {
        val host = extractHttpHost(url) ?: return false
        if (isYouTubeHost(host)) return false
        if (isObviousNonMediaUrl(url)) return false
        if (serverFor(host)?.supportsStreaming == true) return true
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

    /** Keep the exact broad-discovery Google query from Fix Google search query to use broad discovery #412. */
    fun searchQuery(song: String): String {
        val corrected = SearchEngine.correctedQuery(song).trim()
        if (corrected.isBlank()) return "music"
        val clean = SearchEngine.withoutSearchNoise(corrected).replace(Regex("\\s+"), " ").trim()
        if (clean.isBlank()) return "music"
        val phrase = "\"${clean.replace("\"", " ").trim()}\""
        return "$phrase آهنگ دانلود"
    }

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
