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
    override val name: String = "Direct Search"

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
                break
            } ?: break
            completed++
            try {
                future.get().forEach { result ->
                    val key = result.url.substringBefore('#').trimEnd('/').lowercase(Locale.ROOT)
                    if (key.isNotBlank()) merged.putIfAbsent(key, result)
                }
            } catch (_: Exception) { }
            if (merged.size >= limit * 3) break
        }

        // Do not leave the remaining 428-domain batch running after the UI search
        // has returned. This is critical on Android where a large abandoned fanout
        // would otherwise steal the IO threads from the next search.
        futures.forEach { future -> if (!future.isDone) future.cancel(true) }

        return merged.values
            .sortedByDescending { SearchRanking.webScore(text, it.title, it.url, it.isYouTube) }
            .take(limit)
    }

    private fun searchDomain(domain: String, query: String, limit: Int): List<GoogleResultParser.Result> {
        val base = "https://$domain/"
        for (candidate in DirectSearchPatterns.templates(domain, query)) {
            if (Thread.currentThread().isInterrupted) return emptyList()
            val html = fetch(candidate) ?: continue
            val results = parseSameSiteResults(html, domain, limit)
            if (results.isNotEmpty()) return results
        }

        if (Thread.currentThread().isInterrupted) return emptyList()
        // Non-standard sites are resolved from their own search form/links.
        val home = fetch(base) ?: return emptyList()
        for (candidate in DirectSearchPatterns.fromSearchForms(home, base, query)) {
            if (Thread.currentThread().isInterrupted) return emptyList()
            val html = fetch(candidate) ?: continue
            val results = parseSameSiteResults(html, domain, limit)
            if (results.isNotEmpty()) return results
        }
        return emptyList()
    }

    private fun fetch(url: String): String? {
        if (!ServerConfig.isPublicWebUrl(url)) return null
        var connection: HttpURLConnection? = null
        return try {
            connection = URL(url).openConnection() as HttpURLConnection
            connection.requestMethod = "GET"
            connection.connectTimeout = 1400
            connection.readTimeout = 2200
            connection.instanceFollowRedirects = true
            connection.useCaches = true
            connection.setRequestProperty("User-Agent", SearchNetwork.USER_AGENT)
            connection.setRequestProperty("Accept-Language", "fa-IR,fa;q=0.9,en;q=0.8")
            connection.setRequestProperty("Accept", "text/html,application/xhtml+xml;q=0.9,*/*;q=0.5")
            if (connection.responseCode !in 200..299) return null
            connection.inputStream.bufferedReader(Charsets.UTF_8).use { it.readText().take(900_000) }
        } catch (_: Exception) {
            null
        } finally {
            connection?.disconnect()
        }
    }

    private fun parseSameSiteResults(
        html: String,
        domain: String,
        limit: Int
    ): List<GoogleResultParser.Result> {
        if (html.isBlank()) return emptyList()
        return GoogleResultParser.parseAnchors(html, limit * 3)
            .filter { result ->
                val host = try { URI(result.url).host.orEmpty().lowercase(Locale.ROOT) } catch (_: Exception) { "" }
                val normalized = host.removePrefix("www.")
                val target = domain.lowercase(Locale.ROOT).removePrefix("www.")
                normalized == target || normalized.endsWith(".$target")
            }
            .filter { result ->
                val lowerUrl = result.url.lowercase(Locale.ROOT)
                result.title.length >= 2 &&
                    !lowerUrl.contains("/search?") &&
                    !lowerUrl.endsWith("/search") &&
                    !lowerUrl.contains("/page/")
            }
            .distinctBy { it.url.substringBefore('#').trimEnd('/').lowercase(Locale.ROOT) }
            .take(limit)
    }

    private fun extractQuery(input: String): String {
        val candidates = Regex("\\\"([^\\\"]+)\\\"")
            .findAll(input)
            .map { it.groupValues[1].trim() }
            .filter { !it.startsWith("site:", true) }
            .filter { it.isNotBlank() }
            .toList()
        if (candidates.isNotEmpty()) return candidates.maxByOrNull { it.length }.orEmpty()
        return input
            .replace(Regex("site:[^\\s)]+", RegexOption.IGNORE_CASE), " ")
            .replace(Regex("\\bOR\\b", RegexOption.IGNORE_CASE), " ")
            .replace(Regex("[()\\\"]"), " ")
            .replace(Regex("\\s+"), " ")
            .trim()
    }
}

/** URL families shared by the adaptive direct-search resolver. */
object DirectSearchPatterns {
    private val parameterNames = listOf(
        "q", "query", "s", "search", "searchword", "keyword", "keywords", "term", "terms", "searchtext", "text"
    )

