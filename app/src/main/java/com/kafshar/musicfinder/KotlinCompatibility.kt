package com.kafshar.musicfinder

import androidx.media3.common.Player

val Player.hasNextMediaItem: Boolean
    get() = hasNextMediaItem()

val Player.hasPreviousMediaItem: Boolean
    get() = hasPreviousMediaItem()

operator fun String.unaryPlus(): String = this
