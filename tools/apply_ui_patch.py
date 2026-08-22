from pathlib import Path
import re

p = Path("app/src/main/java/com/kafshar/musicfinder/MainActivity.kt")
s = p.read_text(encoding="utf-8")

old_call = "        applyTurquoiseButtonStyle()\n        restoreSearchResults()"
new_call = "        applyTurquoiseButtonStyle()\n        setupCategoriesDrawer()\n        restoreSearchResults()"
if old_call in s and "setupCategoriesDrawer()" not in s:
    s = s.replace(old_call, new_call, 1)

pattern = re.compile(r"    private fun applyTurquoiseButtonStyle\(\) \{.*?\n    \}\n\n    private fun requestNotificationPermission", re.S)
replacement = '''    private fun applyTurquoiseButtonStyle() {

        // Keep the existing dark/surface button harmony. Accent is used for
        // the important glyphs and controls instead of tinting every button.
        seekBar.progressTintList =
            ColorStateList.valueOf(turquoiseColor)
        seekBar.thumbTintList =
            ColorStateList.valueOf(turquoiseColor)

        volumeSeekBar.progressTintList =
            ColorStateList.valueOf(turquoiseColor)
        volumeSeekBar.thumbTintList =
            ColorStateList.valueOf(turquoiseColor)

        val iconButtons = listOf(
            playButton,
            previousButton,
            nextButton,
            randomButton
        )

        iconButtons.forEach { button ->
            button.setTextColor(turquoiseColor)
            button.backgroundTintList = null
        }

        findViewById<TextView>(R.id.search).apply {
            setTextColor(0xFFFFFFFF.toInt())
            // Search already has the accent background drawable.
            backgroundTintList = null
        }

        listOf(
            downloadButton,
            cancelDownloadButton,
            pauseDownloadButton,
            saveButton,
            libraryButton,
            historyButton
        ).forEach { button ->
            button.setTextColor(0xFFFFFFFF.toInt())
            button.backgroundTintList = null
        }
    }

    private fun setupCategoriesDrawer() {

        val content = findViewById<android.view.ViewGroup>(android.R.id.content)
        val original = content.getChildAt(0) ?: return

        val wrapper = android.widget.FrameLayout(this)
        wrapper.layoutParams = original.layoutParams
        content.removeView(original)
        wrapper.addView(
            original,
            android.widget.FrameLayout.LayoutParams(
                android.widget.FrameLayout.LayoutParams.MATCH_PARENT,
                android.widget.FrameLayout.LayoutParams.MATCH_PARENT
            )
        )
        content.addView(wrapper)

        fun dp(value: Int): Int =
            (value * resources.displayMetrics.density).toInt()

        val scrim = View(this).apply {
            setBackgroundColor(0x88000000.toInt())
            visibility = View.GONE
            setOnClickListener { drawer.visibility = View.GONE; it.visibility = View.GONE }
        }
        wrapper.addView(
            scrim,
            android.widget.FrameLayout.LayoutParams(
                android.widget.FrameLayout.LayoutParams.MATCH_PARENT,
                android.widget.FrameLayout.LayoutParams.MATCH_PARENT
            )
        )

        val drawer = android.widget.LinearLayout(this).apply {
            orientation = android.widget.LinearLayout.VERTICAL
            setPadding(dp(20), dp(24), dp(16), dp(24))
            setBackgroundColor(0xFF14141A.toInt())
            elevation = dp(12).toFloat()
            visibility = View.GONE
        }

        val drawerParams = android.widget.FrameLayout.LayoutParams(
            dp(300),
            android.widget.FrameLayout.LayoutParams.MATCH_PARENT,
            Gravity.END
        )
        wrapper.addView(drawer, drawerParams)

        val title = TextView(this).apply {
            text = "MUSIC CATEGORIES"
            textSize = 18f
            setTextColor(0xFFFFFFFF.toInt())
            setTypeface(typeface, android.graphics.Typeface.BOLD)
            setPadding(0, 0, 0, dp(18))
        }
        drawer.addView(title)

        val categories = listOf(
            "🔥  Popular" to "popular music",
            "⚡  Hard Techno" to "Hard Techno",
            "🎛  Techno" to "Techno",
            "🎧  Electronic" to "Electronic",
            "🏠  House" to "House",
            "🌊  Deep House" to "Deep House",
            "🌌  Trance" to "Trance",
            "🎹  Progressive" to "Progressive House",
            "💿  Dance" to "Dance",
            "🧠  Psy Trance" to "Psy Trance",
            "🌑  Minimal / Dark" to "Minimal Techno Dark",
            "🇮🇷  موسیقی ایرانی" to "موسیقی ایرانی",
            "🌍  موسیقی خارجی" to "موسیقی خارجی"
        )

        categories.forEach { (label, searchTerm) ->
            val item = TextView(this).apply {
                text = label
                textSize = 15f
                setTextColor(turquoiseColor)
                gravity = Gravity.CENTER_VERTICAL
                setPadding(dp(10), 0, dp(8), 0)
                setBackgroundColor(0xFF1B1B23.toInt())
                layoutParams = android.widget.LinearLayout.LayoutParams(
                    android.widget.LinearLayout.LayoutParams.MATCH_PARENT,
                    dp(48)
                ).apply { bottomMargin = dp(8) }
                setOnClickListener {
                    query.setText(searchTerm)
                    query.setSelection(query.text.length)
                    drawer.visibility = View.GONE
                    scrim.visibility = View.GONE
                    findViewById<TextView>(R.id.search).performClick()
                }
            }
            drawer.addView(item)
        }

        val categoryButton = TextView(this).apply {
            text = "☷"
            textSize = 22f
            gravity = Gravity.CENTER
            setTextColor(turquoiseColor)
            setBackgroundColor(0xFF14141A.toInt())
            elevation = dp(6).toFloat()
            setOnClickListener {
                val open = drawer.visibility != View.VISIBLE
                drawer.visibility = if (open) View.VISIBLE else View.GONE
                scrim.visibility = if (open) View.VISIBLE else View.GONE
            }
        }

        wrapper.addView(
            categoryButton,
            android.widget.FrameLayout.LayoutParams(
                dp(52), dp(52), Gravity.END or Gravity.TOP
            ).apply {
                topMargin = dp(18)
                rightMargin = dp(12)
            }
        )
    }

    private fun requestNotificationPermission'''

if not pattern.search(s):
    raise SystemExit("applyTurquoiseButtonStyle block not found")
s = pattern.sub(replacement, s, count=1)
p.write_text(s, encoding="utf-8")
