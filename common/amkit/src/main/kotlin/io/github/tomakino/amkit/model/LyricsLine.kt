/*
 * Copyright 2026 Tomakino
 * Licensed under the Apache License, Version 2.0
 * http://www.apache.org/licenses/LICENSE-2.0
 */

package io.github.tomakino.amkit.model

import kotlin.time.Duration

/**
 * 词（逐字单元）。
 *
 * `itunes:timing="Line"` 的文档里没有词级时间戳，此时解析器会**把行时间补到词上**
 * （见 `withLineTimeWhenTimeless`），所以 [start] / [end] 依然可用；
 * 只有连行时间都没有时（`timing="None"` 或属性未标注）才为 `null`。
 */
data class LyricsWord(
    val text: String,
    val start: Duration? = null,
    val end: Duration? = null,
)

/**
 * 一行歌词（`<p>`）。
 *
 * 关于翻译/音译的归属：格式上 `ttm:role="x-translation"` 与 `x-roman` 既可以出现在行内，
 * 也可以出现在背景人声组（`x-bg`）内部 —— 不过实测 Apple 的 385 首样本里它们**只出现在
 * head 侧**。为了模型简单，两者都会被收集到本行的
 * [translations] / [transliterations] 中，因为它们语义上都属于这一行。
 */
data class LyricsLine(
    /** `itunes:key`，行唯一标识（通常形如 `L1`）。 */
    val key: String? = null,

    /** `ttm:agent`，指向文档 [LyricsAgent] 的 id。 */
    val agentId: String? = null,

    val start: Duration? = null,
    val end: Duration? = null,

    /** 主唱词序列。 */
    val words: List<LyricsWord> = emptyList(),

    /** 背景人声词序列，对应 `ttm:role="x-bg"`。 */
    val background: List<LyricsWord> = emptyList(),

    /** 翻译列表，可能包含多种语言。 */
    val translations: List<LyricsText> = emptyList(),

    /** 音译（罗马音）列表，可能包含多种语言。 */
    val transliterations: List<LyricsText> = emptyList(),
) {
    /** 主唱行文本。 */
    val text: String get() = words.joinToString(separator = "") { it.text }

    /** 背景人声文本。 */
    val backgroundText: String get() = background.joinToString(separator = "") { it.text }
}
