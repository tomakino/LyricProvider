/*
 * Copyright 2026 Proify, Tomakino
 * Licensed under the Apache License, Version 2.0
 * http://www.apache.org/licenses/LICENSE-2.0
 */

package io.github.proify.lyricon.cmprovider.xposed

import io.github.proify.lyricon.lyric.model.Song
import io.github.proify.lyricon.yrckit.download.YrcDownloader
import io.github.proify.lyricon.yrckit.toSong
import kotlinx.coroutines.CoroutineScope
import kotlinx.coroutines.Dispatchers
import kotlinx.coroutines.SupervisorJob
import kotlinx.coroutines.launch
import java.util.concurrent.ConcurrentHashMap

/** 歌词获取：优先磁盘缓存，未命中走网络并回写。 */
internal object LyricDownloader {

    private val inFlight = ConcurrentHashMap.newKeySet<Long>()
    private val scope = CoroutineScope(Dispatchers.IO + SupervisorJob())

    fun download(id: Long, callback: Callback) {
        if (!inFlight.add(id)) return

        scope.launch {
            try {
                val song = LyricDiskCache.load(id) ?: fetch(id).also {
                    LyricDiskCache.put(id, it)
                }
                callback.onFinished(id, song)
            } catch (e: Exception) {
                callback.onFailed(id, e)
            } finally {
                inFlight.remove(id)
            }
        }
    }

    private fun fetch(id: Long): Song {
        val response = YrcDownloader.fetchLyric(id)
        val metadata = TrackMetadataStore.get(id)
        return response.toSong(metadata?.title, metadata?.artist)
    }

    interface Callback {
        fun onFinished(id: Long, song: Song)
        fun onFailed(id: Long, e: Exception)
    }
}
