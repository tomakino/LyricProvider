/*
 * Copyright 2026 Proify, Tomakino
 * Licensed under the Apache License, Version 2.0
 * http://www.apache.org/licenses/LICENSE-2.0
 */

@file:Suppress("unused")

package io.github.proify.lyricon.bodianprovider.xposed

import android.app.Application
import android.media.MediaMetadata
import android.media.session.PlaybackState
import com.highcapable.kavaref.KavaRef.Companion.resolve
import com.highcapable.yukihookapi.hook.entity.YukiBaseHooker
import com.highcapable.yukihookapi.hook.log.YLog
import io.github.proify.lyricon.bodianprovider.BuildConfig
import io.github.proify.lyricon.bodianprovider.xposed.Bodian.pendingLyricPaths
import io.github.proify.lyricon.lyric.model.Song
import io.github.proify.lyricon.lyric.model.lyricMetadataOf
import io.github.proify.lyricon.provider.LyriconFactory
import io.github.proify.lyricon.provider.LyriconProvider
import io.github.proify.lyricon.provider.ProviderLogo
import kotlinx.coroutines.CoroutineScope
import kotlinx.coroutines.Dispatchers
import kotlinx.coroutines.SupervisorJob
import kotlinx.coroutines.launch
import java.util.concurrent.ConcurrentHashMap

/**
 * 波点音乐（[Constants.MUSIC_PACKAGE_NAME]）歌词提供者。
 *
 * 波点是 Flutter 壳 + 酷我播放内核，两条信息各走各的路：
 *
 * - **播放状态与曲目信息**：`MediaSessionCompat` → 框架层 `MediaSession`，
 *   其中 `METADATA_KEY_MEDIA_ID` 就是酷我的 `rid`。
 * - **歌词**：Dart 侧下载后落盘，把绝对路径经 MethodChannel 交给原生，
 *   由 [FlutterLyricBridge] 截获。
 *
 * 两条路谁先到都有可能，所以用 [pendingLyricPaths] 做一次会合。
 */
object Bodian : YukiBaseHooker() {

    const val TAG = "BodianProvider"

    private val scope = CoroutineScope(Dispatchers.IO + SupervisorJob())

    private var provider: LyriconProvider? = null
    private var application: Application? = null

    @Volatile
    private var currentRid: String? = null

    @Volatile
    private var lastSong: Song? = null

    /** rid -> Dart 给的歌词文件路径，等 MediaSession 那边对上号 */
    private val pendingLyricPaths = ConcurrentHashMap<String, String>()

    /** 已经成功出过歌词的 rid，避免重复解析 */
    private val resolved = ConcurrentHashMap.newKeySet<String>()

    override fun onHook() {
        YLog.info(tag = TAG, msg = "attached: $packageName / $processName")

        onAppLifecycle {
            onCreate {
                application = this
                DiskSongCache.initialize(this)
                LyricSource.initialize(this)
                if (BuildConfig.DEBUG) LyricSource.dumpLyricDir()

                // 放在 Application 创建之后：此时 Flutter 引擎的 dex 一定可加载
                FlutterLyricBridge.install(classLoader) { rid, path, status, isMv ->
                    onLyricDelivered(rid, path, status, isMv)
                }
            }
        }

        hookMediaSession()
    }

    // ---------------------------------------------------------------- Provider

    /**
     * 波点的播放服务进程在不同版本里位置不一，所以不写死进程名：
     * 所有进程都挂 MediaSession，谁先真正收到回调谁才创建 Provider，
     * 天然避免多进程重复注册。
     */
    private fun ensureProvider(): LyriconProvider? {
        provider?.let { return it }

        val context = application ?: appContext ?: return null
        val created = LyriconFactory.createProvider(
            context = context,
            providerPackageName = Constants.PROVIDER_PACKAGE_NAME,
            playerPackageName = Constants.MUSIC_PACKAGE_NAME,
            logo = ProviderLogo.fromSvg(Constants.ICON),
            processName = processName
        )
        created.register()
        provider = created

        YLog.debug(tag = TAG, msg = "provider registered in process=$processName")
        return created
    }

    // ---------------------------------------------------------------- Hooks

    private fun hookMediaSession() {
        "android.media.session.MediaSession".toClass().resolve().apply {
            firstMethod {
                name = "setPlaybackState"
                parameters(PlaybackState::class.java)
            }.hook {
                after {
                    val state = args[0] as? PlaybackState ?: return@after
                    ensureProvider()?.player?.setPlaybackState(state)
                }
            }

            firstMethod {
                name = "setMetadata"
                parameters(MediaMetadata::class.java)
            }.hook {
                after {
                    val metadata = args[0] as? MediaMetadata ?: return@after
                    onMetadataChanged(metadata)
                }
            }
        }
    }

    // ---------------------------------------------------------------- 曲目

    private fun onMetadataChanged(metadata: MediaMetadata) {
        val rid = metadata.getString(MediaMetadata.METADATA_KEY_MEDIA_ID)
        if (rid.isNullOrBlank() || rid == currentRid) return

        currentRid = rid
        val cached = MediaMetadataCache.save(metadata)

        YLog.debug(
            tag = TAG,
            msg = "song changed: rid=$rid, title=${cached?.title}, " +
                    "artist=${cached?.artist}, duration=${cached?.duration}"
        )

        val diskCached = DiskSongCache.get(rid)
        if (diskCached != null && !diskCached.lyrics.isNullOrEmpty()) {
            publish(diskCached)
            resolved.add(rid)
        } else {
            publish(placeholderSong(rid))
        }

        // Dart 可能已经先把路径送过来了；没有也照样试一次按 rid 兜底扫盘
        loadLyric(rid, pendingLyricPaths.remove(rid))
    }

    private fun onLyricDelivered(rid: String?, path: String, status: Int?, isMv: Boolean) {
        val key = rid ?: currentRid ?: return
        if (status != null && status == 0) {
            YLog.debug(tag = TAG, msg = "lyricStatus=0 for rid=$key, still trying $path")
        }

        if (key == currentRid) {
            loadLyric(key, path)
        } else {
            pendingLyricPaths[key] = path
        }
    }

    private fun loadLyric(rid: String, lyricPath: String?) {
        if (resolved.contains(rid)) return

        scope.launch {
            val file = LyricSource.locate(rid, lyricPath)
            if (file == null) {
                YLog.debug(tag = TAG, msg = "no lyric file yet for rid=$rid (path=$lyricPath)")
                return@launch
            }

            val metadata = MediaMetadataCache.get(rid)
            val lines = LyricSource.parse(file, metadata?.duration ?: 0L)
            if (lines.isEmpty()) {
                YLog.warn(tag = TAG, msg = "empty lyric parsed for rid=$rid from ${file.name}")
                return@launch
            }

            val song = Song(
                id = rid,
                name = metadata?.title,
                artist = metadata?.artist,
                duration = metadata?.duration ?: lines.lastOrNull()?.end ?: 0L,
                lyrics = lines
            )

            DiskSongCache.put(song)
            resolved.add(rid)

            if (rid == currentRid) publish(song)
        }
    }

    private fun placeholderSong(rid: String): Song {
        val metadata = MediaMetadataCache.get(rid)
        return Song(
            id = rid,
            name = metadata?.title,
            artist = metadata?.artist,
            duration = metadata?.duration ?: 0L,
            metadata = lyricMetadataOf("placeholder" to "true")
        )
    }

    private fun publish(song: Song?) {
        if (song == null || song == lastSong) return
        lastSong = song
        ensureProvider()?.player?.setSong(song)
    }
}
