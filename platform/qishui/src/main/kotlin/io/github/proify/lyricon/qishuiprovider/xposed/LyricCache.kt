/*
 * Copyright 2026 Proify, Tomakino
 * Licensed under the Apache License, Version 2.0
 * http://www.apache.org/licenses/LICENSE-2.0
 */

package io.github.proify.lyricon.qishuiprovider.xposed

import android.content.Context
import io.github.proify.lyricon.qishuikit.model.QishuiLyric
import kotlinx.serialization.json.Json
import java.io.File

/**
 * 歌词磁盘缓存。
 * @param context 宿主应用上下文，用于定位缓存目录。
 * @param json JSON 编解码器。
 */
class LyricCache(
    context: Context,
    private val json: Json = Json { ignoreUnknownKeys = true }
) {

    /** 缓存根目录。 */
    private val root: File = context.cacheDir.resolve("qishui_lyric")

    /**
     * 计算指定曲目的缓存文件。
     * @param id 曲目 ID。
     * @return 缓存文件。
     */
    private fun file(id: String): File = root.resolve("$id.json")

    /**
     * 读取指定曲目的缓存歌词。
     * @param id 曲目 ID。
     * @return 缓存歌词；未命中或内容损坏时为 null。
     */
    fun load(id: String): QishuiLyric? = runCatching {
        val target = file(id)
        if (!target.exists()) return null
        json.decodeFromString<QishuiLyric>(target.readText())
    }.getOrNull()

    /**
     * 写入指定曲目的缓存歌词。
     * @param lyric 待缓存歌词。
     */
    fun save(lyric: QishuiLyric) {
        runCatching {
            if (!root.exists()) root.mkdirs()
            file(lyric.id).writeText(json.encodeToString(lyric))
        }
    }
}
