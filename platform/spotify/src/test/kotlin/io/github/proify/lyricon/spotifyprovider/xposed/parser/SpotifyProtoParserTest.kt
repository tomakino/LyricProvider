/*
 * Copyright 2026 Proify, Tomakino
 * Licensed under the Apache License, Version 2.0
 * http://www.apache.org/licenses/LICENSE-2.0
 */

@file:Suppress("NonAsciiCharacters")

package io.github.proify.lyricon.spotifyprovider.xposed.parser

import org.junit.Assert.assertEquals
import org.junit.Assert.assertNotNull
import org.junit.Assert.assertNull
import org.junit.Assert.assertTrue
import org.junit.Test

class SpotifyProtoParserTest {

    @Test
    fun 解析包含逐字的完整响应() {
        val bytes = response(
            lyricsData(
                syncType = 2,
                lines = listOf(
                    line(
                        startTimeMs = 52604,
                        words = "On a dark desert highway,",
                        syllables = listOf(
                            syllable(52604, 3, 52694),
                            syllable(52831, 2, 52876),
                            syllable(53012, 5, 53239),
                            syllable(53330, 7, 53784),
                            syllable(53874, 4, 54056),
                            syllable(54147, 4, 54328)
                        )
                    ),
                    line(startTimeMs = 55781, words = "Cool wind in my hair,")
                )
            ),
            colors = colors(background = -1, text = -16777216, highlightText = -65536)
        )

        val parsed = SpotifyProtoParser.parse(bytes)
        assertNotNull(parsed)
        val lyrics = parsed!!.lyrics!!
        assertEquals(2, lyrics.syncType)
        assertEquals(2, lyrics.lines.size)

        val first = lyrics.lines[0]
        assertEquals(52604L, first.startTimeMs)
        assertEquals("On a dark desert highway,", first.words)
        assertEquals(6, first.syllables.size)
        assertEquals(listOf(3, 2, 5, 7, 4, 4), first.syllables.map { it.count })
        assertEquals(
            listOf(52604L, 52831L, 53012L, 53330L, 53874L, 54147L),
            first.syllables.map { it.startTimeMs }
        )
        assertEquals(
            listOf(52694L, 52876L, 53239L, 53784L, 54056L, 54328L),
            first.syllables.map { it.endTimeMs }
        )

        // 无逐字的行不携带 syllables
        val second = lyrics.lines[1]
        assertEquals(55781L, second.startTimeMs)
        assertEquals("Cool wind in my hair,", second.words)
        assertTrue(second.syllables.isEmpty())

        val color = parsed.colors!!
        assertEquals(-1, color.background)
        assertEquals(-16777216, color.text)
        assertEquals(-65536, color.highlightText)
    }

    @Test
    fun 未知字段按wireType跳过() {
        val bytes = response(
            lyricsData(
                lines = listOf(
                    line(
                        startTimeMs = 1000,
                        words = "hello",
                        unknownVarint = 42L,
                        unknownBytes = byteArrayOf(1, 2, 3)
                    )
                )
            )
        )

        val parsed = SpotifyProtoParser.parse(bytes)
        assertNotNull(parsed)
        val first = parsed!!.lyrics!!.lines.single()
        assertEquals(1000L, first.startTimeMs)
        assertEquals("hello", first.words)
        assertTrue(first.syllables.isEmpty())
    }

    @Test
    fun 空字节解析为空响应() {
        val parsed = SpotifyProtoParser.parse(ByteArray(0))
        assertNotNull(parsed)
        assertNull(parsed!!.lyrics)
    }

    @Test
    fun 非法字节返回null() {
        // field 1 wire type 3（group start），解析器不支持
        assertNull(SpotifyProtoParser.parse(byteArrayOf(0x0B.toByte())))
        // 截断的 varint
        assertNull(SpotifyProtoParser.parse(byteArrayOf(0x08, 0x80.toByte())))
    }

    // ---------------------------------- protobuf 测试数据构造 ----------------------------------

    private fun encodeVarint(value: Long): ByteArray {
        if (value == 0L) return byteArrayOf(0)
        val buffer = ByteArray(10)
        var index = 0
        var v = value
        while (v != 0L) {
            var byte = (v and 0x7F).toInt()
            v = v ushr 7
            if (v != 0L) byte = byte or 0x80
            buffer[index++] = byte.toByte()
        }
        return buffer.copyOf(index)
    }

    private fun field(number: Int, wireType: Int, payload: ByteArray = ByteArray(0)): ByteArray =
        encodeVarint(((number shl 3) or wireType).toLong()) + payload

    private fun varintField(number: Int, value: Long): ByteArray =
        field(number, 0, encodeVarint(value))

    private fun bytesField(number: Int, payload: ByteArray): ByteArray =
        field(number, 2, encodeVarint(payload.size.toLong()) + payload)

    private fun stringField(number: Int, value: String): ByteArray =
        bytesField(number, value.toByteArray(Charsets.UTF_8))

    private fun response(lyrics: ByteArray, colors: ByteArray? = null): ByteArray =
        bytesField(1, lyrics) + (colors?.let { bytesField(2, it) } ?: ByteArray(0))

    private fun lyricsData(syncType: Int = 1, lines: List<ByteArray> = emptyList()): ByteArray =
        varintField(1, syncType.toLong()) +
                lines.fold(ByteArray(0)) { acc, line -> acc + bytesField(2, line) }

    private fun line(
        startTimeMs: Long = 0,
        words: String? = null,
        syllables: List<ByteArray> = emptyList(),
        unknownVarint: Long? = null,
        unknownBytes: ByteArray? = null
    ): ByteArray {
        var result = varintField(1, startTimeMs)
        words?.let { result += stringField(2, it) }
        unknownVarint?.let { result += varintField(99, it) }
        unknownBytes?.let { result += bytesField(98, it) }
        return result + syllables.fold(ByteArray(0)) { acc, item -> acc + bytesField(3, item) }
    }

    private fun syllable(startTimeMs: Long, count: Int, endTimeMs: Long): ByteArray =
        varintField(1, startTimeMs) +
                varintField(2, count.toLong()) +
                varintField(3, endTimeMs)

    private fun colors(background: Int, text: Int, highlightText: Int): ByteArray =
        varintField(1, background.toLong()) +
                varintField(2, text.toLong()) +
                varintField(3, highlightText.toLong())
}