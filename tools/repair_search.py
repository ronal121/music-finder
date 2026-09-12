from pathlib import Path
import re

MAIN = Path("app/src/main/java/com/kafshar/musicfinder/MainActivity.kt")


def main():
    text = MAIN.read_text(encoding="utf-8")

    # Idempotent cleanup: keep exactly one declaration of each runtime field.
    for name, declaration in [
        ("siteBatchIndex", "    private var siteBatchIndex = 0"),
        ("siteSearchQueries", "    private var siteSearchQueries: List<String> = emptyList()"),
    ]:
        text = re.sub(rf"^[ \t]*private var {re.escape(name)}[^\n]*\n", "", text, flags=re.M)
        marker = "    private var searchGeneration = 0\n"
        text = text.replace(marker, marker + declaration + "\n", 1)

    captured = "    @Volatile private var capturedRuntimeUrls = java.util.Collections.synchronizedSet(mutableSetOf<String>())\n"
    text = re.sub(r"^[ \t]*@Volatile private var capturedRuntimeUrls[^\n]*\n", "", text, flags=re.M)
    marker = "    private var expectedPageUrl = \"\"\n"
    text = text.replace(marker, marker + captured, 1)

    # Continue to the next Google site batch after exhausting this batch.
    text = text.replace(
        """        if (\n            resultPageIndex >=\n            resultPages.size\n        ) {\n\n            finishSearch()\n            return\n        }""",
        """        if (resultPageIndex >= resultPages.size) {\n            loadNextSiteBatch()\n            return\n        }""",
        1,
    )

    # First validated playable result starts immediately.
    old = """                    if (songs.none { it.url == song.url }) {\n                        songs.add(song)\n                        addSongView(song, songs.lastIndex)\n                    }"""
    new = """                    if (songs.none { it.url == song.url }) {\n                        songs.add(song)\n                        val newIndex = songs.lastIndex\n                        addSongView(song, newIndex)\n                        if (currentIndex == -1) {\n                            currentIndex = newIndex\n                            playSong(song)\n                        }\n                    }"""
    text = text.replace(old, new, 1)

    # Keep the UI clean while search is running.
    text = re.sub(
        r'\s*if \(songs\.isNotEmpty\(\)\) status\.text = "\$\{songs\.size\} آهنگ قابل پخش پیدا شد"',
        '', text, count=1
    )

    # Decode Google /url?url=... redirects to the real result URL.
    old_js = '''var href = links[i].href || "";\n                        var text = links[i].innerText || "";'''
    new_js = '''var href = links[i].href || "";\n                        var text = links[i].innerText || "";\n                        try {\n                            var parsed = new URL(href);\n                            if ((parsed.hostname || "").toLowerCase().indexOf("google.com") >= 0 && parsed.pathname.indexOf("/url") === 0) {\n                                var target = parsed.searchParams.get("url") || parsed.searchParams.get("q");\n                                if (target) href = decodeURIComponent(target);\n                            }\n                        } catch (e) {}'''
    text = text.replace(old_js, new_js, 1)

    MAIN.write_text(text, encoding="utf-8")
    print("Search repair applied successfully.")


if __name__ == "__main__":
    main()
