/*
 * Copyright 2026 Proify, Tomakino
 * Licensed under the Apache License, Version 2.0
 * http://www.apache.org/licenses/LICENSE-2.0
 */

package io.github.proify.lyricon.amprovider.xposed

import android.content.Context
import com.highcapable.yukihookapi.hook.log.YLog
import io.github.proify.extensions.deflate
import io.github.proify.extensions.inflate
import io.github.proify.extensions.json
import io.github.proify.lyricon.amprovider.xposed.model.AppleSong
import kotlinx.serialization.ExperimentalSerializationApi
import java.io.File
import java.util.Locale

/** 歌曲磁盘缓存：按语言目录存放 deflate 压缩的 JSON 文件。 */
object DiskSongManager {
    private var baseDir: File? = null

    fun initialize(context: Context) {
        if (baseDir != null) return
        val languageTag = Locale.getDefault().toLanguageTag()
        baseDir = File(context.filesDir, "lyricon/songs/$languageTag").apply { mkdirs() }
    }

    fun save(appleSong: AppleSong): Boolean {
        val id = appleSong.adamId
        if (id.isNullOrBlank()) return false

        val success = runCatching {
            val file = getFile(id)
            file.parentFile?.mkdirs()
            file.writeBytes(json.encodeToString(appleSong).toByteArray(Charsets.UTF_8).deflate())
        }.isSuccess

        if (success) {
            YLog.debug("DiskSongManager: Saved song $id")
        } else {
            YLog.error("DiskSongManager: Failed to save song $id")
        }
        return success
    }

    @OptIn(ExperimentalSerializationApi::class)
    fun load(id: String): AppleSong? {
        val song = runCatching {
            getFile(id)
                .takeIf { it.exists() }
                ?.readBytes()
                ?.inflate()
                ?.let { json.decodeFromString<AppleSong>(it.toString(Charsets.UTF_8)) }
        }.getOrNull()

        if (song == null) {
            YLog.debug("DiskSongManager: No cached song for $id")
        }
        return song
    }

    private fun getFile(id: String): File = File(baseDir, "$id.json.gz")
}
