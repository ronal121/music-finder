from pathlib import Path

ROOT = Path(__file__).resolve().parents[1]
MAIN = ROOT / "app/src/main/java/com/kafshar/musicfinder/MainActivity.kt"
text = MAIN.read_text(encoding="utf-8")

if "// RUNTIME_SEARCH_FIX_V8" not in text:
    anchor = "    private fun playYouTubeInsideApp(url: String, title: String) {\n"
    if anchor not in text:
        raise SystemExit("playYouTubeInsideApp anchor not found")

    replacement = '''    // RUNTIME_SEARCH_FIX_V8
    private fun playYouTubeInsideApp(url: String, title: String) {
        val id = youtubeVideoId(url)
        if (id.isBlank()) {
            Toast.makeText(this, "شناسه ویدئوی YouTube پیدا نشد", Toast.LENGTH_SHORT).show()
            return
        }
        currentAudioUrl = ""
        currentSong = null
        currentIndex = -1
        titleText.text = title.ifBlank { "YouTube" }
        artistText.text = "YouTube • پخش داخل برنامه"
        status.text = "در حال پخش YouTube..."

        // YouTube's official IFrame API requires at least a 200x200 viewport.
        // Keep that viewport completely transparent and off the visible UI.
        web.alpha = 0f
        web.layoutParams = web.layoutParams.apply {
            width = (200 * resources.displayMetrics.density).toInt()
            height = (200 * resources.displayMetrics.density).toInt()
        }

        val html = """
            <!doctype html><html><body style="margin:0;background:transparent;overflow:hidden">
            <div id="player" style="width:200px;height:200px"></div>
            <script>
              var tag=document.createElement('script');
              tag.src='https://www.youtube.com/iframe_api';
              document.head.appendChild(tag);
              var player;
              function onYouTubeIframeAPIReady(){
                player=new YT.Player('player',{
                  width:'200',height:'200',videoId:'$id',
                  playerVars:{autoplay:1,playsinline:1,controls:0,rel:0,fs:0},
                  events:{onReady:function(e){e.target.setVolume(100);e.target.playVideo();}}
                });
              }
            </script></body></html>
        """.trimIndent()
        try {
            web.stopLoading()
            web.loadDataWithBaseURL("https://www.youtube.com/", html, "text/html", "UTF-8", null)
        } catch (_: Exception) {
            Toast.makeText(this, "پخش YouTube در برنامه ممکن نیست", Toast.LENGTH_SHORT).show()
        }
    }

'''
    text = text.replace(anchor, replacement, 1)

# Return the hidden search WebView to its original tiny footprint whenever a new
# Google search begins. This does not affect the user-facing layout.
needle = '''        // Google is the only discovery source. No hard-coded music-site list is used.
        loadGoogleFallback(text, generation)
'''
replacement = '''        // Google is the only discovery source. No hard-coded music-site list is used.
        web.alpha = 1f
        web.layoutParams = web.layoutParams.apply {
            width = 1
            height = 1
        }
        loadGoogleFallback(text, generation)
'''
if needle in text:
    text = text.replace(needle, replacement, 1)

MAIN.write_text(text, encoding="utf-8")
print("Runtime search/player patch V8 applied")
