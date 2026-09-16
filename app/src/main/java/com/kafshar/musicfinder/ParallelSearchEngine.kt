package com.kafshar.musicfinder

import android.text.Html
import org.json.JSONArray
import java.net.HttpURLConnection
import java.net.URI
import java.net.URLEncoder
import java.util.concurrent.Callable
import java.util.concurrent.Executors
import java.util.concurrent.Future
import java.util.concurrent.TimeUnit

/**
 * Native-first search coordinator.
 *
 * It searches the configured music sites directly over HTTP. Google remains a
 * WebView fallback in MainActivity, so Google/consent/CAPTCHA cannot make the
 * primary search path fail by itself.
 */
object ParallelSearchEngine {
    private const val CONNECT_TIMEOUT_MS = 3500
    private const val READ_TIMEOUT_MS = 5000
    private const val BATCH_TIMEOUT_SECONDS = 9L
    private const val MAX_RESULTS_PER_SITE = 8
    private const val MAX_RESULTS = 60

    private val executor = Executors.newFixedThreadPool(12)

    data class Candidate(
        val url: String,
        val title: String,
        val artist: String,
        val site: String,
        val cover: String = "",
        val score: Int = 0
    )

    /**
     * Search music domains without Google. The returned Future is cancellable;
     * MainActivity uses the generation as a second guard against stale results.
     */
    fun searchDirect(
        query: String,
        generation: Int,
        callback: (Int, List<Candidate>) -> Unit
    ): Future<*> {
        val q = SearchEngine.correctedQuery(query).trim()
        if (q.isBlank()) {
            callback(generation, emptyList())
            return FutureTaskCompleted
        }

        return executor.submit {
            val merged = LinkedHashMap<String, Candidate>()
            val sites = ServerConfig.PRIMARY_SEARCH_SITES

            try {
                val tasks = sites.map { domain ->
                    Callable { searchSite(domain, SearchEngine.buildQueries(q)) }
                }
                val futures = executor.invokeAll(
                    tasks,
                    BATCH_TIMEOUT_SECONDS,
                    TimeUnit.SECONDS
                )
                futures.forEach { future ->
                    if (Thread.currentThread().isInterrupted) return@forEach
                    try {
                        future.get().forEach { candidate ->
                            val key = canonical(candidate.url)
                            val old = merged[key]
                            if (old == null || candidate.score > old.score) {
                                merged[key] = candidate
                            }
                        }
                    } catch (_: Exception) {
                        // One unavailable source must not fail the whole search.
                    }
                }
            } catch (_: InterruptedException) {
                Thread.currentThread().interrupt()
            } catch (_: Exception) {
                // The UI will use the Google fallback if no direct results exist.
            }

            val ranked = merged.values
                .sortedWith(
                    compareByDescending<Candidate> { it.score }
                        .thenBy { it.site }
                        .thenBy { it.title.lowercase() }
                )
                .take(MAX_RESULTS)

            if (!Thread.currentThread().isInterrupted) {
                callback(generation, ranked)
            }
        }
    }

    /** Backward-compatible entry point; native direct search is now primary. */
    fun search(
        query: String,
        generation: Int,
        callback: (Int, List<Candidate>) -> Unit
    ): Future<*> = searchDirect(query, generation, callback)

    private fun searchSite(domain: String, queries: List<String>): List<Candidate> {
        val out = ArrayList<Candidate>()
        val seen = HashSet<String>()

        for (q in queries.take(4)) {
            if (Thread.currentThread().isInterrupted) break
            val encoded = URLEncoder.encode(q, "UTF-8")
            val urls = listOf(
                "https://$domain/?s=$encoded",
                "https://$domain/search?q=$encoded",
                "https://$domain/?q=$encoded",
                "https://$domain/search?query=$encoded"
            )

            for (url in urls) {
                if (Thread.currentThread().isInterrupted || out.size >= MAX_RESULTS_PER_SITE) break
                val html = get(url) ?: continue
                val results = extractLinks(html, domain, q)
                for (candidate in results) {
                    if (seen.add(canonical(candidate.url))) {
                        out += candidate
                        if (out.size >= MAX_RESULTS_PER_SITE) break
                    }
                }
                if (out.isNotEmpty()) break
            }
        }

        // Additive content search. The existing site-search logic above is
        // intentionally untouched. Content candidates are always considered,
        // even when the normal site search already produced eight results.
        if (!Thread.currentThread().isInterrupted) {
            for (q in queries.take(2)) {
                if (Thread.currentThread().isInterrupted) break
                val results = searchWordPressContent(domain, q)
                for (candidate in results) {
                    if (seen.add(canonical(candidate.url))) {
                        out += candidate
                    }
                }
            }
        }

        return out
            .distinctBy { canonical(it.url) }
            .sortedByDescending { it.score }
            .take(MAX_RESULTS_PER_SITE)
    }

