/*
 * Copyright 2026 Proify, Tomakino
 * Licensed under the Apache License, Version 2.0
 * http://www.apache.org/licenses/LICENSE-2.0
 */

package io.github.proify.lyricon.spotifyprovider.xposed

import com.highcapable.yukihookapi.hook.entity.YukiBaseHooker
import com.highcapable.yukihookapi.hook.log.YLog
import io.github.proify.lyricon.provider.LyriconFactory
import io.github.proify.lyricon.provider.LyriconProvider
import io.github.proify.lyricon.provider.ProviderLogo
import io.github.proify.lyricon.spotifyprovider.xposed.core.DexKitManager
import io.github.proify.lyricon.spotifyprovider.xposed.core.LyricFetcher
import io.github.proify.lyricon.spotifyprovider.xposed.core.MediaSessionHooker
import io.github.proify.lyricon.spotifyprovider.xposed.core.OkHttpHooker
import org.luckypray.dexkit.DexKitBridge

/**
 * Spotify 歌词提供者主协调器
 */
object SpotifyHooker : YukiBaseHooker() {

    private const val TAG = "SpotifyHooker"

    private var lyriconProvider: LyriconProvider? = null
    private var dexKitManager: DexKitManager? = null
    private var mediaSessionHooker: MediaSessionHooker? = null
    private var okHttpHooker: OkHttpHooker? = null
    private var lyricFetcher: LyricFetcher? = null
    private var dexKitBridge: DexKitBridge? = null

    init {
        System.loadLibrary("dexkit")
    }

    override fun onHook() {
        YLog.debug(tag = TAG, msg = "正在注入进程: $processName")

        // 初始化组件
        initComponents()

        // 注册生命周期回调
        registerLifecycleCallbacks()

        // 执行 Hook
        performHooks()
    }

    private fun initComponents() {
        dexKitManager = DexKitManager(appInfo.sourceDir)
        lyricFetcher = LyricFetcher()
        okHttpHooker = OkHttpHooker(this, dexKitManager!!)

        // 延迟初始化需要 appContext 的组件
        mediaSessionHooker = MediaSessionHooker(this, lyriconProvider = null) { metadata ->
            handleTrackChanged(metadata)
        }
    }

    private fun registerLifecycleCallbacks() {
        registerAppLifecycle(true) {
            onCreate {
                initProvider()
            }
            onTerminate {
                releaseResources()
            }
        }
    }

    private fun performHooks() {
        // 初始化 DexKit
        dexKitManager?.init()

        // Hook MediaSession
        mediaSessionHooker?.hook()

        // Hook OkHttp
        val context = hostApplication
        val classLoader = hostClassLoader
        if (context != null && classLoader != null) {
            val versionCode = getVersionCode(context)
            val bridge = dexKitBridge
            if (bridge != null) {
                okHttpHooker?.hook(context, classLoader, versionCode, bridge)
            }
        }
    }

    private fun initProvider() {
        val context = hostApplication ?: return

        lyriconProvider = LyriconFactory.createProvider(
            context = context,
            providerPackageName = Constants.PROVIDER_PACKAGE_NAME,
            playerPackageName = Constants.MUSIC_PACKAGE_NAME,
            logo = ProviderLogo.fromSvg(Constants.ICON)
        ).apply { register() }

        // 更新 MediaSessionHooker 的 provider
        mediaSessionHooker =
            MediaSessionHooker(this, lyriconProvider = lyriconProvider) { metadata ->
                handleTrackChanged(metadata)
            }

        YLog.debug(tag = TAG, msg = "Provider 初始化完成")
    }

    private fun handleTrackChanged(metadata: Metadata) {
        val context = hostApplication ?: return

        lyricFetcher?.fetch(
            context = context,
            metadata = metadata,
            onSongReady = { song ->
                lyriconProvider?.player?.setSong(song)
            }
        )
    }

    private fun getVersionCode(context: android.content.Context): Long {
        return try {
            context.packageManager.getPackageInfo(Constants.MUSIC_PACKAGE_NAME, 0).longVersionCode
        } catch (_: Exception) {
            0L
        }
    }

    private fun releaseResources() {
        dexKitManager?.release()
        dexKitManager = null
        mediaSessionHooker = null
        okHttpHooker = null
        lyricFetcher = null
        lyriconProvider = null
        dexKitBridge = null
        YLog.debug(tag = TAG, msg = "资源已释放")
    }
}