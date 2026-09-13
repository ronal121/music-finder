package com.kafshar.musicfinder

import java.net.HttpURLConnection
import java.net.URI
import java.net.URLEncoder
import java.net.URL
import java.nio.charset.StandardCharsets
import java.util.Locale
import java.util.concurrent.Callable
import java.util.concurrent.CompletionService
import java.util.concurrent.ConcurrentHashMap
import java.util.concurrent.ExecutorCompletionService
import java.util.concurrent.ExecutorService
import java.util.concurrent.Executors
import java.util.concurrent.Future
import java.util.concurrent.TimeUnit

/**
 * Direct search against the configured music-site universe.
 *
 * A domain is not assumed to use one global search convention. We first try
 * common URL families, then inspect the site's own HTML search form and use
 * its action + input name. This makes the 428-domain pool an actual resolver
 * rather than a passive list of host names.
 */
class DirectSiteSearchProvider : SearchProvider {
    override val name: String = "Music sites"

    private val executor: ExecutorService = Executors.newFixedThreadPool(20)
    private val recentSearches = ConcurrentHashMap<String, Long>()

    override fun search(query: String, limit: Int): List<GoogleResultParser.Result> {
        if (query.isBlank() || limit <= 0) return emptyList()

        val text = extractQuery(query)
        if (text.isBlank()) return emptyList()

        // MainActivity sends several query variants. Direct search fans out once;
        // repeated variants are handled by the existing discovery pipeline.
        val cacheKey = SearchEngine.normalizeQuery(text)
        val now = System.currentTimeMillis()
        val previous = recentSearches.put(cacheKey, now)
        if (previous != null && now - previous < 20_000L) return emptyList()
        val expired = recentSearches.entries.filter { now - it.value > 120_000L }
        expired.forEach { recentSearches.remove(it.key, it.value) }

        val completion: CompletionService<List<GoogleResultParser.Result>> =
            ExecutorCompletionService(executor)
        val futures = ArrayList<Future<List<GoogleResultParser.Result>>>(MusicSitePool.domains.size)
        MusicSitePool.domains.forEach { domain ->
            futures += completion.submit(Callable { searchDomain(domain, text, limit) })
        }

        val merged = LinkedHashMap<String, GoogleResultParser.Result>()
        val deadline = System.nanoTime() + TimeUnit.MILLISECONDS.toNanos(8_000L)
        var completed = 0
        while (completed < MusicSitePool.domains.size && System.nanoTime() < deadline) {
            val remaining = deadline - System.nanoTime()
            val future = try {
                completion.poll(remaining, TimeUnit.NANOSECONDS)
            } catch (_: InterruptedException) {
                Thread.currentThread().interrupt()
                null
            }
            if (future == null) break
            completed++
            try {
                future.get().forEach { result ->
                    if (merged.size < limit * 4) {
                        merged.putIfAbsent(result.url, result)
                    }
                }
            } catch (_: Exception) {
                // One unavailable site must never abort the complete search.
            }
        }
        futures.forEach { if (!it.isDone) it.cancel(true) }

        return merged.values
            .sortedByDescending { SearchRanking.webScore(text, it.title, it.url, it.url.contains("youtube.com")) }
            .take(limit)
    }

    private fun extractQuery(raw: String): String {
        val quoted = Regex("[\\\"']([^\\\"']+)[\\\"']").find(raw)?.groupValues?.getOrNull(1)
        val source = quoted ?: raw
        return source
            .replace(Regex("\\bsite:[^\\s]+", RegexOption.IGNORE_CASE), " ")
            .replace(Regex("\\s+"), " ")
            .trim()
    }

    private fun searchDomain(domain: String, query: String, limit: Int): List<GoogleResultParser.Result> {
        val encoded = URLEncoder.encode(query, StandardCharsets.UTF_8.toString())
        val candidates = listOf(
            "https://$domain/?s=$encoded",
            "https://$domain/search?q=$encoded",
            "https://$domain/search?query=$encoded",
            "https://$domain/search?s=$encoded",
            "https://$domain/?q=$encoded",
            "https://$domain/find?q=$encoded",
            "https://$domain/?search=$encoded",
            "https://$domain/?keyword=$encoded",
            "https://$domain/search?keyword=$encoded",
            "https://$domain/search?keywords=$encoded",
            "https://$domain/search?search=$encoded",
            "https://$domain/search?searchword=$encoded",
            "https://$domain/search?term=$encoded",
            "https://$domain/search?queryString=$encoded"
        )

        val collected = LinkedHashMap<String, GoogleResultParser.Result>()
        candidates.forEach { url ->
            fetchAndParse(url, domain, query, limit).forEach { collected.putIfAbsent(it.url, it) }
            if (collected.size >= limit) return@forEach
        }

        if (collected.size < limit) {
            discoverSearchUrls(domain, query).forEach { url ->
                fetchAndParse(url, domain, query, limit).forEach { collected.putIfAbsent(it.url, it) }
                if (collected.size >= limit) return@forEach
            }
        }

        return collected.values.take(limit)
    }

