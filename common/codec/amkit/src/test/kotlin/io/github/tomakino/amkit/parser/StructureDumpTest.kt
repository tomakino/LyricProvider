/*
 * Copyright 2026 Proify, Tomakino
 * Licensed under the Apache License, Version 2.0
 * http://www.apache.org/licenses/LICENSE-2.0
 */

package io.github.proify.amkit.parser

import io.github.proify.amkit.parser.model.toStructuredString
import java.io.File
import kotlin.test.Test
import kotlin.test.assertEquals
import kotlin.test.assertTrue

/** 打印真实样本的解析结构，用于人工核对。 */
class StructureDumpTest {

    private val parser = TtmlLyricsParser()

    private val sampleContent: String by lazy {
        File("src/test/resources/samples")
            .listFiles { file -> file.extension == "ttml" }
            .orEmpty()
            .sortedBy { it.name }
            .first()
            .readText()
    }

    @Test
    fun `打印真实样本的歌词结构`() {
        val text = parser.parse(sampleContent).toStructuredString()
        val lines = text.lines()

        println("=== sample ===")
        println(lines.take(DUMP_LINES).joinToString("\n"))
        if (lines.size > DUMP_LINES) {
            println("... ${lines.size - DUMP_LINES} more lines")
        }

        assertTrue(text.startsWith("LyricsDocument("))
    }

    @Test
    fun `逐词保留源文本空格`() {
        val document = parser.parse(sampleContent)
        val transliteration = document.lines
            .firstNotNullOfOrNull { line -> line.transliterations.firstOrNull() }
            ?: error("样本没有音译")

        val words = transliteration.words
        println("word count = ${words.size}")
        words.take(16).forEachIndexed { index, word ->
            val text = word.text
            val codes = text.map { "%04x".format(it.code) }.joinToString(" ")
            println("[$index] len=${text.length} codes=[$codes] 「$text」")
        }

        val joined = words.joinToString(separator = "") { it.text }
        println("joined = 「$joined」")
        println("text   = 「${transliteration.text}」")
        println("hasSpaceWord = ${words.any { it.text == " " }}")

        assertEquals(transliteration.text, joined)
    }

    private companion object {
        const val DUMP_LINES = 100
    }
}
