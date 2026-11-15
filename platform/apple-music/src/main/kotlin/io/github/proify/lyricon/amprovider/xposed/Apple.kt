/*
 * Copyright 2026 Proify, Tomakino
 * Licensed under the Apache License, Version 2.0
 * http://www.apache.org/licenses/LICENSE-2.0
 */

package io.github.proify.lyricon.amprovider.xposed

import android.app.Application
import android.media.MediaMetadata
import com.highcapable.kavaref.KavaRef.Companion.resolve
import com.highcapable.kavaref.condition.type.VagueType
import com.highcapable.yukihookapi.extension.kavaref.hook
import com.highcapable.yukihookapi.extension.kavaref.toClass
import com.highcapable.yukihookapi.hook.entity.YukiBaseHooker
import com.highcapable.yukihookapi.hook.log.YLog
import de.robv.android.xposed.XposedHelpers
import io.github.proify.extensions.android.ScreenStateMonitor
import io.github.proify.lyricon.provider.LyriconFactory
import io.github.proify.lyricon.provider.LyriconProvider
import io.github.proify.lyricon.provider.ProviderConstants
import io.github.proify.lyricon.provider.ProviderLogo
import kotlinx.coroutines.CoroutineScope
import kotlinx.coroutines.Dispatchers
import kotlinx.coroutines.Job
import kotlinx.coroutines.SupervisorJob
import kotlinx.coroutines.delay
import kotlinx.coroutines.isActive
import kotlinx.coroutines.launch
import java.lang.reflect.Method

/**
 * Apple Music 注入入口：
 * 注册 Provider，Hook 切歌 / 歌词构建 / 播放器控制，并向 Provider 同步播放进度。
 */
object Apple : YukiBaseHooker() {

    private lateinit var application: Application
    private lateinit var classLoader: ClassLoader

    @Volatile
    private var isPlaying = false

    // 反射缓存
    private var exoMediaPlayerInstance: Any? = null
    private var getPositionMethod: Method? = null

    // 进度同步协程
    private val coroutineScope by lazy { CoroutineScope(Dispatchers.Default + SupervisorJob()) }
    private var progressJob: Job? = null

    private var provider: LyriconProvider? = null

    override fun onHook() {
        registerAppLifecycle(true) { onCreate { onAppCreate() } }
    }

    //region 初始化

    private fun onAppCreate() {
        application = hostApplication ?: return
        classLoader = hostClassLoader ?: return

        PreferencesMonitor.initialize(application)
        PreferencesMonitor.listener = object : PreferencesMonitor.Listener {
            override fun onTranslationSelectedChanged(selected: Boolean) {
                provider?.player?.setDisplayTranslation(selected)
            }
        }
        DiskSongManager.initialize(application)

        initScreenStateMonitor()
        initProvider()
        startHooks()
        YLog.debug("Apple: Initialization complete")
    }

    private fun initProvider() {
        val helper = LyriconFactory.createProvider(
            context = application,
            providerPackageName = Constants.PROVIDER_PACKAGE_NAME,
            playerPackageName = application.packageName,
            logo = ProviderLogo.fromBase64(Constants.ICON)
        )

        PlaybackManager.init(
            remotePlayer = helper.player,
            requester = LyricRequester(classLoader, application)
        )

        helper.player.setDisplayTranslation(PreferencesMonitor.isTranslationSelected())
        helper.register()
        provider = helper
        YLog.debug("Apple: Lyricon provider registered")
    }

    private fun startHooks() {
        hookMediaMetadataChange()
        hookLyricBuildMethod()
        hookExoMediaPlayer()
        YLog.debug("Apple: All hooks registered")
    }

    //endregion

    //region Hook 1: 切歌（MediaSession.setMetadata）

    private fun hookMediaMetadataChange() {
        "android.media.session.MediaSession".toClass(classLoader)
            .resolve()
            .firstMethod {
                name = "setMetadata"
                parameters(MediaMetadata::class.java)
            }.hook {
                after {
                    val rawMetadata = args[0] as? MediaMetadata ?: return@after
                    val metadata = MediaMetadataCache.putAndGet(rawMetadata) ?: return@after

                    YLog.debug("Apple: Metadata changed -> ${metadata.id}")
                    PlaybackManager.onSongChanged(metadata.id)
                }
            }
    }

