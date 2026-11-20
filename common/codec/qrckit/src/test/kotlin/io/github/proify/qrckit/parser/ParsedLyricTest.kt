/*
 * Copyright 2026 Proify, Tomakino
 * Licensed under the Apache License, Version 2.0
 * http://www.apache.org/licenses/LICENSE-2.0
 */

package io.github.proify.qrckit.parser

import io.github.proify.qrckit.parser.model.ParsedLyric
import kotlin.test.Test
import kotlin.test.assertEquals
import kotlin.test.assertNull
import kotlin.test.assertTrue

/**
 * [ParsedLyric] 的歌词、翻译与罗马音合并测试。
 */
class ParsedLyricTest {

    @Test
    fun mapsLrcTextWithTranslationAndRoma() {
        val parsed = ParsedLyric(
            lyricsRaw = "[00:01.000]你\n[00:02.000]好",
            translationRaw = "[00:01.000]译一\n[00:02.000]//",
            romaRaw = "[00:01.000]ro1\n[00:02.000]ro2"
        )

        val lines = parsed.richLyricLines

        assertEquals(2, lines.size)
        assertEquals("你", lines[0].text)
        assertEquals("译一", lines[0].translation)
        assertNull(lines[1].translation)
        assertEquals("ro1", lines[0].roma)
        assertEquals("ro2", lines[1].roma)
    }

    @Test
    fun prefersQrcXmlContent() {
        val xml = """<root><lyric LyricContent="[0,1000]你(0,500)好(500,500)"/></root>"""
        val parsed = ParsedLyric(lyricsRaw = xml)

        val line = parsed.richLyricLines.single()

        assertEquals("你好", line.text)
        assertEquals(2, line.words?.size)
    }

    @Test
    fun blankLyricsReturnsEmpty() {
        assertTrue(ParsedLyric().richLyricLines.isEmpty())
        assertTrue(ParsedLyric(lyricsRaw = " ").richLyricLines.isEmpty())
    }

    @Test
    fun invalidQrcFallsBackToEmptyLrc() {
        assertTrue(ParsedLyric(lyricsRaw = "<root/>").richLyricLines.isEmpty())
    }
}
