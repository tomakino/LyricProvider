/*
 * Copyright 2026 Proify, Tomakino
 * Licensed under the Apache License, Version 2.0
 * http://www.apache.org/licenses/LICENSE-2.0
 */

@file:Suppress("ConvertTwoComparisonsToRangeCheck")

package io.github.proify.lyricon.bodianprovider.xposed

import io.github.proify.lrckit.LrcParser
import io.github.proify.lyricon.bodianprovider.xposed.KuwoLyricParser.wordTimesAreAbsolute
import io.github.proify.lyricon.lyric.model.LyricWord
import io.github.proify.lyricon.lyric.model.RichLyricLine
import kotlin.math.abs

/**
 * 酷我系 `.lrcx` 逐字歌词解析器。
 *
 * 波点音乐（cn.wenyu.bodian）复用酷我播放器内核，歌词缓存是**明文**落盘的，
 * 本解析器按 App 内解析器（LyricsParserImpl）的逻辑 1:1 还原：
 *
 * ```
 * [kuwo:020]                     ← 值按【八进制】解析
 * [ver:v1.0][ti:…][ar:…][offset:0]
 * [00:06.010]<7212,-7212>词<8414,-6010>：<9616,-4808>李…
 * ```
 *
 * 头部除数：
 * ```
 * V        = Integer.parseInt(值, 8)
 * divBegin = V / 10
 * divEnd   = V % 10
 * ```
 *
 * 逐字标签 `<a,b>`（整数除法，与 App 完全一致）：
 * ```
 * begin = (a + b) / (2 * divBegin)
 * end   = begin + (a - b) / (2 * divEnd)
 * ```
 * 反过来看就是对 (begin, duration) 的一层混淆：
 * `a = divBegin * begin + divEnd * duration`，`b = divBegin * begin − divEnd * duration`。
 *
 * **逐字时间是相对该行行首的偏移**（已在 4 个真实文件、4 组不同除数上验证：
 * 每行首字的 begin 恒为 0）。[wordTimesAreAbsolute] 仍保留自适应判定作为保险。
 *
 * 字的文本范围不在标签里，靠相邻标签的原始下标推算：
 * 某字长度 = 下一个标签的起始下标 − 当前标签的结束下标，最后一个字吃到行尾。
 */
object KuwoLyricParser {

    /** 头部标签；`[by:]` 这类空值也要能匹配，所以值是可选的 */
    private val TAG_REGEX =
        Regex("""\[(ver|ti|ar|al|by|ml|kuwo|offset):\s*(\S+(?:\s+\S+)*)?\s*]""")

    /** 严格的歌词行匹配：`[mm:ss]` / `[mm:ss.xxx]` */
    private val STRICT_LINE_REGEX =
        Regex("""^\s*(\[-?\d{1,2}:\d{1,2}(?:[.:]\d{1,4})?])(.*)$""")

    /**
     * App 用的宽松写法，只在严格匹配失败时兜底。
     * 它的 `.*` 是贪婪的，行文本里若出现 `]` 会吃错，所以不作为首选。
     */
    private val LOOSE_LINE_REGEX = Regex("""(\[\d{1,2}:.*\d{1,4}])\s*(\S+(?:\s+\S+)*)?\s*""")

    /** 逐字标签 `<起, 止[, 其他]>` */
    private val WORD_REGEX = Regex("""<(-?\d+),(-?\d+)(?:,-?\d+)?>""")

    /** 去掉逐字标签后即为纯文本 */
    private val WORD_STRIP = Regex("""<-?\d+,-?\d+(?:,-?\d+)?>""")

    /** App 自己也跳过长度 < 6 的行 */
    private const val MIN_LINE_LENGTH = 6

    /**
     * 解析一份歌词文本。带 `[kuwo:…]` 头的按逐字解析，否则回落到普通 LRC。
     *
     * @param durationMs 歌曲时长，用于补最后一行的 end
     */
    fun parse(content: String?, durationMs: Long = 0L): List<RichLyricLine> {
        if (content.isNullOrBlank()) return emptyList()

        val rawLines = content.split("\n").flatMap { splitJoinedTags(it) }
        val header = readHeader(rawLines)

        val wordwise =
            header.divisors?.let { parseWordwise(rawLines, it, header.offset, durationMs) }
        if (!wordwise.isNullOrEmpty()) return wordwise

        return parseLrc(content, durationMs)
    }

