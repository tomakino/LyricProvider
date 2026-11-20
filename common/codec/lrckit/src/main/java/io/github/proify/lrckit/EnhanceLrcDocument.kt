/*
 * Copyright 2026 Proify, Tomakino
 * Licensed under the Apache License, Version 2.0
 * http://www.apache.org/licenses/LICENSE-2.0
 */

package io.github.proify.lrckit

import io.github.proify.lyricon.lyric.model.LyricLine
import kotlinx.serialization.Serializable

/**
 * 增强 LRC 文档。
 * @property metadata 元数据标签，键保留原始大小写。
 * @property lines 按开始时间排序的歌词行，逐字数据位于每行的 words。
 */
@Serializable
data class EnhanceLrcDocument(
    val metadata: Map<String, String> = emptyMap(),
    val lines: List<LyricLine> = emptyList(),
)