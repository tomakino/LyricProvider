/*
 * Copyright 2026 Proify, Tomakino
 * Licensed under the Apache License, Version 2.0
 * http://www.apache.org/licenses/LICENSE-2.0
 */

package io.github.proify.amkit.parser

import kotlin.test.Test
import kotlin.test.assertEquals
import kotlin.test.assertFailsWith

class ParserApiTest {

    private val parser = TtmlLyricsParser()

    private val ttml = """
        <tt xmlns="http://www.w3.org/ns/ttml" xml:lang="en">
          <body><div><p begin="1.0" end="2.0"><span>Hi</span></p></div></body>
        </tt>
    """.trimIndent()

    @Test
    fun `InputStream 重载`() {
        assertEquals("en", parser.parse(ttml.byteInputStream()).language)
    }

    @Test
    fun `Reader 重载`() {
        assertEquals("en", parser.parse(ttml.reader()).language)
    }

    @Test
    fun `Reader 重载剥离 BOM`() {
        assertEquals("en", parser.parse(("﻿" + ttml).reader()).language)
    }

    @Test
    fun `Reader 空内容抛解析异常`() {
        assertFailsWith<LyricsParseException> { parser.parse("".reader()) }
    }

    @Test
    fun `InputStream 非法内容抛解析异常`() {
        assertFailsWith<LyricsParseException> { parser.parse("<tt".byteInputStream()) }
    }

    @Test
    fun `解析器可复用`() {
        repeat(3) {
            assertEquals("en", parser.parse(ttml).language)
        }
    }
}