    /** 普通 LRC 回落路径（`[offset:]` 由 lrckit 自己处理） */
    private fun parseLrc(content: String, durationMs: Long): List<RichLyricLine> =
        LrcParser.parse(content, durationMs).lines.map { line ->
            RichLyricLine(
                begin = line.begin,
                end = line.end,
                duration = line.duration,
                text = line.text
            )
        }

    // ------------------------------------------------------------------ 头部

    private class Header(val divisors: Divisors?, val offset: Long)

    private class Divisors(val begin: Int, val end: Int)

    private fun readHeader(lines: List<String>): Header {
        var divisors: Divisors? = null
        var offset = 0L

        lines.forEach { line ->
            val match = TAG_REGEX.find(line) ?: return@forEach
            val value = match.groupValues.getOrNull(2).orEmpty()

            when (match.groupValues[1]) {
                "kuwo" -> {
                    if (divisors != null) return@forEach
                    val cut = value.indexOf("][")
                    val trimmed = if (cut > 0) value.substring(0, cut) else value
                    val parsed = trimmed.toIntOrNull(8) ?: return@forEach
                    val begin = parsed / 10
                    val end = parsed % 10
                    if (begin > 0 && end > 0) divisors = Divisors(begin, end)
                }

                "offset" -> offset = value.toLongOrNull() ?: 0L
            }
        }
        return Header(divisors, offset)
    }

    /** App 会把粘在一起的 `][` 拆成独立行，这里保持一致 */
    private fun splitJoinedTags(line: String): List<String> =
        if (line.contains("][")) line.replace("][", "]#_#[").split("#_#") else listOf(line)

    // ------------------------------------------------------------------ 逐字

    private fun parseWordwise(
        rawLines: List<String>,
        divisors: Divisors,
        offset: Long,
        durationMs: Long
    ): List<RichLyricLine> {
        val parsed = mutableListOf<ParsedLine>()

        rawLines.forEach { raw ->
            if (raw.length < MIN_LINE_LENGTH) return@forEach

            val match = STRICT_LINE_REGEX.find(raw) ?: LOOSE_LINE_REGEX.find(raw) ?: return@forEach
            val lineBegin = parseTimeTag(match.groupValues[1]) ?: return@forEach

            val body = match.groupValues.getOrNull(2).orEmpty().trim()
            val text = body.replace(WORD_STRIP, "")

            parsed += ParsedLine(lineBegin + offset, text, extractWords(body, text, divisors))
        }

        if (parsed.isEmpty()) return emptyList()
        parsed.sortBy { it.begin }

        val absolute = wordTimesAreAbsolute(parsed)

        // 翻译行的时间戳是下一条原文的开始，所以按顺序归到它之前最近的一条原文
        val translations = HashMap<ParsedLine, String>()
        var previousMain: ParsedLine? = null

        for (line in parsed) {
            if (line.isTranslation) {
                if (previousMain != null && line.text.isNotBlank()) {
                    translations[previousMain] = line.text.trim()
                }
            } else {
                previousMain = line
            }
        }

        val mains = parsed.filter { !it.isTranslation }

        return mains.mapIndexed { index, line ->
            val translation = translations[line]
            val nextBegin = mains.getOrNull(index + 1)?.begin
            val shift = if (absolute) 0L else line.begin

            val words = line.words.mapNotNull { word ->
                val from = word.charStart.coerceIn(0, line.text.length)
                val to = word.charEnd.coerceIn(from, line.text.length)
                val segment = line.text.substring(from, to)
                if (segment.isEmpty()) return@mapNotNull null

                val begin = word.begin + shift
                val end = (word.end + shift).coerceAtLeast(begin)
                LyricWord(text = segment, begin = begin, end = end, duration = end - begin)
            }.toMutableList()

            // 行末以最后一个字唱完为准，但不越过下一行行首
            val sungEnd = words.lastOrNull()?.end
            val end = when {
                sungEnd != null && nextBegin != null -> minOf(sungEnd, nextBegin)
                sungEnd != null -> sungEnd
                nextBegin != null -> nextBegin
                durationMs > line.begin -> durationMs
                else -> line.begin + 5000L
            }.coerceAtLeast(line.begin)

            RichLyricLine(
                begin = line.begin,
                end = end,
                duration = end - line.begin,
                text = line.text,
                words = words,
                translation = translation
            )
        }
    }

