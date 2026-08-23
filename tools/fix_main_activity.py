from pathlib import Path
import re

path = Path("app/src/main/java/com/kafshar/musicfinder/MainActivity.kt")
text = path.read_text(encoding="utf-8")

setup = r'''    private fun setupButtons() {
        findViewById<TextView>(R.id.search).setOnClickListener { searchMusic() }

        query.setOnEditorActionListener { _, actionId, event ->
            val submit = actionId == EditorInfo.IME_ACTION_SEARCH ||
                    actionId == EditorInfo.IME_ACTION_DONE ||
                    (event != null && event.keyCode == android.view.KeyEvent.KEYCODE_ENTER)
            if (submit) {
                searchMusic()
                true
            } else false
        }

        playButton.setOnClickListener {
            if (currentAudioUrl.isBlank()) {
                if (songs.isNotEmpty()) {
                    currentIndex = currentIndex.coerceIn(0, songs.lastIndex)
                    playSong(songs[currentIndex])
                }
            } else {
                sendServiceAction(
                    MusicService.ACTION_TOGGLE,
                    currentAudioUrl,
                    titleText.text.toString(),
                    artistText.text.toString(),
                    currentSong?.cover.orEmpty()
                )
            }
        }

        previousButton.setOnClickListener { previousSong() }
        nextButton.setOnClickListener { nextSong() }

        randomButton.setOnClickListener {
            randomMode = !randomMode
            randomButton.text = if (randomMode) "🔀✓" else "🔀"
            if (randomMode && songs.isNotEmpty()) {
                val nextIndex = if (songs.size == 1) 0 else {
                    var value: Int
                    do { value = java.util.Random().nextInt(songs.size) } while (value == currentIndex)
                    value
                }
                currentIndex = nextIndex
                playSong(songs[nextIndex])
            }
        }

        seekBar.max = 100
        seekBar.setOnSeekBarChangeListener(object : SeekBar.OnSeekBarChangeListener {
            override fun onProgressChanged(seekBar: SeekBar?, progress: Int, fromUser: Boolean) = Unit
            override fun onStartTrackingTouch(seekBar: SeekBar?) = Unit
            override fun onStopTrackingTouch(seekBar: SeekBar?) {
                val percent = seekBar?.progress?.coerceIn(0, 100) ?: return
                val intent = Intent(this@MainActivity, MusicService::class.java).apply {
                    action = MusicService.ACTION_SEEK_PERCENT
                    putExtra(MusicService.EXTRA_PERCENT, percent)
                }
                safelyStartService(intent)
            }
        })

        downloadButton.setOnClickListener { downloadCurrentSong() }
        cancelDownloadButton.setOnClickListener { cancelDownload() }
        pauseDownloadButton.setOnClickListener { toggleDownloadPause() }
        saveButton.setOnClickListener { saveCurrentSong() }
        libraryButton.setOnClickListener {
            try { startActivity(Intent(this, LibraryActivity::class.java)) }
            catch (_: Exception) { Toast.makeText(this, "کتابخانه در دسترس نیست", Toast.LENGTH_SHORT).show() }
        }
        historyButton.setOnClickListener { toggleHistory() }
    }

'''

if "private fun setupButtons()" not in text:
    marker = "    private fun setupVolumeControl() {"
    if marker not in text:
        raise SystemExit("setupVolumeControl marker not found")
    text = text.replace(marker, setup + marker, 1)

bridge = r'''    private inner class Bridge {
        @JavascriptInterface
        fun results(raw: String?) {
            runOnUiThread {
                if (destroyed) return@runOnUiThread
                resultGeneration = searchGeneration
                resultPageIndex = 0
                resultPages = raw.orEmpty()
                    .split("###")
                    .map { it.trim() }
                    .filter { it.substringBefore("|||").startsWith("http", ignoreCase = true) }
                    .distinctBy { it.substringBefore("|||") }
                    .take(50)

                if (resultPages.isEmpty()) {
                    finishSearch()
                } else {
                    status.text = "در حال بررسی ${resultPages.size} نتیجه..."
                    processNextResultPage()
                }
            }
        }

        @JavascriptInterface
        fun page(raw: String?) {
            runOnUiThread {
                if (destroyed || resultGeneration != searchGeneration) return@runOnUiThread

                val parts = raw.orEmpty().split("###", limit = 4)
                if (parts.size < 4) {
                    finishCurrentResultPage()
                    return@runOnUiThread
                }

                val title = cleanTitle(decode(parts[0])).ifBlank { "Music" }
                val artist = decode(parts[1]).trim().ifBlank { "Unknown Artist" }
                val cover = decode(parts[2]).trim()
                val audioRaw = decode(parts[3]).trim()
                val audioUrl = audioRaw.split("|||")
                    .map { it.trim() }
                    .firstOrNull { it.startsWith("http", ignoreCase = true) }
                    .orEmpty()

                if (audioUrl.isNotBlank()) {
                    val song = SongResult(
                        url = audioUrl,
                        title = title,
                        artist = artist,
                        site = getSiteName(expectedPageUrl),
                        cover = cover
                    )
                    if (songs.none { it.url == song.url }) {
                        songs.add(song)
                        addSongView(song, songs.lastIndex)
                    }
                }

                finishCurrentResultPage()
            }
        }
    }

'''

if "private inner class Bridge" not in text:
    marker = "@SuppressLint(\"SetJavaScriptEnabled\")\nprivate fun configureWebView"
    if marker not in text:
        raise SystemExit("configureWebView marker not found")
    text = text.replace(marker, bridge + marker, 1)

clean = '''    private fun finishSearch() {

        if (destroyed) return

        cancelSearchCallbacks()

        status.text =
            if (songs.isEmpty()) {
                "آهنگ قابل پخش پیدا نشد"
            } else {
                "${songs.size} نتیجه پیدا شد"
            }

        if (songs.isNotEmpty() && currentIndex == -1) {
            currentIndex = 0
        }

        saveSearchResults()
    }
'''

# First, replace the original corrupted finishSearch region through addSong.
first = text.find("    private fun finishSearch()")
if first >= 0:
    add_marker = "\n    private fun addSong("
    add_pos = text.find(add_marker, first)
    if add_pos < 0:
        raise SystemExit("addSong marker not found after finishSearch")
    text = text[:first] + clean + text[add_pos + 1:]

# Remove any later duplicate finishSearch immediately preceding saveSearchResults.
pattern = re.compile(
    r'\n    private fun finishSearch\(\) \{.*?\n    \}\n\n    private fun saveSearchResults',
    re.S,
)
text, removed = pattern.subn("\n    private fun saveSearchResults", text)
if removed > 1:
    raise SystemExit(f"unexpected duplicate cleanup count: {removed}")

# If no finishSearch remains, add the canonical implementation before saveSearchResults.
if text.count("private fun finishSearch()") == 0:
    marker = "    private fun saveSearchResults()"
    if marker not in text:
        raise SystemExit("saveSearchResults marker not found")
    text = text.replace(marker, clean + "\n" + marker, 1)

# Final invariant: exactly one declaration.
count = text.count("private fun finishSearch()")
if count != 1:
    raise SystemExit(f"finishSearch declaration count is {count}, expected 1")

path.write_text(text, encoding="utf-8")
print("MainActivity.kt repaired")
