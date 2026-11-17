/*
 * Copyright 2026 Tomakino
 * Licensed under the Apache License, Version 2.0
 * http://www.apache.org/licenses/LICENSE-2.0
 */

package io.github.tomakino.amkit.parser.model

import kotlin.time.Duration

/** 词。 */
data class LyricsWord(
    val text: String,

    /** 开始时间。 */
    val start: Duration? = null,

    /** 结束时间。 */
    val end: Duration? = null,
)

/** 一行歌词。 */
data class LyricsLine(
    /** 行标识。 */
    val key: String? = null,

    /** 演唱者 id。 */
    val agentId: String? = null,

    val start: Duration? = null,
    val end: Duration? = null,

    /** 主词。 */
    val words: List<LyricsWord> = emptyList(),

    /** 背景人声词。 */
    val background: List<LyricsWord> = emptyList(),

    /** 译文列表。 */
    val translations: List<LyricsText> = emptyList(),

    /** 音译列表。 */
    val transliterations: List<LyricsText> = emptyList(),
) {
    /** 主文本。 */
    val text: String get() = words.joinToString(separator = "") { it.text }

    /** 背景人声文本。 */
    val backgroundText: String get() = background.joinToString(separator = "") { it.text }

    /**
     * 取指定语言的译文。
     *
     * @param language 语言标签
     * @param fallback 是否允许逐级回退
     * @return 首条匹配的译文
     */
    fun translationFor(language: String, fallback: Boolean = false): LyricsText? =
        translations.matchLanguage(language, fallback)

    /**
     * 取指定语言的音译。
     *
     * @param language 语言标签
     * @param fallback 是否允许逐级回退
     * @return 首条匹配的音译
     */
    fun transliterationFor(language: String, fallback: Boolean = false): LyricsText? =
        transliterations.matchLanguage(language, fallback)

    /** 指定语言的译文是否带完整逐字时间。 */
    fun hasTimedTranslation(language: String, fallback: Boolean = false): Boolean =
        translationFor(language, fallback)?.hasCompleteTiming() == true
}
