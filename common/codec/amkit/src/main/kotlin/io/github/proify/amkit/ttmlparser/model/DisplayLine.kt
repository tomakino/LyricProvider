/*
 * Copyright 2026 Proify, Tomakino
 * Licensed under the Apache License, Version 2.0
 * http://www.apache.org/licenses/LICENSE-2.0
 */

package io.github.proify.amkit.ttmlparser.model

/** 一行歌词的显示视图。 */
data class DisplayLine(
    /** 主文本。 */
    val text: String,

    /** 主词。 */
    val words: List<LyricsWord>,

    /** 对照译文。 */
    val translation: String? = null,

    /** 对照译文的逐词。 */
    val translationWords: List<LyricsWord>? = null,

    /** 音译文本。 */
    val transliteration: String? = null,

    /** 音译的逐词。 */
    val transliterationWords: List<LyricsWord>? = null,

    /** 背景人声逐词。 */
    val backgroundWords: List<LyricsWord> = emptyList(),

    /** 背景人声文本。 */
    val background: String? = null,
)
