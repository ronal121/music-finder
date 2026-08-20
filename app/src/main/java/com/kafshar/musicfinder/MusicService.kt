package com.kafshar.musicfinder

import android.app.PendingIntent
import android.content.Intent
import android.net.Uri
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

        const val EXTRA_URL =
            "url"

        const val EXTRA_TITLE =
            "title"

        const val EXTRA_PERCENT =
            "percent"

        const val EXTRA_ARTIST =
            "artist"

        const val EXTRA_COVER =
            "cover"

        const val UPDATE =
            "com.kafshar.musicfinder.PLAYER_UPDATE"

        const val EXTRA_ERROR =
            "error"

        const val EXTRA_ERROR_CODE =
            "error_code"
    }

    private var player: ExoPlayer? = null

    private var mediaSession: MediaSession? = null

    @Volatile
    private var destroyed = false

    private val playerListener =
        object : Player.Listener {

            override fun onIsPlayingChanged(
                isPlaying: Boolean
            ) {
                sendPlayerUpdate()
            }

            override fun onPlaybackStateChanged(
                playbackState: Int
            ) {
                sendPlayerUpdate()
            }

            override fun onMediaItemTransition(
                mediaItem: MediaItem?,
                reason: Int
            ) {

                if (
                    mediaItem != null &&
                    reason != Player.MEDIA_ITEM_TRANSITION_REASON_REPEAT
                ) {
                    saveToHistory(mediaItem)
                }

                sendPlayerUpdate()
            }

            override fun onPositionDiscontinuity(
                oldPosition: Player.PositionInfo,
                newPosition: Player.PositionInfo,
                reason: Int
            ) {
                sendPlayerUpdate()
            }

            override fun onPlayerError(
                error: PlaybackException
            ) {

                sendPlayerError(error)

                /*
                 * مهم:
                 * خطای ExoPlayer نباید باعث Crash برنامه شود.
                 *
                 * فقط پخش متوقف می‌شود.
                 * خود Service زنده می‌ماند تا کاربر بتواند
                 * آهنگ دیگری انتخاب کند.
                 */
                try {
                    player?.pause()
                } catch (_: Exception) {
                }

                sendPlayerUpdate()
            }
        }

    override fun onCreate() {

        super.onCreate()

        destroyed = false

        try {

            val newPlayer =
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
                    .setHandleAudioBecomingNoisy(
                        true
                    )
                    .build()

            player =
                newPlayer

            newPlayer.addListener(
                playerListener
            )

            mediaSession =
                MediaSession.Builder(
                    this,
                    newPlayer
                )
                    .setSessionActivity(
                        createOpenAppPendingIntent()
                    )
                    .build()

        } catch (e: Exception) {

            /*
             * اگر ساخت Player یا MediaSession به هر دلیل
             * شکست خورد، اجازه نمی‌دهیم Exception از Service
             * خارج شود.
             */

            mediaSession = null

            try {
                player?.release()
            } catch (_: Exception) {
            }

            player = null
        }
    }

    override fun onStartCommand(
        intent: Intent?,
        flags: Int,
        startId: Int
    ): Int {

        if (
            destroyed ||
            player == null
        ) {
            return START_NOT_STICKY
        }

        try {

            when (intent?.action) {

                ACTION_PLAY -> {

                    playUrl(
                        intent
                    )
                }

                ACTION_PAUSE -> {

                    safePause()
                }

                ACTION_TOGGLE -> {

                    togglePlayback()
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

                    seekPercent(
                        percent
                    )
                }

                ACTION_GET_POSITION -> {

                    /*
                     * فقط وضعیت Player را برگردان.
                     *
                     * این اکشن نباید Player جدید بسازد،
                     * آهنگ را دوباره prepare کند یا
                     * Service را restart کند.
                     */
                    sendPlayerUpdate()
                }

                ACTION_STOP -> {

                    stopPlaybackAndService()
                }
            }

        } catch (e: Exception) {

            /*
             * هیچ فرمانی از Activity نباید باعث
             * Force Close شدن Service شود.
             */

            sendServiceError(
                e.message
                    ?: "خطای ناشناخته در پخش"
            )
        }

        /*
         * START_NOT_STICKY:
         *
         * اگر Android Service را به دلیل کمبود منابع
         * از بین برد، نباید با Intent قدیمی دوباره
         * یک آهنگ را ناخواسته اجرا کند.
         */
        return START_NOT_STICKY
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
            url.isNullOrBlank()
        ) {

            sendServiceError(
                "آدرس آهنگ معتبر نیست"
            )

            return
        }

        if (
            !isValidMediaUrl(url)
        ) {

            sendServiceError(
                "آدرس پخش معتبر نیست"
            )

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

        val current =
            createMediaItem(
                url = url,
                title = title,
                artist = artist,
                cover = cover
            )
                ?: run {

                    sendServiceError(
                        "ساخت آهنگ امکان‌پذیر نیست"
                    )

                    return
                }

        try {

            val currentPlayer =
                player
                    ?: return

            /*
             * قبل از تعویض آهنگ، Queue قبلی کاملاً
             * جایگزین می‌شود.
             *
             * این کار از باقی ماندن آهنگ‌های قبلی
             * جلوگیری می‌کند.
             */
            val queue =
                buildRelatedQueue(
                    currentUrl = url,
                    currentTitle = title,
                    currentArtist = artist,
                    currentCover = cover
                )

            currentPlayer.stop()

            if (
                queue.size > 1
            ) {

                currentPlayer.setMediaItems(
                    queue,
                    0,
                    0L
                )

            } else {

                currentPlayer.setMediaItem(
                    current
                )
            }

            currentPlayer.prepare()

            currentPlayer.play()

            saveToHistory(
                current
            )

            sendPlayerUpdate()

        } catch (e: Exception) {

            sendServiceError(
                e.message
                    ?: "پخش آهنگ ناموفق بود"
            )

            try {
                player?.stop()
            } catch (_: Exception) {
            }

            sendPlayerUpdate()
        }
    }

    private fun togglePlayback() {

        val currentPlayer =
            player
                ?: return

        try {

            if (
                currentPlayer.currentMediaItem == null
            ) {
                return
            }

            if (
                currentPlayer.isPlaying
            ) {

                currentPlayer.pause()

            } else {

                /*
                 * اگر Player در حالت ENDED باشد،
                 * play() به‌تنهایی همیشه رفتار موردنظر
                 * را ایجاد نمی‌کند.
                 */
                if (
                    currentPlayer.playbackState ==
                    Player.STATE_ENDED
                ) {

                    currentPlayer.seekTo(
                        0
                    )
                }

                currentPlayer.play()
            }

            sendPlayerUpdate()

        } catch (e: Exception) {

            sendServiceError(
                e.message
                    ?: "تغییر وضعیت پخش ناموفق بود"
            )
        }
    }

    private fun safePause() {

        try {

            player?.pause()

            sendPlayerUpdate()

        } catch (e: Exception) {

            sendServiceError(
                e.message
                    ?: "توقف پخش ناموفق بود"
            )
        }
    }

    private fun next() {

        val currentPlayer =
            player
                ?: return

        try {

            if (
                currentPlayer.currentMediaItem == null
            ) {
                return
            }

            if (
                currentPlayer.hasNextMediaItem()
            ) {

                currentPlayer.seekToNextMediaItem()

            } else {

                /*
                 * اگر Queue تمام شده،
                 * به ابتدای آهنگ فعلی برمی‌گردیم.
                 */
                currentPlayer.seekTo(
                    0
                )
            }

            currentPlayer.play()

            sendPlayerUpdate()

        } catch (e: Exception) {

            sendServiceError(
                e.message
                    ?: "آهنگ بعدی قابل پخش نیست"
            )
        }
    }

    private fun previous() {

        val currentPlayer =
            player
                ?: return

        try {

            if (
                currentPlayer.currentMediaItem == null
            ) {
                return
            }

            if (
                currentPlayer.hasPreviousMediaItem()
            ) {

                currentPlayer.seekToPreviousMediaItem()

            } else {

                currentPlayer.seekTo(
                    0
                )
            }

            currentPlayer.play()

            sendPlayerUpdate()

        } catch (e: Exception) {

            sendServiceError(
                e.message
                    ?: "آهنگ قبلی قابل پخش نیست"
            )
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

        val current =
            createMediaItem(
                currentUrl,
                currentTitle,
                currentArtist,
                currentCover
            )

        if (
            current == null
        ) {
            return result
        }

        result.add(
            current
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
                )
                    ?: ""

            if (
                data.isBlank()
            ) {
                return result
            }

            val candidates =
                ArrayList<SongResult>()

            data.split("\n")
                .forEach { line ->

                    try {

                        if (
                            line.isBlank()
                        ) {
                            return@forEach
                        }

                        val parts =
                            line.split(
                                "|||"
                            )

                        if (
                            parts.size < 5
                        ) {
                            return@forEach
                        }

                        val song =
                            SongResult(
                                url =
                                    parts[0].trim(),
                                title =
                                    parts[1].trim(),
                                artist =
                                    parts[2].trim(),
                                site =
                                    parts[3].trim(),
                                cover =
                                    parts[4].trim()
                            )

                        if (
                            song.url.isNotBlank() &&
                            song.url != currentUrl &&
                            isValidMediaUrl(
                                song.url
                            )
                        ) {

                            candidates.add(
                                song
                            )
                        }

                    } catch (_: Exception) {
                        /*
                         * یک رکورد خراب نباید کل Queue
                         * را خراب کند.
                         */
                    }
                }

            if (
                candidates.isEmpty()
            ) {
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
                if (
                    artistWords.isEmpty()
                ) {

                    emptyList()

                } else {

                    candidates.filter { song ->

                        val songArtist =
                            song.artist
                                .lowercase()

                        artistWords.any {
                            word ->
                            songArtist.contains(
                                word
                            )
                        }
                    }
                }

            val sameTitleStyle =
                if (
                    titleWords.isEmpty()
                ) {

                    emptyList()

                } else {

                    candidates.filter { song ->

                        val songTitle =
                            song.title
                                .lowercase()

                        titleWords.any {
                            word ->
                            songTitle.contains(
                                word
                            )
                        }
                    }
                }

            val source =
                when {

                    sameArtist.size >= 2 ->
                        sameArtist

                    sameTitleStyle.isNotEmpty() ->
                        sameTitleStyle

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
                        25,
                        source.size
                    )
                )
                .forEach { song ->

                    val item =
                        createMediaItem(
                            url =
                                song.url,
                            title =
                                song.title,
                            artist =
                                song.artist,
                            cover =
                                song.cover
                        )

                    if (
                        item != null
                    ) {

                        result.add(
                            item
                        )
                    }
                }

        } catch (_: Exception) {
            /*
             * Queue اختیاری است.
             *
             * اگر ساخت Queue خراب شود،
             * خود آهنگ اصلی همچنان قابل پخش است.
             */
        }

        return result
    }

    private fun createMediaItem(
        url: String,
        title: String,
        artist: String,
        cover: String
    ): MediaItem? {

        return try {

            if (
                !isValidMediaUrl(url)
            ) {
                return null
            }

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

                        if (
                            cover.isNotBlank()
                        ) {

                            try {

                                setArtworkUri(
                                    Uri.parse(
                                        cover
                                    )
                                )

                            } catch (_: Exception) {
                            }
                        }
                    }
                    .build()

            MediaItem.Builder()
                .setUri(
                    Uri.parse(url)
                )
                .setMediaId(
                    url
                )
                .setMediaMetadata(
                    metadata
                )
                .build()

        } catch (_: Exception) {

            null
        }
    }

    private fun isValidMediaUrl(
        url: String
    ): Boolean {

        return try {

            val uri =
                Uri.parse(
                    url
                )

            val scheme =
                uri.scheme
                    ?.lowercase()

            (
                scheme == "http" ||
                scheme == "https"
            ) &&
                    !uri.host.isNullOrBlank()

        } catch (_: Exception) {

            false
        }
    }

    private fun saveToHistory(
        item: MediaItem
    ) {

        try {

            val url =
                item.mediaId

            if (
                url.isBlank()
            ) {
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
                )
                    ?: ""

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

            val newLine =
                listOf(
                    url,
                    title,
                    artist,
                    cover
                )
                    .joinToString(
                        "|||"
                    )

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
                newLine
            )

            while (
                lines.size > 100
            ) {

                lines.removeAt(
                    lines.lastIndex
                )
            }

            prefs.edit()
                .putString(
                    "items",
                    lines.joinToString(
                        "\n"
                    )
                )
                .apply()

        } catch (_: Exception) {
            /*
             * History یک قابلیت جانبی است.
             * خرابی آن نباید پخش موسیقی را خراب کند.
             */
        }
    }

    private fun seekPercent(
        percent: Int
    ) {

        val currentPlayer =
            player
                ?: return

        try {

            val duration =
                currentPlayer.duration

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
                (
                    duration *
                        safe.toLong()
                ) / 100L

            currentPlayer.seekTo(
                position.coerceIn(
                    0L,
                    duration
                )
            )

            sendPlayerUpdate()

        } catch (e: Exception) {

            sendServiceError(
                e.message
                    ?: "Seek ناموفق بود"
            )
        }
    }

    private fun sendPlayerUpdate() {

        if (
            destroyed
        ) {
            return
        }

        try {

            val currentPlayer =
                player
                    ?: return

            val item =
                currentPlayer.currentMediaItem

            val duration =
                currentPlayer.duration

            val safeDuration =
                if (
                    duration == C.TIME_UNSET ||
                    duration < 0
                ) {
                    0L
                } else {
                    duration
                }

            val position =
                currentPlayer.currentPosition
                    .coerceAtLeast(
                        0L
                    )

            val intent =
                Intent(
                    UPDATE
                ).apply {

                    setPackage(
                        packageName
                    )

                    putExtra(
                        "playing",
                        currentPlayer.isPlaying
                    )

                    putExtra(
                        "position",
                        position
                    )

                    putExtra(
                        "duration",
                        safeDuration
                    )

                    putExtra(
                        "title",
                        item
                            ?.mediaMetadata
                            ?.title
                            ?.toString()
                            ?: ""
                    )

                    putExtra(
                        "artist",
                        item
                            ?.mediaMetadata
                            ?.artist
                            ?.toString()
                            ?: ""
                    )
                }

            sendBroadcast(
                intent
            )

        } catch (_: Exception) {
            /*
             * Activity ممکن است در حال Destroy شدن باشد.
             * Broadcast شکست‌خورده نباید Service را بکشد.
             */
        }
    }

    private fun sendPlayerError(
        error: PlaybackException
    ) {

        if (
            destroyed
        ) {
            return
        }

        try {

            val intent =
                Intent(
                    UPDATE
                ).apply {

                    setPackage(
                        packageName
                    )

                    putExtra(
                        "playing",
                        false
                    )

                    putExtra(
                        "position",
                        player
                            ?.currentPosition
                            ?: 0L
                    )

                    putExtra(
                        "duration",
                        0L
                    )

                    putExtra(
                        "title",
                        player
                            ?.currentMediaItem
                            ?.mediaMetadata
                            ?.title
                            ?.toString()
                            ?: ""
                    )

                    putExtra(
                        "artist",
                        player
                            ?.currentMediaItem
                            ?.mediaMetadata
                            ?.artist
                            ?.toString()
                            ?: ""
                    )

                    putExtra(
                        EXTRA_ERROR,
                        error.message
                            ?: "خطای پخش"
                    )

                    putExtra(
                        EXTRA_ERROR_CODE,
                        error.errorCode
                    )
                }

            sendBroadcast(
                intent
            )

        } catch (_: Exception) {
        }
    }

    private fun sendServiceError(
        message: String
    ) {

        if (
            destroyed
        ) {
            return
        }

        try {

            val intent =
                Intent(
                    UPDATE
                ).apply {

                    setPackage(
                        packageName
                    )

                    putExtra(
                        "playing",
                        false
                    )

                    putExtra(
                        "position",
                        player
                            ?.currentPosition
                            ?: 0L
                    )

                    putExtra(
                        "duration",
                        player
                            ?.duration
                            ?.takeIf {
                                it > 0 &&
                                        it != C.TIME_UNSET
                            }
                            ?: 0L
                    )

                    putExtra(
                        EXTRA_ERROR,
                        message
                    )
                }

            sendBroadcast(
                intent
            )

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

    private fun stopPlaybackAndService() {

        try {

            player?.stop()

            player?.clearMediaItems()

            sendPlayerUpdate()

        } catch (_: Exception) {
        }

        stopSelf()
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

        /*
         * MediaSessionService مسئول Background Playback است.
         *
         * اگر Player در حال پخش باشد، آن را متوقف
         * یا آزاد نمی‌کنیم فقط چون Activity از Recent Apps
         * حذف شده است.
         */
        try {

            if (
                player?.isPlaying == true
            ) {
                player?.play()
            }

        } catch (_: Exception) {
        }

        super.onTaskRemoved(
            rootIntent
        )
    }

    override fun onDestroy() {

        if (
            destroyed
        ) {
            super.onDestroy()
            return
        }

        destroyed = true

        try {

            player?.removeListener(
                playerListener
            )

        } catch (_: Exception) {
        }

        try {

            mediaSession?.release()

        } catch (_: Exception) {
        }

        mediaSession = null

        try {

            player?.release()

        } catch (_: Exception) {
        }

        player = null

        super.onDestroy()
    }
}
