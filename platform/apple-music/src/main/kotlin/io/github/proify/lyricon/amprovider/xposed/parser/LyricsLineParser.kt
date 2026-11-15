/*
 * Copyright 2026 Proify, Tomakino
 * Licensed under the Apache License, Version 2.0
 * http://www.apache.org/licenses/LICENSE-2.0
 */

package io.github.proify.lyricon.amprovider.xposed.parser

import io.github.proify.lyricon.amprovider.xposed.model.LyricLine

object LyricsLineParser {

    /** 解析原生 Line Vector 为 [LyricLine] 列表。 */
    fun parse(any: Any): MutableList<LyricLine> = parseNativeVector(any) { parseLine(it) }

    private fun parseLine(native: Any): LyricLine = LyricLine().apply {
        LyricsTimingParser.parse(this, native)

        // 主要歌词
        htmlLineText = callMethod(native, "getHtmlLineText") as? String
        callMethod(native, "getWords")?.let { words = LyricsWordParser.parse(it) }
        htmlTranslationLineText = callMethod(native, "getHtmlTranslationLineText") as? String

        // 副歌词
        callMethod(native, "getBackgroundWords", false)?.let {
            backgroundWords = LyricsWordParser.parse(it)
        }
        htmlBackgroundVocalsLineText =
            callMethod(native, "getHtmlBackgroundVocalsLineText") as? String
        htmlTranslatedBackgroundVocalsLineText =
            callMethod(native, "getHtmlTranslatedBackgroundVocalsLineText") as? String

        // 音译
        htmlPronunciationLineText = callMethod(native, "getHtmlPronunciationLineText") as? String
        htmlPronunciationBackgroundVocalsLineText =
            callMethod(native, "getHtmlPronunciationBackgroundVocalsLineText") as? String
    }
}