    private fun fetchAndParse(
        url: String,
        domain: String,
        query: String,
        limit: Int
    ): List<GoogleResultParser.Result> {
        return try {
            val connection = URL(url).openConnection() as HttpURLConnection
            connection.connectTimeout = 1_200
            connection.readTimeout = 1_800
            connection.instanceFollowRedirects = true
            connection.setRequestProperty("User-Agent", SearchNetwork.USER_AGENT)
            connection.setRequestProperty("Accept", "text/html,application/xhtml+xml")
            connection.connect()
            if (connection.responseCode !in 200..399) return emptyList()
            val html = connection.inputStream.bufferedReader().use { it.readText() }
            parseResultLinks(html, domain, query).take(limit)
        } catch (_: Exception) {
            emptyList()
        }
    }

    private fun discoverSearchUrls(domain: String, query: String): List<String> {
        return try {
            val homepage = fetchHtml("https://$domain/") ?: return emptyList()
            val encoded = URLEncoder.encode(query, StandardCharsets.UTF_8.toString())
            val urls = LinkedHashSet<String>()

            Regex("(?is)<form\\b[^>]*action=[\\\"']([^\\\"']+)[\\\"'][^>]*>(.*?)</form>")
                .findAll(homepage)
                .forEach { match ->
                    val action = match.groupValues[1]
                    val body = match.groupValues[2]
                    val input = Regex("(?is)<input\\b[^>]*(?:name=[\\\"']([^\\\"']+)[\\\"'])[^>]*>")
                        .findAll(body)
                        .map { it.groupValues[1] }
                        .firstOrNull { name ->
                            name.lowercase(Locale.ROOT) in setOf("q", "query", "s", "search", "keyword", "keywords", "searchword", "term", "title", "song", "text")
                        }
                    if (input != null) {
                        val base = if (action.startsWith("http", true)) action else "https://$domain/${action.trimStart('/')}"
                        urls += if (base.contains("?")) "$base&$input=$encoded" else "$base?$input=$encoded"
                    }
                }

            Regex("(?i)href=[\\\"']([^\\\"']*(?:search|find)[^\\\"']*)[\\\"']")
                .findAll(homepage)
                .forEach { match ->
                    val href = match.groupValues[1]
                    if (href.startsWith("/")) {
                        val base = "https://$domain${href}"
                        urls += if (base.contains("?")) "$base&query=$encoded" else "$base?query=$encoded"
                    }
                }
            urls.take(6)
        } catch (_: Exception) {
            emptyList()
        }
    }

    private fun fetchHtml(url: String): String? {
        return try {
            val connection = URL(url).openConnection() as HttpURLConnection
            connection.connectTimeout = 1_200
            connection.readTimeout = 1_800
            connection.instanceFollowRedirects = true
            connection.setRequestProperty("User-Agent", SearchNetwork.USER_AGENT)
            connection.setRequestProperty("Accept", "text/html,application/xhtml+xml")
            connection.connect()
            if (connection.responseCode !in 200..399) return null
            connection.inputStream.bufferedReader().use { it.readText() }
        } catch (_: Exception) {
            null
        }
    }

    private fun parseResultLinks(html: String, domain: String, query: String): List<GoogleResultParser.Result> {
        val results = ArrayList<GoogleResultParser.Result>()
        Regex("(?is)<a\\b[^>]*href=[\\\"']([^\\\"']+)[\\\"'][^>]*>(.*?)</a>")
            .findAll(html)
            .forEach { match ->
                val href = match.groupValues[1].trim()
                val title = match.groupValues[2]
                    .replace(Regex("<[^>]+>"), " ")
                    .replace(Regex("\\s+"), " ")
                    .trim()
                if (title.length < 2 || !isSameDomain(href, domain)) return@forEach
                if (href.contains("/search", true) || href.contains("?s=", true) || href.contains("?q=", true)) return@forEach
                results += GoogleResultParser.Result(title, href, null)
            }
        return results.distinctBy { it.url }
    }

    private fun isSameDomain(url: String, domain: String): Boolean {
        return try {
            val host = URI(url).host?.lowercase(Locale.ROOT) ?: return false
            host == domain.lowercase(Locale.ROOT) || host.endsWith(".$domain")
        } catch (_: Exception) {
            false
        }
    }
}
