/*
 * Copyright 2026 Proify, Tomakino
 * Licensed under the Apache License, Version 2.0
 * http://www.apache.org/licenses/LICENSE-2.0
 */

package io.github.proify.lrckit

import org.junit.jupiter.api.Assertions.assertEquals
import org.junit.jupiter.api.Assertions.assertFalse
import org.junit.jupiter.api.Assertions.assertNotNull
import org.junit.jupiter.api.Assertions.assertTrue
import org.junit.jupiter.api.DisplayName
import org.junit.jupiter.api.Test

/**
 * LrcParser 单元测试类。
 * 覆盖了标准解析、多时间戳、中括号兼容性、元数据提取及偏移量处理。
 */
class LrcParserTest {

    @Test
    @DisplayName("测试基础解析：标准格式与时间转换")
    fun testBaseParse() {
        val lrc = "[00:01.50]Hello World"
        val document = LrcParser.parse(lrc)

        assertEquals(1, document.lines.size)
        val line = document.lines[0]
        assertEquals(1500L, line.begin)
        assertEquals("Hello World", line.text)
    }

    @Test
    @DisplayName("测试核心优化：修复歌词正文包含中括号时的截断异常")
    fun testBracketsInContent() {
        // 模拟网易云等平台常见的带中括号补充信息的歌词
        val lrc = "[00:10.00] [和声] 这是一个测试 [歌词中括号]"
        val document = LrcParser.parse(lrc)

        assertEquals(1, document.lines.size)
        // 关键：验证 lastTagEnd 之后的全部内容被保留，且没有被错误截断
        assertEquals("[和声] 这是一个测试 [歌词中括号]", document.lines[0].text)
    }

    @Test
    @DisplayName("测试多时间标签：同一行歌词对应多个时间点")
    fun testMultiTagPerLine() {
        val lrc = "[00:01.00][00:05.00]重复的歌词"
        val document = LrcParser.parse(lrc)

        // 应该解析为两条 LyricLine
        assertEquals(2, document.lines.size)
        assertEquals(1000L, document.lines[0].begin)
        assertEquals(5000L, document.lines[1].begin)
        assertEquals("重复的歌词", document.lines[0].text)
        assertEquals("重复的歌词", document.lines[1].text)
    }

    @Test
    @DisplayName("测试元数据提取：解析 [ar:xxx] 等标签")
    fun testMetaExtraction() {
        val lrc = """
            [ar: Artist Name]
            [ti: Song Title]
            [offset: 500]
            [00:01.00]Lyrics
        """.trimIndent()

        val document = LrcParser.parse(lrc)

        assertEquals("Artist Name", document.metadata["ar"])
        assertEquals("Song Title", document.metadata["ti"])
        // offset 500ms 应该应用到歌词时间轴上 (1000 + 500 = 1500)
        assertEquals(1500L, document.lines[0].begin)
    }

    @Test
    @DisplayName("测试时间戳容错：支持不同的毫秒位数")
    fun testTimeFormatAdaptation() {
        val lrc = """
            [00:01.5] 一位毫秒
            [00:02.50] 两位毫秒
            [00:03.500] 三位毫秒
        """.trimIndent()

        val document = LrcParser.parse(lrc)

        assertEquals(1500L, document.lines[0].begin)
        assertEquals(2500L, document.lines[1].begin)
        assertEquals(3500L, document.lines[2].begin)
    }

    @Test
    @DisplayName("测试边界情况：空字符串与非法行")
    fun testEdgeCases() {
        assertNotNull(LrcParser.parse(null))
        assertNotNull(LrcParser.parse(""))

        val invalidLrc = "这是一行没有标签的纯文本\n[invalid]标签格式错误"
        val document = LrcParser.parse(invalidLrc)
        assertTrue(document.lines.isEmpty())
    }

    @Test
    @DisplayName("测试持续时间计算：修正末行结束时间")
    fun testDurationAndFinalize() {
        val lrc = "[00:01.00]第一句"
        val totalDuration = 10000L // 10秒
        val document = LrcParser.parse(lrc, totalDuration)

        val line = document.lines[0]
        assertEquals(1000L, line.begin)
        assertEquals(10000L, line.end)
        assertEquals(9000L, line.duration)
    }

