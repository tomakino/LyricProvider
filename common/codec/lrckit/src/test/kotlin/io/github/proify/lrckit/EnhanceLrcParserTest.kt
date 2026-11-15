/*
 * Copyright 2026 Proify, Tomakino
 * Licensed under the Apache License, Version 2.0
 * http://www.apache.org/licenses/LICENSE-2.0
 */

package io.github.proify.lrckit

import org.junit.jupiter.api.Assertions.assertEquals
import org.junit.jupiter.api.Assertions.assertFalse
import org.junit.jupiter.api.Assertions.assertNotNull
import org.junit.jupiter.api.Assertions.assertTrue
import org.junit.jupiter.api.DisplayName
import org.junit.jupiter.api.Test

class EnhanceLrcParserTest {

    @Test
    @DisplayName("解析标准LRC格式 - 验证基础时间轴和文本")
    fun testParseStandardLrc() {
        val lrc = """
            [ti:测试歌曲]
            [00:10.20]第一行歌词
            [00:12.50]第二行歌词
        """.trimIndent()

        val doc = EnhanceLrcParser.parse(lrc)

        assertEquals("测试歌曲", doc.metadata["ti"])
        assertEquals(2, doc.lines.size)
        assertEquals(10200L, doc.lines[0].begin)
        assertEquals("第一行歌词", doc.lines[0].text)
        assertEquals(12500L, doc.lines[1].begin)
    }

    @Test
    @DisplayName("解析复杂正文 - 验证包含[]和/的内容")
    fun testParseTextWithBrackets() {
        val lrc = """
            [00:32.99]【/Prologue】
            [00:33.11]何時[人類]向天空築起了[巴別塔]
            [01:15.54][天才]咬下了「苹果」
        """.trimIndent()

        val doc = EnhanceLrcParser.parse(lrc)

        assertEquals(3, doc.lines.size)
        assertEquals("【/Prologue】", doc.lines[0].text)
        assertEquals("何時[人類]向天空築起了[巴別塔]", doc.lines[1].text)
        assertEquals("[天才]咬下了「苹果」", doc.lines[2].text)
        assertEquals(75540L, doc.lines[2].begin)
    }

    @Test
    @DisplayName("解析多时间戳行 - 验证同一行歌词对应多个时间点")
    fun testParseMultipleTags() {
        val lrc = "[00:10.00][00:20.00]重复的歌词"
        val doc = EnhanceLrcParser.parse(lrc)

        // 应该解析为两条独立的行，时间不同但文本相同
        assertEquals(2, doc.lines.size)
        assertEquals(10000L, doc.lines[0].begin)
        assertEquals(20000L, doc.lines[1].begin)
        assertEquals("重复的歌词", doc.lines[0].text)
        assertEquals("重复的歌词", doc.lines[1].text)
    }

    @Test
    @DisplayName("解析逐字时间轴 - 验证LyricWord解析")
    fun testParseKaraokeWords() {
        val lrc = "[00:10.00]<00:10.00>Hello <00:10.50>World"
        val doc = EnhanceLrcParser.parse(lrc)

        val line = doc.lines[0]
        assertNotNull(line.words)
        assertEquals(2, line.words?.size)
        assertEquals("Hello ", line.words?.get(0)?.text)
        assertEquals(500L, line.words?.get(0)?.duration)
        assertEquals("World", line.words?.get(1)?.text)
    }

    @Test
    @DisplayName("解析角色标签 - 验证主唱与背景音逻辑")
    fun testParseRolesAndAlignment() {
        val lrc = """
            [00:10.00]v1: 主唱内容
            [00:12.00]bg: 背景伴唱
        """.trimIndent()

        val doc = EnhanceLrcParser.parse(lrc)

        // 第一行作为主角色，默认不靠右
        assertEquals("主唱内容", doc.lines[0].text)
        assertFalse(doc.lines[0].isAlignedRight)

        // bg 角色应该被识别为靠右
        assertEquals("背景伴唱", doc.lines[1].text)
        assertTrue(doc.lines[1].isAlignedRight)
    }

