/*
 * Copyright 2026 Proify, Tomakino
 * Licensed under the Apache License, Version 2.0
 * http://www.apache.org/licenses/LICENSE-2.0
 */
package io.github.proify.lyricon.cmprovider.xposed

import android.app.Application
import android.media.MediaMetadata
import android.media.session.PlaybackState
import android.util.Log
import com.highcapable.kavaref.KavaRef.Companion.resolve
import com.highcapable.yukihookapi.hook.entity.YukiBaseHooker
import com.highcapable.yukihookapi.hook.log.YLog
import io.github.proify.extensions.json
import io.github.proify.lyricon.cmprovider.xposed.Constants.ICON
import io.github.proify.lyricon.cmprovider.xposed.Constants.PROVIDER_PACKAGE_NAME
import io.github.proify.lyricon.cmprovider.xposed.PreferencesMonitor.PreferenceCallback
import io.github.proify.lyricon.lyric.model.Song
import io.github.proify.lyricon.provider.LyriconFactory
import io.github.proify.lyricon.provider.LyriconProvider
import io.github.proify.lyricon.provider.ProviderLogo
import io.github.proify.lyricon.yrckit.download.response.LyricResponse
import kotlinx.serialization.ExperimentalSerializationApi
import kotlinx.serialization.json.encodeToStream
import org.luckypray.dexkit.DexKitBridge
import java.io.File

/**
 * 网易云音乐模块主入口，根据进程名选择性启用歌词提供者钩子。
 */
object CloudMusic : YukiBaseHooker() {
    private const val TAG = "CloudMusicProvider"
    private val providerManager by lazy { LyricProviderManager() }

    init {
        System.loadLibrary("dexkit")
    }

    override fun onHook() {
        when (processName) {
            packageName,
            "$packageName:play" -> {
                YLog.debug(tag = TAG, msg = "Hooking $processName")
                providerManager.onHook()
            }
        }
    }

    /**
     * 歌词提供者核心管理器，负责设置钩子、管理提供者生命周期、处理歌词下载与缓存。
     */
    private class LyricProviderManager : DownloadCallback {
        private var lyricProvider: LyriconProvider? = null
        private var lastSetSong: Song? = null
        private var currentMusicId: Long = 0

        private var dexKitBridge: DexKitBridge? = null
        private var preferencesMonitor: PreferencesMonitor? = null
        private var lyricInterceptor: LyricInterceptor? = null

        /** 累积内部 Hook 拦截到的单行 LRC 文本，key 为 songId */
        private val lrcLineAccumulator = mutableMapOf<Long, MutableSet<String>>()
        private var lastCacheWriteLineCount = 0

        private var translationType: Int = 114514

        // ---------------------------------- 入口与初始化 ----------------------------------

        fun onHook() {
            YLog.debug("Hooking, processName= $processName")

            dexKitBridge = DexKitBridge.create(appInfo.sourceDir)
            preferencesMonitor = PreferencesMonitor(dexKitBridge!!, object : PreferenceCallback {
                override fun onTranslationOptionChanged(type: Int) {
                    if (translationType == type) return; translationType = type
                    YLog.debug("type=$type")
                    lyricProvider?.player?.setDisplayTranslation(type == 0)
                    lyricProvider?.player?.setDisplayRoma(type == 1)
                }
            })

            lyricInterceptor = LyricInterceptor(dexKitBridge!!, appClassLoader!!)
            lyricInterceptor?.onLyricIntercepted = { lyric, trans, roma ->
                onInternalLyricReceived(lyric, trans, roma)
            }
            lyricInterceptor?.hook()

            onAppLifecycle {
                onCreate {
                    setupProvider()
                }
            }

            rehookAfterTinkerLoad(appClassLoader!!)
            hookMediaSession()
        }

        /**
         * 在 Tinker 热更新后重新挂钩必要的类（如偏好设置监听）。
         */
        private fun rehookAfterTinkerLoad(classLoader: ClassLoader) {
            "com.tencent.tinker.loader.TinkerLoader".toClass(appClassLoader)
                .resolve()
                .method { name = "tryLoad" }
                .forEach {
                    it.hook {
                        after {
                            val app = args[0] as Application
                            rehookAfterTinkerLoad(app.classLoader)
                        }
                    }
                }

            preferencesMonitor?.update(classLoader)
            lyricInterceptor?.rehook(classLoader)
        }

