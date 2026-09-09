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

# Capture runtime resource URLs. Do not depend on WebResourceResponse MIME:
# shouldInterceptRequest often has no useful response MIME for normal resources.
new_intercept = """                override fun shouldInterceptRequest(
                    view: WebView,
                    request: WebResourceRequest
                ): android.webkit.WebResourceResponse? {
                    val requestUrl = request.url.toString()
                    if (!destroyed && resultGeneration == searchGeneration && expectedPageUrl.isNotBlank() &&
                        requestUrl.startsWith(\"http\", true) && !ServerConfig.isYouTubeUrl(requestUrl) &&
                        !ServerConfig.isObviousNonMediaUrl(requestUrl)) {
                        capturedRuntimeUrls.add(requestUrl)
                    }
                    return super.shouldInterceptRequest(view, request)
                }

"""
pattern = r"                override fun shouldInterceptRequest\(\s*view: WebView,\s*request: WebResourceRequest\s*\): android\.webkit\.WebResourceResponse\? \{.*?\n                \}\n\n"
text, count = re.subn(pattern, lambda m: new_intercept, text, count=1, flags=re.S)
if count != 1:
    raise SystemExit("shouldInterceptRequest not found")

if "override fun onLoadResource(view: WebView, url: String)" not in text:
    hook = """                override fun onLoadResource(view: WebView, url: String) {
                    if (!destroyed && resultGeneration == searchGeneration && expectedPageUrl.isNotBlank() &&
                        url.startsWith(\"http\", true) && !ServerConfig.isYouTubeUrl(url) &&
                        !ServerConfig.isObviousNonMediaUrl(url)) {
                        capturedRuntimeUrls.add(url)
                    }
                    super.onLoadResource(view, url)
                }

"""
    needle = "                override fun onReceivedError(\n"
    if needle not in text:
        raise SystemExit("onReceivedError anchor not found")
    text = text.replace(needle, hook + needle, 1)

old_validate = """        val generation = searchGeneration
        io.execute {
            val accepted = candidates.mapNotNull { url ->
"""
new_validate = """        val generation = searchGeneration
        val runtimeCandidates = synchronized(capturedRuntimeUrls) { capturedRuntimeUrls.toList() }
            .filter { it.startsWith(\"http\", true) }
        val allCandidates = (candidates + runtimeCandidates).distinct().take(160)
        io.execute {
            val accepted = allCandidates.mapNotNull { url ->
"""
if old_validate in text:
    text = text.replace(old_validate, new_validate, 1)
else:
    raise SystemExit("validation block not found")

# Replace only the probe function, keeping the rest of MainActivity untouched.
probe_pattern = r"    private fun probeMediaUrl\(url: String, pageUrl: String\): Boolean \{.*?\n    \}\n\n    private fun addYouTubeView"
probe_replacement = """    private fun probeMediaUrl(url: String, pageUrl: String): Boolean {
        if (!ServerConfig.isAllowedMediaUrl(url, pageUrl)) return false
        if (capturedRuntimeUrls.contains(url) || capturedMediaUrls.contains(url)) {
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
                c.setRequestProperty(\"User-Agent\", web.settings.userAgentString)
                c.setRequestProperty(\"Referer\", pageUrl)
                c.setRequestProperty(\"Accept\", \"audio/*,video/*,application/octet-stream,application/vnd.apple.mpegurl,*/*;q=0.5\")
                val cookie = android.webkit.CookieManager.getInstance().getCookie(url)
                if (!cookie.isNullOrBlank()) c.setRequestProperty(\"Cookie\", cookie)
                if (method == \"GET\") c.setRequestProperty(\"Range\", \"bytes=0-1\")
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
        val headType = request(\"HEAD\")
        val type = headType ?: request(\"GET\")
        val mediaMime = type?.startsWith(\"audio/\") == true ||
            type == \"application/vnd.apple.mpegurl\" ||
            type == \"application/x-mpegurl\" ||
            type == \"application/octet-stream\"
        return mediaMime || ServerConfig.looksLikeAudioUrl(url)
    }

    private fun addYouTubeView"""
text, count = re.subn(probe_pattern, lambda m: probe_replacement, text, count=1, flags=re.S)
if count != 1:
    raise SystemExit("probeMediaUrl block not found")

# Reset runtime URLs for each result page.
needle = """        expectedPageUrl = url

        pageTimeout?.let {"""
replacement = """        expectedPageUrl = url
        capturedRuntimeUrls.clear()
        capturedMediaUrls.clear()

        pageTimeout?.let {"""
if needle in text:
    text = text.replace(needle, replacement, 1)

# Redirects are common; validate the final allowed page URL instead of exact equality.
text = text.replace(
    "if (!destroyed && resultGeneration == searchGeneration && expectedPageUrl == url) {\n                                extractMusicPage(url)\n                            }",
    "if (!destroyed && resultGeneration == searchGeneration && ServerConfig.isAllowedPageUrl(url)) {\n                                expectedPageUrl = url\n                                extractMusicPage(url)\n                            }",
    1,
)

MAIN.write_text(text, encoding="utf-8")
print("Runtime search patch V9 applied")
