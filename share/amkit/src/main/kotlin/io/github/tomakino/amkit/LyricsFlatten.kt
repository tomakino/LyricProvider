/*
 * Copyright 2026 Tomakino
 * Licensed under the Apache License, Version 2.0
 * http://www.apache.org/licenses/LICENSE-2.0
 */

package io.github.tomakino.amkit

import io.github.tomakino.amkit.model.LyricsDocument
import io.github.tomakino.amkit.model.LyricsText
import kotlin.time.Duration

/**
 * 扁平化后的一行歌词。
 *
 * 相比树形模型，这里把段落信息上提、把背景人声降为字符串，
 * 适合「按时间顺序逐行渲染」的常见场景。
 */
data class FlatLyricsLine(
    val key: String?,
    val agentId: String?,
    /** 所属段落（`itunes:song-part`）。 */
    val songPart: String?,
    val start: Duration?,
    val end: Duration?,
    /** 主唱文本。 */
    val text: String,
    /** 背景人声文本；没有背景人声时为 `null`，便于调用方直接判空。 */
    val background: String?,
    val translations: List<LyricsText>,
    val transliterations: List<LyricsText>,
)

/**
 * 把树形文档摊平为可直接顺序渲染的行列表。
 *
 * 该操作是**有损**的：背景人声被降为字符串（逐词时间丢失），段落的
 * `start` / `end` 也不保留 —— 只有段落名被上提到 [FlatLyricsLine.songPart]。
 * 需要完整信息时请直接使用 [LyricsDocument.sections]。
 */
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
