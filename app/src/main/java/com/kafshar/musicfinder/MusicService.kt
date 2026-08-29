package com.kafshar.musicfinder

import android.app.Notification
import android.app.NotificationChannel
import android.app.NotificationManager
import android.app.PendingIntent
import android.content.Intent
import android.content.pm.ServiceInfo
import android.media.AudioManager
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
import androidx.media3.common.util.UnstableApi
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
        const val ACTION_MUTE = "com.kafshar.musicfinder.MUTE"
        const val ACTION_UNMUTE = "com.kafshar.musicfinder.UNMUTE"
        const val ACTION_REWIND_10 = "com.kafshar.musicfinder.REWIND_10"
        const val ACTION_FORWARD_10 = "com.kafshar.musicfinder.FORWARD_10"
        const val EXTRA_URL = "url"
        const val EXTRA_TITLE = "title"
        const val EXTRA_PERCENT = "percent"
        const val EXTRA_ARTIST = "artist"
        const val EXTRA_COVER = "cover"
        const val EXTRA_VOLUME = "volume"
        const val EXTRA_MUTED = "muted"
        const val EXTRA_ENDED = "ended"
        const val UPDATE = "com.kafshar.musicfinder.PLAYER_UPDATE"
        private const val CHANNEL_ID = "music_playback"
        private const val NOTIFICATION_ID = 1001
    }

    private lateinit var player: ExoPlayer
    private lateinit var audioManager: AudioManager
    private var mediaSession: MediaSession? = null
    private val handler = Handler(Looper.getMainLooper())
    private var released = false
    private var muted = false
    private var previousVolume = 80
    private var retryingUri = ""
    private var retryCount = 0
    private var foregroundStarted = false
    private var lastTitle = "Music Finder"
    private var lastArtist = "KAFSHAR"
    private var lastCover = ""

    private val ticker = object : Runnable {
        override fun run() {
            if (released) return
            publish()
            updateNotification()
            handler.postDelayed(this, 750L)
        }
    }

    @UnstableApi
    override fun onCreate() {
        super.onCreate()
        released = false
        createNotificationChannel()
        startPlaybackForeground()
        audioManager = getSystemService(AUDIO_SERVICE) as AudioManager
        previousVolume = currentVolumePercent().coerceIn(1, 100)
        player = ExoPlayer.Builder(this)
            .setAudioAttributes(
                AudioAttributes.Builder()
                    .setContentType(C.AUDIO_CONTENT_TYPE_MUSIC)
                    .setUsage(C.USAGE_MEDIA)
                    .build(), true
            )
            .setHandleAudioBecomingNoisy(true)
            .setPauseAtEndOfMediaItems(false)
            .build()
        player.volume = 1f
        mediaSession = MediaSession.Builder(this, player)
            .setSessionActivity(openAppPendingIntent())
            .build()
        player.addListener(object : Player.Listener {
            override fun onIsPlayingChanged(isPlaying: Boolean) {
                publish()
                updateNotification()
            }
            override fun onPlaybackStateChanged(playbackState: Int) {
                publish(ended = playbackState == Player.STATE_ENDED)
                updateNotification()
            }
            override fun onMediaItemTransition(mediaItem: MediaItem?, reason: Int) {
                publish()
                updateNotification()
            }
            override fun onPositionDiscontinuity(oldPosition: Player.PositionInfo, newPosition: Player.PositionInfo, reason: Int) {
                publish()
            }
            override fun onPlayerError(error: PlaybackException) {
                val uri = player.currentMediaItem?.localConfiguration?.uri?.toString().orEmpty()
                if (uri.isNotBlank() && uri == retryingUri && retryCount < 1) {
                    retryCount = 1
                    handler.postDelayed({
                        if (!released) loadAndPlay(uri, lastTitle, lastArtist, lastCover, resetRetry = false)
                    }, 700L)
                } else if (uri.isNotBlank()) {
                    player.pause()
                }
                publish(error.message ?: "Playback error")
                updateNotification()
            }
        })
        handler.post(ticker)
        publish()
        updateNotification()
    }

    private fun loadAndPlay(url: String, title: String, artist: String, cover: String, resetRetry: Boolean = true) {
        if (released || url.isBlank()) return
        if (!ServerConfig.isAllowedMediaUrl(url)) {
            publish("Unsupported media source")
            return
        }
        if (resetRetry) retryCount = 0
        retryingUri = url
        lastTitle = title.ifBlank { "Music Finder" }
        lastArtist = artist.ifBlank { "KAFSHAR" }
        lastCover = cover
        val metadata = MediaMetadata.Builder()
            .setTitle(lastTitle)
            .setArtist(lastArtist)
            .apply { if (cover.isNotBlank()) setArtworkUri(android.net.Uri.parse(cover)) }
            .build()
        val item = MediaItem.Builder().setUri(url).setMediaMetadata(metadata).build()
        try {
            player.setMediaItem(item)
            player.prepare()
            player.play()
            publish()
            updateNotification()
        } catch (e: Exception) {
            publish(e.message ?: "Unable to start playback")
        }
    }

    private fun currentVolumePercent(): Int = try {
        val max = audioManager.getStreamMaxVolume(AudioManager.STREAM_MUSIC).coerceAtLeast(1)
        val current = audioManager.getStreamVolume(AudioManager.STREAM_MUSIC)
        (current * 100 / max).coerceIn(0, 100)
    } catch (_: Exception) { previousVolume.coerceIn(0, 100) }

    private fun setVolumePercent(percent: Int) {
        val value = percent.coerceIn(0, 100)
        try {
            val max = audioManager.getStreamMaxVolume(AudioManager.STREAM_MUSIC).coerceAtLeast(1)
            val raw = kotlin.math.round(max * value / 100f).toInt().coerceIn(0, max)
            audioManager.setStreamVolume(AudioManager.STREAM_MUSIC, raw, 0)
            if (value > 0) previousVolume = value
            muted = value == 0
            if (!muted && ::player.isInitialized) player.volume = 1f
        } catch (_: Exception) { }
        publish()
    }

    private fun mute() {
        val current = currentVolumePercent()
        if (current > 0) previousVolume = current
        muted = true
        try { audioManager.setStreamVolume(AudioManager.STREAM_MUSIC, 0, 0) } catch (_: Exception) { }
        publish()
    }

    private fun unmute() {
        muted = false
        setVolumePercent(previousVolume.coerceIn(1, 100))
    }

    private fun publish(error: String? = null, ended: Boolean = false) {
        try {
            if (released || !::player.isInitialized) return
            val intent = Intent(UPDATE).setPackage(packageName).apply {
                val duration = player.duration.takeIf { it != C.TIME_UNSET && it > 0L } ?: 0L
                putExtra("position", player.currentPosition.coerceAtLeast(0L))
                putExtra("duration", duration)
                putExtra("isPlaying", player.isPlaying)
                putExtra("playing", player.isPlaying)
                putExtra(EXTRA_VOLUME, currentVolumePercent())
                putExtra(EXTRA_MUTED, muted)
                putExtra(EXTRA_ENDED, ended)
                putExtra(EXTRA_TITLE, getCurrentTitle())
                putExtra(EXTRA_ARTIST, getCurrentArtist())
                putExtra(EXTRA_URL, player.currentMediaItem?.localConfiguration?.uri?.toString().orEmpty())
                if (error != null) putExtra("error", error)
            }
            sendBroadcast(intent)
        } catch (_: Exception) { }
    }

    private fun createNotificationChannel() {
        if (Build.VERSION.SDK_INT >= Build.VERSION_CODES.O) {
            getSystemService(NotificationManager::class.java)?.createNotificationChannel(
                NotificationChannel(CHANNEL_ID, "Music Playback", NotificationManager.IMPORTANCE_LOW).apply {
                    description = "Music playback controls"
                    setShowBadge(false)
                    lockscreenVisibility = Notification.VISIBILITY_PUBLIC
                }
            )
        }
    }

    @UnstableApi
    private fun startPlaybackForeground() {
        if (foregroundStarted) return
        val notification = buildNotification()
        if (Build.VERSION.SDK_INT >= Build.VERSION_CODES.Q) {
            startForeground(NOTIFICATION_ID, notification, ServiceInfo.FOREGROUND_SERVICE_TYPE_MEDIA_PLAYBACK)
        } else {
            startForeground(NOTIFICATION_ID, notification)
        }
        foregroundStarted = true
    }

    @UnstableApi
    private fun ensureForeground() {
        if (!foregroundStarted && !released) startPlaybackForeground()
    }

    @UnstableApi
    private fun buildNotification(): Notification {
        val session = mediaSession
        val isPlaying = ::player.isInitialized && player.isPlaying
        val builder = NotificationCompat.Builder(this, CHANNEL_ID)
            .setSmallIcon(android.R.drawable.ic_media_play)
            .setContentTitle(getCurrentTitle())
            .setContentText(getCurrentArtist())
            .setContentIntent(openAppPendingIntent())
            .setOngoing(true)
            .setOnlyAlertOnce(true)
            .setShowWhen(false)
            .setVisibility(NotificationCompat.VISIBILITY_PUBLIC)
            .setCategory(NotificationCompat.CATEGORY_TRANSPORT)
            .setPriority(NotificationCompat.PRIORITY_LOW)
            .addAction(android.R.drawable.ic_media_previous, "Previous", actionPendingIntent(ACTION_PREVIOUS, 301))
            .addAction(if (isPlaying) android.R.drawable.ic_media_pause else android.R.drawable.ic_media_play, if (isPlaying) "Pause" else "Play", actionPendingIntent(ACTION_TOGGLE, 303))
            .addAction(android.R.drawable.ic_media_next, "Next", actionPendingIntent(ACTION_NEXT, 305))
            .addAction(android.R.drawable.ic_menu_close_clear_cancel, "Stop", actionPendingIntent(ACTION_STOP, 306))
        if (session != null) {
            builder.setStyle(MediaStyleNotificationHelper.MediaStyle(session).setShowActionsInCompactView(0, 1, 2))
        }
        return builder.build()
    }

    @UnstableApi
    private fun updateNotification() {
        if (released) return
        try {
            ensureForeground()
            getSystemService(NotificationManager::class.java)?.notify(NOTIFICATION_ID, buildNotification())
        } catch (_: Exception) { }
    }

    private fun actionPendingIntent(action: String, requestCode: Int): PendingIntent = PendingIntent.getService(
        this, requestCode, Intent(this, MusicService::class.java).setAction(action),
        PendingIntent.FLAG_UPDATE_CURRENT or PendingIntent.FLAG_IMMUTABLE
    )

    private fun openAppPendingIntent(): PendingIntent = PendingIntent.getActivity(
        this, 100, Intent(this, MainActivity::class.java).apply {
            flags = Intent.FLAG_ACTIVITY_SINGLE_TOP or Intent.FLAG_ACTIVITY_CLEAR_TOP
        }, PendingIntent.FLAG_UPDATE_CURRENT or PendingIntent.FLAG_IMMUTABLE
    )

    private fun seekRelative(delta: Long) {
        try {
            val duration = player.duration
            val target = player.currentPosition + delta
            player.seekTo(if (duration > 0 && duration != C.TIME_UNSET) target.coerceIn(0, duration) else target.coerceAtLeast(0))
            publish()
        } catch (_: Exception) { }
    }

    private fun getCurrentTitle(): String = try {
        player.currentMediaItem?.mediaMetadata?.title?.toString()?.takeIf { it.isNotBlank() } ?: lastTitle
    } catch (_: Exception) { lastTitle }

    private fun getCurrentArtist(): String = try {
        player.currentMediaItem?.mediaMetadata?.artist?.toString()?.takeIf { it.isNotBlank() } ?: lastArtist
    } catch (_: Exception) { lastArtist }

    @UnstableApi
    override fun onStartCommand(intent: Intent?, flags: Int, startId: Int): Int {
        super.onStartCommand(intent, flags, startId)
        ensureForeground()
        try {
            when (intent?.action) {
                ACTION_PLAY -> {
                    val url = intent.getStringExtra(EXTRA_URL).orEmpty()
                    if (url.isNotBlank()) loadAndPlay(url, intent.getStringExtra(EXTRA_TITLE).orEmpty(), intent.getStringExtra(EXTRA_ARTIST).orEmpty(), intent.getStringExtra(EXTRA_COVER).orEmpty())
                    else if (::player.isInitialized) player.play()
                }
                ACTION_PAUSE -> if (::player.isInitialized) player.pause()
                ACTION_TOGGLE -> {
                    if (!::player.isInitialized) return START_STICKY
                    val url = intent.getStringExtra(EXTRA_URL).orEmpty()
                    val currentUrl = player.currentMediaItem?.localConfiguration?.uri?.toString().orEmpty()
                    if (url.isNotBlank() && currentUrl != url) loadAndPlay(url, intent.getStringExtra(EXTRA_TITLE).orEmpty(), intent.getStringExtra(EXTRA_ARTIST).orEmpty(), intent.getStringExtra(EXTRA_COVER).orEmpty())
                    else if (player.isPlaying) player.pause() else player.play()
                }
                ACTION_STOP -> {
                    if (::player.isInitialized) player.stop()
                    stopForeground(STOP_FOREGROUND_REMOVE)
                    foregroundStarted = false
                    stopSelf()
                    return START_NOT_STICKY
                }
                ACTION_NEXT -> if (::player.isInitialized && player.hasNextMediaItem) player.seekToNextMediaItem()
                ACTION_PREVIOUS -> if (::player.isInitialized && player.hasPreviousMediaItem) player.seekToPreviousMediaItem()
                ACTION_REWIND_10 -> if (::player.isInitialized) seekRelative(-10_000L)
                ACTION_FORWARD_10 -> if (::player.isInitialized) seekRelative(10_000L)
                ACTION_SEEK_PERCENT -> if (::player.isInitialized) {
                    val percent = (intent.extras?.get(EXTRA_PERCENT) as? Number)?.toFloat()?.coerceIn(0f, 100f) ?: 0f
                    val duration = player.duration
                    if (duration > 0 && duration != C.TIME_UNSET) player.seekTo((duration.toDouble() * percent.toDouble() / 100.0).toLong().coerceIn(0L, duration))
                }
                ACTION_GET_POSITION -> publish()
                ACTION_SET_VOLUME -> setVolumePercent(intent.getIntExtra(EXTRA_VOLUME, currentVolumePercent()))
                ACTION_MUTE -> mute()
                ACTION_UNMUTE -> unmute()
            }
        } catch (e: Exception) {
            publish(e.message ?: "Player error")
        }
        publish()
        updateNotification()
        return START_STICKY
    }

    override fun onTaskRemoved(rootIntent: Intent?) {
        super.onTaskRemoved(rootIntent)
    }

    override fun onDestroy() {
        released = true
        handler.removeCallbacksAndMessages(null)
        try { mediaSession?.release() } catch (_: Exception) { }
        mediaSession = null
        try { if (::player.isInitialized) player.release() } catch (_: Exception) { }
        super.onDestroy()
    }

    override fun onGetSession(controllerInfo: MediaSession.ControllerInfo): MediaSession? = mediaSession
}