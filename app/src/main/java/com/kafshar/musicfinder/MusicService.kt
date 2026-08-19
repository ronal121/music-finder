package com.kafshar.musicfinder

import android.app.Notification
import android.app.NotificationChannel
import android.app.NotificationManager
import android.app.PendingIntent
import android.content.Intent
import android.os.Build
import androidx.core.app.NotificationCompat
import androidx.media3.common.AudioAttributes
import androidx.media3.common.C
import androidx.media3.common.MediaItem
import androidx.media3.common.Player
import androidx.media3.exoplayer.ExoPlayer
import androidx.media3.session.MediaSession
import androidx.media3.session.MediaSessionService

class MusicService : MediaSessionService() {

    companion object {
        const val ACTION_PLAY = "com.kafshar.musicfinder.PLAY"
        const val ACTION_PAUSE = "com.kafshar.musicfinder.PAUSE"
        const val ACTION_STOP = "com.kafshar.musicfinder.STOP"

        const val EXTRA_URL = "url"
        const val EXTRA_TITLE = "title"

        private const val CHANNEL_ID = "music_finder_playback"
        private const val NOTIFICATION_ID = 1001
    }

    private lateinit var player: ExoPlayer
    private var mediaSession: MediaSession? = null

    override fun onCreate() {
        super.onCreate()

        createNotificationChannel()

        player = ExoPlayer.Builder(this)
            .setAudioAttributes(
                AudioAttributes.Builder()
                    .setContentType(C.AUDIO_CONTENT_TYPE_MUSIC)
                    .setUsage(C.USAGE_MEDIA)
                    .build(),
                true
            )
            .build()

        player.addListener(
            object : Player.Listener {

                override fun onIsPlayingChanged(isPlaying: Boolean) {
                    updateNotification()
                }

                override fun onMediaItemTransition(
                    mediaItem: MediaItem?,
                    reason: Int
                ) {
                    updateNotification()
                }
            }
        )

        mediaSession = MediaSession.Builder(this, player)
            .setCallback(object : MediaSession.Callback {})
            .build()
    }

    override fun onStartCommand(
        intent: Intent?,
        flags: Int,
        startId: Int
    ): Int {

        when (intent?.action) {

            ACTION_PLAY -> {

                val url = intent.getStringExtra(EXTRA_URL)
                val title =
                    intent.getStringExtra(EXTRA_TITLE)
                        ?: "Music Finder"

                if (!url.isNullOrBlank()) {

                    val mediaItem =
                        MediaItem.Builder()
                            .setUri(url)
                            .setMediaId(url)
                            .setTag(title)
                            .build()

                    player.setMediaItem(mediaItem)
                    player.prepare()
                    player.play()

                    startForeground(
                        NOTIFICATION_ID,
                        createNotification()
                    )
                }
            }

            ACTION_PAUSE -> {
                player.pause()
            }

            ACTION_STOP -> {
                player.stop()
                stopForeground(STOP_FOREGROUND_REMOVE)
                stopSelf()
            }
        }

        updateNotification()

        return START_STICKY
    }

    private fun updateNotification() {

        if (!player.isPlaying && player.playbackState == Player.STATE_IDLE) {
            return
        }

        val notification =
            createNotification()

        if (Build.VERSION.SDK_INT >= 24) {
            getSystemService(NotificationManager::class.java)
                .notify(NOTIFICATION_ID, notification)
        }
    }

    private fun createNotification(): Notification {

        val openIntent =
            Intent(this, MainActivity::class.java)

        val openPendingIntent =
            PendingIntent.getActivity(
                this,
                10,
                openIntent,
                PendingIntent.FLAG_UPDATE_CURRENT or
                        PendingIntent.FLAG_IMMUTABLE
            )

        val title =
            player.currentMediaItem
                ?.mediaMetadata
                ?.title
                ?.toString()
                ?: "Music Finder"

        val playIntent =
            Intent(
                this,
                MusicService::class.java
            ).apply {

                action =
                    if (player.isPlaying)
                        ACTION_PAUSE
                    else
                        ACTION_PLAY

                putExtra(
                    EXTRA_URL,
                    player.currentMediaItem?.localConfiguration?.uri?.toString()
                )

                putExtra(
                    EXTRA_TITLE,
                    title
                )
            }

        val playPendingIntent =
            PendingIntent.getService(
                this,
                11,
                playIntent,
                PendingIntent.FLAG_UPDATE_CURRENT or
                        PendingIntent.FLAG_IMMUTABLE
            )

        val stopIntent =
            Intent(
                this,
                MusicService::class.java
            ).apply {
                action = ACTION_STOP
            }

        val stopPendingIntent =
            PendingIntent.getService(
                this,
                12,
                stopIntent,
                PendingIntent.FLAG_UPDATE_CURRENT or
                        PendingIntent.FLAG_IMMUTABLE
            )

        return NotificationCompat.Builder(
            this,
            CHANNEL_ID
        )
            .setSmallIcon(
                android.R.drawable.ic_media_play
            )
            .setContentTitle(title)
            .setContentText("Music Finder")
            .setContentIntent(openPendingIntent)
            .setOngoing(player.isPlaying)
            .setOnlyAlertOnce(true)
            .setVisibility(
                NotificationCompat.VISIBILITY_PUBLIC
            )
            .addAction(
                if (player.isPlaying)
                    android.R.drawable.ic_media_pause
                else
                    android.R.drawable.ic_media_play,
                if (player.isPlaying)
                    "توقف"
                else
                    "پخش",
                playPendingIntent
            )
            .addAction(
                android.R.drawable.ic_menu_close_clear_cancel,
                "بستن",
                stopPendingIntent
            )
            .build()
    }

    private fun createNotificationChannel() {

        if (Build.VERSION.SDK_INT >= 26) {

            val channel =
                NotificationChannel(
                    CHANNEL_ID,
                    "Music Finder",
                    NotificationManager.IMPORTANCE_LOW
                ).apply {

                    description =
                        "کنترل پخش موسیقی Music Finder"

                    setShowBadge(false)
                }

            getSystemService(
                NotificationManager::class.java
            ).createNotificationChannel(channel)
        }
    }

    override fun onGetSession(
        controllerInfo: MediaSession.ControllerInfo
    ): MediaSession? {
        return mediaSession
    }

    override fun onDestroy() {

        mediaSession?.release()
        mediaSession = null

        player.release()

        super.onDestroy()
    }
}
