/*
 * Copyright 2026 Proify, Tomakino
 * Licensed under the Apache License, Version 2.0
 * http://www.apache.org/licenses/LICENSE-2.0
 */

package io.github.proify.amkit.parser

import kotlin.time.Duration
import kotlin.time.Duration.Companion.milliseconds
import kotlin.time.Duration.Companion.seconds

/**
 * TTML 时间码解析。
 *
 * Apple 的方言存在三种写法：
 *  - **无后缀秒值**（Apple 特有，非 TTML 标准）：`15.525`
 *  - 标准秒值：`12.3s`
 *  - 时钟格式：`MM:SS.fff`、`HH:MM:SS.fff`，小数位 0~3 位
 *
 * 另外支持标准毫秒值 `1500ms`（Apple 不用，社区 TTML 常见）。
 * `f`（帧）与 `t`（tick）无法在没有帧率的前提下解析，按「无法识别」返回 `null`。
 */
internal object TtmlTimeCode {

    /** 解析时间码，无法识别时返回 `null`。 */
    fun parseOrNull(rawValue: String?): Duration? {
        val text = rawValue?.trim().orEmpty()
        if (text.isEmpty()) return null
        return runCatching { parse(text) }.getOrNull()
    }

    /**
     * 解析时间码。
     *
     * @throws IllegalArgumentException 时间码格式非法
     */
    fun parse(text: String): Duration {
        val trimmed = text.trim()
        require(trimmed.isNotEmpty()) { "时间码不能为空" }

        // 毫秒值：1500ms —— Apple 不使用，社区 TTML 常见。必须放在秒值分支**之前**，
        // 否则 "1500ms" 会先被 endsWith("s") 命中，解析成 1500 秒。
        if (trimmed.endsWith("ms", ignoreCase = true) && ':' !in trimmed) {
            return trimmed.dropLast(2).trim().toDouble().milliseconds
        }

        // 标准秒值：12.3s / 12s
        if (trimmed.endsWith("s", ignoreCase = true) && ':' !in trimmed) {
            return trimmed.dropLast(1).trim().toDouble().seconds
        }

        // 时钟格式：MM:SS.fff 或 HH:MM:SS.fff
        if (':' in trimmed) {
            val parts = trimmed.split(':')
            require(parts.size == 2 || parts.size == 3) { "无法识别的时间码: $text" }
            val values = parts.map { it.trim().toDouble() }
            val totalSeconds = when (values.size) {
                2 -> values[0] * SECONDS_PER_MINUTE + values[1]
                else -> values[0] * SECONDS_PER_HOUR +
                        values[1] * SECONDS_PER_MINUTE +
                        values[2]
            }
            return totalSeconds.seconds
        }

        // 帧（f）与 tick（t）需要帧率 / tick 率才能换算，按无法识别处理。
        // 不能落到 toDouble：Kotlin 会接受 "1500f" 这类 Java 浮点后缀，误解析成 1500 秒。
        if (trimmed.endsWith("f", ignoreCase = true) || trimmed.endsWith("t", ignoreCase = true)) {
            throw IllegalArgumentException("无法识别的时间码: $text")
        }

        // Apple 无后缀秒值
        return trimmed.toDouble().seconds
    }

    private const val SECONDS_PER_MINUTE = 60.0
    private const val SECONDS_PER_HOUR = 3600.0
}
