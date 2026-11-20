/*
 * Copyright 2026 Proify, Tomakino
 * Licensed under the Apache License, Version 2.0
 * http://www.apache.org/licenses/LICENSE-2.0
 */

package io.github.proify.lrckit

import org.junit.jupiter.api.Assertions.assertEquals
import org.junit.jupiter.api.Assertions.assertNotNull
import org.junit.jupiter.api.Assertions.assertNull
import org.junit.jupiter.api.Assertions.assertTrue
import org.junit.jupiter.api.DisplayName
import org.junit.jupiter.api.Test

/**
 * [EnhanceLrcParser] 的解析测试。
 * 以测试资源中的增强 LRC 与同名标准 LRC 为基准。
 */
class EnhanceLrcParserTest {

    @Test
    @DisplayName("样本：逐字解析与同名 LRC 的行时间、文本一致")
    fun parsesSampleAgainstPlainLrc() {
        val enhanced = EnhanceLrcParser.parse(readResource("易燃易爆炸.elrc"))
        val plain = LrcParser.parse(readResource("易燃易爆炸.lrc"))

        assertEquals(31, enhanced.lines.size)
        assertEquals(plain.lines.size, enhanced.lines.size)

        enhanced.lines.zip(plain.lines).forEachIndexed { index, (actual, expected) ->
            assertEquals(expected.begin, actual.begin, "第 $index 行 begin 不一致")
            assertEquals(expected.text, actual.text, "第 $index 行文本不一致")
        }

        enhanced.lines.forEachIndexed { index, line ->
            val words = line.words
            assertNotNull(words, "第 $index 行缺少逐字数据")
            assertTrue(words!!.isNotEmpty(), "第 $index 行逐字数据为空")
            assertEquals(
                line.text,
                words.joinToString("") { it.text.orEmpty() },
                "第 $index 行文本与逐字数据不一致"
            )
            assertEquals(line.end, words.last().end, "第 $index 行末词未对齐行结束时间")

            words.zipWithNext().forEach { (previous, current) ->
                assertTrue(
                    previous.begin <= current.begin,
                    "第 $index 行词时间轴未按 begin 排序"
                )
                assertEquals(current.begin, previous.end, "第 $index 行词时间轴出现空隙或重叠")
            }
            words.forEach { word ->
                assertTrue(word.begin >= line.begin, "第 $index 行词开始早于行开始")
                assertTrue(word.end <= line.end, "第 $index 行词结束晚于行结束")
                assertEquals(word.end - word.begin, word.duration, "第 $index 行词 duration 不一致")
            }
        }

        val first = enhanced.lines.first()
        assertEquals(0L, first.begin)
        assertEquals(5460L, first.end)
        assertEquals("陈粒 - 易燃易爆炸", first.text)
        assertEquals("陈", first.words!!.first().text)
        assertEquals(682L, first.words!!.first().duration)

        val last = enhanced.lines.last()
        assertEquals(162054L, last.begin)
        assertEquals(167043L, last.end)
    }

    @Test
    @DisplayName("标准 LRC：无逐字标签时不产生 words")
    fun parsesPlainLineWithoutWords() {
        val line = EnhanceLrcParser.parse("[00:10.20]第一行歌词").lines.single()

        assertEquals(10200L, line.begin)
        assertEquals("第一行歌词", line.text)
        assertNull(line.words)
    }

    @Test
    @DisplayName("行尾无文本标签：作为行结束时间且不生成空词")
    fun trailingTagBecomesLineEnd() {
        val doc = EnhanceLrcParser.parse(
            """
            [00:10.00]<00:10.00>甲<00:12.00>
            [00:20.00]尾行
            """.trimIndent()
        )

        val line = doc.lines.first()
        assertEquals(12000L, line.end)
        assertEquals(1, line.words?.size)
        assertEquals("甲", line.text)
    }

    @Test
    @DisplayName("多行时间标签：每个时间点生成一行")
    fun expandsMultipleLineTags() {
        val lines = EnhanceLrcParser.parse("[00:10.00][00:20.00]重复的歌词").lines

        assertEquals(listOf(10000L, 20000L), lines.map { it.begin })
        assertTrue(lines.all { it.text == "重复的歌词" })
    }

    @Test
    @DisplayName("正文包含中括号：不参与时间标签识别")
    fun keepsBracketsInContent() {
        val line = EnhanceLrcParser.parse("[00:33.11]何時[人類]向天空築起了[巴別塔]").lines.single()

        assertEquals("何時[人類]向天空築起了[巴別塔]", line.text)
        assertNull(line.words)
    }

