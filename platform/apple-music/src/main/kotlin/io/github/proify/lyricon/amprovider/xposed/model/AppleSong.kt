/*
 * Copyright 2026 Proify, Tomakino
 * Licensed under the Apache License, Version 2.0
 * http://www.apache.org/licenses/LICENSE-2.0
 */

package io.github.proify.lyricon.amprovider.xposed.model

import kotlinx.serialization.Serializable

@Serializable
data class AppleSong(
    var name: String? = null,
    var artist: String? = null,
    var adamId: String? = null,
    var agents: MutableList<LyricAgent> = mutableListOf(),
    var duration: Int = 0,
    var lyrics: MutableList<LyricLine> = mutableListOf()
)