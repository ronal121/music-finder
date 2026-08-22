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
                    .setShowActionsInCompactView(*intArrayOf(0, 2, 4))
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
        player.currentMediaItem?.mediaMetadata?.title?.toString()?.takeIf { it.isNotBlank() }
            ?: "Music Finder"
    } catch (_: Exception) { "Music Finder" }

    private fun getCurrentArtist(): String = try {
        player.currentMediaItem?.mediaMetadata?.artist?.toString()?.takeIf { it.isNotBlank() }
            ?: "Kafshar"
    } catch (_: Exception) { "Kafshar" }

    private fun createOpenAppPendingIntent(): PendingIntent {
        val intent = Intent(this, MainActivity::class.java).apply {
            flags = Intent.FLAG_ACTIVITY_SINGLE_TOP or Intent.FLAG_ACTIVITY_CLEAR_TOP
        }
        return PendingIntent.getActivity(
            this,
            100,
            intent,
            PendingIntent.FLAG_UPDATE_CURRENT or PendingIntent.FLAG_IMMUTABLE
        )
    }

    private fun safeSendUpdate() {
        try {
            if (!::player.isInitialized || released) return
            val duration = player.duration.takeIf { it != C.TIME_UNSET && it > 0L } ?: 0L
            val position = player.currentPosition.coerceAtLeast(0L)
            val volume = try {
                audioManager.getStreamVolume(AudioManager.STREAM_MUSIC)
            } catch (_: Exception) { -1 }

            val intent = Intent(UPDATE).apply {
                setPackage(packageName)
                putExtra("position", position)
                putExtra("duration", duration)
                putExtra("isPlaying", player.isPlaying)
                putExtra(EXTRA_VOLUME, volume)
                putExtra(EXTRA_TITLE, getCurrentTitle())
                putExtra(EXTRA_ARTIST, getCurrentArtist())
            }
            sendBroadcast(intent)
            lastReportedVolume = volume
        } catch (_: Exception) { }
    }

    private fun updateForegroundNotification() {
        if (released || !::player.isInitialized) return
        try {
            getSystemService(NotificationManager::class.java)
                ?.notify(NOTIFICATION_ID, buildForegroundNotification())
        } catch (_: Exception) { }
    }

    override fun onStartCommand(intent: Intent?, flags: Int, startId: Int): Int {
        when (intent?.action) {
            ACTION_PLAY -> player.play()
            ACTION_PAUSE -> player.pause()
            ACTION_TOGGLE -> if (player.isPlaying) player.pause() else player.play()
            ACTION_STOP -> {
                player.stop()
                stopForeground(STOP_FOREGROUND_REMOVE)
                stopSelf()
            }
            ACTION_NEXT -> player.seekToNextMediaItem()
            ACTION_PREVIOUS -> player.seekToPreviousMediaItem()
            ACTION_REWIND_10 -> seekRelative(-10_000L)
            ACTION_FORWARD_10 -> seekRelative(10_000L)
            ACTION_SEEK_PERCENT -> {
                val percent = intent.getFloatExtra(EXTRA_PERCENT, 0f).coerceIn(0f, 100f)
                val duration = player.duration
                if (duration > 0L && duration != C.TIME_UNSET) {
                    player.seekTo((duration * percent / 100f).toLong())
                }
            }
            ACTION_SET_VOLUME -> {
                val volume = intent.getIntExtra(EXTRA_VOLUME, -1)
                if (volume >= 0) {
                    val max = audioManager.getStreamMaxVolume(AudioManager.STREAM_MUSIC)
                    audioManager.setStreamVolume(
                        AudioManager.STREAM_MUSIC,
                        volume.coerceIn(0, max),
                        0
                    )
                }
            }
        }
        safeSendUpdate()
        updateForegroundNotification()
        return START_STICKY
    }

    private fun saveToHistory(mediaItem: MediaItem) {
        // History persistence is intentionally handled by the existing app layer.
    }

    override fun onTaskRemoved(rootIntent: Intent?) {
        // Keep playback alive when the app task is swiped away.
        super.onTaskRemoved(rootIntent)
    }

    override fun onDestroy() {
        released = true
        updateHandler.removeCallbacksAndMessages(null)
        try { mediaSession?.release() } catch (_: Exception) { }
        mediaSession = null
        try { player.release() } catch (_: Exception) { }
        super.onDestroy()
    }

    override fun onGetSession(controllerInfo: MediaSession.ControllerInfo): MediaSession? {
        return mediaSession
    }
}
