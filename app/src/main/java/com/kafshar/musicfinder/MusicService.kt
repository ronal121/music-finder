package com.kafshar.musicfinder

import android.app.Notification
import android.app.NotificationChannel
import android.app.NotificationManager
import android.app.PendingIntent
import android.content.Intent
import android.content.pm.ServiceInfo
import android.net.Uri
import android.os.Build
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

        const val ACTION_PLAY =
            "com.kafshar.musicfinder.PLAY"

        const val ACTION_PAUSE =
            "com.kafshar.musicfinder.PAUSE"

        const val ACTION_TOGGLE =
            "com.kafshar.musicfinder.TOGGLE"

        const val ACTION_STOP =
            "com.kafshar.musicfinder.STOP"

        const val ACTION_NEXT =
            "com.kafshar.musicfinder.NEXT"

        const val ACTION_PREVIOUS =
            "com.kafshar.musicfinder.PREVIOUS"

        const val ACTION_SEEK_PERCENT =
            "com.kafshar.musicfinder.SEEK_PERCENT"

        const val ACTION_GET_POSITION =
            "com.kafshar.musicfinder.GET_POSITION"

        const val ACTION_SET_VOLUME =
            "com.kafshar.musicfinder.SET_VOLUME"

        const val EXTRA_URL = "url"
        const val EXTRA_TITLE = "title"
        const val EXTRA_PERCENT = "percent"
        const val EXTRA_ARTIST = "artist"
        const val EXTRA_COVER = "cover"
        const val EXTRA_VOLUME = "volume"

        const val UPDATE =
            "com.kafshar.musicfinder.PLAYER_UPDATE"

        private const val NOTIFICATION_CHANNEL_ID =
            "music_playback"

        private const val NOTIFICATION_CHANNEL_NAME =
            "Music Playback"

        private const val NOTIFICATION_ID = 1001
    }

    private lateinit var player: ExoPlayer

    private var mediaSession: MediaSession? = null

    @Volatile
    private var released = false

    override fun onCreate() {
        /*
         * مهم:
         *
         * این سرویس با startForegroundService() اجرا می‌شود.
         * بنابراین باید قبل از هر کار سنگین، Foreground شود.
         *
         * Notification و startForeground در ابتدای onCreate
         * انجام می‌شوند تا Android منتظر ExoPlayer، MediaSession،
         * صف آهنگ‌ها یا عملیات دیگری نماند.
         */
        createNotificationChannel()
        startMusicForeground()

        super.onCreate()

        released = false

        player =
            ExoPlayer.Builder(this)
                .setAudioAttributes(
                    AudioAttributes.Builder()
                        .setContentType(
                            C.AUDIO_CONTENT_TYPE_MUSIC
                        )
                        .setUsage(
                            C.USAGE_MEDIA
                        )
                        .build(),
                    true
                )
                .setHandleAudioBecomingNoisy(true)
                .setPauseAtEndOfMediaItems(false)
                .build()

        val savedVolume =
            getSharedPreferences(
                "player_settings",
                MODE_PRIVATE
            ).getInt(
                "volume_percent",
                80
            ).coerceIn(0, 100)

        player.volume =
            savedVolume / 100f

        mediaSession =
            MediaSession.Builder(
                this,
                player
            )
                .setSessionActivity(
                    createOpenAppPendingIntent()
                )
                .build()

        player.addListener(
            object : Player.Listener {

                override fun onIsPlayingChanged(
                    isPlaying: Boolean
                ) {
                    safeSendUpdate()
                    updateForegroundNotification()
                }

                override fun onPlaybackStateChanged(
                    playbackState: Int
                ) {
                    safeSendUpdate()
                    updateForegroundNotification()
                }

                override fun onMediaItemTransition(
                    mediaItem: MediaItem?,
                    reason: Int
                ) {
                    if (mediaItem != null) {
                        saveToHistory(mediaItem)
                    }

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

                override fun onPlayerError(
                    error: PlaybackException
                ) {
                    try {
                        player.pause()
                    } catch (_: Exception) {
                    }

                    safeSendUpdate()
                    updateForegroundNotification()
                }
            }
        )

        updateForegroundNotification()
    }

    /**
     * ایجاد Notification Channel برای Foreground Service.
     */
    private fun createNotificationChannel() {

        if (Build.VERSION.SDK_INT >= Build.VERSION_CODES.O) {

            val channel =
                NotificationChannel(
                    NOTIFICATION_CHANNEL_ID,
                    NOTIFICATION_CHANNEL_NAME,
                    NotificationManager.IMPORTANCE_LOW
                ).apply {

                    description =
                        "Music playback controls"

                    setShowBadge(false)

                    lockscreenVisibility =
                        Notification.VISIBILITY_PUBLIC
                }

            val manager =
                getSystemService(
                    NotificationManager::class.java
                )

            manager?.createNotificationChannel(channel)
        }
    }

    /**
     * این تابع باید خیلی زود اجرا شود.
     *
     * هیچ عملیات شبکه،
     * Bitmap،
     * SharedPreferences سنگین،
     * MediaItem،
     * Queue
     * یا ExoPlayer
     * قبل از این تابع انجام نمی‌شود.
     */
    private fun startMusicForeground() {

        val notification =
            buildForegroundNotification()

        try {

            if (
                Build.VERSION.SDK_INT >=
                Build.VERSION_CODES.Q
            ) {

                startForeground(
                    NOTIFICATION_ID,
                    notification,
                    ServiceInfo.FOREGROUND_SERVICE_TYPE_MEDIA_PLAYBACK
                )

            } else {

                startForeground(
                    NOTIFICATION_ID,
                    notification
                )
            }

        } catch (_: Exception) {
            /*
             * عمداً چیزی throw نمی‌کنیم.
             *
             * اگر دستگاه محدودیت خاصی داشته باشد،
             * ادامه lifecycle سرویس را خراب نمی‌کنیم.
             */
        }
    }

    /**
     * Notification اولیه و Notification زمان پخش.
     */
    private fun buildForegroundNotification(): Notification {

        val openIntent =
            createOpenAppPendingIntent()

        val builder =
            NotificationCompat.Builder(
                this,
                NOTIFICATION_CHANNEL_ID
            )
                .setSmallIcon(
                    android.R.drawable.ic_media_play
                )
                .setContentTitle(
                    getCurrentTitle()
                )
                .setContentText(
                    getCurrentArtist()
                )
                .setContentIntent(
                    openIntent
                )
                .setOngoing(true)
                .setOnlyAlertOnce(true)
                .setShowWhen(false)
                .setVisibility(
                    NotificationCompat.VISIBILITY_PUBLIC
                )
                .setCategory(
                    NotificationCompat.CATEGORY_TRANSPORT
                )
                .setPriority(
                    NotificationCompat.PRIORITY_LOW
                )

        return builder.build()
    }

    private fun getCurrentTitle(): String {

        return try {

            if (
                ::player.isInitialized &&
                player.currentMediaItem != null
            ) {

                player.currentMediaItem
                    ?.mediaMetadata
                    ?.title
                    ?.toString()
                    ?.ifBlank {
                        "Music Finder"
                    }
                    ?: "Music Finder"

            } else {
                "Music Finder"
            }

        } catch (_: Exception) {
            "Music Finder"
        }
    }

    private fun getCurrentArtist(): String {

        return try {

            if (
                ::player.isInitialized &&
                player.currentMediaItem != null
            ) {

                player.currentMediaItem
                    ?.mediaMetadata
                    ?.artist
                    ?.toString()
                    ?.ifBlank {
                        "Music Finder"
                    }
                    ?: "Music Finder"

            } else {
                "Music Finder"
            }

        } catch (_: Exception) {
            "Music Finder"
        }
    }

    /**
     * Notification را بدون stop/start مجدد به‌روزرسانی می‌کند.
     */
    private fun updateForegroundNotification() {

        if (released) {
            return
        }

        try {

            val manager =
                getSystemService(
                    NotificationManager::class.java
                )

            manager?.notify(
                NOTIFICATION_ID,
                buildForegroundNotification()
            )

        } catch (_: Exception) {
        }
    }

    override fun onStartCommand(
        intent: Intent?,
        flags: Int,
        startId: Int
    ): Int {

        if (
            released ||
            !::player.isInitialized
        ) {
            return START_STICKY
        }

        try {

            when (intent?.action) {

                ACTION_PLAY -> {
                    playUrl(intent)
                }

                ACTION_PAUSE -> {

                    player.pause()

                    safeSendUpdate()
                    updateForegroundNotification()
                }

                ACTION_TOGGLE -> {

                    if (
                        player.currentMediaItem == null
                    ) {

                        val url =
                            intent.getStringExtra(
                                EXTRA_URL
                            )

                        if (!url.isNullOrBlank()) {
                            playUrl(intent)
                        }

                    } else {

                        if (player.isPlaying) {
                            player.pause()
                        } else {
                            player.play()
                        }

                        safeSendUpdate()
                        updateForegroundNotification()
                    }
                }

                ACTION_NEXT -> {
                    next()
                }

                ACTION_PREVIOUS -> {
                    previous()
                }

                ACTION_SEEK_PERCENT -> {

                    val percent =
                        intent.getIntExtra(
                            EXTRA_PERCENT,
                            0
                        )

                    seekPercent(percent)
                }

                ACTION_SET_VOLUME -> {

                    val volume =
                        intent.getIntExtra(
                            EXTRA_VOLUME,
                            80
                        ).coerceIn(0, 100)

                    player.volume =
                        volume / 100f

                    getSharedPreferences(
                        "player_settings",
                        MODE_PRIVATE
                    ).edit()
                        .putInt(
                            "volume_percent",
                            volume
                        )
                        .apply()

                    safeSendUpdate()
                }

                ACTION_GET_POSITION -> {
                    safeSendUpdate()
                }

                ACTION_STOP -> {

                    try {
                        player.stop()
                    } catch (_: Exception) {
                    }

                    safeSendUpdate()

                    try {
                        stopForeground(
                            STOP_FOREGROUND_REMOVE
                        )
                    } catch (_: Exception) {
                    }

                    stopSelf()
                }
            }

        } catch (_: Exception) {

            safeSendUpdate()
        }

        return START_STICKY
    }

    private fun playUrl(
        intent: Intent
    ) {

        val url =
            intent.getStringExtra(
                EXTRA_URL
            )
                ?.trim()

        if (
            url.isNullOrBlank() ||
            !ServerConfig.isAllowedMediaUrl(url)
        ) {
            safeSendUpdate()
            return
        }

        val title =
            intent.getStringExtra(
                EXTRA_TITLE
            )
                ?.trim()
                ?.ifBlank {
                    "Music Finder"
                }
                ?: "Music Finder"

        val artist =
            intent.getStringExtra(
                EXTRA_ARTIST
            )
                ?.trim()
                ?.ifBlank {
                    "Music Finder"
                }
                ?: "Music Finder"

        val cover =
            intent.getStringExtra(
                EXTRA_COVER
            )
                ?.trim()
                ?: ""

        /*
         * Foreground قبلاً برقرار شده است.
         *
         * از اینجا به بعد می‌توانیم با خیال راحت
         * MediaItem و Queue را بسازیم.
         */

        val current =
            createMediaItem(
                url,
                title,
                artist,
                cover
            )

        val queue =
            buildRelatedQueue(
                url,
                title,
                artist,
                cover
            )

        try {

            player.stop()
            player.clearMediaItems()

            if (queue.size > 1) {

                player.setMediaItems(
                    queue,
                    0,
                    0L
                )

            } else {

                player.setMediaItem(
                    current
                )
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
            } catch (_: Exception) {
            }

            safeSendUpdate()
            updateForegroundNotification()
        }
    }

    private fun next() {

        try {

            if (
                player.hasNextMediaItem()
            ) {

                player.seekToNextMediaItem()

            } else if (
                player.currentMediaItem != null
            ) {

                player.seekTo(
                    0,
                    0L
                )
            }

            player.prepare()
            player.play()

            safeSendUpdate()
            updateForegroundNotification()

        } catch (_: Exception) {

            safeSendUpdate()
        }
    }

    private fun previous() {

        try {

            if (
                player.hasPreviousMediaItem()
            ) {

                player.seekToPreviousMediaItem()

            } else {

                player.seekTo(
                    0,
                    0L
                )
            }

            player.prepare()
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

        val result =
            ArrayList<MediaItem>()

        result.add(
            createMediaItem(
                currentUrl,
                currentTitle,
                currentArtist,
                currentCover
            )
        )

        try {

            val prefs =
                getSharedPreferences(
                    "search_results",
                    MODE_PRIVATE
                )

            val data =
                prefs.getString(
                    "songs",
                    ""
                ) ?: ""

            if (data.isBlank()) {
                return result
            }

            val candidates =
                ArrayList<SongResult>()

            data.split("\n")
                .take(60)
                .forEach { line ->

                    val parts =
                        line.split(
                            "|||",
                            limit = 5
                        )

                    if (parts.size < 5) {
                        return@forEach
                    }

                    val song =
                        SongResult(
                            url = parts[0],
                            title = parts[1],
                            artist = parts[2],
                            site = parts[3],
                            cover = parts[4]
                        )

                    if (
                        song.url.isNotBlank() &&
                        song.url != currentUrl &&
                        ServerConfig.isAllowedMediaUrl(
                            song.url
                        )
                    ) {

                        candidates.add(song)
                    }
                }

            if (candidates.isEmpty()) {
                return result
            }

            val artistWords =
                currentArtist
                    .lowercase()
                    .split(
                        Regex(
                            "[\\s,،\\-_|]+"
                        )
                    )
                    .filter {
                        it.length >= 2
                    }

            val titleWords =
                currentTitle
                    .lowercase()
                    .split(
                        Regex(
                            "[\\s,،\\-_|]+"
                        )
                    )
                    .filter {
                        it.length >= 2
                    }

            val sameArtist =
                candidates.filter { song ->

                    val artist =
                        song.artist.lowercase()

                    artistWords.any {
                        artist.contains(it)
                    }
                }

            val sameTitle =
                candidates.filter { song ->

                    val title =
                        song.title.lowercase()

                    titleWords.any {
                        title.contains(it)
                    }
                }

            val source =
                when {

                    sameArtist.size >= 2 ->
                        sameArtist

                    sameTitle.isNotEmpty() ->
                        sameTitle

                    sameArtist.isNotEmpty() ->
                        sameArtist

                    else ->
                        candidates
                }

            source
                .distinctBy {
                    it.url
                }
                .shuffled()
                .take(
                    minOf(
                        15,
                        source.size
                    )
                )
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

        } catch (_: Exception) {
        }

        return result
    }

    private fun createMediaItem(
        url: String,
        title: String,
        artist: String,
        cover: String
    ): MediaItem {

        val metadata =
            MediaMetadata.Builder()
                .setTitle(
                    title.ifBlank {
                        "Music Finder"
                    }
                )
                .setArtist(
                    artist.ifBlank {
                        "Music Finder"
                    }
                )
                .apply {

                    if (cover.isNotBlank()) {

                        try {

                            setArtworkUri(
                                Uri.parse(cover)
                            )

                        } catch (_: Exception) {
                        }
                    }
                }
                .build()

        return MediaItem.Builder()
            .setUri(url)
            .setMediaId(url)
            .setMediaMetadata(metadata)
            .build()
    }

    private fun saveToHistory(
        item: MediaItem
    ) {

        try {

            val url =
                item.mediaId

            if (url.isBlank()) {
                return
            }

            val prefs =
                getSharedPreferences(
                    "music_history",
                    MODE_PRIVATE
                )

            val old =
                prefs.getString(
                    "items",
                    ""
                ) ?: ""

            val title =
                item.mediaMetadata.title
                    ?.toString()
                    ?: ""

            val artist =
                item.mediaMetadata.artist
                    ?.toString()
                    ?: ""

            val cover =
                item.mediaMetadata.artworkUri
                    ?.toString()
                    ?: ""

            val line =
                listOf(
                    url,
                    title,
                    artist,
                    cover
                ).joinToString("|||")

            val lines =
                old.split("\n")
                    .filter {
                        it.isNotBlank() &&
                                !it.startsWith(
                                    "$url|||"
                                )
                    }
                    .toMutableList()

            lines.add(
                0,
                line
            )

            while (
                lines.size > 50
            ) {

                lines.removeAt(
                    lines.lastIndex
                )
            }

            prefs.edit()
                .putString(
                    "items",
                    lines.joinToString("\n")
                )
                .apply()

        } catch (_: Exception) {
        }
    }

    private fun seekPercent(
        percent: Int
    ) {

        try {

            val duration =
                player.duration

            if (
                duration <= 0 ||
                duration == C.TIME_UNSET
            ) {
                return
            }

            val safe =
                percent.coerceIn(
                    0,
                    100
                )

            val position =
                duration *
                        safe /
                        100L

            player.seekTo(position)

            safeSendUpdate()

        } catch (_: Exception) {
        }
    }

    private fun safeSendUpdate() {

        if (
            released ||
            !::player.isInitialized
        ) {
            return
        }

        try {

            val item =
                player.currentMediaItem

            val duration =
                if (
                    player.duration ==
                    C.TIME_UNSET
                ) {
                    0L
                } else {
                    player.duration
                }

            val intent =
                Intent(UPDATE).apply {

                    setPackage(
                        packageName
                    )

                    putExtra(
                        "playing",
                        player.isPlaying
                    )

                    putExtra(
                        "position",
                        player.currentPosition
                    )

                    putExtra(
                        "duration",
                        duration
                    )

                    putExtra(
                        "title",
                        item?.mediaMetadata
                            ?.title
                            ?.toString()
                            ?: ""
                    )

                    putExtra(
                        "artist",
                        item?.mediaMetadata
                            ?.artist
                            ?.toString()
                            ?: ""
                    )

                    putExtra(
                        "volume",
                        (player.volume * 100f)
                            .toInt()
                            .coerceIn(0, 100)
                    )
                }

            sendBroadcast(intent)

        } catch (_: Exception) {
        }
    }

    private fun createOpenAppPendingIntent():
        PendingIntent {

        val intent =
            Intent(
                this,
                MainActivity::class.java
            ).apply {

                flags =
                    Intent.FLAG_ACTIVITY_SINGLE_TOP or
                            Intent.FLAG_ACTIVITY_CLEAR_TOP
            }

        return PendingIntent.getActivity(
            this,
            200,
            intent,
            PendingIntent.FLAG_UPDATE_CURRENT or
                    PendingIntent.FLAG_IMMUTABLE
        )
    }

    override fun onGetSession(
        controllerInfo:
            MediaSession.ControllerInfo
    ): MediaSession? {

        return mediaSession
    }

    override fun onTaskRemoved(
        rootIntent: Intent?
    ) {

        try {

            if (
                ::player.isInitialized &&
                player.playbackState !=
                Player.STATE_IDLE
            ) {

                player.play()
            }

        } catch (_: Exception) {
        }

        super.onTaskRemoved(rootIntent)
    }

    override fun onDestroy() {

        released = true

        try {

            mediaSession?.release()

        } catch (_: Exception) {
        }

        mediaSession = null

        try {

            if (::player.isInitialized) {

                player.stop()
                player.clearMediaItems()
                player.release()
            }

        } catch (_: Exception) {
        }

        try {

            stopForeground(
                STOP_FOREGROUND_REMOVE
            )

        } catch (_: Exception) {
        }

        super.onDestroy()
    }
}
