package com.kafshar.musicfinder

import org.junit.Assert.assertFalse
import org.junit.Assert.assertTrue
import org.junit.Test

class ServerConfigTest {
    @Test fun acceptsConfiguredHttpsMusicHost() {
        assertTrue(ServerConfig.isAllowedMediaUrl("https://musicdel.ir/music/song.mp3"))
    }

    @Test fun rejectsUnknownMediaHost() {
        assertFalse(ServerConfig.isAllowedMediaUrl("https://example.com/song.mp3"))
    }

    @Test fun acceptsGoogleSearchPage() {
        assertTrue(ServerConfig.isAllowedPageUrl("https://www.google.com/search?q=music"))
    }

    @Test fun rejectsNonHttpSchemes() {
        assertFalse(ServerConfig.isAllowedMediaUrl("file:///sdcard/song.mp3"))
        assertFalse(ServerConfig.isAllowedPageUrl("javascript:alert(1)"))
    }
}
