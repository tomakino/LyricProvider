/*
 * Copyright 2026 Proify, Tomakino
 * Licensed under the Apache License, Version 2.0
 * http://www.apache.org/licenses/LICENSE-2.0
 */

@file:Suppress("unused")

package io.github.proify.lyricon.spotifyprovider.xposed.api.exception

/**
 * Spotify 歌词提供者基础异常
 */
sealed class SpotifyException(message: String, cause: Throwable? = null) :
    Exception(message, cause) {

    /**
     * 网络相关异常
     */
    sealed class NetworkException(message: String, cause: Throwable? = null) :
        SpotifyException(message, cause) {

        /** 连接超时 */
        class TimeoutException(cause: Throwable? = null) : NetworkException("连接超时", cause)

        /** 连接失败 */
        class ConnectionException(cause: Throwable? = null) : NetworkException("连接失败", cause)

        /** DNS 解析失败 */
        class DnsException(cause: Throwable? = null) : NetworkException("DNS 解析失败", cause)
    }

    /**
     * API 相关异常
     */
    sealed class ApiException(message: String, cause: Throwable? = null) :
        SpotifyException(message, cause) {

        /** 未找到歌词 */
        class NoLyricFoundException(trackId: String) : ApiException("未找到歌词: $trackId")

        /** API 响应错误 */
        class ResponseException(code: Int, message: String) :
            ApiException("API 响应错误: $code $message")

        /** 解析失败 */
        class ParseException(message: String, cause: Throwable? = null) :
            ApiException(message, cause)
    }

    /**
     * 缓存相关异常
     */
    sealed class CacheException(message: String, cause: Throwable? = null) :
        SpotifyException(message, cause) {

        /** 缓存读取失败 */
        class ReadException(cause: Throwable? = null) : CacheException("缓存读取失败", cause)

        /** 缓存写入失败 */
        class WriteException(cause: Throwable? = null) : CacheException("缓存写入失败", cause)

        /** 缓存损坏 */
        class CorruptedCacheException(cause: Throwable? = null) : CacheException("缓存损坏", cause)
    }
}