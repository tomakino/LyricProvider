/*
 * Copyright 2026 Proify, Tomakino
 * Licensed under the Apache License, Version 2.0
 * http://www.apache.org/licenses/LICENSE-2.0
 */

package io.github.proify.lyricon.cmprovider.xposed

import android.media.MediaMetadata
import android.util.Log
import kotlinx.serialization.Serializable

object MediaMetadataCache {
    private const val TAG = "MetadataCache"
    private val map = mutableMapOf<Long, Metadata>()

    fun save(metadata: MediaMetadata): Metadata? {
        val mediaId = metadata.getString(MediaMetadata.METADATA_KEY_MEDIA_ID)
        if (mediaId == null) {
            Log.w(TAG, "MEDIA_ID is null — metadata will be ignored")
            return null
        }
        val id = mediaId.toLongOrNull() ?: mediaId.hashCode().toLong()
        val numeric = mediaId.toLongOrNull() != null
        Log.d(TAG, "MEDIA_ID=$mediaId ($id) numeric=$numeric")

        if (map.containsKey(id)) {
            return map[id]
        }
        val title = metadata.getString(MediaMetadata.METADATA_KEY_TITLE)
        val artist = metadata.getString(MediaMetadata.METADATA_KEY_ARTIST)
        val duration = metadata.getLong(MediaMetadata.METADATA_KEY_DURATION)

        val data = Metadata(id, title, artist, duration)
        map[id] = data
        return data
    }

    fun get(id: Long): Metadata? = map[id]

    /** 为云盘等本地来源的歌曲创建占位 metadata */
    fun savePlaceholder(id: Long) {
        if (!map.containsKey(id)) {
            map[id] = Metadata(id, null, null, 0)
            Log.d(TAG, "Placeholder metadata created for id=$id")
        }
    }
}

@Serializable
data class Metadata(
    val id: Long,
    val title: String?,
    val artist: String?,
    val duration: Long
)