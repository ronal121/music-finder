package com.kafshar.musicfinder

import java.net.URLEncoder
import java.nio.charset.StandardCharsets

/** Shared, deterministic search-URL generation used by DirectSiteSearch tests and search logic. */
object DirectSearchPatterns {
    fun templates(domain: String, query: String): List<String> {
        val encoded = encode(query)
        return listOf(
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
            "https://$domain/search?queryString=$encoded",
            "https://$domain/search/$encoded"
        )
    }

    fun fromSearchForms(html: String, homepageUrl: String, query: String): List<String> {
        val encoded = encode(query)
        val host = homepageUrl.removeSuffix("/")
        val urls = LinkedHashSet<String>()
        val formRegex = Regex("(?is)<form\\b[^>]*action=[\\\"']([^\\\"']+)[\\\"'][^>]*>(.*?)</form>")
        val inputRegex = Regex("(?is)<input\\b[^>]*>")
        val nameRegex = Regex("(?i)\\bname=[\\\"']([^\\\"']+)[\\\"']")
        val signalRegex = Regex("(?i)(search|جستجو|آهنگ|song|track|music|keyword|query|term|title)")

        formRegex.findAll(html).forEach { form ->
            val action = form.groupValues[1]
            val body = form.groupValues[2]
            val input = inputRegex.findAll(body)
                .mapNotNull { input -> nameRegex.find(input.value)?.groupValues?.getOrNull(1) }
                .firstOrNull { name -> signalRegex.containsMatchIn(name) || body.contains(name, ignoreCase = true) }
                ?: inputRegex.findAll(body)
                    .mapNotNull { input -> nameRegex.find(input.value)?.groupValues?.getOrNull(1) }
                    .firstOrNull()
                ?: return@forEach

            val base = when {
                action.startsWith("http://") || action.startsWith("https://") -> action
                action.startsWith("/") -> "$host$action"
                else -> "$host/${action.trimStart('/')}"
            }
            urls += if (base.contains("?")) "$base&$input=$encoded" else "$base?$input=$encoded"
        }
        return urls.toList()
    }

    private fun encode(query: String): String =
        URLEncoder.encode(query, StandardCharsets.UTF_8.toString())
}
