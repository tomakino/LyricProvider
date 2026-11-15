/*
 * Copyright 2026 Proify, Tomakino
 * Licensed under the Apache License, Version 2.0
 * http://www.apache.org/licenses/LICENSE-2.0
 */

package io.github.proify.lyricon.spotifyprovider.xposed.parser

import io.github.proify.lyricon.spotifyprovider.xposed.api.proto.ProtoColorData
import io.github.proify.lyricon.spotifyprovider.xposed.api.proto.ProtoLyricLine
import io.github.proify.lyricon.spotifyprovider.xposed.api.proto.ProtoLyricResponse
import io.github.proify.lyricon.spotifyprovider.xposed.api.proto.ProtoLyricsData
import io.github.proify.lyricon.spotifyprovider.xposed.api.proto.ProtoSyllable

/**
 * Spotify color-lyrics protobuf 响应解析器。
 *
 * 纯函数实现：输入原始字节，输出不可变模型；字节损坏或遇到不支持的 wire type 时返回 null。
 * 解析过程不依赖任何 Android / Xposed 上下文，可独立单元测试。
 */
object SpotifyProtoParser {

    private const val FIELD_LYRICS = 1
    private const val FIELD_COLORS = 2

    private const val FIELD_SYNC_TYPE = 1
    private const val FIELD_LINES = 2
    private const val FIELD_PROVIDER = 3
    private const val FIELD_PROVIDER_LYRICS_ID = 4
    private const val FIELD_PROVIDER_DISPLAY_NAME = 5
    private const val FIELD_PREVIEW_LINES = 17

    private const val FIELD_START_TIME_MS = 1
    private const val FIELD_WORDS = 2
    private const val FIELD_SYLLABLES = 3

    private const val FIELD_COUNT = 2
    private const val FIELD_END_TIME_MS = 3

    private const val WIRE_VARINT = 0
    private const val WIRE_FIXED64 = 1
    private const val WIRE_LENGTH_DELIMITED = 2
    private const val WIRE_FIXED32 = 5

    /**
     * 解析完整响应。
     *
     * @param bytes 服务端返回的原始 protobuf 字节
     * @return 解析后的不可变响应模型；解析失败返回 null
     */
    fun parse(bytes: ByteArray): ProtoLyricResponse? = runCatching {
        parseLyricResponse(ProtoCursor(bytes)).value
    }.getOrNull()

    // ---------------------------------- 不可变游标与解析结果 ----------------------------------

    private data class ProtoCursor(val bytes: ByteArray, val offset: Int = 0) {
        val isEnd: Boolean get() = offset >= bytes.size
        fun remaining(): Int = bytes.size - offset
        override fun equals(other: Any?): Boolean {
            if (this === other) return true
            if (javaClass != other?.javaClass) return false

            other as ProtoCursor

            if (offset != other.offset) return false
            if (!bytes.contentEquals(other.bytes)) return false
            if (isEnd != other.isEnd) return false

            return true
        }

        override fun hashCode(): Int {
            var result = offset
            result = 31 * result + bytes.contentHashCode()
            result = 31 * result + isEnd.hashCode()
            return result
        }
    }

    private data class Parsed<out T>(val value: T, val cursor: ProtoCursor)

    private data class FieldTag(val number: Int, val wireType: Int)

    // ---------------------------------- 基础 wire 读取 ----------------------------------

    private fun ProtoCursor.readVarint(): Parsed<Long> {
        var value = 0L
        var shift = 0
        var cursor = this
        while (true) {
            if (cursor.isEnd) throw IllegalArgumentException("Unexpected end of protobuf while reading varint")
            val byte = cursor.bytes[cursor.offset].toInt() and 0xFF
            cursor = cursor.copy(offset = cursor.offset + 1)
            value = value or ((byte and 0x7F).toLong() shl shift)
            if (byte and 0x80 == 0) return Parsed(value, cursor)
            shift += 7
            if (shift >= 64) throw IllegalArgumentException("Malformed protobuf varint")
        }
    }

    private fun ProtoCursor.readTag(): Parsed<FieldTag> {
        val (raw, next) = readVarint()
        val tag = raw.toInt()
        return Parsed(FieldTag(tag ushr 3, tag and 0x07), next)
    }

    private fun ProtoCursor.readLengthDelimited(): Parsed<ByteArray> {
        val (length, afterLength) = readVarint()
        val lengthInt = length.toInt()
        if (length > Int.MAX_VALUE.toLong() || lengthInt > afterLength.remaining()) {
            throw IllegalArgumentException("Truncated protobuf length-delimited field")
        }
        val end = afterLength.offset + lengthInt
        return Parsed(
            afterLength.bytes.copyOfRange(afterLength.offset, end),
            afterLength.copy(offset = end)
        )
    }

    private fun ProtoCursor.skip(wireType: Int): ProtoCursor = when (wireType) {
        WIRE_VARINT -> readVarint().cursor
        WIRE_FIXED64 -> copy(offset = offset + 8)
        WIRE_LENGTH_DELIMITED -> readLengthDelimited().cursor
        WIRE_FIXED32 -> copy(offset = offset + 4)
        else -> throw IllegalArgumentException("Unsupported protobuf wire type: $wireType")
    }

    private fun Parsed<ByteArray>.toUtf8String(): Parsed<String> =
        Parsed(String(value, Charsets.UTF_8), cursor)

    // ---------------------------------- 消息解析 ----------------------------------