    @Test
    @DisplayName("解析独立bg元数据 - 验证与上一行合并")
    fun testHandleBackgroundMeta() {
        val lrc = """
            [00:10.00]Hello World
            [bg: (Background Voice)]
        """.trimIndent()

        val doc = EnhanceLrcParser.parse(lrc)

        assertEquals(1, doc.lines.size)
        val line = doc.lines[0]
        assertEquals("Hello World", line.text)
        assertEquals("(Background Voice)", line.secondary)
    }

    @Test
    @DisplayName("验证时间补全 - 确保最后一行有duration")
    fun testFinalizeDuration() {
        val totalDuration = 60000L
        val lrc = "[00:50.00]最后一行"

        val doc = EnhanceLrcParser.parse(lrc, duration = totalDuration)

        val lastLine = doc.lines.last()
        assertEquals(50000L, lastLine.begin)
        assertEquals(60000L, lastLine.end)
        assertEquals(10000L, lastLine.duration)
    }

    @Test
    @DisplayName("验证Offset偏移量 - 验证整体时间移动")
    fun testApplyOffset() {
        val lrc = """
            [offset:1000]
            [00:10.00]歌词
        """.trimIndent()

        val doc = EnhanceLrcParser.parse(lrc)

        // offset 为正表示整体延迟，即时间戳变大
        assertEquals(11000L, doc.lines[0].begin)
    }

    @Test
    @DisplayName("逐字文本拼接 - 中文逐字不得插入分隔符")
    fun testWordLevelTextHasNoSeparator() {
        val cjk =
            EnhanceLrcParser.parse("[00:10.00]<00:10.00>你<00:10.50>好<00:11.00>世<00:11.50>界")

        assertEquals("你好世界", cjk.lines[0].text)
        // 文本必须与逐字数据直接拼接结果一致（与 RichLyricLine.normalize 保持一致）
        assertEquals(cjk.lines[0].text, cjk.lines[0].words?.joinToString("") { it.text.orEmpty() })

        // 英文逐字自带的空格必须保留，但不得被额外插入空格
        val english = EnhanceLrcParser.parse("[00:10.00]<00:10.00>Hello <00:10.50>World")
        assertEquals("Hello World", english.lines[0].text)
    }

    @Test
    @DisplayName("逐字末词 - 结束时间对齐行结束时间")
    fun testLastWordAlignedToLineEnd() {
        val lrc = """
            [00:10.00]<00:10.00>甲<00:10.50>乙
            [00:12.00]尾行
        """.trimIndent()

        val line = EnhanceLrcParser.parse(lrc).lines[0]
        val last = line.words!!.last()

        assertEquals(12000L, line.end)
        assertEquals(line.end, last.end)
        assertEquals(1500L, last.duration)
    }

    @Test
    @DisplayName("行结束时间 - 不被末词结束时间覆盖")
    fun testLineEndNotOverriddenByLastWord() {
        val lrc = """
            [00:10.00]<00:10.00>甲
            [00:30.00]乙
        """.trimIndent()

        val line = EnhanceLrcParser.parse(lrc).lines[0]

        assertEquals(30000L, line.end)
        assertEquals(20000L, line.duration)
        assertEquals(30000L, line.words!!.last().end)
    }

    @Test
    @DisplayName("多段和声 - 按换行追加而非相互覆盖")
    fun testSecondaryAppendedNotOverwritten() {
        val lrc = """
            [00:10.00]v1: 主唱
            [00:10.00]bg: 和声一
            [00:10.00]bg: 和声二
        """.trimIndent()

        val doc = EnhanceLrcParser.parse(lrc)

        assertEquals(1, doc.lines.size)
        val line = doc.lines[0]
        assertEquals("主唱", line.text)
        assertEquals("和声一\n和声二", line.secondary)
        assertTrue(line.isAlignedRight)
    }
}