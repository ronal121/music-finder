package com.kafshar.musicfinder

import java.net.HttpURLConnection
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
        val encoded = URLEncoder.encode(query, StandardCharsets.UTF_8.name())
        val url = endpoint(encoded)
        val connection = (URL(url).openConnection() as HttpURLConnection).apply {
            requestMethod = "GET"
            connectTimeout = 4500
            readTimeout = 6500
            instanceFollowRedirects = true
            useCaches = false
            setRequestProperty("User-Agent", SearchNetwork.USER_AGENT)
            setRequestProperty("Accept-Language", "fa-IR,fa;q=0.9,en;q=0.8")
        }
        return try {
            val code = connection.responseCode
            if (code !in 200..299) return emptyList()
            val html = connection.inputStream.bufferedReader(Charsets.UTF_8).use { it.readText() }
            GoogleResultParser.parseAnchors(html, limit)
        } finally {
            connection.disconnect()
        }
    }
}

object SearchNetwork {
    const val USER_AGENT = "Mozilla/5.0 (Linux; Android 12) AppleWebKit/537.36 Chrome/128 Mobile Safari/537.36"

    val providers: List<SearchProvider> = listOf(
        HttpSearchProvider("google") { "https://www.google.com/search?q=$it&num=20&hl=fa&gbv=1" },
        HttpSearchProvider("bing") { "https://www.bing.com/search?q=$it&count=20&setlang=fa" },
        HttpSearchProvider("duckduckgo") { "https://html.duckduckgo.com/html/?q=$it" }
    )
}
