/*
 * Copyright 2026 Proify, Tomakino
 * Licensed under the Apache License, Version 2.0
 * http://www.apache.org/licenses/LICENSE-2.0
 */

package io.github.tomakino.amkit.parser

import io.github.tomakino.amkit.parser.model.LyricsTiming
import io.github.tomakino.amkit.parser.model.flatten
import io.github.tomakino.amkit.parser.model.toStructuredString
import java.io.File
import kotlin.test.Test
import kotlin.test.assertEquals
import kotlin.test.assertNotNull
import kotlin.test.assertTrue

/**
 * 用真实 Apple 响应做回归：只断言结构自洽，不比对逐字文本。
 */
class SampleCorpusTest {

    private val parser = TtmlLyricsParser()

    private val samples: List<Pair<String, String>> by lazy {
        val dir = File("src/test/resources/samples")
        require(dir.isDirectory) { "缺少样本目录: ${dir.absolutePath}" }
        dir.listFiles { file -> file.extension == "ttml" }
            .orEmpty()
            .sortedBy { it.name }
            .map { it.nameWithoutExtension to it.readText() }
    }

    @Test
    fun `至少包含 6 份真实样本`() {
        assertTrue(samples.size >= 6, "实际 ${samples.size}")
    }

    @Test
    fun `全部样本可解析`() {
        samples.forEach { (name, ttml) ->
            val document = parser.parse(ttml)
            assertNotNull(document.language, name)
            assertTrue(document.lines.isNotEmpty(), name)
            assertTrue(document.timing != LyricsTiming.UNKNOWN, name)
        }
    }

    @Test
    fun `行 key 唯一`() {
        samples.forEach { (name, ttml) ->
            val keys = parser.parse(ttml).lines.mapNotNull { it.key }
            assertEquals(keys.size, keys.toSet().size, name)
        }
    }

    @Test
    fun `词时间自洽`() {
        samples.forEach { (name, ttml) ->
            parser.parse(ttml).lines.forEach { line ->
                line.words.forEach { word ->
                    val start = word.start
                    val end = word.end
                    if (start != null && end != null) {
                        assertTrue(end >= start, "$name 词时间倒挂: ${word.text}")
                    }
                }
            }
        }
    }

    @Test
    fun `译文语言可投影`() {
        samples.forEach { (name, ttml) ->
            val document = parser.parse(ttml)
            val languages = document.translationLanguages.ifEmpty { listOf("") }

            languages.forEach { language ->
                val lines = document.displayLines(DisplayOptions(language = language))
                assertEquals(document.lines.size, lines.size, "$name 投影行数不一致")
            }
        }
    }

    @Test
    fun `音译语言可投影`() {
        var projected = 0

        samples.forEach { (name, ttml) ->
            val document = parser.parse(ttml)

            document.transliterationLanguages.forEach { language ->
                val lines = document.displayLines(
                    DisplayOptions(transliterationLanguage = language),
                )
                assertEquals(document.lines.size, lines.size, name)
                if (lines.any { it.transliteration != null }) projected++
            }
        }

        assertTrue(projected > 0, "语料里应至少有一份包含音译")
    }

    @Test
    fun `有译文的样本可替换`() {
        val withTranslation = samples.filter { (_, ttml) ->
            parser.parse(ttml).translationLanguages.isNotEmpty()
        }

        assertTrue(withTranslation.isNotEmpty(), "语料里应至少有一份包含译文")

        withTranslation.forEach { (name, ttml) ->
            val document = parser.parse(ttml)
            val language = document.translationLanguages.first()
            val line = document.displayLines(DisplayOptions(language = language)).first()

            assertNotNull(line, name)
            assertTrue(line.text.isNotEmpty(), name)
        }
    }

    @Test
    fun `输出视图对真实样本不崩溃`() {
        samples.forEach { (name, ttml) ->
            val document = parser.parse(ttml)

            assertTrue(document.flatten().isNotEmpty(), name)
            assertTrue(document.toStructuredString().contains("LyricsDocument("), name)
        }
    }
}
