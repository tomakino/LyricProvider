/*
 * Copyright 2026 Proify, Tomakino
 * Licensed under the Apache License, Version 2.0
 * http://www.apache.org/licenses/LICENSE-2.0
 */

package io.github.proify.lrckit

import io.github.proify.lyricon.lyric.model.LyricLine
import java.util.regex.Pattern

/**
 * LRC 解析器。
 * 忽略逐字时间标签，仅解析行时间轴与文本；同一时间点只保留首条。
 */
object LrcParser {

    /** 行首连续的行时间标签。 */
    private val LINE_PREFIX = Pattern.compile("""^(?:\[\d{1,3}[ :.]\d{2}(?:[ :.]\d{1,3})?])+""")

    /** 单个行时间标签。 */
    private val LINE_TAG = Pattern.compile("""\[(\d{1,3})[ :.](\d{2})(?:[ :.](\d{1,3}))?]""")

    /** 逐字时间标签。 */
    private val WORD_TAG = Pattern.compile("""<\d{1,3}[ :.]\d{2}(?:[ :.]\d{1,3})?>""")

    /** 元数据标签。 */
    private val META_TAG = Pattern.compile("""^\[(\w+)\s*:\s*(.*)]$""")

    /** 无后继行且无总时长时，末行的持续时长（毫秒）。 */
    private const val FALLBACK_LINE_DURATION = 5000L

    /** 元数据中全局偏移的键名。 */
    private const val OFFSET_KEY = "offset"

    /**
     * 解析原始歌词。
     * @param raw 原始文本；null 或空白返回空文档。
     * @param duration 音频总时长，单位为毫秒，用于确定末行结束时间。
     * @return 按开始时间排序的文档。
     */
    fun parse(raw: String?, duration: Long = 0): LrcDocument {
        if (raw.isNullOrBlank()) return LrcDocument()

        val metadata = mutableMapOf<String, String>()
        val parsedLines = mutableListOf<ParsedLine>()
        val sourceText = raw.removePrefix("\uFEFF")

        sourceText.lineSequence().forEach { source ->
            val line = source.trimStart()
            if (!line.startsWith("[")) return@forEach

            val prefix = LINE_PREFIX.matcher(line)
            if (prefix.find()) {
                val times = extractTimes(line.substring(prefix.start(), prefix.end()))
                if (times.isNotEmpty()) {
                    val content = line.substring(prefix.end()).trimStart()
                    val stripped = WORD_TAG.matcher(content).replaceAll("")
                    val text = if (stripped == content) content.trim() else stripped
                    parsedLines += ParsedLine(times, text)
                }
            } else {
                val meta = META_TAG.matcher(line.trimEnd())
                if (meta.matches()) {
                    metadata[meta.group(1)] = meta.group(2).trim()
                }
            }
        }

        val offset = metadata.entries
            .firstOrNull { it.key.equals(OFFSET_KEY, ignoreCase = true) }
            ?.value
            ?.toLongOrNull()
            ?: 0L

        return LrcDocument(metadata, buildLines(parsedLines, offset, duration))
    }

    /**
     * 展开行时间标签，按时间排序并推导每行结束时间。
     * @param lines 已解析的原始行。
     * @param offset 全局偏移毫秒数。
     * @param totalDuration 音频总时长，毫秒。
     * @return 按开始时间排序的歌词行。
     */
    private fun buildLines(
        lines: List<ParsedLine>,
        offset: Long,
        totalDuration: Long
    ): List<LyricLine> {
        val pending = lines.flatMap { line ->
            line.times.map { time ->
                PendingLine(
                    begin = (time + offset).coerceAtLeast(0L),
                    text = line.text,
                )
            }
        }.distinctBy { it.begin }.sortedBy { it.begin }

        val distinctBegins = pending.map { it.begin }.distinct()
        val successor = HashMap<Long, Long>(distinctBegins.size)
        distinctBegins.forEachIndexed { index, begin ->
            distinctBegins.getOrNull(index + 1)?.let { successor[begin] = it }
        }

        return pending.map { line ->
            val end = successor[line.begin]
                ?: if (totalDuration > line.begin) totalDuration else line.begin + FALLBACK_LINE_DURATION
            val lineEnd = end.coerceAtLeast(line.begin)

            LyricLine(
                begin = line.begin,
                end = lineEnd,
                duration = lineEnd - line.begin,
                text = line.text,
            )
        }
    }

    /**
     * 从行首前缀中提取全部时间标签。
     * @param prefix 仅含行时间标签的字符串。
     * @return 按出现顺序排列的毫秒时间。
     */
    private fun extractTimes(prefix: String): List<Long> =
        LINE_TAG.matcher(prefix).let { matcher ->
            buildList {
                while (matcher.find()) {
                    add(toMs(matcher.group(1), matcher.group(2), matcher.group(3)))
                }
            }
        }

    /**
     * 将时间标签转换为毫秒。
     * @param minute 分钟部分。
     * @param second 秒部分。
     * @param fraction 毫秒部分，位数决定倍率。
     * @return 自歌曲开始起的毫秒数。
     */
    private fun toMs(minute: String, second: String, fraction: String?): Long {
        val m = minute.toLongOrNull() ?: 0L
        val s = second.toLongOrNull() ?: 0L
        val ms = when (fraction?.length) {
            1 -> (fraction.toLongOrNull() ?: 0L) * 100
            2 -> (fraction.toLongOrNull() ?: 0L) * 10
            3 -> fraction.toLongOrNull() ?: 0L
            else -> 0L
        }
        return m * 60000 + s * 1000 + ms
    }

    /**
     * 原始行。
     * @property times 该行的全部行时间标签，毫秒。
     * @property text 去除逐字标签后的行文本。
     */
    private class ParsedLine(val times: List<Long>, val text: String)

    /**
     * 待推导结束时间的行。
     * @property begin 行开始时间，毫秒。
     * @property text 行文本。
     */
    private class PendingLine(val begin: Long, val text: String)
}
