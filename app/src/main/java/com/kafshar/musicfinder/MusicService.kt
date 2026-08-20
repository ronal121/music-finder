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

        const val EXTRA_URL = "url"
        const val EXTRA_TITLE = "title"
        const val EXTRA_PERCENT = "percent"
        const val EXTRA_ARTIST = "artist"
        const val EXTRA_COVER = "cover"

        const val UPDATE =
            "com.kafshar.musicfinder.PLAYER_UPDATE"
    }

    private lateinit var player: ExoPlayer

    private var mediaSession: MediaSession? = null

    private var released = false

    override fun onCreate() {

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
                .setHandleAudioBecomingNoisy(
                    true
                )
                .setPauseAtEndOfMediaItems(
                    false
                )
                .build()

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
                }

                override fun onPlaybackStateChanged(
                    playbackState: Int
                ) {
                    safeSendUpdate()
                }

                override fun onMediaItemTransition(
                    mediaItem: MediaItem?,
                    reason: Int
                ) {

                    if (
                        mediaItem != null
                    ) {
                        saveToHistory(
                            mediaItem
                        )
                    }

                    safeSendUpdate()
                }

                override fun onPositionDiscontinuity(
                    oldPosition:
                        Player.PositionInfo,
                    newPosition:
                        Player.PositionInfo,
                    reason: Int
                ) {
                    safeSendUpdate()
                }

                override fun onPlayerError(
                    error: PlaybackException
                ) {

                    player.pause()

                    safeSendUpdate()
                }
            }
        )
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
            return START_NOT_STICKY
        }

        try {

            when (intent?.action) {

                ACTION_PLAY -> {
                    playUrl(intent)
                }

                ACTION_PAUSE -> {
                    player.pause()
                    safeSendUpdate()
                }

                ACTION_TOGGLE -> {

                    if (
                        player.currentMediaItem ==
                        null
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

                ACTION_GET_POSITION -> {
                    safeSendUpdate()
                }

                ACTION_STOP -> {

                    player.stop()
                    safeSendUpdate()

                    stopSelf()
                }
            }

        } catch (_: Exception) {

            safeSendUpdate()
        }

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
            url.isNullOrBlank() ||
            !isValidMediaUrl(url)
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

        } catch (_: Exception) {

            player.stop()
            safeSendUpdate()
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

                    if (parts.size >= 5) {

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
                            isValidMediaUrl(
                                song.url
                            )
                        ) {
                            candidates.add(song)
                        }
                    }
                }

            if (candidates.isEmpty()) {
                return result
            }

            val currentArtistWords =
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

            val currentTitleWords =
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

                    currentArtistWords.any {
                        artist.contains(it)
                    }
                }

            val sameTitle =
                candidates.filter { song ->

                    val title =
                        song.title.lowercase()

                    currentTitleWords.any {
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

    private fun isValidMediaUrl(
        value: String
    ): Boolean {

        return try {

            val uri =
                Uri.parse(value)

            (
                uri.scheme.equals(
                    "http",
                    true
                ) ||
                uri.scheme.equals(
                    "https",
                    true
                )
            ) &&
                    !uri.host.isNullOrBlank()

        } catch (_: Exception) {
            false
        }
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

                    if (
                        cover.isNotBlank()
                    ) {

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

            player.seekTo(
                position
            )

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
                        if (
                            player.duration ==
                            C.TIME_UNSET
                        ) {
                            0L
                        } else {
                            player.duration
                        }
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

        if (
            ::player.isInitialized &&
            player.isPlaying
        ) {
            player.play()
        }

        super.onTaskRemoved(
            rootIntent
        )
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

        super.onDestroy()
    }
}
