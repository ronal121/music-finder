package com.kafshar.musicfinder

import java.net.URI
import java.net.URLDecoder

/** Testable parser for Google/WebView result anchors. It does not know any music domains. */
object GoogleResultParser {
    data class Result(val url: String, val title: String, val isYouTube: Boolean)

    fun normalizeUrl(raw: String, base: String = "https://www.google.com/"): String? {
        if (raw.isBlank()) return null
        return try {
            val input = unescapeHtml(raw.trim())
            val resolved = URI(base).resolve(input)
            val host = resolved.host.orEmpty().lowercase()

            if (host.contains("google.")) {
                val query = parseQuery(resolved.rawQuery)
                // Google result redirects may use q, url or u. q can be present but empty,
                // so choose the first non-empty destination instead of blindly preferring q.
                val target = listOf(query["q"], query["url"], query["u"])
                    .firstOrNull { !it.isNullOrBlank() }
                    ?.trim()

                if (!target.isNullOrBlank() && target.startsWith("http", true)) {
                    return decodeUrlRepeatedly(target)
                        .takeIf { it.startsWith("http://", true) || it.startsWith("https://", true) }
                }
            }

            resolved.toString().takeIf {
                it.startsWith("http://", true) || it.startsWith("https://", true)
            }
        } catch (_: Exception) {
            null
        }
    }

    fun parseAnchors(html: String, limit: Int = 30): List<Result> {
        if (html.isBlank() || limit <= 0) return emptyList()

        val results = LinkedHashMap<String, Result>()

        // Google has used both href and data-href, and its result markup changes over time.
        // Keep the parser structural rather than depending on a specific result CSS class.
        val attribute = Regex(
            "(?:href|data-href)\\s*=\\s*([\\\"'])(.*?)\\1",
            setOf(RegexOption.IGNORE_CASE, RegexOption.DOT_MATCHES_ALL)
        )
        val anchor = Regex(
            "<a\\b[^>]*>(.*?)</a>",
            setOf(RegexOption.IGNORE_CASE, RegexOption.DOT_MATCHES_ALL)
        )

        for (m in anchor.findAll(html)) {
            if (results.size >= limit) break

            val anchorHtml = m.groupValues[0]
            val title = stripHtml(unescapeHtml(m.groupValues[1])).trim()
            if (title.isBlank()) continue

            val href = attribute.find(anchorHtml)?.groupValues?.getOrNull(2).orEmpty()
            if (href.isBlank()) continue

            val url = normalizeUrl(href) ?: continue
            if (!isExternalHttp(url)) continue

            val key = canonicalKey(url)
            results.putIfAbsent(
                key,
                Result(url, title.take(300), ServerConfig.isYouTubeUrl(url))
            )
        }

        // Some markup exposes a destination on data-href outside a conventional anchor.
        if (results.size < limit) {
            for (m in attribute.findAll(html)) {
                if (results.size >= limit) break
                val url = normalizeUrl(m.groupValues.getOrNull(2).orEmpty()) ?: continue
                if (!isExternalHttp(url)) continue
                val key = canonicalKey(url)
                if (results.containsKey(key)) continue
                results[key] = Result(url, hostTitle(url), ServerConfig.isYouTubeUrl(url))
            }
        }

        return results.values.toList()
    }

    private fun parseQuery(raw: String?): Map<String, String> =
        raw.orEmpty()
            .split('&')
            .mapNotNull { part ->
                val p = part.split('=', limit = 2)
                if (p.size != 2) return@mapNotNull null
                try {
                    URLDecoder.decode(p[0], "UTF-8") to URLDecoder.decode(p[1], "UTF-8")
                } catch (_: Exception) {
                    null
                }
            }
            .toMap()

    private fun decodeUrlRepeatedly(value: String): String {
        var current = value
        repeat(3) {
            val decoded = try { URLDecoder.decode(current, "UTF-8") } catch (_: Exception) { current }
            if (decoded == current) return current
            current = decoded
        }
        return current
    }

    private fun isExternalHttp(url: String): Boolean {
        if (!url.startsWith("http://", true) && !url.startsWith("https://", true)) return false
        val host = try { URI(url).host.orEmpty().lowercase() } catch (_: Exception) { "" }
        return host.isNotBlank() &&
            !host.contains("google.") &&
            host != "webcache.googleusercontent.com"
    }

    private fun canonicalKey(url: String): String =
        url.substringBefore('#').trimEnd('/').lowercase()

    private fun hostTitle(url: String): String = try {
        URI(url).host.orEmpty().removePrefix("www.").ifBlank { "Result" }
    } catch (_: Exception) {
        "Result"
    }

    private fun stripHtml(value: String): String =
        value.replace(Regex("<[^>]+>"), " ")
            .replace(Regex("\\s+"), " ")

    private fun unescapeHtml(value: String): String = value
        .replace("&amp;", "&", true)
        .replace("&quot;", "\"", true)
        .replace("&#39;", "'", true)
        .replace("&lt;", "<", true)
        .replace("&gt;", ">", true)
}