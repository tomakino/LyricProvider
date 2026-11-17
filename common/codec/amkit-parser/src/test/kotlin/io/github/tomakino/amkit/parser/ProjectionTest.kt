/*
 * Copyright 2026 Proify, Tomakino
 * Licensed under the Apache License, Version 2.0
 * http://www.apache.org/licenses/LICENSE-2.0
 */

package io.github.tomakino.amkit.parser

import io.github.tomakino.amkit.parser.model.DisplayLine
import kotlin.test.Test
import kotlin.test.assertEquals
import kotlin.test.assertNull
import kotlin.test.assertTrue

class ProjectionTest {

    private val parser = TtmlLyricsParser()

    /** 原文 2 个字符、译文 2 个字符，字符对齐可成功。 */
    private val plainReplacement = """
        <tt xmlns="http://www.w3.org/ns/ttml"
            xmlns:itunes="http://music.apple.com/lyric-ttml-internal"
            xmlns:ttm="http://www.w3.org/ns/ttml#metadata"
            itunes:timing="Word" xml:lang="en">
          <head><metadata>
            <iTunesMetadata xmlns="http://music.apple.com/lyric-ttml-internal">
              <translations>
                <translation xml:lang="zh-Hans" type="replacement">
                  <text for="L1">你好</text>
                </translation>
              </translations>
            </iTunesMetadata>
          </metadata></head>
          <body><div>
            <p begin="1.0" end="2.0" itunes:key="L1">
              <span begin="1.0" end="2.0">Hi</span>
            </p>
          </div></body>
        </tt>
    """.trimIndent()

    @Test
    fun `字符对齐替换主词序列`() {
        val aligned = parser.parse(plainReplacement).displayLines(
            DisplayOptions(language = "zh-Hans", alignUntimedTranslation = true),
        ).single()

        assertEquals("你好", aligned.text)
        assertEquals("你好", aligned.words.joinToString("") { it.text })
    }

    @Test
    fun `关闭字符对齐时主词序列保持原文`() {
        val strict = parser.parse(plainReplacement).displayLines(
            DisplayOptions(language = "zh-Hans", alignUntimedTranslation = false),
        ).single()

        assertEquals("你好", strict.text)
        assertEquals("Hi", strict.words.joinToString("") { it.text })
    }

    @Test
    fun `没有译文时原文且无对照`() {
        val ttml = """
            <tt xmlns="http://www.w3.org/ns/ttml"
                xmlns:itunes="http://music.apple.com/lyric-ttml-internal"
                itunes:timing="Word" xml:lang="en">
              <body><div>
                <p begin="1.0" end="2.0"><span>Only original</span></p>
              </div></body>
            </tt>
        """.trimIndent()

        val line = parser.parse(ttml).displayLines(DisplayOptions(language = "zh-Hans")).single()

        assertEquals("Only original", line.text)
        assertNull(line.translation)
        assertNull(line.transliteration)
    }

    @Test
    fun `背景人声透传`() {
        val ttml = """
            <tt xmlns="http://www.w3.org/ns/ttml"
                xmlns:itunes="http://music.apple.com/lyric-ttml-internal"
                xmlns:ttm="http://www.w3.org/ns/ttml#metadata"
                itunes:timing="Word" xml:lang="en">
              <body><div>
                <p begin="1.0" end="3.0">
                  <span begin="1.0" end="2.0">Main</span>
                  <span ttm:role="x-bg" begin="2.0" end="3.0"> (bg)</span>
                </p>
              </div></body>
            </tt>
        """.trimIndent()

        val line = parser.parse(ttml).displayLines().single()

        assertEquals("Main", line.text)
        assertTrue(line.backgroundWords.isNotEmpty())
        assertEquals("(bg)", line.background)
    }

    @Test
    fun `音译未要求时为 null`() {
        val line = parser.parse(plainReplacement).displayLines(DisplayOptions(language = "zh-Hans")).single()
        assertNull(line.transliteration)
        assertNull(line.transliterationWords)
    }

    @Test
    fun `只有文本时 background 可直接填写`() {
        val line = DisplayLine(text = "Hi", words = emptyList(), background = "(bg)")

        assertEquals("(bg)", line.background)
        assertTrue(line.backgroundWords.isEmpty())
    }

    @Test
    fun `没有背景时 background 为 null`() {
        assertNull(DisplayLine(text = "Hi", words = emptyList()).background)
    }

    @Test
    fun `displayLines 多次投影互不影响`() {
        val document = parser.parse(plainReplacement)

        val first = document.displayLines(DisplayOptions(language = "zh-Hans")).single()
        val second = document.displayLines(DisplayOptions(language = "zh-Hans", applyReplacement = false)).single()

        assertEquals("你好", first.text)
        assertEquals("Hi", second.text)
        assertEquals("你好", second.translation)
        assertTrue(first.text != second.text)
    }
}
