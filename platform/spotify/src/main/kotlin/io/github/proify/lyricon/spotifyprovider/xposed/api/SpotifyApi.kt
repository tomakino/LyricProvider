/*
 * Copyright 2026 Proify, Tomakino
 * Licensed under the Apache License, Version 2.0
 * http://www.apache.org/licenses/LICENSE-2.0
 */

package io.github.proify.lyricon.spotifyprovider.xposed.api

import com.highcapable.yukihookapi.hook.log.YLog
import kotlinx.serialization.json.Json
import okhttp3.Request

/**
 * Spotify API 封装对象，负责处理歌词获取等网络请求
 */
object SpotifyApi {

    const val TAG = "SpotifyApi"

    val keysRequired = arrayOf(
        "authorization",
        "client-token",
        "user-agent",
        "x-client-id",
    )

    private const val BASE_URL = "https://gae2-spclient.spotify.com/color-lyrics/v2/track/"

    /** 请求头锁：捕获 Hook 与网络线程可能并发读写 */
    private val headersLock = Any()

    private val headers = mutableMapOf<String, String>()

    val jsonParser = Json {
        ignoreUnknownKeys = true
        coerceInputValues = true
    }

    /**
     * 根据歌曲 ID 获取原始歌词字节
     *
     * @param id 歌曲唯一标识
     * @return 歌词原始字节（protobuf；服务端降级时可能是 JSON）
     * @throws Exception 网络错误或解析异常
     */
    @Throws(Exception::class)
    fun fetchRawLyric(id: String): ByteArray {
        YLog.debug(tag = TAG, msg = "获取歌词: $id")
        return performNetworkRequest(id)
    }

    /**
     * 记录一条从宿主请求中捕获的请求头（线程安全）。
     *
     * @return true 表示值有更新
     */
    fun recordHeader(key: String, value: String): Boolean = synchronized(headersLock) {
        if (headers[key] == value) {
            false
        } else {
            headers[key] = value
            true
        }
    }

    /**
     * 返回已捕获请求头的快照（线程安全）。
     */
    fun snapshotHeaders(): Map<String, String> = synchronized(headersLock) {
        headers.toMap()
    }

    /**
     * 执行实际的网络请求逻辑
     */
    @Throws(Exception::class)
    private fun performNetworkRequest(id: String): ByteArray {
        val url = "$BASE_URL$id?vocalRemoval=true&clientLanguage=zh_CN&preview=false"

        val requestBuilder = Request.Builder()
            .url(url)
            .get()
            .addHeader("accept", "application/protobuf")
            .addHeader("content-type", "application/protobuf")
            .addHeader("app-platform", "Android")

        // 注入外部配置的 Header（快照拷贝，避免并发修改异常）
        snapshotHeaders().forEach { (key, value) ->
            requestBuilder.addHeader(key, value)
        }

        val request = requestBuilder.build()

        // 使用优化的客户端执行请求
        val response = SpotifyApiClient.executeWithRetry(request)

        return response.body.bytes()
    }
}