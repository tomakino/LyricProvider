/*
 * Copyright 2026 Proify, Tomakino
 * Licensed under the Apache License, Version 2.0
 * http://www.apache.org/licenses/LICENSE-2.0
 */

package io.github.proify.lyricon.spotifyprovider.xposed

import android.media.MediaMetadata
import android.media.session.PlaybackState
import com.highcapable.kavaref.KavaRef.Companion.resolve
import com.highcapable.kavaref.extension.classOf
import com.highcapable.yukihookapi.extension.kavaref.hook
import com.highcapable.yukihookapi.extension.kavaref.toClass
import com.highcapable.yukihookapi.hook.entity.YukiBaseHooker
import com.highcapable.yukihookapi.hook.log.YLog
import io.github.proify.lyricon.provider.LyriconProvider

/** MediaSession 播放状态与元数据 hook。 */
class MediaSessionHooker(
    private val hooker: YukiBaseHooker,
    private var lyriconProvider: LyriconProvider? = null,
    private val onTrackChanged: (TrackMetadata) -> Unit
) {

    companion object {
        private const val TAG = "MediaSessionHooker"
    }

    private var currentTrackId: String? = null

    /** 更新 provider 引用。 */
    fun updateProvider(provider: LyriconProvider?) {
        lyriconProvider = provider
    }

    /** 安装 MediaSession hook。 */
    fun hook() {
        YLog.debug(tag = TAG, msg = "开始 Hook MediaSession")

        hooker.apply {
            "android.media.session.MediaSession".toClass().resolve().apply {
                firstMethod {
                    name = "setPlaybackState"
                    parameters(classOf<PlaybackState>())
                }.hook {
                    after {
                        val state = (args[0] as? PlaybackState) ?: return@after
                        lyriconProvider?.player?.setPlaybackState(state)
                    }
                }

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
        val data = TrackMetadata.fromMediaMetadata(metadata)
        if (data == null) {
            logInvalidMetadata(metadata)
            return
        }

        if (data.id == currentTrackId) return
        currentTrackId = data.id

        YLog.debug(tag = TAG, msg = "轨道变更: ${data.title} - ${data.artist}")
        onTrackChanged(data)
    }

    private fun logInvalidMetadata(metadata: MediaMetadata) {
        val title = metadata.getString(MediaMetadata.METADATA_KEY_TITLE)
        val artist = metadata.getString(MediaMetadata.METADATA_KEY_ARTIST)

        if (title.orEmpty().isNotBlank() || artist.orEmpty().isNotBlank()) {
            YLog.info(tag = TAG, msg = "无效的轨道信息: $title - $artist")
        }
    }
}
