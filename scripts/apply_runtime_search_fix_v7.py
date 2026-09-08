from pathlib import Path
import re

ROOT = Path(__file__).resolve().parents[1]
MAIN = ROOT / "app/src/main/java/com/kafshar/musicfinder/MainActivity.kt"
text = MAIN.read_text(encoding="utf-8")

MARKER = "// RUNTIME_SEARCH_FIX_V7"

# V7 makes runtime discovery MIME-first instead of URL-pattern-first. The WebView
# reports every non-static resource it sees; probeMediaUrl then decides whether it
# is actually playable audio. This is what catches extensionless CDN endpoints.
if MARKER not in text:
    anchor = "    private var webRecreating = false\n"
    if anchor not in text:
        raise SystemExit("webRecreating anchor not found")
    text = text.replace(anchor, anchor + f'''\n    {MARKER}\n    private val capturedRuntimeUrls = java.util.Collections.synchronizedSet(mutableSetOf<String>())\n''', 1)

# Replace the V5 URL classifier with a capture filter. It deliberately accepts
# extensionless URLs; obvious static resources are excluded to keep probing bounded.
classifier = '''    private fun isLikelyRuntimeMediaUrl(url: String): Boolean {
        val lower = url.lowercase(Locale.US)
        if (!lower.startsWith("http://") && !lower.startsWith("https://")) return false
        if (ServerConfig.isYouTubeUrl(url)) return false
        val path = lower.substringBefore("?").substringBefore("#")
        val obviousStatic = listOf(
            ".css", ".js", ".json", ".xml", ".html", ".htm", ".svg", ".png",
            ".jpg", ".jpeg", ".gif", ".webp", ".ico", ".woff", ".woff2", ".ttf",
            ".otf", ".map", ".wasm"
        )
        if (obviousStatic.any { path.endsWith(it) }) return false
        return true
    }

'''
pattern = r"    private fun isLikelyRuntimeMediaUrl\(url: String\): Boolean \{.*?\n    \}\n\n"
text, count = re.subn(pattern, classifier, text, count=1, flags=re.S)
if count != 1:
    raise SystemExit("runtime URL classifier not found")

# The JS bridge must no longer discard extensionless resources.
bridge_pattern = r"        @JavascriptInterface\n        fun runtimeMedia\(raw: String\?\) \{.*?\n        \}\n\n"
bridge = '''        @JavascriptInterface
        fun runtimeMedia(raw: String?) {
            if (destroyed || resultGeneration != searchGeneration) return
            val url = try {
                URLDecoder.decode(raw.orEmpty(), "UTF-8")
            } catch (_: Exception) {
                raw.orEmpty()
            }
            if (isLikelyRuntimeMediaUrl(url)) {
                capturedRuntimeUrls.add(url)
            }
        }

'''
text, count = re.subn(bridge_pattern, bridge, text, count=1, flags=re.S)
if count != 1:
    raise SystemExit("runtimeMedia bridge not found")

# Replace extractMusicPage with a broad resource scan. performance entries include
# extensionless CDN URLs even when no <audio src> exists in the DOM.
extract = '''    private fun extractMusicPage(pageUrl: String) {
        if (destroyed || resultGeneration != searchGeneration) return
        expectedPageUrl = pageUrl
        val script = """
            (function(){
              try {
                var urls = [];
                function add(v) {
                  if (!v) return;
                  try {
                    var u = new URL(v, location.href).href;
                    if (/^https?:/i.test(u)) urls.push(u);
                  } catch(e) {}
                }
                document.querySelectorAll('audio,video,source').forEach(function(e){
                  add(e.currentSrc); add(e.src);
                  ['src','data-src','data-url','data-audio','data-file','data-mp3','data-stream','data-audio-url','data-stream-url','data-media-url'].forEach(function(k){ add(e.getAttribute(k)); });
                });
                document.querySelectorAll('[data-audio-url],[data-stream-url],[data-media-url],[data-file]').forEach(function(e){
                  add(e.getAttribute('data-audio-url')); add(e.getAttribute('data-stream-url')); add(e.getAttribute('data-media-url')); add(e.getAttribute('data-file'));
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
            handler.postDelayed({
                if (!destroyed && resultGeneration == searchGeneration && expectedPageUrl == pageUrl) {
                    try { web.evaluateJavascript(script, null) } catch (_: Exception) { }
                }
            }, 2200L)
        } catch (_: Exception) {
            finishCurrentResultPage()
        }
    }

'''
pattern = r"    private fun extractMusicPage\(pageUrl: String\) \{.*?\n    \}\n\n    private fun validateAndAddAudioCandidates"
text, count = re.subn(pattern, extract + "    private fun validateAndAddAudioCandidates", text, count=1, flags=re.S)
if count != 1:
    raise SystemExit("extractMusicPage not found")

