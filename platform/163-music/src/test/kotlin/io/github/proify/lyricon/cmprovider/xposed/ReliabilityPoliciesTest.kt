/*
 * Copyright 2026 Proify, Tomakino
 * Licensed under the Apache License, Version 2.0
 * http://www.apache.org/licenses/LICENSE-2.0
 */

package io.github.proify.lyricon.cmprovider.xposed

import org.junit.Assert.assertEquals
import org.junit.Assert.assertFalse
import org.junit.Assert.assertNull
import org.junit.Assert.assertTrue
import org.junit.Test
import java.util.concurrent.CountDownLatch
import java.util.concurrent.Executors
import java.util.concurrent.TimeUnit
import java.util.concurrent.atomic.AtomicInteger

class ReliabilityPoliciesTest {
    @Test
    fun `media id supports all known unambiguous representations`() {
        assertEquals(123L, MediaIdParser.parse("123"))
        assertEquals(123L, MediaIdParser.parse("https://music.163.com/song?id=123"))
        assertEquals(123L, MediaIdParser.parse("cloudmusic://song/123"))
        assertEquals(123L, MediaIdParser.parse("song:123"))
        assertEquals(123L, MediaIdParser.parse("foo:bar:123"))
        assertEquals(123L, MediaIdParser.parse("/song/123?id=123"))
    }

    @Test
    fun `media id rejects invalid and ambiguous representations`() {
        assertNull(MediaIdParser.parse(null))
        assertNull(MediaIdParser.parse("not-a-song"))
        assertNull(MediaIdParser.parse("0"))
        assertNull(MediaIdParser.parse("https://host:8080"))
        assertNull(MediaIdParser.parse("/song/123?id=456"))
        assertNull(MediaIdParser.parse("?id=123&id=456"))
    }

    @Test
    fun `hook target accepts only supported first package processes`() {
        listOf("com.netease.cloudmusic", "com.hihonor.cloudmusic").forEach { packageName ->
            assertTrue(HookTarget.accepts(packageName, packageName, packageName, true))
            assertTrue(HookTarget.accepts(packageName, packageName, "$packageName:play", true))
            assertFalse(HookTarget.accepts(packageName, packageName, "$packageName:push", true))
            assertFalse(HookTarget.accepts(packageName, packageName, packageName, false))
            assertFalse(HookTarget.accepts(packageName, "other.package", packageName, true))
        }
        assertFalse(HookTarget.accepts("other.package", "other.package", "other.package", true))
    }

    @Test
    fun `download gate admits only one concurrent owner per id`() {
        val gate = DownloadGate()
        val workers = 24
        val ready = CountDownLatch(workers)
        val start = CountDownLatch(1)
        val done = CountDownLatch(workers)
        val admitted = AtomicInteger()
        val executor = Executors.newFixedThreadPool(workers)

        repeat(workers) {
            executor.execute {
                ready.countDown()
                start.await()
                if (gate.tryStart(42L)) admitted.incrementAndGet()
                done.countDown()
            }
        }

        assertTrue(ready.await(5, TimeUnit.SECONDS))
        start.countDown()
        assertTrue(done.await(5, TimeUnit.SECONDS))
        executor.shutdownNow()
        assertEquals(1, admitted.get())
        assertFalse(gate.tryStart(42L))
        gate.finish(42L)
        assertTrue(gate.tryStart(42L))
    }

    @Test
    fun `late result never publishes over the current track`() {
        val completedDownloadId = 7L
        var currentTrackId: Long? = completedDownloadId
        assertTrue(CurrentTrackPolicy.shouldPublish(completedDownloadId, currentTrackId))

        currentTrackId = 8L
        assertFalse(CurrentTrackPolicy.shouldPublish(completedDownloadId, currentTrackId))
        assertFalse(CurrentTrackPolicy.shouldPublish(7L, null))
    }

    @Test
    fun `capability selection requires exactly one candidate`() {
        assertNull(CapabilitySelection.uniqueOrNull(emptyList<String>()))
        assertEquals("method", CapabilitySelection.uniqueOrNull(listOf("method")))
        assertNull(CapabilitySelection.uniqueOrNull(listOf("first", "second")))
    }

    @Test
    fun `empty and pure lyric songs use safe duration fallback`() {
        val empty = LocalLyricCache(musicId = 1L).toSong(
            Metadata(id = 1L, title = "Empty", artist = null, duration = -1L)
        )
        val pure = LocalLyricCache(musicId = 2L, pureMusic = true).toSong(
            Metadata(id = 2L, title = "Pure", artist = null, duration = 12_345L)
        )

        assertEquals(0L, empty.duration)
        assertTrue(empty.lyrics.isNullOrEmpty())
        assertEquals(12_345L, pure.duration)
        assertTrue(pure.lyrics.isNullOrEmpty())
        assertEquals(9_000L, resolveSongDuration(null, 9_000L))
        assertEquals(0L, resolveSongDuration(0L, -1L))
    }
}
