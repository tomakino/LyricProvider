/*
 * Copyright 2026 Proify, Tomakino
 * Licensed under the Apache License, Version 2.0
 * http://www.apache.org/licenses/LICENSE-2.0
 */

package io.github.proify.lyricon.amprovider.xposed

import com.highcapable.yukihookapi.hook.log.YLog
import io.github.proify.amkit.download.AppleMusicLyricsDownloader
import io.github.proify.amkit.parser.TtmlLyricsParser
import io.github.proify.amkit.parser.toSong
import io.github.proify.lyricon.amprovider.xposed.cache.LyricDiskCache
import io.github.proify.lyricon.amprovider.xposed.cache.MediaMetadataCache
import io.github.proify.lyricon.amprovider.xposed.credentials.CredentialsStore
import io.github.proify.lyricon.lyric.model.Song
import kotlinx.coroutines.CoroutineScope
import kotlinx.coroutines.Dispatchers
import kotlinx.coroutines.SupervisorJob
import kotlinx.coroutines.launch
import java.util.concurrent.ConcurrentHashMap

/**
 * 歌词获取。
 *
 * 同一曲目同时只允许一个请求；命中磁盘缓存时直接复用。
 */
internal object LyricDownloader {

    private val inFlight = ConcurrentHashMap.newKeySet<String>()
    private val scope = CoroutineScope(Dispatchers.IO + SupervisorJob())
    private val ttmlLyricsParser = TtmlLyricsParser()

    /** 拉取歌词，结果通过 [callback] 返回。 */
    fun download(id: String, callback: Callback) {
        if (!inFlight.add(id)) return

        scope.launch {
            try {
                val song = LyricDiskCache.load(id) ?: fetch(id)
                if (song != null) {
                    LyricDiskCache.save(song)
                    callback.onFinished(id, song)
                } else {
                    callback.onFailed(id, Exception("歌词下载失败：$id"))
                }
            } catch (e: Exception) {
                callback.onFailed(id, e)
            } finally {
                inFlight.remove(id)
            }
        }
    }

    suspend fun fetch(adamId: String): Song? {
        val credentials = CredentialsStore.credentials() ?: run {
            YLog.error("凭据未就绪")
            return null
        }
        val storefront = CredentialsStore.storefront()

        return runCatching {
            val ttml = AppleMusicLyricsDownloader(credentials = credentials)
                .download(adamId, storefront)

            val metadata = MediaMetadataCache.get(adamId)
            ttmlLyricsParser.parse(ttml).toSong(
                adamId,
                metadata?.title,
                metadata?.artist,
                metadata?.duration ?: 0
            )
        }.onFailure {
            YLog.error("下载失败 $adamId storefront=$storefront", it)
        }.getOrNull()
    }

    /** 下载结果回调。 */
    interface Callback {
        fun onFinished(id: String, song: Song)
        fun onFailed(id: String, e: Exception)
    }
}
