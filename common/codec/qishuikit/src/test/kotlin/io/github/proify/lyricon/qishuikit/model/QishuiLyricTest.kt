/*
 * Copyright 2026 Proify, Tomakino
 * Licensed under the Apache License, Version 2.0
 * http://www.apache.org/licenses/LICENSE-2.0
 */

package io.github.proify.lyricon.qishuikit.model

import java.util.Locale
import kotlin.test.Test
import kotlin.test.assertEquals
import kotlin.test.assertNull

class QishuiLyricTest {

    @Test
    fun buildsSongFromKrc() {
        val lyric = QishuiLyric(
            id = "123",
            name = "一笑江湖",
            artist = "闻人听書_",
            duration = 208941,
            type = "krc",
            content = "[1000,2000]<0,500,0>你<500,500,0>好"
        )

        val song = lyric.toSong()

        assertEquals("123", song?.id)
        assertEquals("一笑江湖", song?.name)
        assertEquals("闻人听書_", song?.artist)
        assertEquals(208941L, song?.duration)
        assertEquals(1, song?.lyrics?.size)
        assertEquals("你好", song?.lyrics?.get(0)?.text)
        assertEquals(1000L, song?.lyrics?.get(0)?.begin)
        assertEquals(2, song?.lyrics?.get(0)?.words?.size)
    }

    @Test
    fun buildsSongFromLrc() {
        val lyric = QishuiLyric(
            id = "456",
            type = "lrc",
            content = "[00:01.00]hello\n[00:02.50]world"
        )

        val song = lyric.toSong()

        assertEquals(2, song?.lyrics?.size)
        assertEquals("hello", song?.lyrics?.get(0)?.text)
        assertEquals(1000L, song?.lyrics?.get(0)?.begin)
        assertEquals("world", song?.lyrics?.get(1)?.text)
        assertEquals(2500L, song?.lyrics?.get(1)?.begin)
    }

    @Test
    fun fallsBackToKrcWhenTypeMissing() {
        val lyric = QishuiLyric(id = "1", content = "[0,1000]<0,500,0>x")

        val song = lyric.toSong()

        assertEquals(1, song?.lyrics?.size)
        assertEquals("x", song?.lyrics?.get(0)?.text)
    }

    @Test
    fun fillsTranslationFromLrc() {
        val lyric = QishuiLyric(
            id = "1",
            type = "krc",
            content = "[0,1000]<0,500,0>x",
            langTranslations = mapOf(
                "ZH" to QishuiTranslation(content = "[00:00.00]译文", type = "lrc")
            )
        )

        val song = lyric.toSong()

        assertEquals("译文", song?.lyrics?.get(0)?.translation)
    }

    @Test
    fun fillsTranslationFromKrc() {
        val lyric = QishuiLyric(
            id = "1",
            type = "krc",
            content = "[0,1000]<0,500,0>x",
            langTranslations = mapOf(
                "ZH" to QishuiTranslation(content = "[0,1000]<0,500,0>译", type = "krc")
            )
        )

        val song = lyric.toSong()

        assertEquals("译", song?.lyrics?.get(0)?.translation)
    }

    @Test
    fun fillsTranslationFromTranslationsMap() {
        val lyric = QishuiLyric(
            id = "1",
            type = "krc",
            content = "[0,1000]<0,500,0>x",
            translations = mapOf("cn" to "[00:00.00]译文")
        )

        val song = lyric.toSong()

        assertEquals("译文", song?.lyrics?.get(0)?.translation)
    }

    @Test
    fun picksTranslationByNormalizedLocale() {
        val previous = Locale.getDefault()
        try {
            Locale.setDefault(Locale.SIMPLIFIED_CHINESE)

            val lyric = QishuiLyric(
                id = "1",
                type = "krc",
                content = "[0,1000]<0,500,0>x",
                translations = mapOf(
                    "en" to "[00:00.00]hello",
                    "cn" to "[00:00.00]译文"
                )
            )

            assertEquals("译文", lyric.toSong()?.lyrics?.get(0)?.translation)
        } finally {
            Locale.setDefault(previous)
        }
    }

    @Test
    fun ignoresPlaceholderTranslation() {
        val lyric = QishuiLyric(
            id = "1",
            type = "krc",
            content = "[0,1000]<0,500,0>x",
            langTranslations = mapOf(
                "ZH" to QishuiTranslation(content = "[00:00.00]//", type = "lrc")
            )
        )

        val song = lyric.toSong()

        assertNull(song?.lyrics?.get(0)?.translation)
    }

    @Test
    fun returnsNullWhenContentMissing() {
        assertNull(QishuiLyric(id = "1", type = "krc").toSong())
        assertNull(QishuiLyric(id = "1", type = "lrc", content = "").toSong())
    }

    @Test
    fun returnsNullWhenNoLinesParsed() {
        assertNull(QishuiLyric(id = "1", type = "krc", content = "no time").toSong())
    }
}
