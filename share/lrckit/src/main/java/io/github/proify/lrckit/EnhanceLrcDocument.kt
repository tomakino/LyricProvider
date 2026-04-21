/*
 * Copyright 2026 Proify, Tomakino
 * Licensed under the Apache License, Version 2.0
 * http://www.apache.org/licenses/LICENSE-2.0
 */

package io.github.proify.lrckit

import io.github.proify.lyricon.lyric.model.RichLyricLine
import kotlinx.serialization.Serializable

@Serializable
data class EnhanceLrcDocument(
    val metadata: Map<String, String> = emptyMap(),
    val lines: List<RichLyricLine> = emptyList(),
) {
    /**
     * 应用全局时间偏移。
     * @param offsetMs 偏移毫秒数
     * @return 新的 EnhanceLrcDocument
     */
    internal fun applyOffset(offsetMs: Long): EnhanceLrcDocument {
        val newLines = lines.map { line ->
            val newBegin = line.begin + offsetMs
            val newEnd = newBegin + line.duration
            val newDuration = line.duration
            line.copy(begin = newBegin, end = newEnd, duration = newDuration)
        }
        return EnhanceLrcDocument(metadata, newLines)
    }
}