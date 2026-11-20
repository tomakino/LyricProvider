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
 * [LrcParser] 的解析测试。
 * 以测试资源中的标准 LRC 为基准，并校验与 [EnhanceLrcParser] 的行级一致性。
 */
class LrcParserTest {

    @Test
    @DisplayName("样本：行时间、文本、结束时间与增强解析器一致")
    fun parsesSampleConsistentlyWithEnhance() {
        val raw = readResource("易燃易爆炸.lrc")
        val plain = LrcParser.parse(raw)
        val enhanced = EnhanceLrcParser.parse(raw)

        assertEquals(31, plain.lines.size)
        assertEquals(enhanced.lines.size, plain.lines.size)

        plain.lines.zip(enhanced.lines).forEachIndexed { index, (actual, expected) ->
            assertEquals(expected.begin, actual.begin, "第 $index 行 begin 不一致")
            assertEquals(expected.end, actual.end, "第 $index 行 end 不一致")
            assertEquals(expected.text, actual.text, "第 $index 行文本不一致")
        }

        val first = plain.lines.first()
        assertEquals(0L, first.begin)
        assertEquals("陈粒 - 易燃易爆炸", first.text)

        val last = plain.lines.last()
        assertEquals(162054L, last.begin)
        assertEquals(167054L, last.end)
        assertEquals(5000L, last.duration)
    }

    @Test
    @DisplayName("时间轴：有序、相邻行首尾相接、时长自洽")
    fun keepsTimelineConsistent() {
        val lines = LrcParser.parse(readResource("易燃易爆炸.lrc")).lines

        lines.forEach { line ->
            assertTrue(line.begin >= 0)
            assertTrue(line.end >= line.begin)
            assertEquals(line.end - line.begin, line.duration)
        }
        lines.zipWithNext().forEach { (previous, current) ->
            assertTrue(previous.begin <= current.begin)
            if (previous.begin < current.begin) {
                assertEquals(current.begin, previous.end)
            }
        }
    }

    @Test
    @DisplayName("多时间标签：每个时间点生成一行")
    fun expandsMultipleTags() {
        val lines = LrcParser.parse("[00:10.00][00:20.00]重复的歌词").lines

        assertEquals(listOf(10000L, 20000L), lines.map { it.begin })
        assertEquals(listOf("重复的歌词", "重复的歌词"), lines.map { it.text })
    }

    @Test
    @DisplayName("正文包含中括号：不参与标签识别")
    fun keepsBracketsInContent() {
        val lines = LrcParser.parse(
            """
            [00:32.99]【/Prologue】
            [00:33.11]何時[人類]向天空築起了[巴別塔]
            [01:15.54][天才]咬下了「苹果」
            """.trimIndent()
        ).lines

        assertEquals(3, lines.size)
        assertEquals("【/Prologue】", lines[0].text)
        assertEquals("何時[人類]向天空築起了[巴別塔]", lines[1].text)
        assertEquals("[天才]咬下了「苹果」", lines[2].text)
        assertEquals(75540L, lines[2].begin)
    }

    @Test
    @DisplayName("时间格式：毫秒位数与分隔符自适应")
    fun adaptsTimeFormat() {
        val digits = LrcParser.parse(
            """
            [00:01.5]一位
            [00:02.50]两位
            [00:03.500]三位
            """.trimIndent()
        ).lines
        assertEquals(listOf(1500L, 2500L, 3500L), digits.map { it.begin })

        val separators = LrcParser.parse(
            """
            [00:01:50]冒号
            [00 02 50]空格
            [00.03.50]点号
            """.trimIndent()
        ).lines
        assertEquals(listOf(1500L, 2500L, 3500L), separators.map { it.begin })
    }

    @Test
    @DisplayName("offset：行时间与增强解析器同步平移")
    fun appliesOffset() {
        val raw = """
            [offset:1000]
            [00:10.00]歌词
            [00:12.00]第二行
        """.trimIndent()

        val plain = LrcParser.parse(raw)
        val enhanced = EnhanceLrcParser.parse(raw)

        assertEquals(11000L, plain.lines[0].begin)
        assertEquals(13000L, plain.lines[0].end)
        plain.lines.zip(enhanced.lines).forEach { (actual, expected) ->
            assertEquals(expected.begin, actual.begin)
            assertEquals(expected.end, actual.end)
        }
    }

    @Test
    @DisplayName("总时长：修正末行结束时间")
    fun usesTotalDurationForLastLine() {
        val line = LrcParser.parse("[00:50.00]最后一行", duration = 60000L).lines.single()

        assertEquals(60000L, line.end)
        assertEquals(10000L, line.duration)
    }

    @Test
    @DisplayName("空输入与非法行：忽略")
    fun ignoresBlankAndMalformedInput() {
        assertTrue(LrcParser.parse(null).lines.isEmpty())
        assertTrue(LrcParser.parse("").lines.isEmpty())

        val lines = LrcParser.parse(
            """
            纯文本没有标签
            [invalid]格式错误
            [00:01.00]正常行
            """.trimIndent()
        ).lines
        assertEquals(1, lines.size)
        assertEquals("正常行", lines[0].text)
    }

    @Test
    @DisplayName("空行间奏：保留空文本行")
    fun keepsInterludeLine() {
        val lines = LrcParser.parse("[00:00.85]\n[00:05.00]内容").lines

        assertEquals(2, lines.size)
        assertEquals("", lines[0].text)
        assertEquals(5000L, lines[0].end)
        assertEquals("内容", lines[1].text)
    }

