/*
 * Copyright 2026 Proify, Tomakino
 * Licensed under the Apache License, Version 2.0
 * http://www.apache.org/licenses/LICENSE-2.0
 */

package io.github.proify.lyricon.qmhdprovider.xposed

import android.content.Context
import android.media.MediaMetadata
import android.media.session.MediaSession
import android.media.session.PlaybackState
import com.highcapable.kavaref.KavaRef.Companion.resolve
import com.highcapable.yukihookapi.hook.entity.YukiBaseHooker
import com.highcapable.yukihookapi.hook.log.YLog
import io.github.proify.lyricon.lyric.model.RichLyricLine
import io.github.proify.lyricon.lyric.model.Song
import io.github.proify.lyricon.provider.LyriconFactory
import io.github.proify.lyricon.provider.LyriconProvider
import io.github.proify.lyricon.provider.ProviderLogo
import io.github.proify.qrckit.QrcDownloader
import kotlinx.coroutines.CoroutineScope
import kotlinx.coroutines.Dispatchers
import kotlinx.coroutines.SupervisorJob
import kotlinx.coroutines.cancel
import kotlinx.coroutines.launch
import java.util.concurrent.ConcurrentHashMap

/**
 * QQ音乐 HD 版 Lyricon 适配器
 */
object QQMusicHD : YukiBaseHooker() {

    private const val TAG = "Lyricon_QQMusicHD"
    private const val KEY_OPEN_TRANSLATION = "KEY_OPEN_TRANSLATION"
    private const val PREF_NAME_QQMUSIC = "qqmusic"

    private var provider: LyriconProvider? = null

    @Volatile
    private var currentSongId: String? = null

    @Volatile
    private var pendingSongId: String? = null

    private val internalScope = CoroutineScope(Dispatchers.IO + SupervisorJob())
    private val ongoingDownloads = ConcurrentHashMap.newKeySet<String>()

    private var isCreated = false
    override fun onHook() {
        val loader = appClassLoader ?: return

        onAppLifecycle {
            onCreate {
                if (isCreated) return@onCreate; isCreated = true

                DiskSongCache.initialize(this)
                initLyriconProvider(this)
            }
            onTerminate { internalScope.cancel() }
        }

        // ========== Hook RemoteControlManager 以拦截真实 SongId ==========
        "com.tencent.qqmusic.qplayer.core.player.controller.RemoteControlManager"
            .toClassOrNull(loader)
            ?.resolve()
            ?.apply {
                firstMethod {
                    parameters(
                        "com.tencent.qqmusic.openapisdk.model.SongInfo",
                        "com.tencent.qqmusic.openapisdk.core.player.IMediaMetaDataInterface"
                    )
                }.hook {
                    before {
                        val songInfo = args[0] ?: return@before
                        runCatching {
                            // 使用反射获取 SongId，此处保持 Long 到 String 的转换
                            val id =
                                songInfo.javaClass.getMethod("getSongId").invoke(songInfo) as Long
                            pendingSongId = id.toString()
                        }.onFailure { e ->
                            YLog.error("$TAG: Failed to extract songId from SongInfo", e)
                        }
                    }
                }
            } ?: YLog.error("$TAG: RemoteControlManager class not found")

        // ========== Hook MMKV 实时监听翻译开关 ==========
        "com.tencent.mmkv.MMKV".toClassOrNull(loader)
            ?.resolve()
            ?.apply {
                firstMethod {
                    name = "putInt"
                    parameters(String::class.java, Int::class.java)
                }.hook {
                    after {
                        val key = args[0] as? String
                        val value = args[1] as? Int
                        if (key == KEY_OPEN_TRANSLATION && value != null) {
                            // 0 为开启，非 0 为关闭
                            provider?.player?.setDisplayTranslation(value == 0)
                        }
                    }
                }
            }

        // ========== Hook MediaSession 同步播放状态与元数据 ==========
        MediaSession::class.java.resolve().apply {
            firstMethod {
                name = "setPlaybackState"
                parameters(PlaybackState::class.java)
            }.hook {
                after {
                    val state = args[0] as? PlaybackState
                    provider?.player?.setPlaybackState(state)
                }
            }

            firstMethod {
                name = "setMetadata"
                parameters(MediaMetadata::class.java)
            }.hook {
                after {
                    val metadata = args[0] as? MediaMetadata
                    metadata?.let { processMetadata(it) }
                }
            }
        }
    }

    /**
     * 处理歌曲元数据变更并触发歌词下载
     */
    private fun processMetadata(metadata: MediaMetadata) {
        val songId = pendingSongId ?: return
        if (songId == currentSongId) return
        currentSongId = songId

        val title = metadata.getString(MediaMetadata.METADATA_KEY_TITLE) ?: ""
        val artist = metadata.getString(MediaMetadata.METADATA_KEY_ARTIST) ?: ""
        val duration = metadata.getLong(MediaMetadata.METADATA_KEY_DURATION)

        // 优先检查本地缓存
        DiskSongCache.get(songId)?.let { cachedSong ->
            provider?.player?.setSong(cachedSong)
            return
        }

        // 无缓存时先更新基本信息，再异步下载歌词
        val initialSong = Song(id = songId, name = title, artist = artist, duration = duration)
        provider?.player?.setSong(initialSong)

        if (!ongoingDownloads.add(songId)) return

        internalScope.launch {
            try {
                val qrcResult = QrcDownloader.downloadLyrics(songId)
                val richLyrics = qrcResult.parsedLyric.richLyricLines.filterInvalidTranslation()

                if (richLyrics.isNotEmpty()) {
                    val fullSong = initialSong.copy(lyrics = richLyrics)
                    DiskSongCache.put(fullSong)

                    if (songId == currentSongId) {
                        provider?.player?.setSong(fullSong)
                    }
                }
            } catch (e: Exception) {
                YLog.error("$TAG: QRC download failed for songId=$songId", e)
            } finally {
                ongoingDownloads.remove(songId)
            }
        }
    }

    /**
     * 初始化 Lyricon 提供者
     */
    private fun initLyriconProvider(context: Context) {
        val newProvider = LyriconFactory.createProvider(
            context = context,
            providerPackageName = Constants.PROVIDER_PACKAGE_NAME,
            playerPackageName = Constants.MUSIC_PACKAGE_NAME,
            logo = ProviderLogo.fromSvg(Constants.ICON)
        )

        // 读取初始翻译配置
        runCatching {
            val prefs = context.getSharedPreferences(PREF_NAME_QQMUSIC, Context.MODE_PRIVATE)
            val isTranslationOn = prefs.getInt(KEY_OPEN_TRANSLATION, 1) == 0
            newProvider.player.setDisplayTranslation(isTranslationOn)
        }.onFailure { e ->
            YLog.error("$TAG: Failed to read initial translation setting", e)
        }

        newProvider.register()
        this.provider = newProvider
    }

    /**
     * 过滤无效的翻译标记（如 "//"）
     */
    private fun List<RichLyricLine>.filterInvalidTranslation(): List<RichLyricLine> {
        return this.onEach { line ->
            if (line.translation?.trim() == "//") {
                line.translation = null
            }
        }
    }
}