package com.kafshar.musicfinder

import org.junit.Assert.assertEquals
import org.junit.Assert.assertTrue
import org.junit.Test

class SearchEngineTest {
    @Test fun normalizesWhitespaceAndArabicVariants() {
        assertEquals("michael jackson", SearchEngine.normalizeQuery("  Michael   Jackson  "))
        assertEquals("ی", SearchEngine.normalizeQuery("ي"))
    }

    @Test fun correctsKnownTypos() {
        assertEquals("michael jackson", SearchEngine.correctedQuery("michal jakson"))
        assertEquals("billie jean", SearchEngine.correctedQuery("BILL JIN"))
    }

    @Test fun producesFallbackSuggestions() {
        val suggestions = SearchEngine.suggestions("Taylor Swift")
        assertTrue(suggestions.isNotEmpty())
    }

    @Test fun parsesArtistAndTitle() {
        val parsed = SearchEngine.parseArtistTitle("Adele - Hello")
        assertEquals("Adele", parsed.first)
        assertEquals("Hello", parsed.second)
    }
}
