from pathlib import Path
import re

ROOT = Path(__file__).resolve().parents[1]
MAIN = ROOT / "app/src/main/java/com/kafshar/musicfinder/MainActivity.kt"

main = MAIN.read_text(encoding="utf-8")

if "FAST_RESULT_PREVIEW_V1" not in main:
    field = "    private lateinit var resultsContainer: LinearLayout\n"
    if field not in main:
        raise SystemExit("resultsContainer field not found")
    main = main.replace(
        field,
        field + "    private var searchPreviewContainer: LinearLayout? = null\n",
        1,
    )

    anchor = "    private fun setupButtons() {\n"
    if anchor not in main:
        raise SystemExit("setupButtons anchor not found")

    helper = r'''    // FAST_RESULT_PREVIEW_V1
    private fun showSearchPreviews(items: List<Pair<String, String>>) {
        if (items.isEmpty()) return

        val container = searchPreviewContainer ?: LinearLayout(this).also {
            it.orientation = LinearLayout.VERTICAL
            it.setPadding(0, 0, 0, 8)
            searchPreviewContainer = it
            resultsContainer.addView(
                it,
                0,
                LinearLayout.LayoutParams(
                    LinearLayout.LayoutParams.MATCH_PARENT,
                    LinearLayout.LayoutParams.WRAP_CONTENT
                )
            )
        }

        container.removeAllViews()

        val header = TextView(this).apply {
            text = "نتایج جستجو"
            setTextColor(turquoiseColor)
            textSize = 17f
            setPadding(8, 10, 8, 6)
        }
        container.addView(header)

        items.take(10).forEachIndexed { index, item ->
            val title = item.first.ifBlank { "نتیجه ${index + 1}" }
            val site = item.second.ifBlank { "منبع موسیقی" }
            val row = LinearLayout(this).apply {
                orientation = LinearLayout.VERTICAL
                setPadding(12, 9, 12, 9)
                setBackgroundColor(0xFF15151D.toInt())
            }
            val titleView = TextView(this).apply {
                text = title
                setTextColor(0xFFFFFFFF.toInt())
                textSize = 15f
                maxLines = 2
            }
            val siteView = TextView(this).apply {
                text = "$site  •  در حال آماده‌سازی پخش..."
                setTextColor(0xFFAAAAAA.toInt())
                textSize = 11f
                maxLines = 1
            }
            row.addView(titleView)
            row.addView(siteView)
            container.addView(
                row,
                LinearLayout.LayoutParams(
                    LinearLayout.LayoutParams.MATCH_PARENT,
                    LinearLayout.LayoutParams.WRAP_CONTENT
                ).apply { setMargins(0, 0, 0, 6) }
            )
        }
    }

    private fun clearSearchPreviews() {
        searchPreviewContainer?.removeAllViews()
        searchPreviewContainer?.visibility = View.GONE
    }

'''
    main = main.replace(anchor, helper + anchor, 1)

    # Show Google results immediately, before any music-page extraction starts.
    old = '''                val parsed = GoogleResultParser.parse(html, 30)
                resultGeneration = searchGeneration
'''
    new = '''                val parsed = GoogleResultParser.parse(html, 15)
                showSearchPreviews(
                    parsed.take(10).map { result ->
                        result.title.ifBlank { result.url } to
                            result.url.removePrefix("https://").removePrefix("http://").substringBefore("/")
                    }
                )
                resultGeneration = searchGeneration
'''
    if old not in main:
        raise SystemExit("google parser block not found")
    main = main.replace(old, new, 1)

    old = '''                val discovered = raw.orEmpty()
                    .split("###")
'''
    new = '''                val discovered = raw.orEmpty()
                    .split("###")
'''
    if old not in main:
        raise SystemExit("results parser anchor not found")

    # Insert preview after the discovered list has been constructed.
    old2 = '''                    .distinctBy { it.first.substringBefore("#").trimEnd('/').lowercase() }
                    .take(15)

                discovered.filter { it.third }.forEach'''
    new2 = '''                    .distinctBy { it.first.substringBefore("#").trimEnd('/').lowercase() }
                    .take(12)

                showSearchPreviews(
                    discovered.filterNot { it.third }.take(10).map { result ->
                        result.second.ifBlank { result.first } to
                            result.first.removePrefix("https://").removePrefix("http://").substringBefore("/")
                    }
                )

                discovered.filter { it.third }.forEach'''
    if old2 not in main:
        raise SystemExit("discovered list block not found")
    main = main.replace(old2, new2, 1)

    # Keep background extraction bounded; the visible list is independent of extraction.
    old = '''                resultPages = parsed.filterNot { it.isYouTube }
                    .map { "${it.url}|||${it.title}" }
'''
    new = '''                resultPages = parsed.filterNot { it.isYouTube }
                    .take(10)
                    .map { "${it.url}|||${it.title}" }
'''
    if old not in main:
        raise SystemExit("resultPages block not found")
    main = main.replace(old, new, 1)

    old = '''        val text = query.text.toString().trim()
        if (text.isBlank()) {'''
    new = '''        val text = query.text.toString().trim()
        clearSearchPreviews()
        if (text.isBlank()) {'''
    if old not in main:
        raise SystemExit("searchMusic text block not found")
    main = main.replace(old, new, 1)

    # Reduce the per-page wait so dead/slow sites do not hold the whole search hostage.
    main = main.replace('            7500L\n', '            4500L\n', 1)

    MAIN.write_text(main, encoding="utf-8")

print("Applied immediate result preview, bounded background extraction, and faster page timeout")
