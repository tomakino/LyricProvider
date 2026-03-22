/*
 * Copyright 2026 Proify, Tomakino
 * Licensed under the Apache License, Version 2.0
 * http://www.apache.org/licenses/LICENSE-2.0
 */

package io.github.proify.lyricon.localprovider.xposed

import android.net.Uri
import com.kyant.taglib.TagLib
import io.github.proify.lyricon.localprovider.model.LyricsResult
import io.github.proify.lyricon.localprovider.lrckit.EnhanceLrcParser
import io.github.proify.lyricon.lyric.model.RichLyricLine
import kotlinx.coroutines.Dispatchers
import kotlinx.coroutines.withContext
import java.io.File

object EmbeddedLyricsProvider {

    private val lyricTagRegex by lazy { Regex("(?i)\\b(LYRICS|LYRICS\\d*|USLT)\\b") }

    suspend fun searchByAudioFile(
        audioFilePath: String,
        trackName: String?,
        artistName: String?,
        albumName: String?
    ): LyricsResult? = withContext(Dispatchers.IO) {
        println("EmbeddedLyricsProvider: 尝试读取内嵌歌词: $audioFilePath")

        val audioFile = File(audioFilePath)
        if (!audioFile.exists()) {
            println("EmbeddedLyricsProvider: 音频文件不存在")
            return@withContext null
        }

        val uri = Uri.fromFile(audioFile)
        val lyrics = extractLyricFromTag(uri) ?: run {
            println("EmbeddedLyricsProvider: 未找到内嵌歌词")
            return@withContext null
        }

        println("EmbeddedLyricsProvider: 找到内嵌歌词，长度 ${lyrics.length} 字节")
        val richLines = parseLyrics(lyrics)
        if (richLines.isEmpty()) {
            println("EmbeddedLyricsProvider: 内嵌歌词解析后为空")
            return@withContext null
        }

        richLines.forEach { line ->
            if (line.translation == null && line.secondary != null) {
                line.translation = line.secondary
            }
        }

        LyricsResult(
            trackName = trackName,
            artistName = artistName,
            albumName = albumName,
            rich = richLines
        ).also {
            println("EmbeddedLyricsProvider: 成功返回内嵌歌词结果")
        }
    }

    private fun extractLyricFromTag(uri: Uri): String? {
        val context = LocalProvider.appContext ?: return null
        return try {
            context.contentResolver.openFileDescriptor(uri, "r")?.use { pfd ->
                TagLib.getMetadata(pfd.detachFd())?.let { metadata ->
                    metadata.propertyMap.entries.firstOrNull { (key, _) ->
                        lyricTagRegex.matches(key)
                    }?.value?.firstOrNull()
                }
            }
        } catch (e: Exception) {
            println("EmbeddedLyricsProvider: 读取标签失败: ${e.message}")
            null
        }
    }

    private fun parseLyrics(raw: String): List<RichLyricLine> {
        val doc = EnhanceLrcParser.parse(raw)
        return doc.lines
    }
}