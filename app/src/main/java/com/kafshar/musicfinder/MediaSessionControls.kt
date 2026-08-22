package com.kafshar.musicfinder

import android.content.Context
import android.media.AudioManager
import android.os.Bundle
import androidx.media3.common.Player
import androidx.media3.common.util.UnstableApi
import androidx.media3.session.CommandButton
import androidx.media3.session.MediaSession
import androidx.media3.session.SessionCommand
import androidx.media3.session.SessionResult
import com.google.common.util.concurrent.Futures
import com.google.common.util.concurrent.ListenableFuture

@OptIn(UnstableApi::class)
object MediaSessionControls {
    private const val VOLUME_DOWN = "com.kafshar.musicfinder.volume_down"
    private const val VOLUME_UP = "com.kafshar.musicfinder.volume_up"
    private const val MUTE = "com.kafshar.musicfinder.mute"

    private fun command(action: String) = SessionCommand(action, Bundle.EMPTY)

    fun layout(): List<CommandButton> = listOf(
        CommandButton.Builder(CommandButton.ICON_REWIND).setPlayerCommand(Player.COMMAND_SEEK_BACK).setDisplayName("۱۰ ثانیه عقب").build(),
        CommandButton.Builder(CommandButton.ICON_SKIP_BACK).setPlayerCommand(Player.COMMAND_SEEK_TO_PREVIOUS_MEDIA_ITEM).setDisplayName("آهنگ قبلی").build(),
        CommandButton.Builder(CommandButton.ICON_PLAY).setPlayerCommand(Player.COMMAND_PLAY_PAUSE).setDisplayName("پخش / توقف").build(),
        CommandButton.Builder(CommandButton.ICON_SKIP_FORWARD).setPlayerCommand(Player.COMMAND_SEEK_TO_NEXT_MEDIA_ITEM).setDisplayName("آهنگ بعدی").build(),
        CommandButton.Builder(CommandButton.ICON_FAST_FORWARD).setPlayerCommand(Player.COMMAND_SEEK_FORWARD).setDisplayName("۱۰ ثانیه جلو").build(),
        CommandButton.Builder(CommandButton.ICON_VOLUME_DOWN).setSessionCommand(command(VOLUME_DOWN)).setDisplayName("کم کردن صدای گوشی").build(),
        CommandButton.Builder(CommandButton.ICON_VOLUME_OFF).setSessionCommand(command(MUTE)).setDisplayName("بی‌صدا / صدای گوشی").build(),
        CommandButton.Builder(CommandButton.ICON_VOLUME_UP).setSessionCommand(command(VOLUME_UP)).setDisplayName("زیاد کردن صدای گوشی").build(),
        CommandButton.Builder(CommandButton.ICON_STOP).setPlayerCommand(Player.COMMAND_STOP).setDisplayName("خروج از پخش").build()
    )

    fun callback(): MediaSession.Callback = object : MediaSession.Callback {
        override fun onConnect(session: MediaSession, controller: MediaSession.ControllerInfo): MediaSession.ConnectionResult {
            val commands = MediaSession.ConnectionResult.DEFAULT_SESSION_COMMANDS.buildUpon()
                .add(command(VOLUME_DOWN)).add(command(VOLUME_UP)).add(command(MUTE)).build()
            return MediaSession.ConnectionResult.AcceptedResultBuilder(session)
                .setAvailableSessionCommands(commands).build()
        }

        override fun onCustomCommand(
            session: MediaSession,
            controller: MediaSession.ControllerInfo,
            customCommand: SessionCommand,
            args: Bundle
        ): ListenableFuture<SessionResult> {
            val audioManager = session.player.context.getSystemService(Context.AUDIO_SERVICE) as? AudioManager
            if (audioManager != null) {
                when (customCommand.customAction) {
                    VOLUME_DOWN -> audioManager.adjustStreamVolume(AudioManager.STREAM_MUSIC, AudioManager.ADJUST_LOWER, AudioManager.FLAG_SHOW_UI)
                    VOLUME_UP -> audioManager.adjustStreamVolume(AudioManager.STREAM_MUSIC, AudioManager.ADJUST_RAISE, AudioManager.FLAG_SHOW_UI)
                    MUTE -> if (android.os.Build.VERSION.SDK_INT >= android.os.Build.VERSION_CODES.M) {
                        audioManager.adjustStreamVolume(AudioManager.STREAM_MUSIC, AudioManager.ADJUST_TOGGLE_MUTE, AudioManager.FLAG_SHOW_UI)
                    } else {
                        audioManager.setStreamVolume(AudioManager.STREAM_MUSIC, 0, AudioManager.FLAG_SHOW_UI)
                    }
                }
            }
            return Futures.immediateFuture(SessionResult(SessionResult.RESULT_SUCCESS))
        }
    }
}
