from pathlib import Path
import re

ROOT = Path(__file__).resolve().parents[1]
MAIN = ROOT / "app/src/main/java/com/kafshar/musicfinder/MainActivity.kt"
SEARCH = ROOT / "app/src/main/java/com/kafshar/musicfinder/SearchEngine.kt"
LAYOUT = ROOT / "app/src/main/res/layout/activity_main.xml"

# Persian/Iranian discovery domains plus established international/electronic platforms.
# These are Google hints, not mandatory servers; unavailable domains are harmless.
SITES = '''
music-fa.com upmusics.com songsara.net rozmusic.com nicmusic.net vmusic.ir sakhamusic.ir jenabmusic.com ganja2music.com iran-music.net silamusic.ir bibakmusic.com blogmusic.ir pop-music.ir behmusic.com irmp3.ir next1.ir musicdel.ir mybia2music.com musics-fa.com musicito.com persianamusic.ir songironi.ir farsiplayer.com meloyab.com playmusic.ir radiojavan.com beeptunes.com navaak.com santoori.com easy-persian.com musicmedia.ir smusic.ir rosemusics.com tabanmusic.com mokhtalefmusic.com musiclove.ir ahaang.com madarmusic.ir nab-music.com music-single.com musicweek.ir ir-music.ir upmusic.ir upmusics.ir takmusics.com tapmusics.ir isongs.ir nashid.ir music-saz.ir ahangfakher.ir top10music.ir sahebmusic.ir batomusic.ir talesh-music.ir farsmusical.ir jmp3.ir hailymusic.ir tanin-taraneh.ir mazanimusic.ir radiomazani.com abrarecord.com babol3da.com musics-mehr.com musicbaran.org musicbaran.ir melodyfa.com melodyha.com ahangestan.com ahang98.com ahangino.com ahangchi.com ahangdownload.com ahangha.com musicsweb.ir music98.ir musicema.com musicema.ir musiciranian.com musicjavan.com musicjavan.ir musicdel.net musicdel.org music-fa.ir music-fa.net rozmusic.net rozmusic.ir nicmusic.ir jenab-music.com jenabmusic.ir ganja2music.ir silamusic.com bibakmusic.ir pop-music.com behmusic.ir irmp3.com next1music.com next1.ir apmusic.ir apmusics.ir tehranmusic.com tehranmusic.ir tehran-music.ir musicscity.ir music-city.ir musicsweb.com musicweb.ir musicwebs.ir musiciran.ir musiciran.com iranmusic.ir iranmusic.com iran-music.com iranianmusic.ir iranianmusic.com persian-music.ir persianmusic.ir persianmusic.com persianmusic.net persianmusics.ir persianmusics.com persian-song.com persiansong.ir persiansongs.ir farsimusic.ir farsimusic.com farsisong.ir farsisong.com farsimp3.ir farsimp3.com farsimusic.net parsmp3.ir parsmp3.com parsmusic.ir parsmusic.com parsmusic.net iranmp3.ir iranmp3.com iranmp3.net mp3iran.ir mp3iran.com mp3iran.net mp3music.ir mp3music.com mp3music.net musicdl.ir musicdl.com musicdownload.ir musicdownload.com downloadmusic.ir downloadmusic.com ahangdownload.ir downloadahang.ir downloadahang.com mp3download.ir mp3download.com ahangnew.ir ahangnew.com newmusic.ir newmusic.com newmusic.net newmusics.ir newmusics.com iranmusicdownload.ir iranmusicdownload.com persianmusicdownload.ir persianmusicdownload.com musicsdownload.ir musicsdownload.com ahangroz.ir ahangroz.com ahangiran.ir ahangiran.com ahangpersian.ir ahangpersian.com musicirani.ir musicirani.com iranmusics.ir iranmusics.com musicirani.net musicpersian.ir musicpersian.com musicpersian.net musicstar.ir musicstar.com musicstars.ir musicstars.com musicland.ir musicland.com musicplanet.ir musicplanet.com musicbox.ir musicbox.com musicroom.ir musicroom.com musicradio.ir musicradio.com radio-music.ir radio-music.com songfa.ir songfa.com songmusic.ir songmusic.com songmp3.ir songmp3.com mp3song.ir mp3song.com ahang2.ir ahang2.com ahangplus.ir ahangplus.com musicplus.ir musicplus.com musicplus.net ahang24.ir ahang24.com music24.ir music24.com music24.net ahangmix.ir ahangmix.com musicmix.ir musicmix.com musicmix.net remixfa.ir remixfa.com remixmusic.ir remixmusic.com remixmusic.net djmusic.ir djmusic.com djmusic.net djsong.ir djsong.com djsongs.ir djsongs.com rapfa.ir rapfa.com rapmusic.ir rapmusic.com rapmusic.net hiphopfa.ir hiphopfa.com hiphopmusic.ir hiphopmusic.com classicmusic.ir classicmusic.com oldmusic.ir oldmusic.com nostalgicmusic.ir nostalgicmusic.com
'''.split()

