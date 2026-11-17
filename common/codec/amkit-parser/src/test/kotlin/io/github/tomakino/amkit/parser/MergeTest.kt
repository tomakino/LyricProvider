/*
 * Copyright 2026 Proify, Tomakino
 * Licensed under the Apache License, Version 2.0
 * http://www.apache.org/licenses/LICENSE-2.0
 */

package io.github.tomakino.amkit.parser

import kotlin.test.Test
import kotlin.test.assertEquals
import kotlin.test.assertTrue

class MergeTest {

    private val parser = TtmlLyricsParser()

    private fun ttml(inlineTranslation: String, headTranslations: String) = """
        <tt xmlns="http://www.w3.org/ns/ttml"
            xmlns:itunes="http://music.apple.com/lyric-ttml-internal"
            xmlns:ttm="http://www.w3.org/ns/ttml#metadata"
            itunes:timing="Word" xml:lang="en">
          <head><metadata>
            <iTunesMetadata xmlns="http://music.apple.com/lyric-ttml-internal">
              <translations>$headTranslations</translations>
            </iTunesMetadata>
          </metadata></head>
          <body><div>
            <p begin="1.0" end="2.0" itunes:key="L1">
              <span begin="1.0" end="2.0">Hi</span>
              $inlineTranslation
            </p>
          </div></body>
        </tt>
    """.trimIndent()

    @Test
    fun `行内与 head 侧同语言时行内优先`() {
        val document = parser.parse(
            ttml(
                inlineTranslation = """<span ttm:role="x-translation" xml:lang="zh-Hans">你</span>""",
                headTranslations = """
                    <translation xml:lang="zh-Hans" type="replacement">
                      <text for="L1"><span begin="1.0" end="2.0" xmlns="http://www.w3.org/ns/ttml">你</span></text>
                    </translation>
                """,
            ),
        )

        val translations = document.lines.single().translations
        assertEquals(1, translations.size)
        assertEquals("zh-Hans", translations.single().language)
    }

    @Test
    fun `不同语言都保留`() {
        val document = parser.parse(
            ttml(
                inlineTranslation = """<span ttm:role="x-translation" xml:lang="zh-Hans">你</span>""",
                headTranslations = """
                    <translation xml:lang="ja" type="replacement">
                      <text for="L1"><span begin="1.0" end="2.0" xmlns="http://www.w3.org/ns/ttml">こんにちは</span></text>
                    </translation>
                """,
            ),
        )

        val languages = document.lines.single().translations.mapNotNull { it.language }.toSet()
        assertEquals(setOf("zh-Hans", "ja"), languages)
    }

    @Test
    fun `无 key 的行不合并`() {
        val document = parser.parse(
            ttml(
                inlineTranslation = "",
                headTranslations = """
                    <translation xml:lang="zh-Hans" type="replacement">
                      <text for="OTHER"><span begin="1.0" end="2.0" xmlns="http://www.w3.org/ns/ttml">你</span></text>
                    </translation>
                """,
            ),
        )

        assertTrue(document.lines.single().translations.isEmpty())
    }
}
