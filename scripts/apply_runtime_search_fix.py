from pathlib import Path
import re

ROOT = Path(__file__).resolve().parents[1]
MAIN = ROOT / "app/src/main/java/com/kafshar/musicfinder/MainActivity.kt"
MANIFEST = ROOT / "app/src/main/AndroidManifest.xml"

marker = "// RUNTIME_SEARCH_FIX_V2"
text = MAIN.read_text(encoding="utf-8")
if marker not in text:
    text = text.replace(
        'import java.util.Locale\n',
        'import java.util.Locale\nimport java.util.Collections\n',
        1,
    )

    text = text.replace(
        '    private var webRecreating = false\n',
        '    private var webRecreating = false\n\n'
        '    // RUNTIME_SEARCH_FIX_V2: media requests observed from the real WebView player.\n'
        '    private val capturedMediaUrls = Collections.synchronizedSet(mutableSetOf<String>())\n',
        1,
    )

    old_extract = '''    private fun extractMusicPage(pageUrl: String) {\n        if (destroyed || resultGeneration != searchGeneration) return\n        expectedPageUrl = pageUrl\n        val script = """\n            (function(){\n              try {\n                var html = document.documentElement ? document.documentElement.outerHTML : document.body.innerHTML;\n                MusicFinder.pageHtml(encodeURIComponent(html || ''));\n              } catch(e) {\n                MusicFinder.pageHtml('');\n              }\n            })();\n        """.trimIndent()\n        try { web.evaluateJavascript(script, null) } catch (_: Exception) { finishCurrentResultPage() }\n    }\n'''
    new_extract = '''    private fun extractMusicPage(pageUrl: String) {\n        if (destroyed || resultGeneration != searchGeneration) return\n        expectedPageUrl = pageUrl\n        val script = """\n            (function(){\n              try {\n                var urls = [];\n                function add(v) {\n                  if (!v) return;\n                  try { urls.push(new URL(v, location.href).href); } catch(e) {}\n                }\n                document.querySelectorAll('audio,video,source').forEach(function(e){\n                  add(e.currentSrc); add(e.src);\n                  ['src','data-src','data-url','data-audio','data-file','data-mp3','data-stream'].forEach(function(k){ add(e.getAttribute(k)); });\n                });\n                document.querySelectorAll('iframe').forEach(function(e){ add(e.src); });\n                try {\n                  performance.getEntriesByType('resource').forEach(function(e){ add(e.name); });\n                } catch(e) {}\n                MusicFinder.runtimeMedia(encodeURIComponent(JSON.stringify(urls)));\n                var html = document.documentElement ? document.documentElement.outerHTML : document.body.innerHTML;\n                MusicFinder.pageHtml(encodeURIComponent(html || ''));\n              } catch(e) {\n                MusicFinder.runtimeMedia('');\n                MusicFinder.pageHtml('');\n              }\n            })();\n        """.trimIndent()\n        try { web.evaluateJavascript(script, null) } catch (_: Exception) { finishCurrentResultPage() }\n    }\n'''
    if old_extract not in text:
        raise SystemExit("extractMusicPage block not found")
    text = text.replace(old_extract, new_extract, 1)

    bridge_anchor = '''        @JavascriptInterface\n        fun page(raw: String?) {\n'''
    bridge_method = '''        @JavascriptInterface\n        fun runtimeMedia(raw: String?) {\n            runOnUiThread {\n                if (destroyed || resultGeneration != searchGeneration) return@runOnUiThread\n                val decoded = try { URLDecoder.decode(raw.orEmpty(), "UTF-8") } catch (_: Exception) { "" }\n                val urls = try {\n                    Regex("\\\"(https?://[^\\\"]+)\\\"").findAll(decoded).map { it.groupValues[1] }.toList()\n                } catch (_: Exception) { emptyList() }\n                val candidates = urls\n                    .map { it.replace("\\\\/", "/") }\n                    .filter { isLikelyRuntimeMediaUrl(it) }\n                    .distinct()\n                    .take(30)\n                capturedMediaUrls.addAll(candidates)\n                if (candidates.isNotEmpty()) {\n                    validateAndAddAudioCandidates(\n                        currentPageTitle(),\n                        currentPageArtist(),\n                        currentPageCover(),\n                        candidates,\n                        expectedPageUrl\n                    )\n                }\n            }\n        }\n\n        @JavascriptInterface\n        fun page(raw: String?) {\n'''
    if bridge_anchor not in text:
        raise SystemExit("page bridge anchor not found")
    text = text.replace(bridge_anchor, bridge_method, 1)

    # Insert metadata cache helpers just before extractMusicPage.
    helper_anchor = '    private fun extractMusicPage(pageUrl: String) {\n'
    helpers = '''    private var runtimeTitle = "Music"\n    private var runtimeArtist = "Unknown Artist"\n    private var runtimeCover = ""\n\n    private fun currentPageTitle(): String = runtimeTitle.ifBlank { "Music" }\n    private fun currentPageArtist(): String = runtimeArtist.ifBlank { "Unknown Artist" }\n    private fun currentPageCover(): String = runtimeCover\n\n    private fun isLikelyRuntimeMediaUrl(url: String): Boolean {\n        val lower = url.lowercase(Locale.US)\n        if (!lower.startsWith("http://") && !lower.startsWith("https://")) return false\n        if (ServerConfig.isYouTubeUrl(url)) return false\n        if (Regex("\\\\.(css|js|json|xml|html?|svg|png|jpe?g|gif|webp|ico|woff2?|ttf|map)(?:$|[?#])").containsMatchIn(lower)) return false\n        return Regex("\\\\.(mp3|m4a|aac|ogg|oga|opus|wav|flac|m3u8|mp4)(?:$|[?#])").containsMatchIn(lower) ||\n            lower.contains("audio") || lower.contains("stream") || lower.contains("media") ||\n            lower.contains("playlist") || lower.contains("source=") || lower.contains("file=")\n    }\n\n'''
    text = text.replace(helper_anchor, helpers + helper_anchor, 1)

    # Make runtime-captured URLs trustworthy even when HEAD/range probing is blocked by CDN policy.
    old_probe = '        if (!ServerConfig.isAllowedMediaUrl(url, pageUrl)) return false\n        fun request(method: String): String? {\n'
    new_probe = '        if (!ServerConfig.isAllowedMediaUrl(url, pageUrl)) return false\n        if (capturedMediaUrls.contains(url)) return true\n        fun request(method: String): String? {\n'
    if old_probe not in text:
        raise SystemExit("probe anchor not found")
    text = text.replace(old_probe, new_probe, 1)

    # Keep metadata from the static parser for runtime player candidates.
    old_validate = '''                validateAndAddAudioCandidates(\n                    parsed.title.ifBlank { "Music" },\n                    parsed.artist.ifBlank { "Unknown Artist" },\n                    parsed.cover,\n                    candidates,\n                    expectedPageUrl\n                )\n'''
    new_validate = '''                runtimeTitle = parsed.title.ifBlank { "Music" }\n                runtimeArtist = parsed.artist.ifBlank { "Unknown Artist" }\n                runtimeCover = parsed.cover\n                validateAndAddAudioCandidates(\n                    runtimeTitle,\n                    runtimeArtist,\n                    runtimeCover,\n                    candidates,\n                    expectedPageUrl\n                )\n'''
    if old_validate not in text:
        raise SystemExit("metadata validation block not found")
    text = text.replace(old_validate, new_validate, 1)

    # Also cache metadata in the legacy parser path.
    old_page = '''                validateAndAddAudioCandidates(\n                    title,\n                    artist,\n                    cover,\n                    audioCandidates,\n                    expectedPageUrl\n                )\n'''
    new_page = '''                runtimeTitle = title\n                runtimeArtist = artist\n                runtimeCover = cover\n                validateAndAddAudioCandidates(\n                    title,\n                    artist,\n                    cover,\n                    audioCandidates,\n                    expectedPageUrl\n                )\n'''
    if old_page not in text:
        raise SystemExit("legacy page block not found")
    text = text.replace(old_page, new_page, 1)

    # Reset captured runtime state for each page.
    old_expected = '        expectedPageUrl = url\n\n        pageTimeout?.let {'
    new_expected = '        expectedPageUrl = url\n        runtimeTitle = "Music"\n        runtimeArtist = "Unknown Artist"\n        runtimeCover = ""\n        capturedMediaUrls.clear()\n\n        pageTimeout?.let {'
    if old_expected not in text:
        raise SystemExit("page state anchor not found")
    text = text.replace(old_expected, new_expected, 1)

    # Give dynamic players enough time to initialize.
    text = text.replace('            7500L\n', '            15000L\n', 1)

    # Observe media requests made by the actual WebView player. Never block the callback.
    request_anchor = '''                override fun onPageFinished(\n                    view: WebView,\n                    url: String\n                ) {\n'''
    intercept = '''                override fun shouldInterceptRequest(\n                    view: WebView,\n                    request: WebResourceRequest\n                ): android.webkit.WebResourceResponse? {\n                    if (!destroyed && resultGeneration == searchGeneration && expectedPageUrl.isNotBlank()) {\n                        val requestUrl = request.url.toString()\n                        if (isLikelyRuntimeMediaUrl(requestUrl)) {\n                            capturedMediaUrls.add(requestUrl)\n                            handler.post {\n                                if (!destroyed && resultGeneration == searchGeneration) {\n                                    validateAndAddAudioCandidates(\n                                        currentPageTitle(),\n                                        currentPageArtist(),\n                                        currentPageCover(),\n                                        listOf(requestUrl),\n                                        expectedPageUrl\n                                    )\n                                }\n                            }\n                        }\n                    }\n                    return super.shouldInterceptRequest(view, request)\n                }\n\n'''
    if request_anchor not in text:
        raise SystemExit("onPageFinished anchor not found")
    text = text.replace(request_anchor, intercept + request_anchor, 1)

    # YouTube results stay inside the app.
    old_youtube = '''                try {\n                    startActivity(Intent(Intent.ACTION_VIEW, android.net.Uri.parse(url)))\n                } catch (_: Exception) {\n                    Toast.makeText(this@MainActivity, "باز کردن YouTube ممکن نیست", Toast.LENGTH_SHORT).show()\n                }\n'''
    new_youtube = '''                val videoId = youtubeVideoId(url)\n                if (videoId.isBlank()) {\n                    Toast.makeText(this@MainActivity, "شناسه ویدئوی YouTube پیدا نشد", Toast.LENGTH_SHORT).show()\n                } else {\n                    try {\n                        startActivity(Intent(this@MainActivity, YouTubePlayerActivity::class.java).apply {\n                            putExtra(YouTubePlayerActivity.EXTRA_VIDEO_ID, videoId)\n                            putExtra(YouTubePlayerActivity.EXTRA_TITLE, title)\n                        })\n                    } catch (_: Exception) {\n                        Toast.makeText(this@MainActivity, "پخش YouTube داخل برنامه ممکن نیست", Toast.LENGTH_SHORT).show()\n                    }\n                }\n'''
    if old_youtube not in text:
        raise SystemExit("YouTube click block not found")
    text = text.replace(old_youtube, new_youtube, 1)

    text = text.replace('text = "YouTube • باز کردن"', 'text = "YouTube • پخش داخل برنامه"', 1)
    MAIN.write_text(text, encoding="utf-8")

# Manifest entry for the internal YouTube player.
manifest = MANIFEST.read_text(encoding="utf-8")
activity = '        <activity android:name=".YouTubePlayerActivity" android:exported="false" />\n'
if activity not in manifest:
    anchor = '        <activity android:name=".MainActivity" android:exported="false" />\n'
    if anchor not in manifest:
        raise SystemExit("MainActivity manifest anchor not found")
    manifest = manifest.replace(anchor, anchor + '\n' + activity, 1)
    MANIFEST.write_text(manifest, encoding="utf-8")

print("Runtime search/player patch applied")
