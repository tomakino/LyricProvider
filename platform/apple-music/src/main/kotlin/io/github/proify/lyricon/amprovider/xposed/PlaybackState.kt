/*
 * Copyright 2026 Proify, Tomakino
 * Licensed under the Apache License, Version 2.0
 * http://www.apache.org/licenses/LICENSE-2.0
 */

package io.github.proify.lyricon.amprovider.xposed

/** Apple Music 播放器内的状态枚举。 */
enum class PlaybackState(private val value: Int) {
    UNKNOWN(-1),
    STOPPED(0),
    PLAYING(1),
    PAUSED(2);

    companion object {
        private val byValue = entries.associateBy { it.value }

        fun from(value: Int): PlaybackState = byValue[value] ?: UNKNOWN
    }
}
