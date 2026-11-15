/*
 * Copyright 2026 Proify, Tomakino
 * Licensed under the Apache License, Version 2.0
 * http://www.apache.org/licenses/LICENSE-2.0
 */

@file:Suppress("unused")

package io.github.proify.cloudlyric.provider.lrclib

import kotlinx.serialization.Serializable

/**
 * LrcLib 响应实体类
 * * 官方 API 文档参考: https://lrclib.net/docs
 *
 * @property id 唯一标识符
 * @property trackName 歌曲名称
 * @property artistName 艺术家名称
 * @property albumName 专辑名称
 * @property duration 歌曲时长（秒）
 * @property instrumental 是否为纯音乐
 * @property plainLyrics 无时间戳的纯文本歌词
 * @property syncedLyrics 带时间戳的 LRC 格式歌词
 */
@Serializable
data class LrcLibResponse(
    val id: Long,
    val trackName: String? = null,
    val artistName: String? = null,
    val albumName: String? = null,
    val duration: Double? = null,
    val instrumental: Boolean = false,
    val plainLyrics: String? = null,
    val syncedLyrics: String? = null
) {
    /**
     * 检查此响应是否包含可用的同步歌词
     */
    fun hasSyncedLyrics(): Boolean = !syncedLyrics.isNullOrBlank()
}