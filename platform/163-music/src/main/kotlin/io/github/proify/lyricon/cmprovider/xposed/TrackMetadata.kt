/*
 * Copyright 2026 Proify, Tomakino
 * Licensed under the Apache License, Version 2.0
 * http://www.apache.org/licenses/LICENSE-2.0
 */

package io.github.proify.lyricon.cmprovider.xposed

import android.media.MediaMetadata
import java.util.concurrent.ConcurrentHashMap

/** 单曲元数据。 */
data class TrackMetadata(
    val id: Long,
    val title: String?,
    val artist: String?,
    val duration: Long,
) {
    companion object {
        /** 从宿主元数据构造。 */
        fun from(mediaMetadata: MediaMetadata): TrackMetadata? {
            val id = MediaIdParser.parse(
                mediaMetadata.getString(MediaMetadata.METADATA_KEY_MEDIA_ID)
            ) ?: return null

            return TrackMetadata(
                id = id,
                title = mediaMetadata.getString(MediaMetadata.METADATA_KEY_TITLE),
                artist = mediaMetadata.getString(MediaMetadata.METADATA_KEY_ARTIST),
                duration = mediaMetadata.getLong(MediaMetadata.METADATA_KEY_DURATION),
            )
        }
    }
}

internal object TrackMetadataStore {
    private val entries = ConcurrentHashMap<Long, TrackMetadata>()

    fun put(metadata: TrackMetadata) {
        entries[metadata.id] = metadata
    }

    fun get(id: Long): TrackMetadata? = entries[id]
}
