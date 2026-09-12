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
            if escaped: escaped = False
            elif c == "\\": escaped = True
            elif c == '"': in_string = False
        else:
            if c == '"': in_string = True
            elif c == '{': depth += 1
            elif c == '}':
                depth -= 1
                if depth == 0:
                    return text[:start] + replacement.rstrip() + text[i + 1:]
    raise SystemExit(f"unterminated method: {signature}")


def main():
    text = MAIN.read_text(encoding="utf-8")
    text = replace_method(text, "    private fun loadNextSiteBatch()", '''    private fun loadNextSiteBatch() {
        if (destroyed || siteSearchQueries.isEmpty() || discoveryEngineIndex >= SearchNetwork.providers.size) {
            dynamicSearchFinished = true
            finishSearch()
            return
        }
        if (siteBatchIndex >= siteSearchQueries.size) {
            discoveryEngineIndex++
            siteBatchIndex = 0
            if (discoveryEngineIndex >= SearchNetwork.providers.size) {
                dynamicSearchFinished = true
                finishSearch()
                return
            }
        }
        val generation = searchGeneration
        val provider = SearchNetwork.providers[discoveryEngineIndex]
        val searchQuery = siteSearchQueries[siteBatchIndex++]
        status.text = "در حال جستجو در ${provider.name}..."
        searchFuture = io.submit {
            val results = try { provider.search(searchQuery, 20) } catch (_: Exception) { emptyList() }
            if (destroyed || generation != searchGeneration) return@submit
            runOnUiThread {
                if (destroyed || generation != searchGeneration) return@runOnUiThread
                resultGeneration = generation
                resultPageIndex = 0
                val ranked = results
                    .filter { ServerConfig.isAllowedPageUrl(it.url) }
                    .distinctBy { it.url.substringBefore('#').trimEnd('/').lowercase() }
                    .sortedByDescending { SearchRanking.webScore(searchQuery, it.title, it.url, it.isYouTube) }
                    .take(15)
                ranked.filter { it.isYouTube }.forEach { addYouTubeView(it.url, it.title.ifBlank { "YouTube" }) }
                resultPages = ranked.filterNot { it.isYouTube }.map { "${it.url}|||${it.title}" }
                if (resultPages.isEmpty()) loadNextSiteBatch() else processNextResultPage()
            }
        }
    }''')

    text = replace_method(text, "    private fun extractGoogleResults()", '''    private fun extractGoogleResults() {
        if (destroyed || searchGeneration <= 0) return
        val script = """
            (function() {
                try {
                    var links = document.querySelectorAll("a");
                    var found = [];
                    var seen = {};
                    for (var i = 0; i < links.length; i++) {
                        var href = links[i].href || "";
                        var label = links[i].innerText || "";
                        try {
                            var parsed = new URL(href);
                            if ((parsed.hostname || "").toLowerCase().indexOf("google.com") >= 0 && parsed.pathname.indexOf("/url") === 0) {
                                var target = parsed.searchParams.get("url") || parsed.searchParams.get("q");
                                if (target) href = decodeURIComponent(target);
                            }
                        } catch (e) {}
                        if (!/^https?:\\/\\//i.test(href)) continue;
                        var lower = href.toLowerCase();
                        if (lower.indexOf("google.com/search") >= 0 || lower.indexOf("google.com/accounts") >= 0 || lower.indexOf("support.google.com") >= 0 || lower.indexOf("policies.google.com") >= 0) continue;
                        if (seen[href]) continue;
                        seen[href] = true;
                        found.push(href + "|||" + label.replace(/[\\r\\n]+/g, " "));
                        if (found.length >= 50) break;
                    }
                    MusicFinder.results(found.join("###"));
                } catch (e) { MusicFinder.results(""); }
            })();
        """.trimIndent()
        try { web.evaluateJavascript(script, null) } catch (_: Exception) { if (!destroyed) loadNextSiteBatch() }
    }''')
    MAIN.write_text(text, encoding="utf-8")
    print("Wired SearchProvider abstraction into the Android search pipeline.")

if __name__ == "__main__":
    main()
