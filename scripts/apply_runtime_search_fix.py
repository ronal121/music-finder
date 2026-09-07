from pathlib import Path
import re

ROOT = Path(__file__).resolve().parents[1]
MAIN = ROOT / "app/src/main/java/com/kafshar/musicfinder/MainActivity.kt"
MANIFEST = ROOT / "app/src/main/AndroidManifest.xml"
MARKER = "// RUNTIME_SEARCH_FIX_V5"

text = MAIN.read_text(encoding="utf-8")

# State used to collect media URLs from WebView runtime/network activity.
if MARKER not in text:
    anchor = "    private var webRecreating = false\n"
    state = '''    private var webRecreating = false

    // RUNTIME_SEARCH_FIX_V5
    private val capturedMediaUrls = java.util.Collections.synchronizedSet(mutableSetOf<String>())
    private var runtimeTitle = "Music"
    private var runtimeArtist = "Unknown Artist"
    private var runtimeCover = ""
'''
    if anchor not in text:
        raise SystemExit("webRecreating anchor not found")
    text = text.replace(anchor, state, 1)

# Replace the page extraction routine with a runtime-aware DOM/resource scan.
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
                document.querySelectorAll('[data-audio-url],[data-stream-url],[data-media-url],[data-file]').forEach(function(e){
                  add(e.getAttribute('data-audio-url'));
                  add(e.getAttribute('data-stream-url'));
                  add(e.getAttribute('data-media-url'));
                  add(e.getAttribute('data-file'));
                });
                try {
                  performance.getEntriesByType('resource').forEach(function(e){ if (e && e.name) add(e.name); });
                } catch(e) {}
                var seen = {};
                urls.forEach(function(u){ if (u && !seen[u]) { seen[u] = true; MusicFinder.runtimeMedia(encodeURIComponent(u)); } });
                var html = document.documentElement ? document.documentElement.outerHTML : '';
                MusicFinder.pageHtml(encodeURIComponent(html || ''));
              } catch(e) {
                MusicFinder.pageHtml('');
              }
            })();
        """.trimIndent()
        try {
            web.evaluateJavascript(script, null)
        } catch (_: Exception) {
            finishCurrentResultPage()
        }
    }

'''
pattern = r"    private fun extractMusicPage\(pageUrl: String\) \{.*?\n    \}\n\n    private fun validateAndAddAudioCandidates"
text, count = re.subn(pattern, extract + "    private fun validateAndAddAudioCandidates", text, count=1, flags=re.S)
if count != 1:
    raise SystemExit("extractMusicPage function not found")

# Runtime URL classifier without fragile Kotlin regex escaping.
helper = '''    private fun isLikelyRuntimeMediaUrl(url: String): Boolean {
        val lower = url.lowercase(Locale.US)
        if (!lower.startsWith("http://") && !lower.startsWith("https://")) return false
        if (ServerConfig.isYouTubeUrl(url)) return false
        val path = lower.substringBefore("?").substringBefore("#")
        val obviousNonMedia = listOf(
            ".css", ".js", ".json", ".xml", ".html", ".htm", ".svg",
            ".png", ".jpg", ".jpeg", ".gif", ".webp", ".ico", ".woff",
            ".woff2", ".ttf", ".map"
        )
        if (obviousNonMedia.any { path.endsWith(it) }) return false
        val mediaExtensions = listOf(
            ".mp3", ".m4a", ".aac", ".ogg", ".oga", ".opus", ".wav",
            ".flac", ".m3u8", ".mp4", ".webm"
        )
        return mediaExtensions.any { path.endsWith(it) } ||
            lower.contains("audio") || lower.contains("stream") ||
            lower.contains("media") || lower.contains("playlist") ||
            lower.contains("source=") || lower.contains("file=")
    }

'''
if "private fun isLikelyRuntimeMediaUrl" not in text:
    anchor = "    private fun extractMusicPage(pageUrl: String) {\n"
    if anchor not in text:
        raise SystemExit("extractMusicPage anchor missing")
    text = text.replace(anchor, helper + anchor, 1)

# Bridge receives one decoded URL at a time. This avoids JSON/escaping failures.
bridge = '''        @JavascriptInterface
        fun runtimeMedia(raw: String?) {
            runOnUiThread {
                if (destroyed || resultGeneration != searchGeneration) return@runOnUiThread
                val url = try {
                    URLDecoder.decode(raw.orEmpty(), "UTF-8")
                } catch (_: Exception) {
                    raw.orEmpty()
                }
                if (isLikelyRuntimeMediaUrl(url)) {
                    capturedMediaUrls.add(url)
                }
            }
        }

'''
if "fun runtimeMedia(raw: String?)" not in text:
    anchor = "        @JavascriptInterface\n        fun pageHtml(raw: String?) {\n"
    if anchor not in text:
        raise SystemExit("pageHtml anchor not found")
    text = text.replace(anchor, bridge + anchor, 1)

# Store parser metadata and combine parser + runtime candidates.
old = '''                val parsed = MusicPageParser.parse(html, expectedPageUrl)
                val candidates = parsed.audioCandidates
'''
new = '''                val parsed = MusicPageParser.parse(html, expectedPageUrl)
                runtimeTitle = parsed.title.ifBlank { "Music" }
                runtimeArtist = parsed.artist.ifBlank { "Unknown Artist" }
                runtimeCover = parsed.cover
                val candidates = (parsed.audioCandidates + capturedMediaUrls.toList())
                    .filter { it.startsWith("http", true) }
                    .distinct()
                    .take(40)
'''
if old not in text:
    raise SystemExit("pageHtml parser block not found")
text = text.replace(old, new, 1)

# Store metadata in the older page bridge too and merge captured runtime URLs.
old = '''                val audioCandidates = decode(parts[3])
                    .split("|||")
                    .map { it.trim() }
                    .filter { it.startsWith("http", true) }
                    .distinct()
                    .take(30)

                validateAndAddAudioCandidates(
                    title,
                    artist,
                    cover,
                    audioCandidates,
                    expectedPageUrl
                )
'''
new = '''                runtimeTitle = title
                runtimeArtist = artist
                runtimeCover = cover

                val audioCandidates = (decode(parts[3])
                    .split("|||")
                    .map { it.trim() }
                    .filter { it.startsWith("http", true) } + capturedMediaUrls.toList())
                    .distinct()
                    .take(40)

                validateAndAddAudioCandidates(
                    title,
                    artist,
                    cover,
                    audioCandidates,
                    expectedPageUrl
                )
'''
if old in text:
    text = text.replace(old, new, 1)

# Accept URLs already observed by the WebView even when a CDN rejects a native HEAD request.
probe_pattern = r"(private fun probeMediaUrl\(url: String, pageUrl: String\): Boolean \{\s*if \(!ServerConfig\.isAllowedMediaUrl\(url, pageUrl\)\) return false\s*)"
text, count = re.subn(probe_pattern, r"\1        if (capturedMediaUrls.contains(url)) return true\n", text, count=1)
if count != 1:
    raise SystemExit("probeMediaUrl function not found")

# Capture media requests made by HTML5/JS players, including extensionless CDN URLs when
# the request is accompanied by a media content-type.
if "override fun shouldInterceptRequest" not in text:
    intercept = '''                override fun shouldInterceptRequest(
                    view: WebView,
                    request: WebResourceRequest
                ): android.webkit.WebResourceResponse? {
                    if (!destroyed && resultGeneration == searchGeneration && expectedPageUrl.isNotBlank()) {
                        val requestUrl = request.url.toString()
                        if (isLikelyRuntimeMediaUrl(requestUrl)) {
                            capturedMediaUrls.add(requestUrl)
                        }
                    }
                    return super.shouldInterceptRequest(view, request)
                }

'''
    anchor = "                override fun onPageFinished(\n                    view: WebView,\n                    url: String\n                ) {\n"
    if anchor not in text:
        raise SystemExit("onPageFinished anchor not found")
    text = text.replace(anchor, intercept + anchor, 1)

# Give dynamic players more time and make repeated runtime scans possible through onPageFinished.
text = text.replace("            7500L\n", "            15000L\n")
text = text.replace(".take(15)", ".take(30)")
text = text.replace("GoogleResultParser.parseAnchors(html, 30)", "GoogleResultParser.parseAnchors(html, 40)")

reset_old = "        expectedPageUrl = url\n\n        pageTimeout?.let {"
reset_new = '''        expectedPageUrl = url
        runtimeTitle = "Music"
        runtimeArtist = "Unknown Artist"
        runtimeCover = ""
        capturedMediaUrls.clear()

        pageTimeout?.let {'''
if reset_old in text:
    text = text.replace(reset_old, reset_new, 1)

# Keep the internal YouTube player. Never launch an external YouTube intent.
if "YouTubePlayerActivity::class.java" not in text:
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
        raise SystemExit("YouTube ACTION_VIEW block not found")
    text = text.replace('text = "YouTube • باز کردن"', 'text = "YouTube • پخش داخل برنامه"', 1)

MAIN.write_text(text, encoding="utf-8")

manifest = MANIFEST.read_text(encoding="utf-8")
activity = '        <activity android:name=".YouTubePlayerActivity" android:exported="false" />\n'
if activity not in manifest:
    anchor = '        <activity android:name=".MainActivity" android:exported="false" />\n'
    if anchor not in manifest:
        raise SystemExit("MainActivity manifest anchor not found")
    MANIFEST.write_text(manifest.replace(anchor, anchor + activity, 1), encoding="utf-8")

print("Runtime search/player patch V5 applied")
