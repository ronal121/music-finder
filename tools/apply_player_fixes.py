from pathlib import Path

ROOT = Path(__file__).resolve().parents[1]
MAIN = ROOT / 'app/src/main/java/com/kafshar/musicfinder/MainActivity.kt'
SERVICE = ROOT / 'app/src/main/java/com/kafshar/musicfinder/MusicService.kt'

m = MAIN.read_text()
s = SERVICE.read_text()

# Search all configured sites.
m = m.replace('''        val searchQuery =
            "\\\"$text\\\" " +
                    "(site:rozmusic.com OR " +
                    "site:mybia2music.com OR " +
                    "site:musicdel.ir OR " +
                    "site:musics-fa.com)"''',
              '        val searchQuery = ServerConfig.searchQuery(text)', 1)

m = m.replace('''        val script = """
            (function() {
                try {
                    var links =
                        document.querySelectorAll("a");''',
              '''        val allowedHostsJs = ServerConfig.MUSIC_SITES.joinToString(",") { "\\\"$it\\\"" }

        val script = """
            (function() {
                try {
                    var allowedHosts = [$allowedHostsJs];
                    var links =
                        document.querySelectorAll("a");''', 1)

m = m.replace('''                        var allowed =
                            lower.indexOf("rozmusic.com") >= 0 ||
                            lower.indexOf("mybia2music.com") >= 0 ||
                            lower.indexOf("musicdel.ir") >= 0 ||
                            lower.indexOf("musics-fa.com") >= 0;''',
              '''                        var allowed = false;
                        for (var h = 0; h < allowedHosts.length; h++) {
                            if (lower.indexOf(allowedHosts[h]) >= 0) {
                                allowed = true;
                                break;
                            }
                        }''', 1)

# Highlight the playing search result.
m = m.replace('''                setBackgroundColor(
                    0xFF15151D.toInt()
                )
            }''',
              '''                setBackgroundColor(
                    0xFF15151D.toInt()
                )
                tag = song.url
            }''', 1)

marker = '''        row.setOnClickListener {

            val position =
                songs.indexOfFirst {
                    it.url == song.url
                }

            if (position >= 0) {

                currentIndex = position

                playSong(song)
            }
        }
    }

    private fun toggleLibrarySong('''
replacement = '''        row.setOnClickListener {

            val position =
                songs.indexOfFirst {
                    it.url == song.url
                }

            if (position >= 0) {

                currentIndex = position

                playSong(song)
            }
        }
    }

    private fun updateActiveResultHighlight(url: String) {
        if (destroyed) return
        for (i in 0 until resultsContainer.childCount) {
            val child = resultsContainer.getChildAt(i)
            val active = url.isNotBlank() && child.tag == url
            child.setBackgroundColor(if (active) 0xFF244343.toInt() else 0xFF15151D.toInt())
            child.alpha = if (active) 1f else 0.82f
        }
    }

    private fun toggleLibrarySong('''
m = m.replace(marker, replacement, 1)

m = m.replace('''                val volume =
                    intent.getIntExtra(
                        "volume",
                        -1
                    )

                runOnUiThread {''',
              '''                val volume =
                    intent.getIntExtra(
                        "volume",
                        -1
                    )

                val mediaUrl = intent.getStringExtra("mediaUrl") ?: ""

                runOnUiThread {''', 1)

m = m.replace('''                    if (volume in 0..100) {
                        volumeSeekBar.progress = volume
                        volumeText.text = "$volume%"
                    }
                }''',
              '''                    if (volume in 0..100) {
                        volumeSeekBar.progress = volume
                        volumeText.text = "$volume%"
                    }

                    if (mediaUrl.isNotBlank()) updateActiveResultHighlight(mediaUrl)
                }''', 1)

# Time labels are continuously fed by MusicService; never leave them blank/stale.
m = m.replace('''        if (duration > 0) {

            val percent =
                (
                    position.toDouble() /
                            duration.toDouble() *
                            100.0
                    )
                    .toInt()
                    .coerceIn(0, 100)

            seekBar.progress =
                percent

            currentTimeText.text =
                formatTime(position)

            durationText.text =
                formatTime(duration)
        }

        playButton.text =''',
              '''        currentTimeText.text = formatTime(position)
        durationText.text = formatTime(duration)

        if (duration > 0) {
            seekBar.progress = (position.toDouble() / duration.toDouble() * 100.0).toInt().coerceIn(0, 100)
        }

        playButton.text =''', 1)

