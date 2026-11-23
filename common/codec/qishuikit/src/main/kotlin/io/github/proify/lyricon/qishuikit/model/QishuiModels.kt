/*
 * Copyright 2026 Proify, Tomakino
 * Licensed under the Apache License, Version 2.0
 * http://www.apache.org/licenses/LICENSE-2.0
 */

package io.github.proify.lyricon.qishuikit.model

import kotlinx.serialization.SerialName
import kotlinx.serialization.Serializable

@Serializable
data class QishuiH5Response(
    val lyric: QishuiH5Lyric? = null,
    @SerialName("seo_track") val seoTrack: QishuiH5SeoTrack? = null,
    @SerialName("track_player") val trackPlayer: QishuiH5TrackPlayer? = null
)

@Serializable
data class QishuiH5Lyric(
    val type: String? = null,
    val content: String? = null,
    @SerialName("lang_translations") val langTranslations: Map<String, QishuiTranslation>? = null
)

@Serializable
data class QishuiH5SeoTrack(
    val track: QishuiH5Track? = null
)

@Serializable
data class QishuiH5Track(
    val name: String? = null,
    val artists: List<QishuiH5Artist>? = null,
    val duration: Long? = null
)

@Serializable
data class QishuiH5Artist(
    val name: String? = null
)

@Serializable
data class QishuiH5TrackPlayer(
    @SerialName("media_id") val mediaId: String? = null,
    @SerialName("video_model") val videoModel: String? = null
)

@Serializable
data class QishuiTranslation(
    val content: String? = null,
    val type: String? = null
)
