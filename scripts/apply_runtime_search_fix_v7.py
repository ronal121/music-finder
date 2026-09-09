from pathlib import Path
import re

ROOT = Path(__file__).resolve().parents[1]
MAIN = ROOT / "app/src/main/java/com/kafshar/musicfinder/MainActivity.kt"
text = MAIN.read_text(encoding="utf-8")

MARKER = "// RUNTIME_SEARCH_FIX_V10"

# Runtime capture state must exist in the real source, not only in a generated CI copy.
state_anchor = '    private var expectedPageUrl = ""\n'
if "private val capturedRuntimeUrls" not in text:
    if state_anchor not in text:
        raise SystemExit("expectedPageUrl anchor not found")
    text = text.replace(
        state_anchor,
        state_anchor +
        '    @Volatile private var capturedRuntimeUrls = java.util.Collections.synchronizedSet(mutableSetOf<String>())\n' +
        '    ' + MARKER + '\n',
        1,
    )

# Capture every HTTP(S) resource requested by the result page. We intentionally do not
# depend on WebResourceResponse.mimeType because Android WebView frequently leaves it null.
if "override fun shouldInterceptRequest" not in text:
    hook = '''                override fun shouldInterceptRequest(\n                    view: WebView,\n                    request: WebResourceRequest\n                ): android.webkit.WebResourceResponse? {\n                    val requestUrl = request.url.toString()\n                    if (!destroyed && resultGeneration == searchGeneration && expectedPageUrl.isNotBlank() &&\n                        requestUrl.startsWith("http", true) && !ServerConfig.isYouTubeUrl(requestUrl) &&\n                        !ServerConfig.isObviousNonMediaUrl(requestUrl)) {\n                        capturedRuntimeUrls.add(requestUrl)\n                    }\n                    return super.shouldInterceptRequest(view, request)\n                }\n\n                override fun onLoadResource(view: WebView, url: String) {\n                    if (!destroyed && resultGeneration == searchGeneration && expectedPageUrl.isNotBlank() &&\n                        url.startsWith("http", true) && !ServerConfig.isYouTubeUrl(url) &&\n                        !ServerConfig.isObviousNonMediaUrl(url)) {\n                        capturedRuntimeUrls.add(url)\n                    }\n                    super.onLoadResource(view, url)\n                }\n\n'''
    needle = "                override fun onReceivedError(\n"
    if needle not in text:
        raise SystemExit("onReceivedError anchor not found")
    text = text.replace(needle, hook + needle, 1)

# Make page extraction consume runtime candidates even when static HTML contains no audio URL.
old = '''                val parsed = MusicPageParser.parse(html, expectedPageUrl)\n                val candidates = parsed.audioCandidates\n                if (candidates.isEmpty()) {\n                    // Give JS-generated players a second pass before abandoning the page.\n                    handler.postDelayed({\n                        if (!destroyed && resultGeneration == searchGeneration) {\n                            extractMusicPage(expectedPageUrl)\n                        }\n                    }, 900L)\n                    return@runOnUiThread\n                }\n'''
new = '''                val parsed = MusicPageParser.parse(html, expectedPageUrl)\n                val runtimeCandidates = synchronized(capturedRuntimeUrls) { capturedRuntimeUrls.toList() }\n                    .filter { it.startsWith("http", true) }\n                val candidates = (parsed.audioCandidates + runtimeCandidates)\n                    .distinct()\n                    .take(160)\n                if (candidates.isEmpty()) {\n                    // Give JS-generated players/network requests another pass before abandoning the page.\n                    handler.postDelayed({\n                        if (!destroyed && resultGeneration == searchGeneration && expectedPageUrl.isNotBlank()) {\n                            extractMusicPage(expectedPageUrl)\n                        }\n                    }, 1200L)\n                    return@runOnUiThread\n                }\n'''
if old in text:
    text = text.replace(old, new, 1)

# Reset captured network requests for every new result page.
needle = '''        expectedPageUrl = url\n\n        pageTimeout?.let {'''
replacement = '''        expectedPageUrl = url\n        capturedRuntimeUrls.clear()\n\n        pageTimeout?.let {'''
if needle in text and "capturedRuntimeUrls.clear()" not in text:
    text = text.replace(needle, replacement, 1)
elif needle in text:
    # Ensure the clear is in the result-page path even if a previous patch partially ran.
    if "expectedPageUrl = url\n        capturedRuntimeUrls.clear()" not in text:
        text = text.replace(needle, replacement, 1)

# Browser-like media probe. Keep the user-agent constant so no WebView state is accessed
# from the background executor.
probe_pattern = r"    private fun probeMediaUrl\(url: String, pageUrl: String\): Boolean \{.*?\n    \}\n\n    private fun addYouTubeView"
probe = '''    private fun probeMediaUrl(url: String, pageUrl: String): Boolean {\n        if (!ServerConfig.isAllowedMediaUrl(url, pageUrl)) return false\n        val browserUa = "Mozilla/5.0 (Linux; Android 12) AppleWebKit/537.36 Chrome/128 Mobile Safari/537.36"\n\n        fun request(method: String): String? {\n            var c: HttpURLConnection? = null\n            return try {\n                c = URL(url).openConnection() as HttpURLConnection\n                c.requestMethod = method\n                c.instanceFollowRedirects = true\n                c.connectTimeout = 5000\n                c.readTimeout = 5000\n                c.useCaches = false\n                c.setRequestProperty("User-Agent", browserUa)\n                c.setRequestProperty("Referer", pageUrl)\n                c.setRequestProperty("Accept", "audio/*,video/*,application/octet-stream,application/vnd.apple.mpegurl,*/*;q=0.5")\n                val cookie = android.webkit.CookieManager.getInstance().getCookie(url)\n                if (!cookie.isNullOrBlank()) c.setRequestProperty("Cookie", cookie)\n                if (method == "GET") c.setRequestProperty("Range", "bytes=0-1")\n                c.connect()\n                val type = c.contentType?.lowercase(Locale.US)?.substringBefore(';')?.trim()\n                val code = c.responseCode\n                if (code in 200..399) type else null\n            } catch (_: Exception) {\n                null\n            } finally {\n                try { c?.disconnect() } catch (_: Exception) {}\n            }\n        }\n\n        val type = request("HEAD") ?: request("GET")\n        val mediaMime = type?.startsWith("audio/") == true ||\n            type == "application/vnd.apple.mpegurl" ||\n            type == "application/x-mpegurl" ||\n            type == "application/octet-stream"\n        return mediaMime || ServerConfig.looksLikeAudioUrl(url)\n    }\n\n    private fun addYouTubeView'''
text, count = re.subn(probe_pattern, lambda m: probe, text, count=1, flags=re.S)
if count != 1:
    raise SystemExit("probeMediaUrl block not found")

MAIN.write_text(text, encoding="utf-8")
print("Runtime search patch V10 applied to source")
