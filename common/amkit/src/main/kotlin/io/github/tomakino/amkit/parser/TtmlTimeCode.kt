/*
 * Copyright 2026 Proify, Tomakino
 * Licensed under the Apache License, Version 2.0
 * http://www.apache.org/licenses/LICENSE-2.0
 */

package io.github.tomakino.amkit.parser

import kotlin.time.Duration
import kotlin.time.Duration.Companion.milliseconds
import kotlin.time.Duration.Companion.seconds

/**
 * TTML 时间码解析。
 *
 * 实测 Apple 的方言中同时存在三种写法：
 *  - **无后缀秒值**（Apple 特有，非 TTML 标准）：`15.525`
 *  - 标准秒值：`12.3s`
 *  - 时钟格式：`MM:SS.fff`、`HH:MM:SS.fff`，小数位 0~3 位
 *
 * 另外顺手支持标准毫秒值 `1500ms`（Apple 不用，社区 TTML 常见）。
 * `f`（帧）与 `t`（tick）无法在没有帧率的前提下解析，因此不支持 ——
 * 遇到时按「无法识别」降级为 `null`，而不是猜一个值。
 *
 * 之所以统一返回 [Duration] 而不是毫秒 `Long`，是为了让调用方
 * 无法把「秒」和「毫秒」混用（类型系统层面消除一整类错误）。
 */
internal object TtmlTimeCode {

    /**
     * 宽容解析：无法识别时返回 `null`。
     *
     * 歌词文件属于外部输入，单个时间戳写坏不应让整份文档解析失败。
     */
    fun parseOrNull(rawValue: String?): Duration? {
        val text = rawValue?.trim().orEmpty()
        if (text.isEmpty()) return null
        return runCatching { parse(text) }.getOrNull()
    }

    /**
     * 严格解析：无法识别时抛出 [IllegalArgumentException]。
     *
     * @throws IllegalArgumentException 时间码格式非法。
     */
    fun parse(text: String): Duration {
        val trimmed = text.trim()
        require(trimmed.isNotEmpty()) { "时间码不能为空" }

        // 毫秒值：1500ms —— Apple 实测不用，但社区 TTML 常见。必须放在秒值分支**之前**，
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

        // Apple 无后缀秒值
        return trimmed.toDouble().seconds
    }

    private const val SECONDS_PER_MINUTE = 60.0
    private const val SECONDS_PER_HOUR = 3600.0
}
