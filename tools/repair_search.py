from pathlib import Path

ROOT = Path("app/src/main/java/com/kafshar/musicfinder")
MAIN = ROOT / "MainActivity.kt"
GP = ROOT / "GoogleResultParser.kt"
MP = ROOT / "MusicPageParser.kt"
SE = ROOT / "SearchEngine.kt"


def replace_once(path, old, new):
    text = path.read_text(encoding="utf-8")
    if old not in text:
        raise SystemExit(f"missing target in {path}: {old[:120]!r}")
    path.write_text(text.replace(old, new, 1), encoding="utf-8")


# Discovery layer: search engines discover candidate pages; they are never final audio sources.
replace_once(MAIN, '''    private var googleFallbackUsed = false

    private var resultPages: List<String> = emptyList()
''', '''    private var googleFallbackUsed = false
    private var discoveryEngineIndex = 0
    private val discoveryEngines = listOf("google", "bing", "duckduckgo")

    private var resultPages: List<String> = emptyList()
''')

replace_once(MAIN, '''                if (resultPages.isEmpty()) {
                    status.text = "Google نتیجه قابل پردازشی برنگرداند"
                    finishSearch()
                } else {
''', '''                if (resultPages.isEmpty()) {
                    loadNextDiscoveryEngine()
                } else {
''')

replace_once(MAIN, '''        googleFallbackUsed = false
        resultGeneration = generation
''', '''        googleFallbackUsed = false
        discoveryEngineIndex = 0
        resultGeneration = generation
''')

text = MAIN.read_text(encoding="utf-8")
start = text.index("    private fun loadGoogleFallback(text: String, generation: Int) {")
end = text.index("\n    private fun cancelSearchCallbacks()", start)
newblock = '''    private fun loadGoogleFallback(text: String, generation: Int) {
        if (destroyed || generation != searchGeneration) return
        discoveryEngineIndex = 0
        loadNextDiscoveryEngine()
    }

    private fun loadNextDiscoveryEngine() {
        val generation = searchGeneration
        if (destroyed || generation <= 0 || generation != resultGeneration) return
        if (discoveryEngineIndex >= discoveryEngines.size) {
            finishSearch()
            return
        }

        val engine = discoveryEngines[discoveryEngineIndex++]
        val raw = query.text.toString().trim()
        val q = SearchEngine.buildGoogleQuery(raw)
        val encoded = try { URLEncoder.encode(q, "UTF-8") } catch (_: Exception) { "" }
        if (encoded.isBlank()) { loadNextDiscoveryEngine(); return }

        val url = when (engine) {
            "google" -> "https://www.google.com/search?q=$encoded&num=50&hl=en&gbv=1"
            "bing" -> "https://www.bing.com/search?q=$encoded&count=50&setlang=en"
            else -> "https://html.duckduckgo.com/html/?q=$encoded"
        }

        status.text = when (engine) {
            "google" -> "در حال جستجوی Google..."
            "bing" -> "Google نتیجه کافی نداد؛ در حال جستجوی Bing..."
            else -> "Bing نتیجه کافی نداد؛ در حال جستجوی DuckDuckGo..."
        }
        resultPages = emptyList()
        resultPageIndex = 0
        expectedPageUrl = ""
        capturedRuntimeUrls.clear()
        try {
            web.stopLoading()
            web.loadUrl(url)
        } catch (_: Exception) {
            loadNextDiscoveryEngine()
        }
    }
'''
MAIN.write_text(text[:start] + newblock + text[end:], encoding="utf-8")

replace_once(MAIN, '''                    if (
                        url.contains(
                            "google.com/search",
                            true
                        )
                    ) {
''', '''                    if (
                        url.contains("google.com/search", true) ||
                        url.contains("bing.com/search", true) ||
                        url.contains("duckduckgo.com/html", true)
                    ) {
''')

text = MAIN.read_text(encoding="utf-8").replace(".parseAnchors(html, 30)", ".parseAnchors(html, 50)", 1)
MAIN.write_text(text, encoding="utf-8")

