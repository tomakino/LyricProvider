/*
 * Copyright 2026 Proify, Tomakino
 * Licensed under the Apache License, Version 2.0
 * http://www.apache.org/licenses/LICENSE-2.0
 */

package io.github.proify.lyricon.yrckit.download.response

import kotlinx.serialization.Serializable

@Serializable
data class Contributor(
    val id: Long,
    val status: Int,
    val demand: Int,
    val userid: Long,
    val nickname: String,
    val uptime: Long
)