    //endregion

    //region Hook 2: 歌词构建完成（PlayerLyricsViewModel.buildTimeRangeToLyricsMap）

    private fun hookLyricBuildMethod() {
        classLoader.loadClass("com.apple.android.music.player.viewmodel.PlayerLyricsViewModel")
            .resolve()
            .firstMethod { name = "buildTimeRangeToLyricsMap" }
            .hook {
                after {
                    YLog.debug("Apple: buildTimeRangeToLyricsMap args=$args")
                    val arg = args.getOrNull(0) ?: run {
                        YLog.debug("Apple: buildTimeRangeToLyricsMap args0 is null")
                        return@after
                    }

                    val songNative = XposedHelpers.callMethod(arg, "get")
                    YLog.debug("Apple: Lyrics built, native song=$songNative")
                    PlaybackManager.onLyricsBuilt(songNative)
                }
            }
    }

    //endregion

    //region Hook 3: 播放器控制（ExoMediaPlayer / LocalMediaPlayerController）

    private fun hookExoMediaPlayer() {
        val exoPlayerClass =
            classLoader.loadClass("com.apple.android.music.playback.player.ExoMediaPlayer")

        // 缓存播放器实例与进度读取方法
        exoPlayerClass.declaredConstructors.forEach { constructor ->
            constructor.hook {
                after {
                    exoMediaPlayerInstance = instanceOrNull
                    getPositionMethod = instanceOrNull?.javaClass?.getDeclaredMethod("getCurrentPosition")
                }
            }
        }

        // 用户手动拖动进度时同步到 Provider
        exoPlayerClass.resolve().firstMethod {
            name = "seekToPosition"
            parameters(Long::class)
        }.hook {
            after {
                val position = arg(0).get<Long>() ?: 0L
                if (isPlaying) provider?.player?.seekTo(position)
            }
        }

        // 播放状态切换时启停进度同步
        classLoader.loadClass("com.apple.android.music.playback.controller.LocalMediaPlayerController")
            .resolve()
            .method {
                name = "onPlaybackStateChanged"
                parameters(VagueType, Int::class, Int::class)
            }.first().hook {
                after {
                    when (PlaybackState.from(args[2] as Int)) {
                        PlaybackState.PLAYING -> startProgressSync()
                        else -> stopProgressSync()
                    }
                }
            }
    }

    //endregion

    //region 进度同步

    private fun startProgressSync() {
        if (isPlaying) return
        isPlaying = true
        provider?.player?.setPlaybackState(true)
        startProgressLoop()
        YLog.debug("Apple: Playback started, syncing position")
    }

    private fun stopProgressSync() {
        isPlaying = false
        provider?.player?.setPlaybackState(false)
        stopProgressLoop()
        YLog.debug("Apple: Playback stopped")
    }

    private fun startProgressLoop() {
        if (progressJob?.isActive == true) return
        progressJob = coroutineScope.launch {
            while (isActive && isPlaying) {
                updatePosition()
                delay(ProviderConstants.DEFAULT_POSITION_UPDATE_INTERVAL)
            }
        }
    }

    private fun updatePosition() {
        val position = runCatching {
            getPositionMethod?.invoke(exoMediaPlayerInstance) as? Long ?: 0L
        }.getOrDefault(0L)
        provider?.player?.setPosition(position)
    }

    private fun stopProgressLoop() {
        progressJob?.cancel()
        progressJob = null
    }

    //endregion

    //region 屏幕状态

    private fun initScreenStateMonitor() {
        ScreenStateMonitor.initialize(application)
        ScreenStateMonitor.addListener(object : ScreenStateMonitor.ScreenStateListener {
            override fun onScreenOn() {
                if (isPlaying) startProgressLoop()
            }

            override fun onScreenOff() {
                stopProgressLoop()
            }

            override fun onScreenUnlocked() {
                if (isPlaying && progressJob == null) startProgressLoop()
            }
        })
    }

    //endregion
}
