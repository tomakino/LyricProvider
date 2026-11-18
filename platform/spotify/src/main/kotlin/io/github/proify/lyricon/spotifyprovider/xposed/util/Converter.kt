/*
 * Copyright 2026 Proify, Tomakino
 * Licensed under the Apache License, Version 2.0
 * http://www.apache.org/licenses/LICENSE-2.0
 */

@file:OptIn(ExperimentalSerializationApi::class)

package io.github.proify.lyricon.spotifyprovider.xposed.util

import io.github.proify.lyricon.lyric.model.LyricWord
import io.github.proify.lyricon.lyric.model.RichLyricLine
import io.github.proify.lyricon.lyric.model.Song
import io.github.proify.lyricon.spotifyprovider.xposed.TrackMetadata
import io.github.proify.lyricon.spotifyprovider.xposed.api.proto.ColorLyricsResponse
import io.github.proify.lyricon.spotifyprovider.xposed.api.proto.LyricsLine
import io.github.proify.lyricon.spotifyprovider.xposed.api.proto.LyricsResponse
import kotlinx.serialization.ExperimentalSerializationApi
import kotlinx.serialization.decodeFromByteArray
import kotlinx.serialization.protobuf.ProtoBuf

/**
 * 将 Spotify color-lyrics protobuf 响应转换为歌曲。
 *
 * @param metadata 当前曲目元数据，提供 id、标题、艺术家与时长
 * @return 转换后的歌曲；响应为空或解析失败返回 null
 */
fun ByteArray.toSongOrNull(metadata: TrackMetadata): Song? {
    if (isEmpty()) return null
    return runCatching { ProtoBuf.decodeFromByteArray<ColorLyricsResponse>(this) }
        .getOrNull()
        ?.lyrics
        ?.toSong(metadata)
}

/**
 * protobuf 歌词主体转歌曲。
 */
fun LyricsResponse.toSong(metadata: TrackMetadata): Song = Song(
    id = metadata.id,
    name = metadata.title,
    artist = metadata.artist,
    duration = metadata.duration,
    lyrics = toLyrics()
)

/**
 * protobuf 歌词主体转富歌词行列表。
 *
 * 行级结束时间在协议中缺省，按下一行起始时间兜底，最后一行 +5000ms；
 * 逐字数据有效的行携带 [LyricWord] 列表，否则回退为纯行级歌词。
 */
fun LyricsResponse.toLyrics(): List<RichLyricLine> =
    lines.mapIndexedNotNull { index, line ->
        val text = line.words
        if (text.isBlank()) return@mapIndexedNotNull null
        val endTimeMs = lines.getOrNull(index + 1)?.startTimeMs ?: (line.startTimeMs + 5000L)
        RichLyricLine(
            begin = line.startTimeMs,
            end = endTimeMs,
            duration = (endTimeMs - line.startTimeMs).coerceAtLeast(0L),
            text = text,
            words = line.toLyricWords(endTimeMs)
        )
    }

/**
 * 按 [LyricsLine.syllables] 的字符数累计游标切片整行文本，生成逐字 [LyricWord]。
 *
 * 任一音节计数异常或累计长度与整行文本不一致时返回 null，
 * 由调用方回退为纯行级歌词，避免切片错位。
 */
private fun LyricsLine.toLyricWords(lineEnd: Long): List<LyricWord>? {
    val text = words
    if (syllables.isEmpty()) return null

    val result = ArrayList<LyricWord>(syllables.size)
    var offset = 0
    syllables.forEachIndexed { index, syllable ->
        if (syllable.numChars < 0 || offset + syllable.numChars > text.length) return null
        val endOffset = offset + syllable.numChars
        val end = syllables.getOrNull(index + 1)?.startTimeMs ?: lineEnd
        result += LyricWord(
            begin = syllable.startTimeMs,
            end = end,
            duration = (end - syllable.startTimeMs).coerceAtLeast(0L),
            text = text.substring(offset, endOffset)
        )
        offset = endOffset
    }
    return result.takeIf { offset == text.length }
}
