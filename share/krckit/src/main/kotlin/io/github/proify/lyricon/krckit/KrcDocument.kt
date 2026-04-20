/*
 * Copyright 2026 Proify, Tomakino
 * Licensed under the Apache License, Version 2.0
 * http://www.apache.org/licenses/LICENSE-2.0
 */

@file:Suppress("unused")

package io.github.proify.lyricon.krckit

import io.github.proify.lyricon.krckit.language.LanguageParser
import io.github.proify.lyricon.lyric.model.LyricLine
import io.github.proify.lyricon.lyric.model.RichLyricLine
import kotlinx.serialization.Serializable
import kotlin.io.encoding.Base64

@Serializable
data class KrcDocument(
    val metadata: Map<String, String> = emptyMap(),
    val lines: List<LyricLine> = emptyList()
) {

    val richLyricLines by lazy {
        val languageInfo = language?.let { LanguageParser.parse(it) }
        val romas = languageInfo?.getRoma()?.flatten() ?: emptyList()
        val translates = languageInfo?.getTranslate()?.flatten() ?: emptyList()

        if (lines.size != romas.size) {
            println("romas size not match lines size, sourceSize: ${lines.size}, romaSize: ${romas.size}")
        }
        if (lines.size != translates.size) {
            println("translates size not match lines size, sourceSize: ${lines.size}, translateSize: ${translates.size}")
        }

        lines.mapIndexed { index, line ->
            RichLyricLine(
                begin = line.begin,
                end = line.end,
                duration = line.duration,
                text = line.text,
                words = line.words,
                roma = if (lines.size == romas.size) romas.getOrNull(index) else null,
                translation = if (lines.size == translates.size) translates.getOrNull(index) else null
            )
        }
    }

    val language by lazy {
        val key = metadata.keys.find {
            it.equals("language", ignoreCase = true)
        }
        if (key.isNullOrBlank()) return@lazy null
        val value = metadata[key]
        if (value.isNullOrBlank()) return@lazy null
        val decode = runCatching {
            val decoded = Base64.decode(value)
            String(decoded, Charsets.UTF_8)
        }.onFailure {
            it.printStackTrace()
        }.getOrNull() ?: value

        decode
    }

    /**
     * 应用全局时间偏移
     * @param offsetMs 偏移毫秒数（支持正负）
     * @return 偏移后的新文档
     */
    fun applyOffset(offsetMs: Long): KrcDocument {
        if (offsetMs == 0L) return this

        val newLines = lines.map { line ->
            val newBegin = (line.begin + offsetMs).coerceAtLeast(0L)
            val newEnd = newBegin + line.duration

            line.copy(
                begin = newBegin,
                end = newEnd,
                duration = line.duration
            )
        }
        return copy(lines = newLines)
    }
}