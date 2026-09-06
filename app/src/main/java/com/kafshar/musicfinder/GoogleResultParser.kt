package com.kafshar.musicfinder

import java.net.URI
import java.net.URLDecoder

/** Testable parser for Google/WebView result anchors. It does not know any music domains. */
object GoogleResultParser {
    data class Result(val url: String, val title: String, val isYouTube: Boolean)

    fun normalizeUrl(raw: String, base: String = "https://www.google.com/"): String? {
        if (raw.isBlank()) return null
        return try {
            val input = raw.trim()
            val uri = URI(input)
            val host = uri.host.orEmpty().lowercase()
            if (host.contains("google.")) {
                val query = parseQuery(uri.rawQuery)
                val target = query["q"] ?: query["url"] ?: query["u"]
                if (!target.isNullOrBlank() && target.startsWith("http", true)) return target
            }
            val resolved = URI(base).resolve(uri)
            resolved.toString().takeIf { it.startsWith("http://", true) || it.startsWith("https://", true) }
        } catch (_: Exception) { null }
    }

    fun parseAnchors(html: String, limit: Int = 30): List<Result> {
        if (html.isBlank()) return emptyList()
        val results = LinkedHashMap<String, Result>()
        val anchor = Regex(
            "<a\\b[^>]*href\\s*=\\s*[\\\"']([^\\\"']+)[\\\"'][^>]*>(.*?)</a>",
            RegexOption.IGNORE_CASE + RegexOption.DOT_MATCHES_ALL
        )
        for (m in anchor.findAll(html)) {
            if (results.size >= limit) break
            val url = normalizeUrl(unescapeHtml(m.groupValues[1])) ?: continue
            if (!isExternalHttp(url)) continue
            val title = stripHtml(unescapeHtml(m.groupValues[2])).trim()
            if (title.isBlank()) continue
            val key = url.substringBefore('#').trimEnd('/').lowercase()
            results.putIfAbsent(key, Result(url, title.take(300), ServerConfig.isYouTubeUrl(url)))
        }
        return results.values.toList()
    }

    private fun parseQuery(raw: String?): Map<String, String> = raw.orEmpty().split('&').mapNotNull { part ->
        val p = part.split('=', limit = 2)
        if (p.size == 2) URLDecoder.decode(p[0], "UTF-8") to URLDecoder.decode(p[1], "UTF-8") else null
    }.toMap()

    private fun isExternalHttp(url: String): Boolean {
        if (!url.startsWith("http://", true) && !url.startsWith("https://", true)) return false
        val host = try { URI(url).host.orEmpty().lowercase() } catch (_: Exception) { "" }
        return host.isNotBlank() && !host.contains("google.") && host != "webcache.googleusercontent.com"
    }

    private fun stripHtml(value: String): String = value.replace(Regex("<[^>]+>"), " ").replace(Regex("\\s+"), " ")
    private fun unescapeHtml(value: String): String = value
        .replace("&amp;", "&", true)
        .replace("&quot;", "\"", true)
        .replace("&#39;", "'", true)
        .replace("&lt;", "<", true)
        .replace("&gt;", ">", true)
}
