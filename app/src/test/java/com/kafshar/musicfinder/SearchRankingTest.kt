package com.kafshar.musicfinder

import org.junit.Assert.assertEquals
import org.junit.Assert.assertTrue
import org.junit.Test

class SearchRankingTest {
    @Test fun playableExactResultWins() {
        val exact = SearchResult(title = "Billie Jean", artist = "Michael Jackson", mediaUrl = "https://musicdel.ir/a.mp3", isPlayable = true)
        val partial = SearchResult(title = "Billie Jean Remix", artist = "Unknown")
        val ranked = SearchRanking.rank("Billie Jean", listOf(partial, exact))
        assertEquals(exact.title, ranked.first().title)
        assertTrue(ranked.first().score > ranked.last().score)
    }

    @Test fun duplicateTitleArtistIsCollapsed() {
        val a = SearchResult(title = "Hello", artist = "Adele", score = 1)
        val b = SearchResult(title = "Hello", artist = "Adele", mediaUrl = "https://musicdel.ir/hello.mp3", isPlayable = true)
        assertEquals(1, SearchRanking.rank("Adele Hello", listOf(a, b)).size)
    }
}
