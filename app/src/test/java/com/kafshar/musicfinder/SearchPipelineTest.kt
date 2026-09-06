package com.kafshar.musicfinder

import org.junit.Assert.assertEquals
import org.junit.Assert.assertTrue
import org.junit.Test

class SearchPipelineTest {
    @Test fun googleDirectAndRedirectUrlsAreNormalized() {
        assertEquals("https://example.com/song.mp3", GoogleResultParser.normalizeUrl("https://example.com/song.mp3"))
        assertEquals("https://example.com/song.mp3", GoogleResultParser.normalizeUrl("https://www.google.com/url?q=https%3A%2F%2Fexample.com%2Fsong.mp3"))
    }

    @Test fun googleAnchorsAreParsedDedupedAndYoutubeSeparated() {
        val html = """
            <a href="/url?q=https%3A%2F%2Fexample.com%2Fsong"><h3>Song One</h3></a>
            <a href="https://example.com/song"><h3>Duplicate</h3></a>
            <a href="https://www.youtube.com/watch?v=abc12345678"><h3>Song Video</h3></a>
            <a href="https://google.com/preferences">Google</a>
        """.trimIndent()
        val results = GoogleResultParser.parseAnchors(html)
        assertEquals(2, results.size)
        assertTrue(results.any { it.url == "https://example.com/song" && !it.isYouTube })
        assertTrue(results.any { it.isYouTube })
    }

    @Test fun musicPageFindsExtensionlessDownloadAndRelativeMedia() {
        val html = """
            <meta property="og:title" content="Test Song">
            <meta property="music:musician" content="Test Artist">
            <audio data-src="/media/track"></audio>
            <a href="https://cdn.example.com/song.mp3">download</a>
            <script>const stream = "https://cdn.example.com/live?type=audio";</script>
        """.trimIndent()
        val parsed = MusicPageParser.parse(html, "https://example.com/music/page")
        assertEquals("Test Song", parsed.title)
        assertEquals("Test Artist", parsed.artist)
        assertTrue(parsed.audioCandidates.contains("https://example.com/media/track"))
        assertTrue(parsed.audioCandidates.any { it.endsWith("song.mp3") })
        assertTrue(parsed.audioCandidates.any { it.contains("type=audio") })
    }

    @Test fun mediaValidationRejectsYoutubeAndPagesButAcceptsPageCandidates() {
        assertEquals(MediaUrlValidator.Decision.REJECT_YOUTUBE, MediaUrlValidator.classify("https://youtube.com/watch?v=x"))
        assertEquals(MediaUrlValidator.Decision.REJECT_OBVIOUS_PAGE, MediaUrlValidator.classify("https://example.com/song.html"))
        assertEquals(MediaUrlValidator.Decision.ACCEPT_CANDIDATE, MediaUrlValidator.classify("https://example.com/stream?id=42", "https://example.com/song"))
    }

    @Test fun searchEnginePreservesNormalQueryAndCorrectsKnownTypoWithoutDuplication() {
        assertTrue(SearchEngine.buildGoogleQuery("Adele Hello").contains("Adele Hello"))
        val typo = SearchEngine.buildGoogleQuery("michal jackson")
        assertTrue(typo.contains("michal jackson"))
        assertTrue(typo.contains("michael jackson"))
        assertTrue(!typo.contains("michael jackson michael jackson"))
    }
}
