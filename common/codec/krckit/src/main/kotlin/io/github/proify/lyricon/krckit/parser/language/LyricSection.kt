/*
 * Copyright 2026 Proify, Tomakino
 * Licensed under the Apache License, Version 2.0
 * http://www.apache.org/licenses/LICENSE-2.0
 */

package io.github.proify.lyricon.krckit.parser.language

import kotlinx.serialization.Serializable

/**
 * KRC language 中的歌词分段。
 * @property language 语言标识。
 * @property lyricContent 按行、按词块组织的文本。
 * @property type 分段类型。
 */
@Serializable
data class LyricSection(
    val language: Int,
    val lyricContent: List<List<String>>,
    val type: Int
) {
    companion object {
        /** 罗马音分段类型。 */
        const val TYPE_ROMA = 0

        /** 翻译分段类型。 */
        const val TYPE_TRANSLATE = 1
    }

    /**
     * 将逐行词块拼接为整行文本。
     * @return 每行拼接后的文本。
     */
    fun flatten(): List<String> = lyricContent.map { it.joinToString("") }
}
