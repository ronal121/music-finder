from pathlib import Path

TARGET = Path("app/src/main/java/com/kafshar/musicfinder/MainActivity.kt")
s = TARGET.read_text(encoding="utf-8")

s = s.replace(
'''data class SongResult(
    val url: String,
    val title: String,
    val artist: String,
    val site: String,
    val cover: String = ""
)''',
'''data class SongResult(
    val url: String,
    val title: String,
    val artist: String,
    val site: String,
    val cover: String = "",
    val isYouTube: Boolean = false
)''', 1)

old = '''                resultPages =
                    raw.orEmpty()
                        .split("###")
                        .map { it.trim() }
                        .filter {
                            it.substringBefore(
                                "|||"
                            ).startsWith(
                                "http",
                                true
                            )
                        }
                        .distinctBy {
                            it.substringBefore("|||")
                        }
                        .take(50)

                if (resultPages.isEmpty()) {'''
new = '''                val discovered = raw.orEmpty()
                    .split("###")
                    .map { it.trim() }
                    .mapNotNull { entry ->
                        val p = entry.split("|||", limit = 3)
                        val url = p.getOrNull(0)?.trim().orEmpty()
                        if (!url.startsWith("http", true)) return@mapNotNull null
                        val title = decode(p.getOrNull(1)?.trim().orEmpty())
                        val isYouTube = p.getOrNull(2) == "1" || ServerConfig.isYouTubeUrl(url)
                        Triple(url, title, isYouTube)
                    }
                    .distinctBy { it.first.substringBefore("#").trimEnd('/').lowercase() }
                    .take(15)

                discovered.filter { it.third }.forEach { (url, title, _) ->
                    addYouTubeView(url, title.ifBlank { "YouTube" })
                }

                resultPages = discovered
                    .filterNot { it.third }
                    .map { "${it.first}|||${it.second}" }

                if (resultPages.isEmpty()) {'''
if old not in s:
    raise SystemExit("Bridge results block not found")
s = s.replace(old, new, 1)

old = '''                val audio =
                    decode(parts[3])
                        .split("|||")
                        .map { it.trim() }
                        .firstOrNull {
                            it.startsWith(
                                "http",
                                true
                            )
                        }
                        .orEmpty()

                if (
                    audio.isNotBlank() &&
                    ServerConfig.isAllowedMediaUrl(
                        audio
                    )
                ) {

                    val song =
                        SongResult(
                            audio,
                            title,
                            artist,
                            getSiteName(
                                expectedPageUrl
                            ),
                            cover
                        )

                    if (
                        songs.none {
                            it.url == song.url
                        }
                    ) {

                        songs.add(song)

                        addSongView(
                            song,
                            songs.lastIndex
                        )

                        status.text =
                            "${songs.size} آهنگ پیدا شد"
                    }
                }

                finishCurrentResultPage()'''
new = '''                val audioCandidates = decode(parts[3])
                    .split("|||")
                    .map { it.trim() }
                    .filter { it.startsWith("http", true) }
                    .distinct()
                    .take(30)

                validateAndAddAudioCandidates(
                    title,
                    artist,
                    cover,
                    audioCandidates,
                    expectedPageUrl
                )'''
if old not in s:
    raise SystemExit("Bridge page block not found")
s = s.replace(old, new, 1)

old = '''        searchFuture = ParallelSearchEngine.searchDirect(text, generation) { callbackGeneration, candidates ->
            runOnUiThread {
                if (destroyed || callbackGeneration != searchGeneration) return@runOnUiThread
                resultPages = candidates.map { it.url }
                    .filter { ServerConfig.isAllowedPageUrl(it) }
                    .distinctBy { it.substringBefore("#").trimEnd('/').lowercase() }
                    .take(60)
                resultPageIndex = 0
                if (resultPages.isEmpty()) {
                    status.text = "منابع مستقیم نتیجه‌ای ندادند؛ در حال جستجوی جایگزین..."
                    loadGoogleFallback(text, generation)
                } else {
                    status.text = "${resultPages.size} صفحه پیدا شد؛ در حال استخراج آهنگ..."
                    processNextResultPage()
                }
            }
        }'''
