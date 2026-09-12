/*
 * Copyright 2026 Tomakino
 * Licensed under the Apache License, Version 2.0
 * http://www.apache.org/licenses/LICENSE-2.0
 */

package io.github.tomakino.amkit.model

/**
 * 带语言标记的文本，用于翻译与音译。
 *
 * 结构与 [LyricsLine] **刻意保持同构**：head 侧 `<text for="L1">` 的内部组成
 * 与 `<p>` 完全一样 —— 带时间戳的 `<span>`、充当分隔符的裸文本、以及
 * `ttm:role="x-bg"` 背景组。让两者字段对齐，渲染方就能用同一套逻辑处理
 * 原文与译文，不必为译文单独写一条降级路径。
 */
data class LyricsText(
    /** 主文本（不含背景人声；背景见 [background]）。 */
    val text: String,

    /** `xml:lang`，例如 `zh-Hans-CN`、`ja-Latn`。 */
    val language: String? = null,

    /**
     * 条目 `type`。
     *
     * 实测 Apple 在 head 侧译文容器上标注用途：`replacement`（译文**替换**原文显示）
     * 与 `subtitle`（字幕式对照）。这是渲染决策信息 —— 丢掉它，调用方就还原不出
     * 客户端的显示方式。
     */
    val type: String? = null,

    /**
     * `automaticallyCreated`：条目由**机器自动生成**（而非人工转录/校对）。
     *
     * 实测 Apple 会在译文与音译容器上标这个属性（385 首样本里 28 首出现，
     * 取值恒为 `true`）。它是质量信号 —— 自动生成的音译未必可靠，
     * UI 可以据此决定是否展示或加标注。
     */
    val autoCreated: Boolean = false,

    /**
     * 逐词片段，各自带时间戳。
     *
     * Apple 的逐字译文（如日文罗马音）会与主歌词**逐词一一对齐**，
     * 因此这里保留完整结构；没有词级数据时为空列表。
     */
    val words: List<LyricsWord> = emptyList(),

    /** 背景人声片段，对应 `ttm:role="x-bg"`。 */
    val background: List<LyricsWord> = emptyList(),
) {
    /** 背景人声文本。 */
    val backgroundText: String get() = background.joinToString(separator = "") { it.text }
}
