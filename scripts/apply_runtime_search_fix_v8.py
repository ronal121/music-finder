from pathlib import Path
import re

ROOT = Path(__file__).resolve().parents[1]
MAIN = ROOT / "app/src/main/java/com/kafshar/musicfinder/MainActivity.kt"
text = MAIN.read_text(encoding="utf-8")

# Make runtime HTTP probing behave like the WebView session: cookies, referer,
# Range and an audio-friendly Accept header are important for CDNs that reject
# anonymous HEAD requests.
probe_pattern = r'''    private fun probeMediaUrl\(url: String, pageUrl: String\): Boolean \{.*?\n    \}\n\n    private fun addYouTubeView'''
probe_replacement = '''    private fun probeMediaUrl(url: String, pageUrl: String): Boolean {
        if (!ServerConfig.isAllowedMediaUrl(url, pageUrl)) return false
        if (capturedRuntimeUrls.contains(url) || capturedMediaUrls.contains(url)) return true

        fun request(method: String): String? {
            return try {
                val c = URL(url).openConnection() as HttpURLConnection
                c.requestMethod = method
                c.instanceFollowRedirects = true
                c.connectTimeout = 4000
                c.readTimeout = 4000
                c.setRequestProperty("User-Agent", web.settings.userAgentString ?: "Mozilla/5.0")
                c.setRequestProperty("Referer", pageUrl)
                c.setRequestProperty("Accept", "audio/*,video/*;q=0.9,*/*;q=0.8")
                val cookie = android.webkit.CookieManager.getInstance().getCookie(url)
                if (!cookie.isNullOrBlank()) c.setRequestProperty("Cookie", cookie)
                if (method == "GET") c.setRequestProperty("Range", "bytes=0-1")
                c.connect()
                val type = c.contentType?.lowercase(Locale.US)?.substringBefore(';')?.trim()
                val code = c.responseCode
                c.disconnect()
                if (code in 200..399) type else null
            } catch (_: Exception) { null }
        }

        val headType = request("HEAD")
        val type = headType ?: request("GET")
        val mimeAccept = type?.startsWith("audio/") == true ||
            type == "application/vnd.apple.mpegurl" ||
            type == "application/x-mpegurl" ||
            type == "application/octet-stream" ||
            (type?.startsWith("video/") == true && url.contains("audio", true))
        return mimeAccept || ServerConfig.looksLikeAudioUrl(url)
    }

    private fun addYouTubeView'''
text, n = re.subn(probe_pattern, probe_replacement, text, count=1, flags=re.S)
if n != 1:
    raise SystemExit("probeMediaUrl not found")

# YouTube results must never launch an external browser/app. Route the click to
# the same hidden in-app IFrame player instead.
old_click = '''            setOnClickListener {
                try {
                    startActivity(Intent(Intent.ACTION_VIEW, android.net.Uri.parse(url)))
                } catch (_: Exception) {
                    Toast.makeText(this@MainActivity, "باز کردن YouTube ممکن نیست", Toast.LENGTH_SHORT).show()
                }
            }'''
new_click = '''            setOnClickListener {
                playYouTubeInsideApp(url, title)
            }'''
if old_click in text:
    text = text.replace(old_click, new_click, 1)

# A dynamic player may create its real media request only after its Play control
# is clicked. Perform a first extraction, click generic play controls, then take
# a second extraction after the network request has had time to appear.
extract_pattern = r'''    private fun extractMusicPage\(pageUrl: String\) \{.*?\n    \}\n\n    private fun validateAndAddAudioCandidates'''
extract_replacement = '''    private fun extractMusicPage(pageUrl: String) {
        if (destroyed || resultGeneration != searchGeneration) return
        expectedPageUrl = pageUrl
        val script = """
            (function(){
              function send(){
                try {
                  var html = document.documentElement ? document.documentElement.outerHTML : document.body.innerHTML;
                  MusicFinder.pageHtml(encodeURIComponent(html || ''));
                } catch(e) { MusicFinder.pageHtml(''); }
              }
              try {
                var selectors = [
                  'audio', 'video', 'button[aria-label*="play" i]',
                  '[role="button"][aria-label*="play" i]', '.play', '.play-button',
                  '.player-play', '[data-action="play"]', '[data-play]',
                  '[data-testid*="play" i]'
                ];
                selectors.forEach(function(sel){
                  document.querySelectorAll(sel).forEach(function(e){
                    try { if (sel !== 'audio' && sel !== 'video') e.click(); } catch(x) {}
                  });
                });
              } catch(e) {}
              send();
              setTimeout(send, 1200);
            })();
        """.trimIndent()
        try { web.evaluateJavascript(script, null) } catch (_: Exception) { finishCurrentResultPage() }
    }

    private fun validateAndAddAudioCandidates'''
text, n = re.subn(extract_pattern, extract_replacement, text, count=1, flags=re.S)
if n != 1:
    raise SystemExit("extractMusicPage not found")

# The hidden WebView needs a real 200x200 viewport for YouTube's IFrame API,
# while normal Google/site searching remains effectively invisible.
if "// RUNTIME_SEARCH_FIX_V8" not in text:
    text = text.replace(
        "    private var googleFallbackUsed = false\n",
        "    private var googleFallbackUsed = false\n\n    // RUNTIME_SEARCH_FIX_V8\n",
        1,
    )

anchor = "    private fun playYouTubeInsideApp(url: String, title: String) {\n"
if anchor in text and "YouTube's official IFrame API requires" not in text:
    replacement = '''    // RUNTIME_SEARCH_FIX_V8
    private fun playYouTubeInsideApp(url: String, title: String) {
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

        web.alpha = 0f
        web.layoutParams = web.layoutParams.apply {
            width = (200 * resources.displayMetrics.density).toInt()
            height = (200 * resources.displayMetrics.density).toInt()
        }

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
    text = text.replace(anchor, replacement, 1)

# Restore the search WebView footprint whenever a new Google search starts.
needle = '''        // Google is the only discovery source. No hard-coded music-site list is used.
        loadGoogleFallback(text, generation)
'''
replacement = '''        // Google is the only discovery source. No hard-coded music-site list is used.
        web.alpha = 1f
        web.layoutParams = web.layoutParams.apply {
            width = 1
            height = 1
        }
        loadGoogleFallback(text, generation)
'''
if needle in text:
    text = text.replace(needle, replacement, 1)

MAIN.write_text(text, encoding="utf-8")
print("Runtime search/player patch V8 applied")
