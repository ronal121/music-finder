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

        MusicServer("mojmusic.ir", 59), MusicServer("sarvnema.ir", 58), MusicServer("hailymusic.ir", 57),
        MusicServer("biya2ahang.ir", 56), MusicServer("radiomazani.com", 55), MusicServer("musickordi.com", 54),
        MusicServer("persianamusic.ir", 53), MusicServer("musicito.com", 52), MusicServer("mihanseda.com", 51),
        MusicServer("takmusics.com", 50), MusicServer("rubik-music.com", 49), MusicServer("shabamusic.com", 48),
        MusicServer("musicaz.ir", 47), MusicServer("azturk.ir", 46), MusicServer("delkash-music.ir", 45),
        MusicServer("4zarb.com", 44), MusicServer("iranmusicazin.ir", 43), MusicServer("musictag.ir", 42),
        MusicServer("melimusics.com", 41), MusicServer("download1music.ir", 40), MusicServer("talashdl.ir", 39),
        MusicServer("sorud.com", 38), MusicServer("fnanen.com", 37), MusicServer("hibamusic.com", 36),
        MusicServer("sevilmusics.com", 35), MusicServer("musicc.ir", 34), MusicServer("dornamusic.com", 33),
        MusicServer("textahang.com", 32), MusicServer("lyricsfa.com", 31), MusicServer("musicsara.com", 30),
        MusicServer("ahangestan.com", 29), MusicServer("musicg.ir", 28), MusicServer("musicfeed.ir", 27),
        MusicServer("musics4u.ir", 26), MusicServer("musicjoo.ir", 25), MusicServer("musiceiranian.ir", 24),
        MusicServer("persianhiphop.com", 23), MusicServer("rapfa.ir", 22), MusicServer("hiphopfa.com", 21),
        MusicServer("musicisho.com", 20), MusicServer("musicema.com", 19), MusicServer("navaar.ir", 18),
        MusicServer("musico.ir", 17), MusicServer("music-fa.ir", 16), MusicServer("ahangdl.ir", 15),
        MusicServer("ahang98.com", 14), MusicServer("ahangchi.com", 13), MusicServer("music-irani.ir", 12),
        MusicServer("iranmusicbox.com", 11), MusicServer("musicbaran.com", 10), MusicServer("musicsun.ir", 9),
        MusicServer("musictop.ir", 8), MusicServer("musicday.ir", 7), MusicServer("musicbaz.ir", 6),
        MusicServer("musicpouya.ir", 5), MusicServer("music98.ir", 4), MusicServer("musicparsi.ir", 3),
        MusicServer("musicsara.net", 2), MusicServer("download-music.ir", 1), MusicServer("ahangdownload.com", 0),
        MusicServer("musicsdownload.ir", -1), MusicServer("musiciranian.ir", -2), MusicServer("musiconline.ir", -3),
        MusicServer("ahangestan.ir", -4), MusicServer("musicplus.ir", -5), MusicServer("musicnavaz.com", -6)
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
        if (serverFor(host)?.supportsStreaming == true) return true
        return looksLikeAudioUrl(url)
    }

    fun looksLikeAudioUrl(url: String): Boolean {
        val l = url.lowercase()
        return listOf(
            ".mp3", ".m4a", ".aac", ".ogg", ".opus", ".wav", ".flac", ".webm",
            "audio/", "/download", "/dl/", "download.php", "getfile", "mediafile", ".mp4"
        ).any { l.contains(it) }
    }

    /**
     * Keep Google discovery broad. The app filters the returned links against
     * MUSIC_SITES in extractGoogleResults(). A huge domain list is kept here,
     * while direct native search intentionally remains limited to the primary
     * servers so the existing search flow is not slowed down or reordered.
     */
    fun searchQuery(song: String): String {
        val corrected = SearchEngine.correctedQuery(song).trim()
        if (corrected.isBlank()) return "music"

        val clean = SearchEngine.withoutSearchNoise(corrected)
            .replace(Regex("\\s+"), " ")
            .trim()
        if (clean.isBlank()) return "music"

        return if (clean.split(' ').size >= 4) {
            // Lyric fragments are often written with small textual variants.
            "$clean آهنگ دانلود"
        } else {
            val phrase = ""${clean.replace(""", " ").trim()}""
            "$phrase آهنگ دانلود"
        }
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
