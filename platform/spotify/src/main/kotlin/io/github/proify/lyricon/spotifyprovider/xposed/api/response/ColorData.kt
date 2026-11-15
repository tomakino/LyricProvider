/*
 * Copyright 2026 Proify, Tomakino
 * Licensed under the Apache License, Version 2.0
 * http://www.apache.org/licenses/LICENSE-2.0
 */

package io.github.proify.lyricon.spotifyprovider.xposed.api.response

import kotlinx.serialization.Serializable

@Serializable
data class ColorData(
    val background: Int,
    val text: Int,
    val highlightText: Int
)