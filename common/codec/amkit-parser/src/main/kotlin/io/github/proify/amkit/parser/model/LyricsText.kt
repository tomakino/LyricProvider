/*
 * Copyright 2026 Proify, Tomakino
 * Licensed under the Apache License, Version 2.0
 * http://www.apache.org/licenses/LICENSE-2.0
 */

@file:Suppress("unused")

package io.github.proify.amkit.parser.model

/** 带语言标记的文本。 */
data class LyricsText(
    val text: String,

    /** 语言标签。 */
    val language: String? = null,

    /** 用途标记：`replacement` 或 `subtitle`。 */
    val type: String? = null,

    /** 机器生成。 */
    val autoCreated: Boolean = false,

    /** 逐词片段。 */
    val words: List<LyricsWord> = emptyList(),

    /** 背景人声词。 */
    val background: List<LyricsWord> = emptyList(),
) {
    /** 背景人声文本。 */
    val backgroundText: String get() = background.joinToString(separator = "") { it.text }
}
