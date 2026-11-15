/*
 * Copyright 2026 Proify, Tomakino
 * Licensed under the Apache License, Version 2.0
 * http://www.apache.org/licenses/LICENSE-2.0
 */

package io.github.proify.lyricon.spotifyprovider.xposed.api.response

import kotlinx.serialization.Serializable

@Serializable
data class LyricLine(
    val startTimeMs: Long = 0,
    val words: String? = null,
    val endTimeMs: Long = 0,
    val transliteratedWords: String? = null
)