/*
 * Copyright 2026 Proify, Tomakino
 * Licensed under the Apache License, Version 2.0
 * http://www.apache.org/licenses/LICENSE-2.0
 */

package io.github.proify.lyricon.amprovider.xposed.playback

import io.github.proify.lyricon.amprovider.xposed.cache.LyricsCache
import io.github.proify.lyricon.amprovider.xposed.lyrics.AmkitLyricsSource
import io.github.proify.lyricon.amprovider.xposed.lyrics.LyricsMapper
import io.github.proify.lyricon.amprovider.xposed.util.DebugLog
import io.github.proify.lyricon.lyric.model.Song
import io.github.proify.lyricon.provider.RemotePlayer
import kotlinx.coroutines.CoroutineScope
import kotlinx.coroutines.launch

/** 当前歌曲与歌词解析调度，按 mediaId 去重。 */
object PlaybackManager {

    private var player: RemotePlayer? = null
    private var lyricsSource: AmkitLyricsSource? = null
    private var scope: CoroutineScope? = null

    private var currentId: String? = null
    private val resolving = java.util.concurrent.ConcurrentHashMap.newKeySet<String>()

    fun init(
        remotePlayer: RemotePlayer,
        source: AmkitLyricsSource,
        coroutineScope: CoroutineScope,
    ) {
        player = remotePlayer
        lyricsSource = source
        scope = coroutineScope
    }

    /** 歌曲事件入口；mediaId 为空表示清空。 */
    fun onMetadata(mediaId: String?, title: String?, artist: String?, durationMs: Long) {
        if (mediaId.isNullOrBlank()) {
            if (currentId != null) {
                currentId = null
                updateSong(null)
                DebugLog.log("Playback", "歌曲清空")
            }
            return
        }
        if (mediaId == currentId) return
        currentId = mediaId
        DebugLog.log("Playback", "当前歌曲 $mediaId")

        val cached = LyricsCache.load(mediaId)
        if (cached != null) {
            DebugLog.log("Playback", "缓存命中 $mediaId")
            updateSong(cached)
            return
        }

        updateSong(Song(mediaId, title, artist))
        scope?.launch { resolve(mediaId, title, artist, durationMs) }
    }

    private suspend fun resolve(id: String, title: String?, artist: String?, durationMs: Long) {
        if (!resolving.add(id)) return
        try {
            val document = lyricsSource?.fetch(id) ?: return

            val song = LyricsMapper.toSong(
                id = id,
                name = title,
                artist = artist,
                durationMs = durationMs,
                document = document,
            )
            LyricsCache.save(song)
            if (currentId == id) {
                DebugLog.log("Playback", "歌词就绪 $id")
                updateSong(song)
            }
        } finally {
            resolving.remove(id)
        }
    }

    private fun updateSong(song: Song?) {
        player?.setSong(song)
    }
}
