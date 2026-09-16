from pathlib import Path
import re

MAIN = Path('app/src/main/java/com/kafshar/musicfinder/MainActivity.kt')
SERVICE = Path('app/src/main/java/com/kafshar/musicfinder/MusicService.kt')
LAYOUT = Path('app/src/main/res/layout/activity_main.xml')

s = MAIN.read_text(encoding='utf-8')

# State for two random-play modes.
if 'private var autoPlaySameArtist = true' not in s:
    s = s.replace('    private var randomMode = false', '''    private var randomMode = false
    private var autoPlaySameArtist = true
    private var autoPlaySearchInProgress = false
    private var autoPlayStartCount = 0
    private val autoPlayedUrls = LinkedHashSet<String>()''', 1)

# Wire clear button and mode buttons. Existing project already has clearQuery in its current layout.
marker = '        findViewById<TextView>(R.id.search).setOnClickListener { searchMusic() }'
if marker in s:
    s = s.replace(marker, '''        findViewById<TextView>(R.id.search).setOnClickListener { hideKeyboard(); searchMusic() }
        findViewById<TextView>(R.id.clearQuery).setOnClickListener { query.text.clear(); hideKeyboard() }
        findViewById<TextView>(R.id.sameArtistMode).setOnClickListener { autoPlaySameArtist = true; updateAutoPlayModeUi(); hideKeyboard() }
        findViewById<TextView>(R.id.allArtistsMode).setOnClickListener { autoPlaySameArtist = false; updateAutoPlayModeUi(); hideKeyboard() }
        updateAutoPlayModeUi()''', 1)

# Keyboard should disappear for the important controls.
for old, new in {
    'previousButton.setOnClickListener { previousSong() }': 'previousButton.setOnClickListener { hideKeyboard(); previousSong() }',
    'nextButton.setOnClickListener { nextSong() }': 'nextButton.setOnClickListener { hideKeyboard(); nextSong() }',
    'downloadButton.setOnClickListener { downloadCurrentSong() }': 'downloadButton.setOnClickListener { hideKeyboard(); downloadCurrentSong() }',
    'cancelDownloadButton.setOnClickListener { cancelDownload() }': 'cancelDownloadButton.setOnClickListener { hideKeyboard(); cancelDownload() }',
    'pauseDownloadButton.setOnClickListener { toggleDownloadPause() }': 'pauseDownloadButton.setOnClickListener { hideKeyboard(); toggleDownloadPause() }',
    'saveButton.setOnClickListener { saveCurrentSong() }': 'saveButton.setOnClickListener { hideKeyboard(); saveCurrentSong() }',
    'libraryButton.setOnClickListener { startActivity(Intent(this, LibraryActivity::class.java)) }': 'libraryButton.setOnClickListener { hideKeyboard(); startActivity(Intent(this, LibraryActivity::class.java)) }',
    'historyButton.setOnClickListener { toggleHistory() }': 'historyButton.setOnClickListener { hideKeyboard(); toggleHistory() }',
}.items():
    s = s.replace(old, new)

# Store page URL and make the old lyrics area open the real song page.
if 'currentSongPageUrl = songPageUrls[song.url].orEmpty()' not in s:
    s = s.replace('        currentSong = song\n', '''        currentSong = song
        currentSongPageUrl = songPageUrls[song.url].orEmpty()
        if (currentSongPageUrl.isNotBlank()) {
            lyricsText.text = "📄  مشاهده متن و صفحه آهنگ در سایت  ›"
            lyricsText.visibility = View.VISIBLE
        }
        autoPlayedUrls.add(song.url)
''', 1)

# Auto-play helpers.
if 'private fun startAutoPlaySearch()' not in s:
    anchor = '    private fun setupVolumeControl()'
    helpers = '''    private fun updateAutoPlayModeUi() {
        val same = findViewById<TextView>(R.id.sameArtistMode)
        val all = findViewById<TextView>(R.id.allArtistsMode)
        same.text = if (autoPlaySameArtist) "● 🎤 فقط همین خواننده" else "○ 🎤 فقط همین خواننده"
        all.text = if (!autoPlaySameArtist) "● 🎵 همه خواننده‌ها" else "○ 🎵 همه خواننده‌ها"
        same.alpha = if (autoPlaySameArtist) 1f else .55f
        all.alpha = if (!autoPlaySameArtist) 1f else .55f
    }

    private fun startAutoPlaySearch() {
        if (destroyed) return
        val current = currentSong ?: return
        val host = try { Uri.parse(currentSongPageUrl).host.orEmpty().removePrefix("www.") } catch (_: Exception) { "" }
        if (host.isBlank()) return
        val q = if (autoPlaySameArtist && current.artist.isNotBlank() && current.artist != "Unknown Artist") {
            "${current.artist} site:$host"
        } else {
            "music site:$host"
        }
        val encoded = try { URLEncoder.encode(q, "UTF-8") } catch (_: Exception) { return }
        autoPlaySearchInProgress = true
        autoPlayStartCount = songs.size
        searchGeneration++
        cancelSearchCallbacks()
        resultGeneration = searchGeneration
        resultPages = emptyList()
        resultPageIndex = 0
        expectedPageUrl = ""
        status.text = if (autoPlaySameArtist) "در حال پیدا کردن آهنگ بعدی از ${current.artist}..." else "در حال پیدا کردن آهنگ بعدی از همین سایت..."
        try { web.stopLoading(); web.loadUrl("https://www.google.com/search?q=$encoded&num=50&hl=en&gbv=1") } catch (_: Exception) { autoPlaySearchInProgress = false }
    }

    private fun handlePlaybackEnded() {
        if (destroyed || autoPlaySearchInProgress) return
        startAutoPlaySearch()
    }

'''
    s = s.replace(anchor, helpers + anchor, 1)