m = m.replace('''        currentSong = song
        currentAudioUrl = song.url

        titleText.text =''',
              '''        currentSong = song
        currentAudioUrl = song.url
        updateActiveResultHighlight(song.url)

        titleText.text =''', 1)

# Service owns next/previous so the related queue is also used by app buttons.
start = m.find('    private fun nextSong() {')
end = m.find('    private fun sendServiceAction(', start)
if start >= 0 and end > start:
    m = m[:start] + '''    private fun nextSong() {
        if (destroyed) return
        safelyStartService(Intent(this, MusicService::class.java).apply {
            action = MusicService.ACTION_NEXT
        })
    }

    private fun previousSong() {
        if (destroyed) return
        safelyStartService(Intent(this, MusicService::class.java).apply {
            action = MusicService.ACTION_PREVIOUS
        })
    }

''' + m[end:]

# Keep results from earlier searches, giving the related queue a larger pool.
old = '''        val limited =
            songs.take(60)

        val data =
            limited.joinToString("\\n") {'''
new = '''        val prefs = getSharedPreferences("search_results", MODE_PRIVATE)
        val oldData = prefs.getString("songs", "") ?: ""
        val merged = ArrayList<SongResult>()
        oldData.split("\\n").forEach { line ->
            val parts = line.split("|||", limit = 5)
            if (parts.size == 5 && merged.none { it.url == parts[0] }) {
                merged.add(SongResult(parts[0], parts[1], parts[2], parts[3], parts[4]))
            }
        }
        songs.forEach { song -> if (merged.none { it.url == song.url }) merged.add(song) }

        val limited = merged.take(200)

        val data =
            limited.joinToString("\\n") {'''
m = m.replace(old, new, 1)

# Hardware volume is already controlled by STREAM_MUSIC in the stable service.
# Add explicit notification actions without replacing the Media3 session.
s = s.replace('''        const val ACTION_SET_VOLUME = "com.kafshar.musicfinder.SET_VOLUME"''',
              '''        const val ACTION_SET_VOLUME = "com.kafshar.musicfinder.SET_VOLUME"
        const val ACTION_REWIND_10 = "com.kafshar.musicfinder.REWIND_10"
        const val ACTION_FORWARD_10 = "com.kafshar.musicfinder.FORWARD_10"''', 1)

s = s.replace('''                ACTION_GET_POSITION -> safeSendUpdate()
                ACTION_STOP -> {''',
              '''                ACTION_GET_POSITION -> safeSendUpdate()
                ACTION_REWIND_10 -> seekRelative(-10_000L)
                ACTION_FORWARD_10 -> seekRelative(10_000L)
                ACTION_STOP -> {''', 1)

old = '''    private fun buildForegroundNotification(): Notification {
        return NotificationCompat.Builder(this, NOTIFICATION_CHANNEL_ID)
            .setSmallIcon(android.R.drawable.ic_media_play)
            .setContentTitle(getCurrentTitle())
            .setContentText(getCurrentArtist())
            .setContentIntent(createOpenAppPendingIntent())
            .setOngoing(true)
            .setOnlyAlertOnce(true)
            .setShowWhen(false)
            .setVisibility(NotificationCompat.VISIBILITY_PUBLIC)
            .setCategory(NotificationCompat.CATEGORY_TRANSPORT)
            .setPriority(NotificationCompat.PRIORITY_LOW)
            .build()
    }
'''
new = '''    private fun buildForegroundNotification(): Notification {
        val builder = NotificationCompat.Builder(this, NOTIFICATION_CHANNEL_ID)
            .setSmallIcon(android.R.drawable.ic_media_play)
            .setContentTitle(getCurrentTitle())
            .setContentText(getCurrentArtist())
            .setContentIntent(createOpenAppPendingIntent())
            .setOngoing(false)
            .setOnlyAlertOnce(true)
            .setShowWhen(false)
            .setVisibility(NotificationCompat.VISIBILITY_PUBLIC)
            .setCategory(NotificationCompat.CATEGORY_TRANSPORT)
            .setPriority(NotificationCompat.PRIORITY_LOW)
            .addAction(android.R.drawable.ic_media_previous, "Previous", actionPendingIntent(ACTION_PREVIOUS, 301))
            .addAction(android.R.drawable.ic_media_rew, "-10s", actionPendingIntent(ACTION_REWIND_10, 302))
            .addAction(
                if (::player.isInitialized && player.isPlaying) android.R.drawable.ic_media_pause else android.R.drawable.ic_media_play,
                if (::player.isInitialized && player.isPlaying) "Pause" else "Play",
                actionPendingIntent(ACTION_TOGGLE, 303)
            )
            .addAction(android.R.drawable.ic_media_ff, "+10s", actionPendingIntent(ACTION_FORWARD_10, 304))
            .addAction(android.R.drawable.ic_media_next, "Next", actionPendingIntent(ACTION_NEXT, 305))
            .addAction(android.R.drawable.ic_menu_close_clear_cancel, "Stop", actionPendingIntent(ACTION_STOP, 306))
        return builder.build()
    }

    private fun actionPendingIntent(action: String, requestCode: Int): PendingIntent {
        val intent = Intent(this, MusicService::class.java).apply { this.action = action }
        return PendingIntent.getService(
            this,
            requestCode,
            intent,
            PendingIntent.FLAG_UPDATE_CURRENT or PendingIntent.FLAG_IMMUTABLE
        )
    }

    private fun seekRelative(deltaMs: Long) {
        try {
            val duration = player.duration
            val target = player.currentPosition + deltaMs
            player.seekTo(if (duration > 0 && duration != C.TIME_UNSET) target.coerceIn(0L, duration) else target.coerceAtLeast(0L))
            safeSendUpdate()
            updateForegroundNotification()
        } catch (_: Exception) { }
    }
'''
if old not in s:
    raise SystemExit('notification block not found')
