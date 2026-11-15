/*
 * Copyright 2026 Proify, Tomakino
 * Licensed under the Apache License, Version 2.0
 * http://www.apache.org/licenses/LICENSE-2.0
 */

package io.github.proify.lyricon.amprovider.xposed

import com.highcapable.yukihookapi.hook.log.YLog
import io.github.proify.lyricon.amprovider.xposed.parser.AppleSongParser
import io.github.proify.lyricon.amprovider.xposed.util.toSong
import io.github.proify.lyricon.lyric.model.Song

/** 歌曲数据仓库：聚合磁盘缓存、元数据缓存与原生解析结果。 */
object SongRepository {

    /**
     * 根据 ID 获取歌曲。
     * 策略：磁盘缓存 -> 元数据占位符（仅标题/歌手，无歌词）。
     */
    fun getSong(id: String): Song {
        val cached = DiskSongManager.load(id)
        if (cached != null) {
            YLog.debug("SongRepository: Cache hit for $id")
            return cached.toSong()
        }

        val metadata = MediaMetadataCache.getMetadataById(id)
        YLog.debug("SongRepository: Cache miss for $id, using placeholder")
        return Song(id, metadata?.title, metadata?.artist)
    }

    /** 解析原生 Song 并缓存到磁盘；缺少 ID 或解析失败时返回 null。 */
    fun saveSong(nativeSong: Any): Song? {
        val appleSong = AppleSongParser.parse(nativeSong)
        val id = appleSong.adamId
        if (id.isNullOrBlank()) {
            YLog.debug("SongRepository: Native song has no adamId, ignored")
            return null
        }
        DiskSongManager.save(appleSong)
        return appleSong.toSong()
    }
}
