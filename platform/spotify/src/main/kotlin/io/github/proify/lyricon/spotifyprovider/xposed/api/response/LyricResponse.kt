/*
 * Copyright 2026 Proify, Tomakino
 * Licensed under the Apache License, Version 2.0
 * http://www.apache.org/licenses/LICENSE-2.0
 */

package io.github.proify.lyricon.spotifyprovider.xposed.api.response

import kotlinx.serialization.Serializable

@Serializable
data class LyricResponse(
    val lyrics: LyricsData,
    val colors: ColorData? = null,
    val hasVocalRemoval: Boolean = false
)