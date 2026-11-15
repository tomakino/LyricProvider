/*
 * Copyright 2026 Proify, Tomakino
 * Licensed under the Apache License, Version 2.0
 * http://www.apache.org/licenses/LICENSE-2.0
 */

package io.github.proify.lrckit

import io.github.proify.lyricon.lyric.model.LyricWord
import io.github.proify.lyricon.lyric.model.RichLyricLine
import kotlinx.serialization.Serializable

@Serializable
data class EnhanceLrcDocument(
    val metadata: Map<String, String> = emptyMap(),
    val lines: List<RichLyricLine> = emptyList(),
) {
    /**
     * 应用全局时间偏移。
     *
     * 行时间与逐字时间轴必须同步平移：只移动行会让卡拉 OK 高亮与行时间窗错位，
     * 表现为逐字跑到行开始之前或行结束之后。
     *
     * @param offsetMs 偏移毫秒数
     * @return 新的 EnhanceLrcDocument
     */
    internal fun applyOffset(offsetMs: Long): EnhanceLrcDocument {
        if (offsetMs == 0L) return this

        val newLines = lines.map { line ->
            val newBegin = line.begin + offsetMs
            val newEnd = newBegin + line.duration
            val newDuration = line.duration

            line.copy(
                begin = newBegin,
                end = newEnd,
                duration = newDuration,
                words = line.words?.shiftBy(offsetMs),
                secondaryWords = line.secondaryWords?.shiftBy(offsetMs),
                translationWords = line.translationWords?.shiftBy(offsetMs)
            )
        }
        return EnhanceLrcDocument(metadata, newLines)
    }
}

/** 逐字时间轴整体平移，各词的 duration 保持不变。 */
private fun List<LyricWord>.shiftBy(offsetMs: Long): List<LyricWord> = map { word ->
    word.copy(begin = word.begin + offsetMs, end = word.end + offsetMs)
}
