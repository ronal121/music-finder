from pathlib import Path
import re

ROOT = Path(__file__).resolve().parents[1]
MAIN = ROOT / "app/src/main/java/com/kafshar/musicfinder/MainActivity.kt"
MANIFEST = ROOT / "app/src/main/AndroidManifest.xml"
MARKER = "// RUNTIME_SEARCH_FIX_V4"

text = MAIN.read_text(encoding="utf-8")
if MARKER not in text:
    if "import java.util.Collections\n" not in text:
        text = text.replace("import java.util.Locale\n", "import java.util.Locale\nimport java.util.Collections\n", 1)
    state_anchor = "    private var webRecreating = false\n"
    state = "    private var webRecreating = false\n\n    // RUNTIME_SEARCH_FIX_V4\n    private val capturedMediaUrls = Collections.synchronizedSet(mutableSetOf<String>())\n    private var runtimeTitle = \"Music\"\n    private var runtimeArtist = \"Unknown Artist\"\n    private var runtimeCover = \"\"\n"
    if "capturedMediaUrls" not in text:
        if state_anchor not in text:
            raise SystemExit("webRecreating state anchor not found")
        text = text.replace(state_anchor, state, 1)

    extract = '''    private fun extractMusicPage(pageUrl: String) {
        if (destroyed || resultGeneration != searchGeneration) return
        expectedPageUrl = pageUrl
        val script = """
            (function(){
              try {
                var urls = [];
                function add(v) {
                  if (!v) return;
                  try { urls.push(new URL(v, location.href).href); } catch(e) {}
                }
                document.querySelectorAll('audio,video,source').forEach(function(e){
                  add(e.currentSrc); add(e.src);
                  ['src','data-src','data-url','data-audio','data-file','data-mp3','data-stream'].forEach(function(k){ add(e.getAttribute(k)); });
                });
                try {
                  performance.getEntriesByType('resource').forEach(function(e){ if (e && e.name) add(e.name); });
                } catch(e) {}
                MusicFinder.runtimeMedia(encodeURIComponent(JSON.stringify(urls)));
                var html = document.documentElement ? document.documentElement.outerHTML : '';
                MusicFinder.pageHtml(encodeURIComponent(html || ''));
              } catch(e) {
                MusicFinder.runtimeMedia('');
                MusicFinder.pageHtml('');
              }
            })();
        """.trimIndent()
        try { web.evaluateJavascript(script, null) } catch (_: Exception) { finishCurrentResultPage() }
    }

'''
    pattern = r"    private fun extractMusicPage\(pageUrl: String\) \{.*?\n    \}\n\n    private fun validateAndAddAudioCandidates"
    text, count = re.subn(pattern, extract + "    private fun validateAndAddAudioCandidates", text, count=1, flags=re.S)
    if count != 1:
        raise SystemExit("extractMusicPage function not found")

    helper = '''    private fun isLikelyRuntimeMediaUrl(url: String): Boolean {
        val lower = url.lowercase(Locale.US)
        if (!lower.startsWith("http://") && !lower.startsWith("https://")) return false
        if (ServerConfig.isYouTubeUrl(url)) return false
        if (Regex("\\\\.(css|js|json|xml|html?|svg|png|jpe?g|gif|webp|ico|woff2?|ttf|map)(?:$|[?#])").containsMatchIn(lower)) return false
        return Regex("\\\\.(mp3|m4a|aac|ogg|oga|opus|wav|flac|m3u8|mp4)(?:$|[?#])").containsMatchIn(lower) ||
            lower.contains("audio") || lower.contains("stream") || lower.contains("media") ||
            lower.contains("playlist") || lower.contains("source=") || lower.contains("file=")
    }

'''
    if "private fun isLikelyRuntimeMediaUrl" not in text:
        anchor = "    private fun extractMusicPage(pageUrl: String) {\n"
        if anchor not in text:
            raise SystemExit("extractMusicPage anchor missing")
        text = text.replace(anchor, helper + anchor, 1)

    bridge = '''        @JavascriptInterface
        fun runtimeMedia(raw: String?) {
            runOnUiThread {
                if (destroyed || resultGeneration != searchGeneration) return@runOnUiThread
                val decoded = try { URLDecoder.decode(raw.orEmpty(), "UTF-8") } catch (_: Exception) { "" }
                val urls = Regex("\\\"(https?://[^\\\"]+)\\\"")
                    .findAll(decoded)
                    .map { it.groupValues[1].replace("\\\\/", "/") }
                    .toList()
                val candidates = urls.filter { isLikelyRuntimeMediaUrl(it) }.distinct().take(30)
                capturedMediaUrls.addAll(candidates)
                if (candidates.isNotEmpty()) {
                    validateAndAddAudioCandidates(
                        runtimeTitle.ifBlank { "Music" },
                        runtimeArtist.ifBlank { "Unknown Artist" },
                        runtimeCover,
                        candidates,
                        expectedPageUrl
                    )
                }
            }
        }

'''
    if "fun runtimeMedia(raw: String?)" not in text:
        anchor = "        @JavascriptInterface\n        fun pageHtml(raw: String?) {\n"
        if anchor not in text:
            raise SystemExit("pageHtml anchor not found")
        text = text.replace(anchor, bridge + anchor, 1)

    old = "                val parsed = MusicPageParser.parse(html, expectedPageUrl)\n                val candidates = parsed.audioCandidates\n"
    new = "                val parsed = MusicPageParser.parse(html, expectedPageUrl)\n                runtimeTitle = parsed.title.ifBlank { \"Music\" }\n                runtimeArtist = parsed.artist.ifBlank { \"Unknown Artist\" }\n                runtimeCover = parsed.cover\n                val candidates = parsed.audioCandidates\n"
    if old in text:
        text = text.replace(old, new, 1)

    page_old = "                val cover =\n                    decode(parts[2])\n                        .trim()\n\n                val audioCandidates = decode(parts[3])"
    page_new = "                val cover =\n                    decode(parts[2])\n                        .trim()\n\n                runtimeTitle = title\n                runtimeArtist = artist\n                runtimeCover = cover\n\n                val audioCandidates = decode(parts[3])"
    if page_old in text:
        text = text.replace(page_old, page_new, 1)

    probe_pattern = r"(private fun probeMediaUrl\(url: String, pageUrl: String\): Boolean \{\s*if \(!ServerConfig\.isAllowedMediaUrl\(url, pageUrl\)\) return false\s*)"
    text, count = re.subn(probe_pattern, r"\1        if (capturedMediaUrls.contains(url)) return true\n", text, count=1)
    if count != 1:
        raise SystemExit("probeMediaUrl function not found")

    if "override fun shouldInterceptRequest" not in text:
        intercept = '''                override fun shouldInterceptRequest(
                    view: WebView,
                    request: WebResourceRequest
                ): android.webkit.WebResourceResponse? {
                    if (!destroyed && resultGeneration == searchGeneration && expectedPageUrl.isNotBlank()) {
                        val requestUrl = request.url.toString()
                        if (isLikelyRuntimeMediaUrl(requestUrl)) {
                            capturedMediaUrls.add(requestUrl)
                            handler.post {
                                if (!destroyed && resultGeneration == searchGeneration) {
                                    validateAndAddAudioCandidates(
                                        runtimeTitle.ifBlank { "Music" },
                                        runtimeArtist.ifBlank { "Unknown Artist" },
                                        runtimeCover,
                                        listOf(requestUrl),
                                        expectedPageUrl
                                    )
                                }
                            }
                        }
                    }
                    return super.shouldInterceptRequest(view, request)
                }

'''
        anchor = "                override fun onPageFinished(\n                    view: WebView,\n                    url: String\n                ) {\n"
        if anchor not in text:
            raise SystemExit("onPageFinished anchor not found")
        text = text.replace(anchor, intercept + anchor, 1)

    reset_old = "        expectedPageUrl = url\n\n        pageTimeout?.let {"
    reset_new = "        expectedPageUrl = url\n        runtimeTitle = \"Music\"\n        runtimeArtist = \"Unknown Artist\"\n        runtimeCover = \"\"\n        capturedMediaUrls.clear()\n\n        pageTimeout?.let {"
    if reset_old in text:
        text = text.replace(reset_old, reset_new, 1)
    text = text.replace("            7500L\n", "            15000L\n", 1)

    yt_pattern = r"(private fun addYouTubeView\(url: String, title: String\) \{.*?setOnClickListener \{\s*)try \{\s*startActivity\(Intent\(Intent\.ACTION_VIEW, android\.net\.Uri\.parse\(url\)\)\)\s*\} catch \(_:\s*Exception\) \{.*?\}\s*(\})"
    def yt_repl(m):
        return m.group(1) + '''val videoId = youtubeVideoId(url)
                if (videoId.isBlank()) {
                    Toast.makeText(this@MainActivity, "شناسه ویدئوی YouTube پیدا نشد", Toast.LENGTH_SHORT).show()
                } else {
                    startActivity(Intent(this@MainActivity, YouTubePlayerActivity::class.java).apply {
                        putExtra(YouTubePlayerActivity.EXTRA_VIDEO_ID, videoId)
                        putExtra(YouTubePlayerActivity.EXTRA_TITLE, title)
                    })
                }''' + "\n            " + m.group(2)
    text, count = re.subn(yt_pattern, yt_repl, text, count=1, flags=re.S)
    if count != 1:
        raise SystemExit("addYouTubeView ACTION_VIEW block not found")
    text = text.replace('text = "YouTube • باز کردن"', 'text = "YouTube • پخش داخل برنامه"', 1)

    MAIN.write_text(text, encoding="utf-8")

manifest = MANIFEST.read_text(encoding="utf-8")
activity = '        <activity android:name=".YouTubePlayerActivity" android:exported="false" />\n'
if activity not in manifest:
    anchor = '        <activity android:name=".MainActivity" android:exported="false" />\n'
    if anchor not in manifest:
        raise SystemExit("MainActivity manifest anchor not found")
    MANIFEST.write_text(manifest.replace(anchor, anchor + activity, 1), encoding="utf-8")

print("Runtime search/player patch V4 applied")
