package com.kafshar.musicfinder

import java.net.HttpURLConnection
import java.net.URI
import java.net.URL
import java.util.Locale
import java.util.concurrent.Callable
import java.util.concurrent.CompletionService
import java.util.concurrent.ConcurrentHashMap
import java.util.concurrent.ExecutorCompletionService
import java.util.concurrent.ExecutorService
import java.util.concurrent.Executors
import java.util.concurrent.Future
import java.util.concurrent.TimeUnit

/** Fast adaptive direct search over the complete MusicSitePool. */
class DirectSiteSearchProvider(
    private val domains: List<String> = MusicSitePool.domains,
    concurrency: Int = 24
) : SearchProvider {
    override val name: String = "Music sites"

    private val executor: ExecutorService = Executors.newFixedThreadPool(concurrency.coerceIn(8, 32))
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
        // Direct search is a fast complementary path. Do not make the UI wait for
        // every slow/dead domain in the 400+ site pool.
        val deadline = System.nanoTime() + TimeUnit.MILLISECONDS.toNanos(4_500L)
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
                // One broken site must never abort the complete search.
            }
            if (merged.size >= limit * 2) break
        }
        futures.forEach { if (!it.isDone) it.cancel(true) }

        return merged.values
            .sortedWith(compareByDescending<GoogleResultParser.Result> {
                SearchRanking.webScore(text, it.title, it.url, it.isYouTube)
            }.thenBy { canonicalKey(it.url) })
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
        val homepage = fetchHtml("https://$domain/", connectTimeout = 650, readTimeout = 950)

        // Inspect the real search form first; this avoids relying only on CMS guesses.
        if (!homepage.isNullOrBlank()) {
            DirectSearchPatterns.fromSearchForms(homepage, "https://$domain/", query)
                .take(2)
                .forEach { url ->
                    fetchAndParse(url, domain, query, limit).forEach { collected.putIfAbsent(canonicalKey(it.url), it) }
                }
        }

        // Keep the common fallback set small for latency. The complete domain pool
        // is still searched; we simply do not spend seconds probing every convention.
        for (url in DirectSearchPatterns.templates(domain, query).take(6)) {
            if (collected.size >= limit) break
            fetchAndParse(url, domain, query, limit).forEach { collected.putIfAbsent(canonicalKey(it.url), it) }
        }

        return collected.values.take(limit)
    }

    private fun fetchAndParse(url: String, domain: String, query: String, limit: Int): List<GoogleResultParser.Result> {
        return try {
            val html = fetchHtml(url, connectTimeout = 750, readTimeout = 1_100) ?: return emptyList()
            parseResultLinks(html, domain, query).take(limit)
        } catch (_: Exception) {
            emptyList()
        }
    }

    private fun fetchHtml(url: String, connectTimeout: Int, readTimeout: Int): String? {
        if (!ServerConfig.isAllowedPageUrl(url)) return null
        var connection: HttpURLConnection? = null
        return try {
            connection = URL(url).openConnection() as HttpURLConnection
            connection.connectTimeout = connectTimeout
            connection.readTimeout = readTimeout
            connection.instanceFollowRedirects = true
            connection.useCaches = false
            connection.setRequestProperty("User-Agent", SearchNetwork.USER_AGENT)
            connection.setRequestProperty("Accept-Language", "fa-IR,fa;q=0.9,en;q=0.8")
            connection.setRequestProperty("Accept", "text/html,application/xhtml+xml;q=0.9,*/*;q=0.5")
            connection.connect()
            if (connection.responseCode !in 200..399) return null
            if (!ServerConfig.isAllowedPageUrl(connection.url.toString())) return null
            connection.inputStream.bufferedReader(Charsets.UTF_8).use { it.readText().take(800_000) }
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
        return results.values.sortedWith(compareByDescending<GoogleResultParser.Result> {
            SearchRanking.webScore(query, it.title, it.url, it.isYouTube)
        })
    }

    private fun cleanTitle(raw: String): String = raw
        .replace(Regex("<[^>]+>"), " ")
        .replace("&amp;", "&", true)
        .replace("&quot;", "\"", true)
        .replace("&#39;", "'", true)
        .replace("&nbsp;", " ", true)
        .replace(Regex("\\s+"), " ")
        .trim()

    private fun isNonSongPage(url: String): Boolean = try {
        val uri = URI(url)
        val path = uri.path.orEmpty().lowercase(Locale.ROOT)
        val query = uri.rawQuery.orEmpty().lowercase(Locale.ROOT)
        path == "/" || path.contains("/search") || path.contains("/category/") ||
            path.contains("/tag/") || path.contains("/author/") || path.contains("/page/") ||
            query.startsWith("s=") || query.startsWith("q=") || query.startsWith("search=")
    } catch (_: Exception) { true }

    private fun isSameDomain(url: String, domain: String): Boolean = try {
        val host = URI(url).host?.lowercase(Locale.ROOT)?.removePrefix("www.") ?: return false
        val expected = domain.lowercase(Locale.ROOT).removePrefix("www.")
        host == expected || host.endsWith(".$expected")
    } catch (_: Exception) { false }

    private fun canonicalKey(url: String): String = url.substringBefore('#').trimEnd('/').lowercase(Locale.ROOT)
}
