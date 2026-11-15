/*
 * Copyright 2026 Proify, Tomakino
 * Licensed under the Apache License, Version 2.0
 * http://www.apache.org/licenses/LICENSE-2.0
 */

@file:Suppress("unused")

package io.github.proify.extensions.android

import android.content.BroadcastReceiver
import android.content.Context
import android.content.Intent
import android.content.IntentFilter
import android.util.Log
import androidx.core.content.ContextCompat

object ScreenStateMonitor {
    private const val TAG = "ScreenStateMonitor"

    private val listeners = mutableSetOf<ScreenStateListener>()
    private var appContext: Context? = null
    private var receiver: BroadcastReceiver? = null

    @Volatile
    var state: ScreenState = ScreenState.UNKNOWN
        private set

    enum class ScreenState {
        UNKNOWN,
        ON,
        OFF
    }

    interface ScreenStateListener {
        fun onScreenOn()
        fun onScreenOff()
        fun onScreenUnlocked()
    }

    fun initialize(context: Context) {
        if (appContext != null) return
        appContext = context.applicationContext
        registerReceiver()
    }

    fun isInitialized(): Boolean = appContext != null

    fun addListener(listener: ScreenStateListener) {
        listeners += listener
    }

    fun removeListener(listener: ScreenStateListener) {
        listeners -= listener
    }

    fun release() {
        val ctx = appContext ?: return
        runCatching {
            receiver?.let { ctx.unregisterReceiver(it) }
        }
        listeners.clear()
        receiver = null
        appContext = null
    }

    private fun registerReceiver() {
        val ctx = appContext ?: return

        val filter = IntentFilter().apply {
            addAction(Intent.ACTION_SCREEN_ON)
            addAction(Intent.ACTION_SCREEN_OFF)
            addAction(Intent.ACTION_USER_PRESENT)
        }

        receiver = ScreenReceiver()
        ContextCompat.registerReceiver(
            ctx,
            receiver,
            filter,
            ContextCompat.RECEIVER_EXPORTED
        )
    }

    private class ScreenReceiver : BroadcastReceiver() {
        override fun onReceive(context: Context?, intent: Intent?) {
            when (intent?.action) {
                Intent.ACTION_SCREEN_ON -> onScreenOn()
                Intent.ACTION_SCREEN_OFF -> onScreenOff()
                Intent.ACTION_USER_PRESENT -> onScreenUnlocked()
            }
        }
    }

    private fun onScreenOn() {
        state = ScreenState.ON
        dispatch { it.onScreenOn() }
    }

    private fun onScreenOff() {
        state = ScreenState.OFF
        dispatch { it.onScreenOff() }
    }

    private fun onScreenUnlocked() {
        state = ScreenState.ON
        dispatch { it.onScreenUnlocked() }
    }

    private inline fun dispatch(action: (ScreenStateListener) -> Unit) {
        listeners.forEach {
            try {
                action(it)
            } catch (e: Exception) {
                Log.e(TAG, "Listener dispatch error", e)
            }
        }
    }
}