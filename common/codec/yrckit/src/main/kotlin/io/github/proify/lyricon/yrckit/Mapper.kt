/*
 * Copyright 2026 Proify, Tomakino
 * Licensed under the Apache License, Version 2.0
 * http://www.apache.org/licenses/LICENSE-2.0
 */

package io.github.proify.lyricon.yrckit

import io.github.proify.extensions.findClosest
import io.github.proify.lrckit.LrcParser
import io.github.proify.lyricon.lyric.model.LyricLine
import io.github.proify.lyricon.lyric.model.RichLyricLine
import io.github.proify.lyricon.lyric.model.Song
import io.github.proify.lyricon.yrckit.download.response.LyricResponse

fun LyricResponse.toSong(
    name: String?,
    artist: String?
): Song {
    val richLyricLines = toRichLines()

    return Song().apply {
        this.name = name
        this.artist = artist
        duration = richLyricLines.lastOrNull()?.end ?: 0
        lyrics = richLyricLines
    }
}

fun LyricResponse.toRichLines(): List<RichLyricLine> {
    val sourceLines = parseSourceLines() ?: return emptyList()
    val translates = parseSourceTranLines()?.sortedBy { it.begin }
    val romas = parseRomaLines()

    return sourceLines.map { line ->
        val matchTranslate = translates?.findClosest(line.begin, 1000)
        val matchRoma = romas?.findClosest(line.begin, 1000)

        RichLyricLine(
            begin = line.begin,
            end = line.end,
            duration = line.duration,
            text = line.text,
            words = line.words,
            translation = matchTranslate?.text,
            roma = matchRoma?.text
        )
    }
}

private fun LyricResponse.parseSourceLines(): List<LyricLine>? {
    yrc?.lyric?.takeIf { it.isNotBlank() }?.let {
        val lines = YrcParser.parse(it)
        if (lines.isNotEmpty()) return lines
    }

    lrc?.lyric?.takeIf { it.isNotBlank() }?.let {
        val lines = LrcParser.parse(it).lines
        if (lines.isNotEmpty()) return lines
    }
    return null
}

private fun LyricResponse.parseSourceTranLines(): List<LyricLine>? {
    ytlrc?.lyric?.takeIf { it.isNotBlank() }?.let {
        val lines = LrcParser.parse(it).lines
        if (lines.isNotEmpty()) return lines
    }

    tlyric?.lyric?.takeIf { it.isNotBlank() }?.let {
        val lines = LrcParser.parse(it).lines
        if (lines.isNotEmpty()) return lines
    }
    return null
}

private fun LyricResponse.parseRomaLines(): List<LyricLine>? {
    romalrc?.lyric?.takeIf { it.isNotBlank() }?.let {
        val lines = LrcParser.parse(it).lines
        if (lines.isNotEmpty()) return lines
    }
    return null
}