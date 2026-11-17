/*
 * Copyright 2026 Proify, Tomakino
 * Licensed under the Apache License, Version 2.0
 * http://www.apache.org/licenses/LICENSE-2.0
 */

package io.github.tomakino.amkit

import io.github.tomakino.amkit.parser.DisplayOptions
import io.github.tomakino.amkit.parser.TtmlLyricsParser
import io.github.tomakino.amkit.parser.displayLines
import kotlin.test.Test
import kotlin.test.assertEquals
import kotlin.test.assertFalse
import kotlin.test.assertNull
import kotlin.test.assertTrue

/** 解析与本地化投影的单元测试（不联网）。 */
class CoreTest {

    private val parser = TtmlLyricsParser()

    /**
     * 译文容器内的 `<span>` 必须显式声明 TTML 命名空间。
     *
     * 这是 Apple 真实响应的写法（容器是 itunes 命名空间，词仍是 TTML）；
     * 省略时词会被当成非歌词元素跳过。
     */
    private val replacementTtml = """
        <tt xmlns="http://www.w3.org/ns/ttml"
            xmlns:itunes="http://music.apple.com/lyric-ttml-internal"
            xmlns:ttm="http://www.w3.org/ns/ttml#metadata"
            itunes:timing="Word" xml:lang="zh-Hant">
          <head><metadata>
            <iTunesMetadata xmlns="http://music.apple.com/lyric-ttml-internal">
              <translations>
                <translation xml:lang="zh-Hans" type="replacement">
                  <text for="L1">
                    <span begin="1.0" end="2.0" xmlns="http://www.w3.org/ns/ttml">我受够了</span>
                  </text>
                </translation>
              </translations>
            </iTunesMetadata>
          </metadata></head>
          <body><div>
            <p begin="1.0" end="2.0" itunes:key="L1" ttm:agent="v1">
              <span begin="1.0" end="2.0">我受夠了</span>
            </p>
          </div></body>
        </tt>
    """.trimIndent()

    /** 同一行同时带：replacement 译文、subtitle 译文、ja-Latn 音译。 */
    private val richTtml = """
        <tt xmlns="http://www.w3.org/ns/ttml"
            xmlns:itunes="http://music.apple.com/lyric-ttml-internal"
            xmlns:ttm="http://www.w3.org/ns/ttml#metadata"
            itunes:timing="Word" xml:lang="zh-Hant">
          <head><metadata>
            <iTunesMetadata xmlns="http://music.apple.com/lyric-ttml-internal">
              <translations>
                <translation xml:lang="zh-Hans" type="replacement">
                  <text for="L1">
                    <span begin="1.0" end="2.0" xmlns="http://www.w3.org/ns/ttml">我受够了</span>
                  </text>
                </translation>
                <translation xml:lang="zh-Hans" type="subtitle">
                  <text for="L1">
                    <span begin="1.0" end="2.0" xmlns="http://www.w3.org/ns/ttml">我受够了·字幕</span>
                  </text>
                </translation>
              </translations>
              <transliterations>
                <transliteration xml:lang="ja-Latn">
                  <text for="L1">
                    <span begin="1.0" end="2.0" xmlns="http://www.w3.org/ns/ttml">watashi</span>
                  </text>
                </transliteration>
              </transliterations>
            </iTunesMetadata>
          </metadata></head>
          <body><div>
            <p begin="1.0" end="2.0" itunes:key="L1" ttm:agent="v1">
              <span begin="1.0" end="2.0">我受夠了</span>
            </p>
          </div></body>
        </tt>
    """.trimIndent()

    @Test
    fun `解析出语言、计时与逐字`() {
        val document = parser.parse(replacementTtml)

        assertEquals("zh-Hant", document.language)
        assertEquals(1, document.sections.size)
        assertEquals(1, document.lines.size)

        val line = document.lines.single()
        assertEquals("我受夠了", line.text)
        assertEquals(1, line.words.size)
        assertEquals("L1", line.key)
        assertEquals("v1", line.agentId)
    }

    @Test
    fun `replacement 替换主文本并采用译文逐字`() {
        val document = parser.parse(replacementTtml)
        val line = document.displayLines(DisplayOptions(language = "zh-Hans")).single()

        assertEquals("我受够了", line.text)
        assertEquals("我受够了", line.words.joinToString("") { it.text })
        assertNull(line.translation)
        assertTrue(line.words.all { it.start != null })
    }

    @Test
    fun `关闭替换后译文作为对照`() {
        val document = parser.parse(replacementTtml)
        val line = document.displayLines(
            DisplayOptions(language = "zh-Hans", applyReplacement = false),
        ).single()

        assertEquals("我受夠了", line.text)
        assertEquals("我受够了", line.translation)
    }

    @Test
    fun `纯文本译文可按字符对齐`() {
        val plainTtml = replacementTtml.replace(
            """<span begin="1.0" end="2.0" xmlns="http://www.w3.org/ns/ttml">我受够了</span>""",
            "我受够了",
        )

        val document = parser.parse(plainTtml)
        val line = document.displayLines(DisplayOptions(language = "zh-Hans")).single()

        assertEquals("我受够了", line.text)
        assertEquals(1, line.words.size)
        assertEquals("我受够了", line.words.single().text)
    }

    @Test
    fun `语言标签精确匹配`() {
        val document = parser.parse(replacementTtml)

        assertTrue(document.hasTranslation("zh-Hans"))
        assertFalse(document.hasTranslation("zh-Hans-CN"))
        assertEquals(listOf("zh-Hans"), document.translationLanguages)
    }

    @Test
    fun `语言回退 zh-Hans-CN 命中 zh-Hans`() {
        val document = parser.parse(replacementTtml)

        assertTrue(document.hasTranslation("zh-Hans-CN", fallback = true))
        assertTrue(document.translationFor("zh-Hans-CN", fallback = true)?.language == "zh-Hans")

        val line = document.displayLines(DisplayOptions(language = "zh-Hans-CN")).single()
        assertEquals("我受够了", line.text)
    }

    @Test
    fun `language 为 null 时自动取第一条译文`() {
        val document = parser.parse(replacementTtml)
        val line = document.displayLines().single()

        assertEquals("我受够了", line.text)
    }

    @Test
    fun `音译按独立语言投影`() {
        val document = parser.parse(richTtml)
        val line = document.displayLines(
            DisplayOptions(language = "zh-Hans", transliterationLanguage = "ja-Latn"),
        ).single()

        assertEquals("watashi", line.transliteration)
        assertEquals("watashi", line.transliterationWords?.joinToString("") { it.text })
    }

    @Test
    fun `translationType 指定 subtitle 时保留原文`() {
        val document = parser.parse(richTtml)
        val line = document.displayLines(
            DisplayOptions(language = "zh-Hans", translationType = "subtitle"),
        ).single()

        assertEquals("我受夠了", line.text)
        assertEquals("我受够了·字幕", line.translation)
    }

    @Test
    fun `translationType 缺省取第一条`() {
        val document = parser.parse(richTtml)
        val line = document.displayLines(DisplayOptions(language = "zh-Hans")).single()

        assertEquals("我受够了", line.text)
    }
}
