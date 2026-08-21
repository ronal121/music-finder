package com.kafshar.musicfinder

/** A source that can contribute music pages to MusicFinder search. */
interface MusicSiteProvider {
    val host: String

    fun matches(url: String): Boolean =
        ServerConfig.isMusicHost(runCatching { android.net.Uri.parse(url).host }.getOrNull()) &&
                ServerConfig.normalizeHost(runCatching { android.net.Uri.parse(url).host }.getOrNull())
                    .let { it == host || it.endsWith(".$host") }
}

object MusicSiteProviders {
    val all: List<MusicSiteProvider> = ServerConfig.MUSIC_SITES.map { site ->
        object : MusicSiteProvider {
            override val host: String = site
        }
    }

    fun providerFor(url: String): MusicSiteProvider? =
        all.firstOrNull { it.matches(url) }
}
