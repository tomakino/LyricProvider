/*
 * Copyright 2026 Proify, Tomakino
 * Licensed under the Apache License, Version 2.0
 * http://www.apache.org/licenses/LICENSE-2.0
 */

package io.github.proify.cloudlyric

import io.github.proify.cloudlyric.provider.LyricsConfig
import kotlinx.coroutines.CoroutineDispatcher
import kotlinx.coroutines.Dispatchers
import kotlinx.coroutines.async
import kotlinx.coroutines.awaitAll
import kotlinx.coroutines.coroutineScope

/**
 * 云歌词聚合搜索核心逻辑类
 */
class CloudLyrics(
    private val providers: List<LyricsProvider> = LyricsConfig.ALL_PROVIDERS,
    private val ioDispatcher: CoroutineDispatcher = Dispatchers.IO
) {

    /**
     * 使用 DSL 配置进行搜索
     */
    suspend fun search(block: SearchOptions.() -> Unit): List<ProviderLyrics> {
        return search(SearchOptions().apply(block))
    }

    /**
     * 执行搜索逻辑
     */
    suspend fun search(options: SearchOptions): List<ProviderLyrics> = coroutineScope {
        val targetProviders = providers.filter { provider ->
            val id = provider.id
            (options.includeProviders?.contains(id) ?: true) &&
                    !(options.excludeProviders?.contains(id) ?: false)
        }

        targetProviders.map { provider ->
            async(ioDispatcher) {
                runCatching {
                    provider.search(
                        query = options.query,
                        trackName = options.trackName,
                        artistName = options.artistName,
                        albumName = options.albumName,
                        limit = options.perProviderLimit
                    ).take(options.perProviderLimit)
                        .map { ProviderLyrics(provider, it) }
                }.getOrElse {
                    it.printStackTrace()
                    emptyList()
                }
            }
        }
            .awaitAll()
            .flatten()
            .asSequence()
            // 应用自定义过滤谓词
            .filter { item -> options.predicates.all { it(item) } }
            // 基础质量过滤
            .filter { it.lyrics.rich.isNotEmpty() }
            // 综合排序：基础得分 + 偏好得分
            .sortedByDescending { item ->
                var finalScore = calculateIntegrityScore(item.lyrics)
                options.preferences.forEach { (predicate, bonus) ->
                    if (predicate(item)) finalScore += bonus
                }
                finalScore
            }
            .take(options.maxTotalResults)
            .toList()
    }

    /**
     * 计算单条歌词结果的完整度得分
     */
    private fun calculateIntegrityScore(lyrics: LyricsResult): Int {
        var score = 0
        if (lyrics.trackName.isNullOrBlank().not()) score += 20
        if (lyrics.artistName.isNullOrBlank().not()) score += 20
        if (lyrics.albumName.isNullOrBlank().not()) score += 10
        if (lyrics.rich.isNotEmpty()) score += 50
        return score
    }
}