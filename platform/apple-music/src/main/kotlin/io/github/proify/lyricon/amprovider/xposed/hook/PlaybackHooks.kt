/*
 * Copyright 2026 Proify, Tomakino
 * Licensed under the Apache License, Version 2.0
 * http://www.apache.org/licenses/LICENSE-2.0
 */

package io.github.proify.lyricon.amprovider.xposed.hook

import android.media.MediaMetadata
import com.highcapable.kavaref.KavaRef.Companion.resolve
import com.highcapable.kavaref.condition.type.VagueType
import com.highcapable.kavaref.extension.classOf
import com.highcapable.yukihookapi.extension.kavaref.hook
import com.highcapable.yukihookapi.extension.kavaref.toClass
import com.highcapable.yukihookapi.hook.param.PackageParam
import io.github.proify.lyricon.amprovider.xposed.cache.MediaMetadataCache
import io.github.proify.lyricon.amprovider.xposed.core.SessionState
import io.github.proify.lyricon.amprovider.xposed.playback.PlaybackState

/** 宿主播放事件回调。 */
internal interface PlaybackSink {

    fun onMetadata(metadata: MediaMetadataCache.Metadata)

    fun onPlaybackStarted()

    fun onPlaybackStopped()

    fun onSeek(position: Long)
}

/** 挂载媒体会话与播放器 hook。 */
internal fun PackageParam.installPlaybackHooks(
    classLoader: ClassLoader,
    state: SessionState,
    sink: PlaybackSink,
) {
    hookMediaSession(classLoader, state, sink)
    hookExoPlayer(classLoader, state, sink)
    hookController(classLoader, state, sink)
}

private fun PackageParam.hookMediaSession(
    classLoader: ClassLoader,
    state: SessionState,
    sink: PlaybackSink,
) {
    "android.media.session.MediaSession".toClass(classLoader)
        .resolve()
        .firstMethod {
            name = "setMetadata"
            parameters(classOf<MediaMetadata>())
        }
        .hook {
            after {
                state.mediaSession = instanceOrNull
                val raw = args.getOrNull(0) as? MediaMetadata ?: return@after
                MediaMetadataCache.putAndGet(raw)?.let(sink::onMetadata)
            }
        }
}

private fun PackageParam.hookExoPlayer(
    classLoader: ClassLoader,
    state: SessionState,
    sink: PlaybackSink,
) {
    val playerClass = classLoader.loadClass(EXO_PLAYER)

    playerClass.declaredConstructors.forEach { constructor ->
        constructor.hook {
            after {
                val instance = instanceOrNull
                state.exoPlayer = instance
                state.exoPlayerPosition = runCatching {
                    instance?.javaClass?.getDeclaredMethod("getCurrentPosition")
                }.getOrNull()
            }
        }
    }

    playerClass.resolve()
        .firstMethod {
            name = "seekToPosition"
            parameters(Long::class)
        }
        .hook {
            after { sink.onSeek(arg(0).get<Long>() ?: 0L) }
        }
}

private fun PackageParam.hookController(
    classLoader: ClassLoader,
    state: SessionState,
    sink: PlaybackSink,
) {
    val controllerClass = classLoader.loadClass(CONTROLLER)

    controllerClass.declaredConstructors.forEach { constructor ->
        constructor.hook {
            after {
                val instance = instanceOrNull
                state.controller = instance
                state.controllerPosition = runCatching {
                    instance?.javaClass?.getDeclaredMethod("getCurrentPosition")
                }.getOrNull()
            }
        }
    }

    controllerClass.resolve()
        .method {
            name = "onPlaybackStateChanged"
            parameters(VagueType, Int::class, Int::class)
        }
        .first()
        .hook {
            after {
                if (state.controller == null) state.controller = instanceOrNull
                when (PlaybackState.from(args.getOrNull(2) as? Int ?: -1)) {
                    PlaybackState.PLAYING -> sink.onPlaybackStarted()
                    else -> sink.onPlaybackStopped()
                }
            }
        }
}

private const val EXO_PLAYER = "com.apple.android.music.playback.player.ExoMediaPlayer"

private const val CONTROLLER =
    "com.apple.android.music.playback.controller.LocalMediaPlayerController"
