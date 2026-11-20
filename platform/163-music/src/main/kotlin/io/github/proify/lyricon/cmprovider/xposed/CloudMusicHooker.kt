/*
 * Copyright 2026 Proify, Tomakino
 * Licensed under the Apache License, Version 2.0
 * http://www.apache.org/licenses/LICENSE-2.0
 */

package io.github.proify.lyricon.cmprovider.xposed

import android.app.Application
import android.media.MediaMetadata
import android.media.session.MediaSession
import android.media.session.PlaybackState
import com.highcapable.kavaref.KavaRef.Companion.resolve
import com.highcapable.kavaref.extension.classOf
import com.highcapable.yukihookapi.extension.kavaref.hook
import com.highcapable.yukihookapi.hook.entity.YukiBaseHooker
import com.highcapable.yukihookapi.hook.log.YLog
import io.github.proify.lyricon.cmprovider.xposed.ProviderConfig.LOGO_SVG
import io.github.proify.lyricon.cmprovider.xposed.ProviderConfig.PROVIDER_PACKAGE_NAME
import io.github.proify.lyricon.dexkit.YangDexKit
import io.github.proify.lyricon.lyric.model.Song
import io.github.proify.lyricon.provider.LyriconFactory
import io.github.proify.lyricon.provider.LyriconProvider
import io.github.proify.lyricon.provider.ProviderLogo

/** 单个进程内的 Hook 与 Provider 编排。 */
internal class CloudMusicHooker : YukiBaseHooker() {

    private var provider: LyriconProvider? = null
    private var monitor: PreferencesMonitor? = null

    @Volatile
    private var currentMusicId: Long? = null
    private var lastSetSong: Song? = null

    override fun onHook() {
        YLog.info("processName: $processName")
        LyricDiskCache.init(appInfo.dataDir)

        registerAppLifecycle {
            onCreate { onApplicationCreated() }
        }

        hookMediaSession()
    }

    private fun onApplicationCreated() {
        val app = hostApplication ?: return

        setupProvider(app)

        val session = YangDexKit.session(app.applicationInfo, app.classLoader)
        val created = session.use {
            PreferencesMonitor(
                dexkit = it,
                hostPackageName = packageName,
                callback = ::dispatchTranslationOptionChange
            )
        }
        monitor = created
        created.update(app.classLoader)
    }

    private fun hookMediaSession() {
        classOf<MediaSession>()
            .resolve()
            .apply {
                firstMethod {
                    name = "setPlaybackState"
                    parameters(classOf<PlaybackState>())
                }.hook {
                    after {
                        onPlaybackStateChanged(args[0] as? PlaybackState)
                    }
                }
                firstMethod {
                    name = "setMetadata"
                    parameters(classOf<MediaMetadata>())
                }.hook {
                    after {
                        (args.getOrNull(0) as? MediaMetadata)?.let(::onMetadataChanged)
                    }
                }
            }
    }

    private fun setupProvider(app: Application) {
        if (provider != null) return

        provider = LyriconFactory.createProvider(
            context = app,
            providerPackageName = PROVIDER_PACKAGE_NAME,
            playerPackageName = packageName,
            logo = ProviderLogo.fromSvg(LOGO_SVG)
        ).apply {
            register()
        }
    }

    private fun onMetadataChanged(mediaMetadata: MediaMetadata) {
        val metadata = TrackMetadata.from(mediaMetadata) ?: run {
            YLog.debug("Ignoring metadata without a supported media id")
            return
        }
        TrackMetadataStore.put(metadata)

        if (currentMusicId == metadata.id) return
        currentMusicId = metadata.id

        LyricDownloader.download(metadata.id, object : LyricDownloader.Callback {
            override fun onFinished(id: Long, song: Song) {
                if (isCurrentMusic(id)) setSong(song)
            }

            override fun onFailed(id: Long, e: Exception) {
                YLog.error("Download failed: id=$id", e)
            }
        })
    }

    private fun onPlaybackStateChanged(state: PlaybackState?) {
        provider?.player?.setPlaybackState(state)
    }

    private fun dispatchTranslationOptionChange(type: Int) {
        YLog.debug("Translation option changed: type=$type")

        provider?.player?.setDisplayTranslation(type == TranslationMode.TRANSLATION)
        provider?.player?.setDisplayRoma(type == TranslationMode.ROMANIZATION)
    }

    private fun setSong(song: Song) {
        if (song == lastSetSong) return
        provider?.player?.setSong(song)
        lastSetSong = song
    }

    private fun isCurrentMusic(id: Long): Boolean = currentMusicId == id
}
