/*
 * Copyright 2026 Proify, Tomakino
 * Licensed under the Apache License, Version 2.0
 * http://www.apache.org/licenses/LICENSE-2.0
 */

package io.github.proify.lyricon.spotifyprovider.xposed.api

/** 歌词请求异常。 */
sealed class SpotifyException(message: String, cause: Throwable? = null) :
    Exception(message, cause) {

    /** 接口响应异常。 */
    sealed class ApiException(message: String, cause: Throwable? = null) :
        SpotifyException(message, cause) {

        /** 该曲目没有可用歌词。 */
        class NoLyricFoundException(trackId: String) : ApiException("未找到歌词: $trackId")

        /** 接口返回了非成功状态码。 */
        class ResponseException(code: Int, message: String) :
            ApiException("API 响应错误: $code $message")
    }
}
