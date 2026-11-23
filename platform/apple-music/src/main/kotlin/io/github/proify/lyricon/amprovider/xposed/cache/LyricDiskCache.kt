/*
 * Copyright 2026 Proify, Tomakino
 * Licensed under the Apache License, Version 2.0
 * http://www.apache.org/licenses/LICENSE-2.0
 */

package io.github.proify.lyricon.amprovider.xposed.cache

import android.content.Context
import com.highcapable.yukihookapi.hook.log.YLog
import io.github.proify.extensions.deflate
import io.github.proify.extensions.inflate
import io.github.proify.extensions.json
import io.github.proify.lyricon.lyric.model.Song
import java.io.File
import java.util.Locale

/** 歌词磁盘缓存：deflate JSON，按语言分目录。 */
object LyricDiskCache {

    private const val CACHE_VERSION = 1

    private var baseDir: File? = null

    fun initialize(context: Context) {
        if (baseDir != null) return
        val languageTag = Locale.getDefault().toLanguageTag()
        baseDir = File(context.filesDir, "lyricon/lyrics/v$CACHE_VERSION/$languageTag")
            .apply { mkdirs() }
    }

    fun load(id: String): Song? = runCatching {
        val file = fileOf(id).takeIf { it.exists() } ?: return null
        json.decodeFromString<Song>(
            file.readBytes().inflate().toString(Charsets.UTF_8)
        )
    }.onFailure {
        YLog.warn(tag = "AMProvider", msg = "LyricDiskCache: 读取失败 $id")
    }.getOrNull()

    fun save(song: Song): Boolean = runCatching {
        val id = song.id ?: return false
        val file = fileOf(id)
        file.parentFile?.mkdirs()
        val encoded = json.encodeToString(song)
        file.writeBytes(encoded.toByteArray(Charsets.UTF_8).deflate())
    }.onFailure {
        YLog.error(tag = "AMProvider", msg = "LyricDiskCache: 写入失败")
    }.isSuccess

    fun has(id: String): Boolean = fileOf(id).exists()

    private fun fileOf(id: String): File =
        File(baseDir, "$id.json.gz")

}