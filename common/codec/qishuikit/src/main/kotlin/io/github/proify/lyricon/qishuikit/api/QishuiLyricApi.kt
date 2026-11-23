/*
 * Copyright 2026 Proify, Tomakino
 * Licensed under the Apache License, Version 2.0
 * http://www.apache.org/licenses/LICENSE-2.0
 */

package io.github.proify.lyricon.qishuikit.api

import io.github.proify.lyricon.qishuikit.model.QishuiH5Response
import io.github.proify.lyricon.qishuikit.model.QishuiLyric
import kotlinx.coroutines.Dispatchers
import kotlinx.coroutines.withContext
import kotlinx.serialization.json.Json
import okhttp3.OkHttpClient
import okhttp3.Request
import java.util.concurrent.TimeUnit

class QishuiLyricApi(
    private val client: OkHttpClient = DEFAULT_CLIENT,
    private val json: Json = DEFAULT_JSON,
    private val endpoint: String = DEFAULT_ENDPOINT
) {

    suspend fun fetch(trackId: String): QishuiLyric? = withContext(Dispatchers.IO) {
        val request = Request.Builder()
            .url(endpoint.format(trackId))
            .header("User-Agent", USER_AGENT)
            .get()
            .build()

        val body = client.newCall(request).execute().use { response ->
            if (!response.isSuccessful) return@withContext null
            response.body.string()
        }

        val parsed = json.decodeFromString<QishuiH5Response>(body)
        val lyric = parsed.lyric ?: return@withContext null
        val content = lyric.content ?: return@withContext null
        val track = parsed.seoTrack?.track

        QishuiLyric(
            id = trackId,
            name = track?.name,
            artist = track?.artists?.joinToString(", ") { it.name.orEmpty() },
            duration = track?.duration ?: 0L,
            type = lyric.type ?: "krc",
            content = content,
            langTranslations = lyric.langTranslations
        )
    }

    private companion object {
        const val DEFAULT_ENDPOINT =
            "https://api.qishui.com/luna/h5/seo_track?track_id=%s&device_platform=web"
        const val USER_AGENT =
            "Mozilla/5.0 (Windows NT 10.0; Win64; x64) AppleWebKit/537.36 (KHTML, like Gecko) Chrome/120 Safari/537.36"

        val DEFAULT_JSON = Json { ignoreUnknownKeys = true }

        val DEFAULT_CLIENT: OkHttpClient = OkHttpClient.Builder()
            .connectTimeout(8, TimeUnit.SECONDS)
            .readTimeout(8, TimeUnit.SECONDS)
            .build()
    }
}
