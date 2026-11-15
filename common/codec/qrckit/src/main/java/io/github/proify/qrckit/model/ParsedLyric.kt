/*
 * Copyright 2026 Proify, Tomakino
 * Licensed under the Apache License, Version 2.0
 * http://www.apache.org/licenses/LICENSE-2.0
 */

package io.github.proify.qrckit.model

import io.github.proify.extensions.findClosest
import io.github.proify.lrckit.LrcDocument
import io.github.proify.lrckit.LrcParser
import io.github.proify.lyricon.lyric.model.RichLyricLine
import io.github.proify.qrckit.QrcParser
import kotlinx.serialization.Serializable

@Serializable
data class ParsedLyric(
    val lyricsRaw: String? = null,
    val translationRaw: String? = null,
    val romaRaw: String? = null
) {
    val richLyricLines: List<RichLyricLine> by lazy {
        val raw = lyricsRaw?.takeIf { it.isNotBlank() } ?: return@lazy emptyList()

        val transIndex = translationData.lines
        val romaIndex = lrcRomaData.lines

        val sourceLines = runCatching { QrcParser.parseXML(raw).firstOrNull()?.lines }
            .getOrNull()
            ?.takeIf { it.isNotEmpty() }
            ?: lrcDocument.lines

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

    private val lrcDocument: LrcDocument by lazy {
        val raw = lyricsRaw
        if (raw.isNullOrBlank()) LrcDocument() else LrcParser.parse(raw)
    }

    private val translationData: LrcDocument by lazy {
        val raw = translationRaw
        if (raw.isNullOrBlank()) LrcDocument() else LrcParser.parse(raw)
    }

    private val lrcRomaData: LrcDocument by lazy {
        val raw = romaRaw
        if (raw.isNullOrBlank()) LrcDocument() else LrcParser.parse(raw)
    }
}