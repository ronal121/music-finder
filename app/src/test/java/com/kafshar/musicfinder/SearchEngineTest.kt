package com.kafshar.musicfinder

import org.junit.Assert.assertEquals
import org.junit.Assert.assertTrue
import org.junit.Test

class SearchEngineTest {
    @Test fun normalizesWhitespaceAndArabicVariants() {
        assertEquals("michael jackson", SearchEngine.normalizeQuery("  Michael   Jackson  "))
        assertEquals("ی", SearchEngine.normalizeQuery("ي"))
        assertEquals("عارف سلطان قلبها", SearchEngine.normalizeQuery("عارف سلطان قلب‌ها"))
    }
    @Test fun correctsKnownTypos() {
        assertEquals("michael jackson", SearchEngine.correctedQuery("michal jakson"))
        assertEquals("billie jean", SearchEngine.correctedQuery("BILL JIN"))
        assertEquals("aref", SearchEngine.correctedQuery("areff"))
    }
    @Test fun fuzzyMatchingRecognizesTypos() {
        assertTrue(SearchEngine.similarity("michael", "michal") >= 75)
        assertTrue(SearchEngine.similarity("billie jean", "bill jin") >= 50)
    }
    @Test fun producesFallbackSuggestions() {
        val suggestions = SearchEngine.suggestions("عارف")
        assertTrue(suggestions.any { it.contains("آهنگ") })
        assertEquals(suggestions.distinct(), suggestions)
    }
    @Test fun parsesArtistAndTitle() {
        val parsed = SearchEngine.parseArtistTitle("Adele - Hello")
        assertEquals("Adele", parsed.first)
        assertEquals("Hello", parsed.second)
    }
    @Test fun recognizesSingleWordArtist() {
        assertEquals(SearchEngine.Intent.ARTIST, SearchEngine.detectIntent("عارف"))
    }
    @Test fun buildsArtistAwareGoogleQuery() {
        val query = ServerConfig.searchQuery("عارف")
        assertTrue(query.contains("آهنگ"))
        assertTrue(query.contains("خواننده"))
        assertTrue(query.contains("music"))
    }
}
