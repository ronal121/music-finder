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
 * Adaptive direct search over the complete MusicSitePool.
 *
 * Each domain is isolated in its own task. A failed/slow domain only loses its
 * own result; the rest of the pool continues until the bounded search deadline.
 */
class DirectSiteSearchProvider(
    private val domains: List<String> = MusicSitePool.domains,
    concurrency: Int = 12
) : SearchProvider {
    override val name: String = "Music sites"

    private val executor: ExecutorService = Executors.newFixedThreadPool(concurrency.coerceIn(4, 16))
    private val domainSet = domains.map { it.lowercase(Locale.ROOT).removePrefix("www.") }.toSet()

    override fun search(query: String, limit: Int): List<GoogleResultParser.Result> {
        if (query.isBlank() || limit <= 0 || domains.isEmpty()) return emptyList()
        val text = extractQuery(query)
        if (text.isBlank()) return emptyList()

        val completion: CompletionService<List<GoogleResultParser.Result>> = ExecutorCompletionService(executor)
        val futures = ArrayList<Future<List<GoogleResultParser.Result>>>(domains.size)
        domains.distinct().forEach { domain ->
            futures += completion.submit(Callable { searchDomain(domain, text, limit) })
        }

        val merged = LinkedHashMap<String, GoogleResultParser.Result>()
        val deadline = System.nanoTime() + TimeUnit.MILLISECONDS.toNanos(8_000L)
        var completed = 0
        while (completed < futures.size && System.nanoTime() < deadline) {
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
                    val key = canonicalKey(result.url)
                    if (key.isNotBlank()) merged.putIfAbsent(key, result)
                }
            } catch (_: Exception) {
                // A single site must never abort the complete search.
            }
        }
        futures.forEach { if (!it.isDone) it.cancel(true) }

        return merged.values
            .sortedWith(
                compareByDescending<GoogleResultParser.Result> {
                    SearchRanking.webScore(text, it.title, it.url, it.isYouTube)
                }.thenBy { canonicalKey(it.url) }
            )
            .take(limit)
    }

    private fun extractQuery(raw: String): String {
        val quoted = Regex("[\\\"']([^\\\"']+)[\\\"']").find(raw)?.groupValues?.getOrNull(1)
        return (quoted ?: raw)
            .replace(Regex("\\bsite:[^\\s]+", RegexOption.IGNORE_CASE), " ")
            .replace(Regex("\\s+"), " ")
            .trim()
    }

    private fun searchDomain(domain: String, query: String, limit: Int): List<GoogleResultParser.Result> {
        val collected = LinkedHashMap<String, GoogleResultParser.Result>()
        val homepage = fetchHtml("https://$domain/")

        // The site's own search form is the strongest signal of its actual endpoint.
        if (!homepage.isNullOrBlank()) {
            DirectSearchPatterns.fromSearchForms(homepage, "https://$domain/", query)
                .forEach { url ->
                    fetchAndParse(url, domain, query, limit).forEach { collected.putIfAbsent(canonicalKey(it.url), it) }
                    if (collected.size >= limit) return@forEach
                }
        }

        // Then try common CMS conventions. Stop as soon as enough real result pages exist.
        for (url in DirectSearchPatterns.templates(domain, query)) {
            if (collected.size >= limit) break
            fetchAndParse(url, domain, query, limit).forEach { collected.putIfAbsent(canonicalKey(it.url), it) }
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
            val html = fetchHtml(url) ?: return emptyList()
            parseResultLinks(html, domain, query).take(limit)
        } catch (_: Exception) {
            emptyList()
        }
    }

    private fun fetchHtml(url: String): String? {
        if (!ServerConfig.isAllowedPageUrl(url)) return null
        var connection: HttpURLConnection? = null
        return try {
            connection = URL(url).openConnection() as HttpURLConnection
            connection.connectTimeout = 1_200
            connection.readTimeout = 1_800
            connection.instanceFollowRedirects = true
            connection.useCaches = false
            connection.setRequestProperty("User-Agent", SearchNetwork.USER_AGENT)
            connection.setRequestProperty("Accept-Language", "fa-IR,fa;q=0.9,en;q=0.8")
            connection.setRequestProperty("Accept", "text/html,application/xhtml+xml;q=0.9,*/*;q=0.5")
            connection.connect()
            if (connection.responseCode !in 200..399) return null
            if (!ServerConfig.isAllowedPageUrl(connection.url.toString())) return null
            connection.inputStream.bufferedReader(Charsets.UTF_8).use { it.readText().take(1_000_000) }
        } catch (_: Exception) {
            null
        } finally {
            try { connection?.disconnect() } catch (_: Exception) { }
        }
    }

    private fun parseResultLinks(html: String, domain: String, query: String): List<GoogleResultParser.Result> {
        val results = LinkedHashMap<String, GoogleResultParser.Result>()
        val base = "https://$domain/"
        val anchorRegex = Regex("(?is)<a\\b[^>]*href=[\\\"']([^\\\"']+)[\\\"'][^>]*>(.*?)</a>")
        anchorRegex.findAll(html).forEach { match ->
            val href = DirectSearchPatterns.normalize(match.groupValues[1], base) ?: return@forEach
            if (!isSameDomain(href, domain)) return@forEach
            if (isNonSongPage(href)) return@forEach
            if (ServerConfig.hasAudioExtension(href)) return@forEach

            val title = cleanTitle(match.groupValues[2])
            if (title.length < 2) return@forEach
            val result = GoogleResultParser.Result(href, title.take(300), ServerConfig.isYouTubeUrl(href))
            results.putIfAbsent(canonicalKey(href), result)
        }
        return results.values
            .sortedWith(compareByDescending<GoogleResultParser.Result> {
                SearchRanking.webScore(query, it.title, it.url, it.isYouTube)
            })
    }

    private fun cleanTitle(raw: String): String =
        raw.replace(Regex("<[^>]+>"), " ")
            .replace("&amp;", "&", true)
            .replace("&quot;", "\"", true)
            .replace("&#39;", "'", true)
            .replace("&nbsp;", " ", true)
            .replace(Regex("\\s+"), " ")
            .trim()

    private fun isNonSongPage(url: String): Boolean {
        return try {
            val uri = URI(url)
            val path = uri.path.orEmpty().lowercase(Locale.ROOT)
            val query = uri.rawQuery.orEmpty().lowercase(Locale.ROOT)
            path == "/" ||
                path.contains("/search") ||
                path.contains("/category/") ||
                path.contains("/tag/") ||
                path.contains("/author/") ||
                path.contains("/page/") ||
                query.startsWith("s=") || query.startsWith("q=") || query.startsWith("search=")
        } catch (_: Exception) {
            true
        }
    }

    private fun isSameDomain(url: String, domain: String): Boolean {
        return try {
            val host = URI(url).host?.lowercase(Locale.ROOT)?.removePrefix("www.") ?: return false
            host == domain.lowercase(Locale.ROOT).removePrefix("www.") || host.endsWith(".${domain.lowercase(Locale.ROOT).removePrefix("www.")}")
        } catch (_: Exception) {
            false
        }
    }

    private fun canonicalKey(url: String): String =
        url.substringBefore('#').trimEnd('/').lowercase(Locale.ROOT)
}
