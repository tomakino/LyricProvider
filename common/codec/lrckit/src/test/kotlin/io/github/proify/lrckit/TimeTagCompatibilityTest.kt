/*
 * Copyright 2026 Proify, Tomakino
 * Licensed under the Apache License, Version 2.0
 * http://www.apache.org/licenses/LICENSE-2.0
 */

package io.github.proify.lrckit

import org.junit.jupiter.api.Assertions.assertEquals
import org.junit.jupiter.api.Assertions.assertTrue
import org.junit.jupiter.api.DisplayName
import org.junit.jupiter.api.Test

/**
 * 时间标签格式兼容性与容错性验证。
 */
class TimeTagCompatibilityTest {

    @Test
    @DisplayName("行标签：毫秒 1/2/3 位")
    fun parsesFractionDigits() {
        val lines = LrcParser.parse("[00:01.5]a\n[00:02.50]b\n[00:03.500]c").lines

        assertEquals(listOf(1500L, 2500L, 3500L), lines.map { it.begin })
    }

    @Test
    @DisplayName("行标签：无毫秒")
    fun parsesWithoutFraction() {
        val line = LrcParser.parse("[00:10]甲").lines.single()

        assertEquals(10000L, line.begin)
    }

    @Test
    @DisplayName("行标签：1 位分钟")
    fun parsesSingleDigitMinute() {
        val line = LrcParser.parse("[0:10.00]甲").lines.single()

        assertEquals(10000L, line.begin)
    }

    @Test
    @DisplayName("行标签：混合分隔符")
    fun parsesMixedSeparators() {
        val lines = LrcParser.parse(
            """
            [00:01.50]a
            [00:02:50]b
            [00 03 50]c
            [00.04.50]d
            [00:05 50]e
            [00 06.50]f
            """.trimIndent()
        ).lines

        assertEquals(listOf(1500L, 2500L, 3500L, 4500L, 5500L, 6500L), lines.map { it.begin })
    }

    @Test
    @DisplayName("行标签：毫秒前导零边界")
    fun parsesFractionBoundaries() {
        val lines = LrcParser.parse(
            "[00:00.001]a\n[00:00.010]b\n[00:00.100]c\n[00:00.999]d"
        ).lines

        assertEquals(listOf(1L, 10L, 100L, 999L), lines.map { it.begin })
    }

    @Test
    @DisplayName("行标签：秒进位边界")
    fun parsesSecondBoundary() {
        val lines = LrcParser.parse("[00:59.999]a\n[01:00.000]b").lines

        assertEquals(listOf(59999L, 60000L), lines.map { it.begin })
    }

    @Test
    @DisplayName("行标签：三位分钟")
    fun parsesThreeDigitMinute() {
        val line = LrcParser.parse("[123:45.6]甲").lines.single()

        assertEquals(7425600L, line.begin)
    }

    @Test
    @DisplayName("行标签：重复时间只保留首条")
    fun parsesDuplicateTags() {
        val lines = LrcParser.parse("[00:10.00][00:10.00]重复").lines

        assertEquals(1, lines.size)
        assertEquals(10000L, lines.single().begin)
        assertEquals(15000L, lines.single().end)
    }

    @Test
    @DisplayName("行标签：标签前空格与制表符")
    fun trimsLeadingWhitespaceBeforeTag() {
        val lines = LrcParser.parse("  [00:10.00]甲\n\t[00:12.00]乙").lines

        assertEquals(2, lines.size)
        assertEquals(10000L, lines[0].begin)
        assertEquals(12000L, lines[1].begin)
    }

    @Test
    @DisplayName("行标签：畸形标签忽略")
    fun ignoresMalformedTags() {
        val inputs = listOf(
            "[00:10.00",
            "[[00:10.00]]",
            "[-00:10.00]",
            "[00:10.00:50]",
            "【00:10.00】",
            "[00：10.00]",
            "[００:１０.００]"
        )

        inputs.forEach { raw ->
            assertTrue(LrcParser.parse(raw).lines.isEmpty(), "不应解析: $raw")
        }
    }

