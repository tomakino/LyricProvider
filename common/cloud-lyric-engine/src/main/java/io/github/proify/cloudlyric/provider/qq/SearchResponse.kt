/*
 * Copyright 2026 Proify, Tomakino
 * Licensed under the Apache License, Version 2.0
 * http://www.apache.org/licenses/LICENSE-2.0
 */

@file:Suppress("unused")

package io.github.proify.cloudlyric.provider.qq

import kotlinx.serialization.SerialName
import kotlinx.serialization.Serializable
import kotlinx.serialization.json.JsonElement

// 通用外层结构
@Serializable
data class SearchResponse(val req: ReqData)

@Serializable
data class ReqData(val data: BodyContainer)

@Serializable
data class BodyContainer(val body: SearchBody)

@Serializable
data class SearchBody(
    val song: SongResult? = null,
    val album: JsonElement? = null,
    val songlist: JsonElement? = null
)

@Serializable
data class SongResult(
    @SerialName("list") val list: List<SongItem> = emptyList()
)

@Serializable
data class SongItem(
    val name: String,
    val subtitle: String? = null,
    @SerialName("singer") val singers: List<Singer> = emptyList(),
    @SerialName("album") val album: AlbumInfo? = null
)

@Serializable
data class Singer(val name: String)

@Serializable
data class AlbumInfo(val name: String)