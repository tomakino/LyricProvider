/*
 * Copyright 2026 Proify, Tomakino
 * Licensed under the Apache License, Version 2.0
 * http://www.apache.org/licenses/LICENSE-2.0
 */

package io.github.proify.cloudlyric.provider.lrclib

import io.github.proify.cloudlyric.LyricsProvider
import io.github.proify.cloudlyric.LyricsResult
import io.github.proify.cloudlyric.toRichLines
import io.github.proify.lrckit.LrcParser
import kotlinx.coroutines.Dispatchers
import kotlinx.coroutines.withContext
import kotlinx.serialization.json.Json
import java.net.HttpURLConnection
import java.net.URL
import java.net.URLEncoder
import java.nio.charset.StandardCharsets

/**
 * [LrcLibProvider] 是基于 LRCLIB API 实现的歌词提供者。
 */
class LrcLibProvider : LyricsProvider {
    companion object {
        const val ID = "LrcLib"

        private const val USER_AGENT: String =
            "CloudLyric (https://github.com/proify/LyricProvider)"

        private const val BASE_URL = "https://lrclib.net/api"

        private const val TIMEOUT = 6000
    }

    override val id: String = ID

    private val json = Json {
        ignoreUnknownKeys = true
        coerceInputValues = true
    }

    override suspend fun search(
        query: String?,
        trackName: String?,
        artistName: String?,
        albumName: String?,
        limit: Int
    ): List<LyricsResult> = withContext(Dispatchers.IO) {
        val queryParams = mutableMapOf<String, String>()
        query?.let { queryParams["q"] = it }
        trackName?.let { queryParams["track_name"] = it }
        artistName?.let { queryParams["artist_name"] = it }
        albumName?.let { queryParams["album_name"] = it }

        if (queryParams["q"] == null && queryParams["track_name"] == null) return@withContext emptyList()

        val fullUrl = "$BASE_URL/search?${encodeParams(queryParams)}"
        val responseBody = sendRawRequest(fullUrl) ?: return@withContext emptyList()

        return@withContext try {
            val response = json.decodeFromString<List<LrcLibResponse>>(responseBody)
            response
                .sortedByDescending { calculateIntegrityScore(it) }
                .take(limit)
                .map {
                    LyricsResult(
                        trackName = it.trackName,
                        artistName = it.artistName,
                        albumName = it.albumName,
                        rich = LrcParser.parse(it.syncedLyrics ?: "").lines.toRichLines(),
                        instrumental = it.instrumental,
                    )
                }
        } catch (e: Exception) {
            e.printStackTrace()
            emptyList()
        }
    }

    private fun sendRawRequest(urlString: String): String? {
        var connection: HttpURLConnection? = null
        return try {
            @Suppress("DEPRECATION") val url = URL(urlString)
            connection = (url.openConnection() as HttpURLConnection).apply {
                requestMethod = "GET"
                connectTimeout = TIMEOUT
                readTimeout = TIMEOUT
                setRequestProperty("User-Agent", USER_AGENT)
                setRequestProperty("Accept", "application/json")
                doInput = true
            }

            if (connection.responseCode == HttpURLConnection.HTTP_OK) {
                connection.inputStream.bufferedReader().use { it.readText() }
            } else {
                null
            }
        } catch (e: Exception) {
            e.printStackTrace()
            null
        } finally {
            connection?.disconnect()
        }
    }

    private fun encodeParams(params: Map<String, String>): String {
        return params.map { (key, value) ->
            val encodedKey = URLEncoder.encode(key, StandardCharsets.UTF_8.name())
            val encodedValue = URLEncoder.encode(value, StandardCharsets.UTF_8.name())
            "$encodedKey=$encodedValue"
        }.joinToString("&")
    }

    private fun calculateIntegrityScore(response: LrcLibResponse): Int {
        var score = 0
        if (!response.trackName.isNullOrBlank()) score += 20
        if (!response.artistName.isNullOrBlank()) score += 20
        if (!response.albumName.isNullOrBlank()) score += 10
        if (!response.syncedLyrics.isNullOrBlank()) score += 50
        return score
    }
}