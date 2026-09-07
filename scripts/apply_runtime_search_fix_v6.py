from pathlib import Path

ROOT = Path(__file__).resolve().parents[1]
MAIN = ROOT / "app/src/main/java/com/kafshar/musicfinder/MainActivity.kt"
text = MAIN.read_text(encoding="utf-8")

old = '''if (!destroyed && resultGeneration == searchGeneration && expectedPageUrl == url) {
                                extractMusicPage(url)
                            }'''
new = '''if (!destroyed && resultGeneration == searchGeneration && expectedPageUrl.isNotBlank()) {
                                expectedPageUrl = url
                                extractMusicPage(url)
                            }'''
if old in text:
    text = text.replace(old, new, 1)

old = '''        return mediaExtensions.any { path.endsWith(it) } ||
            lower.contains("audio") || lower.contains("stream") ||
            lower.contains("media") || lower.contains("playlist") ||
            lower.contains("source=") || lower.contains("file=")'''
new = '''        return mediaExtensions.any { path.endsWith(it) } ||
            lower.contains("audio") || lower.contains("stream") ||
            lower.contains("media") || lower.contains("playlist") ||
            lower.contains("source=") || lower.contains("file=") ||
            lower.contains("mime=audio") || lower.contains("type=audio") ||
            lower.contains("content-type=audio")'''
if old in text:
    text = text.replace(old, new, 1)

needle = '''        try {
            web.evaluateJavascript(script, null)
        } catch (_: Exception) {
            finishCurrentResultPage()
        }
    }

    private fun validateAndAddAudioCandidates'''
replacement = '''        try {
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

    private fun validateAndAddAudioCandidates'''
if needle in text:
    text = text.replace(needle, replacement, 1)

old = '''                    if (
                        request.isForMainFrame &&
                        !destroyed &&
                        resultPages.isNotEmpty()
                    ) {
                        finishCurrentResultPage()
                    }'''
new = '''                    if (
                        request.isForMainFrame &&
                        !destroyed &&
                        resultPages.isNotEmpty() &&
                        capturedMediaUrls.isEmpty()
                    ) {
                        handler.postDelayed({
                            if (!destroyed && resultGeneration == searchGeneration) {
                                finishCurrentResultPage()
                            }
                        }, 1200L)
                    }'''
if old in text:
    text = text.replace(old, new, 1)

MAIN.write_text(text, encoding="utf-8")
print("Runtime search patch V6 applied")
