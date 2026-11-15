/*
 * Copyright 2026 Proify, Tomakino
 * Licensed under the Apache License, Version 2.0
 * http://www.apache.org/licenses/LICENSE-2.0
 */

package io.github.proify.lyricon.spotifyprovider.xposed.api

import com.highcapable.yukihookapi.hook.log.YLog
import io.github.proify.lyricon.spotifyprovider.xposed.api.exception.SpotifyException.ApiException
import okhttp3.ConnectionPool
import okhttp3.OkHttpClient
import okhttp3.Request
import okhttp3.Response
import java.io.IOException
import java.net.ConnectException
import java.net.SocketException
import java.net.SocketTimeoutException
import java.util.concurrent.TimeUnit

/**
 * Spotify API 客户端，提供优化的网络请求
 */
object SpotifyApiClient {

    private const val TAG = "SpotifyApiClient"
    private const val MAX_RETRIES = 3
    private const val INITIAL_RETRY_DELAY_MS = 1000L

    /**
     * 优化的 OkHttpClient 配置
     */
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

    /**
     * 执行带重试的网络请求
     */
    fun executeWithRetry(
        request: Request,
        maxRetries: Int = MAX_RETRIES
    ): Response {
        var lastException: Exception? = null
        var retryDelay = INITIAL_RETRY_DELAY_MS

        for (attempt in 1..maxRetries) {
            try {
                return executeRequest(request)
            } catch (e: Exception) {
                lastException = e

                if (attempt < maxRetries && isRetryable(e)) {
                    YLog.warn(
                        tag = TAG,
                        msg = "请求失败，${retryDelay}ms 后重试 ($attempt/$maxRetries): ${e.message}"
                    )
                    Thread.sleep(retryDelay)
                    retryDelay *= 2 // 指数退避
                } else {
                    break
                }
            }
        }

        throw lastException ?: IOException("未知错误")
    }

    private fun executeRequest(request: Request): Response {
        return client.newCall(request).execute().use { response ->
            if (!response.isSuccessful) {
                when (response.code) {
                    404 -> throw ApiException.NoLyricFoundException("未知")
                    in 500..599 -> throw ApiException.ResponseException(
                        response.code,
                        "服务器错误: ${response.message}"
                    )

                    else -> throw ApiException.ResponseException(
                        response.code,
                        "请求失败: ${response.message}"
                    )
                }
            }
            response
        }
    }

    private fun isRetryable(e: Exception): Boolean {
        return when (e) {
            is SocketTimeoutException -> true
            is ConnectException -> true
            is SocketException -> true
            is IOException -> true
            else -> false
        }
    }
}