new = '''        // Google is the only discovery source. No hard-coded music-site list is used.
        loadGoogleFallback(text, generation)'''
if old not in s:
    raise SystemExit("searchMusic block not found")
s = s.replace(old, new, 1)

start = s.index('    private fun extractGoogleResults() {')
end = s.index('    private fun extractMusicPage(', start)
new_func = r'''    private fun extractGoogleResults() {

        if (destroyed || searchGeneration <= 0) return

        val script = """
            (function(){
              try{
                var found=[];
                function real(h){
                  try{
                    var x=new URL(h, location.href);
                    if(x.hostname.toLowerCase().indexOf('google.')>=0){
                      var q=x.searchParams.get('q') || x.searchParams.get('url');
                      if(q && q.indexOf('http')===0) return decodeURIComponent(q);
                    }
                    return x.href;
                  }catch(e){ return h; }
                }
                function youtube(u){
                  try{
                    var h=new URL(u).hostname.toLowerCase().replace(/^www\\./,'');
                    return h==='youtube.com' || h.endsWith('.youtube.com') || h==='youtu.be';
                  }catch(e){ return false; }
                }
                var anchors=document.querySelectorAll('a');
                for(var i=0;i<anchors.length && found.length<15;i++){
                  var a=anchors[i];
                  var u=real(a.href||'');
                  if(!/^https?:/i.test(u)) continue;
                  try{
                    var h=new URL(u).hostname.toLowerCase();
                    if(h.indexOf('google.')>=0 || h==='webcache.googleusercontent.com') continue;
                  }catch(e){ continue; }
                  var t=(a.innerText||a.textContent||'').replace(/[\\r\\n\\t]+/g,' ').replace(/\\s+/g,' ').trim();
                  if(!t && a.querySelector('h3')) t=a.querySelector('h3').innerText||'';
                  var key=u.split('#')[0].replace(/\\/$/,'').toLowerCase();
                  var dup=false;
                  for(var j=0;j<found.length;j++){ if(found[j].split('|||')[0].toLowerCase()===key){dup=true;break;} }
                  if(dup) continue;
                  found.push(u+'|||'+encodeURIComponent(t)+'|||'+(youtube(u)?'1':'0'));
                }
                MusicFinder.results(found.join('###'));
              }catch(e){ MusicFinder.results(''); }
            })();
        """.trimIndent()

        try { web.evaluateJavascript(script, null) } catch (_: Exception) { finishSearch() }
    }

'''
s = s[:start] + new_func + s[end:]

