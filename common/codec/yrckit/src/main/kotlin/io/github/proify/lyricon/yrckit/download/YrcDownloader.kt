/*
 * Copyright 2026 Proify, Tomakino
 * Licensed under the Apache License, Version 2.0
 * http://www.apache.org/licenses/LICENSE-2.0
 */

package io.github.proify.lyricon.yrckit.download

import io.github.proify.lyricon.yrckit.download.response.LyricResponse
import kotlinx.serialization.json.Json
import java.io.IOException

object YrcDownloader {

    private val jsonDecoder = Json {
        ignoreUnknownKeys = true
        coerceInputValues = true
    }

    @Throws(IOException::class)
    fun fetchLyric(id: Long): LyricResponse {
        val raw = NeteaseEApi.fetchLyric(id)
        val response = jsonDecoder.decodeFromString<LyricResponse>(raw)
        return response
    }
}