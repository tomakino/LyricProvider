/*
 * Copyright 2026 Proify, Tomakino
 * Licensed under the Apache License, Version 2.0
 * http://www.apache.org/licenses/LICENSE-2.0
 */

package io.github.proify.lrckit

import io.github.proify.lyricon.lyric.model.LyricLine
import io.github.proify.lyricon.lyric.model.LyricWord
import java.util.regex.Pattern

/**
 * 增强型 LRC 解析器。
 * 解析行时间轴与 `<mm:ss.SSS>` 逐字时间标签；同一时间点只保留首条。
 */
object EnhanceLrcParser {

    /** 行首连续的行时间标签。 */
    private val LINE_PREFIX = Pattern.compile("""^(?:\[\d{1,3}[ :.]\d{2}(?:[ :.]\d{1,3})?])+""")

    /** 单个行时间标签。 */
    private val LINE_TAG = Pattern.compile("""\[(\d{1,3})[ :.](\d{2})(?:[ :.](\d{1,3}))?]""")

    /** 逐字时间标签，支持尖括号与方括号两种写法。 */
    private val WORD_TAG = Pattern.compile(
        """<(\d{1,3})[ :.](\d{2})(?:[ :.](\d{1,3}))?>|\[(\d{1,3})[ :.](\d{2})(?:[ :.](\d{1,3}))?]"""
    )

    /** 元数据标签。 */
    private val META_TAG = Pattern.compile("""^\[(\w+)\s*:\s*(.*)]$""")

    /** 无后继行且无总时长时，末行的持续时长（毫秒）。 */
    private const val FALLBACK_LINE_DURATION = 5000L

    /** 元数据中全局偏移的键名。 */
    private const val OFFSET_KEY = "offset"

    /**
     * 解析增强 LRC。
     * @param raw 原始文本；null 或空白返回空文档。
     * @param duration 音频总时长，单位为毫秒，用于确定末行结束时间。
     * @return 按开始时间排序的文档；逐字数据位于每行的 words。
     */
    fun parse(raw: String?, duration: Long = 0): EnhanceLrcDocument {
        if (raw.isNullOrBlank()) return EnhanceLrcDocument()

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
                    parsedLines += parseLineBody(times, line.substring(prefix.end()).trimStart())
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

        return EnhanceLrcDocument(metadata, buildLines(parsedLines, offset, duration))
    }

    /**
     * 解析一行的正文，提取逐字数据与可选的结束标签。
     * @param times 该行的全部行时间标签，毫秒。
     * @param content 剥离行时间标签后的正文。
     * @return 行文本、逐字数据与结束标签。
     */
    private fun parseLineBody(times: List<Long>, content: String): ParsedLine {
        val spans = collectWordSpans(content)
        if (spans.isEmpty()) return ParsedLine(times, content.trim(), emptyList(), null)

        val leadingText = content.substring(0, spans.first().start)
        val words = mutableListOf<ParsedWord>()
        var endTag: Long? = null
        var leadingConsumed = false

        spans.forEachIndexed { index, span ->
            val next = spans.getOrNull(index + 1)
            val text = content.substring(span.end, next?.start ?: content.length)

            // 行内最后一个标签之后没有任何字符，说明它是该行的结束时间，而不是新词的开始
            if (next == null && text.isEmpty()) {
                endTag = span.time
                return@forEachIndexed
            }
            if (text.isEmpty()) return@forEachIndexed

            val isLeading = !leadingConsumed && leadingText.isNotBlank()

            if (isLeading) leadingConsumed = true

            val wordText = if (isLeading) leadingText + text else text
            val wordBegin = if (isLeading) times.firstOrNull() ?: span.time else span.time

            words += ParsedWord(wordBegin, wordText)
        }

        val text = if (words.isEmpty()) {
            WORD_TAG.matcher(content).replaceAll("").trim()
        } else {
            words.joinToString("") { it.text }
        }
        return ParsedLine(times, text, words, endTag)
    }

