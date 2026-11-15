/*
 * Copyright 2026 Proify, Tomakino
 * Licensed under the Apache License, Version 2.0
 * http://www.apache.org/licenses/LICENSE-2.0
 */

package io.github.proify.lyricon.cmprovider.xposed

import io.github.proify.lyricon.yrckit.download.response.LyricContent
import io.github.proify.lyricon.yrckit.download.response.LyricResponse
import kotlinx.serialization.json.Json
import org.junit.Assert.assertEquals
import org.junit.Assert.assertFalse
import org.junit.Assert.assertNotNull
import org.junit.Assert.assertNull
import org.junit.Assert.assertTrue
import org.junit.Rule
import org.junit.Test
import org.junit.rules.TemporaryFolder

class LyricCacheStoreTest {
    @get:Rule
    val temporaryFolder = TemporaryFolder()

    private val json = Json { ignoreUnknownKeys = true }
    private val store = LyricCacheStore(json)

    @Test
    fun `response validation accepts source lyrics or pure music only`() {
        assertNotNull(
            LyricResponse(
                code = 200,
                lrc = LyricContent(lyric = "[00:00.00]source")
            ).toValidCacheOrNull(1L)
        )
        assertNotNull(LyricResponse(code = 200, pureMusic = true).toValidCacheOrNull(2L))
        assertNull(
            LyricResponse(
                code = 500,
                lrc = LyricContent(lyric = "[00:00.00]source")
            ).toValidCacheOrNull(3L)
        )
        assertNull(
            LyricResponse(
                code = 200,
                tlyric = LyricContent(lyric = "translation only")
            ).toValidCacheOrNull(4L)
        )
        assertNull(LyricResponse(code = 200).toValidCacheOrNull(5L))
    }

    @Test
    fun `corrupt and invalid cache is deleted and treated as miss`() {
        val corrupt = temporaryFolder.newFile("10")
        corrupt.writeText("{broken", Charsets.UTF_8)
        assertNull(store.read(corrupt, 10L))
        assertFalse(corrupt.exists())

        val invalid = temporaryFolder.newFile("11")
        invalid.writeText(json.encodeToString(LocalLyricCache(musicId = 11L)), Charsets.UTF_8)
        assertNull(store.read(invalid, 11L))
        assertFalse(invalid.exists())
    }

    @Test
    fun `cache replacement retains schema path and leaves no temporary file`() {
        val directory = temporaryFolder.newFolder("cache")
        val finalFile = directory.resolve("12")
        val first = LocalLyricCache(musicId = 12L, pureMusic = true)
        val replacement = LocalLyricCache(musicId = 12L, lrc = "[00:00.00]updated")

        assertTrue(store.write(finalFile, first))
        assertTrue(store.write(finalFile, replacement))
        assertEquals("12", finalFile.name)
        assertEquals(replacement, store.read(finalFile, 12L))
        assertTrue(directory.listFiles().orEmpty().contentEquals(arrayOf(finalFile)))
    }

    @Test
    fun `wrong song cache is removed so one download can own recovery`() {
        val file = temporaryFolder.newFile("13")
        file.writeText(
            json.encodeToString(LocalLyricCache(musicId = 99L, pureMusic = true)),
            Charsets.UTF_8
        )
        val gate = DownloadGate()

        assertNull(store.read(file, 13L))
        assertTrue(gate.tryStart(13L))
        assertFalse(gate.tryStart(13L))
    }
}
