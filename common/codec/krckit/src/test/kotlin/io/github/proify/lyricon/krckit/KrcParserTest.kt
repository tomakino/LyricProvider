/*
 * Copyright 2026 Proify, Tomakino
 * Licensed under the Apache License, Version 2.0
 * http://www.apache.org/licenses/LICENSE-2.0
 */

package io.github.proify.lyricon.krckit

import io.github.proify.lyricon.krckit.parser.KrcParser
import io.github.proify.lyricon.lyric.model.LyricLine
import kotlin.test.Test
import kotlin.test.assertEquals
import kotlin.test.assertTrue

/**
 * [KrcParser] 的 KRC 行、元数据与逐字解析测试。
 */
class KrcParserTest {

    @Test
    fun parsesJigokuShoujoSample() {
        val document = KrcParser.parse(readResourceFile("極楽浄土.krc"))

        assertEquals("極楽浄土", document.metadata["ti"])
        assertEquals("GARNiDELiA (ガルニデリア)", document.metadata["ar"])
        assertEquals(35, document.lines.size)

        val first = document.lines.first()
        assertEquals(0L, first.begin)
        assertEquals(10790L, first.duration)
        assertEquals("極楽浄土 (极乐净土) - GARNiDELiA", first.text)
        assertEquals(10, first.words?.size)

        assertEquals(194524L, document.lines.last().begin)
        assertTimeline(document.lines)
    }

    @Test
    fun parsesHuangmeiSample() {
        val document = KrcParser.parse(readResourceFile("黄梅戏.krc"))

        assertEquals("黄梅戏", document.metadata["ti"])
        assertEquals("慕容晓晓", document.metadata["ar"])
        assertEquals(43, document.lines.size)

        val first = document.lines.first()
        assertEquals(114L, first.begin)
        assertEquals(1543L, first.duration)
        assertEquals("黄梅戏 - 慕容晓晓", first.text)

        assertEquals(207147L, document.lines.last().begin)
        assertTimeline(document.lines)
    }

    @Test
    fun collectsMultilineMetadata() {
        val content = """
            [language:eyJjb250ZW50IjpbXSwidmVyc2lvbiI6MX0
            =]
            [0,1000]<0,500,0>甲
        """.trimIndent()

        val document = KrcParser.parse(content)

        assertEquals("eyJjb250ZW50IjpbXSwidmVyc2lvbiI6MX0=", document.metadata["language"])
        assertEquals(1, document.lines.size)
    }

    @Test
    fun fallsBackToPlainTextWithoutWordTags() {
        val document = KrcParser.parse("[0,1000]纯文本")

        val line = document.lines.single()
        assertEquals("纯文本", line.text)
        assertTrue(line.words!!.isEmpty())
    }

    @Test
    fun keepsDuplicateBeginLines() {
        val document = KrcParser.parse(
            "[0,1000]<0,500,0>甲\n[0,1000]<0,500,0>乙"
        )

        assertEquals(2, document.lines.size)
        assertEquals(listOf("甲", "乙"), document.lines.map { it.text })
    }

    @Test
    fun sortsLinesByBegin() {
        val document = KrcParser.parse(
            "[2000,1000]<0,500,0>乙\n[1000,1000]<0,500,0>甲"
        )

        assertEquals(listOf(1000L, 2000L), document.lines.map { it.begin })
        assertEquals(listOf("甲", "乙"), document.lines.map { it.text })
    }

    @Test
    fun parsesEmptyInput() {
        assertTrue(KrcParser.parse(null).lines.isEmpty())
        assertTrue(KrcParser.parse("").lines.isEmpty())
        assertTrue(KrcParser.parse("   ").lines.isEmpty())
    }

    @Test
    fun ignoresNumericMetadataKey() {
        val document = KrcParser.parse("[123:456]\n[0,1000]<0,500,0>甲")

        assertTrue(document.metadata.isEmpty())
        assertEquals(1, document.lines.size)
    }

    /**
     * 校验行与逐字时间轴不变量。
     * @param lines 待校验的歌词行。
     */
    private fun assertTimeline(lines: List<LyricLine>) {
        lines.zipWithNext().forEach { (previous, current) ->
            assertTrue(previous.begin <= current.begin)
        }
        lines.forEach { line ->
            assertTrue(line.begin >= 0)
            assertEquals(line.end - line.begin, line.duration)

            val words = line.words!!
            assertEquals(line.text, words.joinToString("") { it.text.orEmpty() })
            words.zipWithNext().forEach { (previous, current) ->
                assertTrue(previous.begin <= current.begin)
            }
            words.forEach { word ->
                assertTrue(word.begin >= line.begin)
                assertTrue(word.end <= line.end)
                assertEquals(word.end - word.begin, word.duration)
            }
        }
    }

    /**
     * 读取 classpath 下的测试资源。
     * @param fileName 资源文件名。
     * @return 资源文本。
     */
    private fun readResourceFile(fileName: String): String {
        val inputStream = javaClass.classLoader.getResourceAsStream(fileName)
            ?: throw IllegalArgumentException("File $fileName not found")
        return inputStream.bufferedReader().use { it.readText() }
    }
}
