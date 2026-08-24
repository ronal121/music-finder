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
        MusicServer("rozmusic.com", 100), MusicServer("nex1music.com", 99),
        MusicServer("musicbaran.ir", 98), MusicServer("mymusicbaran.ir", 98),
        MusicServer("musicdel.ir", 97), MusicServer("songsara.net", 96),
        MusicServer("radiojavan.com", 95), MusicServer("musicete.com", 94),
        MusicServer("musicetu.com", 94), MusicServer("musicsweb.ir", 93),
        MusicServer("melovy.ir", 92), MusicServer("jenab-music.com", 91),
        MusicServer("fazamusic.com", 90), MusicServer("360bikalam.com", 89),
        MusicServer("dtaraneh.net", 88), MusicServer("ahangirani.ir", 87),
        MusicServer("upmusics.com", 86), MusicServer("nicmusic.net", 85),
        MusicServer("vmusic.ir", 84), MusicServer("sakhamusic.ir", 83),
        MusicServer("ganja2music.com", 82), MusicServer("iran-music.net", 81),
        MusicServer("silamusic.ir", 80), MusicServer("bibakmusic.com", 79),
        MusicServer("beeptunes.com", 78), MusicServer("blogmusic.ir", 77),
        MusicServer("pop-music.ir", 76), MusicServer("behmusic.com", 75),
        MusicServer("irmp3.ir", 74), MusicServer("next1.ir", 73),
        MusicServer("mytehranmusic.com", 72), MusicServer("mybia2music.com", 70),
        MusicServer("musics-fa.com", 69), MusicServer("pro.iraniandj.ir", 68),
        MusicServer("worldofmusic.ir", 67), MusicServer("iranmusic.ir", 66)
    )

    val MUSIC_HOSTS: Set<String>
        get() = SERVERS.asSequence().filter { it.enabled }
            .flatMap { sequenceOf(it.domain) + it.mediaHosts.asSequence() }
            .mapNotNull(::normalizeHost).toSet()

    val MUSIC_SITES: List<String>
        get() = SERVERS.asSequence().filter { it.enabled && it.supportsSearch }
            .sortedByDescending { it.priority }.map { it.domain }.toList()

    fun serverFor(host: String?): MusicServer? {
        val normalized = normalizeHost(host) ?: return null
        return SERVERS.firstOrNull { server ->
            val domain = normalizeHost(server.domain) ?: return@firstOrNull false
            hostMatchesDomain(normalized, domain) || server.mediaHosts.any { mediaHost ->
                normalizeHost(mediaHost)?.let { hostMatchesDomain(normalized, it) } == true
            }
        }
    }

    fun serverForUrl(url: String?): MusicServer? = extractHttpHost(url)?.let(::serverFor)
    fun isMusicHost(host: String?): Boolean = serverFor(host)?.supportsStreaming == true

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

    /**
     * Build a Google query that strongly prefers the configured music sources.
     *
     * The old query was only "$song music". Google could therefore spend the
     * first result pages on YouTube, Spotify, Wikipedia, lyrics sites, etc.
     * MainActivity intentionally filters those URLs out, which often resulted
     * in zero usable results. We now add a compact OR group of the highest
     * priority configured domains. The complete server registry is still kept
     * for filtering and playback; only the Google discovery query is narrowed.
     */
    fun searchQuery(song: String): String {
        val normalized = SearchEngine.correctedQuery(song)
            .trim().replace(Regex("\\s+"), " ")
        if (normalized.isBlank()) return "music"

        val domains = SERVERS.asSequence()
            .filter { it.enabled && it.supportsSearch }
            .sortedByDescending { it.priority }
            .take(12)
            .map { "site:${it.domain}" }
            .joinToString(" OR ")

        return if (domains.isBlank()) {
            "$normalized music"
        } else {
            "($normalized) music ($domains)"
        }
    }

    fun siteName(url: String): String {
        val host = extractHttpHost(url) ?: return "Music"
        return when {
            hostMatchesDomain(host, "rozmusic.com") -> "RozMusic"
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
            hostMatchesDomain(host, GOOGLE_HOST) -> "Google"
            else -> "Music"
        }
    }

    private fun hostMatchesDomain(host: String, domain: String): Boolean {
        val normalizedHost = normalizeHost(host) ?: return false
        val normalizedDomain = normalizeHost(domain) ?: return false
        return normalizedHost == normalizedDomain || normalizedHost.endsWith(".$normalizedDomain")
    }

    private fun normalizeHost(host: String?): String? = host?.trim()?.lowercase()
        ?.removePrefix("www.")?.trimEnd('.')?.takeIf { it.isNotBlank() }

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
        val withoutCredentials = authority.substringAfterLast('@')
        if (withoutCredentials.startsWith("[")) {
            val closingBracket = withoutCredentials.indexOf(']')
            if (closingBracket > 1) return withoutCredentials.substring(1, closingBracket)
            return null
        }
        return withoutCredentials.substringBeforeLast(':').takeIf { it.isNotBlank() }
    }
}
