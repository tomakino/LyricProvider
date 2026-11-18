/*
 * Copyright 2026 Proify, Tomakino
 * Licensed under the Apache License, Version 2.0
 * http://www.apache.org/licenses/LICENSE-2.0
 */

package io.github.proify.lyricon.spotifyprovider.xposed

import android.content.Context
import com.highcapable.yukihookapi.hook.log.YLog
import io.github.proify.lyricon.lyric.model.Song
import io.github.proify.lyricon.spotifyprovider.xposed.api.SpotifyApi
import io.github.proify.lyricon.spotifyprovider.xposed.api.SpotifyException
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
        metadata: TrackMetadata,
        onSongReady: (Song) -> Unit
    ) {
        // 1. 检查磁盘缓存
        val cached = SongDiskCacheManager.get(context, metadata.id)
        if (cached != null) {
            YLog.debug(tag = TAG, msg = "磁盘缓存命中: ${metadata.id}")
            applySong(cached, onSongReady)
            return
        }

        // 2. 设置占位符
        onSongReady(Song(name = metadata.title, artist = metadata.artist))

        // 3. 异步下载
        download(metadata, context, onSongReady)
    }

    private fun download(
        metadata: TrackMetadata,
        context: Context,
        onSongReady: (Song) -> Unit
    ) {
        if (!downloadingIds.add(metadata.id)) return

        scope.launch {
            try {
                YLog.debug(tag = TAG, msg = "开始下载歌词: ${metadata.id}")
                val bytes = SpotifyApi.fetchRawLyric(metadata.id)
                val song = bytes.toSongOrNull(metadata)
                if (song != null) {
                    SongDiskCacheManager.put(context, metadata.id, song)
                    applySong(song, onSongReady)
                }
            } catch (e: Exception) {
                handleDownloadError(metadata.id, e)
            } finally {
                downloadingIds.remove(metadata.id)
            }
        }
    }

    private fun applySong(song: Song, onSongReady: (Song) -> Unit) {
        if (song != lastSong) {
            lastSong = song
            YLog.debug(tag = TAG, msg = "歌词已准备就绪: ${song.name}")
            onSongReady(song)
        }
    }

    private fun handleDownloadError(id: String, e: Exception) {
        when (e) {
            is SpotifyException.ApiException.NoLyricFoundException -> {
                YLog.debug(tag = TAG, msg = "未找到歌词: ${e.message}")
            }

            else -> {
                YLog.error(tag = TAG, msg = "下载歌词失败: $id", e = e)
            }
        }
    }
}
