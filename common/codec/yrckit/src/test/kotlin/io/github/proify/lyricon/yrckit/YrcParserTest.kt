/*
 * Copyright 2026 Proify, Tomakino
 * Licensed under the Apache License, Version 2.0
 * http://www.apache.org/licenses/LICENSE-2.0
 */

package io.github.proify.lyricon.yrckit

import io.github.proify.lyricon.lyric.model.LyricLine
import kotlinx.serialization.json.Json
import kotlinx.serialization.json.jsonObject
import kotlinx.serialization.json.jsonPrimitive
import kotlin.test.Test
import kotlin.test.assertEquals
import kotlin.test.assertTrue

/**
 * [YrcParser] 的行时间与逐字解析测试。
 */
class YrcParserTest {

    @Test
    fun parsesBasicLine() {
        val line = YrcParser.parse("[1000,2000](1000,500,0)你(1500,500,0)好").single()

        assertEquals(1000L, line.begin)
        assertEquals(3000L, line.end)
        assertEquals(2000L, line.duration)
        assertEquals("你好", line.text)
        assertEquals(2, line.words!!.size)
        assertEquals(500L, line.words!![0].duration)
    }

    @Test
    fun sortsLinesByBegin() {
        val lines = YrcParser.parse(
            "[2000,1000](2000,500,0)乙\n[1000,1000](1000,500,0)甲"
        )

        assertEquals(listOf(1000L, 2000L), lines.map { it.begin })
        assertEquals(listOf("甲", "乙"), lines.map { it.text })
    }

    @Test
    fun keepsFirstLineForSameBegin() {
        val lines = YrcParser.parse(
            "[1000,1000](1000,500,0)甲\n[1000,1000](1000,500,0)乙"
        )

        assertEquals(1, lines.size)
        assertEquals("甲", lines.single().text)
    }

    @Test
    fun skipsEmptyWordText() {
        val line = YrcParser.parse("[1000,1000](1000,500,0)(1500,500,0)好").single()

        assertEquals("好", line.text)
        assertEquals(1, line.words!!.size)
        assertEquals(1500L, line.words!!.single().begin)
    }

    @Test
    fun sortsWordsByBegin() {
        val line = YrcParser.parse("[1000,1000](1500,500,0)好(1000,500,0)你").single()

        assertEquals("你好", line.text)
        assertEquals(listOf(1000L, 1500L), line.words!!.map { it.begin })
    }

    @Test
    fun keepsWhitespaceInWordText() {
        val line = YrcParser.parse("[1000,2000](1000,500,0)Hello (1500,500,0)World").single()

        assertEquals("Hello World", line.text)
        assertEquals("Hello ", line.words!![0].text)
    }

    @Test
    fun ignoresBlankAndJsonLines() {
        assertTrue(YrcParser.parse(null).isEmpty())
        assertTrue(YrcParser.parse("").isEmpty())
        assertTrue(YrcParser.parse("  \n{\"a\":1}\n").isEmpty())
    }

    @Test
    fun keepsHeaderLineWithMalformedWords() {
        val lines = YrcParser.parse("纯文本\n[1000,1000](x,y,0)乙")

        assertEquals(1, lines.size)
        assertEquals("", lines.single().text)
        assertTrue(lines.single().words!!.isEmpty())
    }

    @Test
    fun supportsZeroDuration() {
        val line = YrcParser.parse("[1000,0]").single()

        assertEquals(1000L, line.end)
        assertEquals(0L, line.duration)
        assertEquals("", line.text)
        assertTrue(line.words!!.isEmpty())
    }

    @Test
    fun handlesCrlf() {
        val lines = YrcParser.parse(
            "[1000,1000](1000,500,0)甲\r\n[2000,1000](2000,500,0)乙"
        )

        assertEquals(2, lines.size)
        assertEquals("乙", lines[1].text)
    }

    @Test
    fun parsesRealYrcSample() {
        val lines = YrcParser.parse(readYrcLyric("2.json"))

        assertEquals(60, lines.size)
        assertEquals("编曲：岑思源", lines.first().text)
        assertEquals("发行公司：童年时影视文化（天津）有限公司", lines.last().text)
        assertTimeline(lines)
    }

    @Test
    fun parsesRealYrcSampleWithEnglishSpaces() {
        val lines = YrcParser.parse(readYrcLyric("3.json"))

        assertEquals(49, lines.size)
        assertEquals("I love it when you call me señorita", lines.first().text)
        assertEquals("Ooh, you keep me coming for ya", lines.last().text)
        assertTimeline(lines)
    }

