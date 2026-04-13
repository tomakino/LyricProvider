/*
 * Copyright 2026 Proify, Tomakino
 * Licensed under the Apache License, Version 2.0
 * http://www.apache.org/licenses/LICENSE-2.0
 */

package io.github.proify.lyricon.spotifyprovider.xposed.api

import android.util.Log
import kotlinx.serialization.json.Json
import okhttp3.OkHttpClient
import okhttp3.Request
import org.json.JSONObject
import java.io.IOException
import java.util.Locale
import java.util.concurrent.TimeUnit

/**
 * Spotify API 封装对象，负责处理歌词获取等网络请求
 */
object SpotifyApi {

    val keysRequired = arrayOf(
        "authorization",
        "client-token",
        "user-agent",
        "x-client-id"
    )

    private const val TAG = "SpotifyApi"
    private const val BASE_URL = "https://guc3-spclient.spotify.com/color-lyrics/v2/track/"

    val headers = mutableMapOf<String, String>()

    val jsonParser = Json {
        ignoreUnknownKeys = true
        coerceInputValues = true
    }

    /**
     * 线程安全的 OkHttpClient 单例
     */
    private val client: OkHttpClient by lazy {
        OkHttpClient.Builder()
            .connectTimeout(15, TimeUnit.SECONDS)
            .readTimeout(15, TimeUnit.SECONDS)
            .build()
    }

    /**
     * 根据歌曲 ID 获取原始歌词字符串
     *
     * @param id 歌曲唯一标识
     * @return 歌词 JSON 字符串
     * @throws Exception 网络错误或解析异常
     */
    @Throws(Exception::class)
    fun fetchRawLyric(id: String): String = performNetworkRequest(id)

    /**
     * 执行实际的网络请求逻辑
     */
    @Throws(Exception::class)
    private fun performNetworkRequest(id: String): String {
        val url = "$BASE_URL$id?vocalRemoval=false&clientLanguage=${
            Locale.getDefault().toLanguageTag()
        }&preview=false"

        val requestBuilder = Request.Builder()
            .url(url)
            .get()
            .addHeader("accept", "application/json")
            .addHeader("app-platform", "WebPlayer")

        // 注入外部配置的 Header
        headers.forEach { (key, value) ->
            requestBuilder.addHeader(key, value)
        }

        val request = requestBuilder.build()

        client.newCall(request).execute().use { response ->
            val code = response.code
            val bodyString = response.body.string()

            if (code == 404) {
                throw NoFoundLyricException(id, "No lyric found for $id")
            }

            if (!response.isSuccessful) {
                throw IOException("HTTP error code: $code, msg: ${response.message}")
            }

            return try {
                JSONObject(bodyString)
                bodyString
            } catch (e: Exception) {
                Log.e(TAG, "Invalid JSON response for $id: $bodyString", e)
                throw IOException("Invalid JSON response")
            }
        }
    }
}