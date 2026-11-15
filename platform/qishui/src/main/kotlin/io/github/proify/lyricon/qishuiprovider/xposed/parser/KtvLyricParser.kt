package io.github.proify.lyricon.qishuiprovider.xposed.parser

import io.github.proify.lyricon.lyric.model.LyricLine
import io.github.proify.lyricon.lyric.model.LyricWord

object KtvLyricParser {

    // 匹配行时间： [start,duration]
    private val lineTimeRegex = "\\[(\\d+),(\\d+)]".toRegex()

    // 匹配时间标签（既可独立存在，也可紧跟字符）
    private val tagRegex = "<(\\d+),(\\d+),(\\d+)>".toRegex()

    /**
     * 解析整个文件内容为 Line 列表。
     *
     * @param content 原始文本（多行）
     * @param inferUntaggedTime 若为 true，则对未带时间标签的字符推断 begin 为上一个 word.end（duration = 0），
     *                          若为 false，则把未带标签字符的时间设为 0。
     */
    fun parse(content: String?, inferUntaggedTime: Boolean = true): List<LyricLine> {
        val result = mutableListOf<LyricLine>()
        if (content.isNullOrBlank()) return result

        content.lineSequence()
            .filter { it.isNotBlank() }
            .forEach { rawLine ->
                val timeMatch = lineTimeRegex.find(rawLine) ?: return@forEach

                val lineStart = timeMatch.groupValues[1].toLong()
                val lineDuration = timeMatch.groupValues[2].toLong()
                val lineEnd = lineStart + lineDuration

                val line = LyricLine(
                    begin = lineStart,
                    end = lineEnd,
                    duration = lineDuration
                )

                val words = mutableListOf<LyricWord>()

                // 行体（包含字符与可能的行内标签）
                val body = rawLine.substring(timeMatch.range.last + 1)

                var index = 0
                var lastAssignedEnd: Long? = null

                while (index < body.length) {
                    val ch = body[index]

                    // 情况 A：当前位置为 '<' 且这处是一个独立时间标签（没有前字符）
                    if (ch == '<') {
                        val tagAtIndex = tagRegex.find(body, index)
                        if (tagAtIndex != null && tagAtIndex.range.first == index) {
                            // 独立标签（通常是行音高或无主字的标签），跳过它。
                            index = tagAtIndex.range.last + 1
                            continue
                        }
                    }

                    // 情况 B：当前字符后紧跟时间标签 -> 正常带标签字
                    if (index + 1 < body.length && body[index + 1] == '<') {
                        val tagMatch = tagRegex.find(body, index + 1)
                        if (tagMatch != null && tagMatch.range.first == index + 1) {
                            val offset = tagMatch.groupValues[1].toLong()
                            val duration = tagMatch.groupValues[2].toLong()
                            val wordBegin = lineStart + offset
                            val wordEnd = wordBegin + duration

                            val word = LyricWord(
                                begin = wordBegin,
                                end = wordEnd,
                                duration = duration,
                                text = ch.toString()
                            )

                            words.add(word)
                            lastAssignedEnd = wordEnd
                            index = tagMatch.range.last + 1
                            continue
                        }
                    }

                    // 情况 C：未带标签字符（裸字符 / 空格 / 标点 / 行尾单独字符）
                    val inferredBegin = if (inferUntaggedTime) {
                        lastAssignedEnd ?: lineStart
                    } else {
                        0L
                    }
                    val inferredDuration = 0L
                    val inferredEnd = inferredBegin + inferredDuration

                    val word = LyricWord(
                        begin = inferredBegin,
                        end = inferredEnd,
                        duration = inferredDuration,
                        text = ch.toString()
                    )

                    words.add(word)
                    // lastAssignedEnd 保持不变（因为 inferredDuration = 0），但为了连续推断，更新为 inferredEnd
                    lastAssignedEnd = inferredEnd
                    index++
                }

                // 还原行文本：移除所有时间标签（保留空格与行内非时间字符）
                val textRecovered = body.replace(tagRegex, "")
                line.text = textRecovered

                line.words = words
                result.add(line)
            }

        return result
    }
}