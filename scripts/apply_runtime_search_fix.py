from pathlib import Path
import re

ROOT = Path(__file__).resolve().parents[1]
MAIN = ROOT / "app/src/main/java/com/kafshar/musicfinder/MainActivity.kt"
MANIFEST = ROOT / "app/src/main/AndroidManifest.xml"
MARKER = "// RUNTIME_SEARCH_FIX_V3"

text = MAIN.read_text(encoding="utf-8")
if MARKER not in text:
    text = text.replace(
        "import java.util.Locale\n",
        "import java.util.Locale\nimport java.util.Collections\n",
        1,
    )
    text = text.replace(
        "    private var webRecreating = false\n",
        "    private var webRecreating = false\n\n"
        "    // RUNTIME_SEARCH_FIX_V3: observe media created by the real WebView player.\n"
        "    private val capturedMediaUrls = Collections.synchronizedSet(mutableSetOf<String>())\n",
        1,
    )

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
                  performance.getEntriesByType('resource').forEach(function(e){ add(e.name); });
                } catch(e) {}
                MusicFinder.runtimeMedia(encodeURIComponent(JSON.stringify(urls)));
                var html = document.documentElement ? document.documentElement.outerHTML : document.body.innerHTML;
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
    text, count = re.subn(
        r"    private fun extractMusicPage\(pageUrl: String\) \{.*?\n    \}\n\n    private fun validateAndAddAudioCandidates",
        extract + "\n    private fun validateAndAddAudioCandidates",
        text,
        count=1,
        flags=re.S,
    )
    if count != 1:
        raise SystemExit("extractMusicPage function not found")

    helper = '''    private var runtimeTitle = "Music"
    private var runtimeArtist = "Unknown Artist"
    private var runtimeCover = ""

    private fun currentPageTitle(): String = runtimeTitle.ifBlank { "Music" }
    private fun currentPageArtist(): String = runtimeArtist.ifBlank { "Unknown Artist" }
    private fun currentPageCover(): String = runtimeCover

    private fun isLikelyRuntimeMediaUrl(url: String): Boolean {
        val lower = url.lowercase(Locale.US)
        if (!lower.startsWith("http://") && !lower.startsWith("https://")) return false
        if (ServerConfig.isYouTubeUrl(url)) return false
        if (Regex("\\\\.(css|js|json|xml|html?|svg|png|jpe?g|gif|webp|ico|woff2?|ttf|map)(?:$|[?#])").containsMatchIn(lower)) return false
        return Regex("\\\\.(mp3|m4a|aac|ogg|oga|opus|wav|flac|m3u8|mp4)(?:$|[?#])").containsMatchIn(lower) ||
            lower.contains("audio") || lower.contains("stream") || lower.contains("media") ||
            lower.contains("playlist") || lower.contains("source=") || lower.contains("file=")
    }

'''
    text = text.replace("    private fun extractMusicPage(pageUrl: String) {\n", helper + "    private fun extractMusicPage(pageUrl: String) {\n", 1)

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
                        currentPageTitle(), currentPageArtist(), currentPageCover(), candidates, expectedPageUrl
                    )
                }
            }
        }

'''
    anchor = "        @JavascriptInterface\n        fun page(raw: String?) {\n"
    if anchor not in text:
        raise SystemExit("Bridge page() anchor not found")
    text = text.replace(anchor, bridge + anchor, 1)

    # Preserve parsed metadata for dynamically discovered player URLs.
    text = re.sub(
        r"(val parsed = MusicPageParser\.parse\(html, expectedPageUrl\).*?val candidates = parsed\.audioCandidates\n)",
        r"\1                runtimeTitle = parsed.title.ifBlank { \"Music\" }\n                runtimeArtist = parsed.artist.ifBlank { \"Unknown Artist\" }\n                runtimeCover = parsed.cover\n",
        text,
        count=1,
        flags=re.S,
    )
    text = text.replace(
        "                val audioCandidates = decode(parts[3])",
        "                runtimeTitle = title\n                runtimeArtist = artist\n                runtimeCover = cover\n\n                val audioCandidates = decode(parts[3])",
        1,
    )

    # Accept media URLs observed from the WebView even when CDN HEAD/range probes fail.
    text, count = re.subn(
        r"(private fun probeMediaUrl\(url: String, pageUrl: String\): Boolean \{\s*if \(!ServerConfig\.isAllowedMediaUrl\(url, pageUrl\)\) return false\s*)",
        r"\1        if (capturedMediaUrls.contains(url)) return true\n",
        text,
        count=1,
    )
    if count != 1:
        raise SystemExit("probeMediaUrl function not found")

    # Reset per-page capture state and extend dynamic-player grace period.
    text = text.replace(
        "        expectedPageUrl = url\n\n        pageTimeout?.let {",
        "        expectedPageUrl = url\n        runtimeTitle = \"Music\"\n        runtimeArtist = \"Unknown Artist\"\n        runtimeCover = \"\"\n        capturedMediaUrls.clear()\n\n        pageTimeout?.let {",
        1,
    )
    text = text.replace("            7500L\n", "            15000L\n", 1)

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
                                        currentPageTitle(), currentPageArtist(), currentPageCover(), listOf(requestUrl), expectedPageUrl
                                    )
                                }
                            }
                        }
                    }
                    return super.shouldInterceptRequest(view, request)
                }

'''
    if "override fun shouldInterceptRequest(" not in text:
        anchor = "                override fun onPageFinished(\n                    view: WebView,\n                    url: String\n                ) {\n"
        if anchor not in text:
            raise SystemExit("WebView onPageFinished anchor not found")
        text = text.replace(anchor, intercept + anchor, 1)

    # Route YouTube results to the in-app player instead of ACTION_VIEW.
    old = '''                    startActivity(Intent(Intent.ACTION_VIEW, android.net.Uri.parse(url)))'''
    new = '''                    val videoId = youtubeVideoId(url)
                    if (videoId.isBlank()) {
                        Toast.makeText(this@MainActivity, "شناسه ویدئوی YouTube پیدا نشد", Toast.LENGTH_SHORT).show()
                    } else {
                        startActivity(Intent(this@MainActivity, YouTubePlayerActivity::class.java).apply {
                            putExtra(YouTubePlayerActivity.EXTRA_VIDEO_ID, videoId)
                            putExtra(YouTubePlayerActivity.EXTRA_TITLE, title)
                        })
                    }'''
    if old not in text:
        raise SystemExit("YouTube ACTION_VIEW call not found")
    text = text.replace(old, new, 1)
    text = text.replace("text = \"YouTube • باز کردن\"", "text = \"YouTube • پخش داخل برنامه\"", 1)

    MAIN.write_text(text, encoding="utf-8")

manifest = MANIFEST.read_text(encoding="utf-8")
activity = '        <activity android:name=".YouTubePlayerActivity" android:exported="false" />\n'
if activity not in manifest:
    anchor = '        <activity android:name=".MainActivity" android:exported="false" />\n'
    if anchor not in manifest:
        raise SystemExit("MainActivity manifest anchor not found")
    manifest = manifest.replace(anchor, anchor + "\n" + activity, 1)
    MANIFEST.write_text(manifest, encoding="utf-8")

print("Runtime search/player patch applied")
