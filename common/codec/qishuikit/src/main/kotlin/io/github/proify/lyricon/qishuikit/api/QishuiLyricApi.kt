/*
 * Copyright 2026 Proify, Tomakino
 * Licensed under the Apache License, Version 2.0
 * http://www.apache.org/licenses/LICENSE-2.0
 */

package io.github.proify.lyricon.qishuikit.api

import io.github.proify.lyricon.qishuikit.model.QishuiH5Response
import io.github.proify.lyricon.qishuikit.model.QishuiLyric
import kotlinx.coroutines.CancellationException
import kotlinx.coroutines.Dispatchers
import kotlinx.coroutines.withContext
import kotlinx.serialization.json.Json
import okhttp3.OkHttpClient
import okhttp3.Request
import java.util.concurrent.TimeUnit

/**
 * 汽水歌词接口客户端。
 * @param client HTTP 客户端。
 * @param json JSON 解码器。
 * @param endpoint 接口地址模板，`%s` 为曲目 ID 占位符。
 */
class QishuiLyricApi(
    private val client: OkHttpClient = DEFAULT_CLIENT,
    private val json: Json = DEFAULT_JSON,
    private val endpoint: String = DEFAULT_ENDPOINT
) {

    /**
     * 拉取指定曲目的歌词。
     * @param trackId 曲目 ID。
     * @return 歌词数据；请求失败、响应不成功或缺少歌词时为 null。
     */
    suspend fun fetch(trackId: String): QishuiLyric? = withContext(Dispatchers.IO) {
        try {
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
                langTranslations = lyric.langTranslations,
                translations = lyric.translations
            )
        } catch (e: CancellationException) {
            throw e
        } catch (e: Exception) {
            null
        }
    }
    private companion object {
        /** 默认歌词接口地址模板。 */
        const val DEFAULT_ENDPOINT =
            "https://api.qishui.com/luna/h5/seo_track?track_id=%s&device_platform=web"
        /** 默认请求标识。 */
        const val USER_AGENT =
            "Mozilla/5.0 (Windows NT 10.0; Win64; x64) AppleWebKit/537.36 (KHTML, like Gecko) Chrome/120 Safari/537.36"

        /** 默认 JSON 解码器。 */
        val DEFAULT_JSON = Json { ignoreUnknownKeys = true }

        /** 默认 HTTP 客户端。 */
        val DEFAULT_CLIENT: OkHttpClient = OkHttpClient.Builder()
            .connectTimeout(8, TimeUnit.SECONDS)
            .readTimeout(8, TimeUnit.SECONDS)
            .build()
    }
}
