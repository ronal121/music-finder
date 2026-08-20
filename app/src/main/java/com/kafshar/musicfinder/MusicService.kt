package com.kafshar.musicfinder

import android.app.PendingIntent
import android.content.Intent
import android.net.Uri
import androidx.media3.common.AudioAttributes
import androidx.media3.common.C
import androidx.media3.common.MediaItem
import androidx.media3.common.MediaMetadata
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

    override fun onCreate() {
        super.onCreate()

        player = ExoPlayer.Builder(this)
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
                    if (mediaItem != null) {
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
            }
        )
    }

    override fun onStartCommand(
        intent: Intent?,
        flags: Int,
        startId: Int
    ): Int {

        when (intent?.action) {

            ACTION_PLAY -> {
                playUrl(intent)
            }

            ACTION_PAUSE -> {
                player.pause()
                sendPlayerUpdate()
            }

            ACTION_TOGGLE -> {

                if (player.isPlaying) {
                    player.pause()
                } else {
                    player.play()
                }

                sendPlayerUpdate()
            }

            ACTION_NEXT -> {

                if (player.hasNextMediaItem()) {
                    player.seekToNextMediaItem()
                } else {
                    player.seekTo(0)
                }

                player.play()
                sendPlayerUpdate()
            }

            ACTION_PREVIOUS -> {

                if (player.hasPreviousMediaItem()) {
                    player.seekToPreviousMediaItem()
                } else {
                    player.seekTo(0)
                }

                player.play()
                sendPlayerUpdate()
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
                // فقط وضعیت را ارسال می‌کنیم.
                // این اکشن نباید باعث راه‌اندازی مجدد سرویس شود.
                sendPlayerUpdate()
            }

            ACTION_STOP -> {

                player.stop()
                sendPlayerUpdate()
                stopSelf()
            }
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

        if (url.isNullOrBlank()) {
            return
        }

        val title =
            intent.getStringExtra(
                EXTRA_TITLE
            ) ?: "Music Finder"

        val artist =
            intent.getStringExtra(
                EXTRA_ARTIST
            ) ?: "Music Finder"

        val cover =
            intent.getStringExtra(
                EXTRA_COVER
            ) ?: ""

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

        sendPlayerUpdate()
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
            .forEach { line ->

                val parts =
                    line.split("|||")

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
                        song.url != currentUrl
                    ) {
                        candidates.add(song)
                    }
                }
            }

        if (candidates.isEmpty()) {
            return result
        }

        val artistWords =
            currentArtist
                .lowercase()
                .split(
                    Regex("[\\s,،\\-_|]+")
                )
                .filter {
                    it.length >= 2
                }

        val sameArtist =
            candidates.filter { song ->

                val songArtist =
                    song.artist.lowercase()

                artistWords.any { word ->
                    songArtist.contains(word)
                }
            }

        val titleWords =
            currentTitle
                .lowercase()
                .split(
                    Regex("[\\s,،\\-_|]+")
                )
                .filter {
                    it.length >= 2
                }

        val sameTitleStyle =
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

                sameTitleStyle.isNotEmpty() ->
                    sameTitleStyle

                sameArtist.isNotEmpty() ->
                    sameArtist

                else ->
                    candidates
            }

        source
            .shuffled()
            .take(
                minOf(
                    25,
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
                .setTitle(title)
                .setArtist(artist)
                .apply {

                    if (cover.isNotBlank()) {

                        setArtworkUri(
                            Uri.parse(cover)
                        )
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

        val newLine =
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
                    !it.startsWith("$url|||")
                }
                .toMutableList()

        lines.add(
            0,
            newLine
        )

        while (lines.size > 100) {
            lines.removeAt(lines.lastIndex)
        }

        prefs.edit()
            .putString(
                "items",
                lines.joinToString("\n")
            )
            .apply()
    }

    private fun seekPercent(
        percent: Int
    ) {

        val duration =
            player.duration

        if (duration <= 0) {
            return
        }

        val safe =
            percent.coerceIn(
                0,
                100
            )

        val position =
            duration * safe / 100

        player.seekTo(position)

        sendPlayerUpdate()
    }

    private fun sendPlayerUpdate() {

        val item =
            player.currentMediaItem

        val intent =
            Intent(UPDATE).apply {

                setPackage(packageName)

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
                    player.duration
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

        // اگر آهنگ در حال پخش است،
        // سرویس ادامه می‌دهد.
        if (player.isPlaying) {
            player.play()
        }

        super.onTaskRemoved(rootIntent)
    }

    override fun onDestroy() {

        mediaSession?.release()
        mediaSession = null

        player.release()

        super.onDestroy()
    }
}