    /**
     * WordPress native search supplies candidate pages. Each candidate is then
     * opened and checked against the actual page text, so lyric-only matches
     * can outrank unrelated title matches. This is additive and does not alter
     * the existing site-search URLs or SearchEngine logic.
     */
    private fun searchWordPressContent(domain: String, query: String): List<Candidate> {
        val encoded = try {
            URLEncoder.encode(query, "UTF-8")
        } catch (_: Exception) {
            return emptyList()
        }

        val url =
            "https://$domain/wp-json/wp/v2/search" +
                "?search=$encoded&per_page=8&subtype=any&_fields=id,title,url,subtype"

        val json = get(url) ?: return emptyList()
        val out = ArrayList<Candidate>()

        try {
            val array = JSONArray(json)
            // Only inspect the strongest few WP candidates to keep this additive
            // check cheap enough for the existing parallel-search timeout.
            for (i in 0 until minOf(array.length(), 4)) {
                if (Thread.currentThread().isInterrupted) break

                val item = array.optJSONObject(i) ?: continue
                val pageUrl = item.optString("url").trim()
                if (pageUrl.isBlank() || !ServerConfig.isAllowedPageUrl(pageUrl)) continue

                val titleObject = item.optJSONObject("title")
                val title = Html.fromHtml(
                    titleObject?.optString("rendered").orEmpty(),
                    Html.FROM_HTML_MODE_LEGACY
                ).toString()
                    .replace(Regex("\\s+"), " ")
                    .trim()

                if (title.isBlank()) continue

                val pageHtml = get(pageUrl)
                val pageText = pageHtml
                    ?.replace(Regex("(?is)<script\\b[^>]*>.*?</script>"), " ")
                    ?.replace(Regex("(?is)<style\\b[^>]*>.*?</style>"), " ")
                    ?.let {
                        Html.fromHtml(it, Html.FROM_HTML_MODE_LEGACY)
                            .toString()
                            .replace(Regex("\\s+"), " ")
                            .trim()
                    }
                    .orEmpty()

                val normalizedQuery = SearchEngine.normalizeQuery(query).trim()
                val normalizedTitle = SearchEngine.normalizeQuery(title)
                val normalizedContent = SearchEngine.normalizeQuery(pageText)
                val queryTokens = normalizedQuery
                    .split(' ')
                    .filter { it.length > 1 }
                    .distinct()

                if (queryTokens.isEmpty()) continue

                val titleHits = queryTokens.count { normalizedTitle.contains(it) }
                val contentHits = queryTokens.count { normalizedContent.contains(it) }
                val allContentTokens = contentHits == queryTokens.size
                val exactPhrase = normalizedQuery.length > 3 &&
                    normalizedContent.contains(normalizedQuery)
                val relevance = SearchEngine.similarity(query, title)
                val sitePriority =
                    (ServerConfig.serverForUrl(pageUrl)?.priority ?: 0) / 10

                // Content evidence deliberately dominates a weak title match.
                val score =
                    90 +
                        titleHits * 18 +
                        contentHits * 34 +
                        if (allContentTokens) 70 else 0 +
                        if (exactPhrase) 100 else 0 +
                        relevance +
                        sitePriority

                // A WP search hit that has no query token anywhere in the
                // actual page is not useful as a content result.
                if (contentHits == 0 && titleHits == 0) continue

                out += Candidate(
                    url = pageUrl.substringBefore('#').trimEnd('/'),
                    title = cleanTitle(title),
                    artist = "",
                    site = ServerConfig.siteName(pageUrl),
                    score = score
                )
            }
        } catch (_: Exception) {
            return emptyList()
        }

        return out
            .distinctBy { canonical(it.url) }
            .sortedByDescending { it.score }
            .take(MAX_RESULTS_PER_SITE)
    }

