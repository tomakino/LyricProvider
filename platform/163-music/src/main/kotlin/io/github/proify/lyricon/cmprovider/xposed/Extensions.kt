/*
 * Copyright 2026 Proify, Tomakino
 * Licensed under the Apache License, Version 2.0
 * http://www.apache.org/licenses/LICENSE-2.0
 */

package io.github.proify.lyricon.cmprovider.xposed

import io.github.proify.extensions.findClosest
import io.github.proify.lrckit.LrcParser
import io.github.proify.lyricon.lyric.model.LyricLine
import io.github.proify.lyricon.lyric.model.RichLyricLine
import io.github.proify.lyricon.lyric.model.Song
import io.github.proify.lyricon.yrckit.YrcParser

fun LocalLyricCache.toSong(metadataOverride: Metadata? = null): Song {
    val metadata = metadataOverride ?: MediaMetadataCache.get(musicId)
    val richLyricLines = toRichLines()

    return Song(id = musicId.toString()).apply {
        name = metadata?.title
        artist = metadata?.artist
        duration = resolveSongDuration(metadata?.duration, richLyricLines.lastOrNull()?.end)
        lyrics = richLyricLines
    }
}

fun LocalLyricCache.toRichLines(): List<RichLyricLine> {
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

private fun LocalLyricCache.parseSourceLines(): List<LyricLine>? {
    yrc?.takeIf { it.isNotBlank() }?.let {
        val lines = YrcParser.parse(it)
        if (lines.isNotEmpty()) return lines
    }

    lrc?.takeIf { it.isNotBlank() }?.let {
        val lines = LrcParser.parse(it).lines
        if (lines.isNotEmpty()) return lines
    }
    return null
}

private fun LocalLyricCache.parseSourceTranLines(): List<LyricLine>? {
    yrcTranslateLyric?.takeIf { it.isNotBlank() }?.let {
        val lines = LrcParser.parse(it).lines
        if (lines.isNotEmpty()) return lines
    }

    lrcTranslateLyric?.takeIf { it.isNotBlank() }?.let {
        val lines = LrcParser.parse(it).lines
        if (lines.isNotEmpty()) return lines
    }
    return null
}

private fun LocalLyricCache.parseRomaLines(): List<LyricLine>? {
    roma?.takeIf { it.isNotBlank() }?.let {
        val lines = LrcParser.parse(it).lines
        if (lines.isNotEmpty()) return lines
    }
    return null
}

internal fun resolveSongDuration(metadataDuration: Long?, lastLyricEnd: Long?): Long =
    metadataDuration?.takeIf { it > 0 }
        ?: lastLyricEnd?.takeIf { it > 0 }
        ?: 0L
