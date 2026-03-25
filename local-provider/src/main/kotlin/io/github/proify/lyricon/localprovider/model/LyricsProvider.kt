/*
 * Copyright 2026 Proify, Tomakino
 * Licensed under the Apache License, Version 2.0
 * http://www.apache.org/licenses/LICENSE-2.0
 */

package io.github.proify.lyricon.localprovider.model

interface LyricsProvider {
    val id: String
    suspend fun search(
        query: String? = null,
        trackName: String? = null,
        artistName: String? = null,
        albumName: String? = null,
        limit: Int = 10
    ): List<LyricsResult>
}