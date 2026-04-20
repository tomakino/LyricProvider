/*
 * Copyright 2026 Proify, Tomakino
 * Licensed under the Apache License, Version 2.0
 * http://www.apache.org/licenses/LICENSE-2.0
 */

package io.github.proify.lyricon.krckit.language

import kotlinx.serialization.Serializable

/**
 * 包含语言类型和具体的歌词行
 */
@Serializable
data class LyricSection(
    val language: Int,
    /**
     * 二维列表结构：
     * 外层 List 代表“行”
     * 内层 List 代表“字”或“词块”
     */
    val lyricContent: List<List<String>>,
    val type: Int
) {
    companion object {
        const val TYPE_ROMA = 0
        const val TYPE_TRANSLATE = 1
    }

    fun flatten(): List<String> {
        return lyricContent.map {
            it.joinToString("")
        }
    }
}