/*
 * Copyright 2026 Proify, Tomakino
 * Licensed under the Apache License, Version 2.0
 * http://www.apache.org/licenses/LICENSE-2.0
 */

package io.github.proify.lyricon.qmhdprovider.xposed

import android.content.Context
import android.media.MediaMetadata
import android.media.session.PlaybackState
import android.os.Bundle
import com.highcapable.kavaref.KavaRef.Companion.resolve
import com.highcapable.yukihookapi.hook.entity.YukiBaseHooker
import com.highcapable.yukihookapi.hook.log.YLog
import io.github.proify.lrckit.EnhanceLrcParser
import io.github.proify.lyricon.lyric.model.RichLyricLine
import io.github.proify.lyricon.lyric.model.Song
import io.github.proify.lyricon.provider.LyriconFactory
import io.github.proify.lyricon.provider.LyriconProvider
import io.github.proify.lyricon.provider.ProviderLogo
import io.github.proify.qrckit.QrcDownloader
import kotlinx.coroutines.CoroutineScope
import kotlinx.coroutines.Dispatchers
import kotlinx.coroutines.SupervisorJob
import kotlinx.coroutines.launch
import java.util.concurrent.ConcurrentHashMap

/**
 * QQ音乐HD 歌词提供器 Hook
 *
 * 策略：
 * 1. Hook RemoteControlManager 的 notifyMetaChangeToSystem 方法，
 *    直接从 SongInfo.getSongId() 获取真实 songId
 * 2. setMetadata 触发时先用内嵌 LRC 立即显示行级歌词
 * 3. 异步用 songId 调 QrcDownloader 下载 QRC 逐字歌词并替换
 */
object QQMusicHD : YukiBaseHooker() {

    private const val TAG = "Lyricon_QQMusicHD"

    private const val PREF_NAME_QQMUSIC = "qqmusicplayer"
    private const val KEY_DISPLAY_TRANS = "showTransLyric"
    private const val KEY_DISPLAY_ROMA = "showRomaLyric"
    private const val BUNDLE_KEY_LYRIC = "android.media.metadata.LYRIC"

    private var lyriconProvider: LyriconProvider? = null
    @Volatile private var currentSongId: String? = null

    /** 由 RemoteControlManager hook 捕获的真实 songId */
    @Volatile private var latestSongId: String? = null

    private val scope = CoroutineScope(Dispatchers.IO + SupervisorJob())
    private val downloadingIds = ConcurrentHashMap.newKeySet<String>()

    /** QRC 歌词缓存：songId → lyrics，LRU 淘汰，最多缓存 20 首 */
    private val qrcCache = object : LinkedHashMap<String, List<RichLyricLine>>(20, 0.75f, true) {
        override fun removeEldestEntry(eldest: MutableMap.MutableEntry<String, List<RichLyricLine>>?) =
            size > 20
    }

