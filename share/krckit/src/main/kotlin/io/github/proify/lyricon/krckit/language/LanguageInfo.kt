/*
 * Copyright 2026 Proify, Tomakino
 * Licensed under the Apache License, Version 2.0
 * http://www.apache.org/licenses/LICENSE-2.0
 */

package io.github.proify.lyricon.krckit.language

import kotlinx.serialization.Serializable

@Serializable
data class LanguageInfo(
    val content: List<LyricSection>,
    val version: Int
) {
    fun getRoma() = content.find { it.type == LyricSection.TYPE_ROMA }
    fun getTranslate() = content.find { it.type == LyricSection.TYPE_TRANSLATE }
}