    private fun parseLyricResponse(start: ProtoCursor): Parsed<ProtoLyricResponse> {
        var lyrics: ProtoLyricsData? = null
        var colors: ProtoColorData? = null
        var cursor = start
        while (!cursor.isEnd) {
            val (field, next) = cursor.readTag()
            when (field.number) {
                FIELD_LYRICS -> {
                    val payload = next.readLengthDelimited()
                    lyrics = parseLyricsData(ProtoCursor(payload.value)).value
                    cursor = payload.cursor
                }

                FIELD_COLORS -> {
                    val payload = next.readLengthDelimited()
                    colors = parseColorData(ProtoCursor(payload.value))
                    cursor = payload.cursor
                }

                else -> cursor = next.skip(field.wireType)
            }
        }
        return Parsed(ProtoLyricResponse(lyrics = lyrics, colors = colors), cursor)
    }

    private fun parseLyricsData(start: ProtoCursor): Parsed<ProtoLyricsData> {
        var syncType = 0
        var provider: String? = null
        var providerLyricsId: String? = null
        var providerDisplayName: String? = null
        val lines = mutableListOf<ProtoLyricLine>()
        val previewLines = mutableListOf<ProtoLyricLine>()
        var cursor = start
        while (!cursor.isEnd) {
            val (field, next) = cursor.readTag()
            when (field.number) {
                FIELD_SYNC_TYPE -> {
                    val parsed = next.readVarint()
                    syncType = parsed.value.toInt()
                    cursor = parsed.cursor
                }

                FIELD_LINES -> {
                    val payload = next.readLengthDelimited()
                    lines += parseLyricLine(ProtoCursor(payload.value)).value
                    cursor = payload.cursor
                }

                FIELD_PROVIDER -> {
                    val payload = next.readLengthDelimited().toUtf8String()
                    provider = payload.value
                    cursor = payload.cursor
                }

                FIELD_PROVIDER_LYRICS_ID -> {
                    val payload = next.readLengthDelimited().toUtf8String()
                    providerLyricsId = payload.value
                    cursor = payload.cursor
                }

                FIELD_PROVIDER_DISPLAY_NAME -> {
                    val payload = next.readLengthDelimited().toUtf8String()
                    providerDisplayName = payload.value
                    cursor = payload.cursor
                }

                FIELD_PREVIEW_LINES -> {
                    val payload = next.readLengthDelimited()
                    previewLines += parseLyricLine(ProtoCursor(payload.value)).value
                    cursor = payload.cursor
                }

                else -> cursor = next.skip(field.wireType)
            }
        }
        return Parsed(
            ProtoLyricsData(
                syncType = syncType,
                lines = lines,
                provider = provider,
                providerLyricsId = providerLyricsId,
                providerDisplayName = providerDisplayName,
                previewLines = previewLines
            ),
            cursor
        )
    }

    private fun parseLyricLine(start: ProtoCursor): Parsed<ProtoLyricLine> {
        var startTimeMs = 0L
        var words: String? = null
        val syllables = mutableListOf<ProtoSyllable>()
        var cursor = start
        while (!cursor.isEnd) {
            val (field, next) = cursor.readTag()
            when (field.number) {
                FIELD_START_TIME_MS -> {
                    val parsed = next.readVarint()
                    startTimeMs = parsed.value
                    cursor = parsed.cursor
                }

                FIELD_WORDS -> {
                    val payload = next.readLengthDelimited().toUtf8String()
                    words = payload.value
                    cursor = payload.cursor
                }

                FIELD_SYLLABLES -> {
                    val payload = next.readLengthDelimited()
                    syllables += parseSyllable(ProtoCursor(payload.value)).value
                    cursor = payload.cursor
                }

                else -> cursor = next.skip(field.wireType)
            }
        }
        return Parsed(
            ProtoLyricLine(
                startTimeMs = startTimeMs,
                words = words,
                syllables = syllables
            ), cursor
        )
    }

    private fun parseSyllable(start: ProtoCursor): Parsed<ProtoSyllable> {
        var startTimeMs = 0L
        var count = 0
        var endTimeMs = 0L
        var cursor = start
        while (!cursor.isEnd) {
            val (field, next) = cursor.readTag()
            when (field.number) {
                FIELD_START_TIME_MS -> {
                    val parsed = next.readVarint()
                    startTimeMs = parsed.value
                    cursor = parsed.cursor
                }

                FIELD_COUNT -> {
                    val parsed = next.readVarint()
                    count = parsed.value.toInt()
                    cursor = parsed.cursor
                }

                FIELD_END_TIME_MS -> {
                    val parsed = next.readVarint()
                    endTimeMs = parsed.value
                    cursor = parsed.cursor
                }

                else -> cursor = next.skip(field.wireType)
            }
        }
        return Parsed(ProtoSyllable(startTimeMs, count, endTimeMs), cursor)
    }

    private fun parseColorData(start: ProtoCursor): ProtoColorData {
        val values = buildList {
            var cursor = start
            while (!cursor.isEnd) {
                val (field, next) = cursor.readTag()
                if (field.wireType == WIRE_VARINT) {
                    val parsed = next.readVarint()
                    add(parsed.value.toInt())
                    cursor = parsed.cursor
                } else {
                    cursor = next.skip(field.wireType)
                }
            }
        }
        return ProtoColorData(
            background = values.getOrElse(0) { 0 },
            text = values.getOrElse(1) { 0 },
            highlightText = values.getOrElse(2) { 0 }
        )
    }
}
