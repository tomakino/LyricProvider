/*
 * Copyright 2026 Proify, Tomakino
 * Licensed under the Apache License, Version 2.0
 * http://www.apache.org/licenses/LICENSE-2.0
 */

@file:Suppress("unused")

package io.github.proify.lyricon.spotifyprovider.xposed.util

import com.highcapable.yukihookapi.hook.log.YLog

/**
 * 日志工具，提供结构化日志
 */
object LogUtils {

    /**
     * 性能监控日志
     */
    fun <T> measureAndLog(
        tag: String,
        operation: String,
        block: () -> T
    ): T {
        val startTime = System.currentTimeMillis()
        val result = block()
        val duration = System.currentTimeMillis() - startTime

        YLog.debug(tag = tag, msg = "$operation 耗时: ${duration}ms")

        return result
    }

    /**
     * 带上下文的错误日志
     */
    fun logError(
        tag: String,
        operation: String,
        context: Map<String, Any?> = emptyMap(),
        error: Throwable
    ) {
        val contextStr = context.entries.joinToString(", ") { "${it.key}=${it.value}" }
        YLog.error(
            tag = tag,
            msg = "$operation 失败 [$contextStr]: ${error.message}",
            e = error
        )
    }

    /**
     * 调试信息日志
     */
    fun logDebug(
        tag: String,
        operation: String,
        data: Map<String, Any?> = emptyMap()
    ) {
        val dataStr = data.entries.joinToString(", ") { "${it.key}=${it.value}" }
        YLog.debug(tag = tag, msg = "$operation [$dataStr]")
    }

    /**
     * 信息日志
     */
    fun logInfo(
        tag: String,
        operation: String,
        data: Map<String, Any?> = emptyMap()
    ) {
        val dataStr = data.entries.joinToString(", ") { "${it.key}=${it.value}" }
        YLog.info(tag = tag, msg = "$operation [$dataStr]")
    }
}