/*
 * Copyright 2026 Proify, Tomakino
 * Licensed under the Apache License, Version 2.0
 * http://www.apache.org/licenses/LICENSE-2.0
 */

package io.github.proify.qrckit.parser

import io.github.proify.lyricon.lyric.model.LyricLine
import io.github.proify.lyricon.lyric.model.LyricWord
import io.github.proify.qrckit.parser.model.QrcDocument

/**
 * QQ音乐 QRC 歌词解析器。
 * 支持带 `LyricContent` 的 XML 与纯 QRC 文本。
 */
object QrcParser {

    /** 行时间标签：开始时间与持续时间，毫秒。 */
    private val LINE_PATTERN = Regex("""\[(\d+)\s*,\s*(\d+)]""")

    /** 逐字标签：文本、开始时间与持续时间。 */
    private val WORD_PATTERN = Regex("""([^()\n\r]*)\((\d+)\s*,\s*(\d+)\)""")

    /** 元数据标签。 */
    private val META_PATTERN = Regex("""\[(\w+)\s*:\s*([^]]*)]""")

    /**
     * 解析包含 QRC 的 XML。
     * @param xml 原始 XML；null 或空白返回空列表。
     * @return 每个非空 `LyricContent` 对应一个解析结果。
     */
    fun parseXml(xml: String?): List<QrcDocument> {
        if (xml.isNullOrBlank()) return emptyList()

        // 匹配 LyricContent=" 之后的所有内容，直到最后一个引号跟随 />
        val pattern = Regex("""LyricContent\s*=\s*"([\s\S]*?)"(?=\s*/?>)""")

        return pattern.findAll(xml).mapNotNull { match ->
            val rawContent = match.groupValues[1]
            if (rawContent.isBlank()) {
                null
            } else {
                // 处理转义并解析
                val decodedContent = decodeXmlEntities(rawContent)
                val (metadata, lines) = parseLyric(decodedContent)
                QrcDocument(metadata, lines)
            }
        }.toList()
    }

    /**
     * 解析纯 QRC 文本。
     * @param content QRC 文本。
     * @return 元数据与按开始时间排序的歌词行。
     */
    fun parseLyric(content: String): Pair<Map<String, String>, List<LyricLine>> {
        val metadata = mutableMapOf<String, String>()
        val lines = mutableListOf<LyricLine>()

        // 1. 提取 ID3 元数据 (包括 ti, ar, al, kana 等)
        META_PATTERN.findAll(content).forEach {
            metadata[it.groupValues[1]] = it.groupValues[2].trim()
        }

        // 2. 识别行起始标签位置
        val lineMatches = LINE_PATTERN.findAll(content).toList()

        for (i in lineMatches.indices) {
            val currentMatch = lineMatches[i]
            val lineBegin = currentMatch.groupValues[1].toLongOrNull() ?: 0L
            val lineDuration = currentMatch.groupValues[2].toLongOrNull() ?: 0L

            val bodyStart = currentMatch.range.last + 1
            val bodyEnd = if (i + 1 < lineMatches.size) {
                lineMatches[i + 1].range.first
            } else {
                // 处理最后一行，QRC 结尾可能带有额外的 ']'
                val lastIdx = content.lastIndexOf(']')
                if (lastIdx > bodyStart) lastIdx else content.length
            }

            if (bodyStart < bodyEnd) {
                val lineBody = content.substring(bodyStart, bodyEnd)
                lines.add(parseLineBody(lineBegin, lineDuration, lineBody))
            }
        }

        return metadata to lines.sortedBy { it.begin }
    }

    /**
     * 解析单行正文，生成逐字数据与行文本。
     * @param lineBegin 行开始时间，毫秒。
     * @param lineDuration 行持续时间，毫秒。
     * @param rawBody 行正文。
     * @return 歌词行；无逐字标签时文本回退为清理后的正文。
     */
    private fun parseLineBody(lineBegin: Long, lineDuration: Long, rawBody: String): LyricLine {
        val trimmedBody = rawBody.trim('\n', '\r')
        val words = mutableListOf<LyricWord>()

        WORD_PATTERN.findAll(trimmedBody).forEach { match ->
            val text = match.groupValues[1]
            val wordStart = match.groupValues[2].toLongOrNull() ?: 0L
            val wordDuration = match.groupValues[3].toLongOrNull() ?: 0L

            if (wordDuration >= 0) {
                words.add(
                    LyricWord(
                        begin = wordStart,
                        end = wordStart + wordDuration,
                        duration = wordDuration,
                        text = text
                    )
                )
            }
        }

        // 生成渲染文本：如果有字标签则拼接，否则回退到清理后的原始文本
        val finalText = if (words.isEmpty()) {
            trimmedBody.replace(Regex("""\(\d+,\d+\)"""), "").trim()
        } else {
            words.joinToString("") { it.text.orEmpty() }
        }

        return LyricLine(
            begin = lineBegin,
            duration = lineDuration,
            end = lineBegin + lineDuration,
            text = finalText,
            words = words
        )
    }

    /**
     * 还原 XML 实体。
     * @param input 含实体的文本。
     * @return 还原后的文本。
     */
    private fun decodeXmlEntities(input: String): String {
        return input.replace("&quot;", "\"")
            .replace("&amp;", "&")
            .replace("&lt;", "<")
            .replace("&gt;", ">")
            .replace("&apos;", "'")
    }
}