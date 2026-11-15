/*
 * Copyright 2026 Proify, Tomakino
 * Licensed under the Apache License, Version 2.0
 * http://www.apache.org/licenses/LICENSE-2.0
 */

package io.github.proify.lyricon.amprovider.xposed.playback

import com.highcapable.yukihookapi.hook.log.YLog
import io.github.proify.lyricon.amprovider.xposed.cache.LyricsCache
import io.github.proify.lyricon.amprovider.xposed.cache.MediaMetadataCache
import io.github.proify.lyricon.amprovider.xposed.lyrics.AmkitLyricsSource
import io.github.proify.lyricon.amprovider.xposed.lyrics.LyricsMapper
import io.github.proify.lyricon.lyric.model.Song
import io.github.proify.lyricon.provider.RemotePlayer
import kotlinx.coroutines.CoroutineScope
import kotlinx.coroutines.launch

/**
 * 播放事件与歌词数据管理。
 *
 * 与旧实现的区别：不再需要「触发宿主下载 + hook 截获」这条依赖 App 内部类的链路，
 * 切歌后直接由 [AmkitLyricsSource] 通过公开接口拉取歌词，因此对 6.5.2 / 7.0.0 一致适用。
 */
object PlaybackManager {

    private const val TAG = "AMProvider"

    private var player: RemotePlayer? = null
    private var lyricsSource: AmkitLyricsSource? = null
    private var scope: CoroutineScope? = null

    private var currentSongId: String? = null
    private var currentSong: Song? = null

    /** 正在下载的曲目，避免同一首歌重复请求。 */
    private val downloading = mutableSetOf<String>()

    fun init(remotePlayer: RemotePlayer, source: AmkitLyricsSource, coroutineScope: CoroutineScope) {
        player = remotePlayer
        lyricsSource = source
        scope = coroutineScope
    }

    /** 切歌时回调：命中磁盘缓存立即推送，否则先推占位并异步拉取。 */
    fun onSongChanged(newId: String?) {
        if (newId.isNullOrBlank()) {
            currentSongId = null
            updateSong(null)
            YLog.debug(tag = TAG, msg = "PlaybackManager: 歌曲清空")
            return
        }
        if (newId == currentSongId) return
        currentSongId = newId
        YLog.debug(tag = TAG, msg = "PlaybackManager: 切歌 $newId")

        val cached = LyricsCache.load(newId)
        if (cached != null) {
            YLog.debug(tag = TAG, msg = "PlaybackManager: 缓存命中 $newId")
            updateSong(cached)
            return
        }

        val metadata = MediaMetadataCache.getMetadataById(newId)
        updateSong(Song(newId, metadata?.title, metadata?.artist))
        scope?.launch { download(newId) }
    }

    private suspend fun download(id: String) {
        if (!downloading.add(id)) return
        try {
            val document = lyricsSource?.fetch(id) ?: return
            val metadata = MediaMetadataCache.getMetadataById(id)
            val song = LyricsMapper.toSong(
                id = id,
                name = metadata?.title,
                artist = metadata?.artist,
                durationMs = metadata?.duration ?: 0L,
                document = document
            )
            LyricsCache.save(song)
            if (currentSongId == id) {
                YLog.debug(tag = TAG, msg = "PlaybackManager: amkit 下载完成 $id")
                updateSong(song)
            }
        } finally {
            downloading.remove(id)
        }
    }

    private fun updateSong(song: Song?) {
        currentSong = song
        player?.setSong(song)
    }
}
