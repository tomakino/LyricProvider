/*
 * Copyright 2026 Proify, Tomakino
 * Licensed under the Apache License, Version 2.0
 * http://www.apache.org/licenses/LICENSE-2.0
 */

@file:Suppress("unused")

package io.github.proify.lrckit

import io.github.proify.lyricon.lyric.model.LyricLine
import kotlinx.serialization.Serializable

@Serializable
data class LrcDocument(
    val metadata: Map<String, String> = emptyMap(),
    val lines: List<LyricLine> = emptyList()
) {

    /**
     * 应用全局时间偏移
     * @param offsetMs 偏移毫秒数（支持正负）
     * @return 偏移后的新文档
     */
    internal fun applyOffset(offsetMs: Long): LrcDocument {
        if (offsetMs == 0L) return this

        val newLines = lines.map { line ->
            val newBegin = (line.begin + offsetMs).coerceAtLeast(0L)
            val newEnd = newBegin + line.duration

            line.copy(
                begin = newBegin,
                end = newEnd,
                duration = line.duration
            )
        }
        return copy(lines = newLines)
    }
}