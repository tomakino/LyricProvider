/*
 * Copyright 2026 Proify, Tomakino
 * Licensed under the Apache License, Version 2.0
 * http://www.apache.org/licenses/LICENSE-2.0
 */

@file:OptIn(ExperimentalSerializationApi::class)

package io.github.proify.lyricon.spotifyprovider.xposed.api.proto

import kotlinx.serialization.ExperimentalSerializationApi
import kotlinx.serialization.Serializable
import kotlinx.serialization.protobuf.ProtoNumber

/** Spotify 歌词 protobuf 序列化模型。字段编号与宿主 wire schema 对应。 */

/** color-lyrics 接口顶层响应。 */
@Serializable
data class ColorLyricsResponse(
    @ProtoNumber(1) val lyrics: LyricsResponse? = null,
    @ProtoNumber(2) val colors: ColorData? = null,
    @ProtoNumber(3) val hasVocalRemoval: Boolean = false,
)

/** 歌词配色。 */
@Serializable
data class ColorData(
    @ProtoNumber(1) val background: Int = 0,
    @ProtoNumber(2) val text: Int = 0,
    @ProtoNumber(3) val highlightText: Int = 0,
)

/** 歌词主体（v2）。 */
@Serializable
data class LyricsResponse(
    @ProtoNumber(1) val syncType: Int = 0,
    @ProtoNumber(2) val lines: List<LyricsLine> = emptyList(),
    @ProtoNumber(3) val provider: String = "",
    @ProtoNumber(4) val providerLyricsId: String = "",
    @ProtoNumber(5) val providerDisplayName: String = "",
    @ProtoNumber(7) val syncLyricsUri: String = "",
    @ProtoNumber(9) val alternatives: List<Alternative> = emptyList(),
    @ProtoNumber(10) val language: String = "",
    @ProtoNumber(11) val isRtlLanguage: Boolean = false,
    @ProtoNumber(14) val capStatus: Int = 0,
    @ProtoNumber(19) val translationLanguages: List<String> = emptyList(),
)

/** 单行歌词（v2）。 */
@Serializable
data class LyricsLine(
    @ProtoNumber(1) val startTimeMs: Long = 0L,
    @ProtoNumber(2) val words: String = "",
    @ProtoNumber(3) val syllables: List<Syllable> = emptyList(),
)

/** 逐字块（v2）。 */
@Serializable
data class Syllable(
    @ProtoNumber(1) val startTimeMs: Long = 0L,
    @ProtoNumber(2) val numChars: Int = 0,
)

/** 备选歌词（v2）。 */
@Serializable
data class Alternative(
    @ProtoNumber(1) val language: String = "",
    @ProtoNumber(2) val lines: List<LyricsLine> = emptyList(),
    @ProtoNumber(3) val isRtlLanguage: Boolean = false,
)

/** 歌词主体（v3）。 */
@Serializable
data class LyricsV3Response(
    @ProtoNumber(1) val syncType: Int = 0,
    @ProtoNumber(2) val lines: List<V3Line> = emptyList(),
    @ProtoNumber(3) val previewLines: List<V3Line> = emptyList(),
    @ProtoNumber(4) val provider: V3Provider? = null,
    @ProtoNumber(5) val translation: V3Translation? = null,
    @ProtoNumber(6) val language: V3Language? = null,
    @ProtoNumber(7) val capStatus: Int = 0,
)

/** 单行歌词（v3）。 */
@Serializable
data class V3Line(
    @ProtoNumber(1) val startTimeMs: Long = 0L,
    @ProtoNumber(2) val endTimeMs: Long = 0L,
    @ProtoNumber(3) val words: String = "",
    @ProtoNumber(4) val syncData: List<V3SyncData> = emptyList(),
)

/** 逐字块（v3）。 */
@Serializable
data class V3SyncData(
    @ProtoNumber(1) val startTimeMs: Long = 0L,
    @ProtoNumber(2) val endTimeMs: Long = 0L,
    @ProtoNumber(3) val numChars: Int = 0,
)

/** 语言信息（v3）。 */
@Serializable
data class V3Language(
    @ProtoNumber(1) val code: String = "",
    @ProtoNumber(2) val isRtl: Boolean = false,
)

/** 歌词来源（v3）。 */
@Serializable
data class V3Provider(
    @ProtoNumber(1) val name: String = "",
    @ProtoNumber(2) val lyricsId: String = "",
    @ProtoNumber(3) val displayName: String = "",
)

/** 译文（v3）。 */
@Serializable
data class V3Translation(
    @ProtoNumber(1) val language: V3Language? = null,
    @ProtoNumber(2) val lines: List<V3Line> = emptyList(),
)
