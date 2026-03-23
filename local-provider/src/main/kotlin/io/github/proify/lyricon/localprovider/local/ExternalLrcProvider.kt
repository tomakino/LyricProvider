/*
 * Copyright 2026 Proify, Tomakino
 * Licensed under the Apache License, Version 2.0
 * http://www.apache.org/licenses/LICENSE-2.0
 */

package io.github.proify.lyricon.localprovider.local

import io.github.proify.lrckit.EnhanceLrcParser
import io.github.proify.lyricon.localprovider.model.LyricsProvider
import io.github.proify.lyricon.localprovider.model.LyricsResult
import io.github.proify.lyricon.localprovider.xposed.LocalProvider
import io.github.proify.lyricon.localprovider.xposed.PathManager
import kotlinx.coroutines.Dispatchers
import kotlinx.coroutines.withContext
import java.io.File
import java.util.concurrent.ConcurrentHashMap
import java.util.concurrent.atomic.AtomicBoolean

class ExternalLrcProvider : LyricsProvider {
    override val id = "ExternalLrc"

    private data class LyricCacheEntry(val filePath: String, val lastModified: Long, val result: LyricsResult)
    private val cache = ConcurrentHashMap<String, LyricCacheEntry>()
    private val isCacheInitialized = AtomicBoolean(false)

    override suspend fun search(
        query: String?,
        trackName: String?,
        artistName: String?,
        albumName: String?,
        limit: Int
    ): List<LyricsResult> = emptyList()

    suspend fun searchByAudioFile(
        audioFilePath: String,
        trackName: String?,
        artistName: String?,
        albumName: String?
    ): LyricsResult? = withContext(Dispatchers.IO) {
        println("ExternalLrcProvider: searchByAudioFile: path=$audioFilePath, track=$trackName, artist=$artistName")

        val audioFile = File(audioFilePath)
        if (!audioFile.exists()) return@withContext null

        val baseName = audioFile.nameWithoutExtension
        val dir = audioFile.parentFile ?: return@withContext null

        val lrcFile = File(dir, "$baseName.lrc")
        if (lrcFile.exists()) {
            val result = parseLrcFileForced(lrcFile, trackName, artistName, albumName)
            if (result != null) {
                println("ExternalLrcProvider: 使用同名文件: ${lrcFile.absolutePath}")
                return@withContext result
            }
        }

        println("ExternalLrcProvider: 未找到同目录歌词")
        return@withContext null
    }

    suspend fun searchByMetadata(
        trackName: String?,
        artistName: String?,
        albumName: String?
    ): LyricsResult? = withContext(Dispatchers.IO) {
        println("ExternalLrcProvider: searchByMetadata: track=$trackName, artist=$artistName")

        if (trackName.isNullOrBlank() || trackName.length < 3) {
            println("ExternalLrcProvider: 标题过短或为空，跳过搜索")
            return@withContext null
        }

        val cleanTitle = trackName.replace(Regex("[\\p{Punct}\\s]"), "").lowercase()
        if (cleanTitle.isEmpty()) return@withContext null

        val cached = cache[cleanTitle]
        if (cached != null) {
            val file = File(cached.filePath)
            if (file.exists() && file.lastModified() == cached.lastModified) {
                println("ExternalLrcProvider: 从缓存命中: ${cached.filePath}")
                return@withContext cached.result
            } else {
                cache.remove(cleanTitle)
            }
        }

        ensureCacheInitialized()
        cache[cleanTitle]?.let {
            val file = File(it.filePath)
            if (file.exists() && file.lastModified() == it.lastModified) {
                println("ExternalLrcProvider: 扫描后缓存命中: ${it.filePath}")
                return@withContext it.result
            }
        }

        println("ExternalLrcProvider: 未找到匹配的歌词")
        return@withContext null
    }

