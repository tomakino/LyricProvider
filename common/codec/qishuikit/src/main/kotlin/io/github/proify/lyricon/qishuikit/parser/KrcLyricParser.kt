/*
 * Copyright 2026 Proify, Tomakino
 * Licensed under the Apache License, Version 2.0
 * http://www.apache.org/licenses/LICENSE-2.0
 */

package io.github.proify.lyricon.qishuikit.parser

import io.github.proify.lyricon.lyric.model.LyricLine
import io.github.proify.lyricon.lyric.model.LyricWord

/**
 * 汽水 QRC 解析器。
 *
 * 输入为接口返回的已解密明文，格式为 `[行开始,行时长]<字偏移,字时长,参数>字...`。
 * 标签位于字之前，逐字时间以行开始为基准。
 */
object KrcLyricParser {

    private val lineTimeRegex = """\[\s*(\d+)\s*,\s*(\d+)\s*]""".toRegex()

    private val tagRegex = """<\s*(\d+)\s*,\s*(\d+)\s*,\s*(\d+)\s*>""".toRegex()

    /**
     * 解析 QRC 文本。
     * @param content 已解密的 QRC 原文。
     * @param inferUntaggedTime 无标签字符是否以上一字结束时间作为开始时间。
     * @return 按行顺序排列的歌词行。
     */
    fun parse(content: String?, inferUntaggedTime: Boolean = true): List<LyricLine> {
        if (content.isNullOrBlank()) return emptyList()

        return content.lineSequence()
            .filter { it.isNotBlank() }
            .mapNotNull { parseLine(it, inferUntaggedTime) }
            .toList()
    }

    /**
     * 解析单行歌词。
     * @param rawLine 原始行文本。
     * @param inferUntaggedTime 无标签字符是否推断开始时间。
     * @return 解析后的歌词行；缺少行时间或正文时为 null。
     */
    private fun parseLine(rawLine: String, inferUntaggedTime: Boolean): LyricLine? {
        val time = lineTimeRegex.find(rawLine) ?: return null
        val lineStart = time.groupValues[1].toLongOrNull() ?: return null
        val lineDuration = time.groupValues[2].toLongOrNull() ?: return null

        val body = rawLine.substring(time.range.last + 1)
        if (body.isBlank()) return null

        val tags = tagRegex.findAll(body).map { match ->
            Tag(
                start = match.range.first,
                endExclusive = match.range.last + 1,
                offset = match.groupValues[1].toLongOrNull() ?: 0L,
                duration = match.groupValues[2].toLongOrNull() ?: 0L
            )
        }.toList()

        val words = ArrayList<LyricWord>(body.length)
        val text = StringBuilder(body.length)

        var tagIndex = 0
        var pending: Tag? = null
        var lastEnd: Long? = null
        var index = 0

        while (index < body.length) {
            val nextTag = tags.getOrNull(tagIndex)
            if (nextTag != null && nextTag.start == index) {
                pending = nextTag
                index = nextTag.endExclusive
                tagIndex++
                continue
            }

            val ch = body[index]
            val tag = pending
            val word = if (tag != null) {
                LyricWord(
                    begin = lineStart + tag.offset,
                    end = lineStart + tag.offset + tag.duration,
                    duration = tag.duration,
                    text = ch.toString()
                )
            } else {
                val begin = if (inferUntaggedTime) (lastEnd ?: lineStart) else 0L
                LyricWord(begin = begin, end = begin, duration = 0, text = ch.toString())
            }

            words.add(word)
            lastEnd = word.end
            pending = null
            text.append(ch)
            index++
        }

        return LyricLine(
            begin = lineStart,
            end = lineStart + lineDuration,
            duration = lineDuration,
            text = text.toString(),
            words = words
        )
    }

    /**
     * 逐字标签在正文中的位置与时间。
     * @property start 标签起始下标。
     * @property endExclusive 标签结束后的下标。
     * @property offset 相对行开始的偏移，毫秒。
     * @property duration 持续时长，毫秒。
     */
    private class Tag(
        val start: Int,
        val endExclusive: Int,
        val offset: Long,
        val duration: Long
    )
}
