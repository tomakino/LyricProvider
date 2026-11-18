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

object LyricDownloader {
    private val activeIds = ConcurrentHashMap.newKeySet<Long>()
    private val scope = CoroutineScope(Dispatchers.IO + SupervisorJob())

    fun download(id: Long, callback: LyricDownloadCallback) {
        if (activeIds.contains(id)) return

        scope.launch {
            try {
                val diskCache = fromDisk(id)

                val song = if (diskCache != null) {
                    diskCache
                } else {
                    val song = fromNetwork(id)
                    DiskCacheStore.put(id, song)
                    song
                }

                callback.onLyricDownloadFinished(id, song)
            } catch (e: Exception) {
                callback.onLyricDownloadFailed(id, e)
            } finally {
                activeIds.remove(id)
            }
        }
    }

    private fun fromDisk(id: Long): Song? = DiskCacheStore.load(id)

    private fun fromNetwork(id: Long): Song {
        val response = YrcDownloader.fetchLyric(id)
        val data = TrackMetadataManager.get(id)
        val song = response.toSong(
            data?.title,
            data?.artist
        )
        return song
    }
}

interface LyricDownloadCallback {
    fun onLyricDownloadFinished(id: Long, song: Song)
    fun onLyricDownloadFailed(id: Long, e: Exception)
}