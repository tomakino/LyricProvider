/*
 * Copyright 2026 Proify, Tomakino
 * Licensed under the Apache License, Version 2.0
 * http://www.apache.org/licenses/LICENSE-2.0
 */

package io.github.proify.lyricon.bodianprovider.xposed

import android.media.MediaMetadata
import kotlinx.serialization.Serializable
import java.util.concurrent.ConcurrentHashMap

/**
 * MediaSession 元数据缓存。
 *
 * 波点音乐切歌时 setMetadata 与真正拿到歌词的时机并不一致，
 * 这里按 mediaId 缓存一份，供歌词链路补齐 曲名 / 歌手 / 专辑 / 时长。
 */
object MediaMetadataCache {

    private val map = ConcurrentHashMap<String, Metadata>()

    fun save(metadata: MediaMetadata): Metadata? {
        val id = metadata.getString(MediaMetadata.METADATA_KEY_MEDIA_ID)
        if (id.isNullOrBlank()) return null

        val cached = map[id]
        if (cached != null) return cached

        val data = Metadata(
            id = id,
            title = metadata.getString(MediaMetadata.METADATA_KEY_TITLE),
            artist = metadata.getString(MediaMetadata.METADATA_KEY_ARTIST)
                ?: metadata.getString(MediaMetadata.METADATA_KEY_ALBUM_ARTIST),
            album = metadata.getString(MediaMetadata.METADATA_KEY_ALBUM),
            duration = metadata.getLong(MediaMetadata.METADATA_KEY_DURATION)
        )
        map[id] = data
        return data
    }

    fun get(id: String?): Metadata? = id?.let { map[it] }
}

@Serializable
data class Metadata(
    val id: String,
    val title: String? = null,
    val artist: String? = null,
    val album: String? = null,
    val duration: Long = 0L
)
