/*
 * Copyright 2026 Proify, Tomakino
 * Licensed under the Apache License, Version 2.0
 * http://www.apache.org/licenses/LICENSE-2.0
 */

package io.github.proify.lrckit

import org.junit.jupiter.api.Assertions.assertEquals
import org.junit.jupiter.api.DisplayName
import org.junit.jupiter.api.Test

/**
 * 歌词文本空格与空白字符的解析验证。
 */
class LyricWhitespaceTest {

    @Test
    @DisplayName("中文逐字：拼接不插入分隔符")
    fun cjkWordsHaveNoSeparator() {
        val line = EnhanceLrcParser.parse("[00:10.00]<00:10.00>你<00:10.50>好").lines.single()

        assertEquals("你好", line.text)
        assertEquals(listOf("你", "好"), line.words!!.map { it.text })
    }

    @Test
    @DisplayName("英文逐字：保留词尾空格")
    fun keepsTrailingSpaceInWord() {
        val line = EnhanceLrcParser.parse("[00:10.00]<00:10.00>Hello <00:10.50>World").lines.single()

        assertEquals("Hello World", line.text)
        assertEquals("Hello ", line.words!![0].text)
        assertEquals("World", line.words!![1].text)
    }

    @Test
    @DisplayName("英文逐字：保留词前导空格")
    fun keepsLeadingSpaceInWord() {
        val line = EnhanceLrcParser.parse("[00:10.00]<00:10.00>Hello<00:10.50> World").lines.single()

        assertEquals("Hello World", line.text)
        assertEquals(" World", line.words!![1].text)
    }

    @Test
    @DisplayName("连续多空格：原样保留")
    fun keepsConsecutiveSpaces() {
        val plain = LrcParser.parse("[00:10.00]a  b").lines.single()
        val enhanced = EnhanceLrcParser.parse("[00:10.00]<00:10.00>a<00:10.50>  b").lines.single()

        assertEquals("a  b", plain.text)
        assertEquals("a  b", enhanced.text)
    }

    @Test
    @DisplayName("全角空格：正文内部保留")
    fun keepsIdeographicSpace() {
        val line = LrcParser.parse("[00:10.00]甲　乙").lines.single()

        assertEquals("甲　乙", line.text)
    }

    @Test
    @DisplayName("制表符：正文内部保留")
    fun keepsTab() {
        val line = LrcParser.parse("[00:10.00]甲\t乙").lines.single()

        assertEquals("甲\t乙", line.text)
    }

    @Test
    @DisplayName("普通行：裁剪首尾空白")
    fun trimsPlainLineWhitespace() {
        val line = LrcParser.parse("[00:10.00]   前 后   ").lines.single()

        assertEquals("前 后", line.text)
    }

    @Test
    @DisplayName("逐字行：保留词标签后的空白")
    fun keepsTrailingWhitespaceAfterWordTag() {
        val line = EnhanceLrcParser.parse("[00:10.00] <00:10.00>前 后   ").lines.single()

        assertEquals("前 后   ", line.text)
        assertEquals(line.text, line.words!!.joinToString("") { it.text.orEmpty() })
    }

    @Test
    @DisplayName("普通 LRC：正文多空格原样保留")
    fun keepsPlainLrcSpaces() {
        val line = LrcParser.parse("[00:10.00]Hello  World").lines.single()

        assertEquals("Hello  World", line.text)
    }

    @Test
    @DisplayName("空白词文本：作为空格保留")
    fun keepsBlankWordText() {
        val line = EnhanceLrcParser.parse("[00:10.00]<00:10.00> <00:10.50>甲").lines.single()

        assertEquals(" 甲", line.text)
        assertEquals(" ", line.words!![0].text)
    }

    @Test
    @DisplayName("不换行空格：不被裁剪")
    fun keepsNonBreakingSpace() {
        val line = LrcParser.parse("[00:10.00]甲\u00A0乙").lines.single()

        assertEquals("甲\u00A0乙", line.text)
    }

    @Test
    @DisplayName("emoji 逐字：代理对完整")
    fun keepsEmojiWords() {
        val line = EnhanceLrcParser.parse("[00:10.00]<00:10.00>😀<00:10.50>😀").lines.single()

        assertEquals("😀😀", line.text)
        assertEquals(2, line.words?.size)
        assertEquals(listOf("😀", "😀"), line.words!!.map { it.text })
    }
}