# Search-result redirect normalization.
replace_once(GP, '''            if (host.contains("google.")) {
                val query = parseQuery(resolved.rawQuery)
                val target = listOf(query["q"], query["url"], query["u"], query["uddg"])
                    .firstOrNull { !it.isNullOrBlank() }
                    ?.trim()

                if (!target.isNullOrBlank() && target.startsWith("http", true)) {
                    return decodeUrlRepeatedly(target)
                        .takeIf { it.startsWith("http://", true) || it.startsWith("https://", true) }
                }
            }

            resolved.toString().takeIf {
                it.startsWith("http://", true) || it.startsWith("https://", true)
            }
''', '''            if (host.contains("google.") || host.contains("bing.com") || host.contains("duckduckgo.com") || host.contains("yahoo.com")) {
                val query = parseQuery(resolved.rawQuery)
                val target = listOf(query["q"], query["url"], query["u"], query["uddg"], query["r"], query["target"])
                    .firstOrNull { !it.isNullOrBlank() && it.startsWith("http", true) }
                    ?.trim()
                if (!target.isNullOrBlank()) {
                    return decodeUrlRepeatedly(target)
                        .takeIf { it.startsWith("http://", true) || it.startsWith("https://", true) }
                }
                if (host.contains("bing.com") || host.contains("duckduckgo.com") || host.contains("yahoo.com")) return null
            }

            resolved.toString().takeIf {
                it.startsWith("http://", true) || it.startsWith("https://", true)
            }
''')

# Page parser: domain-agnostic media extraction and embedded-player discovery.
replace_once(MP, '''data class ParsedMusicPage(
    val title: String,
    val artist: String,
    val cover: String,
    val audioCandidates: List<String>
)
''', '''data class ParsedMusicPage(
    val title: String,
    val artist: String,
    val cover: String,
    val audioCandidates: List<String>,
    val pageCandidates: List<String> = emptyList()
)
''')

replace_once(MP, '''        val candidates = LinkedHashSet<String>()

        // Standard HTML5 media plus preload/link hints.
        val mediaTag = Regex(
            "<(?:audio|video|source|a|link)\\\\b[^>]*>",
            RegexOption.IGNORE_CASE
        )
''', '''        val candidates = LinkedHashSet<String>()
        val pageCandidates = LinkedHashSet<String>()

        // Standard HTML5 media, links, and embedded player frames.
        val mediaTag = Regex(
            "<(?:audio|video|source|a|link|iframe)\\\\b[^>]*>",
            RegexOption.IGNORE_CASE
        )
''')

replace_once(MP, '''        for (m in mediaTag.findAll(normalizedHtml)) {
            collectAttributes(m.value, pageUrl, candidates)
        }

        // OpenGraph/Twitter audio metadata and other common meta names.
''', '''        for (m in mediaTag.findAll(normalizedHtml)) {
            val tag = m.value
            val tagName = Regex("^<([a-zA-Z0-9]+)").find(tag)?.groupValues?.getOrNull(1)?.lowercase().orEmpty()
            if (tagName == "iframe") {
                val src = attrValue(tag, "src")
                normalizeUrl(src, pageUrl)?.let { frame ->
                    if (!ServerConfig.isObviousNonMediaUrl(frame)) pageCandidates += frame
                }
            } else {
                collectAttributes(tag, pageUrl, candidates)
            }
        }

        // Relative media URLs are common in player JavaScript and JSON.
        val relativeMedia = Regex(
            "(?:[\\\\\"'])(/(?:[^\\\\\"'<>\\\\\\\\ ]{1,500}(?:\\\\.(?:mp3|m4a|aac|ogg|oga|opus|wav|flac|webm)(?:[?#&][^\\\\\"'<>\\\\\\\\ ]*)?|/(?:download|dl|stream|audio|media)(?:/|[?#&]))))",
            RegexOption.IGNORE_CASE
        )
        for (m in relativeMedia.findAll(normalizedHtml)) {
            normalizeUrl(m.groupValues[1], pageUrl)?.let { candidate ->
                if (!ServerConfig.isObviousNonMediaUrl(candidate)) candidates += candidate
            }
        }

        // OpenGraph/Twitter audio metadata and other common meta names.
''')

