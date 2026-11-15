/*
 * Copyright 2026 Proify, Tomakino
 * Licensed under the Apache License, Version 2.0
 * http://www.apache.org/licenses/LICENSE-2.0
 */

package io.github.proify.lrckit.support

import io.github.proify.lrckit.EnhanceLrcDocument
import io.github.proify.lrckit.LrcDocument
import io.github.proify.lyricon.lyric.model.LyricWord
import org.junit.jupiter.api.Assertions.assertEquals
import org.junit.jupiter.api.Assertions.assertTrue

/**
 * 多格式歌词的解析不变量。
 *
 * 这些不变量是"生成格式数据 → 解析 → 校验 → 修复"自我迭代闭环的收敛判据。
 * 基准取自同族解析器（QRC / YRC / KRC）与 `RichLyricLine.normalize()` 的既有约定，
 * 而不是为本次改动临时发明的规则。
 */
object LyricInvariants {

    /** 校验标准 LRC 文档：行数、时间轴自洽性与有序性。 */
    fun assertLrc(document: LrcDocument, sample: LyricSample) {
        val lines = document.lines
        assertEquals(sample.expectation.lrcLines, lines.size, "[${sample.name}] LRC 行数不符")
        assertTimeline(sample, lines.map { it.begin to it.end }, lines.map { it.duration })
        assertFirstBegin(sample, lines.firstOrNull()?.begin)
    }

    /** 校验增强 LRC 文档：在标准不变量之上追加逐字时间轴与轨道语义校验。 */
    fun assertEnhance(document: EnhanceLrcDocument, sample: LyricSample) {
        val lines = document.lines
        assertEquals(sample.expectation.enhanceLines, lines.size, "[${sample.name}] 增强行数不符")
        assertTimeline(sample, lines.map { it.begin to it.end }, lines.map { it.duration })
        assertFirstBegin(sample, lines.firstOrNull()?.begin)

        sample.expectation.enhanceFirstText?.let {
            assertEquals(it, lines.firstOrNull()?.text, "[${sample.name}] 首行文本不符")
        }
        sample.expectation.enhanceFirstSecondary?.let {
            assertEquals(it, lines.firstOrNull()?.secondary, "[${sample.name}] 首行副轨道不符")
        }
        if (sample.expectation.expectsWords) {
            assertTrue(
                lines.any { !it.words.isNullOrEmpty() },
                "[${sample.name}] 预期存在逐字时间轴"
            )
        }
        if (sample.expectation.expectsAlignedRight) {
            assertTrue(lines.any { it.isAlignedRight }, "[${sample.name}] 预期存在右对齐行")
        }

        lines.forEachIndexed { index, line ->
            assertWordTimeline(sample, index, line.begin, line.end, line.text, line.words)
        }
    }

    /**
     * I1 时间轴有序；I2 时间自洽（`end >= begin`、`duration == end - begin`、非负）；
     * I3 相邻行首尾相接——时间点不同时必须无缝衔接，同一时间点的分组必须共享结束时间。
     */
    private fun assertTimeline(
        sample: LyricSample,
        spans: List<Pair<Long, Long>>,
        durations: List<Long>
    ) {
        spans.forEachIndexed { index, (begin, end) ->
            val where = "[${sample.name}] 第 $index 行"
            assertTrue(begin >= 0, "$where begin 为负: $begin")
            assertTrue(end >= begin, "$where end($end) 早于 begin($begin)")
            assertEquals(durations[index], end - begin, "$where duration 与 end-begin 不一致")

            if (index == 0) return@forEachIndexed
            val (previousBegin, previousEnd) = spans[index - 1]
            assertTrue(previousBegin <= begin, "$where 时间轴未按 begin 排序")
            if (previousBegin < begin) {
                assertEquals(begin, previousEnd, "$where 与上一行之间存在空隙或重叠")
            } else {
                assertEquals(end, previousEnd, "$where 同一时间点的行结束时间不一致")
            }
        }
    }

    /**
     * I4 逐字文本必须等于词的直接拼接（与 `RichLyricLine.normalize()` 一致，禁止分隔符）；
     * I5 逐字时间轴有序、自洽、落在行时间窗内，且末词对齐行结束时间。
     */
    private fun assertWordTimeline(
        sample: LyricSample,
        index: Int,
        lineBegin: Long,
        lineEnd: Long,
        text: String?,
        words: List<LyricWord>?
    ) {
        if (words.isNullOrEmpty()) return

        val where = "[${sample.name}] 第 $index 行逐字" +
                "(line=$lineBegin-$lineEnd, words=${words.joinToString("|") { "${it.begin}-${it.end}" }})"
        assertEquals(
            words.joinToString("") { it.text.orEmpty() },
            text,
            "$where 文本与逐字数据不一致"
        )

        words.zipWithNext().forEach { (previous, current) ->
            assertTrue(previous.begin <= current.begin, "$where 词时间轴未按 begin 排序")
        }
        words.forEach { word ->
            assertTrue(
                word.begin >= lineBegin,
                "$where 词开始早于行开始: ${word.begin} < $lineBegin"
            )
            assertTrue(word.end >= word.begin, "$where 词结束早于词开始")
            assertTrue(word.end <= lineEnd, "$where 词结束晚于行结束: ${word.end} > $lineEnd")
            assertEquals(
                word.end - word.begin,
                word.duration,
                "$where 词 duration 与 end-begin 不一致"
            )
        }
        assertEquals(lineEnd, words.last().end, "$where 末词未对齐行结束时间")
    }

    private fun assertFirstBegin(sample: LyricSample, actual: Long?) {
        val expected = sample.expectation.firstBegin ?: return
        assertEquals(expected, actual, "[${sample.name}] 首行开始时间不符")
    }
}
