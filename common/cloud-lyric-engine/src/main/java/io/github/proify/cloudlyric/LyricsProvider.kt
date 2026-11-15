/*
 * Copyright 2026 Proify, Tomakino
 * Licensed under the Apache License, Version 2.0
 * http://www.apache.org/licenses/LICENSE-2.0
 */

package io.github.proify.cloudlyric

/**
 * 歌词提供商通用接口
 */
interface LyricsProvider {
    /** 提供商名称 */
    val id: String

    /**
     * 搜索歌词
     *
     * @param query 综合搜索 (搜索任何字段)
     * @param trackName 曲目名
     * @param artistName 歌手名
     * @param albumName 专辑名
     */
    suspend fun search(
        query: String? = null,
        trackName: String? = null,
        artistName: String? = null,
        albumName: String? = null,
        limit: Int = 10
    ): List<LyricsResult>
}