        /**
         * 初始化并注册 LyriconProvider。
         */
        private fun setupProvider() {
            val application = appContext ?: return
            lyricProvider?.destroy()

            lyricProvider = LyriconFactory.createProvider(
                context = application,
                providerPackageName = PROVIDER_PACKAGE_NAME,
                playerPackageName = application.packageName,
                logo = ProviderLogo.fromSvg(ICON)
            ).apply {
                val type = preferencesMonitor?.getTranslationType() ?: -1
                translationType = type
                player.setDisplayTranslation(type == 0)
                player.setDisplayRoma(type == 1)

                register()
            }

            YLog.info(tag = TAG, msg = "Provider registered")
        }

        // ---------------------------------- MediaSession 钩子 ----------------------------------

        private fun hookMediaSession() {
            "android.media.session.MediaSession".toClass()
                .resolve()
                .apply {
                    firstMethod {
                        name = "setMetadata"
                        parameters(MediaMetadata::class.java)
                    }.hook {
                        after {
                            val metadata = args[0] as? MediaMetadata ?: return@after
                            val data = MediaMetadataCache.save(metadata) ?: return@after
                            if (currentMusicId == data.id) return@after

                            currentMusicId = data.id
                            onSongChanged(data)
                        }
                    }

                    firstMethod {
                        name = "setPlaybackState"
                        parameters(PlaybackState::class.java)
                    }.hook {
                        after {
                            val state = args[0] as? PlaybackState
                            lyricProvider?.player?.setPlaybackState(state)
                        }
                    }
                }
        }

        // ---------------------------------- 下载回调实现 ----------------------------------

        override fun onDownloadFinished(id: Long, response: LyricResponse) {
            YLog.debug(tag = TAG, msg = "Download finished: $id")
            writeToLocalLyricCache(id, response)
        }

        override fun onDownloadFailed(id: Long, e: Exception) {
            YLog.error(tag = TAG, msg = "Download failed: $id, e=$e")
        }

        // ---------------------------------- 本地缓存读写 ----------------------------------

        private fun getDownloadLyricFile(id: Long): File =
            File(Constants.getDownloadLyricDirectory(appContext!!), id.toString())

        @OptIn(ExperimentalSerializationApi::class)
        private fun writeToLocalLyricCache(id: Long, response: LyricResponse) {
            // 如果 EApi 没有返回任何歌词内容，跳过缓存写入（避免覆盖内部 Hook 已写入的缓存）
            val hasLyric = response.lrc?.lyric?.isNotBlank() == true
                    || response.yrc?.lyric?.isNotBlank() == true
            if (!hasLyric) {
                Log.d(TAG, "EApi returned empty lyrics for $id, skipping cache write")
                return
            }

            val outputFile = getDownloadLyricFile(id)
            val cacheEntry = LocalLyricCache(
                musicId = id,
                lrc = response.lrc?.lyric,
                lrcTranslateLyric = response.tlyric?.lyric,
                yrc = response.yrc?.lyric,
                yrcTranslateLyric = response.ytlrc?.lyric,
                pureMusic = response.pureMusic,
                roma = response.romalrc?.lyric
            )

            outputFile.outputStream().use { outputStream ->
                json.encodeToStream(cacheEntry, outputStream)
            }

            loadLyricFromFile(cacheSource = "network", id = id, cacheFile = outputFile)
        }

        /**
         * 从本地缓存文件加载并设置歌词。
         */
        private fun loadLyricFromFile(cacheSource: String, id: Long, cacheFile: File) {
            YLog.debug(tag = TAG, msg = "Load lyric file: $cacheSource, file=$cacheFile")

            val metadata = MediaMetadataCache.get(id) ?: return
            loadAndSetSong(metadata, cacheFile)
        }

        // ---------------------------------- 歌曲变更处理 ----------------------------------

