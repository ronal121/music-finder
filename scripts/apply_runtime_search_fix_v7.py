from pathlib import Path
import re

ROOT = Path(__file__).resolve().parents[1]
MAIN = ROOT / "app/src/main/java/com/kafshar/musicfinder/MainActivity.kt"
text = MAIN.read_text(encoding="utf-8")

MARKER = "// RUNTIME_SEARCH_FIX_V9"
if MARKER not in text:
    anchor = "    private val capturedRuntimeUrls = java.util.Collections.synchronizedSet(mutableSetOf<String>())\n"
    if anchor not in text:
        raise SystemExit("runtime state not found")
    text = text.replace(anchor, anchor + "    " + MARKER + "\n", 1)

# Capture every non-static resource requested by the WebView. Modern music CDNs
# frequently use extensionless URLs, so URL shape alone cannot identify audio.
old_intercept = r'''                override fun shouldInterceptRequest(
                    view: WebView,
                    request: WebResourceRequest
                ): android.webkit.WebResourceResponse? {
                    val response = super.shouldInterceptRequest(view, request)
                    if (!destroyed && resultGeneration == searchGeneration && expectedPageUrl.isNotBlank()) {
                        val requestUrl = request.url.toString()
                        val mime = response?.mimeType?.lowercase(Locale.US)?.substringBefore(';')?.trim().orEmpty()
                        val mediaMime = mime.startsWith("audio/") ||
                            mime == "application/vnd.apple.mpegurl" ||
                            mime == "application/x-mpegurl" ||
                            mime == "application/octet-stream"
                        if (mediaMime && !ServerConfig.isYouTubeUrl(requestUrl)) {
                            capturedRuntimeUrls.add(requestUrl)
                            capturedMediaUrls.add(requestUrl)
                        }
                    }
                    return response
                }

'''
new_intercept = '''                override fun shouldInterceptRequest(
                    view: WebView,
                    request: WebResourceRequest
                ): android.webkit.WebResourceResponse? {
                    val requestUrl = request.url.toString()
                    if (!destroyed && resultGeneration == searchGeneration && expectedPageUrl.isNotBlank() &&
                        requestUrl.startsWith("http", true) && !ServerConfig.isYouTubeUrl(requestUrl) &&
                        !ServerConfig.isObviousNonMediaUrl(requestUrl)) {
                        capturedRuntimeUrls.add(requestUrl)
                    }
                    return super.shouldInterceptRequest(view, request)
                }

'''
if old_intercept in text:
    text = text.replace(old_intercept, new_intercept, 1)
else:
    pattern = r"                override fun shouldInterceptRequest\(\n                    view: WebView,\n                    request: WebResourceRequest\n                \): android\.webkit\.WebResourceResponse\? \{.*?\n                \}\n\n"
    text, n = re.subn(pattern, new_intercept, text, count=1, flags=re.S)
    if n != 1:
        raise SystemExit("shouldInterceptRequest not found")

# Keep a lightweight onLoadResource hook as a fallback because the default
# shouldInterceptRequest implementation does not expose the WebView response MIME.
if "override fun onLoadResource(view: WebView, url: String)" not in text:
    needle = "                override fun onReceivedError(\n"
    hook = '''                override fun onLoadResource(view: WebView, url: String) {
                    if (!destroyed && resultGeneration == searchGeneration && expectedPageUrl.isNotBlank() &&
                        url.startsWith("http", true) && !ServerConfig.isYouTubeUrl(url) &&
                        !ServerConfig.isObviousNonMediaUrl(url)) {
                        capturedRuntimeUrls.add(url)
                    }
                    super.onLoadResource(view, url)
                }

'''
    if needle not in text:
        raise SystemExit("onReceivedError anchor not found")
    text = text.replace(needle, hook + needle, 1)

