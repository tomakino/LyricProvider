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

class LyricCache(
    context: Context,
    private val json: Json = Json { ignoreUnknownKeys = true }
) {

    private val root: File = context.cacheDir.resolve("qishui_lyric")

    private fun file(id: String): File = root.resolve("$id.json")

    fun load(id: String): QishuiLyric? = runCatching {
        val target = file(id)
        if (!target.exists()) return null
        json.decodeFromString<QishuiLyric>(target.readText())
    }.getOrNull()

    fun save(lyric: QishuiLyric) {
        runCatching {
            if (!root.exists()) root.mkdirs()
            file(lyric.id).writeText(json.encodeToString(lyric))
        }
    }
}
