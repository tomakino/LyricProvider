/*
 * Copyright 2026 Proify, Tomakino
 * Licensed under the Apache License, Version 2.0
 * http://www.apache.org/licenses/LICENSE-2.0
 */

package io.github.proify.lyricon.cmprovider.xposed

import io.github.proify.lyricon.yrckit.download.response.LyricResponse
import kotlinx.serialization.json.Json
import java.io.File
import java.io.FileOutputStream
import java.nio.file.AtomicMoveNotSupportedException
import java.nio.file.Files
import java.nio.file.StandardCopyOption

/** Reads and atomically replaces the existing, schema-compatible lyric cache files. */
internal class LyricCacheStore(private val json: Json) {
    fun read(file: File, expectedMusicId: Long): LocalLyricCache? {
        if (!file.isFile) return null
        val cache = runCatching {
            json.decodeFromString<LocalLyricCache>(file.readText(Charsets.UTF_8))
        }.getOrNull()

        if (cache == null || cache.musicId != expectedMusicId || !cache.hasUsableSource()) {
            file.delete()
            return null
        }
        return cache
    }

    fun write(file: File, cache: LocalLyricCache): Boolean {
        if (cache.musicId <= 0 || !cache.hasUsableSource()) return false
        val parent = file.absoluteFile.parentFile ?: return false
        if (!parent.exists() && !parent.mkdirs()) return false
        val temporary = File.createTempFile(".${file.name}.", ".tmp", parent)

        return try {
            val bytes = json.encodeToString(cache).toByteArray(Charsets.UTF_8)
            FileOutputStream(temporary).use { output ->
                output.write(bytes)
                output.fd.sync()
            }
            replace(temporary, file)
            true
        } catch (_: Exception) {
            false
        } finally {
            if (temporary.exists()) temporary.delete()
        }
    }

    private fun replace(source: File, destination: File) {
        try {
            Files.move(
                source.toPath(),
                destination.toPath(),
                StandardCopyOption.ATOMIC_MOVE,
                StandardCopyOption.REPLACE_EXISTING
            )
        } catch (_: AtomicMoveNotSupportedException) {
            Files.move(
                source.toPath(),
                destination.toPath(),
                StandardCopyOption.REPLACE_EXISTING
            )
        }
    }
}

internal fun LyricResponse.toValidCacheOrNull(musicId: Long): LocalLyricCache? {
    if (code != 200 || musicId <= 0) return null
    val cache = LocalLyricCache(
        musicId = musicId,
        lrc = lrc?.lyric,
        lrcTranslateLyric = tlyric?.lyric,
        yrc = yrc?.lyric,
        yrcTranslateLyric = ytlrc?.lyric,
        pureMusic = pureMusic,
        roma = romalrc?.lyric
    )
    return cache.takeIf(LocalLyricCache::hasUsableSource)
}

internal fun LocalLyricCache.hasUsableSource(): Boolean =
    pureMusic || !lrc.isNullOrBlank() || !yrc.isNullOrBlank()
