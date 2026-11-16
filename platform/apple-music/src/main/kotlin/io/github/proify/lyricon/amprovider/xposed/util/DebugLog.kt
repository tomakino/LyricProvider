/*
 * Copyright 2026 Proify, Tomakino
 * Licensed under the Apache License, Version 2.0
 * http://www.apache.org/licenses/LICENSE-2.0
 */

package io.github.proify.lyricon.amprovider.xposed.util

import android.content.Context
import com.highcapable.yukihookapi.hook.log.YLog
import java.io.File
import java.text.SimpleDateFormat
import java.util.Date
import java.util.Locale

/** 诊断日志：写文件，便于无 logcat 时排查。 */
object DebugLog {

    private const val TAG = "AMProvider"
    private const val FILE_NAME = "lyricon_debug.log"
    private const val MAX_SIZE = 256 * 1024L

    private var file: File? = null
    private val timeFormat = SimpleDateFormat("HH:mm:ss.SSS", Locale.US)

    fun initialize(context: Context) {
        if (file != null) return
        file = File(context.filesDir, FILE_NAME).apply {
            if (exists() && length() > MAX_SIZE) delete()
        }
        log("DebugLog", "initialized -> ${file?.absolutePath}")
    }

    fun log(scope: String, message: String) {
        YLog.debug(tag = TAG, msg = "[$scope] $message")
        runCatching { file?.appendText("${timeFormat.format(Date())} [$scope] $message\n") }
    }

    fun error(scope: String, message: String, throwable: Throwable? = null) {
        val detail = throwable?.let { "${it::class.java.simpleName}: ${it.message}" } ?: ""
        YLog.error(tag = TAG, msg = "[$scope] $message $detail", e = throwable)
        val line = "${timeFormat.format(Date())} [$scope] ERROR $message $detail\n"
        runCatching { file?.appendText(line) }
    }
}