    @Test
    @DisplayName("测试解析罗马音歌词：时间轴不得丢失或重叠")
    fun testParseRomaLyrics() {
        val lrc =
            "[00:00.850]\n[00:01.000]tsu yo ku na re ru ri yu u wo shi tta bo ku wo tsu re te su su me\n[00:18.570]do ro da ra ke no so u ma to u ni yo u ko wa ba ru ko ko ro\n[00:25.760]fu ru e ru te wa tsu ka mi ta i mo no ga a ru so re da ke sa\n[00:32.800]yo ru no ni o i ni\n[00:36.260]so ra ni ra n de mo\n[00:39.650]ka wa tte i ke ru no wa ji bu n ji shi n da ke so re da ke sa\n[00:46.940]tsu yo ku na re ru ri yu u wo shi tta bo ku wo tsu re te su su me\n[00:59.850]do u shi ta tte!\n[01:01.320]ke se na i yu me mo to ma re na i i ma mo\n[01:04.750]da re ka no ta me ni tsu yo ku na re ru na ra\n[01:09.160]a ri ga to u ka na shi mi yo\n[01:13.990]se ka i ni u chi no me sa re te ma ke ru i mi wo shi tta\n[01:19.030]gu re n no ha na yo sa ki ho ko re! u n me i wo te ra shi te\n[01:33.190]i na bi ka ri no za tsu o n ga mi mi wo sa su to ma do u ko ko ro\n[01:40.550]ya sa shi i da ke ja ma mo re na i mo no ga a ru? wa ka tte ru ke do\n[01:47.610]su i me n ka de ka ra ma ru ze n'a ku su ke te mi e ru gi ze n ni te n ba tsu\n[01:51.410]\n[01:52.970]\n[01:54.660]i tsu za i no ha na yo ri i do mi tsu zu ke sa i ta i chi ri n ga u tsu ku shi i\n[02:00.410]ra n bo u ni shi ki tsu me ra re ta to ge da ra ke no mi chi mo\n[02:05.170]ho n ki no bo ku da ke ni a ra wa re ru ka ra no ri ko e te mi se ru yo\n[02:14.390]ka n ta n ni ka ta zu ke ra re ta ma mo re na ka tta yu me mo\n[02:19.400]gu re n no shi n zo u ni ne wo ha ya shi ko no chi ni ya do tte ru\n[02:41.170]hi to shi re zu ha ka na i chi ri yu ku ke tsu ma tsu\n[02:48.350]mu jo u ni ya bu re ta hi me i no ka ze fu ku\n[02:55.670]da re ka no wa ra u ka ge da re ka no na ki go e\n[03:02.160]da re mo ga shi a wa se wo ne ga tte ru\n[03:07.820]do u shi ta tte!\n[03:09.270]ke se na i yu me mo to ma re na i i ma mo\n[03:12.680]da re ka no ta me ni tsu yo ku na re ru na ra\n[03:17.140]a ri ga to u ka na shi mi yo\n[03:22.120]se ka i ni u chi no me sa re te ma ke ru i mi wo shi tta\n[03:26.970]gu re n no ha na yo sa ki ho ko re! u n me i wo te ra shi te"

        val document = LrcParser.parse(lrc)
        val lines = document.lines

        // 罗马音歌词：纯英文/空格正文不得被丢弃，时间轴必须自洽且正文不被标签污染
        assertTrue(lines.size > 10, "罗马音歌词解析出的行数异常: ${lines.size}")
        lines.forEach { line ->
            assertTrue(line.duration > 0, "存在零时长行: $line")
            assertFalse(
                line.text?.contains("]") == true,
                "正文残留时间标签，说明发生了截断异常: $line"
            )
        }
        lines.zipWithNext().forEach { (previous, current) ->
            assertTrue(previous.begin <= current.begin, "时间轴未排序")
            assertTrue(previous.end <= current.begin, "时间轴出现重叠")
        }
    }
}