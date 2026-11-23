/*
 * Copyright 2026 Proify, Tomakino
 * Licensed under the Apache License, Version 2.0
 * http://www.apache.org/licenses/LICENSE-2.0
 */

package io.github.proify.lyricon.qishuikit.model

import kotlinx.serialization.json.Json
import kotlin.test.Test
import kotlin.test.assertEquals
import kotlin.test.assertNull

class QishuiModelsTest {

    private val json = Json { ignoreUnknownKeys = true }

    @Test
    fun decodesFullResponse() {
        val raw = """
            {
              "lyric": {
                "type": "krc",
                "content": "[0,1000]<0,500,0>a",
                "lang_translations": {"en": {"content": "x", "type": "lrc"}}
              },
              "seo_track": {
                "track": {
                  "name": "n",
                  "artists": [{"name": "a1"}, {"name": "a2"}],
                  "duration": 123
                }
              },
              "track_player": {"media_id": "m", "video_model": "{}"}
            }
        """.trimIndent()

        val response = json.decodeFromString<QishuiH5Response>(raw)

        assertEquals("krc", response.lyric?.type)
        assertEquals("[0,1000]<0,500,0>a", response.lyric?.content)
        assertEquals("n", response.seoTrack?.track?.name)
        assertEquals(2, response.seoTrack?.track?.artists?.size)
        assertEquals("a2", response.seoTrack?.track?.artists?.get(1)?.name)
        assertEquals(123L, response.seoTrack?.track?.duration)
        assertEquals("m", response.trackPlayer?.mediaId)
        assertEquals("x", response.lyric?.langTranslations?.get("en")?.content)
    }

    @Test
    fun ignoresUnknownFields() {
        val raw = """{"lyric":{"type":"krc","content":"c","unknown":1},"extra":true}"""

        val response = json.decodeFromString<QishuiH5Response>(raw)

        assertEquals("c", response.lyric?.content)
        assertNull(response.seoTrack)
    }

    @Test
    fun decodesMissingFieldsAsNull() {
        val response = json.decodeFromString<QishuiH5Response>("{}")

        assertNull(response.lyric)
        assertNull(response.seoTrack)
        assertNull(response.trackPlayer)
    }

    @Test
    fun roundTripsLyric() {
        val lyric = QishuiLyric(
            id = "1",
            name = "n",
            artist = "a",
            duration = 100,
            type = "krc",
            content = "c",
            langTranslations = mapOf("en" to QishuiTranslation(content = "x", type = "lrc"))
        )

        val decoded = json.decodeFromString<QishuiLyric>(json.encodeToString(lyric))

        assertEquals(lyric, decoded)
    }
}
