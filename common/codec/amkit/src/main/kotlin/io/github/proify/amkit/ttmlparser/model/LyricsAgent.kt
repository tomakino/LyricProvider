/*
 * Copyright 2026 Proify, Tomakino
 * Licensed under the Apache License, Version 2.0
 * http://www.apache.org/licenses/LICENSE-2.0
 */

package io.github.proify.amkit.ttmlparser.model

/** 姓名。 */
data class LyricsName(
    val text: String,

    /** 姓名类型。 */
    val type: String? = null,
)

/** 演唱者。 */
data class LyricsAgent(
    /** 标识。 */
    val id: String,

    /** 类型。 */
    val type: String? = null,

    val names: List<LyricsName> = emptyList(),
) {
    /** 首个姓名。 */
    fun firstDisplayName(): String? = names.firstOrNull()?.text
}
