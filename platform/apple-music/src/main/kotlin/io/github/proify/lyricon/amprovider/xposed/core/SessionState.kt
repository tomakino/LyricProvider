/*
 * Copyright 2026 Proify, Tomakino
 * Licensed under the Apache License, Version 2.0
 * http://www.apache.org/licenses/LICENSE-2.0
 */

package io.github.proify.lyricon.amprovider.xposed.core

import java.lang.reflect.Method

/** 宿主播放器与媒体会话的反射引用。 */
internal class SessionState {

    @Volatile
    var mediaSession: Any? = null

    @Volatile
    var exoPlayer: Any? = null

    @Volatile
    var exoPlayerPosition: Method? = null

    @Volatile
    var controller: Any? = null

    @Volatile
    var controllerPosition: Method? = null

    /** 依次尝试 ExoMediaPlayer、LocalMediaPlayerController。 */
    fun readPosition(): Long {
        readPosition(exoPlayer, exoPlayerPosition)?.let { return it }
        return readPosition(controller, controllerPosition) ?: 0L
    }

    private fun readPosition(instance: Any?, method: Method?): Long? {
        if (instance == null || method == null) return null
        return runCatching { method.invoke(instance) as? Long }.getOrNull()
    }
}
