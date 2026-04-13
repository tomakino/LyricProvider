/*
 * Copyright 2026 Proify, Tomakino
 * Licensed under the Apache License, Version 2.0
 * http://www.apache.org/licenses/LICENSE-2.0
 */

package io.github.proify.lyricon.amprovider.xposed

import android.util.Log
import com.highcapable.yukihookapi.hook.log.YLog
import io.github.proify.lyricon.lyric.model.Song
import io.github.proify.lyricon.provider.RemotePlayer
import kotlin.system.measureTimeMillis

object PlaybackManager {
    private var player: RemotePlayer? = null
    private var lyricRequester: LyricRequester? = null

    // 状态追踪
    private var currentSongId: String? = null

    fun init(remotePlayer: RemotePlayer, requester: LyricRequester) {
        this.player = remotePlayer
        this.lyricRequester = requester
    }

    /**
     * 当系统切歌或 Metadata 变化时调用
     */
    fun onSongChanged(newId: String?) {
        if (newId.isNullOrBlank()) {
            currentSongId = null
            setSong(null)
            YLog.debug("PlaybackManager: Song changed to null")
            return
        }

        // 避免重复处理同一首歌
        if (newId == currentSongId) return
        currentSongId = newId

        YLog.debug("PlaybackManager: Song changed to $newId")

        // 1. 立即设置歌曲（可能是完整版，也可能是占位版）
        val song = SongRepository.getSong(newId)
        setSong(song)

        // 2. 检查是否需要下载歌词
        if (song.lyrics.isNullOrEmpty()) {
            lyricRequester?.requestDownload(newId)
        } else {
            YLog.debug("PlaybackManager: Song $newId has lyrics, skipping download.")
        }
    }

    /**
     * 当 Hook 捕获到歌词构建完成时调用
     */
    fun onLyricsBuilt(nativeSongObj: Any) {
        val song = SongRepository.saveSong(nativeSongObj)
        if (song == null) {
            YLog.debug("PlaybackManager: Failed to save song.")
            return
        }
        val id = song.id
        val isSongSame by lazy {
            var same = false
            val time = measureTimeMillis {
                same = lastSong != song
            }
            Log.d("PlaybackManager", "Same song check took $time ms.")
            return@lazy same
        }

        if (id == currentSongId && isSongSame) {
            YLog.debug("PlaybackManager: Lyrics ready for current song $id, updating player.")
            setSong(song)
        } else {
            YLog.debug("PlaybackManager: Lyrics ready for song $id, but not current song.")
        }
    }

    private var lastSong: Song? = null
    private fun setSong(song: Song?) {
        lastSong = song
        player?.setSong(song)
    }
}