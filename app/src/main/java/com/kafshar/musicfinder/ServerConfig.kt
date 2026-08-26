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

    private val youtubeDomains = setOf(
        "youtube.com", "m.youtube.com", "youtu.be"
    )

    val SERVERS: List<MusicServer> = listOf(
        MusicServer("beroosic.ir", 110),
        MusicServer("rozmusic.com", 100), MusicServer("nex1music.com", 99),
        MusicServer("musicbaran.ir", 98), MusicServer("mymusicbaran.ir", 98),
        MusicServer("musicviral.ir", 97), MusicServer("musicdel.ir", 96),
        MusicServer("songsara.net", 95), MusicServer("radiojavan.com", 94),
        MusicServer("musicete.com", 93), MusicServer("musicetu.com", 93),
        MusicServer("musicsweb.ir", 92), MusicServer("melovy.ir", 91),
        MusicServer("jenab-music.com", 90), MusicServer("fazamusic.com", 89),
        MusicServer("360bikalam.com", 88), MusicServer("dtaraneh.net", 87),
        MusicServer("ahangirani.ir", 86), MusicServer("upmusics.com", 85),
        MusicServer("nicmusic.net", 84), MusicServer("vmusic.ir", 83),
        MusicServer("sakhamusic.ir", 82), MusicServer("ganja2music.com", 81),
        MusicServer("iran-music.net", 80), MusicServer("silamusic.ir", 79),
        MusicServer("bibakmusic.com", 78), MusicServer("beeptunes.com", 77),
        MusicServer("blogmusic.ir", 76), MusicServer("pop-music.ir", 75),
        MusicServer("behmusic.com", 74), MusicServer("irmp3.ir", 73),
        MusicServer("next1.ir", 72), MusicServer("mytehranmusic.com", 71),
        MusicServer("mybia2music.com", 70), MusicServer("musics-fa.com", 69),
        MusicServer("pro.iraniandj.ir", 68), MusicServer("worldofmusic.ir", 67),
        MusicServer("iranmusic.ir", 66), MusicServer("sahand-music.ir", 65),
        MusicServer("nakaman-music.ir", 64)
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

    /** Keep Google queries compact. A giant OR of 40+ site: filters is unreliable. */
    val PRIMARY_SEARCH_SITES: List<String>
        get() = SERVERS.filter { it.enabled && it.supportsSearch }
            .sortedByDescending { it.priority }
            .take(12)
            .map { it.domain }

    fun serverFor(host: String?): MusicServer? {
        val h = normalizeHost(host) ?: return null
        return SERVERS.firstOrNull { s ->
            hostMatchesDomain(h, s.domain) || s.mediaHosts.any { hostMatchesDomain(h, it) }
        }
    }

    fun serverForUrl(url: String?): MusicServer? = extractHttpHost(url)?.let(::serverFor)

    fun isMusicHost(host: String?): Boolean = serverFor(host)?.supportsStreaming == true

    fun isGoogleHost(host: String?): Boolean =
        hostMatchesDomain(normalizeHost(host).orEmpty(), GOOGLE_HOST)

    fun isYouTubeUrl(url: String?): Boolean =
        extractHttpHost(url)?.let(::isYouTubeHost) == true

    fun isYouTubeHost(host: String?): Boolean =
        youtubeDomains.any { hostMatchesDomain(normalizeHost(host).orEmpty(), it) }

    fun isAllowedPageUrl(url: String): Boolean {
        val host = extractHttpHost(url) ?: return false
        return isGoogleHost(host) || isYouTubeHost(host) || serverFor(host) != null
    }

    /**
     * Accept a media URL only when it is an approved music host or when it has
     * a strong audio-file signature. YouTube media URLs are never accepted.
     */
    fun isAllowedMediaUrl(url: String, pageUrl: String? = null): Boolean {
        val host = extractHttpHost(url) ?: return false
        if (isYouTubeHost(host)) return false

        val server = serverFor(host)
        if (server?.supportsStreaming == true) return true

        if (!looksLikeAudioUrl(url)) return false

        val parentHost = extractHttpHost(pageUrl)
        if (parentHost == null) return true

        return serverFor(parentHost)?.supportsSearch == true
    }

    fun looksLikeAudioUrl(url: String): Boolean {
        val l = url.lowercase()
        return listOf(
            ".mp3", ".m4a", ".aac", ".ogg", ".opus", ".wav", ".flac", ".webm",
            "audio/", "/download", "/dl/", "download.php", "getfile", "mediafile",
            ".mp4"
        ).any { l.contains(it) }
    }

    /**
     * Google works much more reliably with a small, high-quality site filter
     * than with a 40+ domain OR expression. The complete server list is still
     * retained for page/media validation.
     */
    fun searchQuery(song: String): String {
        val q = SearchEngine.correctedQuery(song)
            .trim()
            .replace(Regex("\\s+"), " ")

        if (q.isBlank()) return "music"

        val siteFilter = PRIMARY_SEARCH_SITES.joinToString(" OR ") { "site:$it" }
        return "$q ($siteFilter)"
    }

    fun siteName(url: String): String {
        val host = extractHttpHost(url) ?: return "Music"
        if (isYouTubeHost(host)) return "YouTube"
        return when {
            hostMatchesDomain(host, "beroosic.ir") -> "Beroosic"
            hostMatchesDomain(host, "rozmusic.com") -> "RozMusic"
            hostMatchesDomain(host, "musicviral.ir") -> "MusicViral"
            hostMatchesDomain(host, "nex1music.com") -> "Nex1Music"
            hostMatchesDomain(host, "musicbaran.ir") -> "MusicBaran"
            hostMatchesDomain(host, "mymusicbaran.ir") -> "MyMusicBaran"
            hostMatchesDomain(host, "musicdel.ir") -> "Musicdel"
            hostMatchesDomain(host, "songsara.net") -> "Songsara"
            hostMatchesDomain(host, "radiojavan.com") -> "Radio Javan"
            hostMatchesDomain(host, "musicete.com") -> "Musicete"
            hostMatchesDomain(host, "musicetu.com") -> "Musicetu"
            hostMatchesDomain(host, "musicsweb.ir") -> "MusicsWeb"
            hostMatchesDomain(host, "melovy.ir") -> "Melovy"
            hostMatchesDomain(host, "jenab-music.com") -> "Jenab Music"
            hostMatchesDomain(host, "fazamusic.com") -> "FazaMusic"
            hostMatchesDomain(host, "360bikalam.com") -> "360BiKalam"
            hostMatchesDomain(host, "dtaraneh.net") -> "DTaraneh"
            hostMatchesDomain(host, "ahangirani.ir") -> "Ahangirani"
            hostMatchesDomain(host, "upmusics.com") -> "UpMusics"
            hostMatchesDomain(host, "nicmusic.net") -> "NicMusic"
            hostMatchesDomain(host, "vmusic.ir") -> "VMusic"
            hostMatchesDomain(host, "sakhamusic.ir") -> "SakhaMusic"
            hostMatchesDomain(host, "ganja2music.com") -> "Ganja2Music"
            hostMatchesDomain(host, "iran-music.net") -> "Iran-Music"
            hostMatchesDomain(host, "silamusic.ir") -> "SilaMusic"
            hostMatchesDomain(host, "bibakmusic.com") -> "BibakMusic"
            hostMatchesDomain(host, "beeptunes.com") -> "Beeptunes"
            hostMatchesDomain(host, "blogmusic.ir") -> "BlogMusic"
            hostMatchesDomain(host, "pop-music.ir") -> "Pop-Music"
            hostMatchesDomain(host, "behmusic.com") -> "BehMusic"
            hostMatchesDomain(host, "irmp3.ir") -> "IRMP3"
            hostMatchesDomain(host, "next1.ir") -> "Next1"
            hostMatchesDomain(host, "mytehranmusic.com") -> "MyTehranMusic"
            hostMatchesDomain(host, "mybia2music.com") -> "Bia2Music"
            hostMatchesDomain(host, "musics-fa.com") -> "Musics-FA"
            hostMatchesDomain(host, "pro.iraniandj.ir") -> "IranianDJ Pro"
            hostMatchesDomain(host, "worldofmusic.ir") -> "World of Music"
            hostMatchesDomain(host, "iranmusic.ir") -> "IranMusic"
            hostMatchesDomain(host, "sahand-music.ir") -> "Sahand Music"
            hostMatchesDomain(host, "nakaman-music.ir") -> "Nakaman Music"
            else -> host.removePrefix("www.")
        }
    }

    private fun hostMatchesDomain(host: String, domain: String): Boolean {
        val h = normalizeHost(host) ?: return false
        val d = normalizeHost(domain) ?: return false
        return h == d || h.endsWith(".$d")
    }

    private fun normalizeHost(host: String?): String? = host
        ?.trim()
        ?.lowercase()
        ?.removePrefix("www.")
        ?.trimEnd('.')
        ?.takeIf { it.isNotBlank() }

    private fun extractHttpHost(url: String?): String? {
        if (url.isNullOrBlank()) return null
        val uri = try { URI(url.trim()) } catch (_: Exception) { return null }
        val scheme = uri.scheme?.lowercase() ?: return null
        if (scheme != "http" && scheme != "https") return null
        if (uri.userInfo != null || uri.rawAuthority.isNullOrBlank()) return null
        val host = uri.host?.takeIf { it.isNotBlank() }
            ?: fallbackHost(uri.rawAuthority)
            ?: return null
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
