package com.kafshar.musicfinder

import android.net.Uri

/** Testable parser for Google/WebView result anchors. It does not know any music domains. */
object GoogleResultParser {
    data class Result(val url: String, val title: String, val isYouTube: Boolean)

    fun normalizeUrl(raw: String, base: String = "https://www.google.com/"): String? {
        if (raw.isBlank()) return null
        return try {
            val u = Uri.parse(raw.trim())
            val host = u.host.orEmpty().lowercase()
            if (host.contains("google.")) {
                val target = u.getQueryParameter("q")
                    ?: u.getQueryParameter("url")
                    ?: u.getQueryParameter("u")
                if (!target.isNullOrBlank() && target.startsWith("http", true)) return target
            }
            Uri.parse(base).buildUpon().encodedPath(u.encodedPath ?: "").encodedQuery(u.encodedQuery).build().toString().takeIf { it.startsWith("http", true) }
                ?: raw
        } catch (_: Exception) { null }
    }

    fun parseAnchors(html: String, limit: Int = 30): List<Result> {
        if (html.isBlank()) return emptyList()
        val results = LinkedHashMap<String, Result>()
        val anchor = Regex("<a\\b[^>]*href\\s*=\\s*[\\\"']([^\\\"']+)[\\\"'][^>]*>(.*?)</a>", RegexOption.IGNORE_CASE or RegexOption.DOT_MATCHES_ALL)
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

    private fun isExternalHttp(url: String): Boolean {
        if (!url.startsWith("http://", true) && !url.startsWith("https://", true)) return false
        val host = Uri.parse(url).host?.lowercase().orEmpty()
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
