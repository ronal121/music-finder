from pathlib import Path

ROOT = Path(__file__).resolve().parents[1]
MAIN = ROOT / "app/src/main/java/com/kafshar/musicfinder/MainActivity.kt"

main = MAIN.read_text(encoding="utf-8")

if "FAST_RESULT_PREVIEW_V1" not in main:
    field = "    private lateinit var resultsContainer: LinearLayout\n"
    if field not in main:
        raise SystemExit("resultsContainer field not found")
    main = main.replace(field, field + "    private var searchPreviewContainer: LinearLayout? = null\n", 1)

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
            resultsContainer.addView(it, 0, LinearLayout.LayoutParams(
                LinearLayout.LayoutParams.MATCH_PARENT,
                LinearLayout.LayoutParams.WRAP_CONTENT
            ))
        }
        container.visibility = View.VISIBLE
        container.removeAllViews()
        container.addView(TextView(this).apply {
            text = "نتایج جستجو"
            setTextColor(turquoiseColor)
            textSize = 17f
            setPadding(8, 10, 8, 6)
        })
        items.take(10).forEachIndexed { index, item ->
            val row = LinearLayout(this).apply {
                orientation = LinearLayout.VERTICAL
                setPadding(12, 9, 12, 9)
                setBackgroundColor(0xFF15151D.toInt())
            }
            row.addView(TextView(this).apply {
                text = item.first.ifBlank { "نتیجه ${index + 1}" }
                setTextColor(0xFFFFFFFF.toInt())
                textSize = 15f
                maxLines = 2
            })
            row.addView(TextView(this).apply {
                text = "${item.second.ifBlank { "منبع موسیقی" }}  •  در حال آماده‌سازی پخش..."
                setTextColor(0xFFAAAAAA.toInt())
                textSize = 11f
                maxLines = 1
            })
            container.addView(row, LinearLayout.LayoutParams(
                LinearLayout.LayoutParams.MATCH_PARENT,
                LinearLayout.LayoutParams.WRAP_CONTENT
            ).apply { setMargins(0, 0, 0, 6) })
        }
    }

    private fun clearSearchPreviews() {
        searchPreviewContainer?.removeAllViews()
        searchPreviewContainer?.visibility = View.GONE
    }

'''
    main = main.replace(anchor, helper + anchor, 1)

    old = '''                val parsed = GoogleResultParser.parseAnchors(html, 30)
                resultGeneration = searchGeneration
'''
    new = '''                val parsed = GoogleResultParser.parseAnchors(html, 15)
                showSearchPreviews(parsed.take(10).map { result ->
                    result.title.ifBlank { result.url } to
                        result.url.removePrefix("https://").removePrefix("http://").substringBefore("/")
                })
                resultGeneration = searchGeneration
'''
    if old not in main:
        raise SystemExit("google parser block not found")
    main = main.replace(old, new, 1)

    old = '''                    .distinctBy { it.first.substringBefore("#").trimEnd('/').lowercase() }
                    .take(15)

                discovered.filter { it.third }.forEach'''
    new = '''                    .distinctBy { it.first.substringBefore("#").trimEnd('/').lowercase() }
                    .take(12)

                showSearchPreviews(discovered.filterNot { it.third }.take(10).map { result ->
                    result.second.ifBlank { result.first } to
                        result.first.removePrefix("https://").removePrefix("http://").substringBefore("/")
                })

                discovered.filter { it.third }.forEach'''
    if old not in main:
        raise SystemExit("discovered list block not found")
    main = main.replace(old, new, 1)

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

    main = main.replace("            7500L\n", "            4500L\n", 1)
    MAIN.write_text(main, encoding="utf-8")

print("Applied immediate result preview, bounded background extraction, and faster page timeout")