INTERNATIONAL_SITES = '''
soundcloud.com bandcamp.com audius.co audius.one mixcloud.com hearthis.at jamendo.com freemusicarchive.org archive.org ccMixter.org ccMixter.cc last.fm lastfm.com beatport.com traxsource.com junodownload.com boomkat.com bleep.com residentadvisor.net residentadvisor.com discogs.com rateyourmusic.com allmusic.com noisetrade.com reverbnation.com soundclick.com purevolume.com 8tracks.com diymag.com xlr8r.com edm.com dancingastronaut.com thissongissick.com edmidentity.com youredm.com electronicgroove.com magneticmag.com attackmagazine.com insomniac.com mau5trap.com monstercat.com spinninrecords.com anjunabeats.com anjunadeep.com armadamusic.com toolroomrecords.com defected.com drumcode.se bitbird.com dimmak.com drumandbassarena.com ukf.com dnbdojo.com livesets.com livesets.fm techno.fm techno-sounds.com technomusic.com electro-music.com electronica.org
'''.split()

# Keep international platforms first so the generated Google query stays useful
# even when a search engine truncates very long OR expressions.
SITES = list(dict.fromkeys(INTERNATIONAL_SITES + SITES))
assert len(SITES) > 200, len(SITES)

# 1) Make Google search site-aware without making the rest of the app depend on any one host.
search = SEARCH.read_text(encoding="utf-8")
if "PERSIAN_MUSIC_SITES" not in search:
    anchor = '    private val commonTypos = mapOf('
    pos = search.index(anchor)
    end = search.index('\n\n', pos)
    declaration = '\n\n    // Large discovery pool: these are Google hints, not mandatory servers.\n    val PERSIAN_MUSIC_SITES = listOf(\n' + ''.join('        "' + d + '",\n' for d in SITES) + '    )\n'
    search = search[:end] + declaration + search[end:]

pattern = r'    fun buildGoogleQuery\(input: String\): String \{.*?\n    \}\n\n    fun parseArtistTitle'
replacement = '''    fun buildGoogleQuery(input: String): String {
        val original = displayQuery(input)
        if (original.isBlank()) return "music"
        val corrected = correctedQuery(original)
        val base = if (corrected.isNotBlank() && corrected != normalizeQuery(original) && corrected != original) {
            "($original OR $corrected)"
        } else original
        // Use a bounded, prioritized set: giant Google OR expressions can be truncated.
        val siteFilter = PERSIAN_MUSIC_SITES.take(90).joinToString(" OR ") { "site:$it" }
        return "$base ($siteFilter)"
    }

    fun parseArtistTitle'''
search, n = re.subn(pattern, replacement, search, count=1, flags=re.S)
if n != 1:
    raise SystemExit("buildGoogleQuery not found")
SEARCH.write_text(search, encoding="utf-8")

# 2) YouTube: clicking a result opens a 16:9 WebView in the top-right corner of MainActivity.
main = MAIN.read_text(encoding="utf-8")

# Fix the CI compile error caused by the WebChromeClient reference without its import.
if "import android.webkit.WebChromeClient" not in main:
    main = main.replace("import android.webkit.RenderProcessGoneDetail\n", "import android.webkit.RenderProcessGoneDetail\nimport android.webkit.WebChromeClient\n", 1)

