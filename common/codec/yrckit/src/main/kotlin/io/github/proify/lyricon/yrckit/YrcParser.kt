/*
 * Copyright 2026 Proify, Tomakino
 * Licensed under the Apache License, Version 2.0
 * http://www.apache.org/licenses/LICENSE-2.0
 */

package io.github.proify.lyricon.yrckit

import io.github.proify.lyricon.lyric.model.LyricLine
import io.github.proify.lyricon.lyric.model.LyricWord
import java.util.regex.Pattern

/**
 * YRC 解析器。
 * 解析逐行时间头与逐字时间轴；同一时间点只保留首条。
 */
object YrcParser {

    /** 行时间头：开始时间与持续时间，毫秒。 */
    private val YRC_LINE_HEADER_REGEX = Pattern.compile("\\[(\\d+),(\\d+)]")

    /** 逐字标签：开始时间、持续时间、参数与紧随其后的文本。 */
    private val YRC_SYLLABLE_REGEX = Pattern.compile("\\((\\d+),(\\d+),\\d+\\)([^(]*)")

    /**
     * 解析 YRC 文本。
     * @param raw 原始文本；null 或空白返回空列表。
     * @return 按开始时间排序的歌词行，同一时间点保留首条。
     */
    fun parse(raw: String?): List<LyricLine> {
        if (raw.isNullOrBlank()) return emptyList()

        val entries = ArrayList<LyricLine>((raw.length / 128).coerceIn(4, 16_384))
        for (source in raw.lineSequence()) {
            val line = source.trim()
            if (line.isEmpty() || line[0] == '{') continue
            parseLine(line)?.let { entries.add(it) }
        }
        if (entries.isEmpty()) return emptyList()

        entries.sortBy { it.begin }
        return distinctByBegin(entries)
    }

    /**
     * 解析单行歌词。
     * @param line 已裁剪首尾空白的行。
     * @return 歌词行；不含时间头时返回 null。
     */
    private fun parseLine(line: String): LyricLine? {
        val header = YRC_LINE_HEADER_REGEX.matcher(line)
        if (!header.find()) return null

        val lineStart = header.group(1)?.toLongOrNull() ?: 0L
        val lineDuration = header.group(2)?.toLongOrNull() ?: 0L
        val words = parseWords(line, header.end())

        return LyricLine(
            begin = lineStart,
            end = lineStart + lineDuration,
            duration = lineDuration,
            text = words.joinToString("") { it.text.orEmpty() },
            words = words,
        )
    }

    /**
     * 解析逐字标签。
     * @param line 完整歌词行。
     * @param contentStart 逐字内容的起始下标。
     * @return 按开始时间排序且跳过空文本的词列表。
     */
    private fun parseWords(line: String, contentStart: Int): List<LyricWord> {
        if (line.indexOf('(', contentStart) < 0) return emptyList()

        val matcher = YRC_SYLLABLE_REGEX.matcher(line)
        matcher.region(contentStart, line.length)

        val words = ArrayList<LyricWord>(((line.length - contentStart) / 16).coerceAtLeast(4))
        while (matcher.find()) {
            val text = matcher.group(3) ?: ""
            if (text.isEmpty()) continue

            val start = matcher.group(1)?.toLongOrNull() ?: 0L
            val duration = matcher.group(2)?.toLongOrNull() ?: 0L
            words.add(
                LyricWord(
                    begin = start,
                    end = start + duration,
                    duration = duration,
                    text = text,
                )
            )
        }

        if (words.size < 2) return words
        for (index in 1 until words.size) {
            if (words[index].begin < words[index - 1].begin) {
                return words.sortedBy { it.begin }
            }
        }
        return words
    }

    /**
     * 按开始时间去重，保留每组首条。
     * @param sorted 已按开始时间稳定排序的行。
     * @return 每个开始时间只保留首条的新列表。
     */
    private fun distinctByBegin(sorted: List<LyricLine>): List<LyricLine> {
        if (sorted.size < 2) return sorted

        val result = ArrayList<LyricLine>(sorted.size)
        var previousBegin = Long.MIN_VALUE
        for (line in sorted) {
            if (line.begin == previousBegin) continue
            result.add(line)
            previousBegin = line.begin
        }
        return result
    }
}
