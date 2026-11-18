/*
 * Copyright 2026 Proify, Tomakino
 * Licensed under the Apache License, Version 2.0
 * http://www.apache.org/licenses/LICENSE-2.0
 */

package io.github.proify.lyricon.spotifyprovider.xposed

import com.highcapable.yukihookapi.hook.entity.YukiBaseHooker
import com.highcapable.yukihookapi.hook.log.YLog
import io.github.proify.lyricon.dexkit.DexKitSession
import io.github.proify.lyricon.dexkit.YangDexKit
import io.github.proify.lyricon.provider.LyriconFactory
import io.github.proify.lyricon.provider.LyriconProvider
import io.github.proify.lyricon.provider.ProviderLogo

/** 歌词提供者协调器。 */
object SpotifyHooker : YukiBaseHooker() {

    private const val TAG = "SpotifyHooker"

    private var lyriconProvider: LyriconProvider? = null
    private var dexkit: DexKitSession? = null
    private var mediaSessionHooker: MediaSessionHooker? = null
    private var okHttpHooker: OkHttpHooker? = null
    private var lyricFetcher: LyricFetcher? = null

    init {
        System.loadLibrary("dexkit")
    }

    override fun onHook() {
        val classLoader = hostClassLoader ?: return
        val session = YangDexKit.session(appInfo, classLoader)
        dexkit = session
        lyricFetcher = LyricFetcher()
        okHttpHooker = OkHttpHooker(this, session)
        mediaSessionHooker = MediaSessionHooker(
            hooker = this,
            onTrackChanged = ::handleTrackChanged
        ).also { it.hook() }

        registerAppLifecycle(true) {
            onCreate {
                initProvider()
            }
        }
    }

    private fun initProvider() {
        val context = hostApplication ?: return

        hookOkHttp()

        lyriconProvider = LyriconFactory.createProvider(
            context = context,
            providerPackageName = Constants.PROVIDER_PACKAGE_NAME,
            playerPackageName = Constants.MUSIC_PACKAGE_NAME,
            logo = ProviderLogo.fromSvg(Constants.ICON)
        ).apply { register() }

        mediaSessionHooker?.updateProvider(lyriconProvider)

        YLog.debug(tag = TAG, msg = "Provider 初始化完成")
    }

    private fun hookOkHttp() {
        okHttpHooker?.hook()
        dexkit?.close()
        dexkit = null
    }

    private fun handleTrackChanged(metadata: TrackMetadata) {
        val context = hostApplication ?: return

        lyricFetcher?.fetch(
            context = context,
            metadata = metadata,
            onSongReady = { song ->
                lyriconProvider?.player?.setSong(song)
            }
        )
    }
}
