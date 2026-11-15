/*
 * Copyright 2026 Proify, Tomakino
 * Licensed under the Apache License, Version 2.0
 * http://www.apache.org/licenses/LICENSE-2.0
 */

@file:Suppress("RemoveRedundantBackticks", "NonAsciiCharacters")

import io.github.proify.lyricon.bodianprovider.xposed.KuwoLyricParser
import org.junit.Assert.assertEquals
import org.junit.Assert.assertTrue
import org.junit.Test

/**
 * 酷我 `.lrcx` 逐字解析验证。
 *
 * 样本按 App 的编码规则合成（避免把真实歌词版权内容放进仓库）：
 * ```
 * a = divBegin * begin + divEnd * duration
 * b = divBegin * begin − divEnd * duration
 * ```
 * 其中 begin 是**相对该行行首**的偏移，与真实文件一致。
 */
class KuwoLyricParserTest {

    private val divBegin = 5
    private val divEnd = 3

    /** V = divBegin * 10 + divEnd = 53，头部写成八进制就是 65 */
    private val header = "[kuwo:${Integer.toOctalString(divBegin * 10 + divEnd)}]"

    private fun tag(begin: Long, duration: Long): String {
        val a = divBegin * begin + divEnd * duration
        val b = divBegin * begin - divEnd * duration
        return "<$a,$b>"
    }

    @Test
    fun `八进制头部与逐字时间还原`() {
        val content = buildString {
            appendLine(header)
            appendLine("[ver:v1.0]")
            appendLine("[ti:测试曲目]")
            appendLine("[by:]")
            appendLine("[offset:0]")
            // 相对行首：0 / 300 / 600
            appendLine(
                "[00:10.500]" + tag(0, 300) + "一" + tag(300, 300) + "二" + tag(600, 400) + "三四"
            )
            appendLine("[00:20.000]" + tag(0, 500) + "Hello" + tag(500, 500) + " world")
        }

        val lines = KuwoLyricParser.parse(content)
        assertEquals(2, lines.size)

        val first = lines[0]
        assertEquals(10_500L, first.begin)
        assertEquals("一二三四", first.text)

        val words = first.words.orEmpty()
        assertEquals(3, words.size)

        // 文本切分
        assertEquals("一", words[0].text)
        assertEquals("二", words[1].text)
        assertEquals("三四", words[2].text)

        // 逐字时间 = 行首 + 相对偏移
        assertEquals(10_500L, words[0].begin)
        assertEquals(10_800L, words[0].end)
        assertEquals(10_800L, words[1].begin)
        assertEquals(11_100L, words[1].end)
        assertEquals(11_100L, words[2].begin)
        assertEquals(11_500L, words[2].end)

        // 行末以最后一个字唱完为准
        assertEquals(11_500L, first.end)
    }

    @Test
    fun `含空格的英文分词`() {
        val content = buildString {
            appendLine(header)
            appendLine("[00:20.000]" + tag(0, 500) + "Hello" + tag(500, 500) + " world")
        }

        val words = KuwoLyricParser.parse(content).single().words.orEmpty()
        assertEquals(2, words.size)
        assertEquals("Hello", words[0].text)
        assertEquals(" world", words[1].text)
        assertEquals(20_000L, words[0].begin)
        assertEquals(20_500L, words[1].begin)
        assertEquals(21_000L, words[1].end)
    }

    @Test
    fun `逐字文本拼回来必须等于整行`() {
        val content = buildString {
            appendLine(header)
            appendLine(
                "[01:02.345]" + tag(0, 200) + "男：晚" + tag(200, 250) + "风" +
                        tag(450, 300) + "的" + tag(750, 200) + "声音"
            )
        }

        val line = KuwoLyricParser.parse(content).single()
        assertEquals("男：晚风的声音", line.text)
        assertEquals(62_345L, line.begin)
        assertEquals(line.text, line.words.orEmpty().joinToString("") { it.text.orEmpty() })
    }

    @Test
    fun `没有 kuwo 头时回落到普通 LRC`() {
        val content = """
            [ti:普通歌词]
            [00:01.000]第一行
            [00:05.000]第二行
        """.trimIndent()

        val lines = KuwoLyricParser.parse(content)
        assertEquals(2, lines.size)
        assertEquals(1_000L, lines[0].begin)
        assertEquals("第一行", lines[0].text)
        assertTrue(lines[0].words.isNullOrEmpty())
    }

    @Test
    fun `时间标签解析`() {
        assertEquals(0L, KuwoLyricParser.parseTimeTag("[00:00.000]"))
        assertEquals(6_010L, KuwoLyricParser.parseTimeTag("[00:06.010]"))
        assertEquals(97_504L, KuwoLyricParser.parseTimeTag("[01:37.504]"))
        assertEquals(62_000L, KuwoLyricParser.parseTimeTag("[01:02]"))
        assertEquals(-1_500L, KuwoLyricParser.parseTimeTag("[-00:01.500]"))
    }
}