    fun templates(domain: String, query: String): List<String> {
        val encoded = URLEncoder.encode(query, StandardCharsets.UTF_8.name())
        val slug = SearchEngine.normalizeQuery(query)
            .replace(Regex("\\s+"), "-")
            .take(120)
        val base = "https://$domain"
        val preferred = when {
            domain.contains("radiojavan", true) -> listOf("$base/search?query=$encoded")
            domain.contains("genius", true) -> listOf("$base/search?q=$encoded")
            domain.contains("musixmatch", true) -> listOf("$base/search/$encoded")
            domain.contains("lyrics", true) -> listOf("$base/search?q=$encoded", "$base/search?query=$encoded")
            else -> emptyList()
        }
        val generic = listOf(
            "$base/?s=$encoded",
            "$base/search?q=$encoded",
            "$base/search?query=$encoded",
            "$base/search?s=$encoded",
            "$base/?q=$encoded",
            "$base/search?keyword=$encoded",
            "$base/search?keywords=$encoded",
            "$base/search?search=$encoded",
            "$base/search?term=$encoded",
            "$base/search/$slug"
        )
        return (preferred + generic).distinct()
    }

    fun fromSearchForms(html: String, base: String, query: String): List<String> {
        val forms = Regex(
            "<form\\b[^>]*>(.*?)</form>",
            setOf(RegexOption.IGNORE_CASE, RegexOption.DOT_MATCHES_ALL)
        )
        val output = LinkedHashSet<String>()
        for (match in forms.findAll(html)) {
            val whole = match.value
            val action = Regex("\\baction\\s*=\\s*([\\\"'])(.*?)\\1", RegexOption.IGNORE_CASE)
                .find(whole)?.groupValues?.getOrNull(2).orEmpty()
            val method = Regex("\\bmethod\\s*=\\s*([\\\"'])(.*?)\\1", RegexOption.IGNORE_CASE)
                .find(whole)?.groupValues?.getOrNull(2).orEmpty().lowercase(Locale.ROOT)
            if (method == "post") continue

            val inputRegex = Regex("<input\\b[^>]*>", RegexOption.IGNORE_CASE)
            val input = inputRegex.findAll(whole).map { it.value }.firstOrNull { tag ->
                val type = attr(tag, "type").lowercase(Locale.ROOT)
                val name = attr(tag, "name").lowercase(Locale.ROOT)
                val placeholder = attr(tag, "placeholder").lowercase(Locale.ROOT)
                type == "search" ||
                    name in parameterNames ||
                    name.contains("search") ||
                    placeholder.contains("search") ||
                    placeholder.contains("جست") ||
                    placeholder.contains("آهنگ")
            } ?: continue

            val name = attr(input, "name")
            if (name.isBlank()) continue
            val actionUrl = try {
                URI(base).resolve(action.ifBlank { "/" }).toString()
            } catch (_: Exception) {
                continue
            }
            if (!ServerConfig.isPublicWebUrl(actionUrl)) continue
            val separator = if (actionUrl.contains('?')) '&' else '?'
            val encoded = URLEncoder.encode(query, StandardCharsets.UTF_8.name())
            output += "$actionUrl$separator$name=$encoded"
            if (output.size >= 4) break
        }

        // Some sites expose the search endpoint as a link rather than a <form>.
        val hrefRegex = Regex("(?:href|data-href)\\s*=\\s*([\\\"'])(.*?)\\1", RegexOption.IGNORE_CASE)
        for (match in hrefRegex.findAll(html)) {
            val href = match.groupValues[2]
            if (!href.contains("search", true) && !href.contains("find", true)) continue
            val resolved = try { URI(base).resolve(href).toString() } catch (_: Exception) { continue }
            if (!ServerConfig.isPublicWebUrl(resolved)) continue
            val parameter = parameterNames.firstOrNull { name ->
                Regex("[?&]$name=", RegexOption.IGNORE_CASE).containsMatchIn(resolved)
            } ?: continue
            val marker = Regex("[?&]$parameter=", RegexOption.IGNORE_CASE).find(resolved) ?: continue
            val encoded = URLEncoder.encode(query, StandardCharsets.UTF_8.name())
            output += resolved.substring(0, marker.range.last + 1) + encoded
            if (output.size >= 6) break
        }
        return output.toList()
    }

    private fun attr(tag: String, name: String): String =
        Regex("\\b$name\\s*=\\s*([\\\"'])(.*?)\\1", RegexOption.IGNORE_CASE)
            .find(tag)?.groupValues?.getOrNull(2).orEmpty()
}
