/*
 * Copyright 2026 Proify, Tomakino
 * Licensed under the Apache License, Version 2.0
 * http://www.apache.org/licenses/LICENSE-2.0
 */

package io.github.proify.lyricon.amprovider.xposed

import com.highcapable.yukihookapi.hook.log.YLog
import io.github.proify.lyricon.lyric.model.Song
import io.github.proify.lyricon.provider.RemotePlayer

/**
 * 播放事件与歌词数据管理：
 * 负责把 Apple Music 的切歌 / 歌词构建事件转换为对 [RemotePlayer] 的歌曲推送。
 */
object PlaybackManager {

    private var player: RemotePlayer? = null
    private var lyricRequester: LyricRequester? = null

    /** 当前正在播放的歌曲 ID 与已推送的歌曲内容。 */
    private var currentSongId: String? = null
    private var currentSong: Song? = null

    fun init(remotePlayer: RemotePlayer, requester: LyricRequester) {
        player = remotePlayer
        lyricRequester = requester
    }

    /** 系统 Metadata 变化（切歌）时回调。 */
    fun onSongChanged(newId: String?) {
        if (newId.isNullOrBlank()) {
            currentSongId = null
            updateSong(null)
            YLog.debug("PlaybackManager: Song changed to null")
            return
        }

        if (newId == currentSongId) return
        currentSongId = newId
        YLog.debug("PlaybackManager: Song changed to $newId")

        // 1. 先推送当前可用的歌曲（可能是磁盘缓存，也可能是无歌词占位符）
        val song = SongRepository.getSong(newId)
        updateSong(song)

        // 2. 无歌词时欺骗 Apple Music 触发歌词下载，构建完成后经 onLyricsBuilt 回推
        if (song.lyrics.isNullOrEmpty()) {
            lyricRequester?.requestDownload(newId)
        } else {
            YLog.debug("PlaybackManager: Song $newId already has lyrics, skip download")
        }
    }

    /** Hook 捕获到歌词构建完成时回调。 */
    fun onLyricsBuilt(nativeSong: Any) {
        val song = SongRepository.saveSong(nativeSong)
        if (song == null) {
            YLog.debug("PlaybackManager: Lyrics built but song parsing failed, ignored")
            return
        }

        if (song.id == currentSongId && song != currentSong) {
            YLog.debug("PlaybackManager: Lyrics ready for current song ${song.id}, updating player")
            updateSong(song)
        } else if (song.id != currentSongId) {
            // MediaSession 的 mediaId 与 TTML 的 adamId 若不属于同一 id 体系，会走到这里。
            YLog.warn(
                tag = "AMProvider",
                msg = "PlaybackManager: lyrics id (${song.id}) != playing id ($currentSongId), ignored"
            )
        } else {
            YLog.debug("PlaybackManager: Lyrics for ${song.id} unchanged, ignored")
        }
    }
    private fun updateSong(song: Song?) {
        currentSong = song
        player?.setSong(song)
    }
}
