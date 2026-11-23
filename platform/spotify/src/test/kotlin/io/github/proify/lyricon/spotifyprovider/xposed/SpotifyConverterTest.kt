/*
 * Copyright 2026 Proify, Tomakino
 * Licensed under the Apache License, Version 2.0
 * http://www.apache.org/licenses/LICENSE-2.0
 */

@file:Suppress("TestFunctionName")

package io.github.proify.lyricon.spotifyprovider.xposed

import io.github.proify.lyricon.spotifyprovider.xposed.api.proto.LyricsLine
import io.github.proify.lyricon.spotifyprovider.xposed.api.proto.LyricsResponse
import io.github.proify.lyricon.spotifyprovider.xposed.api.proto.Syllable
import io.github.proify.lyricon.spotifyprovider.xposed.util.toLyrics
import io.github.proify.lyricon.spotifyprovider.xposed.util.toSongOrNull
import org.junit.Assert.assertEquals
import org.junit.Assert.assertNull
import org.junit.Test

class SpotifyConverterTest {

    @Test
    fun 逐字行按count切片并生成LyricWord() {
        val response = LyricsResponse(
            lines = listOf(
                LyricsLine(
                    startTimeMs = 52604,
                    words = "On a dark desert highway,",
                    syllables = listOf(
                        Syllable(52604, 3),
                        Syllable(52831, 2),
                        Syllable(53012, 5),
                        Syllable(53330, 7),
                        Syllable(53874, 4),
                        Syllable(54147, 4)
                    )
                ),
                LyricsLine(startTimeMs = 55781, words = "Cool wind in my hair,")
            )
        )

        val richLines = response.toLyrics()

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
        // 逐字结束时间按下一音节起始推断，末段用行结束时间
        assertEquals(
            listOf(52831L, 53012L, 53330L, 53874L, 54147L, 55781L),
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
        val response = LyricsResponse(
            lines = listOf(
                LyricsLine(
                    startTimeMs = 0,
                    words = "On a dark desert highway,",
                    syllables = listOf(
                        Syllable(0, 3),
                        Syllable(100, 999)
                    )
                )
            )
        )

        val richLines = response.toLyrics()
        assertEquals(1, richLines.size)
        assertNull(richLines[0].words)
        assertEquals("On a dark desert highway,", richLines[0].text)
    }

    @Test
    fun 空白行被过滤() {
        val response = LyricsResponse(
            lines = listOf(
                LyricsLine(startTimeMs = 1000, words = "  "),
                LyricsLine(startTimeMs = 2000, words = "hello")
            )
        )

        val richLines = response.toLyrics()
        assertEquals(1, richLines.size)
        assertEquals("hello", richLines[0].text)
    }

    @Test
    fun 空字节返回null() {
        assertNull(ByteArray(0).toSongOrNull(trackMetadata("track-1")))
    }

    private fun trackMetadata(id: String) = TrackMetadata(
        id = id,
        title = null,
        artist = null,
        duration = 0L
    )
}
