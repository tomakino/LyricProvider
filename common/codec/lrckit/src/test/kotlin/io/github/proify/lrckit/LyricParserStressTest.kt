/*
 * Copyright 2026 Proify, Tomakino
 * Licensed under the Apache License, Version 2.0
 * http://www.apache.org/licenses/LICENSE-2.0
 */

package io.github.proify.lrckit

import io.github.proify.lyricon.lyric.model.LyricLine
import org.junit.jupiter.api.Assertions.assertEquals
import org.junit.jupiter.api.Assertions.assertTrue
import org.junit.jupiter.api.DisplayName
import org.junit.jupiter.api.Test
import kotlin.random.Random

/**
 * [LrcParser] 与 [EnhanceLrcParser] 的规模与不变量压力测试。
 */
class LyricParserStressTest {

    @Test
    @DisplayName("压力：两万行标准 LRC")
    fun parsesLargePlainDocument() {
        val count = 20_000
        val raw = buildString {
            repeat(count) { index ->
                append('[').append(formatTime(index * 1000L)).append(']')
                append("第 ").append(index).append(" 行")
                append('\n')
            }
        }

        val started = System.nanoTime()
        val lines = LrcParser.parse(raw, duration = count * 1000L).lines
        val elapsedMs = (System.nanoTime() - started) / 1_000_000
        println("plain $count lines: ${elapsedMs}ms")

        assertEquals(count, lines.size)
        assertEquals(0L, lines.first().begin)
        assertEquals((count - 1) * 1000L, lines.last().begin)
        assertEquals("第 0 行", lines.first().text)
        assertEquals("第 ${count - 1} 行", lines.last().text)
        lines.forEachIndexed { index, line ->
            assertEquals(index * 1000L, line.begin)
            assertEquals(1000L, line.duration)
        }
        assertTrue(elapsedMs < 10_000, "解析耗时异常: ${elapsedMs}ms")
    }

    @Test
    @DisplayName("压力：五千行、十万逐字")
    fun parsesLargeEnhancedDocument() {
        val lineCount = 5_000
        val wordsPerLine = 20
        val raw = buildString {
            repeat(lineCount) { lineIndex ->
                val lineBegin = lineIndex * 2000L
                append('[').append(formatTime(lineBegin)).append(']')
                repeat(wordsPerLine) { wordIndex ->
                    append('<').append(formatTime(lineBegin + wordIndex * 100L)).append('>')
                    append('字')
                }
                append('\n')
            }
        }

        val started = System.nanoTime()
        val lines = EnhanceLrcParser.parse(raw, duration = lineCount * 2000L).lines
        val elapsedMs = (System.nanoTime() - started) / 1_000_000
        println("enhanced $lineCount lines / $wordsPerLine words: ${elapsedMs}ms")

        assertEquals(lineCount, lines.size)
        assertEquals(lineCount * wordsPerLine, lines.sumOf { it.words?.size ?: 0 })
        lines.forEachIndexed { index, line ->
            val words = line.words!!
            assertEquals(line.text, words.joinToString("") { it.text.orEmpty() })
            assertEquals(line.end, words.last().end)
            assertEquals(index * 2000L, line.begin)
        }
        assertTrue(elapsedMs < 20_000, "解析耗时异常: ${elapsedMs}ms")
    }

    @Test
    @DisplayName("压力：单行两万个逐字标签")
    fun parsesLongSingleLine() {
        val wordsPerLine = 20_000
        val raw = buildString {
            append("[00:00.00]")
            repeat(wordsPerLine) { index ->
                append('<').append(formatTime(index * 10L)).append('>').append('字')
            }
        }

        val started = System.nanoTime()
        val line = EnhanceLrcParser.parse(raw, duration = 300_000L).lines.single()
        val elapsedMs = (System.nanoTime() - started) / 1_000_000
        println("single line $wordsPerLine words: ${elapsedMs}ms")

        assertEquals(wordsPerLine, line.words?.size)
        assertEquals(wordsPerLine, line.text?.length ?: 0)
        assertEquals(line.end, line.words!!.last().end)
        assertTrue(elapsedMs < 20_000, "解析耗时异常: ${elapsedMs}ms")
    }

    @Test
    @DisplayName("压力：随机文档满足时间轴不变量")
    fun randomizedDocumentsSatisfyInvariants() {
        val random = Random(20261120L)
        repeat(200) { documentIndex ->
            val raw = randomDocument(random)
            val plain = LrcParser.parse(raw)
            val enhanced = EnhanceLrcParser.parse(raw)

            assertEquals(plain.lines.size, enhanced.lines.size, "文档 $documentIndex 行数不一致")
            plain.lines.zip(enhanced.lines).forEach { (actual, expected) ->
                assertEquals(expected.begin, actual.begin)
                assertEquals(expected.end, actual.end)
                assertEquals(expected.text, actual.text)
            }
            assertTimeline(plain.lines, documentIndex)
            assertTimeline(enhanced.lines, documentIndex)
        }
    }

