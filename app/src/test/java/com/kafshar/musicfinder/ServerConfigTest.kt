package com.kafshar.musicfinder

import org.junit.Assert.assertFalse
import org.junit.Assert.assertTrue
import org.junit.Test

class ServerConfigTest {
    @Test fun acceptsConfiguredHttpsMusicHost() {
        assertTrue(ServerConfig.isAllowedMediaUrl("https://musicdel.ir/music/song.mp3"))
    }

    @Test fun acceptsDirectAudioFromNewlyDiscoveredHost() {
        assertTrue(
            ServerConfig.isAllowedMediaUrl(
                "https://sahand-example.ir/files/song.mp3",
                "https://sahand-example.ir/music/song"
            )
        )
    }

    @Test fun rejectsUnknownHostWhenMediaIsNotAudio() {
        assertFalse(
            ServerConfig.isAllowedMediaUrl(
                "https://example.com/page.html",
                "https://example.com/page.html"
            )
        )
    }

    @Test fun rejectsCrossHostDirectAudioWhenPageHostIsKnown() {
        assertFalse(
            ServerConfig.isAllowedMediaUrl(
                "https://cdn.example.net/song.mp3",
                "https://example.com/music/song"
            )
        )
    }

    @Test fun acceptsGoogleSearchPage() {
        assertTrue(ServerConfig.isAllowedPageUrl("https://www.google.com/search?q=music"))
    }

    @Test fun acceptsYouTubePage() {
        assertTrue(ServerConfig.isAllowedPageUrl("https://www.youtube.com/watch?v=test"))
        assertTrue(ServerConfig.isYouTubeUrl("https://youtu.be/test"))
    }

    @Test fun rejectsNonHttpSchemes() {
        assertFalse(ServerConfig.isAllowedMediaUrl("file:///sdcard/song.mp3"))
        assertFalse(ServerConfig.isAllowedPageUrl("javascript:alert(1)"))
    }

    @Test fun rejectsYouTubeWatchUrlAsMedia() {
        assertFalse(
            ServerConfig.isAllowedMediaUrl(
                "https://www.youtube.com/watch?v=test",
                "https://www.youtube.com/watch?v=test"
            )
        )
    }
}
