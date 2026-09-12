/*
 * Copyright 2026 Tomakino
 * Licensed under the Apache License, Version 2.0
 * http://www.apache.org/licenses/LICENSE-2.0
 */

package io.github.tomakino.amkit.download

import io.github.tomakino.amkit.LyricsDownloadException
import kotlinx.serialization.Serializable
import kotlinx.serialization.SerializationException
import kotlinx.serialization.json.Json

/**
 * 从 AMP（Apple Music API）响应中提取 TTML 字符串。
 *
 * 只声明真正需要的字段，其余全部忽略：完整的 `MediaEntity` 模型极其庞大，
 * 全量映射既无必要，也会随上游改动频繁失效（YAGNI）。
 *
 * 提取优先级与官方客户端一致：`ttmlLocalizations`（本地化）优先，
 * 缺失时回退到 `ttml`（默认）。
 */
internal object AmpTtmlExtractor {

    private val json = Json {
        ignoreUnknownKeys = true
        isLenient = true
    }

    /**
     * 提取 TTML。
     *
     * @return 找到的 TTML；字段缺失或为空时返回 `null`。
     * @throws LyricsDownloadException 响应不是预期的 JSON 结构。
     *
     * 这里刻意**不把解析异常吞成 `null`**：「响应结构变了」与「这首歌没歌词」
     * 是性质完全不同的两件事 —— 前者说明上游协议变更或响应被中间设备改写，
     * 需要人介入排查；后者只是这首曲子确实没有词，调用方正常降级即可。
     * 一旦合并成同一个返回值，前者会长期伪装成后者而无人察觉。
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