    @Test
    @DisplayName("压力：随机逐字文档的空格与时间轴不变量")
    fun randomizedWordDocumentsSatisfyInvariants() {
        val random = Random(20261121L)
        repeat(200) { documentIndex ->
            val raw = randomWordDocument(random)
            val plain = LrcParser.parse(raw)
            val enhanced = EnhanceLrcParser.parse(raw)

            assertEquals(plain.lines.size, enhanced.lines.size, "文档 $documentIndex 行数不一致")
            plain.lines.zip(enhanced.lines).forEachIndexed { lineIndex, (actual, expected) ->
                val where = "文档 $documentIndex 第 $lineIndex 行\n$raw"
                assertEquals(expected.begin, actual.begin, "$where begin 不一致")
                assertEquals(expected.end, actual.end, "$where end 不一致")
                assertEquals(expected.text, actual.text, "$where text 不一致")
            }
            assertTimeline(enhanced.lines, documentIndex)
        }
    }

    /**
     * 生成带逐字标签与多种空白字符的随机文档。
     * @param random 随机源。
     * @return 歌词文本。
     */
    private fun randomWordDocument(random: Random): String = buildString {
        val lineCount = random.nextInt(1, 10)
        var cursor = random.nextLong(0L, 1_000L)
        val wordPool = listOf("你", "好", "Hello ", "World", "　", "😀", "a  b", "line ")
        repeat(lineCount) {
            cursor += random.nextLong(1_000L, 4_000L)
            append('[').append(formatTime(cursor)).append(']')
            var wordCursor = cursor
            repeat(random.nextInt(1, 6)) {
                append('<').append(formatTime(wordCursor)).append('>')
                append(wordPool.random(random))
                wordCursor += 100L
            }
            append('\n')
        }
    }

    /**
     * 校验时间轴与逐字时间窗的解析不变量。
     * @param lines 待校验的歌词行。
     * @param documentIndex 样本下标。
     */
    private fun assertTimeline(lines: List<LyricLine>, documentIndex: Int) {
        lines.forEachIndexed { index, line ->
            val where = "文档 $documentIndex 第 $index 行"
            assertTrue(line.begin >= 0, "$where begin 为负")
            assertTrue(line.end >= line.begin, "$where end 早于 begin")
            assertEquals(line.end - line.begin, line.duration, "$where duration 不一致")
            if (index > 0) {
                val previous = lines[index - 1]
                assertTrue(previous.begin <= line.begin, "$where 未排序")
                if (previous.begin < line.begin) {
                    assertEquals(line.begin, previous.end, "$where 与上一行不衔接")
                } else {
                    assertEquals(line.end, previous.end, "$where 同一时间点结束时间不一致")
                }
            }
        }
        lines.forEachIndexed { index, line ->
            val words = line.words ?: return@forEachIndexed
            if (words.isEmpty()) return@forEachIndexed
            val where = "文档 $documentIndex 第 $index 行逐字"
            assertEquals(line.text, words.joinToString("") { it.text.orEmpty() }, "$where 文本不一致")
            assertEquals(line.end, words.last().end, "$where 末词未对齐")
            words.zipWithNext().forEach { (previous, current) ->
                assertTrue(previous.begin <= current.begin, "$where 词未排序")
            }
            words.forEach { word ->
                assertTrue(word.begin >= line.begin, "$where 词早于行开始")
                assertTrue(word.end <= line.end, "$where 词晚于行结束")
                assertEquals(word.end - word.begin, word.duration, "$where 词 duration 不一致")
            }
        }
    }

    /**
     * 生成随时间递进、可包含多时间标签的随机文档。
     * @param random 随机源。
     * @return 歌词文本。
     */
    private fun randomDocument(random: Random): String = buildString {
        val lineCount = random.nextInt(1, 20)
        var cursor = random.nextLong(0L, 2_000L)
        repeat(lineCount) { lineIndex ->
            cursor += random.nextLong(1_000L, 4_000L)
            val tags = if (random.nextInt(4) == 0) 2 else 1
            repeat(tags) {
                append('[').append(formatTime(cursor)).append(']')
            }
            when (random.nextInt(3)) {
                0 -> append("纯文本 ").append(lineIndex)
                1 -> append("【").append(lineIndex).append("】")
                else -> append("line ").append(lineIndex)
            }
            append('\n')
        }
    }

    /**
     * 将毫秒格式化为 mm:ss.SSS。
     * @param ms 毫秒时间。
     * @return 时间标签文本。
     */
    private fun formatTime(ms: Long): String {
        val minutes = ms / 60_000
        val seconds = ms % 60_000 / 1000
        val fraction = ms % 1000
        return buildString {
            append(minutes.toString().padStart(2, '0'))
            append(':')
            append(seconds.toString().padStart(2, '0'))
            append('.')
            append(fraction.toString().padStart(3, '0'))
        }
    }
}
