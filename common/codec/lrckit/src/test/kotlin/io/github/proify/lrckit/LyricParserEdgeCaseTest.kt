/*
 * Copyright 2026 Proify, Tomakino
 * Licensed under the Apache License, Version 2.0
 * http://www.apache.org/licenses/LICENSE-2.0
 */

package io.github.proify.lrckit

import org.junit.jupiter.api.Assertions.assertEquals
import org.junit.jupiter.api.Assertions.assertNull
import org.junit.jupiter.api.Assertions.assertTrue
import org.junit.jupiter.api.DisplayName
import org.junit.jupiter.api.Test

/**
 * LRC 与增强 LRC 的边界输入验证。
 */
class LyricParserEdgeCaseTest {

    @Test
    @DisplayName("BOM：首个时间行仍可解析")
    fun parsesFirstLineWithBom() {
        val lines = LrcParser.parse("\uFEFF[00:10.00]甲").lines

        assertEquals(1, lines.size)
        assertEquals("甲", lines.single().text)
    }

    @Test
    @DisplayName("仅 CR 换行：正常分行")
    fun splitsOnCarriageReturn() {
        val lines = LrcParser.parse("[00:10.00]甲\r[00:12.00]乙").lines

        assertEquals(2, lines.size)
        assertEquals("乙", lines[1].text)
    }

    @Test
    @DisplayName("仅换行：返回空文档")
    fun returnsEmptyForOnlyNewlines() {
        assertTrue(LrcParser.parse("\n\n\r\n").lines.isEmpty())
    }

    @Test
    @DisplayName("时间标签间有空格：第二个标签作为正文")
    fun treatsSpacedSecondTagAsContent() {
        val line = LrcParser.parse("[00:10.00] [00:20.00]甲").lines.single()

        assertEquals(10000L, line.begin)
        assertEquals("[00:20.00]甲", line.text)
    }

    @Test
    @DisplayName("无毫秒时间标签：按整秒解析")
    fun parsesTagWithoutFraction() {
        val line = LrcParser.parse("[00:10]甲").lines.single()

        assertEquals(10000L, line.begin)
        assertEquals("甲", line.text)
    }

    @Test
    @DisplayName("秒不足两位：不产生歌词行")
    fun ignoresSingleDigitSecond() {
        assertTrue(LrcParser.parse("[00:1.00]甲").lines.isEmpty())
    }

    @Test
    @DisplayName("分钟超过三位：不产生歌词行")
    fun ignoresFourDigitMinute() {
        assertTrue(LrcParser.parse("[1000:00.00]甲").lines.isEmpty())
    }

    @Test
    @DisplayName("秒超过 59：按毫秒换算")
    fun acceptsSecondOverflow() {
        val line = LrcParser.parse("[00:99.00]甲").lines.single()

        assertEquals(99000L, line.begin)
    }

    @Test
    @DisplayName("空标签：忽略")
    fun ignoresEmptyTag() {
        assertTrue(LrcParser.parse("[]").lines.isEmpty())
    }

    @Test
    @DisplayName("时间标签后紧跟右括号：保留为正文")
    fun keepsExtraBracketInContent() {
        val line = LrcParser.parse("[00:10.00]]甲").lines.single()

        assertEquals("]甲", line.text)
    }

    @Test
    @DisplayName("数字形式标签：符合时间格式时按时间解析")
    fun parsesNumericTagAsTime() {
        val line = LrcParser.parse("[123:45.6]甲").lines.single()

        assertEquals(7425600L, line.begin)
    }

    @Test
    @DisplayName("数字形式标签：无毫秒分隔时作为元数据")
    fun numericTagFallsBackToMetadata() {
        val doc = LrcParser.parse("[123:456]")

        assertEquals("456", doc.metadata["123"])
        assertTrue(doc.lines.isEmpty())
    }

    @Test
    @DisplayName("空值元数据：保留空字符串")
    fun keepsEmptyMetadataValue() {
        val doc = LrcParser.parse("[ar:]")

        assertEquals("", doc.metadata["ar"])
    }

    @Test
    @DisplayName("元数据值含冒号：完整保留")
    fun keepsColonInMetadataValue() {
        assertEquals("a:b", LrcParser.parse("[k:a:b]").metadata["k"])
    }

