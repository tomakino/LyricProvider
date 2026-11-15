/*
 * Copyright 2026 Proify, Tomakino
 * Licensed under the Apache License, Version 2.0
 * http://www.apache.org/licenses/LICENSE-2.0
 */

package io.github.proify.cloudlyric.provider.qq

import kotlinx.serialization.json.Json
import kotlinx.serialization.json.JsonElement
import kotlinx.serialization.json.buildJsonObject
import kotlinx.serialization.json.jsonObject
import kotlinx.serialization.json.put
import kotlinx.serialization.json.putJsonObject
import java.io.OutputStreamWriter
import java.net.HttpURLConnection
import java.net.URI

object MusicApiService {

    private val jsonConfig = Json {
        ignoreUnknownKeys = true
        coerceInputValues = true
    }

    private const val API_URL = "https://u.y.qq.com/cgi-bin/musicu.fcg"

    /**
     * 使用关键词搜索歌曲
     *
     * @param keyword 关键词（字符串）
     * @param searchType 搜索结果类型：0 歌曲，2 专辑，3 歌单，4 MV，7 歌词，8 用户
     * @param resultNum 每页结果数量
     * @param pageNum 页面序号
     * @param origin 是否返回原始完整数据
     * @return 解析后的 [JsonElement]，异常或无数据时返回 null
     */
    fun searchWithKeyword(
        keyword: String,
        searchType: Int = 0,
        resultNum: Int = 10,
        pageNum: Int = 1,
        origin: Boolean = false
    ): JsonElement? {

        // 1. 构建请求 JSON
        val requestBody = buildJsonObject {
            putJsonObject("comm") {
                put("ct", "19")
                put("cv", "1859")
                put("uin", "0")
            }
            putJsonObject("req") {
                put("method", "DoSearchForQQMusicDesktop")
                put("module", "music.search.SearchCgiService")
                putJsonObject("param") {
                    put("grp", 1)
                    put("num_per_page", resultNum)
                    put("page_num", pageNum)
                    put("query", keyword)
                    put("search_type", searchType)
                }
            }
        }.toString()

        var connection: HttpURLConnection? = null
        return try {
            // 2. 初始化连接
            val url = URI.create(API_URL).toURL()
            connection = url.openConnection() as HttpURLConnection

            with(connection) {
                requestMethod = "POST"
                connectTimeout = 10000
                readTimeout = 10000
                doOutput = true // 允许发送请求体

                // 设置请求头
                setRequestProperty("Content-Type", "application/json; charset=utf-8")
                setRequestProperty("Accept", "application/json, text/plain, */*")
                setRequestProperty(
                    "User-Agent",
                    "Mozilla/5.0 (Windows NT 10.0; Win64; x64; rv:109.0) Gecko/20100101 Firefox/115.0"
                )
            }

            // 3. 写入请求体
            OutputStreamWriter(connection.outputStream, "UTF-8").use {
                it.write(requestBody)
            }

            // 4. 读取响应
            if (connection.responseCode == HttpURLConnection.HTTP_OK) {
                val responseText = connection.inputStream.bufferedReader().use { it.readText() }
                val fullJson = jsonConfig.parseToJsonElement(responseText).jsonObject

                if (origin) return fullJson

                // 5. 提取核心数据
                val body = fullJson["req"]?.jsonObject
                    ?.get("data")?.jsonObject
                    ?.get("body")?.jsonObject ?: return null

                when (searchType) {
                    0, 7 -> body["song"]
                    2 -> body["album"]
                    3 -> body["songlist"]
                    4 -> body["mv"]
                    8 -> body["user"]
                    else -> body
                }
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
}