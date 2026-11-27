/*
 * Copyright 2026 Proify, Tomakino
 * Licensed under the Apache License, Version 2.0
 * http://www.apache.org/licenses/LICENSE-2.0
 */

package io.github.proify.amkit.ttmlparser

import io.github.proify.amkit.ttmlparser.model.flatten
import io.github.proify.amkit.ttmlparser.model.toStructuredString
import kotlin.test.Test
import kotlin.test.assertEquals
import kotlin.test.assertNotNull
import kotlin.test.assertNull
import kotlin.test.assertTrue

class OutputTest {

    private val parser = TtmlLyricsParser()

    private val ttml = """
        <tt xmlns="http://www.w3.org/ns/ttml"
            xmlns:itunes="http://music.apple.com/lyric-ttml-internal"
            xmlns:ttm="http://www.w3.org/ns/ttml#metadata"
            itunes:timing="Word" xml:lang="en">
          <head><metadata>
            <iTunesMetadata xmlns="http://music.apple.com/lyric-ttml-internal">
              <translations>
                <translation xml:lang="zh-Hans" type="replacement">
                  <text for="L1"><span begin="1.0" end="2.0" xmlns="http://www.w3.org/ns/ttml">你</span></text>
                </translation>
              </translations>
            </iTunesMetadata>
          </metadata></head>
          <body><div begin="0.0" end="10.0" itunes:song-part="Verse">
            <p begin="1.0" end="2.0" itunes:key="L1" ttm:agent="v1">
              <span begin="1.0" end="2.0">Hi</span>
              <span ttm:role="x-bg" begin="2.0" end="2.5"> (bg)</span>
            </p>
          </div></body>
        </tt>
    """.trimIndent()

    @Test
    fun `flatten 上提段落名并降级背景`() {
        val flat = parser.parse(ttml).flatten().single()

        assertEquals("Verse", flat.songPart)
        assertEquals("L1", flat.key)
        assertEquals("v1", flat.agentId)
        assertEquals("Hi", flat.text)
        assertEquals("(bg)", flat.background)
        assertEquals(1, flat.translations.size)
    }

    @Test
    fun `无背景人声时 flatten 的 background 为 null`() {
        val flat = parser.parse(
            """
            <tt xmlns="http://www.w3.org/ns/ttml"
                xmlns:itunes="http://music.apple.com/lyric-ttml-internal"
                itunes:timing="Word">
              <body><div><p begin="1.0" end="2.0"><span>Hi</span></p></div></body>
            </tt>
            """.trimIndent(),
        ).flatten().single()

        assertNull(flat.background)
    }

    @Test
    fun `toStructuredString 包含关键字段`() {
        val text = parser.parse(ttml).toStructuredString()

        assertTrue(text.contains("LyricsDocument("))
        assertTrue(text.contains("language"))
        assertTrue(text.contains("Verse"))
        assertTrue(text.contains("zh-Hans"))
        assertNotNull(text)
    }

    @Test
    fun `空文档的 toStructuredString`() {
        val text = parser.parse("""<tt xmlns="http://www.w3.org/ns/ttml"/>""").toStructuredString()

        assertTrue(text.contains("LyricsDocument("))
        assertTrue(text.contains("(无)"))
        assertTrue(text.contains("(未声明)"))
    }

    @Test
    fun `无 key 无 agent 无词的行`() {
        val text = parser.parse(
            """
            <tt xmlns="http://www.w3.org/ns/ttml">
              <body><div><p begin="1.0" end="2.0"/></div></body>
            </tt>
            """.trimIndent(),
        ).toStructuredString()

        assertTrue(text.contains("(无 key)"))
    }
}
