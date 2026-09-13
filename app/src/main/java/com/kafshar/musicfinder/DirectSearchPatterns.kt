package com.kafshar.musicfinder

import java.net.URI
import java.net.URLEncoder
import java.nio.charset.StandardCharsets

/**
 * Deterministic search URL generation for heterogeneous music sites.
 *
 * The common templates are deliberately broad; a site's own HTML form is
 * inspected before falling back to less common path conventions.
 */
object DirectSearchPatterns {
    fun templates(domain: String, query: String): List<String> {
        val encoded = encode(query)
        val base = "https://$domain"
        return linkedSetOf(
            "$base/?s=$encoded",
            "$base/search?q=$encoded",
            "$base/search/?q=$encoded",
            "$base/search?query=$encoded",
            "$base/search?s=$encoded",
            "$base/?q=$encoded",
            "$base/find?q=$encoded",
            "$base/?search=$encoded",
            "$base/?keyword=$encoded",
            "$base/search?keyword=$encoded",
            "$base/search?keywords=$encoded",
            "$base/search?search=$encoded",
            "$base/search?searchword=$encoded",
            "$base/search?term=$encoded",
            "$base/search?queryString=$encoded",
            "$base/search/$encoded",
            "$base/music/$encoded",
            "$base/song/$encoded"
        ).toList()
    }

    fun fromSearchForms(html: String, homepageUrl: String, query: String): List<String> {
        if (html.isBlank() || query.isBlank()) return emptyList()
        val encoded = encode(query)
        val baseUri = try { URI(homepageUrl) } catch (_: Exception) { return emptyList() }
        val expectedHost = baseUri.host?.lowercase().orEmpty()
        val urls = LinkedHashSet<String>()
        val formRegex = Regex("(?is)<form\\b[^>]*action=[\\\"']([^\\\"']+)[\\\"'][^>]*>(.*?)</form>")
        val inputRegex = Regex("(?is)<input\\b[^>]*>")
        val nameRegex = Regex("(?i)\\bname=[\\\"']([^\\\"']+)[\\\"']")
        val signalRegex = Regex("(?i)(search|جستجو|آهنگ|song|track|music|keyword|query|term|title|text)")

        formRegex.findAll(html).forEach { form ->
            val action = form.groupValues[1].trim()
            val body = form.groupValues[2]
            val names = inputRegex.findAll(body)
                .mapNotNull { input -> nameRegex.find(input.value)?.groupValues?.getOrNull(1) }
                .toList()
            val input = names.firstOrNull { signalRegex.containsMatchIn(it) } ?: names.firstOrNull() ?: return@forEach
            val resolved = try { baseUri.resolve(action).normalize() } catch (_: Exception) { return@forEach }
            val host = resolved.host?.lowercase().orEmpty()
            if (expectedHost.isBlank() || host != expectedHost) return@forEach
            val base = resolved.toString().substringBefore('#')
            val separator = if (base.contains('?')) '&' else '?'
            urls += "$base$separator${encodeParameter(input)}=$encoded"
        }
        return urls.toList()
    }

    fun normalize(url: String, base: String): String? {
        if (url.isBlank()) return null
        return try {
            val resolved = URI(base).resolve(url.trim()).normalize()
            val scheme = resolved.scheme?.lowercase()
            if (scheme != "http" && scheme != "https") return null
            resolved.toString().substringBefore('#').trimEnd('/').ifBlank { null }
        } catch (_: Exception) {
            null
        }
    }

    private fun encode(query: String): String =
        URLEncoder.encode(query.trim(), StandardCharsets.UTF_8.toString())

    private fun encodeParameter(value: String): String =
        URLEncoder.encode(value.trim(), StandardCharsets.UTF_8.toString())
}
