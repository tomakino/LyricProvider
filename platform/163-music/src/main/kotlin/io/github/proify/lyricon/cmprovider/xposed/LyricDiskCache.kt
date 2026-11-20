/*
 * Copyright 2026 Proify, Tomakino
 * Licensed under the Apache License, Version 2.0
 * http://www.apache.org/licenses/LICENSE-2.0
 */

package io.github.proify.lyricon.cmprovider.xposed

import com.highcapable.yukihookapi.hook.log.YLog
import io.github.proify.extensions.deflate
import io.github.proify.extensions.inflate
import io.github.proify.extensions.json
import io.github.proify.lyricon.lyric.model.Song
import java.io.File

/** 歌词磁盘缓存：按歌曲 id 存压缩 JSON。 */
internal object LyricDiskCache {

    private const val TAG = "LyricDiskCache"
    private const val CACHE_VERSION = 1

    @Volatile
    private var baseDir: File? = null

    fun init(dataDir: String) {
        baseDir = File(dataDir, "lyricon/lyrics/v$CACHE_VERSION")
    }

    fun load(id: Long): Song? = runCatching {
        val file = fileOf(id) ?: return@runCatching null
        if (!file.isFile) return@runCatching null

        json.decodeFromString<Song>(
            file.readBytes().deflate().toString(Charsets.UTF_8)
        )
    }.onFailure {
        YLog.warn(tag = TAG, msg = "Lyric cache read failed: $id", e = it)
    }.getOrNull()

    fun put(id: Long, song: Song) {
        runCatching {
            val file = fileOf(id) ?: return@runCatching
            file.parentFile?.mkdirs()
            file.writeBytes(
                json.encodeToString(song).toByteArray().inflate()
            )
        }.onFailure {
            YLog.warn(tag = TAG, msg = "Lyric cache write failed: $id", e = it)
        }
    }

    private fun fileOf(id: Long): File? {
        val dir = baseDir ?: return null
        return File(dir, "$id.json.gz")
    }
}
