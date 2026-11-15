/*
 * Copyright 2026 Proify, Tomakino
 * Licensed under the Apache License, Version 2.0
 * http://www.apache.org/licenses/LICENSE-2.0
 */

package io.github.proify.lyricon.spotifyprovider.xposed.util

import io.github.proify.lyricon.lyric.model.LyricWord
import io.github.proify.lyricon.lyric.model.RichLyricLine
import io.github.proify.lyricon.lyric.model.Song
import io.github.proify.lyricon.spotifyprovider.xposed.api.SpotifyApi.jsonParser
import io.github.proify.lyricon.spotifyprovider.xposed.api.proto.ProtoLyricLine
import io.github.proify.lyricon.spotifyprovider.xposed.api.proto.ProtoLyricResponse
import io.github.proify.lyricon.spotifyprovider.xposed.api.proto.ProtoLyricsData
import io.github.proify.lyricon.spotifyprovider.xposed.api.proto.ProtoSyllable
import io.github.proify.lyricon.spotifyprovider.xposed.api.response.LyricResponse
import io.github.proify.lyricon.spotifyprovider.xposed.api.response.LyricsData
import io.github.proify.lyricon.spotifyprovider.xposed.cache.MetadataCache
import io.github.proify.lyricon.spotifyprovider.xposed.parser.SpotifyProtoParser

/**
 * 将 Spotify 原始响应字节转换为歌曲。
 *
 * 响应可能是 protobuf（逐字数据所在）或 JSON（服务端降级），按首字节自动分流：
 * - 首字节 `{`：走原 JSON 序列化路径（行级歌词）；
 * - 其余：走 protobuf 解析路径（行级 + 逐字歌词）。
 *
 * @return 转换后的歌曲；响应为空或解析失败返回 null
 */
fun ByteArray.toSongOrNull(id: String): Song? {
    if (isEmpty()) return null
    return if (isJsonBody()) {
        runCatching { jsonParser.decodeFromString<LyricResponse>(toString(Charsets.UTF_8)) }
            .getOrNull()
            ?.toSong(id)
    } else {
        SpotifyProtoParser.parse(this)?.toSong(id)
    }
}

private fun ByteArray.isJsonBody(): Boolean = first() == '{'.code.toByte()

/**
 * JSON 响应转歌曲（原有行级实现）。
 */
fun LyricResponse.toSong(id: String): Song {
    val metadata = MetadataCache.get(id)
    val song = Song()
    song.id = id
    song.name = metadata?.title
    song.artist = metadata?.artist
    song.duration = (metadata?.duration ?: 0).run {
        if (this <= 0L) Long.MAX_VALUE else this
    }
    song.lyrics = lyrics.toLyrics()
    return song
}

fun LyricsData.toLyrics(): List<RichLyricLine> {
    val lyrics = mutableListOf<RichLyricLine>()
    lines.mapIndexed { index, line ->
        if (line.endTimeMs == 0L) {
            val nextLine = lines.getOrNull(index + 1)
            line.copy(endTimeMs = nextLine?.startTimeMs ?: (line.startTimeMs + 5000))
        } else line
    }.forEach { line ->
        if (line.words.isNullOrBlank()) {
            return@forEach
        }
        lyrics += RichLyricLine(
            begin = line.startTimeMs,
            end = line.endTimeMs,
            duration = line.endTimeMs - line.startTimeMs,
            text = line.words,
            translation = line.transliteratedWords
        )
    }

    return lyrics
}

/**
 * protobuf 响应转歌曲。
 */
fun ProtoLyricResponse.toSong(id: String): Song {
    val metadata = MetadataCache.get(id)
    return Song(
        id = id,
        name = metadata?.title,
        artist = metadata?.artist,
        duration = (metadata?.duration ?: 0L).let { if (it <= 0L) Long.MAX_VALUE else it },
        lyrics = lyrics?.toLyrics()
    )
}

/**
 * protobuf 歌词主体转富歌词行列表。
 */
fun ProtoLyricsData.toLyrics(): List<RichLyricLine> = lines.toRichLyricLines()

/**
 * protobuf 歌词行转富歌词行。
 *
 * 行级 [endTimeMs] 在 protobuf 中通常缺省，按下一行起始时间兜底，最后一行 +5000ms；
 * 逐字数据有效的行携带 [LyricWord] 列表，否则回退为纯行级歌词。
 */
fun List<ProtoLyricLine>.toRichLyricLines(): List<RichLyricLine> =
    mapIndexedNotNull { index, line ->
        val text = line.words ?: return@mapIndexedNotNull null
        if (text.isBlank()) return@mapIndexedNotNull null
        val endTimeMs = getOrNull(index + 1)?.startTimeMs ?: (line.startTimeMs + 5000L)
        RichLyricLine(
            begin = line.startTimeMs,
            end = endTimeMs,
            duration = endTimeMs - line.startTimeMs,
            text = text,
            words = line.toLyricWords()
        )
    }

/**
 * 按 [ProtoSyllable.count] 累计游标切片整行文本，生成逐字 [LyricWord]。
 *
 * 任一音节计数异常或累计长度与整行文本不一致时返回 null，
 * 由调用方回退为纯行级歌词，避免切片错位。
 */
private fun ProtoLyricLine.toLyricWords(): List<LyricWord>? {
    val text = words ?: return null
    if (syllables.isEmpty()) return null

    val sliced = syllables.fold(SliceState()) { state, syllable ->
        if (syllable.count < 0 || state.offset + syllable.count > text.length) return null
        val endOffset = state.offset + syllable.count
        state.copy(
            offset = endOffset,
            words = state.words + LyricWord(
                begin = syllable.startTimeMs,
                end = syllable.endTimeMs,
                duration = (syllable.endTimeMs - syllable.startTimeMs).coerceAtLeast(0L),
                text = text.substring(state.offset, endOffset)
            )
        )
    }
    return sliced.words.takeIf { sliced.offset == text.length }
}

/**
 * 切片过程中的不可变游标状态。
 */
private data class SliceState(
    val offset: Int = 0,
    val words: List<LyricWord> = emptyList()
)