    @Test
    @DisplayName("元数据：提取 [key:value]")
    fun parsesMetadata() {
        val doc = LrcParser.parse(
            """
            [ti:测试歌曲]
            [ar:歌手]
            [00:10.00]第一行
            """.trimIndent()
        )

        assertEquals("测试歌曲", doc.metadata["ti"])
        assertEquals("歌手", doc.metadata["ar"])
    }

    @Test
    @DisplayName("逐字标签：行文本与增强解析器一致，但不产出 words")
    fun stripsWordTagsWithoutWords() {
        val raw = "[00:10.00]<00:10.00>Hello <00:10.50>World"
        val plain = LrcParser.parse(raw).lines.single()
        val enhanced = EnhanceLrcParser.parse(raw).lines.single()

        assertEquals("Hello World", plain.text)
        assertEquals(enhanced.text, plain.text)
        assertEquals(enhanced.begin, plain.begin)
        assertEquals(enhanced.end, plain.end)
        assertNull(plain.words)
    }

    @Test
    @DisplayName("负 offset：行开始时间截断到 0")
    fun clampsNegativeOffsetAtZero() {
        val lines = LrcParser.parse(
            """
            [offset:-1500]
            [00:01.00]甲
            [00:02.00]乙
            """.trimIndent()
        ).lines

        assertEquals(0L, lines[0].begin)
        assertEquals(500L, lines[0].end)
        assertEquals(500L, lines[1].begin)
    }

    @Test
    @DisplayName("同一时间点：只保留首条")
    fun keepsFirstLineForSameBegin() {
        val lines = LrcParser.parse(
            """
            [00:10.00]甲
            [00:10.00]乙
            [00:20.00]丙
            """.trimIndent()
        ).lines

        assertEquals(2, lines.size)
        assertEquals("甲", lines[0].text)
        assertEquals(10000L, lines[0].begin)
        assertEquals(20000L, lines[0].end)
        assertEquals(20000L, lines[1].begin)
    }

    @Test
    @DisplayName("重复时间戳：保留首条，丢弃后续")
    fun keepsFirstLineForDuplicateTimestamp() {
        val lines = LrcParser.parse(
            "[02:37.904]由我美丽 还由我贪恋着迷\n[02:37.904]xxxxxx"
        ).lines

        assertEquals(1, lines.size)
        assertEquals("由我美丽 还由我贪恋着迷", lines.single().text)
    }

    @Test
    @DisplayName("多时间标签与 offset 组合")
    fun combinesMultipleTagsWithOffset() {
        val lines = LrcParser.parse(
            """
            [offset:1000]
            [00:10.00][00:20.00]重复
            """.trimIndent()
        ).lines

        assertEquals(listOf(11000L, 21000L), lines.map { it.begin })
        assertEquals(21000L, lines[0].end)
        assertEquals(26000L, lines[1].end)
    }

    @Test
    @DisplayName("总时长早于末行：回退到默认持续时长")
    fun fallsBackWhenDurationBeforeLastLine() {
        val line = LrcParser.parse("[00:50.00]最后一行", duration = 10000L).lines.single()

        assertEquals(55000L, line.end)
    }

    @Test
    @DisplayName("三位分钟：时间标签可解析")
    fun parsesThreeDigitMinutes() {
        val line = LrcParser.parse("[123:45.678]长歌曲").lines.single()

        assertEquals(7425678L, line.begin)
    }

    @Test
    @DisplayName("CRLF 与缺少末尾换行：正常解析")
    fun handlesCrlfAndNoTrailingNewline() {
        val lines = LrcParser.parse("[00:10.00]第一行\r\n[00:12.00]第二行").lines

        assertEquals(2, lines.size)
        assertEquals("第一行", lines[0].text)
        assertEquals("第二行", lines[1].text)
    }

    @Test
    @DisplayName("offset 元数据：键名大小写不敏感")
    fun acceptsCaseInsensitiveOffset() {
        val line = LrcParser.parse("[Offset:1000]\n[00:10.00]歌词").lines.single()

        assertEquals(11000L, line.begin)
    }

    @Test
    @DisplayName("只有时间标签：保留空文本行")
    fun lineWithOnlyTag() {
        val line = LrcParser.parse("[00:10.00]").lines.single()

        assertEquals("", line.text)
        assertNull(line.words)
    }

    @Test
    @DisplayName("非法毫秒位数：整行忽略")
    fun ignoresInvalidFractionDigits() {
        assertTrue(LrcParser.parse("[00:10.0000]无效").lines.isEmpty())
    }

    @Test
    @DisplayName("空白行与纯文本：忽略且不影响时间轴")
    fun ignoresBlankAndPlainLines() {
        val lines = LrcParser.parse("  \n纯文本\n[00:10.00]甲\n   \n[00:12.00]乙").lines

        assertEquals(2, lines.size)
        assertEquals(10000L, lines[0].begin)
        assertEquals(12000L, lines[1].begin)
    }

    /**
     * 读取 classpath 下的测试资源。
     * @param name 资源文件名。
     * @return 资源文本。
     */
    private fun readResource(name: String): String =
        checkNotNull(javaClass.getResourceAsStream("/$name")) { "缺少测试资源：$name" }
            .bufferedReader(Charsets.UTF_8)
            .use { it.readText() }
}
