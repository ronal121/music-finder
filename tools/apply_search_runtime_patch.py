from pathlib import Path

PATH = Path("app/src/main/java/com/kafshar/musicfinder/MainActivity.kt")
text = PATH.read_text(encoding="utf-8")


def replace_method(src, name, replacement):
    marker = f"    private fun {name}("
    start = src.find(marker)
    if start < 0:
        raise SystemExit(f"method not found: {name}")
    brace = src.find("{", start)
    if brace < 0:
        raise SystemExit(f"opening brace not found: {name}")
    depth = 0
    in_string = False
    escape = False
    in_line_comment = False
    in_block_comment = False
    i = brace
    while i < len(src):
        c = src[i]
        n = src[i + 1] if i + 1 < len(src) else ""
        if in_line_comment:
            if c == "\n": in_line_comment = False
        elif in_block_comment:
            if c == "*" and n == "/": in_block_comment = False; i += 1
        elif in_string:
            if escape: escape = False
            elif c == "\\": escape = True
            elif c == '"': in_string = False
        else:
            if c == '/' and n == '/': in_line_comment = True; i += 1
            elif c == '/' and n == '*': in_block_comment = True; i += 1
            elif c == '"': in_string = True
            elif c == '{': depth += 1
            elif c == '}':
                depth -= 1
                if depth == 0:
                    end = i + 1
                    return src[:start] + replacement.rstrip() + src[end:]
        i += 1
    raise SystemExit(f"unbalanced method: {name}")


google = r'''    private fun extractGoogleResults() {
        if (destroyed || searchGeneration <= 0) return
        val script = """
            (function(){
              try {
                var html = document.documentElement ? document.documentElement.outerHTML : document.body.innerHTML;
                MusicFinder.googleHtml(encodeURIComponent(html || ''));
              } catch(e) {
                MusicFinder.googleHtml('');
              }
            })();
        """.trimIndent()
        try { web.evaluateJavascript(script, null) } catch (_: Exception) { finishSearch() }
    }
'''

music = r'''    private fun extractMusicPage(pageUrl: String) {
        if (destroyed || resultGeneration != searchGeneration) return
        expectedPageUrl = pageUrl
        val script = """
            (function(){
              try {
                var html = document.documentElement ? document.documentElement.outerHTML : document.body.innerHTML;
                MusicFinder.pageHtml(encodeURIComponent(html || ''));
              } catch(e) {
                MusicFinder.pageHtml('');
              }
            })();
        """.trimIndent()
        try { web.evaluateJavascript(script, null) } catch (_: Exception) { finishCurrentResultPage() }
    }
'''

# Replace the brittle DOM extraction methods.
text = replace_method(text, "extractGoogleResults", google)
text = replace_method(text, "extractMusicPage", music)

# Add a Kotlin-side Google HTML bridge immediately before Bridge.page().
needle = '''        @JavascriptInterface\n        fun page(raw: String?) {'''
bridge_google = '''        @JavascriptInterface\n        fun googleHtml(raw: String?) {\n            runOnUiThread {\n                if (destroyed || resultGeneration != searchGeneration) return@runOnUiThread\n                val html = try { URLDecoder.decode(raw.orEmpty(), "UTF-8") } catch (_: Exception) { "" }\n                val parsed = GoogleResultParser.parseAnchors(html, 30)\n                resultGeneration = searchGeneration\n                resultPageIndex = 0\n\n                parsed.filter { it.isYouTube }.forEach {\n                    addYouTubeView(it.url, it.title.ifBlank { "YouTube" })\n                }\n\n                resultPages = parsed.filterNot { it.isYouTube }\n                    .map { "${it.url}|||${it.title}" }\n\n                if (resultPages.isEmpty()) {\n                    status.text = "Google نتیجه قابل پردازشی برنگرداند"\n                    finishSearch()\n                } else {\n                    status.text = "${resultPages.size} نتیجه پیدا شد؛ در حال بررسی..."\n                    processNextResultPage()\n                }\n            }\n        }\n\n        @JavascriptInterface\n        fun pageHtml(raw: String?) {\n            runOnUiThread {\n                if (destroyed || resultGeneration != searchGeneration) return@runOnUiThread\n                val html = try { URLDecoder.decode(raw.orEmpty(), "UTF-8") } catch (_: Exception) { "" }\n                if (html.isBlank()) { finishCurrentResultPage(); return@runOnUiThread }\n\n                val parsed = MusicPageParser.parse(html, expectedPageUrl)\n                val candidates = parsed.audioCandidates\n                if (candidates.isEmpty()) {\n                    // Give JS-generated players a second pass before abandoning the page.\n                    handler.postDelayed({\n                        if (!destroyed && resultGeneration == searchGeneration) {\n                            extractMusicPage(expectedPageUrl)\n                        }\n                    }, 900L)\n                    return@runOnUiThread\n                }\n\n                validateAndAddAudioCandidates(\n                    parsed.title.ifBlank { "Music" },\n                    parsed.artist.ifBlank { "Unknown Artist" },\n                    parsed.cover,\n                    candidates,\n                    expectedPageUrl\n                )\n            }\n        }\n\n        @JavascriptInterface\n        fun page(raw: String?) {'''
if needle not in text:
    raise SystemExit("Bridge.page insertion point not found")
text = text.replace(needle, bridge_google, 1)

# Remove the old manual parser path from Bridge.page by leaving it available for compatibility; pageHtml is now authoritative.
# Make page navigation wait for dynamic content before the first extraction.
old = '''                        extractMusicPage(url)'''
new = '''                        handler.postDelayed({\n                            if (!destroyed && resultGeneration == searchGeneration && expectedPageUrl == url) {\n                                extractMusicPage(url)\n                            }\n                        }, 650L)'''
if old not in text:
    raise SystemExit("onPageFinished extraction call not found")
text = text.replace(old, new, 1)

# Increase page timeout slightly so the dynamic second pass can complete.
text = text.replace('            5000L\n        )', '            7500L\n        )', 1)

# Tolerant media probing: HEAD failures, missing MIME, and Range refusal should not reject a URL that is strongly media-shaped.
old_probe = '''        val type = request("HEAD") ?: request("GET")\n        return type?.startsWith("audio/") == true ||\n            (type?.startsWith("video/") == true && url.contains("audio", true)) ||\n            ServerConfig.looksLikeAudioUrl(url)'''
new_probe = '''        val headType = request("HEAD")\n        val type = headType ?: request("GET")\n        val mimeAccept = type?.startsWith("audio/") == true ||\n            (type?.startsWith("video/") == true && url.contains("audio", true))\n        // A lot of CDNs reject HEAD or Range while still serving the media normally.\n        return mimeAccept || ServerConfig.looksLikeAudioUrl(url)'''
if old_probe not in text:
    raise SystemExit("probe block not found")
text = text.replace(old_probe, new_probe, 1)

# Add a deterministic runtime marker so this patch is idempotent.
marker = "// SEARCH_RUNTIME_PARSER_WIRED"
if marker not in text:
    text = text.replace('class MainActivity : Activity() {', 'class MainActivity : Activity() {\n\n    ' + marker, 1)

PATH.write_text(text, encoding="utf-8")
print("Search runtime parser wiring applied")
