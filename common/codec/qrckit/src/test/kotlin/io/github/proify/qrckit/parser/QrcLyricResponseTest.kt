/*
 * Copyright 2026 Proify, Tomakino
 * Licensed under the Apache License, Version 2.0
 * http://www.apache.org/licenses/LICENSE-2.0
 */

package io.github.proify.qrckit.parser

import io.github.proify.qrckit.decrypt.QrcEncrypter
import kotlin.test.Test
import kotlin.test.assertEquals
import kotlin.test.assertNull
import kotlin.test.assertTrue

/**
 * [QrcLyricResponse] 的 CDATA 提取与解密测试。
 */
class QrcLyricResponseTest {

    @Test
    fun extractsAndDecryptsLyrics() {
        val plain = "[0,1000]你(0,500)好(500,500)"
        val encrypted = checkNotNull(QrcEncrypter.encrypt(plain))
        val xml = """<root><content><![CDATA[$encrypted]]></content></root>"""

        val parsed = QrcLyricResponse("1", xml).parsedLyric

        assertEquals(plain, parsed.lyricsRaw)
        assertNull(parsed.translationRaw)
        assertNull(parsed.romaRaw)
    }

    @Test
    fun extractsTranslationAndRoma() {
        val lyrics = "[0,1000]你(0,500)"
        val translation = "[00:00.000]译"
        val roma = "[00:00.000]ro"
        val xml = buildString {
            append("<root>")
            append("<content><![CDATA[").append(QrcEncrypter.encrypt(lyrics))
                .append("]]></content>")
            append("<contentts><![CDATA[").append(QrcEncrypter.encrypt(translation))
                .append("]]></contentts>")
            append("<contentroma><![CDATA[").append(QrcEncrypter.encrypt(roma))
                .append("]]></contentroma>")
            append("</root>")
        }

        val parsed = QrcLyricResponse("1", xml).parsedLyric

        assertEquals(lyrics, parsed.lyricsRaw)
        assertEquals(translation, parsed.translationRaw)
        assertEquals(roma, parsed.romaRaw)
    }

    @Test
    fun missingCdataReturnsNulls() {
        val parsed = QrcLyricResponse("1", "<root/>").parsedLyric

        assertNull(parsed.lyricsRaw)
        assertNull(parsed.translationRaw)
        assertNull(parsed.romaRaw)
    }

    @Test
    fun parsedLyricIsLazy() {
        val response = QrcLyricResponse("1", "<root/>")

        assertTrue(response.parsedLyric === response.parsedLyric)
    }
}
