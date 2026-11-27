/*
 * Copyright 2026 Proify, Tomakino
 * Licensed under the Apache License, Version 2.0
 * http://www.apache.org/licenses/LICENSE-2.0
 */

package io.github.proify.amkit.ttmlparser

import kotlin.test.Test
import kotlin.test.assertEquals

/** 验证 text / words / background / backgroundWords / translation / translationWords / transliteration / transliterationWords。 */
class SixFieldsTest {

    private val parser = TtmlLyricsParser()

    private val ttml = """
        <tt xmlns="http://www.w3.org/ns/ttml"
            xmlns:itunes="http://music.apple.com/lyric-ttml-internal"
            xmlns:ttm="http://www.w3.org/ns/ttml#metadata"
            itunes:timing="Word" xml:lang="ja">
          <head><metadata>
            <iTunesMetadata xmlns="http://music.apple.com/lyric-ttml-internal">
              <translations>
                <translation xml:lang="zh-Hans" type="subtitle">
                  <text for="L1">
                    <span begin="1.0" end="2.0" xmlns="http://www.w3.org/ns/ttml">你好</span>
                    <span begin="2.0" end="3.0" xmlns="http://www.w3.org/ns/ttml">世界</span>
                  </text>
                </translation>
              </translations>
              <transliterations>
                <transliteration xml:lang="ja-Latn">
                  <text for="L1"><span begin="1.0" end="2.0" xmlns="http://www.w3.org/ns/ttml">konnichiwa</span> <span begin="2.0" end="3.0" xmlns="http://www.w3.org/ns/ttml">sekai</span></text>
                </transliteration>
              </transliterations>
            </iTunesMetadata>
          </metadata></head>
          <body><div>
            <p begin="1.0" end="3.0" itunes:key="L1">
              <span begin="1.0" end="2.0">こんにちは</span>
              <span begin="2.0" end="3.0">世界</span>
              <span ttm:role="x-bg" begin="2.5" end="3.0">(ooh)</span>
            </p>
          </div></body>
        </tt>
    """.trimIndent()

    private fun display() = parser.parse(ttml).displayLines(
        DisplayOptions(
            language = "zh-Hans",
            transliterationLanguage = "ja-Latn",
        ),
    ).single()

    @Test
    fun `文本`() {
        assertEquals("こんにちは世界", display().text)
    }

    @Test
    fun `单词`() {
        assertEquals(listOf("こんにちは", "世界"), display().words.map { it.text })
    }

    @Test
    fun `背景`() {
        assertEquals("(ooh)", display().background)
    }

    @Test
    fun `背景单词`() {
        assertEquals(
            listOf("(ooh)"),
            display().backgroundWords.map { it.text },
        )
    }

    @Test
    fun `翻译`() {
        assertEquals("你好世界", display().translation)
    }

    @Test
    fun `翻译单词`() {
        assertEquals(
            listOf("你好", "世界"),
            display().translationWords?.map { it.text },
        )
    }

    @Test
    fun `罗马音`() {
        assertEquals("konnichiwa sekai", display().transliteration)
    }

    @Test
    fun `罗马音单词`() {
        assertEquals(
            listOf("konnichiwa ", "sekai"),
            display().transliterationWords?.map { it.text },
        )
    }
}