    private fun get(url: String): String? {
        var connection: HttpURLConnection? = null
        return try {
            connection = URI(url).toURL().openConnection() as HttpURLConnection
            connection.connectTimeout = CONNECT_TIMEOUT_MS
            connection.readTimeout = READ_TIMEOUT_MS
            connection.instanceFollowRedirects = true
            connection.requestMethod = "GET"
            connection.setRequestProperty(
                "User-Agent",
                "Mozilla/5.0 (Linux; Android 12) AppleWebKit/537.36 Chrome/128 Mobile Safari/537.36"
            )
            connection.setRequestProperty(
                "Accept",
                "text/html,application/xhtml+xml,application/xml;q=0.9,*/*;q=0.8"
            )
            connection.setRequestProperty("Accept-Language", "fa-IR,fa;q=0.9,en;q=0.8")
            connection.connect()

            val status = connection.responseCode
            if (status !in 200..399) return null

            connection.inputStream.bufferedReader(Charsets.UTF_8)
                .use { it.readText().take(900_000) }
        } catch (_: Exception) {
            null
        } finally {
            connection?.disconnect()
        }
    }

    private fun extractLinks(
        html: String,
        site: String,
        query: String
    ): List<Candidate> {
        val out = ArrayList<Candidate>()
        val queryTokens = SearchEngine.normalizeQuery(query)
            .split(' ')
            .filter { it.length > 1 }

        val regex = Regex(
            "(?is)<a\\b([^>]*?)href=[\\\"']([^\\\"']+)[\\\"']([^>]*)>(.*?)</a>"
        )

        for (match in regex.findAll(html).take(1000)) {
            if (Thread.currentThread().isInterrupted) break

            val before = match.groupValues[1]
            val hrefRaw = match.groupValues[2]
            val after = match.groupValues[3]
            val body = match.groupValues[4]

            val href = Html.fromHtml(
                hrefRaw,
                Html.FROM_HTML_MODE_LEGACY
            ).toString().trim()
            val absolute = absoluteUrl(href, site) ?: continue
            if (!ServerConfig.isAllowedPageUrl(absolute)) continue

            val text = Html.fromHtml(
                body,
                Html.FROM_HTML_MODE_LEGACY
            ).toString()
                .replace(Regex("\\s+"), " ")
                .trim()

            val titleAttr = Regex(
                "(?i)(?:title|aria-label)=[\\\"']([^\\\"']+)[\\\"']"
            ).find(before + after)?.groupValues?.getOrNull(1).orEmpty()
            val display = text.ifBlank { titleAttr }.trim()
            if (display.length < 2) continue

            val normalized = SearchEngine.normalizeQuery(display + " " + absolute)
            val hits = queryTokens.count { normalized.contains(it) }
            if (hits == 0) continue

            val score =
                hits * 24 +
                    SearchEngine.similarity(query, display) +
                    if (display.length <= 180) 8 else 0 +
                    (ServerConfig.serverForUrl(absolute)?.priority ?: 0) / 10

            out += Candidate(
                url = absolute.substringBefore('#').trimEnd('/'),
                title = cleanTitle(display),
                artist = "",
                site = ServerConfig.siteName(absolute),
                score = score
            )
        }

        return out
            .distinctBy { canonical(it.url) }
            .sortedByDescending { it.score }
            .take(MAX_RESULTS_PER_SITE)
    }

    private fun cleanTitle(value: String): String =
        value.replace(Regex("\\s+"), " ").trim().take(180)

    private fun canonical(url: String): String =
        url.substringBefore('#').trimEnd('/').lowercase()

    private fun absoluteUrl(href: String, site: String): String? {
        return try {
            when {
                href.startsWith("http://", true) || href.startsWith("https://", true) -> href
                href.startsWith("//") -> "https:$href"
                href.startsWith("/") -> "https://$site$href"
                else -> null
            }
        } catch (_: Exception) {
            null
        }
    }

    /** A no-op Future used for blank queries without allocating a worker. */
    private object FutureTaskCompleted : Future<Any?> {
        override fun cancel(mayInterruptIfRunning: Boolean): Boolean = false
        override fun isCancelled(): Boolean = false
        override fun isDone(): Boolean = true
        override fun get(): Any? = null
        override fun get(timeout: Long, unit: TimeUnit): Any? = null
    }
}
