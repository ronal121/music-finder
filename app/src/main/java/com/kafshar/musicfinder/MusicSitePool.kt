package com.kafshar.musicfinder

/**
 * Search pool used by Google discovery.
 * The pool is deliberately domain-based: Google decides which page inside each
 * domain is relevant to the requested song, then the normal page/audio extractor
 * decides whether that page actually contains a playable media URL.
 */
object MusicSitePool {
    val domains: List<String> = listOf(
        "musicdel.ir", "nex1music.com", "next1.ir", "music-fa.com", "musicfa.co",
        "upmusics.com", "upsong.ir", "ahangirani.ir", "ahaang.com", "radio3da.com",
        "shabamusic.com", "mahanmusic.net", "mahanmusic.com", "takmusic.org", "takmusic.ir",
        "meloyab.com", "meloyab.ir", "songironi.ir", "song-new.ir", "newsong.ir",
        "musicisho.com", "blogmusic.ir", "musicmedia.ir", "ir-music.ir", "iranmusic.ir",
        "iran-music.net", "irmp3.ir", "muzicir.com", "muzicfa.ir", "musics-fa.com",
        "musico.ir", "musiceman.com", "musicemoon.com", "musicbaran.org", "musicbaran.net",
        "joyamusic.ir", "parvamusic.ir", "sarimusic.net", "shirazsong.in", "farstarane.com",
        "balmusic.ir", "naslemusic.com", "tabamusic.com", "tabtaraneh.net", "topseda.ir",
        "montiego.ir", "itarrane.com", "itarane.com", "kingmp3.ir", "foodmusic.ir",
        "poptarane.ir", "kolbemusic.ir", "newahang.com", "farsi1mp3.com", "persian-music2.com",
        "pm30music.com", "pm30music.ir", "hamedan-music.ir", "ghadim-music.ir", "naghmemusic.ir",
        "golsarmusic.ir", "bir-music.com", "sonarmusic.ir", "bibakmusic.com", "msbmusic.ir",
        "webahang.ir", "shirazsong.com", "madarmusic.ir", "irhits.ir", "irhits.com",
        "mrtehran.com", "irantunez.com", "aloonak.com", "mypmcmusic.com", "melobit.com",
        "behtarmusic.com", "beeptunes.com", "vavmusic.com", "muzicir.com", "darvishmusic.com",
        "bamiseda.ir", "iranmusic.net", "iranmusic.info", "iranianmusic.ir", "iranianmusic.com",
        "musicirani.com", "musiciranian.com", "iranian-music.com", "musicpersia.net", "persianmusic.net",
        "persianmusic.ir", "persianmusic.com", "persianmusic.org", "persian-song.com", "persian-song.ir",
        "persianmp3.net", "persianmp3.ir", "persianmp3.com", "farsimusic.net", "farsimusic.ir",
        "farsimusic.com", "farsimusic.org", "farsimp3.ir", "farsimp3.com", "ahangweb.ir",
        "ahangweb.com", "ahangdl.ir", "ahangdl.com", "ahangdownload.ir", "ahangdownload.com",
        "downloadahang.ir", "downloadahang.com", "downloadmusic.ir", "downloadmusic.com", "musicdownload.ir",
        "musicdownload.com", "ahang98.ir", "ahang98.com", "ahangino.ir", "ahangino.com",
        "ahangcity.ir", "ahangcity.com", "ahangestan.ir", "ahangestan.com", "ahangrooz.ir",
        "ahangrooz.com", "ahangnew.ir", "ahangnew.com", "ahangbox.ir", "ahangbox.com",
        "ahangkhooneh.ir", "ahangkhooneh.com", "ahangestan.net", "ahangdl.net", "ahangdownload.net",
        "music98.ir", "music98.com", "music90.ir", "music90.com", "music4.ir", "music4.com",
        "music20.ir", "music20.com", "music7.ir", "music7.com", "music1.ir", "music1.com",
        "musiccenter.ir", "musiccenter.com", "musicland.ir", "musicland.com", "musicworld.ir",
        "musicworld.com", "musicstar.ir", "musicstar.com", "musicplus.ir", "musicplus.com",
        "musicparsi.ir", "musicparsi.com", "musicpersia.ir", "musicpersia.com", "musiciran.ir",
        "musiciran.com", "musiciranian.ir", "musicirani.ir", "musiciranian.net", "musiciran.net",
        "ahangirani.net", "ahangirani.com", "ahangiran.ir", "ahangiran.com", "ahangestan.org",
        "ahangirani.org", "iranmusic.org", "iranmusic.com", "iranmusic.org", "iran-music.ir",
        "iran-music.com", "iranmp3.ir", "iranmp3.com", "iranmp3.net", "iranmp3.org",
        "irmp3.net", "irmp3.com", "irmp3.org", "ir-mp3.ir", "ir-mp3.com", "ir-mp3.net",
        "mp3iran.ir", "mp3iran.com", "mp3iran.net", "mp3farsi.ir", "mp3farsi.com", "mp3farsi.net",
        "mp3song.ir", "mp3song.com", "mp3music.ir", "mp3music.com", "mp3music.net",
        "mp3dl.ir", "mp3dl.com", "mp3dl.net", "musicdl.ir", "musicdl.com", "musicdl.net",
        "songdl.ir", "songdl.com", "songdl.net", "songmusic.ir", "songmusic.com", "songmusic.net",
        "songsara.ir", "songsara.com", "songfa.ir", "songfa.com", "songfa.net", "songiran.ir",
        "songiran.com", "songiran.net", "ahangfa.ir", "ahangfa.com", "ahangfa.net", "ahangfarsi.ir",
        "ahangfarsi.com", "ahangfarsi.net", "farsisong.ir", "farsisong.com", "farsisong.net",
        "musiciran.net", "musicfarsi.ir", "musicfarsi.com", "musicfarsi.net", "musicparsi.net",
        "persianmusic.net", "persianmusic.org", "persianmusic.info", "persianmusic2.com", "persianmusic2.ir",
        "radiomazani.com", "mazanimusic.ir", "babol3da.com", "hailymusic.ir", "tanin-taraneh.ir",
        "abrarecord.com", "radiojavan.com", "radiojavan.ir", "avangmusic.com", "avangmusic.ir",
        "caltexmusic.com", "caltexmusic.ir", "behmusics.com", "iromusic.com", "iromusic.ir",
        "melovaz.net", "melovaz.com", "melovaz.ir", "melovaz.org", "melovaz.co",
        "sahand-music.ir", "brozmusic.ir", "artmusics.top", "rozmusic.com", "mybia2music.com",
        "bia2music.ir", "bia2music.com", "bia2music.net", "musicema.com", "musicema.ir",
        "musicefa.ir", "musicefa.com", "musicchi.ir", "musicchi.com", "musicisho.ir",
        "musicisho.com", "musicday.ir", "musicday.com", "musicup.ir", "musicup.com",
        "musicgo.ir", "musicgo.com", "musicbox.ir", "musicbox.com", "musicbaz.ir", "musicbaz.com"
    ).distinct()

    private const val BATCH_SIZE = 28

    fun googleQueries(song: String): List<String> {
        val text = song.trim().replace(Regex("\\s+"), " ")
        if (text.isBlank()) return emptyList()
        return domains.chunked(BATCH_SIZE).map { batch ->
            val sites = batch.joinToString(" OR ") { "site:$it" }
            "\"$text\" ($sites)"
        }
    }
}
