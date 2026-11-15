/*
 * Copyright 2026 Proify, Tomakino
 * Licensed under the Apache License, Version 2.0
 * http://www.apache.org/licenses/LICENSE-2.0
 */

package io.github.proify.lyricon.amprovider.xposed.model

import kotlinx.serialization.Serializable

@Serializable
data class LyricSection(
    override var agent: String? = null,
    override var begin: Int = 0,
    override var duration: Int = 0,
    override var end: Int = 0,
    var lines: MutableList<LyricLine> = mutableListOf()
) : LyricTiming