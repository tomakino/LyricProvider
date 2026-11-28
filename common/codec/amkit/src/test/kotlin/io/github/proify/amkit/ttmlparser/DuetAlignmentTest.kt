/*
 * Copyright 2026 Proify, Tomakino
 * Licensed under the Apache License, Version 2.0
 * http://www.apache.org/licenses/LICENSE-2.0
 */

package io.github.proify.amkit.ttmlparser

import java.io.File
import kotlin.test.Test
import kotlin.test.assertFalse
import kotlin.test.assertTrue

/** 对唱左右分列的回归：官方 `v1` 左、其余右。 */
class DuetAlignmentTest {

    @Test
    fun `手心的蔷薇 主唱左 副唱右`() {
        val ttml = File("src/test/resources/samples/1788007693.ttml").readText()
        val song = TtmlLyricsParser().parse(ttml).toSong()

        val lyrics = requireNotNull(song.lyrics)
        val l25 = lyrics[24]
        val l26 = lyrics[25]
        val l27 = lyrics[26]
        println("L25 '${l25.text}' right=${l25.isAlignedRight}")
        println("L26 '${l26.text}' right=${l26.isAlignedRight}")
        println("L27 '${l27.text}' right=${l27.isAlignedRight}")

        assertFalse(l25.isAlignedRight, "L25 v1 应在左")
        assertTrue(l26.isAlignedRight, "L26 v2 应在右")
        assertFalse(l27.isAlignedRight, "L27 v1 应在左")
    }

    @Test
    fun `Good Time 结尾合唱行在左`() {
        val ttml = File("src/test/resources/samples/1443468698.ttml").readText()
        val song = TtmlLyricsParser().parse(ttml).toSong()
        val lyrics = requireNotNull(song.lyrics)

        val tail = lyrics.takeLast(8)
        tail.forEach { println("tail '${it.text}' right=${it.isAlignedRight}") }
        tail.forEach { line ->
            assertFalse(line.isAlignedRight, "结尾合唱行应在左：${line.text}")
        }
    }

    @Test
    fun `手心的蔷薇 结尾伤痕累累在左`() {
        val ttml = File("src/test/resources/samples/1788007693.ttml").readText()
        val song = TtmlLyricsParser().parse(ttml).toSong()
        val lyrics = requireNotNull(song.lyrics)

        val line = lyrics[59]
        println("L60 '${line.text}' right=${line.isAlignedRight}")
        assertFalse(line.isAlignedRight, "伤痕累累应在左：${line.text}")
    }

    @Test
    fun `我的美丽 最后我被排除在节奏之外在左`() {
        val ttml = File("src/test/resources/samples/1708336521.ttml").readText()
        val song = TtmlLyricsParser().parse(ttml).toSong()
        val lyrics = requireNotNull(song.lyrics)

        val line = lyrics[60]
        println("L61 '${line.text}' right=${line.isAlignedRight}")
        assertFalse(line.isAlignedRight, "我被排除在节奏之外应在左：${line.text}")
    }
}
