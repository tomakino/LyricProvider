/*
 * Copyright 2026 Proify, Tomakino
 * Licensed under the Apache License, Version 2.0
 * http://www.apache.org/licenses/LICENSE-2.0
 */

package io.github.proify.lyricon.qishuikit.model

import kotlinx.serialization.SerialName
import kotlinx.serialization.Serializable

/**
 * 汽水 SEO 曲目接口响应。
 * @property lyric 歌词数据。
 * @property seoTrack 曲目信息。
 * @property trackPlayer 播放信息。
 */
@Serializable
data class QishuiH5Response(
    val lyric: QishuiH5Lyric? = null,
    @SerialName("seo_track") val seoTrack: QishuiH5SeoTrack? = null,
    @SerialName("track_player") val trackPlayer: QishuiH5TrackPlayer? = null
)

/**
 * 接口返回的歌词数据。
 * @property type 歌词格式，`krc` 或 `lrc`。
 * @property content 歌词原文。
 * @property langTranslations 带格式标记的翻译。
 * @property translations 纯 LRC 文本的翻译。
 */
@Serializable
data class QishuiH5Lyric(
    val type: String? = null,
    val content: String? = null,
    @SerialName("lang_translations") val langTranslations: Map<String, QishuiTranslation>? = null,
    val translations: Map<String, String>? = null
)

/**
 * 曲目信息包装。
 * @property track 曲目。
 */
@Serializable
data class QishuiH5SeoTrack(
    val track: QishuiH5Track? = null
)

/**
 * 曲目信息。
 * @property name 歌名。
 * @property artists 歌手列表。
 * @property duration 时长，毫秒。
 */
@Serializable
data class QishuiH5Track(
    val name: String? = null,
    val artists: List<QishuiH5Artist>? = null,
    val duration: Long? = null
)

/**
 * 歌手信息。
 * @property name 歌手名。
 */
@Serializable
data class QishuiH5Artist(
    val name: String? = null
)

/**
 * 播放信息。
 * @property mediaId 媒体 ID。
 * @property videoModel 播放模型 JSON。
 */
@Serializable
data class QishuiH5TrackPlayer(
    @SerialName("media_id") val mediaId: String? = null,
    @SerialName("video_model") val videoModel: String? = null
)

/**
 * 带格式标记的翻译。
 * @property content 翻译原文。
 * @property type 翻译格式，`krc` 或 `lrc`。
 */
@Serializable
data class QishuiTranslation(
    val content: String? = null,
    val type: String? = null
)
