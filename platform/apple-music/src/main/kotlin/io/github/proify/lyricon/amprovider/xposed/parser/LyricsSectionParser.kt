/*
 * Copyright 2026 Proify, Tomakino
 * Licensed under the Apache License, Version 2.0
 * http://www.apache.org/licenses/LICENSE-2.0
 */

package io.github.proify.lyricon.amprovider.xposed.parser

import io.github.proify.lyricon.amprovider.xposed.model.LyricLine
import io.github.proify.lyricon.amprovider.xposed.model.LyricSection

object LyricsSectionParser {

    /** 解析原生 Section Vector 为 [LyricSection] 列表。 */
    fun parse(any: Any): MutableList<LyricSection> = parseNativeVector(any) { parseSection(it) }

    private fun parseSection(native: Any): LyricSection = LyricSection().apply {
        LyricsTimingParser.parse(this, native)
        callMethod(native, "getLines")?.let { lines = LyricsLineParser.parse(it) }
    }
}

/** 将（多个 Section 中的）所有歌词行拍平为一个列表。 */
fun MutableList<LyricSection>.mergeLyrics(): MutableList<LyricLine> =
    flatMapTo(mutableListOf()) { it.lines }
