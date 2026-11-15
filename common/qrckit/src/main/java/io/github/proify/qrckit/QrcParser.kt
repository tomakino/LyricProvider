/*
 * Copyright 2026 Proify, Tomakino
 * Licensed under the Apache License, Version 2.0
 * http://www.apache.org/licenses/LICENSE-2.0
 */

package io.github.proify.qrckit

import io.github.proify.lyricon.lyric.model.LyricLine
import io.github.proify.lyricon.lyric.model.LyricWord
import io.github.proify.qrckit.model.QrcData

/**
 * QRC 歌词解析工具
 */
object QrcParser {

    private val linePattern = Regex("""\[(\d+)\s*,\s*(\d+)]""")

    // 匹配字标签，允许文本包含除括号外的特殊符号
    private val wordPattern = Regex("""([^()\n\r]*)\((\d+)\s*,\s*(\d+)\)""")
    private val metaPattern = Regex("""\[(\w+)\s*:\s*([^]]*)]""")

    /**
     * 解析包含 QRC 的 XML 字符串
     * 使用贪婪匹配直到标签关闭符，防止属性值内部的引号导致匹配提前中断
     */
    fun parseXML(xml: String?): List<QrcData> {
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
                val (meta, lines) = parseLyric(decodedContent)
                QrcData(meta, lines)
            }
        }.toList()
    }

    /**
     * 解析纯 QRC 文本
     */
    fun parseLyric(content: String): Pair<Map<String, String>, List<LyricLine>> {
        val metaData = mutableMapOf<String, String>()
        val lines = mutableListOf<LyricLine>()

        // 1. 提取 ID3 元数据 (包括 ti, ar, al, kana 等)
        metaPattern.findAll(content).forEach {
            metaData[it.groupValues[1]] = it.groupValues[2].trim()
        }

        // 2. 识别行起始标签位置
        val lineMatches = linePattern.findAll(content).toList()

        for (i in lineMatches.indices) {
            val currentMatch = lineMatches[i]
            val lineStart = currentMatch.groupValues[1].toLongOrNull() ?: 0L
            val lineDur = currentMatch.groupValues[2].toLongOrNull() ?: 0L

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
                lines.add(parseLineBody(lineStart, lineDur, lineBody))
            }
        }

        return metaData to lines.sortedBy { it.begin }
    }

    /**
     * 解析行内详情，精准匹配字标签并生成显示文本
     */
    private fun parseLineBody(lineStart: Long, lineDur: Long, rawBody: String): LyricLine {
        val trimmedBody = rawBody.trim('\n', '\r')
        val words = mutableListOf<LyricWord>()

        wordPattern.findAll(trimmedBody).forEach { match ->
            val text = match.groupValues[1]
            val wStart = match.groupValues[2].toLongOrNull() ?: 0L
            val wDur = match.groupValues[3].toLongOrNull() ?: 0L

            if (wDur >= 0) {
                words.add(
                    LyricWord(
                        begin = wStart,
                        end = wStart + wDur,
                        duration = wDur,
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
            begin = lineStart,
            duration = lineDur,
            end = lineStart + lineDur,
            text = finalText,
            words = words
        )
    }

    private fun decodeXmlEntities(input: String): String {
        return input.replace("&quot;", "\"")
            .replace("&amp;", "&")
            .replace("&lt;", "<")
            .replace("&gt;", ">")
            .replace("&apos;", "'")
    }
}