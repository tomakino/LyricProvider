/*
 * Copyright 2026 Proify, Tomakino
 * Licensed under the Apache License, Version 2.0
 * http://www.apache.org/licenses/LICENSE-2.0
 */

package io.github.proify.lyricon.spotifyprovider.xposed.api.proto

/**
 * Spotify color-lyrics protobuf 响应模型。
 *
 * 字段编号与抓包逆向得到的 wire schema 一一对应，
 * 仅保留歌词展示所需的字段，未知字段在解析时直接跳过。
 */
data class ProtoLyricResponse(
    val lyrics: ProtoLyricsData? = null,
    val colors: ProtoColorData? = null
)

/**
 * 歌词主体。
 *
 * @property syncType 同步类型，1 = 行同步，2 = 逐字同步（仅作参考，不参与判断）
 * @property lines 正文字幕行，逐字数据挂在该字段
 * @property previewLines 预览行，结构与 [lines] 相同，可忽略
 */
data class ProtoLyricsData(
    val syncType: Int = 0,
    val lines: List<ProtoLyricLine> = emptyList(),
    val provider: String? = null,
    val providerLyricsId: String? = null,
    val providerDisplayName: String? = null,
    val previewLines: List<ProtoLyricLine> = emptyList()
)

/**
 * 单行歌词。
 *
 * @property startTimeMs 行起始毫秒
 * @property words 整行文本
 * @property syllables 逐字（音节级）块；无逐字的行该列表为空
 */
data class ProtoLyricLine(
    val startTimeMs: Long = 0,
    val words: String? = null,
    val syllables: List<ProtoSyllable> = emptyList()
)

/**
 * 逐字（音节）块。
 *
 * @property startTimeMs 块起始毫秒
 * @property count 块覆盖的字符数（UTF-16 code unit，与 String.length 一致）
 * @property endTimeMs 块结束毫秒
 */
data class ProtoSyllable(
    val startTimeMs: Long = 0,
    val count: Int = 0,
    val endTimeMs: Long = 0
)

/**
 * 响应中的颜色信息（负 int32 按补码解释）。
 */
data class ProtoColorData(
    val background: Int = 0,
    val text: Int = 0,
    val highlightText: Int = 0
)
