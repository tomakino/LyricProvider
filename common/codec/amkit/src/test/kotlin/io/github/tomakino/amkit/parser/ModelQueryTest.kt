/*
 * Copyright 2026 Proify, Tomakino
 * Licensed under the Apache License, Version 2.0
 * http://www.apache.org/licenses/LICENSE-2.0
 */

package io.github.proify.amkit.parser

import io.github.proify.amkit.parser.model.LyricsTiming
import kotlin.test.Test
import kotlin.test.assertEquals
import kotlin.test.assertFalse
import kotlin.test.assertNull
import kotlin.test.assertTrue
import kotlin.time.Duration.Companion.seconds

class ModelQueryTest {

    private val parser = TtmlLyricsParser()

    private val ttml = """
        <tt xmlns="http://www.w3.org/ns/ttml"
            xmlns:itunes="http://music.apple.com/lyric-ttml-internal"
            xmlns:ttm="http://www.w3.org/ns/ttml#metadata"
            xmlns:amll="http://www.example.com/ns/amll"
            itunes:timing="Word" xml:lang="en">
          <head><metadata>
            <ttm:agent type="person" xml:id="v1">
              <ttm:name type="full">A</ttm:name>
            </ttm:agent>
            <ttm:agent type="person" xml:id="v2"/>
            <amll:meta key="k" value="v1"/>
            <amll:meta key="k" value="v2"/>
            <iTunesMetadata xmlns="http://music.apple.com/lyric-ttml-internal">
              <translations>
                <translation xml:lang="zh-Hans" type="replacement">
                  <text for="L1"><span begin="1.0" end="2.0" xmlns="http://www.w3.org/ns/ttml">你</span></text>
                </translation>
              </translations>
              <transliterations>
                <transliteration xml:lang="ja-Latn">
                  <text for="L1"><span begin="1.0" end="2.0" xmlns="http://www.w3.org/ns/ttml">ni</span></text>
                </transliteration>
              </transliterations>
            </iTunesMetadata>
          </metadata></head>
          <body><div>
            <p begin="1.0" end="2.0" itunes:key="L1" ttm:agent="v1">
              <span begin="1.0" end="2.0">Hi</span>
            </p>
          </div></body>
        </tt>
    """.trimIndent()

    @Test
    fun `lineByKey`() {
        val document = parser.parse(ttml)
        assertEquals("L1", document.lineByKey("L1")?.key)
        assertNull(document.lineByKey("missing"))
    }

    @Test
    fun `文档级译文与音译查询`() {
        val document = parser.parse(ttml)

        assertTrue(document.hasTranslation("zh-Hans"))
        assertFalse(document.hasTranslation("ko"))
        assertEquals("zh-Hans", document.translationFor("zh-Hans")?.language)
        assertEquals("ja-Latn", document.transliterationFor("ja-Latn")?.language)
        assertEquals(listOf("zh-Hans"), document.translationLanguages)
        assertEquals(listOf("ja-Latn"), document.transliterationLanguages)
    }

    @Test
    fun `文档级回退`() {
        val document = parser.parse(ttml)
        assertEquals("zh-Hans", document.translationFor("zh-Hans-CN", fallback = true)?.language)
    }

    @Test
    fun `行级查询`() {
        val line = parser.parse(ttml).lines.single()

        assertEquals("zh-Hans", line.translationFor("zh-Hans")?.language)
        assertEquals("ja-Latn", line.transliterationFor("ja-Latn")?.language)
        assertTrue(line.hasTimedTranslation("zh-Hans"))
        assertFalse(line.hasTimedTranslation("ko"))
        assertEquals("Hi", line.text)
        assertEquals(2.seconds, line.end)
    }

    @Test
    fun `元数据 firstMeta`() {
        val metadata = parser.parse(ttml).metadata
        assertEquals("v1", metadata.firstMeta("k"))
        assertNull(metadata.firstMeta("missing"))
    }

    @Test
    fun `演唱者无姓名时 firstDisplayName 为 null`() {
        val document = parser.parse(ttml)
        val empty = document.agents.first { it.id == "v2" }
        assertNull(empty.firstDisplayName())
    }

    @Test
    fun `计时模式映射`() {
        assertEquals(LyricsTiming.WORD, LyricsTiming.fromAttribute("Word"))
        assertEquals(LyricsTiming.LINE, LyricsTiming.fromAttribute("line"))
        assertEquals(LyricsTiming.NONE, LyricsTiming.fromAttribute("None"))
        assertEquals(LyricsTiming.UNKNOWN, LyricsTiming.fromAttribute(null))
        assertEquals(LyricsTiming.UNKNOWN, LyricsTiming.fromAttribute("weird"))
    }
}
