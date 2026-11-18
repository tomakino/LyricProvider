/*
 * Copyright 2026 Proify, Tomakino
 * Licensed under the Apache License, Version 2.0
 * http://www.apache.org/licenses/LICENSE-2.0
 */

package io.github.proify.lyricon.kgprovider.xposed

import io.github.proify.lyricon.lyric.model.RichLyricLine
import java.util.concurrent.ConcurrentHashMap

internal data class TrackMetadata(
    val title: String,
    val artist: String,
    val album: String,
    val duration: Long,
) {
    val id: String by lazy {
        "$title-$artist-$album-$duration".hashCode().toString()
    }
}

internal object TrackStore {

    private data class Track(
        val metadata: TrackMetadata? = null,
        val lyrics: List<RichLyricLine>? = null,
    )

    private val tracks = ConcurrentHashMap<String, Track>()

    fun putMetadata(metadata: TrackMetadata) {
        tracks.compute(metadata.id) { _, track -> Track(metadata, track?.lyrics) }
    }

    fun attachLyrics(id: String, lyrics: List<RichLyricLine>) {
        tracks.compute(id) { _, track -> Track(track?.metadata, lyrics) }
    }

    fun metadataOf(id: String): TrackMetadata? = tracks[id]?.metadata

    fun lyricsOf(id: String): List<RichLyricLine>? = tracks[id]?.lyrics
}
