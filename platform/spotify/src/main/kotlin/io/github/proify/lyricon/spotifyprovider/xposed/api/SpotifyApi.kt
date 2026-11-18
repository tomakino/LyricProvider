/*
 * Copyright 2026 Proify, Tomakino
 * Licensed under the Apache License, Version 2.0
 * http://www.apache.org/licenses/LICENSE-2.0
 */

package io.github.proify.lyricon.spotifyprovider.xposed.api

import com.highcapable.yukihookapi.hook.log.YLog
import io.github.proify.lyricon.spotifyprovider.xposed.api.SpotifyException.ApiException
import okhttp3.ConnectionPool
import okhttp3.OkHttpClient
import okhttp3.Request
import okhttp3.Response
import java.io.IOException
import java.net.ConnectException
import java.net.SocketException
import java.net.SocketTimeoutException
import java.util.concurrent.ConcurrentHashMap
import java.util.concurrent.TimeUnit

/** Spotify color-lyrics 接口。 */
object SpotifyApi {

    private const val TAG = "SpotifyApi"
    private const val BASE_URL = "https://gae2-spclient.spotify.com/color-lyrics/v2/track/"

    private const val MAX_RETRIES = 3
    private const val INITIAL_RETRY_DELAY_MS = 500L

    private val client: OkHttpClient by lazy {
        OkHttpClient.Builder()
            .connectTimeout(10, TimeUnit.SECONDS)
            .readTimeout(15, TimeUnit.SECONDS)
            .writeTimeout(15, TimeUnit.SECONDS)
            .connectionPool(
                ConnectionPool(
                    maxIdleConnections = 5,
                    keepAliveDuration = 5,
                    timeUnit = TimeUnit.MINUTES
                )
            )
            .retryOnConnectionFailure(true)
            .build()
    }

    /** 从宿主请求中捕获的请求头。 */
    val capturedHeaders = ConcurrentHashMap<String, String>()

    /** 发起歌词请求所需的请求头名称。 */
    val REQUIRED_HEADERS: Set<String> = setOf(
        "authorization",
        "client-token",
        "user-agent",
        "x-client-id",
    )

    /**
     * 拉取指定曲目的原始歌词字节。
     *
     * @param id 曲目标识
     * @throws SpotifyException 请求失败
     */
    @Throws(SpotifyException::class)
    fun fetchRawLyric(id: String): ByteArray {
        val url = "$BASE_URL$id?vocalRemoval=true&clientLanguage=zh_CN&preview=false"

        val requestBuilder = Request.Builder()
            .url(url)
            .get()
            .addHeader("accept", "application/protobuf")
            .addHeader("content-type", "application/protobuf")
            .addHeader("app-platform", "Android")

        capturedHeaders.toMap().forEach { (key, value) ->
            requestBuilder.addHeader(key, value)
        }

        return execute(requestBuilder.build()).body.bytes()
    }

    /**
     * 执行请求，可重试异常按指数退避重试。
     *
     * @param maxRetries 最大尝试次数
     */
    private fun execute(request: Request, maxRetries: Int = MAX_RETRIES): Response {
        var lastException: Exception? = null
        var retryDelay = INITIAL_RETRY_DELAY_MS

        for (attempt in 1..maxRetries) {
            try {
                return executeOnce(request)
            } catch (e: Exception) {
                lastException = e

                if (attempt < maxRetries && isRetryable(e)) {
                    YLog.warn(
                        tag = TAG,
                        msg = "请求失败，${retryDelay}ms 后重试 ($attempt/$maxRetries): ${e.message}"
                    )
                    Thread.sleep(retryDelay)
                    retryDelay *= 2
                } else {
                    break
                }
            }
        }

        throw lastException ?: IOException("未知错误")
    }

    private fun executeOnce(request: Request): Response {
        val response = client.newCall(request).execute()
        if (!response.isSuccessful) {
            val code = response.code
            val message = response.message
            response.close()
            when (code) {
                403, 404 -> throw ApiException.NoLyricFoundException("未知")
                in 500..599 -> throw ApiException.ResponseException(code, "服务器错误: $message")
                else -> throw ApiException.ResponseException(code, "请求失败: $message")
            }
        }
        return response
    }

    private fun isRetryable(e: Exception): Boolean = when (e) {
        is SocketTimeoutException -> true
        is ConnectException -> true
        is SocketException -> true
        is IOException -> true
        else -> false
    }
}
