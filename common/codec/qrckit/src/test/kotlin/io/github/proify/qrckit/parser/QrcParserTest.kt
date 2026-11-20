/*
 * Copyright 2026 Proify, Tomakino
 * Licensed under the Apache License, Version 2.0
 * http://www.apache.org/licenses/LICENSE-2.0
 */

package io.github.proify.qrckit.parser

import kotlin.test.Test
import kotlin.test.assertEquals
import kotlin.test.assertTrue

/**
 * [QrcParser] 的行级、逐字与 XML 解析测试。
 */
class QrcParserTest {

    @Test
    fun parsesBasicLine() {
        val (metadata, lines) = QrcParser.parseLyric("[0,1000]你(0,500)好(500,500)")

        assertTrue(metadata.isEmpty())
        val line = lines.single()
        assertEquals(0L, line.begin)
        assertEquals(1000L, line.end)
        assertEquals(1000L, line.duration)
        assertEquals("你好", line.text)
        assertEquals(listOf("你", "好"), line.words!!.map { it.text })
        assertEquals(listOf(0L, 500L), line.words!!.map { it.begin })
    }

    @Test
    fun parsesMetadata() {
        val (metadata, lines) = QrcParser.parseLyric(
            "[ti:标题]\n[ar:歌手]\n[0,1000]你(0,500)"
        )

        assertEquals("标题", metadata["ti"])
        assertEquals("歌手", metadata["ar"])
        assertEquals(1, lines.size)
    }

    @Test
    fun sortsLinesByBegin() {
        val (_, lines) = QrcParser.parseLyric(
            "[2000,1000]乙(2000,500)\n[1000,1000]甲(1000,500)"
        )

        assertEquals(listOf(1000L, 2000L), lines.map { it.begin })
        assertEquals(listOf("甲", "乙"), lines.map { it.text })
    }

    @Test
    fun fallsBackToPlainTextWithoutWordTags() {
        val (_, lines) = QrcParser.parseLyric("[0,1000]纯文本")

        val line = lines.single()
        assertEquals("纯文本", line.text)
        assertTrue(line.words!!.isEmpty())
    }

    @Test
    fun trimsLineBreaksInsideBody() {
        val (_, lines) = QrcParser.parseLyric("[0,1000]\n你(0,500)\n")

        assertEquals("你", lines.single().text)
    }

    @Test
    fun parsesEmptyInput() {
        assertTrue(QrcParser.parseLyric("").first.isEmpty())
        assertTrue(QrcParser.parseLyric("").second.isEmpty())
        assertTrue(QrcParser.parseXml(null).isEmpty())
        assertTrue(QrcParser.parseXml("").isEmpty())
    }

    @Test
    fun parsesXmlContent() {
        val xml = """
            <root>
              <lyric LyricContent="[0,1000]你(0,500)好(500,500)"/>
              <lyric LyricContent="[1000,1000]再(1000,500)见(1500,500)"/>
            </root>
        """.trimIndent()

        val documents = QrcParser.parseXml(xml)

        assertEquals(2, documents.size)
        assertEquals("你好", documents[0].lines.single().text)
        assertEquals("再见", documents[1].lines.single().text)
    }

    @Test
    fun decodesXmlEntities() {
        val xml = """<root><lyric LyricContent="[0,1000]a&amp;b(0,500)"/></root>"""

        val line = QrcParser.parseXml(xml).single().lines.single()

        assertEquals("a&b", line.text)
    }

    @Test
    fun skipsBlankAndKeepsValidContent() {
        val xml = """
            <root>
              <lyric LyricContent="   "/>
              <lyric LyricContent="[0,1000]有效(0,500)"/>
            </root>
        """.trimIndent()

        val documents = QrcParser.parseXml(xml)

        assertEquals(1, documents.size)
        assertEquals("有效", documents.single().lines.single().text)
    }
}
