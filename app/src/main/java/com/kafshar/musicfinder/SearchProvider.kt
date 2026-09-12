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
            } catch (_: Exception) { return emptyList() }

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
            } catch (_: Exception) { return emptyList() }
            finally { connection.disconnect() }
        }
        return emptyList()
    }
}

object SearchNetwork {
    const val USER_AGENT = "Mozilla/5.0 (Linux; Android 12) AppleWebKit/537.36 Chrome/128 Mobile Safari/537.36"

    // Google is the only discovery engine. SearchQueryPlanner constrains every
    // query to MusicSitePool, so unrestricted web discovery is intentionally off.
    val providers: List<SearchProvider> = listOf(
        HttpSearchProvider("Google") { "https://www.google.com/search?q=$it&num=20&hl=fa&gbv=1" }
    )
}
