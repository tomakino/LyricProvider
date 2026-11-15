/*
 * Copyright 2026 Proify, Tomakino
 * Licensed under the Apache License, Version 2.0
 * http://www.apache.org/licenses/LICENSE-2.0
 */

package io.github.proify.lyricon.amprovider.xposed

import android.media.MediaMetadata
import kotlinx.serialization.Serializable

/** 歌曲元数据缓存：在 MediaSession 更新时记录，供占位符与解析回填使用。 */
object MediaMetadataCache {
    private const val MAX_SIZE = 100

    private val metadataCache = object : LinkedHashMap<String, Metadata>(16, 0.75f, true) {
        override fun removeEldestEntry(eldest: MutableMap.MutableEntry<String, Metadata>?): Boolean =
            size > MAX_SIZE
    }

    /** 缓存并返回新的元数据；MediaId 缺失时返回 null。 */
    fun putAndGet(metadata: MediaMetadata): Metadata? {
        val mediaId = metadata.getString(MediaMetadata.METADATA_KEY_MEDIA_ID)
        if (mediaId.isNullOrBlank()) return null

        val newMetadata = Metadata(
            id = mediaId,
            title = metadata.getString(MediaMetadata.METADATA_KEY_TITLE),
            artist = metadata.getString(MediaMetadata.METADATA_KEY_ARTIST),
            duration = metadata.getLong(MediaMetadata.METADATA_KEY_DURATION)
        )
        metadataCache[mediaId] = newMetadata
        return newMetadata
    }

    fun getMetadataById(mediaId: String): Metadata? = metadataCache[mediaId]

    @Serializable
    data class Metadata(
        val id: String,
        val title: String?,
        val artist: String?,
        val duration: Long
    )
}