# Merge parser candidates and all runtime-observed candidates.
text = text.replace(
'''                val parsed = MusicPageParser.parse(html, expectedPageUrl)
                val candidates = parsed.audioCandidates
''',
'''                val parsed = MusicPageParser.parse(html, expectedPageUrl)
                val candidates = (parsed.audioCandidates + capturedRuntimeUrls.toList())
                    .filter { it.startsWith("http", true) }
                    .distinct()
                    .take(80)
''', 1)

# Also merge runtime URLs in the older page bridge if it is still present.
old = '''                val audioCandidates = decode(parts[3])
                    .split("|||")
                    .map { it.trim() }
                    .filter { it.startsWith("http", true) }
                    .distinct()
                    .take(30)
'''
new = '''                val audioCandidates = (decode(parts[3])
                    .split("|||")
                    .map { it.trim() }
                    .filter { it.startsWith("http", true) } + capturedRuntimeUrls.toList())
                    .distinct()
                    .take(80)
'''
if old in text:
    text = text.replace(old, new, 1)

# Capture resource URLs directly from WebView as well. This catches requests that
# are not visible in performance entries.
onload = '''                override fun onLoadResource(view: WebView, url: String) {
                    if (!destroyed && resultGeneration == searchGeneration && isLikelyRuntimeMediaUrl(url)) {
                        capturedRuntimeUrls.add(url)
                    }
                    super.onLoadResource(view, url)
                }

'''
if "override fun onLoadResource(view: WebView, url: String)" not in text:
    anchor = "                override fun onPageFinished(\n                    view: WebView,\n                    url: String\n                ) {\n"
    if anchor not in text:
        raise SystemExit("onPageFinished anchor not found")
    text = text.replace(anchor, onload + anchor, 1)

# Replace probeMediaUrl so the actual HTTP response type is authoritative.
probe = '''    private fun probeMediaUrl(url: String, pageUrl: String): Boolean {
        if (!ServerConfig.isAllowedMediaUrl(url, pageUrl)) return false

        data class Probe(val type: String?, val length: Long, val code: Int)

        fun request(method: String): Probe? {
            return try {
                val c = URL(url).openConnection() as HttpURLConnection
                c.requestMethod = method
                c.instanceFollowRedirects = true
                c.connectTimeout = 3500
                c.readTimeout = 3500
                c.setRequestProperty("User-Agent", "Mozilla/5.0 (Linux; Android 12) AppleWebKit/537.36 Chrome/128 Mobile Safari/537.36")
                if (pageUrl.isNotBlank()) c.setRequestProperty("Referer", pageUrl)
                if (method == "GET") c.setRequestProperty("Range", "bytes=0-0")
                c.connect()
                val result = Probe(c.contentType?.lowercase(Locale.US), c.contentLengthLong, c.responseCode)
                c.disconnect()
                result
            } catch (_: Exception) { null }
        }

        val head = request("HEAD")
        val probe = head ?: request("GET")
        val type = probe?.type.orEmpty().substringBefore(';').trim()
        val codeOk = probe?.code in 200..399

        val isAudio = type.startsWith("audio/")
        val isHls = type == "application/vnd.apple.mpegurl" || type == "application/x-mpegurl"
        val isOctetStream = type == "application/octet-stream" && (probe?.length ?: -1L) > 32_768L
        val looksLike = ServerConfig.looksLikeAudioUrl(url)

        return codeOk && (isAudio || isHls || isOctetStream || looksLike)
    }

'''
pattern = r"    private fun probeMediaUrl\(url: String, pageUrl: String\): Boolean \{.*?\n    \}\n\n    private fun addYouTubeView"
text, count = re.subn(pattern, probe + "    private fun addYouTubeView", text, count=1, flags=re.S)
if count != 1:
    raise SystemExit("probeMediaUrl not found")

