package com.kafshar.musicfinder

import org.junit.Assert.assertEquals
import org.junit.Assert.assertTrue
import org.junit.Test

class SearchArchitectureTest {
    @Test fun persianVariantsStayBoundedAndDoNotUseSearchEngines() {
        val query = "کاش که به شهر شما سفر نمیکردم"
        val variants = SearchQueryPlanner.build(query)

        assertTrue(variants.isNotEmpty())
        assertTrue(variants.size <= 3)
        assertTrue(variants.any { it.contains(query) })
        assertTrue(variants.none { it.contains("site:", ignoreCase = true) })
        assertTrue(variants.none { it.contains("google", ignoreCase = true) })
    }

    @Test fun searchNetworkUsesOnlyConfiguredMusicSources() {
        assertEquals(1, SearchNetwork.providers.size)
        assertEquals("Music sites", SearchNetwork.providers.single().name)
        assertTrue(MusicSitePool.domains.isNotEmpty())
        assertTrue(MusicSitePool.domains.size >= 400)
        assertTrue(MusicSitePool.domains.size <= 500)
        assertTrue(MusicSitePool.domains.all { !it.contains("google.") })
    }

    @Test fun arabicAndPersianCharactersNormalizeEqually() {
        assertTrue(SearchEngine.normalizeQuery("ابی گل یخ") == SearchEngine.normalizeQuery("ابي گل يَخ"))
        assertTrue(SearchEngine.normalizeQuery("كاش") == SearchEngine.normalizeQuery("کاش"))
    }

    @Test fun rankingPrefersSongLikeExactTitle() {
        val exact = SearchRanking.webScore("ابی گل یخ", "ابی - گل یخ", "https://example.com/music/abi-gole-yakh", false)
        val category = SearchRanking.webScore("ابی گل یخ", "نتایج جستجو", "https://example.com/category/music", false)
        assertTrue(exact > category)
    }

    @Test fun localAndPrivateTargetsAreRejected() {
        assertTrue(!ServerConfig.isAllowedPageUrl("http://127.0.0.1/song.mp3"))
        assertTrue(!ServerConfig.isAllowedPageUrl("http://192.168.1.10/song.mp3"))
        assertTrue(!ServerConfig.isAllowedPageUrl("file:///sdcard/song.mp3"))
        assertTrue(!ServerConfig.isAllowedMediaUrl("http://127.0.0.1/song.mp3", "https://example.com/page"))
    }

    @Test fun youtubeStaysOutsideDirectMediaPipeline() {
        assertTrue(!ServerConfig.isAllowedMediaUrl("https://www.youtube.com/watch?v=abc123", "https://example.com/page"))
        assertTrue(MediaUrlValidator.classify("https://youtube.com/watch?v=abc123") == MediaUrlValidator.Decision.REJECT_YOUTUBE)
    }
}
