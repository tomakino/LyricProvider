/*
 * Copyright 2026 Proify, Tomakino
 * Licensed under the Apache License, Version 2.0
 * http://www.apache.org/licenses/LICENSE-2.0
 */

package io.github.proify.qrckit.parser.model

import io.github.proify.lyricon.lyric.model.LyricLine
import kotlinx.serialization.Serializable

/**
 * QRC 解析结果。
 * @property metadata 元数据标签，键保留原始大小写。
 * @property lines 按开始时间排序的歌词行。
 */
@Serializable
data class QrcDocument(
    val metadata: Map<String, String>,
    val lines: List<LyricLine>
)