if "YOUTUBE_CORNER_PLAYER_V1" not in main:
    field = "    private lateinit var vinyl: VinylView\n"
    if field not in main: raise SystemExit("vinyl field not found")
    main = main.replace(field, field + "    private lateinit var youtubeCornerPlayer: WebView\n    private lateinit var youtubeCornerClose: TextView\n", 1)

    setup_call = "        setupWebView()\n"
    if setup_call not in main: raise SystemExit("setupWebView call not found")
    main = main.replace(setup_call, setup_call + "        setupYouTubeCornerPlayer()\n", 1)

    setup = '''    // YOUTUBE_CORNER_PLAYER_V1
    @SuppressLint("SetJavaScriptEnabled")
    private fun setupYouTubeCornerPlayer() {
        youtubeCornerPlayer = findViewById(R.id.youtubeCornerPlayer)
        youtubeCornerClose = findViewById(R.id.youtubeCornerClose)
        youtubeCornerPlayer.settings.javaScriptEnabled = true
        youtubeCornerPlayer.settings.domStorageEnabled = true
        youtubeCornerPlayer.settings.mediaPlaybackRequiresUserGesture = false
        youtubeCornerPlayer.webChromeClient = WebChromeClient()
        youtubeCornerPlayer.webViewClient = object : WebViewClient() {}
        youtubeCornerClose.setOnClickListener {
            youtubeCornerPlayer.stopLoading()
            youtubeCornerPlayer.loadUrl("about:blank")
            youtubeCornerPlayer.visibility = View.GONE
            youtubeCornerClose.visibility = View.GONE
        }
    }

    private fun showYouTubeCornerPlayer(url: String) {
        val id = youtubeVideoId(url)
        if (id.isBlank()) {
            Toast.makeText(this, "شناسه ویدئوی YouTube پیدا نشد", Toast.LENGTH_SHORT).show()
            return
        }
        youtubeCornerPlayer.visibility = View.VISIBLE
        youtubeCornerClose.visibility = View.VISIBLE
        youtubeCornerPlayer.loadUrl("https://www.youtube.com/embed/$id?autoplay=1&playsinline=1&rel=0")
    }

'''
    anchor = "    private fun setupButtons() {\n"
    if anchor not in main: raise SystemExit("setupButtons anchor not found")
    main = main.replace(anchor, setup + anchor, 1)

    pattern = r'    private fun addYouTubeView\(url: String, title: String\) \{.*?\n    \}\n\n    private fun youtubeVideoId'
    replacement = '''    private fun addYouTubeView(url: String, title: String) {
        val row = LinearLayout(this).apply {
            orientation = LinearLayout.HORIZONTAL
            gravity = Gravity.CENTER_VERTICAL
            setPadding(12, 10, 12, 10)
            setBackgroundColor(0xFF15151D.toInt())
            setOnClickListener { showYouTubeCornerPlayer(url) }
        }
        val cover = ImageView(this).apply {
            setBackgroundColor(0xFF22222A.toInt())
            scaleType = ImageView.ScaleType.CENTER_CROP
        }
        row.addView(cover, LinearLayout.LayoutParams(58, 58))
        val id = youtubeVideoId(url)
        if (id.isNotBlank()) loadCover("https://i.ytimg.com/vi/$id/hqdefault.jpg", cover)
        val box = LinearLayout(this).apply {
            orientation = LinearLayout.VERTICAL
            gravity = Gravity.CENTER_VERTICAL
            setPadding(12, 0, 8, 0)
        }
        val t = TextView(this).apply {
            text = if (title.isBlank()) "YouTube" else title
            setTextColor(0xFFFFFFFF.toInt())
            textSize = 15f
            maxLines = 2
        }
        val sub = TextView(this).apply {
            text = "YouTube • پخش داخل برنامه"
            setTextColor(0xFFFF5555.toInt())
            textSize = 11f
        }
        box.addView(t)
        box.addView(sub)
        row.addView(box, LinearLayout.LayoutParams(0, LinearLayout.LayoutParams.WRAP_CONTENT, 1f))
        resultsContainer.addView(row, LinearLayout.LayoutParams(LinearLayout.LayoutParams.MATCH_PARENT, LinearLayout.LayoutParams.WRAP_CONTENT).apply {
            setMargins(0, 0, 0, 8)
        })
    }

    private fun youtubeVideoId'''
    main, n = re.subn(pattern, replacement, main, count=1, flags=re.S)
    if n != 1: raise SystemExit("addYouTubeView not found")

MAIN.write_text(main, encoding="utf-8")

layout = LAYOUT.read_text(encoding="utf-8")
if 'android:id="@+id/youtubeCornerPlayer"' not in layout:
    anchor = '    <WebView android:id="@+id/web" android:layout_width="1dp" android:layout_height="1dp" android:visibility="visible" tools:ignore="WebViewLayout" />'
    overlay = '''    <WebView
        android:id="@+id/youtubeCornerPlayer"
        android:layout_width="240dp"
        android:layout_height="135dp"
        android:layout_gravity="top|end"
        android:layout_marginTop="12dp"
        android:layout_marginEnd="12dp"
        android:visibility="gone"
        android:elevation="30dp"
        android:background="@android:color/black"
        tools:ignore="WebViewLayout" />
    <TextView
        android:id="@+id/youtubeCornerClose"
        android:layout_width="30dp"
        android:layout_height="30dp"
        android:layout_gravity="top|end"
        android:layout_marginTop="6dp"
        android:layout_marginEnd="6dp"
        android:gravity="center"
        android:text="×"
        android:textColor="@color/white"
        android:textSize="20sp"
        android:background="@drawable/bg_secondary_button"
        android:elevation="31dp"
        android:visibility="gone"
        android:clickable="true"
        android:focusable="true"
        android:contentDescription="Close YouTube player" />
    <WebView android:id="@+id/web" android:layout_width="1dp" android:layout_height="1dp" android:visibility="visible" tools:ignore="WebViewLayout" />'''
    if anchor not in layout: raise SystemExit("web anchor not found")
    layout = layout.replace(anchor, overlay, 1)
    LAYOUT.write_text(layout, encoding="utf-8")

print(f"Applied {len(SITES)} Persian + international music discovery domains and YouTube corner player")
