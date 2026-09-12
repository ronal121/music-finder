from pathlib import Path

ROOT = Path('app/src/main/java/com/kafshar/musicfinder')
MAIN = ROOT / 'MainActivity.kt'
GP = ROOT / 'GoogleResultParser.kt'


def replace_once(path, old, new):
    text = path.read_text(encoding='utf-8')
    if old not in text:
        raise SystemExit(f'missing target in {path}')
    path.write_text(text.replace(old, new, 1), encoding='utf-8')

# MainActivity: make discovery multi-engine instead of Google-only.
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

text = MAIN.read_text(encoding='utf-8')
start = text.index('    private fun loadGoogleFallback(text: String, generation: Int) {')
end = text.index('\n    private fun cancelSearchCallbacks()', start)
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
MAIN.write_text(text[:start] + newblock + text[end:], encoding='utf-8')

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

text = MAIN.read_text(encoding='utf-8').replace('.parseAnchors(html, 30)', '.parseAnchors(html, 50)', 1)
MAIN.write_text(text, encoding='utf-8')

# Search-result parser: unwrap redirect URLs from Google/Bing/DDG/Yahoo.
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

print('search discovery repair applied')
