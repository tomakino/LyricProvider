/*
 * Copyright 2026 Proify, Tomakino
 * Licensed under the Apache License, Version 2.0
 * http://www.apache.org/licenses/LICENSE-2.0
 */

package io.github.proify.lyricon.spotifyprovider.xposed.core

import android.content.Context
import com.highcapable.yukihookapi.hook.log.YLog
import io.github.proify.lyricon.lyric.model.Song
import io.github.proify.lyricon.spotifyprovider.xposed.Metadata
import io.github.proify.lyricon.spotifyprovider.xposed.api.NoFoundLyricException
import io.github.proify.lyricon.spotifyprovider.xposed.api.SpotifyApi
import io.github.proify.lyricon.spotifyprovider.xposed.cache.DiskCache
import io.github.proify.lyricon.spotifyprovider.xposed.util.toSongOrNull
import kotlinx.coroutines.CoroutineScope
import kotlinx.coroutines.Dispatchers
import kotlinx.coroutines.SupervisorJob
import kotlinx.coroutines.launch
import java.util.concurrent.ConcurrentHashMap

/**
 * 歌词获取管理器
 */
class LyricFetcher {

    companion object {
        private const val TAG = "LyricFetcher"
    }

    private val scope = CoroutineScope(Dispatchers.IO + SupervisorJob())
    private val downloadingIds = ConcurrentHashMap.newKeySet<String>()
    private var lastSong: Song? = null

    /**
     * 获取歌词
     */
    fun fetch(
        context: Context,
        metadata: Metadata,
        onSongReady: (Song) -> Unit
    ) {
        val (id, title, artist) = metadata

        // 1. 检查磁盘缓存
        val cachedResponse = DiskCache.get(context, id)
        if (cachedResponse != null) {
            YLog.debug(tag = TAG, msg = "磁盘缓存命中: $id")
            applyResponse(id, cachedResponse, onSongReady)
            return
        }

        // 2. 设置占位符
        val placeholder = Song(name = title, artist = artist)
        onSongReady(placeholder)

        // 3. 异步下载
        download(id, context, onSongReady)
    }

    private fun download(
        id: String,
        context: Context,
        onSongReady: (Song) -> Unit
    ) {
        if (!downloadingIds.add(id)) return

        scope.launch {
            try {
                YLog.debug(tag = TAG, msg = "开始下载歌词: $id")
                val response = SpotifyApi.fetchRawLyric(id)

                // 保存到缓存
                DiskCache.put(context, id, response)

                // 应用响应
                applyResponse(id, response, onSongReady)
            } catch (e: Exception) {
                handleDownloadError(id, e)
            } finally {
                downloadingIds.remove(id)
            }
        }
    }

    private fun applyResponse(
        id: String,
        response: ByteArray,
        onSongReady: (Song) -> Unit
    ) {
        val song = response.toSongOrNull(id)
        if (song != null && song != lastSong) {
            lastSong = song
            YLog.debug(tag = TAG, msg = "歌词已准备就绪: ${song.name}")
            onSongReady(song)
        }
    }

    private fun handleDownloadError(id: String, e: Exception) {
        when (e) {
            is NoFoundLyricException -> {
                YLog.debug(tag = TAG, msg = "未找到歌词: ${e.message}")
            }

            else -> {
                YLog.error(tag = TAG, msg = "下载歌词失败: $id", e = e)
            }
        }
    }
}