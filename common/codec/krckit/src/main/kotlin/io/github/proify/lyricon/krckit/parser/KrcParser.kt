/*
 * Copyright 2026 Proify, Tomakino
 * Licensed under the Apache License, Version 2.0
 * http://www.apache.org/licenses/LICENSE-2.0
 */

package io.github.proify.lyricon.krckit.parser

import io.github.proify.lyricon.lyric.model.LyricLine
import io.github.proify.lyricon.lyric.model.LyricWord
import java.util.regex.Pattern

/**
 * KRC 歌词解析器。
 * 按行解析行时间、元数据与逐字标签，单行异常不影响其他行。
 */
object KrcParser {

    /** 行标签：开始时间与持续时间，毫秒。 */
    private val LINE_PATTERN = Pattern.compile("""^\[(\d+)\s*,\s*(\d+)](.*)$""")

    /** 元数据起始标签，值可延伸到行尾。 */
    private val META_PATTERN = Pattern.compile("""^\[([a-zA-Z0-9_]+)\s*:(.*)$""")

    /** 逐字标签：开始偏移、持续时间与参数。 */
    private val WORD_TAG_PATTERN = Pattern.compile("""<(\d+)\s*,\s*(\d+)\s*,\s*(\d+)>""")

    /** 无逐字标签时用于清理残留标签。 */
    private val WORD_TAG_CLEANUP = Regex("""<\d+\s*,\s*\d+\s*,\s*\d+>""")

    /**
     * 解析 KRC 文本。
     * @param content 原始文本，可能为解密后的明文。
     * @return 元数据与按开始时间排序的歌词行。
     */
    fun parse(content: String?): KrcDocument {
        if (content.isNullOrBlank()) return KrcDocument(emptyMap(), emptyList())

        val metadata = mutableMapOf<String, String>()
        val lines = mutableListOf<LyricLine>()

        // 跨行元数据（如折行的 Base64）的收集状态
        var currentMetaKey: String? = null
        val currentMetaValue = StringBuilder()

        content.lineSequence().forEach { rawLine ->
            val line = rawLine.trim()
            if (line.isEmpty()) return@forEach

            val lineMatcher = LINE_PATTERN.matcher(line)
            if (lineMatcher.matches()) {
                currentMetaKey = null

                val lineBegin = lineMatcher.group(1).toLongOrNull() ?: 0L
                val lineDuration = lineMatcher.group(2).toLongOrNull() ?: 0L
                val lineBody = lineMatcher.group(3).trim()

                if (lineBody.isNotEmpty()) {
                    try {
                        lines.add(parseLineBody(lineBegin, lineDuration, lineBody))
                    } catch (e: Exception) {
                        e.printStackTrace()
                    }
                }
                return@forEach
            }

            val metaMatcher = META_PATTERN.matcher(line)
            if (metaMatcher.matches()) {
                val key = metaMatcher.group(1)
                // 纯数字键是畸形数据伪装成元数据，直接忽略
                if (key.all { it.isDigit() }) return@forEach

                var value = metaMatcher.group(2).trim()
                if (value.endsWith("]")) {
                    metadata[key] = value.dropLast(1).trim()
                    currentMetaKey = null
                } else {
                    currentMetaKey = key
                    currentMetaValue.clear()
                    currentMetaValue.append(value)
                }
                return@forEach
            }

            if (currentMetaKey != null) {
                if (line.endsWith("]")) {
                    currentMetaValue.append(line.dropLast(1).trim())
                    metadata[currentMetaKey] = currentMetaValue.toString()
                    currentMetaKey = null
                } else {
                    currentMetaValue.append(line)
                }
            }
        }

        return KrcDocument(metadata, lines.sortedBy { it.begin })
    }

    /**
     * 解析一行歌词正文。
     * @param lineBegin 行开始时间，毫秒。
     * @param lineDuration 行持续时间，毫秒。
     * @param body 剥离行标签后的正文。
     * @return 逐字数据与行文本。
     */
    private fun parseLineBody(lineBegin: Long, lineDuration: Long, body: String): LyricLine {
        val words = mutableListOf<LyricWord>()
        val textBuilder = StringBuilder()

        val wordMatcher = WORD_TAG_PATTERN.matcher(body)
        var previousTagEnd = -1
        var previousWordBegin = 0L
        var previousWordDuration = 0L

        // 用游标截取文本，正文中的 '<' 或 '>' 不会被误判为标签边界
        while (wordMatcher.find()) {
            val currentStart = wordMatcher.start()

            if (previousTagEnd != -1) {
                val text = body.substring(previousTagEnd, currentStart)
                val begin = lineBegin + previousWordBegin
                words.add(
                    LyricWord(
                        begin = begin,
                        end = begin + previousWordDuration,
                        duration = previousWordDuration,
                        text = text
                    )
                )
                textBuilder.append(text)
            }

            previousWordBegin = wordMatcher.group(1).toLongOrNull() ?: 0L
            previousWordDuration = wordMatcher.group(2).toLongOrNull() ?: 0L
            previousTagEnd = wordMatcher.end()
        }

        if (previousTagEnd != -1 && previousTagEnd <= body.length) {
            val text = body.substring(previousTagEnd)
            val begin = lineBegin + previousWordBegin
            words.add(
                LyricWord(
                    begin = begin,
                    end = begin + previousWordDuration,
                    duration = previousWordDuration,
                    text = text
                )
            )
            textBuilder.append(text)
        }

        // 没有逐字标签时按普通文本降级，并清理残留的损坏标签
        val text = if (words.isEmpty()) {
            WORD_TAG_CLEANUP.replace(body, "")
        } else {
            textBuilder.toString()
        }

        return LyricLine(
            begin = lineBegin,
            end = lineBegin + lineDuration,
            duration = lineDuration,
            text = text,
            words = words
        )
    }
}
