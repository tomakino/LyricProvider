/*
 * Copyright 2026 Proify, Tomakino
 * Licensed under the Apache License, Version 2.0
 * http://www.apache.org/licenses/LICENSE-2.0
 */

package io.github.proify.lyricon.amprovider.xposed.api.lyric

import kotlinx.serialization.Serializable
import kotlinx.serialization.SerializationException
import kotlinx.serialization.json.Json

/** 从 AMP 响应中提取 TTML。 */
internal object AmpTtmlExtractor {

    private val json = Json {
        ignoreUnknownKeys = true
        isLenient = true
    }

    /**
     * 提取 TTML。
     *
     * @return TTML
     * @throws LyricsDownloadException 响应不是预期的 JSON 结构
     */
    fun extractTtml(responseBody: String): String? {
        val response = try {
            json.decodeFromString<AmpResponse>(responseBody)
        } catch (e: SerializationException) {
            throw LyricsDownloadException(
                "AMP 响应不是预期的 JSON 结构（长度 ${responseBody.length}）；" +
                        "可能是上游协议变更，或响应被中间设备改写",
                e,
            )
        }

        val attributes = response.data?.firstOrNull()?.attributes ?: return null

        return attributes.ttmlLocalizations?.takeIf { it.isNotBlank() }
            ?: attributes.ttml?.takeIf { it.isNotBlank() }
    }
}

@Serializable
private data class AmpResponse(
    val data: List<AmpEntity>? = null,
)

@Serializable
private data class AmpEntity(
    val attributes: AmpAttributes? = null,
)

@Serializable
private data class AmpAttributes(
    val ttml: String? = null,
    val ttmlLocalizations: String? = null,
)