    @Test
    @DisplayName("offset 元数据：行与逐字时间同步平移")
    fun appliesOffsetToLineAndWords() {
        val doc = EnhanceLrcParser.parse(
            """
            [offset:1000]
            [00:10.00]<00:10.00>甲<00:10.50>乙
            [00:12.00]尾行
            """.trimIndent()
        )

        val line = doc.lines.first()
        assertEquals(11000L, line.begin)
        assertEquals(13000L, line.end)
        assertEquals(listOf(11000L, 11500L), line.words!!.map { it.begin })
        assertEquals(listOf(11500L, 13000L), line.words!!.map { it.end })
    }

    @Test
    @DisplayName("总时长：修正末行结束时间")
    fun usesTotalDurationForLastLine() {
        val line = EnhanceLrcParser.parse("[00:50.00]最后一行", duration = 60000L).lines.single()

        assertEquals(60000L, line.end)
        assertEquals(10000L, line.duration)
    }

    @Test
    @DisplayName("空输入：返回空文档")
    fun returnsEmptyDocumentForBlankInput() {
        assertTrue(EnhanceLrcParser.parse(null).lines.isEmpty())
        assertTrue(EnhanceLrcParser.parse("").lines.isEmpty())
        assertTrue(EnhanceLrcParser.parse("   ").lines.isEmpty())
    }

    @Test
    @DisplayName("空行间奏：保留空文本行")
    fun keepsInterludeLine() {
        val lines = EnhanceLrcParser.parse("[00:00.85]\n[00:05.00]内容").lines

        assertEquals(2, lines.size)
        assertEquals("", lines[0].text)
        assertNull(lines[0].words)
        assertEquals(5000L, lines[0].end)
        assertEquals("内容", lines[1].text)
    }

    @Test
    @DisplayName("元数据：提取 [key:value]")
    fun parsesMetadata() {
        val doc = EnhanceLrcParser.parse(
            """
            [ti:测试歌曲]
            [ar:歌手]
            [00:10.00]第一行
            """.trimIndent()
        )

        assertEquals("测试歌曲", doc.metadata["ti"])
        assertEquals("歌手", doc.metadata["ar"])
        assertEquals("第一行", doc.lines.single().text)
    }

    @Test
    @DisplayName("行尾标签前的正文：保留为行文本")
    fun keepsTextBeforeTrailingTag() {
        val line = EnhanceLrcParser.parse("[00:10.00]abc<00:12.00>").lines.single()

        assertEquals("abc", line.text)
        assertNull(line.words)
        assertEquals(12000L, line.end)
    }

    @Test
    @DisplayName("负 offset：行与逐字时间截断到 0")
    fun clampsNegativeOffsetAtZero() {
        val lines = EnhanceLrcParser.parse(
            """
            [offset:-1500]
            [00:01.00]<00:01.00>甲<00:01.50>乙
            [00:02.00]尾行
            """.trimIndent()
        ).lines

        assertEquals(0L, lines[0].begin)
        assertEquals(500L, lines[0].end)
        assertEquals(500L, lines[1].begin)
        assertEquals(0L, lines[0].words!!.first().begin)
    }

    @Test
    @DisplayName("中间空逐字标签：只作为时间边界，不生成空词")
    fun skipsEmptyWordTag() {
        val line = EnhanceLrcParser.parse(
            "[00:10.00]<00:10.00>甲<00:10.30><00:10.60>乙"
        ).lines.single()

        assertEquals("甲乙", line.text)
        assertEquals(2, line.words?.size)
        assertEquals(600L, line.words!!.first().duration)
    }

    @Test
    @DisplayName("逐字时间越界：夹到行时间窗内")
    fun clampsWordTimesToLineWindow() {
        val line = EnhanceLrcParser.parse(
            "[00:10.00]<00:09.00>前<00:13.00>后\n[00:12.00]尾行"
        ).lines.first()

        assertEquals(10000L, line.begin)
        assertEquals(12000L, line.end)
        line.words!!.forEach { word ->
            assertTrue(word.begin >= line.begin)
            assertTrue(word.end <= line.end)
            assertEquals(word.end - word.begin, word.duration)
        }
    }

