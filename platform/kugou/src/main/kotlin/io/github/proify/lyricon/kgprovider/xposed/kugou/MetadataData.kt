/*
 * Copyright 2026 Proify, Tomakino
 * Licensed under the Apache License, Version 2.0
 * http://www.apache.org/licenses/LICENSE-2.0
 */

package io.github.proify.lyricon.kgprovider.xposed.kugou

class MetadataData(
    val title: String,
    val artist: String,
    val album: String,
    val duration: Long
) {
    val generateId by lazy {
        "$title-$artist-$album-$duration".hashCode()
            .toString()
    }
}