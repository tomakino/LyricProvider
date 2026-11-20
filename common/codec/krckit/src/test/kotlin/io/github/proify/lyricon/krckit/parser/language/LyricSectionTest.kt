/*
 * Copyright 2026 Proify, Tomakino
 * Licensed under the Apache License, Version 2.0
 * http://www.apache.org/licenses/LICENSE-2.0
 */

package io.github.proify.lyricon.krckit.parser.language

import kotlin.test.Test
import kotlin.test.assertEquals

/**
 * [LyricSection] 的文本拼接测试。
 */
class LyricSectionTest {

    @Test
    fun flattensRows() {
        val section = LyricSection(
            language = 0,
            lyricContent = listOf(listOf("a", "b"), listOf("c")),
            type = LyricSection.TYPE_ROMA
        )

        assertEquals(listOf("ab", "c"), section.flatten())
    }
}
