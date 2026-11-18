/*
 * Copyright 2026 Proify, Tomakino
 * Licensed under the Apache License, Version 2.0
 * http://www.apache.org/licenses/LICENSE-2.0
 */

package io.github.proify.lyricon.kgprovider.xposed

import android.media.session.PlaybackState
import com.highcapable.yukihookapi.hook.entity.YukiBaseHooker
import com.highcapable.yukihookapi.hook.log.YLog
import io.github.proify.lyricon.dexkit.YangDexKit
import io.github.proify.lyricon.lyric.model.RichLyricLine
import io.github.proify.lyricon.lyric.model.Song
import io.github.proify.lyricon.provider.LyriconFactory
import io.github.proify.lyricon.provider.LyriconProvider
import io.github.proify.lyricon.provider.ProviderLogo
import kotlinx.coroutines.CoroutineScope
import kotlinx.coroutines.Dispatchers
import kotlinx.coroutines.SupervisorJob
import kotlinx.coroutines.launch

class KuGouHooker : YukiBaseHooker() {

    private val scope = CoroutineScope(Dispatchers.IO + SupervisorJob())

    @Volatile
    private var provider: LyriconProvider? = null

    @Volatile
    private var currentTrackId: String? = null

    @Volatile
    private var pushedTrackId: String? = null

    @Volatile
    private var pushedLyrics: List<RichLyricLine>? = null

    init {
        System.loadLibrary("dexkit")
    }

    override fun onHook() {
        if (!isLyricProcess) return
        val classLoader = hostClassLoader ?: return

        val dexkit = YangDexKit.session(appInfo, classLoader)

        MediaSessionHooks(
            classLoader = classLoader,
            onPlaybackStateChanged = ::onPlaybackStateChanged,
            onTrackChanged = ::onTrackChanged,
        ).install()

        scope.launch {
            dexkit.use {
                LyricFileHooks(
                    param = this@KuGouHooker,
                    dexkit = it,
                    scope = scope,
                    onLyricsLoaded = ::onLyricsLoaded,
                ).install()
            }
        }

        registerAppLifecycle(true) {
            onCreate { ensureProvider() }
        }
    }

    private val isLyricProcess: Boolean
        get() = processName.endsWith(":support") || processName.endsWith(".support")

    private fun onPlaybackStateChanged(state: PlaybackState?) {
        provider?.player?.setPlaybackState(state)
    }

    private fun onTrackChanged(metadata: TrackMetadata) {
        val id = metadata.id
        if (id == currentTrackId) return
        currentTrackId = id
        TrackStore.putMetadata(metadata)
        publishSong(id)
    }

    private fun onLyricsLoaded(lyrics: List<RichLyricLine>) {
        val id = currentTrackId ?: return
        TrackStore.attachLyrics(id, lyrics)
        publishSong(id)
    }

    private fun publishSong(id: String) {
        val metadata = TrackStore.metadataOf(id) ?: return
        val lyrics = TrackStore.lyricsOf(id) ?: return
        if (id == pushedTrackId && lyrics === pushedLyrics) return

        val song = Song(
            id = id,
            name = metadata.title,
            artist = metadata.artist,
            duration = metadata.duration.takeIf { it > 0 } ?: lyrics.lastOrNull()?.end ?: 0L,
            lyrics = lyrics,
        )

        pushedTrackId = id
        pushedLyrics = lyrics
        provider?.player?.setSong(song)
        YLog.info(tag = ProviderConfig.LOG_TAG, msg = "Pushed song: ${song.name}")
    }

    private fun ensureProvider() {
        if (provider != null) return
        val context = hostApplication ?: return

        try {
            provider = LyriconFactory.createProvider(
                context = context,
                providerPackageName = ProviderConfig.PACKAGE_NAME,
                playerPackageName = context.packageName,
                logo = ProviderLogo.fromBase64(ProviderConfig.LOGO_BASE64),
            ).apply {
                register()
                player.setDisplayTranslation(true)
            }
            YLog.info(
                tag = ProviderConfig.LOG_TAG,
                msg = "Provider initialized for ${context.packageName}",
            )
        } catch (e: Exception) {
            YLog.error(
                tag = ProviderConfig.LOG_TAG,
                msg = "Provider init failed: ${e.message}",
            )
        }
    }
}