    /**
     * 展开行时间标签，推导结束时间并收束逐字时间窗。
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
                    endTag = line.endTag?.let { (it + offset).coerceAtLeast(0L) },
                    words = line.words.map { it.copy(begin = (it.begin + offset).coerceAtLeast(0L)) },
                    text = line.text,
                    allowEndTag = line.times.size == 1,
                )
            }
        }.distinctBy { it.begin }.sortedBy { it.begin }

        val distinctBegins = pending.map { it.begin }.distinct()
        val successor = HashMap<Long, Long>(distinctBegins.size)
        distinctBegins.forEachIndexed { index, begin ->
            distinctBegins.getOrNull(index + 1)?.let { successor[begin] = it }
        }

        return pending.map { line ->
            val providedEnd = line.endTag?.takeIf { line.allowEndTag && it > line.begin }
            val end = providedEnd
                ?: successor[line.begin]
                ?: if (totalDuration > line.begin) totalDuration else line.begin + FALLBACK_LINE_DURATION
            val lineEnd = end.coerceAtLeast(line.begin)
            val words = if (line.words.isEmpty()) {
                null
            } else {
                alignWords(line.words, line.begin, lineEnd)
            }
            val text = if (words.isNullOrEmpty()) {
                line.text
            } else {
                words.joinToString("") { it.text.orEmpty() }
            }

            LyricLine(
                begin = line.begin,
                end = lineEnd,
                duration = lineEnd - line.begin,
                text = text,
                words = words,
            )
        }
    }

    /**
     * 将逐字时间窗限制在行时间窗内，末词对齐行结束时间。
     * @param words 原始逐字数据。
     * @param lineBegin 行开始时间，毫秒。
     * @param lineEnd 行结束时间，毫秒。
     * @return 时间合法的逐字数据。
     */
    private fun alignWords(
        words: List<ParsedWord>,
        lineBegin: Long,
        lineEnd: Long
    ): List<LyricWord> = words.mapIndexed { index, word ->
        val begin = word.begin.coerceIn(lineBegin, lineEnd)
        val end = if (index == words.lastIndex) {
            lineEnd
        } else {
            words[index + 1].begin.coerceIn(begin, lineEnd)
        }
        LyricWord(begin = begin, end = end, duration = end - begin, text = word.text)
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
     * 定位正文中的全部逐字时间标签。
     * @param content 剥离行时间标签后的正文。
     * @return 按出现顺序排列的标签时间与文本区间。
     */
    private fun collectWordSpans(content: String): List<WordSpan> {
        val matcher = WORD_TAG.matcher(content)
        val spans = mutableListOf<WordSpan>()
        while (matcher.find()) {
            val minute = matcher.group(1) ?: matcher.group(4) ?: continue
            val second = matcher.group(2) ?: matcher.group(5) ?: continue
            val fraction = matcher.group(3) ?: matcher.group(6)

            spans += WordSpan(
                time = toMs(minute, second, fraction),
                start = matcher.start(),
                end = matcher.end(),
            )
        }
        return spans
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
     * @property text 行文本。
     * @property words 逐字数据。
     * @property endTag 行内结束标签对应的时间，毫秒。
     */
    private class ParsedLine(
        val times: List<Long>,
        val text: String,
        val words: List<ParsedWord>,
        val endTag: Long?,
    )

    /**
     * 原始逐字数据。
     * @property begin 词开始时间，毫秒。
     * @property text 词文本。
     */
    private data class ParsedWord(val begin: Long, val text: String)

    /**
     * 逐字时间标签的文本区间。
     * @property time 标签对应的时间，毫秒。
     * @property start 标签在正文中的起始下标。
     * @property end 标签在正文中的结束下标。
     */
    private class WordSpan(val time: Long, val start: Int, val end: Int)

    /**
     * 待推导结束时间的行。
     * @property begin 行开始时间，毫秒。
     * @property endTag 行内结束标签对应的时间，毫秒。
     * @property words 已偏移的逐字数据。
     * @property text 行文本。
     * @property allowEndTag 是否允许使用行内结束标签。
     */
    private class PendingLine(
        val begin: Long,
        val endTag: Long?,
        val words: List<ParsedWord>,
        val text: String,
        val allowEndTag: Boolean,
    )
}
