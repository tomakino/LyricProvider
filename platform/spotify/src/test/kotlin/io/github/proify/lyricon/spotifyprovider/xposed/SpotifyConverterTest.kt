/*
 * Copyright 2026 Proify, Tomakino
 * Licensed under the Apache License, Version 2.0
 * http://www.apache.org/licenses/LICENSE-2.0
 */

@file:Suppress("TestFunctionName")

package io.github.proify.lyricon.spotifyprovider.xposed

import io.github.proify.lyricon.spotifyprovider.xposed.api.proto.ProtoLyricLine
import io.github.proify.lyricon.spotifyprovider.xposed.api.proto.ProtoLyricsData
import io.github.proify.lyricon.spotifyprovider.xposed.api.proto.ProtoSyllable
import io.github.proify.lyricon.spotifyprovider.xposed.util.toLyrics
import io.github.proify.lyricon.spotifyprovider.xposed.util.toRichLyricLines
import io.github.proify.lyricon.spotifyprovider.xposed.util.toSongOrNull
import org.junit.Assert.assertEquals
import org.junit.Assert.assertNotNull
import org.junit.Assert.assertNull
import org.junit.Test

class SpotifyConverterTest {

    @Test
    fun 逐字行按count切片并生成LyricWord() {
        val lines = listOf(
            ProtoLyricLine(
                startTimeMs = 52604,
                words = "On a dark desert highway,",
                syllables = listOf(
                    ProtoSyllable(52604, 3, 52694),
                    ProtoSyllable(52831, 2, 52876),
                    ProtoSyllable(53012, 5, 53239),
                    ProtoSyllable(53330, 7, 53784),
                    ProtoSyllable(53874, 4, 54056),
                    ProtoSyllable(54147, 4, 54328)
                )
            ),
            ProtoLyricLine(startTimeMs = 55781, words = "Cool wind in my hair,")
        )

        val richLines = lines.toRichLyricLines()

        assertEquals(2, richLines.size)
        val first = richLines[0]
        assertEquals(52604L, first.begin)
        assertEquals(55781L, first.end)
        assertEquals(3177L, first.duration)
        assertEquals("On a dark desert highway,", first.text)
        assertEquals(
            listOf("On ", "a ", "dark ", "desert ", "high", "way,"),
            first.words!!.map { it.text }
        )
        assertEquals(
            listOf(52604L, 52831L, 53012L, 53330L, 53874L, 54147L),
            first.words!!.map { it.begin }
        )
        assertEquals(
            listOf(52694L, 52876L, 53239L, 53784L, 54056L, 54328L),
            first.words!!.map { it.end }
        )

        // 无逐字的行回退为纯行级歌词，末行 end = start + 5000
        val second = richLines[1]
        assertEquals(55781L, second.begin)
        assertEquals(60781L, second.end)
        assertNull(second.words)
    }

    @Test
    fun 累计长度不一致时整行回退纯文本() {
        val lines = listOf(
            ProtoLyricLine(
                startTimeMs = 0,
                words = "On a dark desert highway,",
                syllables = listOf(
                    ProtoSyllable(0, 3, 100),
                    ProtoSyllable(100, 999, 200)
                )
            )
        )

        val richLines = lines.toRichLyricLines()
        assertEquals(1, richLines.size)
        assertNull(richLines[0].words)
        assertEquals("On a dark desert highway,", richLines[0].text)
    }

    @Test
    fun 空白行被过滤() {
        val lines = listOf(
            ProtoLyricLine(startTimeMs = 1000, words = "  "),
            ProtoLyricLine(startTimeMs = 2000, words = "hello")
        )

        val richLines = lines.toRichLyricLines()
        assertEquals(1, richLines.size)
        assertEquals("hello", richLines[0].text)
    }

    @Test
    fun lyricsData转富歌词列表() {
        val data = ProtoLyricsData(
            syncType = 2,
            lines = listOf(
                ProtoLyricLine(
                    startTimeMs = 0,
                    words = "hi",
                    syllables = listOf(ProtoSyllable(0, 2, 100))
                )
            )
        )

        val richLines = data.toLyrics()
        assertEquals(1, richLines.size)
        assertEquals(listOf("hi"), richLines[0].words!!.map { it.text })
    }

    @Test
    fun JSON响应走原有解析路径() {
        val json = """
            {"lyrics":{"syncType":"LINE_SYNCED","lines":[
                {"startTimeMs":"0","words":"hello","endTimeMs":"100"}
            ]}}
        """.trimIndent()

        val song = json.toByteArray().toSongOrNull("track-1")
        assertNotNull(song)
        assertEquals("hello", song!!.lyrics!!.single().text)
    }
}