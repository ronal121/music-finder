package com.kafshar.musicfinder

import android.app.Notification
import android.app.NotificationChannel
import android.app.NotificationManager
import android.app.PendingIntent
import android.content.Intent
import android.content.pm.ServiceInfo
import android.media.AudioManager
import android.net.Uri
import android.os.Build
import android.os.Handler
import android.os.Looper
import androidx.core.app.NotificationCompat
import androidx.media3.common.AudioAttributes
import androidx.media3.common.C
import androidx.media3.common.MediaItem
import androidx.media3.common.MediaMetadata
import androidx.media3.common.PlaybackException
import androidx.media3.common.Player
import androidx.media3.exoplayer.ExoPlayer
import androidx.media3.session.MediaSession
import androidx.media3.session.MediaSessionService

class MusicService : MediaSessionService() {

    companion object {
        const val ACTION_PLAY = "com.kafshar.musicfinder.PLAY"
        const val ACTION_PAUSE = "com.kafshar.musicfinder.PAUSE"
        const val ACTION_TOGGLE = "com.kafshar.musicfinder.TOGGLE"
        const val ACTION_STOP = "com.kafshar.musicfinder.STOP"
        const val ACTION_NEXT = "com.kafshar.musicfinder.NEXT"
        const val ACTION_PREVIOUS = "com.kafshar.musicfinder.PREVIOUS"
        const val ACTION_SEEK_PERCENT = "com.kafshar.musicfinder.SEEK_PERCENT"
        const val ACTION_GET_POSITION = "com.kafshar.musicfinder.GET_POSITION"
        const val ACTION_SET_VOLUME = "com.kafshar.musicfinder.SET_VOLUME"
        const val ACTION_REWIND_10 = "com.kafshar.musicfinder.REWIND_10"
        const val ACTION_FORWARD_10 = "com.kafshar.musicfinder.FORWARD_10"

        const val EXTRA_URL = "url"
        const val EXTRA_TITLE = "title"
        const val EXTRA_PERCENT = "percent"
        const val EXTRA_ARTIST = "artist"
        const val EXTRA_COVER = "cover"
        const val EXTRA_VOLUME = "volume"

        const val UPDATE = "com.kafshar.musicfinder.PLAYER_UPDATE"

        private const val NOTIFICATION_CHANNEL_ID = "music_playback"
        private const val NOTIFICATION_CHANNEL_NAME = "Music Playback"
        private const val NOTIFICATION_ID = 1001
    }

    private lateinit var player: ExoPlayer
    private lateinit var audioManager: AudioManager
    private var mediaSession: MediaSession? = null

    private val volumeHandler = Handler(Looper.getMainLooper())
    private var lastReportedVolume = -1

    private val volumeMonitor = object : Runnable {
        override fun run() {
            if (released) return
            safeSendUpdate()
            volumeHandler.postDelayed(this, 250L)
        }
    }

    @Volatile
    private var released = false

    override fun onCreate() {
        createNotificationChannel()
        startMusicForeground()
        super.onCreate()
        released = false

        audioManager = getSystemService(AUDIO_SERVICE) as AudioManager

        player = ExoPlayer.Builder(this)
            .setAudioAttributes(
                AudioAttributes.Builder()
                    .setContentType(C.AUDIO_CONTENT_TYPE_MUSIC)
                    .setUsage(C.USAGE_MEDIA)
                    .build(),
                true
            )
            .setHandleAudioBecomingNoisy(true)
            .setPauseAtEndOfMediaItems(false)
            .build()

        // Android's STREAM_MUSIC is now the single source of truth for volume.
        // The hardware volume keys therefore control the same volume shown in-app.
        player.volume = 1f

        mediaSession = MediaSession.Builder(this, player)
            .setSessionActivity(createOpenAppPendingIntent())
            .build()

        player.addListener(object : Player.Listener {
            override fun onIsPlayingChanged(isPlaying: Boolean) {
                safeSendUpdate()
                updateForegroundNotification()
            }

            override fun onPlaybackStateChanged(playbackState: Int) {
                safeSendUpdate()
                updateForegroundNotification()
            }

            override fun onMediaItemTransition(mediaItem: MediaItem?, reason: Int) {
                if (mediaItem != null) saveToHistory(mediaItem)
                safeSendUpdate()
                updateForegroundNotification()
            }

            override fun onPositionDiscontinuity(
                oldPosition: Player.PositionInfo,
                newPosition: Player.PositionInfo,
                reason: Int
            ) {
                safeSendUpdate()
            }

            override fun onPlayerError(error: PlaybackException) {
                try { player.pause() } catch (_: Exception) { }
                safeSendUpdate()
                updateForegroundNotification()
            }
        })

        volumeHandler.post(volumeMonitor)
        updateForegroundNotification()
        safeSendUpdate()
    }

