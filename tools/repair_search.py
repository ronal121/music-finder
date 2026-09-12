from pathlib import Path

MAIN = Path("app/src/main/java/com/kafshar/musicfinder/MainActivity.kt")


def replace_method(text: str, signature: str, replacement: str) -> str:
    start = text.find(signature)
    if start < 0:
        raise SystemExit(f"missing method: {signature}")
    brace = text.find("{", start)
    depth = 0
    in_string = False
    escaped = False
    for i in range(brace, len(text)):
        c = text[i]
        if in_string:
            if escaped:
                escaped = False
            elif c == "\\":
                escaped = True
            elif c == '"':
                in_string = False
        else:
            if c == '"':
                in_string = True
            elif c == '{':
                depth += 1
            elif c == '}':
                depth -= 1
                if depth == 0:
                    return text[:start] + replacement.rstrip() + text[i + 1:]
    raise SystemExit(f"unterminated method: {signature}")


def add_field(text: str, marker: str, field: str) -> str:
    if field.strip() in text:
        return text
    return text.replace(marker, marker + "\n" + field, 1)


def main():
    text = MAIN.read_text(encoding="utf-8")
    text = text.replace(
        "data class SongResult(\n    val url: String,\n    val title: String,\n    val artist: String,\n    val site: String,\n    val cover: String = \"\",\n    val isYouTube: Boolean = false\n)",
        "data class SongResult(\n    val url: String,\n    val title: String,\n    val artist: String,\n    val site: String,\n    val cover: String = \"\",\n    val isYouTube: Boolean = false,\n    val referer: String = \"\"\n)"
    )
    text = add_field(text, "    private var searchGeneration = 0", "    private val parallelPageInspector = ParallelPageInspector(4)\n    private var parallelBatchRunning = false\n    private val dynamicPages = ArrayDeque<ParallelPageInspector.Page>()\n    private var dynamicPageBusy = false\n    private var dynamicSearchFinished = false\n    private val failedPlaybackUrls = mutableSetOf<String>()")

    text = replace_method(text, "    private fun searchMusic()", '''    private fun searchMusic() {
        if (destroyed) return
        val text = query.text.toString().trim()
        if (text.isEmpty()) {
            Toast.makeText(this, "نام آهنگ یا خواننده را وارد کنید", Toast.LENGTH_SHORT).show()
            return
        }
        searchGeneration++
        val generation = searchGeneration
        cancelSearchCallbacks()
        parallelPageInspector.cancel()
        parallelBatchRunning = false
        dynamicPages.clear()
        dynamicPageBusy = false
        dynamicSearchFinished = false
        failedPlaybackUrls.clear()
        resultPages = emptyList()
        resultPageIndex = 0
        resultGeneration = generation
        expectedPageUrl = ""
        siteBatchIndex = 0
        discoveryEngineIndex = 0
        siteSearchQueries = SearchQueryPlanner.build(text)
        songs.clear()
        currentIndex = -1
        currentAudioUrl = ""
        currentSong = null
        resultsContainer.removeAllViews()
        titleText.text = text
        artistText.text = ""
        status.text = "در حال جستجو..."
        seekBar.progress = 0
        currentTimeText.text = "00:00"
        durationText.text = "00:00"
        vinyl.clearCover()
        vinyl.stopRotation()
        val timeout = Runnable {
            if (!destroyed && generation == searchGeneration) {
                dynamicSearchFinished = true
                status.text = if (songs.isEmpty()) "زمان جستجو تمام شد؛ آهنگ قابل پخش پیدا نشد" else "جستجوی منابع بیشتر متوقف شد"
                saveSearchResults()
            }
        }
        searchTimeout = timeout
        handler.postDelayed(timeout, 45_000L)
        loadNextSiteBatch()
    }''')

    text = replace_method(text, "    private fun loadNextSiteBatch()", '''    private fun loadNextSiteBatch() {
        if (destroyed || siteSearchQueries.isEmpty() || discoveryEngineIndex >= discoveryEngines.size) {
            dynamicSearchFinished = true
            finishSearch()
            return
        }
        if (siteBatchIndex >= siteSearchQueries.size) {
            discoveryEngineIndex++
            siteBatchIndex = 0
            if (discoveryEngineIndex >= discoveryEngines.size) {
                dynamicSearchFinished = true
                finishSearch()
                return
            }
        }
        pageTimeout?.let { handler.removeCallbacks(it) }
        pageTimeout = null
        expectedPageUrl = ""
        resultPages = emptyList()
        resultPageIndex = 0
        val generation = searchGeneration
        val provider = discoveryEngines[discoveryEngineIndex]
        val searchQuery = siteSearchQueries[siteBatchIndex++]
        val encoded = try { URLEncoder.encode(searchQuery, "UTF-8") } catch (_: Exception) {
            loadNextSiteBatch(); return
        }
        val url = when (provider) {
            "google" -> "https://www.google.com/search?q=$encoded&num=20&hl=fa&gbv=1"
            "bing" -> "https://www.bing.com/search?q=$encoded&count=20&setlang=fa"
            else -> "https://html.duckduckgo.com/html/?q=$encoded"
        }
        try {
            web.stopLoading()
            web.loadUrl(url)
        } catch (_: Exception) {
            if (generation == searchGeneration) loadNextSiteBatch()
        }
    }''')

    text = replace_method(text, "    private fun loadNextDiscoveryEngine()", '''    private fun loadNextDiscoveryEngine() {
        if (destroyed || searchGeneration <= 0) return
        discoveryEngineIndex++
        siteBatchIndex = 0
        loadNextSiteBatch()
    }''')

    text = replace_method(text, "    private fun loadGoogleFallback(text: String, generation: Int)", '''    private fun loadGoogleFallback(text: String, generation: Int) {
        if (destroyed || generation != searchGeneration) return
        discoveryEngineIndex = (discoveryEngineIndex + 1).coerceAtMost(discoveryEngines.lastIndex + 1)
        siteBatchIndex = 0
        loadNextSiteBatch()
    }''')

    text = replace_method(text, "    private fun validateAndAddAudioCandidates(", '''    private fun validateAndAddAudioCandidates(
        title: String,
        artist: String,
        cover: String,
        candidates: List<String>,
        pageUrl: String
    ) {
        if (candidates.isEmpty() || destroyed) return
        val generation = searchGeneration
        candidates.distinct().take(80).forEach { url ->
            io.execute {
                val validation = MediaProbe.probe(url, pageUrl)
                if (!validation.playable) return@execute
                val finalUrl = validation.finalUrl.ifBlank { url }
                if (!ServerConfig.isAllowedMediaUrl(finalUrl, pageUrl)) return@execute
                runOnUiThread {
                    if (destroyed || generation != searchGeneration) return@runOnUiThread
                    if (songs.any { it.url == finalUrl }) return@runOnUiThread
                    val song = SongResult(finalUrl, title, artist, getSiteName(pageUrl), cover, false, pageUrl)
                    songs.add(song)
                    addSongView(song, songs.lastIndex)
                    if (currentIndex == -1) {
                        currentIndex = songs.lastIndex
                        playSong(song)
                    }
                }
            }
        }
    }''')

    text = replace_method(text, "    private fun probeMediaUrl(url: String, pageUrl: String): Boolean", '''    private fun probeMediaUrl(url: String, pageUrl: String): Boolean {
        return MediaProbe.probe(url, pageUrl).playable
    }''')

    text = replace_method(text, "    private fun processNextResultPage()", '''    private fun processNextResultPage() {
        if (destroyed || resultGeneration != searchGeneration || parallelBatchRunning) return
        if (resultPageIndex >= resultPages.size) {
            loadNextSiteBatch()
            return
        }
        val generation = searchGeneration
        val start = resultPageIndex
        val end = minOf(resultPageIndex + 10, resultPages.size)
        resultPageIndex = end
        val pages = resultPages.subList(start, end).mapNotNull { raw ->
            val parts = raw.split("|||", limit = 2)
            val url = parts.getOrNull(0).orEmpty().trim()
            if (!ServerConfig.isAllowedPageUrl(url)) null else ParallelPageInspector.Page(url, parts.getOrNull(1).orEmpty())
        }
        if (pages.isEmpty()) return processNextResultPage()
        parallelBatchRunning = true
        status.text = "در حال بررسی ${pages.size} منبع..."
        parallelPageInspector.inspect(
            generation,
            pages,
            { it == searchGeneration && !destroyed },
            { inspection ->
                runOnUiThread {
                    if (destroyed || generation != searchGeneration) return@runOnUiThread
                    if (inspection.candidates.isNotEmpty()) {
                        validateAndAddAudioCandidates(inspection.title, inspection.artist, inspection.cover, inspection.candidates, inspection.page.url)
                    } else if (dynamicPages.none { it.url == inspection.page.url }) {
                        dynamicPages.addLast(inspection.page)
                    }
                }
            },
            {
                runOnUiThread {
                    if (destroyed || generation != searchGeneration) return@runOnUiThread
                    parallelBatchRunning = false
                    startNextDynamicPage()
                    if (resultPageIndex < resultPages.size) processNextResultPage() else loadNextSiteBatch()
                }
            }
        )
    }''')

    text = replace_method(text, "    private fun finishCurrentResultPage()", '''    private fun finishCurrentResultPage() {
        pageTimeout?.let { handler.removeCallbacks(it) }
        pageTimeout = null
        dynamicPageBusy = false
        startNextDynamicPage()
    }''')

    text = replace_method(text, "    private fun finishSearch()", '''    private fun finishSearch() {
        if (destroyed || !dynamicSearchFinished || parallelBatchRunning) return
        if (dynamicPages.isNotEmpty() || dynamicPageBusy) {
            startNextDynamicPage()
            return
        }
        cancelSearchCallbacks()
        status.text = if (songs.isEmpty()) "آهنگ قابل پخش پیدا نشد" else "${songs.size} آهنگ قابل پخش پیدا شد"
        if (songs.isNotEmpty() && currentIndex < 0) currentIndex = 0
        saveSearchResults()
    }''')

    text = replace_method(text, "    private fun saveSearchResults()", '''    private fun saveSearchResults() {
        if (!destroyed) SongResultStore.save(this, songs)
    }''')

    text = replace_method(text, "    private fun restoreSearchResults()", '''    private fun restoreSearchResults() {
        songs.clear()
        songs.addAll(SongResultStore.restore(this).filter { it.isYouTube || ServerConfig.isAllowedMediaUrl(it.url, it.referer.ifBlank { null }) })
        songs.forEachIndexed { index, song -> addSongView(song, index) }
        if (songs.isNotEmpty()) currentIndex = 0
    }''')

    if "private fun startNextDynamicPage()" not in text:
        anchor = "    private fun finishCurrentResultPage()"
        helper = '''    private fun startNextDynamicPage() {
        if (destroyed || dynamicPageBusy) return
        val next = dynamicPages.removeFirstOrNull()
        if (next == null) {
            if (dynamicSearchFinished && !parallelBatchRunning) finishSearch()
            return
        }
        dynamicPageBusy = true
        expectedPageUrl = next.url
        capturedRuntimeUrls.clear()
        pageTimeout?.let { handler.removeCallbacks(it) }
        val generation = searchGeneration
        val timeout = Runnable { if (!destroyed && generation == searchGeneration) finishCurrentResultPage() }
        pageTimeout = timeout
        handler.postDelayed(timeout, 7500L)
        try { web.loadUrl(next.url) } catch (_: Exception) { finishCurrentResultPage() }
    }

'''
        text = text.replace(anchor, helper + anchor, 1)

    # Tighten WebView navigation policy without breaking search-engine pages.
    old = '''                    return !(\n                        url.contains(\n                            "google.com",\n                            true\n                        ) ||\n                        ServerConfig.isAllowedPageUrl(\n                            url\n                        )\n                    )'''
    new = '''                    return !(ServerConfig.isAllowedPageUrl(url) ||\n                        url.contains("google.com", true) ||\n                        url.contains("bing.com", true) ||\n                        url.contains("duckduckgo.com", true))'''
    text = text.replace(old, new, 1)

    # Runtime page inspection must release its WebView slot after parsing.
    target = '''                validateAndAddAudioCandidates(\n                    parsed.title.ifBlank { "Music" },\n                    parsed.artist.ifBlank { "Unknown Artist" },\n                    parsed.cover,\n                    candidates,\n                    expectedPageUrl\n                )\n            }\n        }'''
    replacement = '''                validateAndAddAudioCandidates(\n                    parsed.title.ifBlank { "Music" },\n                    parsed.artist.ifBlank { "Unknown Artist" },\n                    parsed.cover,\n                    candidates,\n                    expectedPageUrl\n                )\n                finishCurrentResultPage()\n            }\n        }'''
    text = text.replace(target, replacement, 1)

    marker = "                if (mediaUrl.isNotBlank()) {\n                    updateActiveResultHighlight(mediaUrl)\n                }"
    if "playNextValidatedAfterFailure" not in text:
        text = text.replace(marker, marker + "\n\n                val playbackError = intent.getStringExtra(\"error\").orEmpty()\n                if (playbackError.isNotBlank()) handler.post { playNextValidatedAfterFailure(mediaUrl) }", 1)
        anchor = "    private fun updatePlayerProgress("
        helper = '''    private fun playNextValidatedAfterFailure(failedUrl: String) {
        if (failedUrl.isBlank() || failedPlaybackUrls.contains(failedUrl) || songs.isEmpty()) return
        failedPlaybackUrls += failedUrl
        val start = currentIndex.coerceAtLeast(0)
        for (offset in 1..songs.size) {
            val index = (start + offset) % songs.size
            val candidate = songs[index]
            if (!candidate.isYouTube && !failedPlaybackUrls.contains(candidate.url)) {
                currentIndex = index
                playSong(candidate)
                return
            }
        }
    }

'''
        text = text.replace(anchor, helper + anchor, 1)

    text = text.replace("        cancelSearchCallbacks()\n\n        cancelDownloadRequested", "        cancelSearchCallbacks()\n        parallelPageInspector.shutdown()\n\n        cancelDownloadRequested", 1)

    # Pass the discovered page as Referer to Media3.
    text = text.replace("                putExtra(\n                    MusicService.EXTRA_COVER,\n                    cover\n                )", "                putExtra(\n                    MusicService.EXTRA_COVER,\n                    cover\n                )\n\n                putExtra(\n                    \"referer\",\n                    currentSong?.referer.orEmpty()\n                )", 1)

    MAIN.write_text(text, encoding="utf-8")
    print("Applied architecture-level search repair.")


if __name__ == "__main__":
    main()
