/*
 * Copyright 2026 Proify, Tomakino
 * Licensed under the Apache License, Version 2.0
 * http://www.apache.org/licenses/LICENSE-2.0
 */

package io.github.proify.lyricon.cmprovider.xposed

import android.media.MediaMetadata
import android.media.session.MediaSession
import android.media.session.PlaybackState
import com.highcapable.kavaref.KavaRef.Companion.resolve
import com.highcapable.kavaref.extension.classOf
import com.highcapable.yukihookapi.extension.kavaref.hook
import com.highcapable.yukihookapi.hook.entity.YukiBaseHooker
import com.highcapable.yukihookapi.hook.log.YLog
import io.github.proify.lyricon.cmprovider.xposed.Constants.ICON
import io.github.proify.lyricon.cmprovider.xposed.Constants.PROVIDER_PACKAGE_NAME
import io.github.proify.lyricon.dexkit.YangDexKit
import io.github.proify.lyricon.lyric.model.Song
import io.github.proify.lyricon.provider.LyriconFactory
import io.github.proify.lyricon.provider.LyriconProvider
import io.github.proify.lyricon.provider.ProviderLogo

class CloudMusic : YukiBaseHooker() {

    private var provider: LyriconProvider? = null
    private var monitor: PreferencesMonitor? = null

    private var currentMusicId: Long? = null
    private var lastSetSong: Song? = null

    override fun onHook() {
        YLog.info("processName: $processName")
        DiskCacheStore.init(appInfo.dataDir)

        registerAppLifecycle {
            onCreate {
                onApplicationCreated()
            }
        }

        hookMediaSession()
    }

    private fun onApplicationCreated() {
        val app = hostApplication!!

        setupProvider()

        val session = YangDexKit.session(
            app.applicationInfo,
            app.classLoader
        )

        session.use {
            monitor = PreferencesMonitor(
                dexkit = session,
                hostPackageName = packageName,
                callback = { type ->
                    dispatchTranslationOptionChange(type)
                }
            )
        }

        monitor?.update(app.classLoader)
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
                        val state = args[0] as? PlaybackState
                        onPlaybackStateChanged(state)
                    }
                }
                firstMethod {
                    name = "setMetadata"
                    parameters(classOf<MediaMetadata>())
                }.hook {
                    after {
                        val metadata = args.getOrNull(0) as? MediaMetadata
                        metadata?.let { onMetadataChanged(it) }
                    }
                }
            }
    }

    private fun setupProvider() {
        if (provider != null) return

        provider = LyriconFactory.createProvider(
            context = hostApplication!!,
            providerPackageName = PROVIDER_PACKAGE_NAME,
            playerPackageName = packageName,
            logo = ProviderLogo.fromSvg(ICON)
        ).apply {
            register()
        }
    }

    private fun onMetadataChanged(mediaMetadata: MediaMetadata) {
        val metadata = TrackMetadataManager.put(mediaMetadata) ?: run {
            YLog.debug("Ignoring metadata without a supported media id")
            return
        }

        if (isCurrentMusic(metadata.id)) return
        currentMusicId = metadata.id

        LyricDownloader.download(metadata.id, object : LyricDownloadCallback {
            override fun onLyricDownloadFinished(id: Long, song: Song) {
                if (isCurrentMusic(id)) {
                    setSong(song)
                }
            }

            override fun onLyricDownloadFailed(id: Long, e: Exception) {
                YLog.error("Download failed: id=$id", e)
            }
        })
    }

    private fun onPlaybackStateChanged(state: PlaybackState?) {
        provider?.player?.setPlaybackState(state)
    }

    private fun dispatchTranslationOptionChange(type: Int) {
        YLog.debug("Translation option changed: type=$type")

        provider?.player?.setDisplayTranslation(type == TRANSLATION_TYPE)
        provider?.player?.setDisplayRoma(type == ROMANIZATION_TYPE)
    }

    private fun setSong(song: Song?) {
        if (song != lastSetSong || song == null) {
            provider?.player?.setSong(song)
            lastSetSong = song
        }
    }

    private fun isCurrentMusic(id: Long): Boolean = currentMusicId == id

    companion object {
        private const val DISABLED_TRANSLATION_TYPE = -1
        private const val TRANSLATION_TYPE = 0
        private const val ROMANIZATION_TYPE = 1
    }
}