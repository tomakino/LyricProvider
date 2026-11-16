/*
 * Copyright 2026 Proify, Tomakino
 * Licensed under the Apache License, Version 2.0
 * http://www.apache.org/licenses/LICENSE-2.0
 */

package io.github.proify.lyricon.amprovider.xposed

import android.app.Application
import android.media.MediaMetadata
import com.highcapable.yukihookapi.hook.entity.YukiBaseHooker
import com.highcapable.yukihookapi.hook.log.YLog
import io.github.proify.extensions.android.ScreenStateMonitor
import io.github.proify.lyricon.amprovider.xposed.cache.LyricsCache
import io.github.proify.lyricon.amprovider.xposed.cache.MediaMetadataCache
import io.github.proify.lyricon.amprovider.xposed.core.ProgressSync
import io.github.proify.lyricon.amprovider.xposed.core.SessionState
import io.github.proify.lyricon.amprovider.xposed.hook.PlaybackSink
import io.github.proify.lyricon.amprovider.xposed.hook.installPlaybackHooks
import io.github.proify.lyricon.amprovider.xposed.hook.installPreferenceHooks
import io.github.proify.lyricon.amprovider.xposed.lyrics.AmkitLyricsSource
import io.github.proify.lyricon.amprovider.xposed.lyrics.CredentialsStore
import io.github.proify.lyricon.amprovider.xposed.playback.PlaybackManager
import io.github.proify.lyricon.amprovider.xposed.prefs.PreferencesMonitor
import io.github.proify.lyricon.amprovider.xposed.util.DebugLog
import io.github.proify.lyricon.amprovider.xposed.util.callMethodOrNull
import io.github.proify.lyricon.provider.LyriconFactory
import io.github.proify.lyricon.provider.LyriconProvider
import io.github.proify.lyricon.provider.ProviderLogo
import kotlinx.coroutines.CoroutineScope
import kotlinx.coroutines.Dispatchers
import kotlinx.coroutines.SupervisorJob

/** Apple Music 注入入口：装配 Provider 与各 hook。 */
object Apple : YukiBaseHooker() {

    private const val TAG = "AMProvider"

    private lateinit var application: Application
    private lateinit var classLoader: ClassLoader
    private var provider: LyriconProvider? = null

    private val state = SessionState()

    private val scope by lazy { CoroutineScope(Dispatchers.Default + SupervisorJob()) }

    private val progress by lazy { ProgressSync(scope, state) { provider?.player } }

    private val sink = object : PlaybackSink {

        override fun onMetadata(metadata: MediaMetadataCache.Metadata) {
            PlaybackManager.onMetadata(
                metadata.id,
                metadata.title,
                metadata.artist,
                metadata.duration,
            )
        }

        override fun onPlaybackStarted() {
            progress.onPlaybackChanged(true)
            readCurrentMetadata()?.let(::onMetadata)
        }

        override fun onPlaybackStopped() {
            progress.onPlaybackChanged(false)
        }

        override fun onSeek(position: Long) {
            if (progress.isPlaying) provider?.player?.seekTo(position)
        }
    }

    override fun onHook() = registerAppLifecycle(true) { onCreate { onAppCreate() } }

    private fun onAppCreate() {
        application = hostApplication ?: return
        classLoader = hostClassLoader ?: return

        installSafely("DebugLog") { DebugLog.initialize(application) }
        installSafely("PreferencesMonitor") { PreferencesMonitor.initialize(application) }
        installSafely("LyricsCache") { LyricsCache.initialize(application) }
        installSafely("CredentialsStore") { CredentialsStore.initialize(application) }

        PreferencesMonitor.listener = object : PreferencesMonitor.Listener {
            override fun onTranslationSelectedChanged(selected: Boolean) {
                provider?.player?.setDisplayTranslation(selected)
            }
        }

        initScreenStateMonitor()
        registerProvider()
        installHooks()
        YLog.debug("Apple: initialized")
    }

    private fun registerProvider() {
        val helper = LyriconFactory.createProvider(
            context = application,
            providerPackageName = Constants.PROVIDER_PACKAGE_NAME,
            playerPackageName = application.packageName,
            logo = ProviderLogo.fromBase64(Constants.ICON),
        )
        PlaybackManager.init(
            remotePlayer = helper.player,
            source = AmkitLyricsSource(),
            coroutineScope = scope,
        )
        helper.player.setDisplayTranslation(PreferencesMonitor.resolveDisplayTranslation())
        helper.register()
        provider = helper
    }

    private fun installHooks() {
        installSafely("playback") { installPlaybackHooks(classLoader, state, sink) }
        installSafely("preferences") { installPreferenceHooks(classLoader) }
    }

    private fun readCurrentMetadata(): MediaMetadataCache.Metadata? {
        val session = state.mediaSession ?: return null
        val controller = session.callMethodOrNull("getController") ?: return null
        val metadata = controller.callMethodOrNull("getMetadata") as? MediaMetadata ?: return null
        return MediaMetadataCache.putAndGet(metadata)
    }

    private fun initScreenStateMonitor() {
        ScreenStateMonitor.initialize(application)
        ScreenStateMonitor.addListener(object : ScreenStateMonitor.ScreenStateListener {
            override fun onScreenOn() = progress.onScreenOn()
            override fun onScreenOff() = progress.onScreenOff()
            override fun onScreenUnlocked() = progress.onScreenUnlocked()
        })
    }

    private fun installSafely(name: String, block: () -> Unit) {
        try {
            block()
        } catch (e: Throwable) {
            YLog.error(tag = TAG, msg = "Apple: $name 初始化失败", e = e)
        }
    }
}
