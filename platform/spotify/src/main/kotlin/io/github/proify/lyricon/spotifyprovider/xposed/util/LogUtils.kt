/*
 * Copyright 2026 Proify, Tomakino
 * Licensed under the Apache License, Version 2.0
 * http://www.apache.org/licenses/LICENSE-2.0
 */

package io.github.proify.lyricon.spotifyprovider.xposed.util

import com.highcapable.yukihookapi.hook.log.YLog

/** 日志工具。 */
object LogUtils {

    /** 执行 [block]，记录其耗时并返回结果。 */
    fun <T> measureAndLog(tag: String, operation: String, block: () -> T): T {
        val startTime = System.currentTimeMillis()
        val result = block()
        val duration = System.currentTimeMillis() - startTime
        YLog.debug(tag = tag, msg = "$operation 耗时: ${duration}ms")
        return result
    }
}
