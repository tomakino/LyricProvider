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
 *
 * 适配 Apple Music 7.0.0-beta：
 * - 偏好设置类已被混淆（见 [PreferencesMonitor]），初始化不再因单个 Hook 缺失而整体失败；
 * - 位置读取增加 LocalMediaPlayerController 兜底，避免 ExoMediaPlayer 实例捕获失败后进度不动。
 */
object Apple : YukiBaseHooker() {

    private const val TAG = "AMProvider"

    private const val CLASS_PLAYER_LYRICS_VIEW_MODEL =
        "com.apple.android.music.player.viewmodel.PlayerLyricsViewModel"
    private const val CLASS_EXO_MEDIA_PLAYER =
        "com.apple.android.music.playback.player.ExoMediaPlayer"
    private const val CLASS_LOCAL_MEDIA_PLAYER_CONTROLLER =
        "com.apple.android.music.playback.controller.LocalMediaPlayerController"

    private lateinit var application: Application
    private lateinit var classLoader: ClassLoader

    @Volatile
    private var isPlaying = false

    // 反射缓存
    private var exoMediaPlayerInstance: Any? = null
    private var getPositionMethod: Method? = null

    /** 兜底进度来源：LocalMediaPlayerController#getCurrentPosition()。 */
    private var mediaPlayerControllerInstance: Any? = null
    private var controllerPositionMethod: Method? = null

    // 进度同步协程
    private val coroutineScope by lazy { CoroutineScope(Dispatchers.Default + SupervisorJob()) }
    private var progressJob: Job? = null

    private var provider: LyriconProvider? = null

    override fun onHook() {
        onAppLifecycle {
            onCreate { onAppCreate() }
        }
    }

    //region 初始化

    private fun onAppCreate() {
        application = appContext ?: return
        classLoader = appClassLoader ?: return

        // 任何一项初始化失败都不应中断整体流程：目标 App 的混淆名可能随版本变化。
        hookSafely("PreferencesMonitor") { PreferencesMonitor.initialize(application) }
        PreferencesMonitor.listener = object : PreferencesMonitor.Listener {
            override fun onTranslationSelectedChanged(selected: Boolean) {
                provider?.player?.setDisplayTranslation(selected)
            }
        }
        hookSafely("DiskSongManager") { DiskSongManager.initialize(application) }

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

        helper.player.setDisplayTranslation(PreferencesMonitor.resolveDisplayTranslation())
        helper.register()
        provider = helper
        YLog.debug("Apple: Lyricon provider registered")
    }

    private fun startHooks() {
        hookSafely("MediaSession.setMetadata") { hookMediaMetadataChange() }
        hookSafely("PlayerLyricsViewModel.buildTimeRangeToLyricsMap") { hookLyricBuildMethod() }
        hookSafely("ExoMediaPlayer") { hookExoMediaPlayer() }
        YLog.debug("Apple: All hooks registered")
    }

    private fun hookSafely(name: String, block: () -> Unit) {
        try {
            block()
        } catch (e: Throwable) {
            YLog.error(tag = TAG, msg = "Apple: failed to hook $name", e = e)
        }
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
        classLoader.loadClass(CLASS_PLAYER_LYRICS_VIEW_MODEL)
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
        val exoPlayerClass = classLoader.loadClass(CLASS_EXO_MEDIA_PLAYER)

        // 缓存播放器实例与进度读取方法
        exoPlayerClass.declaredConstructors.forEach { constructor ->
            constructor.hook {
                after {
                    exoMediaPlayerInstance = instanceOrNull
                    getPositionMethod = runCatching {
                        instanceClass?.getDeclaredMethod("getCurrentPosition")
                    }.getOrNull()
                    YLog.debug("Apple: ExoMediaPlayer captured")
                }
            }
        }

        // 用户手动拖动进度时同步到 Provider
        exoPlayerClass.resolve().firstMethod {
            name = "seekToPosition"
            parameters(Long::class)
        }.hook {
            after {
                val position = args(0).cast<Long>() ?: 0L
                if (isPlaying) provider?.player?.seekTo(position)
            }
        }

        val controllerClass = classLoader.loadClass(CLASS_LOCAL_MEDIA_PLAYER_CONTROLLER)

        // 兜底进度来源：控制器实例一定会在播放前构造
        controllerClass.declaredConstructors.forEach { constructor ->
            constructor.hook {
                after {
                    mediaPlayerControllerInstance = instanceOrNull
                    controllerPositionMethod = runCatching {
                        instanceClass?.getDeclaredMethod("getCurrentPosition")
                    }.getOrNull()
                }
            }
        }

        // 播放状态切换时启停进度同步
        controllerClass
            .resolve()
            .method {
                name = "onPlaybackStateChanged"
                parameters(VagueType, Int::class, Int::class)
            }.first().hook {
                after {
                    mediaPlayerControllerInstance = mediaPlayerControllerInstance ?: instanceOrNull
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
        provider?.player?.setPosition(readPosition())
    }

    /** 依次尝试 ExoMediaPlayer / LocalMediaPlayerController，两者都不可用时返回 0。 */
    private fun readPosition(): Long {
        readPositionFrom(exoMediaPlayerInstance, getPositionMethod)?.let { return it }
        return readPositionFrom(mediaPlayerControllerInstance, controllerPositionMethod) ?: 0L
    }

    private fun readPositionFrom(instance: Any?, method: Method?): Long? {
        if (instance == null || method == null) return null
        return runCatching { method.invoke(instance) as? Long }.getOrNull()
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
