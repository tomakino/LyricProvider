/*
 * Copyright 2026 Proify, Tomakino
 * Licensed under the Apache License, Version 2.0
 * http://www.apache.org/licenses/LICENSE-2.0
 */

package io.github.proify.amkit.ttmlparser

import io.github.proify.amkit.ttmlparser.model.LyricsTiming
import kotlin.test.Test
import kotlin.test.assertEquals
import kotlin.test.assertFailsWith
import kotlin.test.assertNull
import kotlin.time.Duration.Companion.milliseconds
import kotlin.time.Duration.Companion.seconds

class DocumentParsingTest {

    private val parser = TtmlLyricsParser()

    private val richTtml = """
        <tt xmlns="http://www.w3.org/ns/ttml"
            xmlns:itunes="http://music.apple.com/lyric-ttml-internal"
            xmlns:ttm="http://www.w3.org/ns/ttml#metadata"
            xmlns:amll="http://www.example.com/ns/amll"
            itunes:timing="Word" xml:lang="en">
          <head>
            <metadata>
              <ttm:title>Song</ttm:title>
              <ttm:agent type="person" xml:id="v1">
                <ttm:name type="full">Singer A</ttm:name>
                <ttm:name type="family">A</ttm:name>
              </ttm:agent>
              <ttm:agent type="group" xml:id="v2">
                <ttm:name type="full">Choir</ttm:name>
              </ttm:agent>
              <amll:meta key="musicName" value="Song"/>
              <amll:meta key="musicName" value="Song (EN)"/>
              <iTunesMetadata xmlns="http://music.apple.com/lyric-ttml-internal"
                  leadingSilence="0.060">
                <songwriters><songwriter>W1</songwriter></songwriters>
                <audio role="spatial" lyricOffset="0.045"/>
              </iTunesMetadata>
            </metadata>
          </head>
          <body dur="30.0">
            <div begin="0.0" end="15.0" itunes:song-part="Verse">
              <p begin="1.0" end="3.0" itunes:key="L1" ttm:agent="v1">
                <span begin="1.0" end="2.0">Hello</span>
                <span begin="2.0" end="3.0"> world</span>
              </p>
              <p begin="3.0" end="5.0" itunes:key="L2" ttm:agent="v2">
                <span begin="3.0" end="4.0">Backing</span>
                <span ttm:role="x-bg" begin="4.0" end="5.0"> (ooh)</span>
              </p>
            </div>
          </body>
        </tt>
    """.trimIndent()

    @Test
    fun `元数据`() {
        val document = parser.parse(richTtml)

        assertEquals("Song", document.metadata.title)
        assertEquals("en", document.language)
        assertEquals(listOf("Song", "Song (EN)"), document.metadata.meta["musicName"])
        assertEquals(listOf("W1"), document.metadata.songwriters)
    }

    @Test
    fun `演唱者`() {
        val document = parser.parse(richTtml)

        assertEquals(2, document.agents.size)
        val singer = document.agents.first { it.id == "v1" }
        assertEquals("person", singer.type)
        assertEquals(
            listOf("full" to "Singer A", "family" to "A"),
            singer.names.map { it.type to it.text },
        )
        assertEquals("Singer A", singer.firstDisplayName())
    }

    @Test
    fun `段落`() {
        val document = parser.parse(richTtml)

        val section = document.sections.single()
        assertEquals("Verse", section.songPart)
        assertEquals(0.seconds, section.start)
        assertEquals(15.seconds, section.end)
        assertEquals(2, section.lines.size)
        assertEquals(30.seconds, document.duration)
    }

    @Test
    fun `音频与前奏静音`() {
        val document = parser.parse(richTtml)

        assertEquals("spatial", document.audio?.role)
        assertEquals(45.milliseconds, document.audio?.lyricOffset)
        assertEquals(60.milliseconds, document.leadingSilence)
    }

    @Test
    fun `背景人声`() {
        val document = parser.parse(richTtml)

        val line = document.lines[1]
        assertEquals("Backing", line.text)
        assertEquals("(ooh)", line.backgroundText)
    }

    @Test
    fun `无计时文档`() {
        val ttml = """
            <tt xmlns="http://www.w3.org/ns/ttml"
                xmlns:itunes="http://music.apple.com/lyric-ttml-internal"
                itunes:timing="None">
              <body><div><p><span>Static</span></p></div></body>
            </tt>
        """.trimIndent()

        val document = parser.parse(ttml)

        assertEquals(LyricsTiming.NONE, document.timing)
        assertNull(document.lines.single().words.single().start)
    }

    @Test
    fun `逐行模式不补词时间`() {
        val ttml = """
            <tt xmlns="http://www.w3.org/ns/ttml"
                xmlns:itunes="http://music.apple.com/lyric-ttml-internal"
                itunes:timing="Line">
              <body><div>
                <p begin="2.0" end="4.0">Line only</p>
              </div></body>
            </tt>
        """.trimIndent()

        val document = parser.parse(ttml)
        val word = document.lines.single().words.single()

        assertNull(word.start)
        assertNull(word.end)
    }

    @Test
    fun `BOM 前缀`() {
        val document = parser.parse("\uFEFF" + richTtml)
        assertEquals("en", document.language)
    }

    @Test
    fun `空文本抛解析异常`() {
        assertFailsWith<LyricsParseException> { parser.parse("") }
        assertFailsWith<LyricsParseException> { parser.parse("   ") }
    }

    @Test
    fun `非法 XML 抛解析异常`() {
        assertFailsWith<LyricsParseException> { parser.parse("<tt") }
    }

    @Test
    fun `缺少根元素抛解析异常`() {
        assertFailsWith<LyricsParseException> { parser.parse("<?xml version=\"1.0\"?>") }
    }
}