    private suspend fun ensureCacheInitialized() = withContext(Dispatchers.IO) {
        if (isCacheInitialized.get()) return@withContext
        val context = LocalProvider.appContext ?: return@withContext
        val customPaths = PathManager.getPaths(context)

        for (customPath in customPaths) {
            val customDir = File(customPath)
            if (!customDir.isDirectory) continue

            customDir.walkTopDown().maxDepth(3).forEach { file ->
                if (file.isFile && file.extension.equals("lrc", ignoreCase = true)) {
                    val content = runCatching { file.readText() }.getOrNull()
                    if (content.isNullOrBlank()) return@forEach
                    val doc = EnhanceLrcParser.parse(content)
                    val fileTitle = doc.metadata["ti"]?.trim()
                    if (fileTitle.isNullOrBlank()) return@forEach
                    val cleanFileTitle = fileTitle.replace(Regex("[\\p{Punct}\\s]"), "").lowercase()
                    if (cleanFileTitle.isEmpty()) return@forEach

                    val richLines = doc.lines.filter { line ->
                        val text = line.text ?: ""
                        !text.contains("作曲", ignoreCase = true) &&
                                !text.contains("作词", ignoreCase = true) &&
                                !text.contains("编曲", ignoreCase = true)
                    }
                    if (richLines.isEmpty()) return@forEach

                    val result = LyricsResult(
                        trackName = fileTitle,
                        artistName = doc.metadata["ar"],
                        albumName = doc.metadata["al"],
                        rich = richLines
                    )
                    cache[cleanFileTitle] = LyricCacheEntry(file.absolutePath, file.lastModified(), result)
                    println("ExternalLrcProvider: 缓存歌词: $cleanFileTitle -> ${file.absolutePath}")
                }
            }
        }
        isCacheInitialized.set(true)
    }

    private suspend fun parseLrcFileForced(
        file: File,
        fallbackTitle: String?,
        fallbackArtist: String?,
        fallbackAlbum: String?
    ): LyricsResult? = withContext(Dispatchers.IO) {
        val lrcContent = runCatching { file.readText() }.getOrNull()
        if (lrcContent.isNullOrBlank()) return@withContext null

        val enhanceDoc = EnhanceLrcParser.parse(lrcContent)
        val richLines = enhanceDoc.lines
        if (richLines.isEmpty()) return@withContext null

        richLines.forEach { line ->
            if (line.translation == null && line.secondary != null) {
                line.translation = line.secondary
            }
        }

        LyricsResult(
            trackName = fallbackTitle ?: enhanceDoc.metadata["ti"],
            artistName = fallbackArtist ?: enhanceDoc.metadata["ar"],
            albumName = fallbackAlbum,
            rich = richLines
        )
    }

    private suspend fun parseLrcFileWithMetadata(
        file: File,
        expectedTitle: String?,
        expectedArtist: String?,
        expectedAlbum: String?
    ): LyricsResult? = withContext(Dispatchers.IO) {
        val lrcContent = runCatching { file.readText() }.getOrNull() ?: return@withContext null
        val enhanceDoc = EnhanceLrcParser.parse(lrcContent)
        val richLines = enhanceDoc.lines

        val fileTitle = enhanceDoc.metadata["ti"]?.trim()
        val fileArtist = enhanceDoc.metadata["ar"]?.trim()

        if (expectedTitle != null && fileTitle != null) {
            if (!isSimilar(expectedTitle, fileTitle)) return@withContext null
        }
        if (expectedArtist != null && fileArtist != null) {
            if (!isSimilar(expectedArtist, fileArtist)) return@withContext null
        }

        richLines.forEach { line ->
            if (line.translation == null && line.secondary != null) {
                line.translation = line.secondary
            }
        }
        if (richLines.isEmpty()) return@withContext null

        LyricsResult(
            trackName = expectedTitle ?: fileTitle,
            artistName = expectedArtist ?: fileArtist,
            albumName = expectedAlbum,
            rich = richLines
        )
    }

    private fun isSimilar(s1: String, s2: String): Boolean {
        val clean1 = s1.replace(Regex("[\\p{Punct}\\s]"), "").lowercase()
        val clean2 = s2.replace(Regex("[\\p{Punct}\\s]"), "").lowercase()
        return clean1.contains(clean2) || clean2.contains(clean1)
    }
}