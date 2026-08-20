package com.kafshar.musicfinder

import android.app.PendingIntent
import android.content.Intent
import android.os.Build
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
    }

    private lateinit var player: ExoPlayer

    private var mediaSession:
        MediaSession? = null

    override fun onCreate() {

        super.onCreate()

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

                    sendPlayerUpdate()
                }

                override fun onPositionDiscontinuity(
                    oldPosition:
                        Player.PositionInfo,

                    newPosition:
                        Player.PositionInfo,

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

        when (
            intent?.action
        ) {

            ACTION_PLAY -> {

                playUrl(
                    intent
                )
            }

            ACTION_PAUSE -> {

                player.pause()

                sendPlayerUpdate()
            }

            ACTION_TOGGLE -> {

                if (
                    player.isPlaying
                ) {

                    player.pause()

                } else {

                    player.play()
                }

                sendPlayerUpdate()
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

                sendPlayerUpdate()
            }

            ACTION_STOP -> {

                player.stop()

                sendPlayerUpdate()

                stopSelf()
            }
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

        if (
            url.isNullOrBlank()
        ) {
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

        val metadata =
            MediaMetadata.Builder()
                .setTitle(title)
                .setArtist(artist)
                .apply {

                    if (
                        cover.isNotBlank()
                    ) {

                        setArtworkUri(
                            android.net.Uri.parse(
                                cover
                            )
                        )
                    }
                }
                .build()

        val item =
            MediaItem.Builder()
                .setUri(url)
                .setMediaId(url)
                .setMediaMetadata(metadata)
                .build()

        player.setMediaItem(item)

        player.prepare()

        player.play()

        sendPlayerUpdate()
    }

    private fun seekPercent(
        percent: Int
    ) {

        val duration =
            player.duration

        if (
            duration <= 0
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
            100

        player.seekTo(position)

        sendPlayerUpdate()
    }

    private fun sendPlayerUpdate() {

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
                    player.duration
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
            )

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

        super.onTaskRemoved(
            rootIntent
        )
    }

    override fun onDestroy() {

        mediaSession?.release()

        mediaSession = null

        player.release()

        super.onDestroy()
    }
}
