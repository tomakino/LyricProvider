/*
 * Copyright 2026 Proify, Tomakino
 * Licensed under the Apache License, Version 2.0
 * http://www.apache.org/licenses/LICENSE-2.0
 */

package io.github.proify.amkit.parser

import io.github.proify.amkit.parser.model.DisplayLine
import java.io.File
import kotlin.test.Test
import kotlin.test.assertEquals
import kotlin.test.assertTrue

/** 多语言语料的批量结构验证。 */
class CorpusTest {

    private val parser = TtmlLyricsParser()

    private val samples: List<Pair<String, String>> by lazy {
        val dir = File("src/test/resources/samples")
        require(dir.isDirectory) { "缺少样本目录" }
        dir.listFiles { file -> file.extension == "ttml" }
            .orEmpty()
            .sortedBy { it.name }
            .map { it.nameWithoutExtension to it.readText() }
    }

    private fun project(ttml: String): List<DisplayLine> {
        val document = parser.parse(ttml)
        return document.displayLines(
            DisplayOptions(
                language = document.translationLanguages.firstOrNull(),
                transliterationLanguage = document.transliterationLanguages.firstOrNull(),
            ),
        )
    }

    /** replacement 译文不并入主文本，用于检查逐词翻译。 */
    private fun projectAsSubtitle(ttml: String): List<DisplayLine> {
        val document = parser.parse(ttml)
        return document.displayLines(
            DisplayOptions(
                language = document.translationLanguages.firstOrNull(),
                applyReplacement = false,
            ),
        )
    }

    @Test
    fun `语料规模`() {
        assertTrue(samples.size >= 20, "实际 ${samples.size}")
    }

    @Test
    fun `每份样本都能投影`() {
        samples.forEach { (name, ttml) ->
            val document = parser.parse(ttml)
            val lines = project(ttml)

            assertEquals(document.lines.size, lines.size, name)
            assertTrue(lines.isNotEmpty(), name)
        }
    }

    @Test
    fun `text 与 words 一致`() {
        samples.forEach { (name, ttml) ->
            project(ttml).forEachIndexed { index, line ->
                assertEquals(
                    line.words.joinToString(separator = "") { it.text },
                    line.text,
                    "$name 第 $index 行",
                )
            }
        }
    }

    @Test
    fun `有译文时 translationWords 结构自洽`() {
        samples.forEach { (name, ttml) ->
            project(ttml).forEach { line ->
                val translation = line.translation
                val words = line.translationWords

                if (translation == null) {
                    assertTrue(words == null, "$name 无译文却有逐词片段")
                }
                if (words != null) {
                    assertEquals(
                        translation,
                        words.joinToString(separator = "") { it.text },
                        name,
                    )
                }
            }
        }
    }

    @Test
    fun `有音译时 transliterationWords 结构自洽`() {
        samples.forEach { (name, ttml) ->
            project(ttml).forEach { line ->
                val transliteration = line.transliteration
                val words = line.transliterationWords

                if (transliteration == null) {
                    assertTrue(words == null, "$name 无音译却有逐词片段")
                }
                if (words != null) {
                    assertEquals(
                        transliteration,
                        words.joinToString(separator = "") { it.text },
                        name,
                    )
                }
            }
        }
    }

    @Test
    fun `有背景时 backgroundWords 非空`() {
        samples.forEach { (name, ttml) ->
            project(ttml).forEach { line ->
                if (line.background != null) {
                    assertTrue(line.backgroundWords.isNotEmpty(), name)
                    assertEquals(
                        line.background,
                        line.backgroundWords.joinToString(separator = "") { it.text },
                        name,
                    )
                }
            }
        }
    }

    @Test
    fun `语料覆盖统计`() {
        var withTranslation = 0
        var withTransliteration = 0
        var linesWithBackground = 0
        var linesWithTranslationWords = 0
        var linesWithTransliterationWords = 0
        var totalLines = 0
        var totalWords = 0
        val languages = mutableSetOf<String>()

        samples.forEach { (_, ttml) ->
            val document = parser.parse(ttml)
            if (document.translationLanguages.isNotEmpty()) withTranslation++
            if (document.transliterationLanguages.isNotEmpty()) withTransliteration++
            languages += document.language.orEmpty()

            project(ttml).forEach { line ->
                totalLines++
                totalWords += line.words.size
                if (line.background != null) linesWithBackground++
                if (line.transliterationWords != null) linesWithTransliterationWords++
            }

            projectAsSubtitle(ttml).forEach { line ->
                if (line.translationWords != null) linesWithTranslationWords++
            }
        }

        val perSample = samples.map { (name, ttml) ->
            val count = projectAsSubtitle(ttml).count { it.translationWords != null }
            name to count
        }.filter { it.second > 0 }

        val stat = buildString {
            appendLine("样本数=${samples.size}")
            appendLine("有译文的样本=$withTranslation")
            appendLine("有音译的样本=$withTransliteration")
            appendLine("总行数=$totalLines")
            appendLine("总词数=$totalWords")
            appendLine("有背景的行=$linesWithBackground")
            appendLine("有逐词翻译的行=$linesWithTranslationWords")
            appendLine("有逐词音译的行=$linesWithTransliterationWords")
            appendLine("原文语言=$languages")
            appendLine("--- 有逐词翻译的样本 ---")
            perSample.forEach { (name, count) -> appendLine("$name = $count 行") }
        }
        File("build/corpus-stat.txt").writeText(stat)

        assertTrue(withTranslation > 0)
        assertTrue(languages.size >= 2)
    }
}