    /**
     * 校验行时间轴与逐字时间窗。
     * @param lines 待校验的歌词行。
     */
    private fun assertTimeline(lines: List<LyricLine>) {
        lines.forEachIndexed { index, line ->
            val where = "第 $index 行"
            assertTrue(line.begin >= 0, "$where begin 为负")
            assertTrue(line.end >= line.begin, "$where end 早于 begin")
            assertEquals(line.end - line.begin, line.duration, "$where duration 不一致")
            assertTrue(line.words!!.isNotEmpty(), "$where 缺少逐字数据")
            assertEquals(
                line.text,
                line.words!!.joinToString("") { it.text.orEmpty() },
                "$where 文本与逐字数据不一致"
            )
            line.words!!.forEach { word ->
                assertTrue(word.begin >= line.begin, "$where 词早于行开始")
                assertTrue(word.end <= line.end, "$where 词晚于行结束")
                assertEquals(word.end - word.begin, word.duration, "$where 词 duration 不一致")
            }
            if (index > 0) {
                assertTrue(lines[index - 1].begin <= line.begin, "$where 未排序")
            }
        }
    }

    /**
     * 读取 classpath 下响应资源中的 YRC 文本。
     * @param name 资源文件名。
     * @return yrc.lyric 内容。
     */
    private fun readYrcLyric(name: String): String {
        val raw = checkNotNull(javaClass.getResourceAsStream("/$name")) { "缺少测试资源：$name" }
            .bufferedReader(Charsets.UTF_8)
            .use { it.readText() }
        return Json.parseToJsonElement(raw).jsonObject["yrc"]
            ?.jsonObject?.get("lyric")
            ?.jsonPrimitive?.content
            ?: error("资源 $name 不含 yrc.lyric")
    }

    @Test
    fun parsesSyllableParameter() {
        val line = YrcParser.parse("[1000,1000](1000,500,7)你").single()

        assertEquals("你", line.text)
        assertEquals(500L, line.words!!.single().duration)
    }

    @Test
    fun keepsWordsWithZeroDuration() {
        val line = YrcParser.parse("[1000,1000](1000,0,0)甲(1000,0,0)乙").single()

        assertEquals("甲乙", line.text)
        assertEquals(2, line.words!!.size)
        assertEquals(0L, line.words!![0].duration)
        assertEquals(0L, line.words!![1].duration)
    }

    @Test
    fun keepsLineWithEmptyText() {
        val line = YrcParser.parse("[1000,1000]").single()

        assertEquals("", line.text)
        assertTrue(line.words!!.isEmpty())
        assertEquals(2000L, line.end)
    }

    @Test
    fun ignoresLinesWithoutHeader() {
        assertTrue(YrcParser.parse("(1000,500,0)甲").isEmpty())
    }

    @Test
    fun ignoresNegativeHeader() {
        assertTrue(YrcParser.parse("[-1000,1000](1000,500,0)甲").isEmpty())
    }

    @Test
    fun handlesHeaderWithOuterWhitespace() {
        val line = YrcParser.parse("  [1000,1000](1000,500,0)甲  ").single()

        assertEquals("甲", line.text)
        assertEquals(1000L, line.begin)
    }

    @Test
    fun keepsMultipleEmptyWordTags() {
        val line = YrcParser.parse("[1000,1000](1000,500,0)(1500,500,0)").single()

        assertEquals("", line.text)
        assertTrue(line.words!!.isEmpty())
    }

    @Test
    fun keepsFirstOfDuplicateBeginWithDifferentDurations() {
        val lines = YrcParser.parse(
            "[1000,500](1000,100,0)甲\n[1000,900](1000,100,0)乙"
        )

        assertEquals(1, lines.size)
        assertEquals("甲", lines.single().text)
        assertEquals(500L, lines.single().duration)
        assertEquals(1500L, lines.single().end)
    }

    @Test
    fun parsesHeaderNotAtLineStart() {
        val line = YrcParser.parse("前缀[1000,1000](1000,500,0)甲").single()

        assertEquals(1000L, line.begin)
        assertEquals("甲", line.text)
    }

    @Test
    fun keepsBracketsInWordText() {
        val line = YrcParser.parse("[1000,1000](1000,500,0)[前](1500,500,0)后").single()

        assertEquals("[前]后", line.text)
        assertEquals("[前]", line.words!![0].text)
    }

    @Test
    fun parsesLargeDocument() {
        val lineCount = 5_000
        val wordsPerLine = 20
        val raw = buildString {
            repeat(lineCount) { index ->
                val begin = index * 2000L
                append('[').append(begin).append(",2000]")
                repeat(wordsPerLine) { wordIndex ->
                    append('(').append(begin + wordIndex * 100L).append(",100,0)")
                    append('字')
                }
                append('\n')
            }
        }

        val started = System.nanoTime()
        val lines = YrcParser.parse(raw)
        val elapsedMs = (System.nanoTime() - started) / 1_000_000
        println("yrc $lineCount lines / $wordsPerLine words: ${elapsedMs}ms")

        assertEquals(lineCount, lines.size)
        assertEquals(lineCount * wordsPerLine, lines.sumOf { it.words?.size ?: 0 })
        lines.forEachIndexed { index, line ->
            assertEquals(index * 2000L, line.begin)
            assertEquals("字".repeat(wordsPerLine), line.text)
        }
        assertTrue(elapsedMs < 20_000)
    }
}
