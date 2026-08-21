package com.kafshar.musicfinder

import androidx.media3.common.Player
import androidx.media3.session.CommandButton

object MediaSessionControls {

    fun layout(): List<CommandButton> = listOf(
        CommandButton.Builder(CommandButton.ICON_REWIND)
            .setPlayerCommand(Player.COMMAND_SEEK_BACK)
            .setDisplayName("۱۰ ثانیه عقب")
            .build(),
        CommandButton.Builder(CommandButton.ICON_SKIP_BACK)
            .setPlayerCommand(Player.COMMAND_SEEK_TO_PREVIOUS_MEDIA_ITEM)
            .setDisplayName("آهنگ قبلی")
            .build(),
        CommandButton.Builder(CommandButton.ICON_PLAY)
            .setPlayerCommand(Player.COMMAND_PLAY_PAUSE)
            .setDisplayName("پخش / توقف")
            .build(),
        CommandButton.Builder(CommandButton.ICON_SKIP_FORWARD)
            .setPlayerCommand(Player.COMMAND_SEEK_TO_NEXT_MEDIA_ITEM)
            .setDisplayName("آهنگ بعدی")
            .build(),
        CommandButton.Builder(CommandButton.ICON_FAST_FORWARD)
            .setPlayerCommand(Player.COMMAND_SEEK_FORWARD)
            .setDisplayName("۱۰ ثانیه جلو")
            .build(),
        CommandButton.Builder(CommandButton.ICON_VOLUME_DOWN)
            .setPlayerCommand(Player.COMMAND_SET_VOLUME)
            .setDisplayName("کم کردن صدا")
            .build(),
        CommandButton.Builder(CommandButton.ICON_VOLUME_OFF)
            .setPlayerCommand(Player.COMMAND_SET_VOLUME)
            .setDisplayName("بی‌صدا / صدا")
            .build(),
        CommandButton.Builder(CommandButton.ICON_VOLUME_UP)
            .setPlayerCommand(Player.COMMAND_SET_VOLUME)
            .setDisplayName("زیاد کردن صدا")
            .build(),
        CommandButton.Builder(CommandButton.ICON_STOP)
            .setPlayerCommand(Player.COMMAND_STOP)
            .setDisplayName("خروج از پخش")
            .build()
    )
}
