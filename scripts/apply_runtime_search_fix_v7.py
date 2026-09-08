from pathlib import Path
import re

ROOT = Path(__file__).resolve().parents[1]
MAIN = ROOT / "app/src/main/java/com/kafshar/musicfinder/MainActivity.kt"
text = MAIN.read_text(encoding="utf-8")

MARKER = "// RUNTIME_SEARCH_FIX_V8"
if MARKER not in text:
    anchor = "    private val capturedRuntimeUrls = java.util.Collections.synchronizedSet(mutableSetOf<String>())\n"
    if anchor not in text:
        raise SystemExit("V7 runtime state not found")
    text = text.replace(anchor, anchor + "    " + MARKER + "\n", 1)

old_intercept = '''                override fun shouldInterceptRequest(
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
new_intercept = '''                override fun shouldInterceptRequest(
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
if old_intercept in text:
    text = text.replace(old_intercept, new_intercept, 1)
else:
    pattern = r"                override fun shouldInterceptRequest\(\n                    view: WebView,\n                    request: WebResourceRequest\n                \): android\.webkit\.WebResourceResponse\? \{.*?\n                \}\n\n"
    text, n = re.subn(pattern, new_intercept, text, count=1, flags=re.S)
    if n != 1:
        raise SystemExit("shouldInterceptRequest not found")

text = re.sub(
    r"                override fun onLoadResource\(view: WebView, url: String\) \{.*?\n                \}\n\n",
    "",
    text,
    count=1,
    flags=re.S,
)

probe_pattern = r"(private fun probeMediaUrl\(url: String, pageUrl: String\): Boolean \{\s*if \(!ServerConfig\.isAllowedMediaUrl\(url, pageUrl\)\) return false\s*)"
replacement = r"\1        if (capturedRuntimeUrls.contains(url) || capturedMediaUrls.contains(url)) return true\n"
text, n = re.subn(probe_pattern, replacement, text, count=1, flags=re.S)
if n != 1:
    raise SystemExit("probeMediaUrl prefix not found")

needle = """                try {
                  performance.getEntriesByType('resource').forEach(function(e){ if (e && e.name) add(e.name); });
                } catch(e) {}
"""
replacement = needle + """                try {
                  var selectors = [
                    'button[aria-label*="play" i]', '[role="button"][aria-label*="play" i]',
                    '.play', '.play-button', '.player-play', '[data-action="play"]',
                    '[data-play]', '[data-testid*="play" i]'
                  ];
                  selectors.forEach(function(sel){ document.querySelectorAll(sel).forEach(function(e){ try { e.click(); } catch(x) {} }); });
                } catch(e) {}
"""
if needle in text:
    text = text.replace(needle, replacement, 1)

MAIN.write_text(text, encoding="utf-8")
print("Runtime search patch V8 applied")
