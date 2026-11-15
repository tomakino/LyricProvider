/*
 * Copyright 2026 Tomakino
 * Licensed under the Apache License, Version 2.0
 * http://www.apache.org/licenses/LICENSE-2.0
 */

package io.github.tomakino.amkit.parser

import io.github.tomakino.amkit.model.LyricsText

/**
 * Head 侧 Sidecar 翻译 / 音译条目 —— **解析中间产物**。
 *
 * 对应 `iTunesMetadata` 里的：
 * ```xml
 * <translations>
 *   <translation xml:lang="zh-Hans" type="replacement">
 *     <text for="L1"><span begin="12.990" end="13.757">錯過</span>…</text>
 *   </translation>
 * </translations>
 * ```
 *
 * 之所以放在 `parser` 包而非 `model` 包：它只在把条目合并回对应行的过程中存在，
 * 解析完成后不会出现在 [io.github.tomakino.amkit.model.LyricsDocument] 里
 * （避免与行内的同语言条目重复）。放进公共模型包会让 `model` 里混入一个
 * `internal` 类型，语义上也不对 —— 它是实现细节，不是领域模型。
 */
internal data class LyricsTranslationSidecar(
    /**
     * 行 key（`<text for="L1">` 的 `for`）到译文条目的映射。
     *
     * 值是完整的 [LyricsText] 而不是 `String`：Apple 的 `<text for>` 内部
     * 与 `<p>` 同构（带时间戳的逐词 `<span>` + `x-bg` 背景组），
     * 用字符串装不下逐字译文的时间对齐信息。
     */
    val entries: Map<String, LyricsText> = emptyMap(),
)
