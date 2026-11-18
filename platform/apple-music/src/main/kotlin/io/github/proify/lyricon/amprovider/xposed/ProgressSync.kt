/*
 * Copyright 2026 Proify, Tomakino
 * Licensed under the Apache License, Version 2.0
 * http://www.apache.org/licenses/LICENSE-2.0
 */

package io.github.proify.lyricon.amprovider.xposed

import io.github.proify.lyricon.provider.ProviderConstants
import io.github.proify.lyricon.provider.RemotePlayer
import kotlinx.coroutines.CoroutineScope
import kotlinx.coroutines.Job
import kotlinx.coroutines.delay
import kotlinx.coroutines.isActive
import kotlinx.coroutines.launch
import kotlin.time.Duration.Companion.milliseconds

/**
 * 播放进度同步。
 *
 * 屏幕熄灭时暂停轮询，亮屏或解锁后按 [playing] 恢复。
 */
internal class ProgressSync(
    private val scope: CoroutineScope,
    private val state: SessionState,
    private val playerProvider: () -> RemotePlayer?,
) {

    @Volatile
    private var playing = false

    private var job: Job? = null

    val isPlaying: Boolean
        get() = playing

    fun onPlaybackChanged(isPlaying: Boolean) {
        if (playing == isPlaying) return
        playing = isPlaying
        playerProvider()?.setPlaybackState(isPlaying)
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
                playerProvider()?.setPosition(state.readPosition())
                delay(ProviderConstants.DEFAULT_POSITION_UPDATE_INTERVAL.milliseconds)
            }
        }
    }

    private fun stopLoop() {
        job?.cancel()
        job = null
    }
}
