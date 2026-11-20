/*
 * Copyright 2026 Proify, Tomakino
 * Licensed under the Apache License, Version 2.0
 * http://www.apache.org/licenses/LICENSE-2.0
 */

package io.github.proify.amkit.parser

import io.github.proify.amkit.parser.model.DisplayLine
import io.github.proify.amkit.parser.model.LyricsDocument
import io.github.proify.amkit.parser.model.LyricsLine
import io.github.proify.amkit.parser.model.LyricsText
import io.github.proify.amkit.parser.model.LyricsWord
import io.github.proify.amkit.parser.model.hasCompleteTiming
import io.github.proify.amkit.parser.model.matchLanguage

/** 按语言生成显示视图。 */
fun LyricsDocument.displayLines(language: String): List<DisplayLine> =
    displayLines(DisplayOptions(language = language))

/** 按配置生成显示视图。 */
fun LyricsDocument.displayLines(options: DisplayOptions = DisplayOptions()): List<DisplayLine> =
    lines.map { it.displayIn(options) }

/** 把一行歌词转换成本地化显示视图。 */
internal fun LyricsLine.displayIn(options: DisplayOptions): DisplayLine {
    val localized = translations.matchLanguage(
        language = options.language,
        fallback = options.languageFallback,
        preferredType = options.translationType,
    )
    val romanized = options.transliterationLanguage
        ?.let { language -> transliterations.matchLanguage(language, options.languageFallback) }

    val replacement = options.applyReplacement &&
        localized?.type.equals(REPLACEMENT_TYPE, ignoreCase = true)
    val timed = localized?.hasCompleteTiming() == true
    val romanizedText = romanized?.text?.takeIf { it.isNotBlank() }
    val romanizedWords = romanized?.words?.takeIf { romanized.hasCompleteTiming() }

    return when {
        replacement && localized != null -> {
            val displayWords = when {
                timed -> localized.words
                options.alignUntimedTranslation -> localized.alignedTo(words, text)
                else -> words
            }
            DisplayLine(
                text = localized.text,
                words = displayWords,
                transliteration = romanizedText,
                transliterationWords = romanizedWords,
                backgroundWords = background,
                background = backgroundText.takeIf { it.isNotBlank() },
            )
        }

        localized != null -> DisplayLine(
            text = text,
            words = words,
            translation = localized.text.takeIf { it.isNotBlank() },
            translationWords = localized.words.takeIf { timed },
            transliteration = romanizedText,
            transliterationWords = romanizedWords,
            backgroundWords = background,
            background = backgroundText.takeIf { it.isNotBlank() },
        )

        else -> DisplayLine(
            text = text,
            words = words,
            transliteration = romanizedText,
            transliterationWords = romanizedWords,
            backgroundWords = background,
            background = backgroundText.takeIf { it.isNotBlank() },
        )
    }
}

/** `type` 的取值，Apple 用它表达「译文替换原文」。 */
private const val REPLACEMENT_TYPE = "replacement"

/** 按字符位置把译文映射到原文的词上。 */
internal fun LyricsText.alignedTo(mainWords: List<LyricsWord>, mainText: String): List<LyricsWord> {
    val compact = text.filterNot { it.isWhitespace() }
    val mainCompact = mainText.filterNot { it.isWhitespace() }
    if (compact.isEmpty() || compact.length != mainCompact.length) return mainWords

    var offset = 0
    return mainWords.map { word ->
        if (word.text.isBlank()) return@map word

        val builder = StringBuilder(word.text.length)
        var consumed = true
        for (char in word.text) {
            if (char.isWhitespace()) {
                builder.append(char)
            } else {
                if (offset >= compact.length) {
                    consumed = false
                    break
                }
                builder.append(compact[offset])
                offset++
            }
        }
        if (consumed) word.copy(text = builder.toString()) else word
    }
}
