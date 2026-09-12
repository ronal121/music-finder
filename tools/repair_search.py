from pathlib import Path
import re

ROOT = Path("app/src/main/java/com/kafshar/musicfinder")
MAIN = ROOT / "MainActivity.kt"


def replace_regex(path: Path, pattern: str, replacement: str, count: int = 1):
    text = path.read_text(encoding="utf-8")
    new_text, n = re.subn(pattern, replacement, text, count=count, flags=re.S)
    if n == 0:
        return False
    path.write_text(new_text, encoding="utf-8")
    return True


def replace_literal(path: Path, old: str, new: str):
    text = path.read_text(encoding="utf-8")
    if old not in text:
        return False
    path.write_text(text.replace(old, new, 1), encoding="utf-8")
    return True


# The old implementation hard-coded four sites. Replace it with a batched
# Google search over MusicSitePool.domains. The pool contains 200+ domains;
# Google is queried in small batches so the URL itself stays practical.
replace_literal(
    MAIN,
    '''    private var searchGeneration = 0\n''',
    '''    private var searchGeneration = 0\n    private var siteBatchIndex = 0\n    private var siteSearchQueries: List<String> = emptyList()\n'''
)

search_method = r'''    private fun searchMusic() {
        if (destroyed) return

        val text = query.text.toString().trim()
        if (text.isEmpty()) {
            Toast.makeText(
                this,
                "نام آهنگ یا خواننده را وارد کنید",
                Toast.LENGTH_SHORT
            ).show()
            return
        }

        searchGeneration++
        cancelSearchCallbacks()
        resultPages = emptyList()
        resultPageIndex = 0
        resultGeneration = searchGeneration
        expectedPageUrl = ""

        siteBatchIndex = 0
        siteSearchQueries = MusicSitePool.googleQueries(text)

        songs.clear()
        currentIndex = -1
        currentAudioUrl = ""
        currentSong = null

        resultsContainer.removeAllViews()
        titleText.text = text
        artistText.text = ""
        status.text = ""
        seekBar.progress = 0
        currentTimeText.text = "00:00"
        durationText.text = "00:00"
        vinyl.clearCover()
        vinyl.stopRotation()

        loadNextSiteBatch()
    }

    private fun loadNextSiteBatch() {
        if (destroyed || siteBatchIndex >= siteSearchQueries.size) {
            finishSearch()
            return
        }

        pageTimeoutRunnable?.let { mainHandler.removeCallbacks(it) }
        pageTimeoutRunnable = null
        expectedPageUrl = ""
        resultPages = emptyList()
        resultPageIndex = 0

        val generation = searchGeneration
        val searchQuery = siteSearchQueries[siteBatchIndex++]
        val encoded = try {
            URLEncoder.encode(searchQuery, "UTF-8")
        } catch (_: Exception) {
            loadNextSiteBatch()
            return
        }

        val url = "https://www.google.com/search?q=$encoded&num=50&hl=fa&gbv=1"

        try {
            web.stopLoading()
            web.loadUrl(url)
        } catch (_: Exception) {
            if (generation == searchGeneration) loadNextSiteBatch()
        }
    }
'''
replace_regex(MAIN, r"    private fun searchMusic\(\) \{.*?\n    private fun cancelSearchCallbacks\(\)", search_method + "\n    private fun cancelSearchCallbacks()")

# Google results are already restricted by the query. Do not filter them again
# against a tiny hard-coded list. Keep Google-owned navigation links out.
extract_method = r'''    private fun extractGoogleResults() {
        if (destroyed) return
        if (searchGeneration <= 0) return

        val script = """
            (function() {
                try {
                    var links = document.querySelectorAll("a");
                    var found = [];
                    var seen = {};
                    for (var i = 0; i < links.length; i++) {
                        var href = links[i].href || "";
                        var text = links[i].innerText || "";
                        if (!/^https?:\\/\\//i.test(href)) continue;
                        var lower = href.toLowerCase();
                        if (lower.indexOf("google.com/search") >= 0) continue;
                        if (lower.indexOf("google.com/accounts") >= 0) continue;
                        if (lower.indexOf("support.google.com") >= 0) continue;
                        if (lower.indexOf("policies.google.com") >= 0) continue;
                        if (lower.indexOf("youtube.com") >= 0 || lower.indexOf("youtu.be") >= 0) continue;
                        if (seen[href]) continue;
                        seen[href] = true;
                        found.push(href + "|||" + text.replace(/[\\r\\n]+/g, " "));
                        if (found.length >= 50) break;
                    }
                    MusicFinder.results(found.join("###"));
                } catch (e) {
                    MusicFinder.results("");
                }
            })();
        """.trimIndent()

        try {
            web.evaluateJavascript(script, null)
        } catch (_: Exception) {
            if (!destroyed) loadNextSiteBatch()
        }
    }
'''
replace_regex(MAIN, r"    private fun extractGoogleResults\(\) \{.*?\n    private fun extractMusicPage\(", extract_method + "\n    private fun extractMusicPage(")

# If one Google batch has no usable result pages, immediately move to the next
# batch instead of stopping the entire search.
replace_literal(
    MAIN,
    '''                if (items.isEmpty()) {
                    status.text =
                        "نتیجه‌ای پیدا نشد"

                    return@runOnUiThread
                }

                status.text =
                    "در حال بررسی نتایج..."
''',
    '''                if (items.isEmpty()) {
                    loadNextSiteBatch()
                    return@runOnUiThread
                }

                status.text = ""
'''
)

# When all pages from one batch are consumed, continue with the next site batch.
replace_literal(
    MAIN,
    '''        if (
            resultPageIndex >= resultPages.size
        ) {

            finishSearch()

            return
        }
''',
    '''        if (
            resultPageIndex >= resultPages.size
        ) {
            loadNextSiteBatch()
            return
        }
'''
)

# Never show result counts/status messages at the end of search.
replace_regex(
    MAIN,
    r"    private fun finishSearch\(\) \{.*?\n    private fun addSong\(",
    '''    private fun finishSearch() {
        if (destroyed) return
        cancelSearchCallbacks()
        if (songs.isNotEmpty() && currentIndex == -1) {
            currentIndex = 0
        }
        status.text = ""
        saveSearchResults()
    }

    private fun addSong('''
)

# The first actually extracted audio result starts playback immediately.
replace_literal(
    MAIN,
    '''        songs.add(song)
        addSongView(
            song,
            songs.lastIndex
        )
''',
    '''        songs.add(song)
        val newIndex = songs.lastIndex
        addSongView(song, newIndex)

        if (currentIndex == -1) {
            currentIndex = newIndex
            playSong(song)
        }
'''
)

# Do not let the 12-second status timeout overwrite the clean UI.
replace_regex(
    MAIN,
    r"        val generation =\n            searchGeneration\n\n        val timeout =\n            Runnable \{.*?\n        mainHandler\.postDelayed\(\n            timeout,\n            12000L\n        \)",
    '''        val generation = searchGeneration
        val timeout = Runnable {
            if (!destroyed && generation == searchGeneration && songs.isEmpty()) {
                status.text = ""
            }
        }
        searchTimeoutRunnable = timeout
        mainHandler.postDelayed(timeout, 12000L)'''
)

print("Music Finder search now uses 200+ domain Google batches with immediate first-result playback.")