    @Test
    @DisplayName("无冒号标签：忽略")
    fun ignoresTagWithoutColon() {
        val doc = LrcParser.parse("[key]")

        assertTrue(doc.metadata.isEmpty())
        assertTrue(doc.lines.isEmpty())
    }

    @Test
    @DisplayName("重复元数据：后者覆盖前者")
    fun lastMetadataWins() {
        assertEquals("二", LrcParser.parse("[ar:一]\n[ar:二]").metadata["ar"])
    }

    @Test
    @DisplayName("元数据键：保留原始大小写")
    fun keepsMetadataKeyCase() {
        val doc = LrcParser.parse("[Ti:标题]")

        assertEquals("标题", doc.metadata["Ti"])
        assertNull(doc.metadata["ti"])
    }

    @Test
    @DisplayName("offset 非数字：按 0 处理")
    fun ignoresInvalidOffset() {
        val line = LrcParser.parse("[offset:abc]\n[00:10.00]甲").lines.single()

        assertEquals(10000L, line.begin)
    }

    @Test
    @DisplayName("offset 位于末尾：仍作用于全部行")
    fun appliesOffsetDeclaredAtEnd() {
        val line = LrcParser.parse("[00:10.00]甲\n[offset:1000]").lines.single()

        assertEquals(11000L, line.begin)
    }

    @Test
    @DisplayName("重复 offset：后者覆盖前者")
    fun lastOffsetWins() {
        val line = LrcParser.parse("[offset:1000]\n[offset:2000]\n[00:10.00]甲").lines.single()

        assertEquals(12000L, line.begin)
    }

    @Test
    @DisplayName("offset 恰为负的行开始：截断到 0")
    fun clampsOffsetExactlyToZero() {
        val line = LrcParser.parse("[offset:-10000]\n[00:10.00]甲").lines.single()

        assertEquals(0L, line.begin)
        assertEquals(5000L, line.end)
    }

    @Test
    @DisplayName("总时长等于末行开始：回退默认时长")
    fun fallsBackWhenDurationEqualsBegin() {
        val line = LrcParser.parse("[00:10.00]甲", duration = 10000L).lines.single()

        assertEquals(15000L, line.end)
    }

    @Test
    @DisplayName("中间行：结束时间取下一行开始而非总时长")
    fun intermediateLineUsesSuccessor() {
        val lines = LrcParser.parse("[00:10.00]甲\n[00:20.00]乙", duration = 60000L).lines

        assertEquals(20000L, lines[0].end)
        assertEquals(60000L, lines[1].end)
    }

    @Test
    @DisplayName("逐字时间重复：生成零时长词且不越界")
    fun allowsZeroDurationWord() {
        val line = EnhanceLrcParser.parse("[00:10.00]<00:10.00>甲<00:10.00>乙").lines.single()

        val words = line.words!!
        assertEquals("甲乙", line.text)
        assertEquals(2, words.size)
        assertEquals(0L, words[0].duration)
        words.forEach { word ->
            assertEquals(word.end - word.begin, word.duration)
            assertTrue(word.begin >= line.begin)
            assertTrue(word.end <= line.end)
        }
    }

    @Test
    @DisplayName("逐字时间乱序：夹到行窗内，不重排")
    fun keepsOutOfOrderWordsWithinLine() {
        val line = EnhanceLrcParser.parse("[00:10.00]<00:12.00>甲<00:11.00>乙").lines.single()

        val words = line.words!!
        assertEquals("甲乙", line.text)
        assertEquals(listOf(12000L, 11000L), words.map { it.begin })
        words.forEach { word ->
            assertEquals(word.end - word.begin, word.duration)
            assertTrue(word.begin >= line.begin)
            assertTrue(word.end <= line.end)
        }
    }

    @Test
    @DisplayName("首个逐字标签无文本：忽略该标签")
    fun skipsLeadingEmptyWordTag() {
        val line = EnhanceLrcParser.parse("[00:10.00]<00:10.00><00:10.50>甲").lines.single()

        assertEquals("甲", line.text)
        assertEquals(1, line.words?.size)
        assertEquals(10500L, line.words!!.single().begin)
    }
}