    @Test
    @DisplayName("同一时间点：只保留首条")
    fun keepsFirstLineForSameBegin() {
        val lines = EnhanceLrcParser.parse(
            """
            [00:10.00]<00:10.00>甲
            [00:10.00]<00:10.00>乙
            [00:20.00]尾行
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
        val lines = EnhanceLrcParser.parse(
            "[02:37.904]由我美丽 还由我贪恋着迷\n[02:37.904]xxxxxx"
        ).lines

        assertEquals(1, lines.size)
        assertEquals("由我美丽 还由我贪恋着迷", lines.single().text)
    }

    @Test
    @DisplayName("多行时间标签：行内结束标签不参与结束时间")
    fun multiTagIgnoresEndTag() {
        val lines = EnhanceLrcParser.parse(
            "[00:10.00][00:20.00]<00:10.00>词<00:15.00>\n[00:30.00]尾行"
        ).lines

        assertEquals(3, lines.size)
        assertEquals(20000L, lines[0].end)
        assertEquals(30000L, lines[1].end)
    }

    @Test
    @DisplayName("结束标签早于行开始：忽略该标签")
    fun ignoresEndTagBeforeBegin() {
        val line = EnhanceLrcParser.parse(
            "[00:10.00]<00:10.00>甲<00:05.00>\n[00:20.00]尾行"
        ).lines.first()

        assertEquals(20000L, line.end)
        assertEquals(1, line.words?.size)
    }

    @Test
    @DisplayName("行内结束标签优先于下一行开始")
    fun prefersEndTagOverNextBegin() {
        val line = EnhanceLrcParser.parse(
            "[00:10.00]<00:10.00>甲<00:15.00>\n[00:12.00]尾行"
        ).lines.first()

        assertEquals(15000L, line.end)
    }

    @Test
    @DisplayName("逐字时间格式：毫秒位数与分隔符自适应")
    fun adaptsWordTimeFormat() {
        val line = EnhanceLrcParser.parse(
            "[00:10.00]<00:10.5>甲<00:11:50>乙<00 12 500>丙"
        ).lines.single()

        assertEquals(listOf(10500L, 11500L, 12500L), line.words!!.map { it.begin })
        assertEquals("甲乙丙", line.text)
    }

    @Test
    @DisplayName("正文中的尖括号：非时间格式时不剥离")
    fun keepsNonTagAngleBrackets() {
        val line = EnhanceLrcParser.parse("[00:10.00]<00:10.00>a<b>c<00:10.50>d").lines.single()

        assertEquals("a<b>cd", line.text)
        assertEquals("a<b>c", line.words!!.first().text)
    }

    @Test
    @DisplayName("只有逐字标签：保留空文本行")
    fun lineWithOnlyWordTag() {
        val line = EnhanceLrcParser.parse("[00:10.00]<00:10.00>").lines.single()

        assertEquals("", line.text)
        assertNull(line.words)
    }

    @Test
    @DisplayName("CRLF 与缺少末尾换行：正常解析")
    fun handlesCrlfAndNoTrailingNewline() {
        val lines = EnhanceLrcParser.parse("[00:10.00]第一行\r\n[00:12.00]第二行").lines

        assertEquals(2, lines.size)
        assertEquals("第一行", lines[0].text)
        assertEquals("第二行", lines[1].text)
    }

    @Test
    @DisplayName("offset 元数据：键名大小写不敏感")
    fun acceptsCaseInsensitiveOffset() {
        val line = EnhanceLrcParser.parse("[Offset:1000]\n[00:10.00]歌词").lines.single()

        assertEquals(11000L, line.begin)
    }

    @Test
    @DisplayName("总时长早于末行：回退到默认持续时长")
    fun fallsBackWhenDurationBeforeLastLine() {
        val line = EnhanceLrcParser.parse("[00:50.00]最后一行", duration = 10000L).lines.single()

        assertEquals(55000L, line.end)
    }

    @Test
    @DisplayName("三位分钟：时间标签可解析")
    fun parsesThreeDigitMinutes() {
        val line = EnhanceLrcParser.parse("[123:45.678]长歌曲").lines.single()

        assertEquals(7425678L, line.begin)
    }

    @Test
    @DisplayName("首词前的正文：并入首词文本")
    fun attachesLeadingTextToFirstWord() {
        val line = EnhanceLrcParser.parse("[00:10.00]前<00:10.00>甲").lines.single()

        assertEquals("前甲", line.text)
        assertEquals("前甲", line.words!!.first().text)
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