    override fun onHook() {
        val loader = appClassLoader ?: return

        onAppLifecycle {
            onCreate { setupLyriconProvider(this) }
        }

        // ========== Hook RemoteControlManager 获取真实 songId ==========
        // 调用链：notifyMetaChangeToSystem → updateMetaData → MediaSession.setMetadata()
        // 仅按参数类型匹配，不依赖混淆后的方法名
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
                        try {
                            val songId = songInfo.javaClass
                                .getMethod("getSongId")
                                .invoke(songInfo) as Long
                            latestSongId = songId.toString()
                        } catch (e: Exception) {
                            YLog.error("$TAG: Failed to extract songId", e)
                        }
                    }
                }
            } ?: YLog.error("$TAG: RemoteControlManager class not found")

        // ========== SharedPreferences Hook ==========
        "android.app.SharedPreferencesImpl\$EditorImpl".toClass(loader)
            .resolve()
            .firstMethod {
                name = "putBoolean"
                parameters(String::class.java, Boolean::class.java)
            }.hook {
                after {
                    val key = args[0] as String
                    val value = args[1] as Boolean
                    when (key) {
                        KEY_DISPLAY_TRANS -> lyriconProvider?.player?.setDisplayTranslation(value)
                        KEY_DISPLAY_ROMA -> lyriconProvider?.player?.setDisplayRoma(value)
                    }
                }
            }

        // ========== MediaSession Hook ==========
        "android.media.session.MediaSession".toClass(loader)
            .resolve().apply {
                firstMethod {
                    name = "setPlaybackState"
                    parameters(PlaybackState::class.java)
                }.hook {
                    after {
                        lyriconProvider?.player?.setPlaybackState(args[0] as? PlaybackState)
                    }
                }

                firstMethod {
                    name = "setMetadata"
                    parameters(MediaMetadata::class.java)
                }.hook {
                    after {
                        (args[0] as? MediaMetadata)?.let { handleMetadata(it) }
                    }
                }
            }
    }

    // ========== 歌曲处理 ==========

    private fun handleMetadata(metadata: MediaMetadata) {
        val songId = latestSongId ?: return
        if (songId == currentSongId) return
        currentSongId = songId

        val title = metadata.getString(MediaMetadata.METADATA_KEY_TITLE)
        val artist = metadata.getString(MediaMetadata.METADATA_KEY_ARTIST)
        val duration = metadata.getLong(MediaMetadata.METADATA_KEY_DURATION)

        // 1. 已有 QRC 缓存，直接用
        synchronized(qrcCache) { qrcCache[songId] }?.let { cached ->
            lyriconProvider?.player?.setSong(
                Song(id = songId, name = title, artist = artist,
                    duration = duration, lyrics = cached)
            )
            return
        }

        // 2. 先用内嵌 LRC 立即显示
        val lrcLines = extractLyric(metadata)?.let {
            EnhanceLrcParser.parse(it, duration).lines
                .filter { line -> !line.text.isNullOrBlank() }
        }
        lyriconProvider?.player?.setSong(
            Song(id = songId, name = title, artist = artist,
                duration = duration, lyrics = lrcLines)
        )

        // 3. 异步下载 QRC 逐字歌词
        if (!downloadingIds.add(songId)) return
        scope.launch {
            try {
                val qrcLyrics = QrcDownloader.downloadLyrics(songId)
                    .parsedLyric.richLyricLines
                    .removeInvalidTranslation()

                if (qrcLyrics.isNotEmpty()) {
                    synchronized(qrcCache) { qrcCache[songId] = qrcLyrics }
                    if (currentSongId == songId) {
                        lyriconProvider?.player?.setSong(
                            Song(id = songId, name = title, artist = artist,
                                duration = duration, lyrics = qrcLyrics)
                        )
                    }
                }
            } catch (e: Exception) {
                YLog.error("$TAG: QRC download failed for songId=$songId", e)
            } finally {
                downloadingIds.remove(songId)
            }
        }
    }

    // ========== 工具方法 ==========

    private fun extractLyric(metadata: MediaMetadata): String? = try {
        val bundleField = MediaMetadata::class.java.getDeclaredField("mBundle")
        bundleField.isAccessible = true
        (bundleField.get(metadata) as? Bundle)?.getString(BUNDLE_KEY_LYRIC)
    } catch (_: Exception) { null }

    private fun setupLyriconProvider(context: Context) {
        val provider = LyriconFactory.createProvider(
            context = context,
            providerPackageName = Constants.PROVIDER_PACKAGE_NAME,
            playerPackageName = Constants.MUSIC_PACKAGE_NAME,
            logo = ProviderLogo.fromSvg(Constants.ICON)
        )

        val prefs = context.getSharedPreferences(PREF_NAME_QQMUSIC, Context.MODE_PRIVATE)
        provider.player.apply {
            setDisplayTranslation(prefs.getBoolean(KEY_DISPLAY_TRANS, false))
            setDisplayRoma(prefs.getBoolean(KEY_DISPLAY_ROMA, false))
        }

        provider.register()
        this.lyriconProvider = provider
    }

    private fun List<RichLyricLine>.removeInvalidTranslation() = apply {
        forEach { if (it.translation?.trim() == "//") it.translation = null }
    }
}