    @Test
    @DisplayName("行标签：正文中的时间标签不参与解析")
    fun keepsTagsInContent() {
        val line = LrcParser.parse("[00:10.00]甲[00:20.00]乙").lines.single()

        assertEquals("甲[00:20.00]乙", line.text)
    }

    @Test
    @DisplayName("行标签：连续多标签无正文")
    fun parsesConsecutiveTagsOnly() {
        val lines = LrcParser.parse("[00:10.00][00:20.00]").lines

        assertEquals(listOf(10000L, 20000L), lines.map { it.begin })
        assertEquals("", lines[0].text)
        assertEquals("", lines[1].text)
    }

    @Test
    @DisplayName("行标签：CRLF 与 LF 混合")
    fun parsesMixedLineEndings() {
        val lines = LrcParser.parse("[00:10.00]甲\r\n[00:12.00]乙\n[00:14.00]丙").lines

        assertEquals(3, lines.size)
        assertEquals(listOf(10000L, 12000L, 14000L), lines.map { it.begin })
    }

    @Test
    @DisplayName("词标签：无毫秒")
    fun parsesWordTagWithoutFraction() {
        val line = EnhanceLrcParser.parse("[00:10.00]<00:10>甲<00:10.50>乙").lines.single()

        assertEquals(listOf(10000L, 10500L), line.words!!.map { it.begin })
        assertEquals("甲乙", line.text)
    }

    @Test
    @DisplayName("词标签：混合分隔符与位数")
    fun parsesMixedWordTags() {
        val line = EnhanceLrcParser.parse(
            "[00:10.00]<00:10.5>甲<00:11:50>乙<00 12 500>丙<00:13>丁"
        ).lines.single()

        assertEquals(listOf(10500L, 11500L, 12500L, 13000L), line.words!!.map { it.begin })
        assertEquals("甲乙丙丁", line.text)
    }

    @Test
    @DisplayName("行标签与词标签：格式独立")
    fun mixesLineAndWordFormats() {
        val line = EnhanceLrcParser.parse("[00:10.500]<00:10.5>甲<00:11>乙").lines.single()

        assertEquals(10500L, line.begin)
        assertEquals(listOf(10500L, 11000L), line.words!!.map { it.begin })
    }

    @Test
    @DisplayName("词标签：畸形标签作为正文保留")
    fun keepsMalformedWordTags() {
        val line = EnhanceLrcParser.parse("[00:10.00]<00:10.00>甲<00:10.5x>乙").lines.single()

        assertEquals("甲<00:10.5x>乙", line.text)
        assertEquals(1, line.words?.size)
    }

    @Test
    @DisplayName("词标签：秒溢出边界")
    fun parsesWordTagOverflow() {
        val line = EnhanceLrcParser.parse("[00:10.00]<00:10:99>甲<00:11:00>乙").lines.single()

        assertEquals(listOf(10990L, 11000L), line.words!!.map { it.begin })
        assertEquals("甲乙", line.text)
    }

    @Test
    @DisplayName("时间标签：单行一千个标签")
    fun parsesManyTagsOnSingleLine() {
        val tags = buildString {
            repeat(1000) {
                append('[').append(formatTime(it * 100L)).append(']')
            }
        }

        val lines = LrcParser.parse(tags + "重复").lines

        assertEquals(1000, lines.size)
        assertEquals(0L, lines.first().begin)
        assertEquals(99900L, lines.last().begin)
        assertEquals("重复", lines.first().text)
    }

    /**
     * 将毫秒格式化为两位分钟的时间标签。
     * @param ms 毫秒时间。
     * @return 时间标签文本。
     */
    private fun formatTime(ms: Long): String {
        val minutes = ms / 60_000
        val seconds = ms % 60_000 / 1000
        val fraction = ms % 1000
        return buildString {
            append(minutes.toString().padStart(2, '0'))
            append(':')
            append(seconds.toString().padStart(2, '0'))
            append('.')
            append(fraction.toString().padStart(3, '0'))
        }
    }
}