# When the current media ends, start a fresh Google search from the same site.
if 'MusicService.EXTRA_ENDED' not in s:
    s = s.replace('            val mediaUrl = intent.getStringExtra(MusicService.EXTRA_URL).orEmpty()', '            val mediaUrl = intent.getStringExtra(MusicService.EXTRA_URL).orEmpty()\n            val ended = intent.getBooleanExtra(MusicService.EXTRA_ENDED, false)', 1)
    s = s.replace('                if (mediaUrl.isNotBlank()) updateActiveResultHighlight(mediaUrl)', '                if (mediaUrl.isNotBlank()) updateActiveResultHighlight(mediaUrl)\n                if (ended) handlePlaybackEnded()', 1)

# Shuffle only the fresh auto-play result pages, then play the first fresh playable result.
old = '''resultPages = raw.orEmpty().split("###")
                    .map { it.trim() }'''
new = '''resultPages = raw.orEmpty().split("###")
                    .map { it.trim() }
                    .let { pages -> if (autoPlaySearchInProgress) pages.shuffled() else pages }'''
if old in s and 'if (autoPlaySearchInProgress) pages.shuffled()' not in s:
    s = s.replace(old, new, 1)

old = '''                        addSongView(song, songs.lastIndex)'''
new = '''                        addSongView(song, songs.lastIndex)
                        if (autoPlaySearchInProgress && songs.size > autoPlayStartCount && song.url !in autoPlayedUrls) {
                            autoPlaySearchInProgress = false
                            handler.postDelayed({ playSong(song) }, 250L)
                        }'''
if old in s and 'songs.size > autoPlayStartCount' not in s:
    s = s.replace(old, new, 1)

MAIN.write_text(s, encoding='utf-8')

# MusicService: explicit ended flag.
m = SERVICE.read_text(encoding='utf-8')
if 'const val EXTRA_ENDED = "ended"' not in m:
    m = m.replace('const val EXTRA_MUTED = "muted"', 'const val EXTRA_MUTED = "muted"\n        const val EXTRA_ENDED = "ended"', 1)
if 'putExtra(EXTRA_ENDED' not in m:
    m = m.replace('putExtra(EXTRA_URL, player.currentMediaItem?.localConfiguration?.uri?.toString().orEmpty())', 'putExtra(EXTRA_URL, player.currentMediaItem?.localConfiguration?.uri?.toString().orEmpty())\n                putExtra(EXTRA_ENDED, player.playbackState == Player.STATE_ENDED)', 1)
SERVICE.write_text(m, encoding='utf-8')

# Layout: keep the existing search bar, add one clear button if needed, and add the two modes.
x = LAYOUT.read_text(encoding='utf-8')
if 'android:id="@+id/clearQuery"' not in x and 'android:id="@+id/clearSearch"' not in x:
    x = x.replace('<com.kafshar.musicfinder.HarmonizedButton android:id="@+id/search"', '<TextView android:id="@+id/clearSearch" android:layout_width="34dp" android:layout_height="48dp" android:gravity="center" android:text="×" android:textColor="@color/muted" android:textSize="24sp" android:clickable="true" android:focusable="true"/>\n                <com.kafshar.musicfinder.HarmonizedButton android:id="@+id/search"', 1)
    s = s.replace('findViewById<TextView>(R.id.clearQuery)', 'findViewById<TextView>(R.id.clearSearch)')
if 'android:id="@+id/sameArtistMode"' not in x:
    marker = '<LinearLayout android:id="@+id/resultsSection"'
    modes = '''<LinearLayout android:layout_width="match_parent" android:layout_height="42dp" android:layout_marginTop="8dp" android:gravity="center" android:orientation="horizontal" android:background="@drawable/bg_secondary_button">
                <TextView android:id="@+id/sameArtistMode" android:layout_width="0dp" android:layout_height="match_parent" android:layout_weight="1" android:gravity="center" android:text="● 🎤 فقط همین خواننده" android:textColor="@color/accent" android:textSize="12sp" android:clickable="true" android:focusable="true"/>
                <TextView android:id="@+id/allArtistsMode" android:layout_width="0dp" android:layout_height="match_parent" android:layout_weight="1" android:gravity="center" android:text="○ 🎵 همه خواننده‌ها" android:textColor="@color/accent" android:textSize="12sp" android:clickable="true" android:focusable="true"/>
            </LinearLayout>
            ''' + marker
    x = x.replace(marker, modes, 1)
LAYOUT.write_text(x, encoding='utf-8')

print('USER FEATURES PATCHED')
