@file:Suppress("unused")

package io.github.proify.extensions.android

import android.content.BroadcastReceiver
import android.content.Context
import android.content.Intent
import android.content.IntentFilter
import android.os.PowerManager
import android.util.Log
import androidx.core.content.ContextCompat
import java.util.concurrent.CopyOnWriteArraySet

object ScreenStateMonitor {
    private const val TAG = "ScreenStateMonitor"

    private val listeners = CopyOnWriteArraySet<ScreenStateListener>()

    @Volatile
    private var appContext: Context? = null

    @Volatile
    private var receiver: BroadcastReceiver? = null

    @Volatile
    var state: ScreenState = ScreenState.UNKNOWN
        private set

    enum class ScreenState { UNKNOWN, ON, OFF }

    interface ScreenStateListener {
        fun onScreenOn()
        fun onScreenOff()
        fun onScreenUnlocked()
    }

    @Synchronized
    fun initialize(context: Context) {
        if (appContext != null) return
        val ctx = context.applicationContext
        state = queryCurrentState(ctx)
        if (registerReceiver(ctx)) {
            appContext = ctx
        }
    }

    fun isInitialized(): Boolean = appContext != null

    fun addListener(listener: ScreenStateListener) {
        listeners += listener
    }

    fun removeListener(listener: ScreenStateListener) {
        listeners -= listener
    }

    @Synchronized
    fun release() {
        val ctx = appContext ?: return
        runCatching { receiver?.let { ctx.unregisterReceiver(it) } }
            .onFailure { Log.w(TAG, "unregisterReceiver failed", it) }
        receiver = null
        appContext = null
        listeners.clear()
        state = ScreenState.UNKNOWN
    }

    private fun queryCurrentState(context: Context): ScreenState {
        return runCatching {
            val pm = context.getSystemService(Context.POWER_SERVICE) as? PowerManager
            if (pm?.isInteractive == true) ScreenState.ON else ScreenState.OFF
        }.getOrDefault(ScreenState.UNKNOWN)
    }

    private fun registerReceiver(ctx: Context): Boolean {
        val filter = IntentFilter().apply {
            addAction(Intent.ACTION_SCREEN_ON)
            addAction(Intent.ACTION_SCREEN_OFF)
            addAction(Intent.ACTION_USER_PRESENT)
        }
        val r = ScreenReceiver()
        return runCatching {
            ContextCompat.registerReceiver(ctx, r, filter, ContextCompat.RECEIVER_NOT_EXPORTED)
        }.onSuccess {
            receiver = r
        }.onFailure {
            Log.e(TAG, "registerReceiver failed", it)
        }.isSuccess
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
        if (state == ScreenState.ON) return
        state = ScreenState.ON
        dispatch { it.onScreenOn() }
    }

    private fun onScreenOff() {
        if (state == ScreenState.OFF) return
        state = ScreenState.OFF
        dispatch { it.onScreenOff() }
    }

    private fun onScreenUnlocked() {
        state = ScreenState.ON
        dispatch { it.onScreenUnlocked() }
    }

    private inline fun dispatch(action: (ScreenStateListener) -> Unit) {
        for (listener in listeners) {
            try {
                action(listener)
            } catch (e: Exception) {
                Log.e(TAG, "Listener dispatch error", e)
            }
        }
    }
}