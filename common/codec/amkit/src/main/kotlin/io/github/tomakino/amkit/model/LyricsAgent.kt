/*
 * Copyright 2026 Tomakino
 * Licensed under the Apache License, Version 2.0
 * http://www.apache.org/licenses/LICENSE-2.0
 */

package io.github.tomakino.amkit.model

/**
 * 演唱者姓名（`ttm:name`）。
 *
 * Apple 的多演唱者曲目会给出姓名，并标注类型：
 * ```xml
 * <ttm:agent type="person" xml:id="v1">
 *   <ttm:name type="full">U.S.A. for Africa</ttm:name>
 * </ttm:agent>
 * ```
 * 同一个 agent 可以有多个姓名（`full` / `family` / `given` / `alias`），
 * 因此 [type] 必须保留 —— 只存文本会让「全名」和「姓氏」无从区分。
 */
data class LyricsName(
    /** 姓名文本。 */
    val text: String,

    /** `type`：`full` / `family` / `given` / `alias` 等。 */
    val type: String? = null,
)

/** 演唱者 / 角色，对应 TTML 的 `ttm:agent`。 */
data class LyricsAgent(
    /** `xml:id`，被 `<p ttm:agent="...">` 引用。 */
    val id: String,
    /** `ttm:agent` 的 `type`，如 `person`、`group`。多演唱者曲目里两者会同时出现。 */
    val type: String? = null,
    /** `ttm:name` 列表（可能含 `full`、`family` 等多种形式）。 */
    val names: List<LyricsName> = emptyList(),
) {
    /** 取第一个姓名文本，便于展示；没有姓名时返回 `null`。 */
    fun firstDisplayName(): String? = names.firstOrNull()?.text
}