    private fun createNotificationChannel() {
        if (Build.VERSION.SDK_INT >= Build.VERSION_CODES.O) {
            val channel = NotificationChannel(
                NOTIFICATION_CHANNEL_ID,
                NOTIFICATION_CHANNEL_NAME,
                NotificationManager.IMPORTANCE_LOW
            ).apply {
                description = "Music playback controls"
                setShowBadge(false)
                lockscreenVisibility = Notification.VISIBILITY_PUBLIC
            }
            getSystemService(NotificationManager::class.java)
                ?.createNotificationChannel(channel)
        }
    }

    private fun startMusicForeground() {
        val notification = buildForegroundNotification()
        try {
            if (Build.VERSION.SDK_INT >= Build.VERSION_CODES.Q) {
                startForeground(
                    NOTIFICATION_ID,
                    notification,
                    ServiceInfo.FOREGROUND_SERVICE_TYPE_MEDIA_PLAYBACK
                )
            } else {
                startForeground(NOTIFICATION_ID, notification)
            }
        } catch (_: Exception) { }
    }

    private fun buildForegroundNotification(): Notification {
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

    private fun getCurrentTitle(): String {
        return try {
            if (::player.isInitialized && player.currentMediaItem != null) {
                player.currentMediaItem?.mediaMetadata?.title?.toString()
                    ?.ifBlank { "Music Finder" } ?: "Music Finder"
            } else "Music Finder"
        } catch (_: Exception) { "Music Finder" }
    }

    private fun getCurrentArtist(): String {
        return try {
            if (::player.isInitialized && player.currentMediaItem != null) {
                player.currentMediaItem?.mediaMetadata?.artist?.toString()
                    ?.ifBlank { "Music Finder" } ?: "Music Finder"
            } else "Music Finder"
        } catch (_: Exception) { "Music Finder" }
    }

    private fun updateForegroundNotification() {
        if (released) return
        try {
            getSystemService(NotificationManager::class.java)?.notify(
                NOTIFICATION_ID,
                buildForegroundNotification()
            )
        } catch (_: Exception) { }
    }

    override fun onStartCommand(intent: Intent?, flags: Int, startId: Int): Int {
        if (released || !::player.isInitialized) return START_STICKY

        try {
            when (intent?.action) {
                ACTION_PLAY -> playUrl(intent)
                ACTION_PAUSE -> {
                    player.pause()
                    safeSendUpdate()
                    updateForegroundNotification()
                }
                ACTION_TOGGLE -> {
                    if (player.currentMediaItem == null) {
                        val url = intent.getStringExtra(EXTRA_URL)
                        if (!url.isNullOrBlank()) playUrl(intent)
                    } else {
                        if (player.isPlaying) player.pause() else player.play()
                        safeSendUpdate()
                        updateForegroundNotification()
                    }
                }
                ACTION_NEXT -> next()
                ACTION_PREVIOUS -> previous()
                ACTION_SEEK_PERCENT -> seekPercent(
                    intent.getIntExtra(EXTRA_PERCENT, 0)
                )
                ACTION_SET_VOLUME -> {
                    setSystemVolume(
                        intent.getIntExtra(EXTRA_VOLUME, currentSystemVolumePercent())
                            .coerceIn(0, 100)
                    )
                }
                ACTION_GET_POSITION -> safeSendUpdate()
                ACTION_REWIND_10 -> seekRelative(-10_000L)
                ACTION_FORWARD_10 -> seekRelative(10_000L)
                ACTION_STOP -> {
                    try { player.stop() } catch (_: Exception) { }
                    safeSendUpdate()
                    try { stopForeground(STOP_FOREGROUND_REMOVE) } catch (_: Exception) { }
                    stopSelf()
                }
            }
        } catch (_: Exception) {
            safeSendUpdate()
        }

        return START_STICKY
    }

    private fun currentSystemVolumePercent(): Int {
        return try {
            val max = audioManager.getStreamMaxVolume(AudioManager.STREAM_MUSIC)
            val current = audioManager.getStreamVolume(AudioManager.STREAM_MUSIC)
            if (max <= 0) 0 else ((current * 100f) / max).roundToIntSafe().coerceIn(0, 100)
        } catch (_: Exception) { 0 }
    }

    private fun setSystemVolume(percent: Int) {
        try {
            val max = audioManager.getStreamMaxVolume(AudioManager.STREAM_MUSIC)
            val target = ((percent.coerceIn(0, 100) / 100f) * max).roundToIntSafe()
            audioManager.setStreamVolume(AudioManager.STREAM_MUSIC, target.coerceIn(0, max), 0)
            player.volume = 1f
            getSharedPreferences("player_settings", MODE_PRIVATE)
                .edit()
                .putInt("volume_percent", currentSystemVolumePercent())
                .apply()
            safeSendUpdate()
        } catch (_: Exception) { }
    }

    private fun playUrl(intent: Intent) {
        val url = intent.getStringExtra(EXTRA_URL)?.trim()
        if (url.isNullOrBlank() || !ServerConfig.isAllowedMediaUrl(url)) {
            safeSendUpdate()
            return
        }

        val title = intent.getStringExtra(EXTRA_TITLE)?.trim()?.ifBlank { "Music Finder" } ?: "Music Finder"
        val artist = intent.getStringExtra(EXTRA_ARTIST)?.trim()?.ifBlank { "Music Finder" } ?: "Music Finder"
        val cover = intent.getStringExtra(EXTRA_COVER)?.trim() ?: ""

        val current = createMediaItem(url, title, artist, cover)
        val queue = buildRelatedQueue(url, title, artist, cover)

        try {
            player.stop()
            player.clearMediaItems()
            if (queue.size > 1) {
                player.setMediaItems(queue, 0, 0L)
            } else {
                player.setMediaItem(current)
            }
            player.prepare()
            player.play()
            saveToHistory(current)
            safeSendUpdate()
            updateForegroundNotification()
        } catch (_: Exception) {
            try {
                player.stop()
                player.clearMediaItems()
            } catch (_: Exception) { }
            safeSendUpdate()
            updateForegroundNotification()
        }
    }

    private fun next() {
        try {
            if (player.hasNextMediaItem()) player.seekToNextMediaItem()
            else if (player.currentMediaItem != null) player.seekTo(0, 0L)
            player.prepare()
            player.play()
            safeSendUpdate()
            updateForegroundNotification()
        } catch (_: Exception) { safeSendUpdate() }
    }

    private fun previous() {
        try {
            if (player.hasPreviousMediaItem()) player.seekToPreviousMediaItem()
            else player.seekTo(0, 0L)
            player.prepare()
            player.play()
            safeSendUpdate()
            updateForegroundNotification()
        } catch (_: Exception) { safeSendUpdate() }
    }

    private fun buildRelatedQueue(
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
            data.split("\n").forEach { line ->
                val parts = line.split("|||", limit = 5)
                if (parts.size < 5) return@forEach
                val song = SongResult(parts[0], parts[1], parts[2], parts[3], parts[4])
                if (song.url.isBlank() || song.url == currentUrl || !ServerConfig.isAllowedMediaUrl(song.url)) return@forEach
                val candidateHost = try { Uri.parse(song.url).host?.lowercase()?.removePrefix("www.") ?: "" } catch (_: Exception) { "" }
                if (host.isNotBlank() && candidateHost != host) return@forEach
                candidates.add(song)
            }
            if (candidates.isEmpty()) return result
            val artistWords = currentArtist.lowercase().split(Regex("[\\s,،\\-_|]+" )).filter { it.length >= 2 }
            val titleWords = currentTitle.lowercase().split(Regex("[\\s,،\\-_|]+" )).filter { it.length >= 2 }
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

    private fun createMediaItem(url: String, title: String, artist: String, cover: String): MediaItem {
        val metadata = MediaMetadata.Builder()
            .setTitle(title.ifBlank { "Music Finder" })
            .setArtist(artist.ifBlank { "Music Finder" })
            .apply {
                if (cover.isNotBlank()) {
                    try { setArtworkUri(Uri.parse(cover)) } catch (_: Exception) { }
                }
            }
            .build()
        return MediaItem.Builder()
            .setUri(url)
            .setMediaId(url)
            .setMediaMetadata(metadata)
            .build()
    }

    private fun saveToHistory(item: MediaItem) {
        try {
            val url = item.mediaId
            if (url.isBlank()) return
            val prefs = getSharedPreferences("music_history", MODE_PRIVATE)
            val old = prefs.getString("items", "") ?: ""
            val title = item.mediaMetadata.title?.toString() ?: ""
            val artist = item.mediaMetadata.artist?.toString() ?: ""
            val cover = item.mediaMetadata.artworkUri?.toString() ?: ""
            val line = listOf(url, title, artist, cover).joinToString("|||")
            val lines = old.split("\n").filter {
                it.isNotBlank() && !it.startsWith("$url|||")
            }.toMutableList()
            lines.add(0, line)
            while (lines.size > 50) lines.removeAt(lines.lastIndex)
            prefs.edit().putString("items", lines.joinToString("\n")).apply()
        } catch (_: Exception) { }
    }

    private fun seekPercent(percent: Int) {
        try {
            val duration = player.duration
            if (duration <= 0 || duration == C.TIME_UNSET) return
            val safe = percent.coerceIn(0, 100)
            player.seekTo(duration * safe / 100L)
            safeSendUpdate()
        } catch (_: Exception) { }
    }

    private fun safeSendUpdate() {
        if (released || !::player.isInitialized) return
        try {
            val item = player.currentMediaItem
            val duration = if (player.duration == C.TIME_UNSET) 0L else player.duration
            val volume = currentSystemVolumePercent()
            val intent = Intent(UPDATE).apply {
                setPackage(packageName)
                putExtra("playing", player.isPlaying)
                putExtra("position", player.currentPosition)
                putExtra("duration", duration)
                putExtra("title", item?.mediaMetadata?.title?.toString() ?: "")
                putExtra("artist", item?.mediaMetadata?.artist?.toString() ?: "")
                putExtra("mediaUrl", item?.mediaId ?: item?.localConfiguration?.uri?.toString() ?: "")
                putExtra("volume", volume)
            }
            if (volume != lastReportedVolume) lastReportedVolume = volume
            sendBroadcast(intent)
        } catch (_: Exception) { }
    }

    private fun createOpenAppPendingIntent(): PendingIntent {
        val intent = Intent(this, MainActivity::class.java).apply {
            flags = Intent.FLAG_ACTIVITY_SINGLE_TOP or Intent.FLAG_ACTIVITY_CLEAR_TOP
        }
        return PendingIntent.getActivity(
            this,
            200,
            intent,
            PendingIntent.FLAG_UPDATE_CURRENT or PendingIntent.FLAG_IMMUTABLE
        )
    }

    override fun onGetSession(controllerInfo: MediaSession.ControllerInfo): MediaSession? = mediaSession

    override fun onTaskRemoved(rootIntent: Intent?) {
        try {
            if (::player.isInitialized && player.playbackState != Player.STATE_IDLE) player.play()
        } catch (_: Exception) { }
        super.onTaskRemoved(rootIntent)
    }

    override fun onDestroy() {
        released = true
        volumeHandler.removeCallbacksAndMessages(null)
        try { mediaSession?.release() } catch (_: Exception) { }
        mediaSession = null
        try {
            if (::player.isInitialized) {
                player.stop()
                player.clearMediaItems()
                player.release()
            }
        } catch (_: Exception) { }
        try { stopForeground(STOP_FOREGROUND_REMOVE) } catch (_: Exception) { }
        super.onDestroy()
    }

    private fun Float.roundToIntSafe(): Int = kotlin.math.round(this).toInt()
}