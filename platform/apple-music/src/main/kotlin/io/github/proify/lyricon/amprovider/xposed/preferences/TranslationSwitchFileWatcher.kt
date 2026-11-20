/*
 * Copyright 2026 Proify, Tomakino
 * Licensed under the Apache License, Version 2.0
 * http://www.apache.org/licenses/LICENSE-2.0
 */

package io.github.proify.lyricon.amprovider.xposed.preferences

import android.os.FileObserver
import android.os.Handler
import android.os.Looper
import java.io.File
import java.util.concurrent.atomic.AtomicBoolean

/** 监听单个文件的替换事件，变化后重读并回调。 */
internal abstract class TranslationSwitchFileWatcher(
    protected val directory: File,
    protected val fileName: String,
    private val onChanged: (Boolean) -> Unit,
) : TranslationSwitch {

    private val mainHandler = Handler(Looper.getMainLooper())
    private val reloadPending = AtomicBoolean(false)
    private var observer: FileObserver? = null
    private var retries = 0

    @Volatile
    private var current: Boolean? = null

    override val selected: Boolean
        get() {
            current?.let { return it }
            val value = read() ?: fallback
            current = value
            return value
        }

    /** 读取当前值；无法读取返回 null。 */
    protected abstract fun read(): Boolean?

    /** 首次读取失败时使用的值。 */
    protected abstract val fallback: Boolean

    override fun install() {
        if (observer != null) return
        if (!directory.isDirectory) {
            if (retries++ < MAX_RETRIES) {
                mainHandler.postDelayed({ install() }, RETRY_DELAY_MS)
            }
            return
        }

        @Suppress("DEPRECATION")
        val created = object : FileObserver(directory.path) {
            override fun onEvent(event: Int, path: String?) {
                if (path != fileName) return
                val changed = event and (
                        FileObserver.MODIFY or FileObserver.CLOSE_WRITE or
                                FileObserver.MOVED_TO or FileObserver.CREATE or FileObserver.DELETE
                        ) != 0
                if (changed) scheduleReload()
            }
        }
        created.startWatching()
        observer = created
        current = read()
    }

    private fun scheduleReload() {
        if (!reloadPending.compareAndSet(false, true)) return

        mainHandler.postDelayed({
            reloadPending.set(false)
            val value = read() ?: return@postDelayed
            if (value == current) return@postDelayed
            current = value
            onChanged(value)
        }, DEBOUNCE_MS)
    }

    private companion object {
        const val DEBOUNCE_MS = 120L
        const val RETRY_DELAY_MS = 2_000L
        const val MAX_RETRIES = 5
    }
}
