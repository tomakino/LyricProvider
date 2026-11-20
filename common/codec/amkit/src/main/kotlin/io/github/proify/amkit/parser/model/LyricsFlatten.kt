/*
 * Copyright 2026 Proify, Tomakino
 * Licensed under the Apache License, Version 2.0
 * http://www.apache.org/licenses/LICENSE-2.0
 */

package io.github.proify.amkit.parser.model

import kotlin.time.Duration

/** 扁平化后的一行歌词。 */
data class FlatLyricsLine(
    val key: String?,
    val agentId: String?,
    val songPart: String?,
    val start: Duration?,
    val end: Duration?,
    val text: String,

    /** 背景人声文本。 */
    val background: String?,
    val translations: List<LyricsText>,
    val transliterations: List<LyricsText>,
)

/** 把文档摊平为行列表。 */
fun LyricsDocument.flatten(): List<FlatLyricsLine> =
    sections.flatMap { section ->
        section.lines.map { line ->
            FlatLyricsLine(
                key = line.key,
                agentId = line.agentId,
                songPart = section.songPart,
                start = line.start,
                end = line.end,
                text = line.text,
                background = line.backgroundText.takeIf { it.isNotEmpty() },
                translations = line.translations,
                transliterations = line.transliterations,
            )
        }
    }
