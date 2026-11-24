/*
 * Copyright 2026 Proify, Tomakino
 * Licensed under the Apache License, Version 2.0
 * http://www.apache.org/licenses/LICENSE-2.0
 */
package io.github.proify.lrckit

import org.junit.jupiter.api.Assertions.assertEquals
import org.junit.jupiter.api.Test

class BracketWordTagTest {

    @Test
    fun parsesBracketWordTags() {
        val raw = "[00:17.148]It's[00:17.178] [00:17.178]realme[00:17.218]"

        val line = EnhanceLrcParser.parse(raw, 60_000L).lines.single()

        assertEquals(17_148L, line.begin)
        assertEquals(17_218L, line.end)
        assertEquals("It's realme", line.text)

        val words = line.words.orEmpty()
        assertEquals(listOf("It's ", "realme"), words.map { it.text })
        assertEquals(listOf(17_148L, 17_178L), words.map { it.begin })
    }

    @Test
    fun parsesMultiWordBracketLine() {
        val raw = "[00:17.298]Find[00:17.408] [00:17.408]the[00:17.478] [00:17.488]real[00:18.148]"

        val line = EnhanceLrcParser.parse(raw, 60_000L).lines.single()

        assertEquals("Find the real", line.text)
        assertEquals(18_148L, line.end)
    }
}
