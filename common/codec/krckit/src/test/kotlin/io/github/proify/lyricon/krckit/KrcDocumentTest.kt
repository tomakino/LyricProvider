/*
 * Copyright 2026 Proify, Tomakino
 * Licensed under the Apache License, Version 2.0
 * http://www.apache.org/licenses/LICENSE-2.0
 */

package io.github.proify.lyricon.krckit

import io.github.proify.lyricon.krckit.parser.KrcDocument
import io.github.proify.lyricon.lyric.model.LyricLine
import java.util.Base64
import kotlin.test.Test
import kotlin.test.assertEquals
import kotlin.test.assertNull

/**
 * [KrcDocument] 的翻译、罗马音对齐与偏移测试。
 */
class KrcDocumentTest {

    @Test
    fun alignsTranslationAndRoma() {
        val language =
            """{"content":[{"language":0,"lyricContent":[["ro1"],["ro2"]],"type":0},{"language":0,"lyricContent":[["译1"],["译2"]],"type":1}],"version":1}"""
        val document = KrcDocument(
            metadata = mapOf("language" to encode(language)),
            lines = listOf(line(0, "甲"), line(1000, "乙"))
        )

        assertEquals(language, document.languageJson)
        val rich = document.richLyricLines
        assertEquals("译1", rich[0].translation)
        assertEquals("ro1", rich[0].roma)
        assertEquals("译2", rich[1].translation)
        assertEquals("ro2", rich[1].roma)
    }

    @Test
    fun leavesTrackNullWhenSizeMismatches() {
        val language =
            """{"content":[{"language":0,"lyricContent":[["ro1"]],"type":0}],"version":1}"""
        val document = KrcDocument(
            metadata = mapOf("language" to encode(language)),
            lines = listOf(line(0, "甲"), line(1000, "乙"))
        )

        val rich = document.richLyricLines
        assertNull(rich[0].roma)
        assertNull(rich[1].roma)
        assertNull(rich[0].translation)
    }

    @Test
    fun missingLanguageLeavesTracksNull() {
        val document = KrcDocument(lines = listOf(line(0, "甲")))

        val rich = document.richLyricLines.single()
        assertNull(rich.translation)
        assertNull(rich.roma)
    }

    @Test
    fun invalidLanguageKeepsRawValue() {
        val document = KrcDocument(
            metadata = mapOf("language" to "not-base64"),
            lines = listOf(line(0, "甲"))
        )

        assertEquals("not-base64", document.languageJson)
        assertNull(document.richLyricLines.single().roma)
    }

    @Test
    fun appliesOffset() {
        val document = KrcDocument(lines = listOf(line(1000, "甲")))

        val shifted = document.applyOffset(500)
        assertEquals(1500L, shifted.lines.single().begin)
        assertEquals(2500L, shifted.lines.single().end)

        val clamped = document.applyOffset(-2000)
        assertEquals(0L, clamped.lines.single().begin)
        assertEquals(1000L, clamped.lines.single().duration)
    }

    /**
     * 构造一行歌词。
     * @param begin 开始时间，毫秒。
     * @param text 行文本。
     * @return 歌词行。
     */
    private fun line(begin: Long, text: String) =
        LyricLine(begin = begin, end = begin + 1000, duration = 1000, text = text)

    /**
     * Base64 编码 UTF-8 文本。
     * @param text 原始文本。
     * @return Base64 字符串。
     */
    private fun encode(text: String): String =
        Base64.getEncoder().encodeToString(text.toByteArray())
}
