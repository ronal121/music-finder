package com.kafshar.musicfinder

import org.junit.Assert.assertTrue
import org.junit.Test

class DirectSiteSearchTest {
    @Test fun commonPatternsCoverMultipleSearchConventions() {
        val urls = DirectSearchPatterns.templates("example.com", "ابی گل یخ")
        assertTrue(urls.any { it.contains("?s=") })
        assertTrue(urls.any { it.contains("/search?q=") })
        assertTrue(urls.any { it.contains("/search?query=") })
        assertTrue(urls.any { it.contains("keyword=") })
        assertTrue(urls.any { it.contains("/search/") })
    }

    @Test fun homepageFormSearchParameterIsDiscovered() {
        val html = """
            <html><body>
              <form action="/find" method="get">
                <input type="text" name="track" placeholder="Search song">
                <button>Search</button>
              </form>
            </body></html>
        """.trimIndent()

        val urls = DirectSearchPatterns.fromSearchForms(
            html,
            "https://example.com/",
            "ابی گل یخ"
        )

        assertTrue(urls.any { it.startsWith("https://example.com/find?") && it.contains("track=") })
    }

    @Test fun searchInputCanBeIdentifiedByPersianPlaceholder() {
        val html = """
            <form action="/jostojoo" method="get">
                <input type="text" name="term" placeholder="جستجوی آهنگ">
            </form>
        """.trimIndent()

        val urls = DirectSearchPatterns.fromSearchForms(
            html,
            "https://example.com/",
            "کاش که به شهر شما سفر نمیکردم"
        )

        assertTrue(urls.any { it.contains("/jostojoo?") && it.contains("term=") })
    }
}
