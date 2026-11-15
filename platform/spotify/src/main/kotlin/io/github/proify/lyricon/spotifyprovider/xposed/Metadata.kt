/*
 * Copyright 2026 Proify, Tomakino
 * Licensed under the Apache License, Version 2.0
 * http://www.apache.org/licenses/LICENSE-2.0
 */

package io.github.proify.lyricon.spotifyprovider.xposed

import android.media.MediaMetadata
import kotlinx.serialization.Serializable

/**
 * 歌曲元数据
 */
@Serializable
data class Metadata(
    val id: String,
    val title: String?,
    val artist: String?,
    val duration: Long
) {
    companion object {
        /**
         * 从 MediaMetadata 创建 Metadata
         */
        fun fromMediaMetadata(metadata: MediaMetadata): Metadata? {
            val id = metadata.getString(MediaMetadata.METADATA_KEY_MEDIA_ID)?.run {
                substringAfter("spotify:track:", "")
            }
            if (id.isNullOrBlank()) return null

            val title = metadata.getString(MediaMetadata.METADATA_KEY_TITLE)
            val artist = metadata.getString(MediaMetadata.METADATA_KEY_ARTIST)
            val duration = metadata.getLong(MediaMetadata.METADATA_KEY_DURATION)

            return Metadata(
                id = id,
                title = title,
                artist = artist,
                duration = if (duration == 0L) Long.MAX_VALUE else duration
            )
        }
    }
}