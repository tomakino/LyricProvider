/*
 * Copyright 2026 Proify, Tomakino
 * Licensed under the Apache License, Version 2.0
 * http://www.apache.org/licenses/LICENSE-2.0
 */

package io.github.proify.lyricon.yrckit.download

import kotlinx.serialization.json.JsonNull
import kotlinx.serialization.json.buildJsonObject
import kotlinx.serialization.json.put
import okhttp3.FormBody
import okhttp3.HttpUrl.Companion.toHttpUrl
import okhttp3.OkHttpClient
import okhttp3.Request
import okhttp3.brotli.BrotliInterceptor
import java.io.IOException

internal object NeteaseEApi {
    private const val BASE_URL = "https://interface.music.163.com/eapi/"

    private val http = OkHttpClient.Builder()
        .addInterceptor(BrotliInterceptor)
        .build()

    private fun request(endpoint: String, params: Map<String, Any?>): String {
        val url = "$BASE_URL$endpoint".toHttpUrl()
        val jsonParams = toJson(params)

        val encryptedData = NetEaseCrypto.eApiEncrypt(url.encodedPath, jsonParams)

        val body = FormBody.Builder(Charsets.UTF_8).apply {
            encryptedData.forEach { (k, v) ->
                add(k, v)
            }
        }.build()

        val req = Request.Builder()
            .url(url)
            .post(body)
            .build()

        return http.newCall(req).execute().use { resp ->
            if (!resp.isSuccessful) throw IOException("Http Error: ${resp.code}, ${resp.message}")
            resp.body.string()
        }
    }

    @Throws(IOException::class)
    fun fetchLyric(id: Long): String {
        return request(
            "song/lyric/v1", mapOf(
                "id" to id.toString(),
                "cp" to false,
                "lv" to 0,
                "tv" to 0,
                "rv" to 0,
                "yv" to 0,
                "ytv" to 0,
                "yrv" to 0
            )
        )
    }

    private fun toJson(params: Map<String, Any?>): String {
        val element = buildJsonObject {
            params.forEach { (key, value) ->
                when (value) {
                    is String -> put(key, value)
                    is Number -> put(key, value)
                    is Boolean -> put(key, value)
                    null -> put(key, JsonNull)
                    else -> put(key, value.toString())
                }
            }
        }
        return element.toString()
    }
}