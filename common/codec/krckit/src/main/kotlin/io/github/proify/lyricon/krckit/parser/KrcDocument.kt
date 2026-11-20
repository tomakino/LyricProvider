/*
 * Copyright 2026 Proify, Tomakino
 * Licensed under the Apache License, Version 2.0
 * http://www.apache.org/licenses/LICENSE-2.0
 */

@file:Suppress("unused")

package io.github.proify.lyricon.krckit.parser

import io.github.proify.lyricon.krckit.parser.language.LanguageParser
import io.github.proify.lyricon.lyric.model.LyricLine
import io.github.proify.lyricon.lyric.model.RichLyricLine
import kotlinx.serialization.Serializable
import kotlin.io.encoding.Base64

/**
 * KRC 文档。
 * @property metadata 元数据标签，键保留原始大小写。
 * @property lines 按开始时间排序的歌词行。
 */
@Serializable
data class KrcDocument(
    val metadata: Map<String, String> = emptyMap(),
    val lines: List<LyricLine> = emptyList()
) {

    /**
     * 与翻译、罗马音对齐后的富歌词行。
     * 对应轨道行数与歌词行数不一致时，该轨道字段为 null。
     */
    val richLyricLines: List<RichLyricLine> by lazy {
        val languageInfo = languageJson?.let { LanguageParser.parse(it) }
        val romas = languageInfo?.findRoma()?.flatten() ?: emptyList()
        val translates = languageInfo?.findTranslate()?.flatten() ?: emptyList()

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

    /**
     * Base64 解码后的 language 元数据。
     * 缺失时返回 null；解码失败时保留原始值。
     */
    val languageJson: String? by lazy {
        val key = metadata.keys.find { it.equals("language", ignoreCase = true) }
            ?: return@lazy null
        val value = metadata[key]?.takeIf { it.isNotBlank() } ?: return@lazy null
        decodeBase64(value) ?: value
    }

    /**
     * 应用全局时间偏移。
     * @param offsetMs 偏移毫秒数，支持负值。
     * @return 平移后的新文档，行开始时间不小于 0。
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

    /**
     * 解码 Base64 文本。
     * @param value Base64 字符串。
     * @return 解码后的文本；失败返回 null。
     */
    private fun decodeBase64(value: String): String? = runCatching {
        String(Base64.decode(value), Charsets.UTF_8)
    }.getOrNull()
}