# Clear runtime URLs at the start of each result page, but keep them while that
# page's player initializes.
reset = "        expectedPageUrl = url\n\n        pageTimeout?.let {"
replacement = '''        expectedPageUrl = url
        capturedRuntimeUrls.clear()

        pageTimeout?.let {'''
if reset in text:
    text = text.replace(reset, replacement, 1)

# YouTube: do not open an external Activity. Use the existing 1x1 WebView inside
# MainActivity, with the official YouTube embed. The user remains on the app screen.
method = '''    private fun playYouTubeInsideApp(url: String, title: String) {
        val id = youtubeVideoId(url)
        if (id.isBlank()) {
            Toast.makeText(this, "شناسه ویدئوی YouTube پیدا نشد", Toast.LENGTH_SHORT).show()
            return
        }
        currentAudioUrl = ""
        currentSong = null
        currentIndex = -1
        titleText.text = title.ifBlank { "YouTube" }
        artistText.text = "YouTube • پخش داخل برنامه"
        status.text = "در حال پخش YouTube..."
        val html = """
            <!doctype html><html><body style="margin:0;background:transparent;overflow:hidden">
            <div id="player" style="width:200px;height:200px"></div>
            <script>
              var tag=document.createElement('script');
              tag.src='https://www.youtube.com/iframe_api';
              document.head.appendChild(tag);
              var player;
              function onYouTubeIframeAPIReady(){
                player=new YT.Player('player',{
                  width:'200',height:'200',videoId:'$id',
                  playerVars:{autoplay:1,playsinline:1,controls:0,rel:0,fs:0},
                  events:{onReady:function(e){e.target.setVolume(100);e.target.playVideo();}}
                });
              }
            </script></body></html>
        """.trimIndent()
        try {
            web.stopLoading()
            web.loadDataWithBaseURL("https://www.youtube.com/", html, "text/html", "UTF-8", null)
        } catch (_: Exception) {
            Toast.makeText(this, "پخش YouTube در برنامه ممکن نیست", Toast.LENGTH_SHORT).show()
        }
    }

'''
if "private fun playYouTubeInsideApp" not in text:
    anchor = "    private fun addYouTubeView(url: String, title: String) {\n"
    if anchor not in text:
        raise SystemExit("addYouTubeView anchor not found")
    text = text.replace(anchor, method + anchor, 1)

# Replace the external ACTION_VIEW launch in addYouTubeView.
old = '''            setOnClickListener {
                try {
                    startActivity(Intent(Intent.ACTION_VIEW, android.net.Uri.parse(url)))
                } catch (_: Exception) {
                    Toast.makeText(this@MainActivity, "باز کردن YouTube ممکن نیست", Toast.LENGTH_SHORT).show()
                }
            }'''
new = '''            setOnClickListener {
                playYouTubeInsideApp(url, title)
            }'''
if old in text:
    text = text.replace(old, new, 1)
text = text.replace('text = "YouTube • باز کردن"', 'text = "YouTube • پخش داخل برنامه"', 1)

MAIN.write_text(text, encoding="utf-8")
print("Runtime search/player patch V7 applied")
