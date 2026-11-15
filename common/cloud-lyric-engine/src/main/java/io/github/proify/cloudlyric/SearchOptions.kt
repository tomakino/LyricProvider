/*
 * Copyright 2026 Proify, Tomakino
 * Licensed under the Apache License, Version 2.0
 * http://www.apache.org/licenses/LICENSE-2.0
 */

@file:Suppress("unused")

package io.github.proify.cloudlyric

/**
 * 歌词搜索配置选项
 */
data class SearchOptions(
    /** 综合搜索关键字 */
    var query: String? = null,
    /** 歌曲名称 */
    var trackName: String? = null,
    /** 歌手名称 */
    var artistName: String? = null,
    /** 专辑名称 */
    var albumName: String? = null,

    /** 每个歌词源内部搜索请求的最大条数 */
    var perProviderLimit: Int = 10,
    /** 最终返回的最大结果条数 */
    var maxTotalResults: Int = 15,

    /** 包含的 Provider ID 列表 */
    var includeProviders: List<String>? = null,
    /** 排除的 Provider ID 列表 */
    var excludeProviders: List<String>? = null,

    /**
     * 结果过滤谓词列表。
     * 只有满足所有谓词的结果才会被保留。
     */
    val predicates: MutableList<(ProviderLyrics) -> Boolean> = mutableListOf(),

    /**
     * 结果加分谓词（偏好设置）。
     * 满足该谓词的结果将在排序得分基础上额外加分。
     * 例如：{ it.lyrics.hasTranslation } // 有翻译的优先排在前面
     */
    val preferences: MutableList<Pair<(ProviderLyrics) -> Boolean, Int>> = mutableListOf()
) {
    /**
     * 添加一个过滤条件
     */
    fun filter(predicate: (ProviderLyrics) -> Boolean) {
        predicates.add(predicate)
    }

    /**
     * 添加一个偏好加分项
     * @param score 满足条件时的加分值
     */
    fun prefer(score: Int = 10, predicate: (ProviderLyrics) -> Boolean) {
        preferences.add(predicate to score)
    }
}