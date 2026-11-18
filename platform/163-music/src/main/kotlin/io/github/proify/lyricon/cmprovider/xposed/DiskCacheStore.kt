/*
 * Copyright 2026 Proify, Tomakino
 * Licensed under the Apache License, Version 2.0
 * http://www.apache.org/licenses/LICENSE-2.0
 */

package io.github.proify.lyricon.cmprovider.xposed

import io.github.proify.extensions.deflate
import io.github.proify.extensions.inflate
import io.github.proify.extensions.json
import io.github.proify.lyricon.lyric.model.Song
import java.io.File

object DiskCacheStore {

    private const val CACHE_VERSION = 1

    private var baseDir: File? = null

    fun init(dataDir: String) {
        baseDir = File(
            dataDir,
            "lyricon/lyrics/v$CACHE_VERSION"
        )
    }

    fun has(id: Long): Boolean {
        return fileOf(id.toString())?.exists() ?: false
    }

    fun put(id: Long, song: Song) {
        val file = fileOf(id.toString()) ?: return
        file.parentFile?.mkdirs()
        file.writeBytes(
            json.encodeToString(song)
                .toByteArray()
                .inflate()
        )
    }

    fun load(id: Long): Song? = runCatching {
        val file = fileOf(id.toString()) ?: return null
        if (!file.exists()) {
            null
        } else {
            json.decodeFromString(
                file.readBytes()
                    .deflate()
                    .toString(Charsets.UTF_8)
            )
        }
    }.getOrNull()

    private fun fileOf(id: String): File? =
        if (baseDir == null) null
        else File(baseDir, "$id.json.gz")

}