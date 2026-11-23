/*
 * Copyright 2026 Proify, Tomakino
 * Licensed under the Apache License, Version 2.0
 * http://www.apache.org/licenses/LICENSE-2.0
 */

package io.github.proify.amkit.parser

import kotlin.test.Test
import kotlin.test.assertEquals
import kotlin.test.assertTrue

class SpanCollectorTest {

    private val parser = TtmlLyricsParser()

    private fun parse(body: String): String = body.trimIndent()

    private fun document(inner: String) = parser.parse(
        """
        <tt xmlns="http://www.w3.org/ns/ttml"
            xmlns:itunes="http://music.apple.com/lyric-ttml-internal"
            xmlns:ttm="http://www.w3.org/ns/ttml#metadata"
            itunes:timing="Word" xml:lang="en">
          <body><div>
            <p begin="1.0" end="3.0">$inner</p>
          </div></body>
        </tt>
        """.trimIndent(),
    )

    @Test
    fun `x-roman 收集为音译`() {
        val line = document(
            """<span begin="1.0" end="2.0">Hi</span>""" +
                    """<span ttm:role="x-roman" xml:lang="ja-Latn">hai</span>""",
        ).lines.single()

        assertEquals(listOf("hai"), line.transliterations.map { it.text })
        assertEquals("ja-Latn", line.transliterations.single().language)
    }

    @Test
    fun `x-translation 收集为译文`() {
        val line = document(
            """<span begin="1.0" end="2.0">Hi</span>""" +
                    """<span ttm:role="x-translation" xml:lang="zh-Hans">你好</span>""",
        ).lines.single()

        assertEquals(listOf("你好"), line.translations.map { it.text })
    }

    @Test
    fun `未知 role 当作普通词`() {
        val line = document(
            """<span begin="1.0" end="2.0">Hi</span>""" +
                    """<span ttm:role="x-weird" begin="2.0" end="3.0">!</span>""",
        ).lines.single()

        assertEquals("Hi!", line.text)
    }

    @Test
    fun `CDATA 作为文本`() {
        val line = document("""<![CDATA[Hello]]>""").lines.single()
        assertEquals("Hello", line.text)
    }

    @Test
    fun `嵌套背景组`() {
        val line = document(
            """<span begin="1.0" end="2.0">Main</span>""" +
                    """<span ttm:role="x-bg" begin="2.0" end="3.0"><span>a</span><span>b</span></span>""",
        ).lines.single()

        assertEquals("Main", line.text)
        assertEquals("ab", line.backgroundText)
    }

    @Test
    fun `空 span 不产生词`() {
        val line = document("""<span begin="1.0" end="2.0"></span>""").lines.single()
        assertTrue(line.words.isEmpty())
    }
}
