package com.kafshar.musicfinder

import java.net.HttpURLConnection
import java.net.URI
import java.net.URLEncoder
import java.net.URL
import java.nio.charset.StandardCharsets

interface SearchProvider {
    val name: String
    fun search(query: String, limit: Int = 20): List<GoogleResultParser.Result>
}

class HttpSearchProvider(
    override val name: String,
    private val endpoint: (String) -> String
) : SearchProvider {
    override fun search(query: String, limit: Int): List<GoogleResultParser.Result> {
        if (query.isBlank() || limit <= 0) return emptyList()
        val encoded = URLEncoder.encode(query, StandardCharsets.UTF_8.name())
        var currentUrl = endpoint(encoded)

        repeat(5) {
            if (!ServerConfig.isPublicWebUrl(currentUrl)) return emptyList()

            val connection = try {
                (URL(currentUrl).openConnection() as HttpURLConnection).apply {
                    requestMethod = "GET"
                    connectTimeout = 4500
                    readTimeout = 6500
                    instanceFollowRedirects = false
                    useCaches = false
                    setRequestProperty("User-Agent", SearchNetwork.USER_AGENT)
                    setRequestProperty("Accept-Language", "fa-IR,fa;q=0.9,en;q=0.8")
                    setRequestProperty("Accept", "text/html,application/xhtml+xml;q=0.9,*/*;q=0.5")
                }
            } catch (_: Exception) {
                return emptyList()
            }

            try {
                val code = connection.responseCode
                if (code in 300..399) {
                    val location = connection.getHeaderField("Location") ?: return emptyList()
                    val next = try { URI(currentUrl).resolve(location).toString() } catch (_: Exception) { return emptyList() }
                    if (!ServerConfig.isPublicWebUrl(next)) return emptyList()
                    currentUrl = next
                    return@repeat
                }
                if (code !in 200..299) return emptyList()
                val html = connection.inputStream.bufferedReader(Charsets.UTF_8).use { it.readText() }
                return GoogleResultParser.parseAnchors(html, limit)
            } catch (_: Exception) {
                return emptyList()
            } finally {
                connection.disconnect()
            }
        }
        return emptyList()
    }
}

/**
 * Google-first music discovery. It performs one normal web search plus a small
 * number of safe site: batches. Each batch contains only six domains because
 * oversized Google operator queries can return no results.
 */
class GoogleMusicSearchProvider : SearchProvider {
    override val name: String = "google"

    private val google = HttpSearchProvider("google") {
        "https://www.google.com/search?q=$it&num=20&hl=fa&gbv=1"
    }

    private val priorityDomains = linkedSetOf<String>().apply {
        addAll(
            listOf(
                "sahand-music.ir", "musicdel.ir", "upmusics.com", "next1.ir", "shabamusic.com",
                "rozmusic.com", "mybia2music.com", "bia2music.ir", "behmusic.com", "behmusics.com",
                "songsara.net", "music-fa.com", "radiojavan.com", "nicmusic.net", "vmusic.ir",
                "sakhamusic.ir", "jenabmusic.com", "ganja2music.com", "silamusic.ir", "pop-music.ir",
                "farsiplayer.com", "sorud.com", "matnmusic.com", "dtaraneh.net", "trackmelody.com",
                "biya2ahang.ir", "songsun.ir", "rozsong.com", "sultanmusics.com", "musicaz.ir",
                "myspotify.ir", "musickordi.com", "shomal-music.info", "hailymusic.ir", "ahangtv.com",
                "genius.com", "musixmatch.com", "lyrics.com", "soundcloud.com", "bandcamp.com"
            )
        )
        addAll(MusicSiteAdditions.domains)
    }

    override fun search(query: String, limit: Int): List<GoogleResultParser.Result> {
        if (query.isBlank() || limit <= 0) return emptyList()

        val merged = LinkedHashMap<String, GoogleResultParser.Result>()
        fun add(results: List<GoogleResultParser.Result>) {
            results.forEach { result ->
                val key = result.url.substringBefore("#").trimEnd('/').lowercase()
                if (key.isNotBlank()) merged.putIfAbsent(key, result)
            }
        }

        add(google.search(query, limit.coerceAtLeast(20)))

        // Keep the site-targeted pass bounded. General Google results remain the
        // primary source; these batches only recover pages hidden by broad ranking.
        priorityDomains
            .chunked(6)
            .take(6)
            .forEach { batch ->
                val sites = batch.joinToString(" OR ") { "site:$it" }
                add(google.search("$query ($sites)", 20))
            }

        return merged.values.take(limit.coerceAtLeast(20))
    }
}

object SearchNetwork {
    const val USER_AGENT = "Mozilla/5.0 (Linux; Android 12) AppleWebKit/537.36 Chrome/128 Mobile Safari/537.36"

    val providers: List<SearchProvider> = listOf(
        GoogleMusicSearchProvider(),
        HttpSearchProvider("bing") { "https://www.bing.com/search?q=$it&count=20&setlang=fa" },
        HttpSearchProvider("duckduckgo") { "https://html.duckduckgo.com/html/?q=$it" }
    )
}
