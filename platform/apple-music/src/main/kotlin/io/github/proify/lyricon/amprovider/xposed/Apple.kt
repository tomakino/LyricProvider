/*
 * Copyright 2026 Proify, Tomakino
 * Licensed under the Apache License, Version 2.0
 * http://www.apache.org/licenses/LICENSE-2.0
 */

package io.github.proify.lyricon.amprovider.xposed

import android.media.MediaMetadata
import com.highcapable.kavaref.KavaRef.Companion.resolve
import com.highcapable.kavaref.condition.type.VagueType
import com.highcapable.kavaref.extension.classOf
import com.highcapable.yukihookapi.extension.kavaref.hook
import com.highcapable.yukihookapi.extension.kavaref.toClass
import com.highcapable.yukihookapi.hook.entity.YukiBaseHooker
import com.highcapable.yukihookapi.hook.log.YLog
import io.github.proify.extensions.android.ScreenStateMonitor
import io.github.proify.lyricon.amprovider.xposed.cache.LyricDiskCache
import io.github.proify.lyricon.amprovider.xposed.cache.MediaMetadataCache
import io.github.proify.lyricon.amprovider.xposed.credentials.CredentialsStore
import io.github.proify.lyricon.amprovider.xposed.preferences.PreferencesMonitor
import io.github.proify.lyricon.lyric.model.Song
import io.github.proify.lyricon.provider.LyriconFactory
import io.github.proify.lyricon.provider.LyriconProvider
import io.github.proify.lyricon.provider.ProviderLogo

/**
 * Apple Music 歌词提供商。
 *
 * 从 MediaSession 元数据识别曲目，经 [LyricDownloader] 取词后推给 Lyricon，
 * 同时把 ExoPlayer 的播放状态与进度转发过去。
 */
object Apple : YukiBaseHooker() {

    /** Lyricon 提供者实例。 */
    private var provider: LyriconProvider? = null

    /** 译文开关。 */
    private var translationPreferences: PreferencesMonitor? = null

    /** 上一次推送的曲目，用于去重。 */
    private var lastSong: Song? = null

    /** 当前曲目 id。 */
    private var currentSongId: String? = null

    private val progress = ProgressSync {
        provider?.player?.setPosition(it)
    }

    override fun onHook() {
        registerAppLifecycle(true) {
            onCreate {
                onAppCreate()
            }
        }
    }

    private fun onAppCreate() {
        val application = hostApplication!!
        val classLoader = hostClassLoader!!

        LyricDiskCache.initialize(application)
        CredentialsStore.initialize(application)

        translationPreferences = PreferencesMonitor(application, classLoader) { selected ->
            provider?.player?.setDisplayTranslation(selected)
        }.also { it.start() }

        initScreenStateMonitor()
        registerProvider()

        hookMediaSession()
        hookExoPlayer()
        hookController()

        YLog.info("初始化完成：${application.packageName}")
    }

    private fun onMetadata(metadata: MediaMetadataCache.Metadata) {
        val songId = metadata.id

        if (songId == currentSongId) return
        currentSongId = songId

        YLog.info("曲目切换：$songId")

        val cached = LyricDiskCache.load(songId)
        if (cached != null) {
            YLog.debug("歌词缓存命中：$songId")
            updateSong(cached)
            return
        }

        updateSong(
            Song(
                id = songId,
                name = metadata.title,
                artist = metadata.artist
            )
        )

        LyricDownloader.download(songId, object : LyricDownloader.Callback {
            override fun onFinished(
                id: String,
                song: Song
            ) {
                if (id == currentSongId) {
                    updateSong(song)
                }
            }

            override fun onFailed(id: String, e: Exception) {
                YLog.error(msg = "歌词下载失败：$id", e = e)
            }

        })
    }

    private fun updateSong(song: Song?) {
        if (song == lastSong) return

        lastSong = song

        val ok = provider?.player?.setSong(song)
        YLog.debug("推送曲目：${song?.name}, hasLyrics=${song?.lyrics?.isNotEmpty()}, ok=$ok")
    }

    private fun onPlaybackStarted() {
        YLog.debug("播放开始")
        provider?.player?.setPlaybackState(true)
        progress.onPlaybackChanged(true)
    }

    private fun onPlaybackStopped() {
        YLog.debug("播放停止")
        provider?.player?.setPlaybackState(false)
        progress.onPlaybackChanged(false)
    }

    private fun onSeek(position: Long) {
        YLog.debug("跳转：$position")
        provider?.player?.seekTo(position)
    }

    private fun hookMediaSession() {
        "android.media.session.MediaSession".toClass(hostClassLoader)
            .resolve()
            .firstMethod {
                name = "setMetadata"
                parameters(classOf<MediaMetadata>())
            }
            .hook {
                after {
                    val raw = args.getOrNull(0) as? MediaMetadata ?: return@after
                    YLog.debug("MediaMetadata：${raw.getString(MediaMetadata.METADATA_KEY_MEDIA_ID)}")

                    MediaMetadataCache.put(raw)?.let {
                        onMetadata(it)
                    }
                }
            }
        YLog.debug("hook: MediaSession.setMetadata")
    }

    private fun hookExoPlayer() {
        val playerClass = hostClassLoader!!.loadClass(EXO_PLAYER)

        playerClass.declaredConstructors.forEach { constructor ->
            constructor.hook {
                after {
                    progress.exoPlayerInstance = instanceOrNull
                }
            }
        }

        playerClass.resolve()
            .firstMethod {
                name = "seekToPosition"
                parameters(Long::class)
            }
            .hook {
                after {
                    onSeek(arg(0).get<Long>() ?: 0L)
                }
            }
        YLog.debug("hook: ExoMediaPlayer")
    }

    private fun hookController() {
        val controllerClass = hostClassLoader!!.loadClass(CONTROLLER)

        controllerClass.resolve()
            .firstMethod {
                name = "onPlaybackStateChanged"
                parameters(VagueType, Int::class, Int::class)
            }
            .hook {
                after {
                    when (PlaybackState.from(args.getOrNull(2) as? Int ?: -1)) {
                        PlaybackState.PLAYING -> onPlaybackStarted()
                        else -> onPlaybackStopped()
                    }
                }
            }
        YLog.debug("hook: LocalMediaPlayerController")
    }

    private fun registerProvider() {
        provider = LyriconFactory.createProvider(
            context = hostApplication!!,
            providerPackageName = Constants.PROVIDER_PACKAGE_NAME,
            playerPackageName = hostApplication!!.packageName,
            logo = ProviderLogo.fromBase64(Constants.ICON),
        )
        provider?.player?.setDisplayTranslation(translationPreferences?.displayTranslation ?: false)
        provider?.register()
        YLog.debug("提供者已注册")
    }

    private fun initScreenStateMonitor() {
        ScreenStateMonitor.initialize(hostApplication!!)
        ScreenStateMonitor.addListener(object : ScreenStateMonitor.ScreenStateListener {
            override fun onScreenOn() = progress.onScreenOn()
            override fun onScreenOff() = progress.onScreenOff()
            override fun onScreenUnlocked() = progress.onScreenUnlocked()
        })
    }

    private const val EXO_PLAYER = "com.apple.android.music.playback.player.ExoMediaPlayer"

    private const val CONTROLLER =
        "com.apple.android.music.playback.controller.LocalMediaPlayerController"

}
