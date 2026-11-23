/*
 * Copyright 2026 Proify, Tomakino
 * Licensed under the Apache License, Version 2.0
 * http://www.apache.org/licenses/LICENSE-2.0
 */

package io.github.proify.lyricon.qishuikit.parser

import kotlin.test.Test
import kotlin.test.assertEquals
import kotlin.test.assertTrue

class KrcLyricParserTest {

    @Test
    fun parsesLineTimeAndWords() {
        val lines = KrcLyricParser.parse("[1000,2000]<0,500,0>你<500,500,0>好")

        assertEquals(1, lines.size)
        val line = lines[0]
        assertEquals(1000L, line.begin)
        assertEquals(3000L, line.end)
        assertEquals(2000L, line.duration)
        assertEquals("你好", line.text)

        val words = line.words ?: emptyList()
        assertEquals(2, words.size)
        assertEquals("你", words[0].text)
        assertEquals(1000L, words[0].begin)
        assertEquals(1500L, words[0].end)
        assertEquals("好", words[1].text)
        assertEquals(1500L, words[1].begin)
        assertEquals(2000L, words[1].end)
    }

    @Test
    fun infersUntaggedCharacters() {
        val lines = KrcLyricParser.parse("[0,1000]<0,500,0>ab")

        assertEquals(1, lines.size)
        val line = lines[0]
        assertEquals("ab", line.text)

        val words = line.words ?: emptyList()
        assertEquals(2, words.size)
        assertEquals(500L, words[1].begin)
        assertEquals(500L, words[1].end)
        assertEquals(0L, words[1].duration)
    }

    @Test
    fun doesNotInferUntaggedCharactersWhenDisabled() {
        val lines = KrcLyricParser.parse("[0,1000]<0,500,0>ab", inferUntaggedTime = false)

        val words = lines[0].words ?: emptyList()
        assertEquals(2, words.size)
        assertEquals(0L, words[1].begin)
    }

    @Test
    fun skipsLinesWithoutTime() {
        val lines = KrcLyricParser.parse("no time here\n[1000,500]<0,250,0>x")

        assertEquals(1, lines.size)
        assertEquals(1000L, lines[0].begin)
        assertEquals("x", lines[0].text)
    }

    @Test
    fun parsesMultipleLines() {
        val lines = KrcLyricParser.parse("[0,1000]<0,500,0>a\n[1000,1000]<0,500,0>b")

        assertEquals(2, lines.size)
        assertEquals("a", lines[0].text)
        assertEquals("b", lines[1].text)
        assertEquals(1000L, lines[1].begin)
    }

    @Test
    fun skipsLinesWithoutBody() {
        assertTrue(KrcLyricParser.parse("[0,1000]").isEmpty())
    }

    @Test
    fun lastTagWinsWhenConsecutive() {
        val lines = KrcLyricParser.parse("[0,1000]<0,100,0><100,100,0>x")

        assertEquals(1, lines.size)
        val words = lines[0].words ?: emptyList()
        assertEquals(1, words.size)
        assertEquals(100L, words[0].begin)
    }

    @Test
    fun toleratesWhitespaceInTags() {
        val lines = KrcLyricParser.parse("[0,1000]< 0 , 100 , 0 >x")

        val words = lines[0].words ?: emptyList()
        assertEquals(1, words.size)
        assertEquals(0L, words[0].begin)
        assertEquals(100L, words[0].duration)
    }

    @Test
    fun returnsEmptyForBlankContent() {
        assertTrue(KrcLyricParser.parse(null).isEmpty())
        assertTrue(KrcLyricParser.parse("").isEmpty())
        assertTrue(KrcLyricParser.parse("   ").isEmpty())
    }
}