    /**
     * 判断逐字时间是绝对毫秒还是相对行首的偏移。
     *
     * 真实文件里首字 begin 恒为 0（即相对），但不同版本万一改了口径，
     * 这里两种解释各算一次"首字时间与行时间的总偏差"，取偏差小的那种。
     */
    private fun wordTimesAreAbsolute(lines: List<ParsedLine>): Boolean {
        val samples = lines.asSequence().filter { it.words.isNotEmpty() }.take(16).toList()
        if (samples.isEmpty()) return true

        var absoluteError = 0L
        var relativeError = 0L
        samples.forEach { line ->
            val first = line.words.first().begin
            absoluteError += abs(first - line.begin)
            relativeError += abs(first)
        }
        return absoluteError <= relativeError
    }

    private fun extractWords(body: String, text: String, divisors: Divisors): List<RawWord> {
        val words = WORD_REGEX.findAll(body).map { match ->
            val a = match.groupValues[1].toLongOrNull() ?: 0L
            val b = match.groupValues[2].toLongOrNull() ?: 0L

            val begin = (a + b) / (2L * divisors.begin)
            val end = begin + (a - b) / (2L * divisors.end)

            RawWord(
                begin = begin,
                end = if (end < begin) begin else end,
                rawStart = match.range.first,
                rawEnd = match.range.last + 1
            )
        }.toMutableList()

        if (words.isEmpty()) return words

        if (words.size == 1) {
            words[0].charStart = 0
            words[0].charEnd = text.length
            return words
        }

        words[0].charStart = words[0].rawStart
        for (i in 0 until words.size - 1) {
            val cur = words[i]
            val next = words[i + 1]

            // 时间越界时向后夹紧，与 App 行为一致
            if (next.begin < cur.end) cur.end = next.begin

            cur.charEnd = cur.charStart + (next.rawStart - cur.rawEnd)
            next.charStart = cur.charEnd
        }
        words.last().charEnd = text.length

        return words
    }

    private class RawWord(
        var begin: Long,
        var end: Long,
        val rawStart: Int,
        val rawEnd: Int,
        var charStart: Int = 0,
        var charEnd: Int = 0
    )

    /**
     * 翻译行：没有任何逐字时间（标签全为 0）。
     */
    private val ParsedLine.isTranslation: Boolean
        get() = words.isEmpty() || words.all { it.begin == 0L && it.end == 0L }

    private class ParsedLine(
        val begin: Long,
        val text: String,
        val words: List<RawWord>
    )

    // ------------------------------------------------------------------ 时间

    /** 解析 `[mm:ss]` / `[mm:ss.xxx]`，支持前导负号 */
    fun parseTimeTag(tag: String): Long? {
        val open = tag.indexOf('[')
        val close = tag.lastIndexOf(']')
        if (open < 0 || close <= open) return null

        var body = tag.substring(open + 1, close)
        var sign = 1
        if (body.startsWith("-")) {
            sign = -1
            body = body.substring(1)
        }

        val parts = body.split(Regex("[:.]"))
        if (parts.size != 2 && parts.size != 3) return null
        if (parts.any { part -> part.isEmpty() || part.any { !it.isDigit() } }) return null

        val minutes = parts[0].toLongOrNull() ?: return null
        val seconds = parts[1].toLongOrNull() ?: return null
        var ms = minutes * 60_000L + seconds * 1_000L

        if (parts.size == 3) {
            val frac = parts[2]
            val value = frac.toLongOrNull() ?: return null
            ms += when (frac.length) {
                1 -> value * 100
                2 -> value * 10
                3 -> value
                else -> value / 10
            }
        }
        return sign * ms
    }
}