start = s.index('    private fun extractMusicPage(')
end = s.index('    private fun processNextResultPage()', start)
new_func = r'''    private fun extractMusicPage(pageUrl: String) {

        if (destroyed || resultGeneration != searchGeneration) return
        expectedPageUrl = pageUrl

        val script = """
            (function(){
              try{
                var title='',artist='',cover='',aud=[];
                function add(v){
                  if(!v) return;
                  try{ v=new URL(v, location.href).href; }catch(e){ return; }
                  if(!/^https?:/i.test(v)) return;
                  if(aud.indexOf(v)<0 && aud.length<40) aud.push(v);
                }
                var og=document.querySelector('meta[property="og:title"]');
                if(og) title=og.content||'';
                var h=document.querySelector('h1');
                if(!title && h) title=h.innerText||'';
                var ma=document.querySelector('meta[property="music:musician"]');
                if(ma) artist=ma.content||'';
                var im=document.querySelector('meta[property="og:image"]');
                if(im) cover=im.content||'';
                document.querySelectorAll('audio,video,source,a').forEach(function(el){
                  add(el.currentSrc||el.src||el.href||'');
                  ['data-src','data-url','data-audio','data-mp3','data-file','data-download','data-media','data-stream'].forEach(function(k){ add(el.getAttribute(k)||''); });
                });
                document.querySelectorAll('script,script[type="application/ld+json"]').forEach(function(el){
                  var text=el.textContent||'';
                  var matches=text.match(/https?:\\/\\/[^\\s\\"'<>\\\\]+/g)||[];
                  matches.forEach(add);
                });
                var html=document.documentElement.outerHTML||'';
                var urls=html.match(/https?:\\/\\/[^\\s\\"'<>\\\\]+/g)||[];
                urls.forEach(function(v){
                  if(/(?:\\.mp3|\\.m4a|\\.aac|\\.ogg|\\.opus|\\.wav|\\.flac|\\.webm|download|\\/dl\\/|\\/api\\/audio|media|stream)/i.test(v)) add(v);
                });
                MusicFinder.page(encodeURIComponent(title)+'###'+encodeURIComponent(artist)+'###'+encodeURIComponent(cover)+'###'+encodeURIComponent(aud.join('|||')));
              }catch(e){ MusicFinder.page('######'); }
            })();
        """.trimIndent()

        try { web.evaluateJavascript(script, null) } catch (_: Exception) { finishCurrentResultPage() }
    }

    private fun validateAndAddAudioCandidates(
        title: String,
        artist: String,
        cover: String,
        candidates: List<String>,
        pageUrl: String
    ) {
        if (candidates.isEmpty()) { finishCurrentResultPage(); return }
        val generation = searchGeneration
        io.execute {
            val accepted = candidates.mapNotNull { url ->
                if (!ServerConfig.isAllowedMediaUrl(url, pageUrl)) return@mapNotNull null
                if (probeMediaUrl(url, pageUrl)) url else null
            }.distinct()
            runOnUiThread {
                if (destroyed || generation != searchGeneration) return@runOnUiThread
                accepted.forEach { audio ->
                    val song = SongResult(audio, title, artist, getSiteName(pageUrl), cover)
                    if (songs.none { it.url == song.url }) {
                        songs.add(song)
                        addSongView(song, songs.lastIndex)
                    }
                }
                if (songs.isNotEmpty()) status.text = "${songs.size} آهنگ پیدا شد"
                finishCurrentResultPage()
            }
        }
    }

    private fun probeMediaUrl(url: String, pageUrl: String): Boolean {
        if (!ServerConfig.isAllowedMediaUrl(url, pageUrl)) return false
        fun request(method: String): String? {
            return try {
                val c = URL(url).openConnection() as HttpURLConnection
                c.requestMethod = method
                c.instanceFollowRedirects = true
                c.connectTimeout = 2500
                c.readTimeout = 2500
                c.setRequestProperty("User-Agent", "Mozilla/5.0 (Linux; Android 12) AppleWebKit/537.36 Chrome/128 Mobile Safari/537.36")
                c.setRequestProperty("Referer", pageUrl)
                if (method == "GET") c.setRequestProperty("Range", "bytes=0-0")
                c.connect()
                val type = c.contentType?.lowercase()
                val code = c.responseCode
                c.disconnect()
                if (code in 200..399) type else null
            } catch (_: Exception) { null }
        }
        val type = request("HEAD") ?: request("GET")
        return type?.startsWith("audio/") == true ||
            (type?.startsWith("video/") == true && url.contains("audio", true)) ||
            ServerConfig.looksLikeAudioUrl(url)
    }

    private fun addYouTubeView(url: String, title: String) {
        val row = LinearLayout(this).apply {
            orientation = LinearLayout.HORIZONTAL
            gravity = Gravity.CENTER_VERTICAL
            setPadding(12, 10, 12, 10)
            setBackgroundColor(0xFF15151D.toInt())
            setOnClickListener {
                try {
                    startActivity(Intent(Intent.ACTION_VIEW, android.net.Uri.parse(url)))
                } catch (_: Exception) {
                    Toast.makeText(this@MainActivity, "باز کردن YouTube ممکن نیست", Toast.LENGTH_SHORT).show()
                }
            }
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
            text = "YouTube • باز کردن"
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

    private fun youtubeVideoId(url: String): String {
        return try {
            val u = android.net.Uri.parse(url)
            when {
                u.host?.contains("youtu.be", true) == true -> u.pathSegments.firstOrNull().orEmpty()
                u.getQueryParameter("v") != null -> u.getQueryParameter("v").orEmpty()
                else -> u.pathSegments.firstOrNull { it.length >= 8 && it.matches(Regex("[A-Za-z0-9_-]{8,}")) }.orEmpty()
            }
        } catch (_: Exception) { "" }
    }

'''
s = s[:start] + new_func + s[end:]

TARGET.write_text(s, encoding="utf-8")
print("Search migration applied")