# Inject runtime-discovered URLs into the candidate set. This is the critical
# missing link: previously the app captured network URLs but never validated them.
old_validate = '''        val generation = searchGeneration
        io.execute {
            val accepted = candidates.mapNotNull { url ->
'''
new_validate = '''        val generation = searchGeneration
        val runtimeCandidates = synchronized(capturedRuntimeUrls) { capturedRuntimeUrls.toList() }
            .filter { it.startsWith("http", true) }
        val allCandidates = (candidates + runtimeCandidates).distinct().take(160)
        io.execute {
            val accepted = allCandidates.mapNotNull { url ->
'''
if old_validate in text:
    text = text.replace(old_validate, new_validate, 1)
else:
    raise SystemExit("validation block not found")

# Replace probe implementation with a browser-like GET/HEAD probe carrying WebView cookies.
probe_pattern = r"    private fun probeMediaUrl\(url: String, pageUrl: String\): Boolean \{.*?\n    \}\n\n    private fun addYouTubeView"
probe_replacement = '''    private fun probeMediaUrl(url: String, pageUrl: String): Boolean {
        if (!ServerConfig.isAllowedMediaUrl(url, pageUrl)) return false
        if (capturedRuntimeUrls.contains(url) || capturedMediaUrls.contains(url)) {
            // Runtime discovery proves that the browser requested this resource.
            // Still reject obvious HTML/static URLs before trusting it.
            if (!ServerConfig.isObviousNonMediaUrl(url)) return true
        }
        fun request(method: String): String? {
            var c: HttpURLConnection? = null
            return try {
                c = URL(url).openConnection() as HttpURLConnection
                c.requestMethod = method
                c.instanceFollowRedirects = true
                c.connectTimeout = 5000
                c.readTimeout = 5000
                c.useCaches = false
                c.setRequestProperty("User-Agent", web.settings.userAgentString)
                c.setRequestProperty("Referer", pageUrl)
                c.setRequestProperty("Accept", "audio/*,video/*,application/octet-stream,application/vnd.apple.mpegurl,*/*;q=0.5")
                val cookie = android.webkit.CookieManager.getInstance().getCookie(url)
                if (!cookie.isNullOrBlank()) c.setRequestProperty("Cookie", cookie)
                if (method == "GET") c.setRequestProperty("Range", "bytes=0-1")
                c.connect()
                val type = c.contentType?.lowercase(Locale.US)?.substringBefore(';')?.trim()
                val code = c.responseCode
                if (code in 200..399) type else null
            } catch (_: Exception) {
                null
            } finally {
                try { c?.disconnect() } catch (_: Exception) {}
            }
        }
        val headType = request("HEAD")
        val type = headType ?: request("GET")
        val mediaMime = type?.startsWith("audio/") == true ||
            type == "application/vnd.apple.mpegurl" ||
            type == "application/x-mpegurl" ||
            type == "application/octet-stream"
        return mediaMime || ServerConfig.looksLikeAudioUrl(url)
    }

    private fun addYouTubeView'''
text, n = re.subn(probe_pattern, probe_replacement, text, count=1, flags=re.S)
if n != 1:
    raise SystemExit("probeMediaUrl block not found")

# Clear per-page runtime state before loading the next Google result.
needle = '''        expectedPageUrl = url

        pageTimeout?.let {'''
replacement = '''        expectedPageUrl = url
        capturedRuntimeUrls.clear()
        capturedMediaUrls.clear()

        pageTimeout?.let {'''
if needle in text:
    text = text.replace(needle, replacement, 1)

# Do not require exact URL equality after redirects. A music page commonly redirects
# to a canonical URL before the player is initialized.
old = '''                            if (!destroyed && resultGeneration == searchGeneration && expectedPageUrl == url) {
                                extractMusicPage(url)
                            }'''
new = '''                            if (!destroyed && resultGeneration == searchGeneration && ServerConfig.isAllowedPageUrl(url)) {
                                expectedPageUrl = url
                                extractMusicPage(url)
                            }'''
text = text.replace(old, new, 1)

MAIN.write_text(text, encoding="utf-8")
print("Runtime search patch V9 applied")
