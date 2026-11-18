/*
 * Copyright 2026 Proify, Tomakino
 * Licensed under the Apache License, Version 2.0
 * http://www.apache.org/licenses/LICENSE-2.0
 */

package io.github.proify.lyricon.spotifyprovider.xposed

import android.content.Context
import io.github.proify.extensions.json
import io.github.proify.lyricon.lyric.model.Song
import kotlinx.serialization.ExperimentalSerializationApi
import kotlinx.serialization.json.decodeFromStream
import kotlinx.serialization.json.encodeToStream
import java.io.File
import java.util.Locale

/** 歌词磁盘缓存。 */
object SongDiskCacheManager {

    @OptIn(ExperimentalSerializationApi::class)
    fun put(context: Context, id: String, song: Song) {
        val file = getFile(context, id)
        file.parentFile?.mkdirs()
        file.outputStream().use {
            json.encodeToStream(song, it)
        }
    }

    @OptIn(ExperimentalSerializationApi::class)
    fun get(context: Context, id: String): Song? {
        val file = getFile(context, id)
        return if (file.exists()) {
            file.inputStream().use {
                json.decodeFromStream(Song.serializer(), it)
            }
        } else null
    }

    private fun getFile(context: Context, id: String): File =
        File(getDirectory(context), "$id.json")

    private fun getDirectory(context: Context): File =
        File(
            context.cacheDir,
            "lyricon/lyric/v2/${Locale.getDefault().toLanguageTag()}"
        )
}