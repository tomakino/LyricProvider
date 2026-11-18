/*
 * Copyright 2026 Proify, Tomakino
 * Licensed under the Apache License, Version 2.0
 * http://www.apache.org/licenses/LICENSE-2.0
 */

package io.github.proify.lyricon.cmprovider.xposed

import org.junit.Assert.assertEquals
import org.junit.Assert.assertNull
import org.junit.Test

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

}