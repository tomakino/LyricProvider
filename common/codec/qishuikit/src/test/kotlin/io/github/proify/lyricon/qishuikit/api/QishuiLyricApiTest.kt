/*
 * Copyright 2026 Proify, Tomakino
 * Licensed under the Apache License, Version 2.0
 * http://www.apache.org/licenses/LICENSE-2.0
 */

package io.github.proify.lyricon.qishuikit.api

import kotlinx.coroutines.runBlocking
import kotlin.test.Test
import kotlin.test.assertEquals
import kotlin.test.assertNotNull
import kotlin.test.assertNull
import kotlin.test.assertTrue

/**
 * 走真实接口的下载测试。
 * 需要外网，且依赖线上曲目可用性。
 */
class QishuiLyricApiTest {

    private val api = QishuiLyricApi()

    @Test
    fun fetchesRealLyric() = runBlocking {
        val lyric = api.fetch("6917649010488510477")

        assertNotNull(lyric)
        assertEquals("6917649010488510477", lyric.id)
        assertEquals("一笑江湖", lyric.name)
        assertEquals("闻人听書_", lyric.artist)
        assertEquals("krc", lyric.type)
        assertNotNull(lyric.content)
        assertTrue(lyric.content.startsWith("["))
        assertTrue(lyric.duration > 0)
    }

    @Test
    fun returnsNullForUnknownTrack() = runBlocking {
        assertNull(api.fetch("1"))
    }
}