replace_once(MP, '''        return ParsedMusicPage(
            title.trim().take(300),
            artist.trim().take(200),
            cover.trim(),
            candidates.take(80)
        )
''', '''        return ParsedMusicPage(
            title.trim().take(300),
            artist.trim().take(200),
            cover.trim(),
            candidates.take(100),
            pageCandidates.distinct().take(6)
        )
''')

# MainActivity: if a page embeds a separate player, add that player page to the same candidate queue.
replace_once(MAIN, '''                val candidates = (parsed.audioCandidates + runtimeCandidates)
                    .distinct()
                    .take(160)
                if (candidates.isEmpty()) {
                    handler.postDelayed({
                        if (!destroyed && resultGeneration == searchGeneration && expectedPageUrl.isNotBlank()) {
                            extractMusicPage(expectedPageUrl)
                        }
                    }, 1200L)
                    return@runOnUiThread
                }

                validateAndAddAudioCandidates(
''', '''                val candidates = (parsed.audioCandidates + runtimeCandidates)
                    .distinct()
                    .take(160)

                if (parsed.pageCandidates.isNotEmpty()) {
                    val additions = parsed.pageCandidates
                        .filter { ServerConfig.isAllowedPageUrl(it) }
                        .map { "$it|||Embedded player" }
                    resultPages = (resultPages + additions)
                        .distinctBy { it.substringBefore("|||").substringBefore("#").trimEnd('/').lowercase() }
                        .take(30)
                }

                if (candidates.isEmpty()) {
                    if (parsed.pageCandidates.isNotEmpty()) {
                        finishCurrentResultPage()
                    } else {
                        handler.postDelayed({
                            if (!destroyed && resultGeneration == searchGeneration && expectedPageUrl.isNotBlank()) {
                                extractMusicPage(expectedPageUrl)
                            }
                        }, 1200L)
                    }
                    return@runOnUiThread
                }

                validateAndAddAudioCandidates(
''')

# Any HTTP(S) page discovered by a search engine may be navigated to. Do not special-case Google only.
replace_once(MAIN, '''                override fun shouldOverrideUrlLoading(
                    view: WebView,
                    request: WebResourceRequest
                ): Boolean {
                    val url =
                        request.url.toString()

                    return !(
                        url.contains(
                            "google.com",
                            true
                        ) ||
                        ServerConfig.isAllowedPageUrl(
                            url
                        )
                    )
                }
''', '''                override fun shouldOverrideUrlLoading(
                    view: WebView,
                    request: WebResourceRequest
                ): Boolean {
                    val url = request.url.toString()
                    return !ServerConfig.isAllowedPageUrl(url)
                }
''')

# Query understanding: preserve the original query while allowing corrected/clean variants.
replace_once(SE, '''    fun buildGoogleQuery(input: String): String {
        val original = displayQuery(input)
        if (original.isBlank()) return "music"
        val corrected = correctedQuery(original)
        if (corrected.isNotBlank() && corrected != normalizeQuery(original) && corrected != original) {
            // Do not require either spelling to contain a music keyword. Google can
            // then return lyric pages, artist pages and download pages alike.
            return "($original OR $corrected)"
        }
        return original
    }
''', '''    fun buildGoogleQuery(input: String): String {
        val original = displayQuery(input)
        if (original.isBlank()) return "music"
        val corrected = correctedQuery(original)
        val normalized = normalizeQuery(original)
        val variants = linkedSetOf<String>()
        variants += original
        if (corrected.isNotBlank() && corrected != normalized && corrected != original) {
            variants += corrected
        }
        val clean = withoutSearchNoise(corrected)
        if (clean.isNotBlank() && clean != normalized && clean != corrected) {
            variants += clean
        }
        return when {
            variants.size == 1 -> original
            variants.size == 2 -> "(${variants.joinToString(" OR ")})"
            else -> "(${variants.take(3).joinToString(" OR ")})"
        }
    }
''')

print("deep discovery/page-inspection repair applied")
