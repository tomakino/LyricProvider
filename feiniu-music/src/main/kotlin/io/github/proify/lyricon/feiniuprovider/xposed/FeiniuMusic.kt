/*
 * Copyright 2026 Proify, Tomakino
 * Licensed under the Apache License, Version 2.0
 * http://www.apache.org/licenses/LICENSE-2.0
 */

package io.github.proify.lyricon.feiniuprovider.xposed

import android.content.Context
import android.media.MediaMetadata
import android.media.session.PlaybackState
import com.highcapable.kavaref.KavaRef.Companion.resolve
import com.highcapable.yukihookapi.hook.entity.YukiBaseHooker
import com.highcapable.yukihookapi.hook.log.YLog
import io.github.proify.lyricon.provider.LyriconFactory
import io.github.proify.lyricon.provider.LyriconProvider
import io.github.proify.lyricon.provider.ProviderLogo
import io.github.proify.lyricon.provider.service.addConnectionListener

/**
 * 飞牛音乐把当前歌词行放在 MediaSession 的副标题里，并随播放更新。
 * 按播放器的顺序同步全部当前行，包括歌名、词曲信息和正文。
 */
open class FeiniuMusic(val tag: String = "FeiniuMusicProvider") : YukiBaseHooker() {

    private var provider: LyriconProvider? = null
    private var trackTitle: String? = null
    private var lastLine: String? = null
    private var lastPlaybackState: PlaybackState? = null

    override fun onHook() {
        YLog.debug(tag = tag, msg = "Feiniu music media session hook loaded")

        onAppLifecycle {
            onCreate {
                initProvider(this)
            }
            onTerminate {
                provider?.unregister()
                provider?.destroy()
                provider = null
            }
        }

        hookMediaSession()
    }

    private fun initProvider(context: Context) {
        if (provider != null) return
        val created = LyriconFactory.createProvider(
            context = context,
            providerPackageName = Constants.PROVIDER_PACKAGE_NAME,
            playerPackageName = Constants.MUSIC_PACKAGE_NAME,
            logo = ProviderLogo.fromBase64(Constants.ICON)
        )
        created.service.addConnectionListener {
            onConnected {
                YLog.debug(tag = tag, msg = "Lyricon connected")
                republish()
            }
            onReconnected {
                YLog.debug(tag = tag, msg = "Lyricon reconnected")
                republish()
            }
            onDisconnected {
                YLog.debug(tag = tag, msg = "Lyricon disconnected")
            }
            onConnectTimeout {
                YLog.debug(tag = tag, msg = "Lyricon connect timeout")
            }
        }
        val registered = created.register()
        provider = created
        YLog.debug(
            tag = tag,
            msg = "LyriconProvider register=$registered player=${Constants.MUSIC_PACKAGE_NAME}"
        )
    }

    private fun hookMediaSession() {
        "android.media.session.MediaSession".toClass().resolve().apply {
            firstMethod {
                name = "setPlaybackState"
                parameters(PlaybackState::class.java)
            }.hook {
                after {
                    val state = args[0] as? PlaybackState ?: return@after
                    lastPlaybackState = state
                    provider?.player?.setPlaybackState(state)
                }
            }
            firstMethod {
                name = "setMetadata"
                parameters(MediaMetadata::class.java)
            }.hook {
                after {
                    publishLine(args[0] as? MediaMetadata)
                }
            }
        }
    }

    private fun publishLine(metadata: MediaMetadata?) {
        val title = metadata?.description?.title?.toString()?.trim().orEmpty()
        val line = metadata?.description?.subtitle?.toString().orEmpty()
        if (title.isNotEmpty() && title != trackTitle) {
            trackTitle = title
            lastLine = null
        }
        emit(title, line)
    }

    private fun emit(title: String, line: String) {
        if (line == lastLine) return
        lastLine = line
        YLog.debug(tag = tag, msg = "Lyric line from session title=$title line=$line")
        provider?.player?.sendText(line)
        lastPlaybackState?.let { provider?.player?.setPlaybackState(it) }
    }

    private fun republish() {
        val line = lastLine ?: return
        provider?.player?.sendText(line)
        lastPlaybackState?.let { provider?.player?.setPlaybackState(it) }
    }
}
