package com.kafshar.musicfinder

import java.net.HttpURLConnection
import java.net.URI
import java.net.URLEncoder
import java.net.URL
import java.nio.charset.StandardCharsets
import java.util.concurrent.Callable
import java.util.concurrent.Executors
import java.util.concurrent.TimeUnit

interface SearchProvider {
    val name: String
    fun search(query: String, limit: Int = 20): List<GoogleResultParser.Result>
}

/**
 * Searches the configured music sources directly.
 *
 * No Google/Bing/other web-search engine is involved. Each source is queried
 * through the common search URL patterns used by music/WordPress sites, and
 * every returned result is kept on the source domain itself.
 */
class DirectMusicSiteSearchProvider : SearchProvider {
    override val name: String = "Music sites"

    private val executor = Executors.newFixedThreadPool(
        MusicSitePool.domains.size.coerceAtMost(8)
    )

    override fun search(query: String, limit: Int): List<GoogleResultParser.Result> {
        if (query.isBlank() || limit <= 0) return emptyList()

        val encoded = URLEncoder.encode(
            query.trim().replace(Regex("\\s+"), " "),
            StandardCharsets.UTF_8.name()
        )

        val tasks = MusicSitePool.domains.map { domain ->
            Callable {
                searchSite(domain, encoded, limit)
            }
        }

        val collected = LinkedHashMap<String, GoogleResultParser.Result>()
        try {
            executor.invokeAll(tasks, 12, TimeUnit.SECONDS).forEach { future ->
                if (collected.size >= limit) return@forEach
                val results = try { future.get() } catch (_: Exception) { emptyList() }
                for (result in results) {
                    val key = result.url.substringBefore('#').trimEnd('/').lowercase()
                    if (collected.putIfAbsent(key, result) == null && collected.size >= limit) break
                }
            }
        } catch (_: Exception) {
            // Return whatever completed before the deadline.
        }
        return collected.values.take(limit)
    }

    private fun searchSite(
        domain: String,
        encodedQuery: String,
        limit: Int
    ): List<GoogleResultParser.Result> {
        val endpoints = listOf(
            "https://$domain/?s=$encodedQuery",
            "https://$domain/search?q=$encodedQuery",
            "https://$domain/search/?q=$encodedQuery",
            "https://$domain/?q=$encodedQuery"
        )

        for (endpoint in endpoints) {
            val html = fetchHtml(endpoint) ?: continue
            val parsed = GoogleResultParser.parseAnchors(html, limit * 3)
            val filtered = parsed.filter { result ->
                isSameSite(result.url, domain)
            }
            if (filtered.isNotEmpty()) return filtered.take(limit)
        }
        return emptyList()
    }

    private fun fetchHtml(url: String): String? {
        if (!ServerConfig.isPublicWebUrl(url)) return null
        val connection = try {
            (URL(url).openConnection() as HttpURLConnection).apply {
                requestMethod = "GET"
                connectTimeout = 2500
                readTimeout = 4000
                instanceFollowRedirects = true
                useCaches = false
                setRequestProperty("User-Agent", SearchNetwork.USER_AGENT)
                setRequestProperty("Accept-Language", "fa-IR,fa;q=0.9,en;q=0.8")
                setRequestProperty("Accept", "text/html,application/xhtml+xml;q=0.9,*/*;q=0.5")
            }
        } catch (_: Exception) {
            return null
        }

        return try {
            if (connection.responseCode !in 200..299) return null
            connection.inputStream.bufferedReader(Charsets.UTF_8).use { it.readText() }
        } catch (_: Exception) {
            null
        } finally {
            connection.disconnect()
        }
    }

    private fun isSameSite(url: String, domain: String): Boolean {
        return try {
            val host = URI(url).host.orEmpty().lowercase()
            host == domain || host.endsWith(".$domain")
        } catch (_: Exception) {
            false
        }
    }
}

object SearchNetwork {
    const val USER_AGENT = "Mozilla/5.0 (Linux; Android 12) AppleWebKit/537.36 Chrome/128 Mobile Safari/537.36"

    /** Only the configured music sources are searched. */
    val providers: List<SearchProvider> = listOf(
        DirectMusicSiteSearchProvider()
    )
}
