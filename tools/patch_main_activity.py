from pathlib import Path

path = Path("app/src/main/java/com/kafshar/musicfinder/MainActivity.kt")
s = path.read_text(encoding="utf-8")
marker = "private var worldOfMusicSearchActive = false"
if marker in s:
    print("MainActivity already patched")
    raise SystemExit(0)

s = s.replace(
    'private var expectedPageUrl = ""',
    'private var expectedPageUrl = ""\n\n    private var worldOfMusicSearchActive = false\n    private var worldOfMusicSearchSubmitted = false\n    private var worldOfMusicFallbackRunnable: Runnable? = null',
    1,
)

old = '''                    if (destroyed) return

                    if (\n                        url.contains(\n                            "google.com/search",\n                            ignoreCase = true\n                        )\n                    ) {'''
new = '''                    if (destroyed) return

                    val pageHost = try {\n                        android.net.Uri.parse(url).host?.lowercase()?.removePrefix("www.") ?: ""\n                    } catch (_: Exception) { "" }

                    if (worldOfMusicSearchActive && pageHost == "worldofmusic.ir") {\n                        if (!worldOfMusicSearchSubmitted) {\n                            worldOfMusicSearchSubmitted = true\n                            submitWorldOfMusicSearch()\n                        } else {\n                            worldOfMusicSearchActive = false\n                            extractWorldOfMusicResults()\n                        }\n                        return\n                    }

                    if (\n                        url.contains(\n                            "google.com/search",\n                            ignoreCase = true\n                        )\n                    ) {'''
if old not in s:
    raise SystemExit("onPageFinished anchor not found")
s = s.replace(old, new, 1)

old = '''        val url = "https://www.google.com/search?q=$encoded&num=50"









        try {

            web.stopLoading()
            web.loadUrl(url)

        } catch (_: Exception) {'''
new = '''        val url = "https://www.google.com/search?q=$encoded&num=50"

        try {
            web.stopLoading()
            web.loadUrl(url)
        } catch (_: Exception) {
'''
if old not in s:
    raise SystemExit("search URL anchor not found")
s = s.replace(old, new, 1)

needle = '''        val generation =
            searchGeneration

        val timeout ='''
insert = '''        val generation =\n            searchGeneration

        worldOfMusicFallbackRunnable?.let {\n            mainHandler.removeCallbacks(it)\n        }\n        val fallback = Runnable {\n            if (!destroyed && generation == searchGeneration && songs.isEmpty()) {\n                startWorldOfMusicSearch(text, generation)\n            }\n        }\n        worldOfMusicFallbackRunnable = fallback\n        mainHandler.postDelayed(fallback, 3500L)

        val timeout ='''
if needle not in s:
    raise SystemExit("generation anchor not found")
s = s.replace(needle, insert, 1)

needle = '''    private fun cancelSearchCallbacks() {

        searchTimeoutRunnable?.let {'''
insert = '''    private fun startWorldOfMusicSearch(text: String, generation: Int) {\n        if (destroyed || generation != searchGeneration) return\n        worldOfMusicSearchActive = true\n        worldOfMusicSearchSubmitted = false\n        status.text = "در حال جستجو در World of Music..."\n        try {\n            web.stopLoading()\n            web.loadUrl("https://worldofmusic.ir/")\n        } catch (_: Exception) {\n            worldOfMusicSearchActive = false\n        }\n    }\n\n    private fun submitWorldOfMusicSearch() {\n        val queryJson = org.json.JSONObject.quote(query.text.toString().trim())\n        val script = """\n            (function() {\n                try {\n                    var q = $queryJson;\n                    var inputs = document.querySelectorAll('input');\n                    var input = null;\n                    for (var i = 0; i < inputs.length; i++) {\n                        var p = (inputs[i].placeholder || '').toLowerCase();\n                        var t = (inputs[i].type || '').toLowerCase();\n                        if (t === 'search' || p.indexOf('جستجو') >= 0 || p.indexOf('سبک') >= 0 || p.indexOf('artist') >= 0) { input = inputs[i]; break; }\n                    }\n                    if (!input && inputs.length) input = inputs[0];\n                    if (!input) { MusicFinder.results(''); return; }\n                    input.focus();\n                    input.value = q;\n                    input.dispatchEvent(new Event('input', {bubbles:true}));\n                    input.dispatchEvent(new Event('change', {bubbles:true}));\n                    var form = input.form;\n                    if (form) {\n                        if (form.requestSubmit) form.requestSubmit(); else form.submit();\n                    } else {\n                        input.dispatchEvent(new KeyboardEvent('keydown', {key:'Enter', code:'Enter', keyCode:13, which:13, bubbles:true}));\n                    }\n                } catch (e) { MusicFinder.results(''); }\n            })();\n        """.trimIndent()\n        try { web.evaluateJavascript(script, null) } catch (_: Exception) { }\n    }\n\n    private fun extractWorldOfMusicResults() {\n        val queryJson = org.json.JSONObject.quote(query.text.toString().trim().lowercase())\n        val script = """\n            (function() {\n                try {\n                    var q = $queryJson;\n                    var tokens = q.split(/\\s+/).filter(function(x){return x.length >= 2;});\n                    var links = document.querySelectorAll('a');\n                    var scored = [];\n                    for (var i = 0; i < links.length; i++) {\n                        var a = links[i];\n                        var href = a.href || '';\n                        var text = (a.innerText || a.textContent || '').replace(/[\\r\\n]+/g,' ').trim();\n                        if (!href || !href.toLowerCase().startsWith('http')) continue;\n                        if (href.toLowerCase().indexOf('worldofmusic.ir') < 0) continue;\n                        if (href.replace(/\\/$/, '') === 'https://worldofmusic.ir') continue;\n                        var hay = (text + ' ' + href).toLowerCase();\n                        var score = 0;\n                        for (var j = 0; j < tokens.length; j++) if (hay.indexOf(tokens[j]) >= 0) score++;\n                        if (score > 0) scored.push({u:href,t:text,s:score});\n                    }\n                    scored.sort(function(a,b){return b.s-a.s;});\n                    var out = [];\n                    for (var k = 0; k < scored.length && k < 30; k++) {\n                        if (out.indexOf(scored[k].u) < 0) out.push(scored[k].u + '|||' + scored[k].t);\n                    }\n                    MusicFinder.results(out.join('###'));\n                } catch (e) { MusicFinder.results(''); }\n            })();\n        """.trimIndent()\n        try { web.evaluateJavascript(script, null) } catch (_: Exception) { }\n    }\n\n    private fun cancelSearchCallbacks() {\n\n        worldOfMusicFallbackRunnable?.let {\n            mainHandler.removeCallbacks(it)\n        }\n        worldOfMusicFallbackRunnable = null\n\n        searchTimeoutRunnable?.let {'''
if needle not in s:
    raise SystemExit("cancel anchor not found")
s = s.replace(needle, insert, 1)

path.write_text(s, encoding="utf-8")
print("Patched MainActivity")
