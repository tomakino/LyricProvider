/*
 * Copyright 2026 Proify, Tomakino
 * Licensed under the Apache License, Version 2.0
 * http://www.apache.org/licenses/LICENSE-2.0
 */

package io.github.proify.lyricon.localprovider.model

import io.github.proify.lyricon.lyric.model.RichLyricLine
import kotlinx.serialization.Serializable

@Serializable
data class LyricsResult(
    val trackName: String? = null,
    val artistName: String? = null,
    val albumName: String? = null,
    val rich: List<RichLyricLine> = emptyList(),
    val instrumental: Boolean = false,
)