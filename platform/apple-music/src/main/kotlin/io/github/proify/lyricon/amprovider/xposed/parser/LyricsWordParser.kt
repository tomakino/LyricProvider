/*
 * Copyright 2026 Proify, Tomakino
 * Licensed under the Apache License, Version 2.0
 * http://www.apache.org/licenses/LICENSE-2.0
 */

package io.github.proify.lyricon.amprovider.xposed.parser

import io.github.proify.lyricon.amprovider.xposed.model.LyricWord

object LyricsWordParser {

    /** 解析原生 Word Vector 为 [LyricWord] 列表。 */
    fun parse(any: Any): MutableList<LyricWord> = parseNativeVector(any) { parseWord(it) }

    private fun parseWord(native: Any): LyricWord = LyricWord().apply {
        LyricsTimingParser.parse(this, native)
        text = callMethod(native, "getHtmlLineText") as? String
    }
}
