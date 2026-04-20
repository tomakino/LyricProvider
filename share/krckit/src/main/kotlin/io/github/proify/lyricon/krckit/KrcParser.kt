/*
 * Copyright 2026 Proify, Tomakino
 * Licensed under the Apache License, Version 2.0
 * http://www.apache.org/licenses/LICENSE-2.0
 */

package io.github.proify.lyricon.krckit

import io.github.proify.lyricon.lyric.model.LyricLine
import io.github.proify.lyricon.lyric.model.LyricWord
import java.util.regex.Pattern

/**
 * KRC 歌词解析器
 * 采用高性能且高容错的按行解析策略，能够有效隔离单行错误，并兼容包含特殊闭合字符的畸形歌词文本。
 */
object KrcParser {

    /**
     * 匹配歌词行：[开始时间,持续时间] 剩余内容
     * 使用 ^ 和 $ 锚点锁定单行，确保解析精准度。
     */
    private val LINE_PATTERN = Pattern.compile("""^\[(\d+)\s*,\s*(\d+)](.*)$""")

    /**
     * 匹配 Metadata 起始格式：[key: value...
     * 允许 value 延伸至行尾，配合状态机处理可能缺失的闭合括号 ']'
     */
    private val META_PATTERN = Pattern.compile("""^\[([a-zA-Z0-9_]+)\s*:(.*)$""")

    /**
     * 匹配字标签：<偏移,时长,参数>
     * 仅用于定位标签的边界，不负责提取标签后的文本，文本提取交由绝对坐标计算。
     */
    private val WORD_TAG_PATTERN = Pattern.compile("""<(\d+)\s*,\s*(\d+)\s*,\s*(\d+)>""")

    /**
     * 将 KRC 文本解析为 KrcDocument 结构
     *
     * @param content 原始 KRC 文本内容（可能是解密后的明文）
     * @return [KrcDocument] 包含元数据和按时间排序的歌词行集合
     */
    fun parse(content: String?): KrcDocument {
        if (content.isNullOrBlank()) return KrcDocument(emptyMap(), emptyList())

        val metadata = mutableMapOf<String, String>()
        val lines = mutableListOf<LyricLine>()

        // 状态机变量：用于收集跨行的 Metadata (如带有换行的 Base64 字符串)
        var currentMetaKey: String? = null
        val currentMetaValue = StringBuilder()

        content.lineSequence().forEach { rawLine ->
            val line = rawLine.trim()
            if (line.isEmpty()) return@forEach

            // 1. 尝试匹配常规歌词行
            val lineMatcher = LINE_PATTERN.matcher(line)
            if (lineMatcher.matches()) {
                currentMetaKey = null // 遇到有效歌词行，重置多行 Metadata 收集状态

                val lineStart = lineMatcher.group(1).toLongOrNull() ?: 0L
                val lineDur = lineMatcher.group(2).toLongOrNull() ?: 0L
                val lineBody = lineMatcher.group(3).trim()

                if (lineBody.isNotEmpty()) {
                    try {
                        lines.add(parseLineBody(lineStart, lineDur, lineBody))
                    } catch (e: Exception) {
                        e.printStackTrace()
                    }
                }
                return@forEach
            }

            // 2. 尝试匹配 Metadata 起始声明
            val metaMatcher = META_PATTERN.matcher(line)
            if (metaMatcher.matches()) {
                val key = metaMatcher.group(1)
                // 过滤掉纯数字的标签（为了防范某些极端畸形数据伪装成 Meta）
                if (key.all { it.isDigit() }) return@forEach

                var value = metaMatcher.group(2).trim()

                // 检查是否在同一行闭合
                if (value.endsWith("]")) {
                    value = value.dropLast(1).trim()
                    metadata[key] = value
                    currentMetaKey = null // 单行已闭合，无需跨行收集
                } else {
                    // 未闭合说明数据被换行符截断，开启多行收集状态
                    currentMetaKey = key
                    currentMetaValue.clear()
                    currentMetaValue.append(value)
                }
                return@forEach
            }

            // 3. 处理处于多行 Metadata 收集状态下的游离行
            if (currentMetaKey != null) {
                if (line.endsWith("]")) {
                    // 遇到闭合标志，完成收集
                    currentMetaValue.append(line.dropLast(1).trim())
                    metadata[currentMetaKey] = currentMetaValue.toString()
                    currentMetaKey = null
                } else {
                    // 中间断行数据，直接拼接（去除原有的换行影响）
                    currentMetaValue.append(line)
                }
            }
        }

        return KrcDocument(metadata, lines.sortedBy { it.begin })
    }

    /**
     * 解析具体的一行歌词 Body，将其转换为字级别对象集合
     *
     * @param lineStart 该行起始时间戳
     * @param lineDur   该行持续时长
     * @param body      剥离了行时间戳后的纯歌词内容及字标签
     * @return [LyricLine] 封装好的单行模型
     */
    private fun parseLineBody(lineStart: Long, lineDur: Long, body: String): LyricLine {
        val words = mutableListOf<LyricWord>()
        val textBuilder = StringBuilder()

        val wordMatcher = WORD_TAG_PATTERN.matcher(body)
        var previousWordPrefixEnd = -1
        var previousOffset = 0L
        var previousDuration = 0L

        // 利用游标截取文本，彻底免疫文本中自带的 '<' 或 '>' 字符
        while (wordMatcher.find()) {
            val currentStart = wordMatcher.start()

            // 如果存在上一个字标签，说明 [上一个标签尾部 -> 当前标签头部] 即为上一个字的真实文本
            if (previousWordPrefixEnd != -1) {
                val text = body.substring(previousWordPrefixEnd, currentStart)
                val begin = lineStart + previousOffset
                words.add(
                    LyricWord(
                        begin = begin,
                        end = begin + previousDuration,
                        duration = previousDuration,
                        text = text
                    )
                )
                textBuilder.append(text)
            }

            // 记录当前标签的属性，等待下一个标签到来时提取文本
            previousOffset = wordMatcher.group(1).toLongOrNull() ?: 0L
            previousDuration = wordMatcher.group(2).toLongOrNull() ?: 0L
            previousWordPrefixEnd = wordMatcher.end()
        }

        // 循环结束，处理收尾阶段（最后一个标签的结束位置 -> 行尾）
        if (previousWordPrefixEnd != -1 && previousWordPrefixEnd <= body.length) {
            val text = body.substring(previousWordPrefixEnd)
            val begin = lineStart + previousOffset
            words.add(
                LyricWord(
                    begin = begin,
                    end = begin + previousDuration,
                    duration = previousDuration,
                    text = text
                )
            )
            textBuilder.append(text)
        }

        // 容错处理：如果当前行没有任何字标签，说明可能是普通 LRC 行伪装，做降级处理
        val finalText = if (words.isEmpty()) {
            // 暴力擦除可能存在的损坏标签结构，保留纯净文本
            body.replace(Regex("""<\d+\s*,\s*\d+\s*,\s*\d+>"""), "")
        } else {
            textBuilder.toString()
        }

        return LyricLine(
            begin = lineStart,
            end = lineStart + lineDur,
            duration = lineDur,
            text = finalText,
            words = words
        )
    }
}