/*
 * Copyright 2026 Proify, Tomakino
 * Licensed under the Apache License, Version 2.0
 * http://www.apache.org/licenses/LICENSE-2.0
 */

package io.github.proify.lyricon.spotifyprovider.xposed.core

import android.media.MediaMetadata
import android.media.session.PlaybackState
import com.highcapable.kavaref.KavaRef.Companion.resolve
import com.highcapable.yukihookapi.hook.entity.YukiBaseHooker
import com.highcapable.yukihookapi.hook.log.YLog
import io.github.proify.lyricon.provider.LyriconProvider
import io.github.proify.lyricon.spotifyprovider.xposed.Metadata
import io.github.proify.lyricon.spotifyprovider.xposed.cache.MetadataCache

/**
 * MediaSession Hook 管理器
 */
class MediaSessionHooker(
    private val hooker: YukiBaseHooker,
    private val lyriconProvider: LyriconProvider?,
    private val onTrackChanged: (Metadata) -> Unit
) {

    companion object {
        private const val TAG = "MediaSessionHooker"
    }

    private var currentTrackId: String? = null

    /**
     * Hook MediaSession
     */
    fun hook() {
        YLog.debug(tag = TAG, msg = "开始 Hook MediaSession")

        hooker.apply {
            "android.media.session.MediaSession".toClass().resolve().apply {
                // Hook 播放状态
                firstMethod {
                    name = "setPlaybackState"
                    parameters(PlaybackState::class.java)
                }.hook {
                    after {
                        val state = (args[0] as? PlaybackState) ?: return@after
                        lyriconProvider?.player?.setPlaybackState(state)
                    }
                }

                // Hook 元数据
                firstMethod {
                    name = "setMetadata"
                    parameters("android.media.MediaMetadata")
                }.hook {
                    after {
                        val metadata = args[0] as? MediaMetadata ?: return@after
                        handleMetadata(metadata)
                    }
                }
            }
        }

        YLog.debug(tag = TAG, msg = "MediaSession Hook 完成")
    }

    private fun handleMetadata(metadata: MediaMetadata) {
        val data = Metadata.fromMediaMetadata(metadata)
        MetadataCache.save(metadata)

        if (data == null) {
            // 处理无效元数据（如广告）
            handleInvalidMetadata(metadata)
            return
        }

        if (data.id == currentTrackId) return
        currentTrackId = data.id

        YLog.debug(tag = TAG, msg = "轨道变更: ${data.title} - ${data.artist}")
        onTrackChanged(data)
    }

    private fun handleInvalidMetadata(metadata: MediaMetadata) {
        val title = metadata.getString(MediaMetadata.METADATA_KEY_TITLE)
        val artist = metadata.getString(MediaMetadata.METADATA_KEY_ARTIST)

        if (title.orEmpty().isNotBlank() || artist.orEmpty().isNotBlank()) {
            YLog.info(tag = TAG, msg = "无效的轨道信息，使用标题和艺术家: $title - $artist")
        }
    }
}