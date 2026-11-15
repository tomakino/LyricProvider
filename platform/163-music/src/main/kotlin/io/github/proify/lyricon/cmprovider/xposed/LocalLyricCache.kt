/*
 * Copyright 2026 Proify, Tomakino
 * Licensed under the Apache License, Version 2.0
 * http://www.apache.org/licenses/LICENSE-2.0
 */

package io.github.proify.lyricon.cmprovider.xposed

import kotlinx.serialization.Serializable

@Serializable
data class LocalLyricCache(
    val lrc: String? = null,
    val lrcTranslateLyric: String? = null,
    val yrc: String? = null,
    val yrcTranslateLyric: String? = null,
    val pureMusic: Boolean = false,
    val musicId: Long = 0,
    val roma: String? = null
)