        private fun onSongChanged(metadata: Metadata) {
            val newMusicId = metadata.id
            Log.i(TAG, "Song changed: id=$newMusicId, title=$metadata.title, artist=$metadata.artist")

            // 清空上一首歌的 LRC 行累积器，并写入缓存
            flushLrcAccumulator(currentMusicId)
            lrcLineAccumulator.remove(newMusicId)
            lastCacheWriteLineCount = 0

            // 写入共享文件，让 MAIN 进程也能知道当前的 songId + 歌曲信息
            writeSharedCurrentSongId(metadata)

            val localCacheFile = getDownloadLyricFile(newMusicId)
            if (localCacheFile.exists()) {
                Log.d(TAG, "Cache hit for $newMusicId")
                loadLyricFromFile(
                    cacheSource = "localCache",
                    id = currentMusicId,
                    cacheFile = localCacheFile
                )
            } else {
                Log.d(TAG, "Cache miss for $newMusicId, starting EApi download")
                Downloader.download(newMusicId, this)
            }
        }

        /**
         * 当内部 Hook 拦截到网易云 App 自身解析的歌词时回调。
         * 作为 EApi 的 fallback：仅当 EApi 尚未为此歌曲提供歌词时才使用。
         */
        /**
         * 内部 Hook 拦截到的单行 LRC 文本。
         * 累积所有行，拼成完整 LRC 后逐次更新歌词显示 + 写入缓存。
         *
         * 注意：LRC 行来自 MAIN 进程，而 setMetadata/songId 来自 PLAY 进程。
         * 通过 sharedCurrentSong 文件在两个进程间同步当前的 songId。
         */
        private fun onInternalLyricReceived(lyricLine: String, transLine: String?, romaLine: String?) {
            // 从 PLAY 进程写入的共享文件读取真实 songId
            val realSongId = readSharedCurrentSongId()
            if (realSongId != null && realSongId != currentMusicId) {
                val oldId = currentMusicId
                currentMusicId = realSongId
                // 把 fallback ID 下累积的行转移到真实 ID 下
                val transferred = lrcLineAccumulator.remove(oldId)
                if (transferred != null && transferred.isNotEmpty()) {
                    lrcLineAccumulator.getOrPut(currentMusicId) { LinkedHashSet() }.addAll(transferred)
                    Log.i(TAG, "Transferred ${transferred.size} LRC lines from fallback $oldId to real $currentMusicId")
                } else {
                    lrcLineAccumulator.remove(currentMusicId)
                }
                lastCacheWriteLineCount = 0
                if (MediaMetadataCache.get(currentMusicId) == null) {
                    MediaMetadataCache.savePlaceholder(currentMusicId)
                }
                Log.i(TAG, "Switched to PLAY process songId: $currentMusicId")
            }

            // 如果仍为 0（PLAY 进程尚未 setMetadata），用 fallback ID
            if (currentMusicId == 0L) {
                currentMusicId = lyricLine.hashCode().toLong().let { if (it < 0) -it else it }
                MediaMetadataCache.savePlaceholder(currentMusicId)
                Log.i(TAG, "Using fallback ID: $currentMusicId")
            }

            val metadata = MediaMetadataCache.get(currentMusicId) ?: return

            // 累积 LRC 行
            val lines = lrcLineAccumulator.getOrPut(currentMusicId) { LinkedHashSet() }
            lines.add(lyricLine.trim())
            val fullLrc = lines.joinToString("\n")

            val cacheEntry = LocalLyricCache(musicId = metadata.id, lrc = fullLrc)

            try {
                val song = cacheEntry.toSong()
                // 优先用本地 metadata，没有则从 PLAY 进程共享文件读取
                val (sharedTitle, sharedArtist) = readSharedSongMeta()
                song.name = metadata.title ?: sharedTitle ?: song.name
                song.artist = metadata.artist ?: sharedArtist ?: song.artist
                if (song.lyrics.isNullOrEmpty()) return

                Log.i(TAG, "Internal lyric: ${lines.size} lines for id=$currentMusicId")
                setSong(song)

                // 每累积 >= 3 行即写入缓存（使用真实 songId 的路径，PLAY 进程可读到）
                val cacheFile = getDownloadLyricFile(metadata.id)
                val lineCount = lines.size
                if (lineCount >= 3 || lineCount == 1) {
                    cacheFile.outputStream().use { json.encodeToStream(cacheEntry, it) }
                }
            } catch (e: Exception) {
                Log.e(TAG, "Internal lyric failed: ${e.message}", e)
            }
        }

