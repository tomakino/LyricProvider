/*
 * Copyright 2026 Proify, Tomakino
 * Licensed under the Apache License, Version 2.0
 * http://www.apache.org/licenses/LICENSE-2.0
 */

package io.github.proify.lyricon.spotifyprovider.xposed

import android.media.MediaMetadata
import kotlinx.serialization.Serializable

/** 曲目元数据。 */
@Serializable
data class TrackMetadata(
    val id: String,
    val title: String?,
    val artist: String?,
    val duration: Long
) {
    companion object {

        /** 从宿主媒体元数据提取；缺少曲目 id 时返回 null。 */
        fun fromMediaMetadata(metadata: MediaMetadata): TrackMetadata? {
            val id = metadata.getString(MediaMetadata.METADATA_KEY_MEDIA_ID)?.run {
                substringAfter("spotify:track:", "")
            }
            if (id.isNullOrBlank()) return null

            return TrackMetadata(
                id = id,
                title = metadata.getString(MediaMetadata.METADATA_KEY_TITLE),
                artist = metadata.getString(MediaMetadata.METADATA_KEY_ARTIST),
                duration = metadata.getLong(MediaMetadata.METADATA_KEY_DURATION).let {
                    if (it == 0L) Long.MAX_VALUE else it
                }
            )
        }
    }
}
