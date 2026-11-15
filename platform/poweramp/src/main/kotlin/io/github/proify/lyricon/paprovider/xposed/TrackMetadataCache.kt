/*
 * Copyright 2026 Proify, Tomakino
 * Licensed under the Apache License, Version 2.0
 * http://www.apache.org/licenses/LICENSE-2.0
 */

package io.github.proify.lyricon.paprovider.xposed

import android.os.Bundle
import kotlinx.serialization.Serializable

object TrackMetadataCache {
    private val map = mutableMapOf<String, TrackMetadata>()

    fun save(metadata: Bundle): TrackMetadata? {
        val id = metadata.getLong("id", -1)
        if (id == -1L) return null

        val title = metadata.getString("title")
        val artist = metadata.getString("artist")
        val album = metadata.getString("album")
        val duration = metadata.getLong("durMs")
        val path = metadata.getString("path")

        val data = TrackMetadata(
            raw = metadata,
            id = id.toString(),
            title = title,
            artist = artist,
            album = album,
            duration = duration,
            path = path
        )
        map[id.toString()] = data
        return data
    }

    fun get(id: String): TrackMetadata? = map[id]
}

@Serializable
data class TrackMetadata(
    val raw: Bundle,
    val id: String,
    val title: String?,
    val artist: String?,
    val album: String?,
    val duration: Long,
    val path: String?,
)