        /** PLAY 进程写入、MAIN 进程读取的共享元数据文件 */
        private fun sharedMetaFile(): File =
            File(Constants.getDownloadLyricDirectory(appContext!!), "_current_song")

        private fun writeSharedCurrentSongId(metadata: Metadata) {
            try {
                // id 放第一行，title 第二行，artist 第三行
                sharedMetaFile().writeText("${metadata.id}\n${metadata.title ?: ""}\n${metadata.artist ?: ""}")
            } catch (_: Exception) { }
        }

        private fun readSharedCurrentSongId(): Long? {
            return try {
                val text = sharedMetaFile().takeIf { it.exists() }?.readText() ?: return null
                text.lines().firstOrNull()?.trim()?.toLongOrNull()
            } catch (_: Exception) { null }
        }

        /** 读取共享的歌曲名和艺术家 */
        private fun readSharedSongMeta(): Pair<String?, String?> {
            return try {
                val text = sharedMetaFile().takeIf { it.exists() }?.readText() ?: return null to null
                val lines = text.lines()
                val title = lines.getOrNull(1)?.trim()?.takeIf { it.isNotEmpty() }
                val artist = lines.getOrNull(2)?.trim()?.takeIf { it.isNotEmpty() }
                title to artist
            } catch (_: Exception) { null to null }
        }

        /** 歌曲切换时将累积的 LRC 写入缓存 */
        private fun flushLrcAccumulator(songId: Long) {
            val lines = lrcLineAccumulator[songId] ?: return
            if (lines.isEmpty()) return
            val metadata = MediaMetadataCache.get(songId) ?: return

            val fullLrc = lines.joinToString("\n")
            val cacheEntry = LocalLyricCache(musicId = songId, lrc = fullLrc)
            try {
                val cacheFile = getDownloadLyricFile(songId)
                cacheFile.outputStream().use { json.encodeToStream(cacheEntry, it) }
                Log.i(TAG, "LRC accumulator flushed to cache: ${lines.size} lines for $songId")
            } catch (e: Exception) {
                Log.e(TAG, "Failed to flush LRC cache: ${e.message}", e)
            }
        }

        /**
         * 同步加载缓存文件并设置歌曲，若无有效歌词则回退到基本歌曲信息。
         */
        private fun loadAndSetSong(metadata: Metadata, cacheFile: File?) {
            val id = metadata.id

            var songToSet = Song(
                id = id.toString(),
                name = metadata.title,
                artist = metadata.artist,
                duration = metadata.duration
            )

            if (cacheFile?.exists() == true) {
                try {
                    val cachedData = cacheFile.readText()
                    val cache = json.decodeFromString<LocalLyricCache>(cachedData)
                    val parsedSong = cache.toSong()

                    if (!parsedSong.lyrics.isNullOrEmpty() && !cache.pureMusic) {
                        songToSet = parsedSong
                    } else {
                        // 缓存为空（上次未成功获取歌词），删除以便重新累积
                        cacheFile.delete()
                        Log.d(TAG, "Deleted stale empty cache for $id")
                    }
                } catch (e: Exception) {
                    YLog.error("Sync parse failed for $id: ${e.message}", e = e)
                    cacheFile.delete()
                }
            }

            setSong(songToSet)
        }

        private fun setSong(song: Song) {
            if (lastSetSong == song) return
            lastSetSong = song
            Log.i(TAG, "setSong called: id=${song.id}, name=${song.name}, lines=${song.lyrics?.size ?: 0}, provider=${lyricProvider != null}")
            lyricProvider?.player?.setSong(song)
        }
    }
}