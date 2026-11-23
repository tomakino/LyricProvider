/*
 * Copyright 2026 Proify, Tomakino
 * Licensed under the Apache License, Version 2.0
 * http://www.apache.org/licenses/LICENSE-2.0
 */

package io.github.proify.lyricon.amprovider.xposed

import io.github.proify.extensions.android.Reflect
import io.github.proify.lyricon.provider.ProviderConstants
import kotlinx.coroutines.CoroutineScope
import kotlinx.coroutines.Dispatchers
import kotlinx.coroutines.Job
import kotlinx.coroutines.SupervisorJob
import kotlinx.coroutines.delay
import kotlinx.coroutines.isActive
import kotlinx.coroutines.launch
import kotlin.time.Duration.Companion.milliseconds

/**
 * 播放进度同步。
 *
 * 在播放期间按 [ProviderConstants.DEFAULT_POSITION_UPDATE_INTERVAL] 的间隔
 * 读取 ExoPlayer 位置并回调。
 */
internal class ProgressSync(
    private val callback: (Long) -> Unit,
) {
    private val scope by lazy { CoroutineScope(Dispatchers.Default + SupervisorJob()) }

    @Volatile
    private var playing = false

    private var job: Job? = null

    /** ExoPlayer 实例，由 hook 在构造完成后写入。 */
    var exoPlayerInstance: Any? = null

    fun onPlaybackChanged(isPlaying: Boolean) {
        if (playing == isPlaying) return
        playing = isPlaying
        if (isPlaying) startLoop() else stopLoop()
    }

    fun onScreenOn() {
        if (playing) startLoop()
    }

    fun onScreenOff() {
        stopLoop()
    }

    fun onScreenUnlocked() {
        if (playing) startLoop()
    }

    private fun startLoop() {
        if (job?.isActive == true) return
        job = scope.launch {
            while (isActive && playing) {
                callback(readPosition())
                delay(ProviderConstants.DEFAULT_POSITION_UPDATE_INTERVAL.milliseconds)
            }
        }
    }

    private fun stopLoop() {
        job?.cancel()
        job = null
    }

    /** 当前播放位置，读取失败时为 0。 */
    fun readPosition(): Long =
        Reflect.callOrNull(exoPlayerInstance, "getCurrentPosition") as? Long ?: 0

}
