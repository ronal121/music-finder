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
import androidx.media3.session.MediaStyleNotificationHelper

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

        private const val CHANNEL_ID = "music_playback"
        private const val CHANNEL_NAME = "Music Playback"
        private const val NOTIFICATION_ID = 1001
    }

    private lateinit var player: ExoPlayer
    private lateinit var audioManager: AudioManager
    private var mediaSession: MediaSession? = null

    private val updateHandler = Handler(Looper.getMainLooper())
    private val volumeAndProgressTicker = object : Runnable {
        override fun run() {
            if (released) return
            safeSendUpdate()
            updateForegroundNotification()
            updateHandler.postDelayed(this, 500L)
        }
    }

    private var lastReportedVolume = -1
    @Volatile private var released = false

    override fun onCreate() {
        super.onCreate()
        released = false

        createNotificationChannel()
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

        // ExoPlayer stays at unity gain. The real music volume is the Android
        // STREAM_MUSIC volume, so the hardware volume keys and the in-app
        // volume slider always operate on the exact same volume stream.
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
                updateForegroundNotification()
            }

            override fun onPlayerError(error: PlaybackException) {
                try { player.pause() } catch (_: Exception) { }
                safeSendUpdate()
                updateForegroundNotification()
            }
        })

        updateHandler.post(volumeAndProgressTicker)
        startMusicForeground()
        updateForegroundNotification()
        safeSendUpdate()
    }

    private fun createNotificationChannel() {
        if (Build.VERSION.SDK_INT >= Build.VERSION_CODES.O) {
            val channel = NotificationChannel(
                CHANNEL_ID,
                CHANNEL_NAME,
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
        if (released || mediaSession == null) return
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
        val session = mediaSession
        val duration = try {
            player.duration.takeIf { it != C.TIME_UNSET && it > 0L } ?: 0L
        } catch (_: Exception) {
            0L
        }
        val position = try {
            player.currentPosition.coerceAtLeast(0L)
        } catch (_: Exception) {
            0L
        }

        val builder = NotificationCompat.Builder(this, CHANNEL_ID)
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
            .addAction(
                android.R.drawable.ic_media_previous,
                "Previous",
                actionPendingIntent(ACTION_PREVIOUS, 301)
            )
            .addAction(
                android.R.drawable.ic_media_rew,
                "-10s",
                actionPendingIntent(ACTION_REWIND_10, 302)
            )
            .addAction(
                if (::player.isInitialized && player.isPlaying) {
                    android.R.drawable.ic_media_pause
                } else {
                    android.R.drawable.ic_media_play
                },
                if (::player.isInitialized && player.isPlaying) "Pause" else "Play",
                actionPendingIntent(ACTION_TOGGLE, 303)
            )
            .addAction(
                android.R.drawable.ic_media_ff,
                "+10s",
                actionPendingIntent(ACTION_FORWARD_10, 304)
            )
            .addAction(
                android.R.drawable.ic_media_next,
                "Next",
                actionPendingIntent(ACTION_NEXT, 305)
            )
            .addAction(
                android.R.drawable.ic_menu_close_clear_cancel,
                "Stop",
                actionPendingIntent(ACTION_STOP, 306)
            )

        if (duration > 0L) {
            builder.setProgress(
                1000,
                ((position.toDouble() / duration.toDouble()) * 1000.0)
                    .toInt()
                    .coerceIn(0, 1000),
                false
            )
        } else {
            builder.setProgress(0, 0, true)
        }

        if (session != null) {
            builder.setStyle(
                MediaStyleNotificationHelper.MediaStyle(session)
                    .setShowActionsInCompactView(intArrayOf(0, 2, 4))
            )
        }

        return builder.build()
    }

    private fun actionPendingIntent(action: String, requestCode: Int): PendingIntent {
        val intent = Intent(this, MusicService::class.java).apply {
            this.action = action
        }
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
            player.seekTo(
                if (duration > 0L && duration != C.TIME_UNSET) {
                    target.coerceIn(0L, duration)
                } else {
                    target.coerceAtLeast(0L)
                }
            )
            safeSendUpdate()
            updateForegroundNotification()
        } catch (_: Exception) { }
    }

    private fun getCurrentTitle(): String = try {
        player.currentMediaItem
            ?.mediaMetadata
            ?.title
            ?.toString()
            ?.ifBlank { "Music Finder" }
            ?: "Music Finder"
    } catch (_: Exception) {
        "Music Finder"
    }

    private fun getCurrentArtist(): String = try {
        player.currentMediaItem
            ?.mediaMetadata
            ?.artist
            ?.toString()
            ?.ifBlank { "Music Finder" }
            ?: "Music Finder"
    } catch (_: Exception) {
        "Music Finder"
    }

    private fun updateForegroundNotification() {
        if (released || mediaSession == null || !::player.isInitialized) return
        try {
            getSystemService(NotificationManager::class.java)
                ?.notify(NOTIFICATION_ID, buildForegroundNotification())
        } catch (_: Exception) { }
    }

    override fun onUpdateNotification(
        session: MediaSession,
        startInForegroundRequired: Boolean
    ) {
        if (released) return
        try {
            val notification = buildForegroundNotification()
            if (startInForegroundRequired) {
                if (Build.VERSION.SDK_INT >= Build.VERSION_CODES.Q) {
                    startForeground(
                        NOTIFICATION_ID,
                        notification,
                        ServiceInfo.FOREGROUND_SERVICE_TYPE_MEDIA_PLAYBACK
                    )
                } else {
                    startForeground(NOTIFICATION_ID, notification)
                }
            } else {
                getSystemService(NotificationManager::class.java)
                    ?.notify(NOTIFICATION_ID, notification)
            }
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
                ACTION_SEEK_PERCENT -> seekPercent(intent.getIntExtra(EXTRA_PERCENT, 0))
                ACTION_SET_VOLUME -> setSystemVolume(
                    intent.getIntExtra(EXTRA_VOLUME, currentSystemVolumePercent())
                        .coerceIn(0, 100)
                )
                ACTION_GET_POSITION -> safeSendUpdate()
                ACTION_REWIND_10 -> seekRelative(-10_000L)
                ACTION_FORWARD_10 -> seekRelative(10_000L)

                ACTION_STOP -> stopPlaybackAndService()
            }
        } catch (_: Exception) {
            safeSendUpdate()
        }

        return START_STICKY
    }

    private fun stopPlaybackAndService() {
        try {
            player.pause()
            player.stop()
            player.clearMediaItems()
        } catch (_: Exception) { }

        safeSendUpdate()

        try {
            stopForeground(STOP_FOREGROUND_REMOVE)
        } catch (_: Exception) { }

        try {
            getSystemService(NotificationManager::class.java)
                ?.cancel(NOTIFICATION_ID)
        } catch (_: Exception) { }

        stopSelf()
    }

    private fun currentSystemVolumePercent(): Int = try {
        val max = audioManager.getStreamMaxVolume(AudioManager.STREAM_MUSIC)
        val current = audioManager.getStreamVolume(AudioManager.STREAM_MUSIC)
        if (max <= 0) {
            0
        } else {
            ((current * 100f) / max)
                .roundToIntSafe()
                .coerceIn(0, 100)
        }
    } catch (_: Exception) {
        0
    }

    private fun setSystemVolume(percent: Int) {
        try {
            val max = audioManager.getStreamMaxVolume(AudioManager.STREAM_MUSIC)
            if (max <= 0) return

            val target =
                ((percent.coerceIn(0, 100) / 100f) * max)
                    .roundToIntSafe()
                    .coerceIn(0, max)

            audioManager.setStreamVolume(
                AudioManager.STREAM_MUSIC,
                target,
                0
            )

            // Never multiply the system volume by ExoPlayer volume. Keeping
            // ExoPlayer at 1f makes hardware keys and the app slider identical.
            player.volume = 1f

            val actual = currentSystemVolumePercent()
            getSharedPreferences("player_settings", MODE_PRIVATE)
                .edit()
                .putInt("volume_percent", actual)
                .apply()

            lastReportedVolume = actual
            safeSendUpdate()
            updateForegroundNotification()
        } catch (_: Exception) { }
    }

    private fun playUrl(intent: Intent) {
        val url = intent.getStringExtra(EXTRA_URL)?.trim()
        if (url.isNullOrBlank() || !ServerConfig.isAllowedMediaUrl(url)) {
            safeSendUpdate()
            return
        }

        val title = intent.getStringExtra(EXTRA_TITLE)
            ?.trim()
            ?.ifBlank { "Music Finder" }
            ?: "Music Finder"

        val artist = intent.getStringExtra(EXTRA_ARTIST)
            ?.trim()
            ?.ifBlank { "Music Finder" }
            ?: "Music Finder"

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
            if (player.hasNextMediaItem()) {
                player.seekToNextMediaItem()
                player.play()
            } else if (player.currentMediaItem != null) {
                // There is no valid related item: do not invent an unrelated
                // URL. Keep the current item stopped at its end.
                player.pause()
            }
            safeSendUpdate()
            updateForegroundNotification()
        } catch (_: Exception) {
            safeSendUpdate()
        }
    }

    private fun previous() {
        try {
            if (player.hasPreviousMediaItem()) {
                player.seekToPreviousMediaItem()
            } else {
                player.seekTo(0, 0L)
            }
            player.play()
            safeSendUpdate()
            updateForegroundNotification()
        } catch (_: Exception) {
            safeSendUpdate()
        }
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
            val data = getSharedPreferences("search_results", MODE_PRIVATE)
                .getString("songs", "")
                ?: ""

            if (data.isBlank()) return result

            val host = try {
                Uri.parse(currentUrl)
                    .host
                    ?.lowercase()
                    ?.removePrefix("www.")
                    ?: ""
            } catch (_: Exception) {
                ""
            }

            val candidates = ArrayList<SongResult>()

            data.split("\n").forEach { line ->
                val parts = line.split("|||", limit = 5)
                if (parts.size < 5) return@forEach

                val song = SongResult(
                    parts[0].trim(),
                    parts[1].trim(),
                    parts[2].trim(),
                    parts[3].trim(),
                    parts[4].trim()
                )

                if (
                    song.url.isBlank() ||
                    song.url == currentUrl ||
                    !ServerConfig.isAllowedMediaUrl(song.url)
                ) return@forEach

                val candidateHost = try {
                    Uri.parse(song.url)
                        .host
                        ?.lowercase()
                        ?.removePrefix("www.")
                        ?: ""
                } catch (_: Exception) {
                    ""
                }

                // The automatic next-song queue is deliberately restricted
                // to the same source host as the selected track.
                if (host.isNotBlank() && candidateHost != host) return@forEach

                candidates.add(song)
            }

            if (candidates.isEmpty()) return result

            val artistWords = currentArtist
                .lowercase()
                .split(Regex("[\\s,،\\-_|]+"))
                .filter { it.length >= 2 }

            val titleWords = currentTitle
                .lowercase()
                .split(Regex("[\\s,،\\-_|]+"))
                .filter { it.length >= 2 }

            fun score(song: SongResult): Int {
                var score = 0
                val artist = song.artist.lowercase()
                val title = song.title.lowercase()

                artistWords.forEach { word ->
                    if (artist.contains(word)) score += 20
                    if (title.contains(word)) score += 5
                }

                titleWords.forEach { word ->
                    if (title.contains(word)) score += 12
                    if (artist.contains(word)) score += 4
                }

                return score
            }

            candidates
                .distinctBy { it.url }
                .sortedWith(
                    compareByDescending<SongResult> { score(it) }
                        .thenBy { it.title.lowercase() }
                )
                .take(20)
                .forEach { song ->
                    result.add(
                        createMediaItem(
                            song.url,
                            song.title,
                            song.artist,
                            song.cover
                        )
                    )
                }
        } catch (_: Exception) { }

        return result
    }

    private fun createMediaItem(
        url: String,
        title: String,
        artist: String,
        cover: String
    ): MediaItem {
        val metadata = MediaMetadata.Builder()
            .setTitle(title.ifBlank { "Music Finder" })
            .setArtist(artist.ifBlank { "Music Finder" })
            .apply {
                if (cover.isNotBlank()) {
                    try {
                        setArtworkUri(Uri.parse(cover))
                    } catch (_: Exception) { }
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

            val lines = old
                .split("\n")
                .filter { it.isNotBlank() && !it.startsWith("$url|||") }
                .toMutableList()

            lines.add(0, line)
            while (lines.size > 50) lines.removeAt(lines.lastIndex)

            prefs.edit()
                .putString("items", lines.joinToString("\n"))
                .apply()
        } catch (_: Exception) { }
    }

    private fun seekPercent(percent: Int) {
        try {
            val duration = player.duration
            if (duration <= 0L || duration == C.TIME_UNSET) return

            player.seekTo(
                duration * percent.coerceIn(0, 100) / 100L
            )
            safeSendUpdate()
            updateForegroundNotification()
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
                putExtra(
                    "mediaUrl",
                    item?.mediaId ?: item?.localConfiguration?.uri?.toString() ?: ""
                )
                putExtra("volume", volume)
                putExtra("hasNext", player.hasNextMediaItem())
                putExtra("hasPrevious", player.hasPreviousMediaItem())
            }

            if (volume != lastReportedVolume) {
                lastReportedVolume = volume
                getSharedPreferences("player_settings", MODE_PRIVATE)
                    .edit()
                    .putInt("volume_percent", volume)
                    .apply()
            }

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
        // Removing the app task must not kill playback. The foreground media
        // service owns playback and remains alive until Stop is pressed.
        try {
            if (::player.isInitialized && player.currentMediaItem != null && !player.isPlaying) {
                // Do not force playback if the user deliberately paused it.
            }
        } catch (_: Exception) { }
        super.onTaskRemoved(rootIntent)
    }

    override fun onDestroy() {
        released = true
        updateHandler.removeCallbacksAndMessages(null)

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