s = s.replace(old, new, 1)

s = s.replace('''                putExtra("artist", item?.mediaMetadata?.artist?.toString() ?: "")
                putExtra("volume", volume)''',
              '''                putExtra("artist", item?.mediaMetadata?.artist?.toString() ?: "")
                putExtra("mediaUrl", item?.mediaId ?: item?.localConfiguration?.uri?.toString() ?: "")
                putExtra("volume", volume)''', 1)

# Same-site related queue scored by artist/title.
start = s.find('    private fun buildRelatedQueue(')
end = s.find('    private fun createMediaItem(', start)
if start < 0 or end < 0:
    raise SystemExit('related queue not found')
queue = '''    private fun buildRelatedQueue(
        currentUrl: String,
        currentTitle: String,
        currentArtist: String,
        currentCover: String
    ): List<MediaItem> {
        val result = ArrayList<MediaItem>()
        result.add(createMediaItem(currentUrl, currentTitle, currentArtist, currentCover))
        try {
            val data = getSharedPreferences("search_results", MODE_PRIVATE).getString("songs", "") ?: ""
            if (data.isBlank()) return result
            val host = try { Uri.parse(currentUrl).host?.lowercase()?.removePrefix("www.") ?: "" } catch (_: Exception) { "" }
            val candidates = ArrayList<SongResult>()
            data.split("\\n").forEach { line ->
                val parts = line.split("|||", limit = 5)
                if (parts.size < 5) return@forEach
                val song = SongResult(parts[0], parts[1], parts[2], parts[3], parts[4])
                if (song.url.isBlank() || song.url == currentUrl || !ServerConfig.isAllowedMediaUrl(song.url)) return@forEach
                val candidateHost = try { Uri.parse(song.url).host?.lowercase()?.removePrefix("www.") ?: "" } catch (_: Exception) { "" }
                if (host.isNotBlank() && candidateHost != host) return@forEach
                candidates.add(song)
            }
            if (candidates.isEmpty()) return result
            val artistWords = currentArtist.lowercase().split(Regex("[\\\\s,،\\\\-_|]+" )).filter { it.length >= 2 }
            val titleWords = currentTitle.lowercase().split(Regex("[\\\\s,،\\\\-_|]+" )).filter { it.length >= 2 }
            fun score(song: SongResult): Int {
                var score = 0
                val a = song.artist.lowercase()
                val t = song.title.lowercase()
                artistWords.forEach { if (a.contains(it)) score += 10 }
                titleWords.forEach { if (t.contains(it)) score += 6 }
                return score
            }
            candidates.distinctBy { it.url }.sortedByDescending(::score).take(20).forEach { song ->
                result.add(createMediaItem(song.url, song.title, song.artist, song.cover))
            }
        } catch (_: Exception) { }
        return result
    }

'''
s = s[:start] + queue + s[end:]

MAIN.write_text(m)
SERVICE.write_text(s)
print('Player fixes applied')
