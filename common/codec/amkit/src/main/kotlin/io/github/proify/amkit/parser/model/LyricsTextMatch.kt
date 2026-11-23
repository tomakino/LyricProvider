/*
 * Copyright 2026 Proify, Tomakino
 * Licensed under the Apache License, Version 2.0
 * http://www.apache.org/licenses/LICENSE-2.0
 */

package io.github.proify.amkit.parser.model

/** 词序列是否都带开始时间；空白分隔词不参与判断。 */
internal fun LyricsText.hasCompleteTiming(): Boolean {
    val spoken = words.filter { it.text.isNotBlank() }
    return spoken.isNotEmpty() && spoken.all { it.start != null }
}

/** 按语言取一条文本。 */
internal fun List<LyricsText>.matchLanguage(
    language: String?,
    fallback: Boolean,
    preferredType: String? = null,
): LyricsText? {
    if (isEmpty()) return null
    if (language == null) return firstOrNull()

    val candidates = candidatesFor(language, fallback)
    if (candidates.isEmpty()) return null

    if (preferredType != null) {
        candidates.firstOrNull { it.type.equals(preferredType, ignoreCase = true) }
            ?.let { return it }
    }
    return candidates.first()
}

private fun List<LyricsText>.candidatesFor(language: String, fallback: Boolean): List<LyricsText> {
    val exact = filter { it.language.equals(language, ignoreCase = true) }
    if (exact.isNotEmpty() || !fallback) return exact

    val parts = language.split('-')
    for (size in parts.size - 1 downTo 1) {
        val candidate = parts.take(size).joinToString("-")
        val match = filter { it.language.equals(candidate, ignoreCase = true) }
        if (match.isNotEmpty()) return match
    }
    return emptyList()
}
