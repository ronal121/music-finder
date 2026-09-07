package com.kafshar.musicfinder

import org.junit.Assert.assertEquals
import org.junit.Assert.assertTrue
import org.junit.Test

class GoogleResultParserTest {

    @Test
    fun relativeGoogleRedirectWithEmptyQUsesUrlParameter() {
        val actual = GoogleResultParser.normalizeUrl(
            "/url?q=&url=https%3A%2F%2Fexample.com%2Fsong.mp3"
        )
        assertEquals("https://example.com/song.mp3", actual)
    }

    @Test
    fun relativeGoogleRedirectWithQUsesDestination() {
        val actual = GoogleResultParser.normalizeUrl(
            "/url?sa=t&q=https%3A%2F%2Fexample.com%2Fsong"
        )
        assertEquals("https://example.com/song", actual)
    }

    @Test
    fun googleRedirectSupportsUParameter() {
        val actual = GoogleResultParser.normalizeUrl(
            "https://www.google.com/url?u=https%3A%2F%2Fexample.com%2Fmusic"
        )
        assertEquals("https://example.com/music", actual)
    }

    @Test
    fun parserExtractsExternalResultsFromRelativeRedirects() {
        val html = """
            <html><body>
              <a href="/url?q=&amp;url=https%3A%2F%2Fexample.com%2Fsong">Example Song</a>
              <a href="https://www.google.com/search?q=internal">Google</a>
            </body></html>
        """.trimIndent()

        val results = GoogleResultParser.parseAnchors(html)

        assertEquals(1, results.size)
        assertEquals("https://example.com/song", results.single().url)
        assertTrue(results.single().title.contains("Example Song"))
    }

    @Test
    fun parserHandlesNestedMarkupAndDataHref() {
        val html = """
            <a data-href="https://example.com/a">
              <div><span>Artist - Song</span></div>
            </a>
        """.trimIndent()

        val results = GoogleResultParser.parseAnchors(html)

        assertEquals(1, results.size)
        assertEquals("https://example.com/a", results.single().url)
        assertEquals("Artist - Song", results.single().title)
    }
}