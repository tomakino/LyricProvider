/*
 * Copyright 2026 Proify, Tomakino
 * Licensed under the Apache License, Version 2.0
 * http://www.apache.org/licenses/LICENSE-2.0
 */

package io.github.proify.qrckit.parser.model

import io.github.proify.extensions.findClosest
import io.github.proify.lrckit.LrcDocument
import io.github.proify.lrckit.LrcParser
import io.github.proify.lyricon.lyric.model.RichLyricLine
import io.github.proify.qrckit.parser.QrcParser
import kotlinx.serialization.Serializable

/**
 * QRC 响应解密后的歌词文本。
 * @property lyricsRaw 主歌词文本。
 * @property translationRaw 翻译文本。
 * @property romaRaw 罗马音文本。
 */
@Serializable
data class ParsedLyric(
    val lyricsRaw: String? = null,
    val translationRaw: String? = null,
    val romaRaw: String? = null
) {
    /** 合并翻译与罗马音后的富歌词行；翻译为 `//` 时置空。 */
    val richLyricLines: List<RichLyricLine> by lazy {
        val raw = lyricsRaw?.takeIf { it.isNotBlank() } ?: return@lazy emptyList()

        val transIndex = translationDocument.lines
        val romaIndex = romaDocument.lines

        val sourceLines = runCatching { QrcParser.parseXml(raw).firstOrNull()?.lines }
            .getOrNull()
            ?.takeIf { it.isNotEmpty() }
            ?: lyricsDocument.lines

        sourceLines.map { line ->
            RichLyricLine(
                begin = line.begin,
                end = line.end,
                duration = line.duration,
                text = line.text,
                translation = transIndex.findClosest(line.begin, 50)
                    ?.text.takeUnless {
                        it?.trim() == "//"
                    },
                roma = romaIndex.findClosest(line.begin, 50)?.text,
                words = line.words
            )
        }
    }

    /** 主歌词的 LRC 回退文档。 */
    private val lyricsDocument: LrcDocument by lazy {
        val raw = lyricsRaw
        if (raw.isNullOrBlank()) LrcDocument() else LrcParser.parse(raw)
    }

    /** 翻译文本的 LRC 文档。 */
    private val translationDocument: LrcDocument by lazy {
        val raw = translationRaw
        if (raw.isNullOrBlank()) LrcDocument() else LrcParser.parse(raw)
    }

    /** 罗马音的 LRC 文档。 */
    private val romaDocument: LrcDocument by lazy {
        val raw = romaRaw
        if (raw.isNullOrBlank()) LrcDocument() else LrcParser.parse(raw)
    }
}