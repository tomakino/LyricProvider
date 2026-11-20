/*
 * Copyright 2026 Proify, Tomakino
 * Licensed under the Apache License, Version 2.0
 * http://www.apache.org/licenses/LICENSE-2.0
 */

package io.github.proify.lyricon.krckit.parser.language

import kotlinx.serialization.Serializable

/**
 * KRC language 元数据。
 * @property content 歌词分段。
 * @property version 版本号。
 */
@Serializable
data class LanguageInfo(
    val content: List<LyricSection>,
    val version: Int
) {

    /**
     * 查找罗马音分段。
     * @return 罗马音分段；不存在返回 null。
     */
    fun findRoma(): LyricSection? = content.find { it.type == LyricSection.TYPE_ROMA }

    /**
     * 查找翻译分段。
     * @return 翻译分段；不存在返回 null。
     */
    fun findTranslate(): LyricSection? =
        content.find { it.type == LyricSection.TYPE_TRANSLATE }
}
