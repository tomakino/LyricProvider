/*
 * Copyright 2026 Proify, Tomakino
 * Licensed under the Apache License, Version 2.0
 * http://www.apache.org/licenses/LICENSE-2.0
 */

package io.github.proify.lyricon.yrckit.download.response

import kotlinx.serialization.Serializable

@Serializable
data class LyricResponse(
    val code: Int = 0,
    val sgc: Boolean = false,
    val sfy: Boolean = false,
    val qfy: Boolean = false,
    val lrc: LyricContent? = null,
    val tlyric: LyricContent? = null,
    val yrc: LyricContent? = null,
    val ytlrc: LyricContent? = null,
    val romalrc: LyricContent? = null,
    val lyricUser: Contributor? = null,
    val transUser: Contributor? = null,
    val pureMusic: Boolean = false
)