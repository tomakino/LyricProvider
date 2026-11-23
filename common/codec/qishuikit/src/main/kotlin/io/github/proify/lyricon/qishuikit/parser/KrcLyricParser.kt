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

    fun parse(content: String?, inferUntaggedTime: Boolean = true): List<LyricLine> {
        if (content.isNullOrBlank()) return emptyList()

        return content.lineSequence()
            .filter { it.isNotBlank() }
            .mapNotNull { parseLine(it, inferUntaggedTime) }
            .toList()
    }

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

    private class Tag(
        val start: Int,
        val endExclusive: Int,
        val offset: Long,
        val duration: Long
    )
}
