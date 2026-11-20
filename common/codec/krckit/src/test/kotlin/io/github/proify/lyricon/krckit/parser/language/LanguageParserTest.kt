/*
 * Copyright 2026 Proify, Tomakino
 * Licensed under the Apache License, Version 2.0
 * http://www.apache.org/licenses/LICENSE-2.0
 */

package io.github.proify.lyricon.krckit.parser.language

import kotlin.test.Test
import kotlin.test.assertEquals
import kotlin.test.assertNotNull
import kotlin.test.assertNull

/**
 * [LanguageParser] 与 [LanguageInfo] 的分段查找测试。
 */
class LanguageParserTest {

    @Test
    fun parsesSectionsAndFindsTracks() {
        val info = LanguageParser.parse(
            """{"content":[{"language":1,"lyricContent":[["a","b"]],"type":0},{"language":1,"lyricContent":[["译"]],"type":1}],"version":2,"extra":true}"""
        )

        assertNotNull(info)
        assertEquals(2, info.version)
        assertEquals(listOf("ab"), info.findRoma()!!.flatten())
        assertEquals(listOf("译"), info.findTranslate()!!.flatten())
    }

    @Test
    fun returnsNullForInvalidJson() {
        assertNull(LanguageParser.parse("not-json"))
    }

    @Test
    fun returnsNullWhenTrackMissing() {
        val info = LanguageParser.parse("""{"content":[],"version":1}""")

        assertNotNull(info)
        assertNull(info.findRoma())
        assertNull(info.findTranslate())
    }
}
