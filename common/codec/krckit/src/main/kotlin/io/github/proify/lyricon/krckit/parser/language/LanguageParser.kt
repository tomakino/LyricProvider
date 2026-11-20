/*
 * Copyright 2026 Proify, Tomakino
 * Licensed under the Apache License, Version 2.0
 * http://www.apache.org/licenses/LICENSE-2.0
 */

package io.github.proify.lyricon.krckit.parser.language

import kotlinx.serialization.json.Json

/** KRC language 元数据解析器。 */
object LanguageParser {

    /** 忽略未知字段的 JSON 解析器。 */
    private val json = Json {
        ignoreUnknownKeys = true
        coerceInputValues = true
    }

    /**
     * 解析 language 元数据。
     * @param jsonString JSON 文本。
     * @return 解析结果；格式错误返回 null。
     */
    fun parse(jsonString: String): LanguageInfo? {
        return try {
            json.decodeFromString<LanguageInfo>(jsonString)
        } catch (e: Exception) {
            e.printStackTrace()
            null
        }
    }
}
