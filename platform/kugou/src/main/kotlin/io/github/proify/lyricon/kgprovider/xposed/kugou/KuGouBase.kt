/*
 * Copyright 2026 Proify, Tomakino
 * Licensed under the Apache License, Version 2.0
 * http://www.apache.org/licenses/LICENSE-2.0
 */

package io.github.proify.lyricon.kgprovider.xposed.kugou

import android.media.MediaMetadata
import android.media.session.PlaybackState
import com.highcapable.kavaref.KavaRef.Companion.resolve
import com.highcapable.yukihookapi.hook.entity.YukiBaseHooker
import com.highcapable.yukihookapi.hook.log.YLog
import de.robv.android.xposed.XC_MethodHook
import de.robv.android.xposed.XposedBridge
import io.github.proify.extensions.toRichLyricLines
import io.github.proify.lrckit.LrcParser
import io.github.proify.lyricon.kgprovider.xposed.Constants
import io.github.proify.lyricon.krckit.KrcDecryptor
import io.github.proify.lyricon.krckit.KrcParser
import io.github.proify.lyricon.lyric.model.RichLyricLine
import io.github.proify.lyricon.lyric.model.Song
import io.github.proify.lyricon.provider.LyriconFactory
import io.github.proify.lyricon.provider.LyriconProvider
import io.github.proify.lyricon.provider.ProviderLogo
import kotlinx.coroutines.CoroutineScope
import kotlinx.coroutines.Dispatchers
import kotlinx.coroutines.SupervisorJob
import kotlinx.coroutines.launch
import org.luckypray.dexkit.DexKitBridge
import java.io.File
import java.lang.reflect.Method

/**
 * 酷狗音乐基础 Hook 架构类
 * 负责 MediaSession 状态同步、歌词文件拦截与 Provider 生命周期管理
 */
abstract class KuGouBase : YukiBaseHooker() {

    companion object {
        protected const val TAG = "KuGouProvider"
    }

    private val scope = CoroutineScope(Dispatchers.IO + SupervisorJob())

    protected var provider: LyriconProvider? = null

    protected var currentSongId: String? = null
    private var lastEmittedSong: Song? = null
    private var isInitialized = false
    protected lateinit var dexKitBridge: DexKitBridge
        private set

    init {
        System.loadLibrary("dexkit")
    }

    override fun onHook() {
        if (!shouldHookProcess()) return
        dexKitBridge = DexKitBridge.create(appInfo.sourceDir)
        hookMediaSession()
        scope.launch { asyncHookLyricManager() }

        onAppLifecycle {
            onCreate {
                initProvider()
                onAppCreate()
            }
        }
    }

    protected abstract fun onAppCreate()
    protected abstract fun shouldHookProcess(): Boolean

    /**
     * 初始化 Lyricon Provider
     */
    private fun initProvider() {
        if (isInitialized) return
        val ctx = appContext ?: return

        try {
            provider = LyriconFactory.createProvider(
                context = ctx,
                providerPackageName = Constants.PROVIDER_PACKAGE_NAME,
                playerPackageName = ctx.packageName,
                logo = ProviderLogo.fromBase64(Constants.ICON)
            ).apply {
                register()
                player.setDisplayTranslation(true)
            }
            isInitialized = true
            YLog.info(msg = "Lyricon Provider initialized for ${ctx.packageName}", tag = TAG)
        } catch (e: Exception) {
            YLog.error(msg = "Failed to init provider: ${e.message}", tag = TAG)
        }
    }

    private fun asyncHookLyricManager() {

        fun findLoadLyricMethodFromDexKit(): Method? {
            val methodData = dexKitBridge
                .findClass {
                    matcher {
                        className = "com.kugou.framework.lyric.LyricManager"
                    }
                }
                .findMethod {
                    matcher {
                        addUsingString("file is not krc or lyc or txt file")
                        paramTypes(String::class.java, Boolean::class.javaPrimitiveType)
                    }
                }.singleOrNull()
            return methodData?.getMethodInstance(appClassLoader!!)
        }

//        fun findLoadLyricMethodFromReflection(): Method? {
//            val clazz = "com.kugou.framework.lyric.LyricManager".toClass()
//            val methods = clazz.methods
//            return methods.find {
//                val modifiers = it.modifiers
//                val isPublic = java.lang.reflect.Modifier.isPublic(modifiers)
//                val isStatic = java.lang.reflect.Modifier.isStatic(modifiers)
//
//                !isStatic && isPublic && it.parameterTypes.contentEquals(
//                    arrayOf(
//                        String::class.java,
//                        Boolean::class.javaPrimitiveType
//                    )
//                )
//            }
//        }

        val method = findLoadLyricMethodFromDexKit()

        XposedBridge.hookMethod(method, object : XC_MethodHook() {
            override fun afterHookedMethod(param: MethodHookParam?) {
                val args = param?.args ?: return
                val path = args[0] as? String ?: return
                processLyricFileAsync(path)
            }
        })
    }

    /**
     * 异步解析歌词文件，避免阻塞主线程或 Hook 回调
     * @param path KRC 文件绝对路径
     */
    private fun processLyricFileAsync(path: String) {
        scope.launch {
            try {
                val file = File(path)
                if (!file.exists()) return@launch
                when (file.extension) {
                    "krc" -> {
                        val raw = file.readBytes()
                        val decrypted = KrcDecryptor.decrypt(raw)
                        val document = KrcParser.parse(decrypted)
                        val lyrics = document.richLyricLines

                        if (lyrics.isNotEmpty()) {
                            currentSongId?.let { LyricsCache.put(it, lyrics) }
                            onReceiveLyrics(lyrics)
                        }
                    }

                    "lrc" -> {
                        val raw = file.readText()
                        val document = LrcParser.parse(raw)
                        val lyrics = document.lines.toRichLyricLines()

                        if (lyrics.isNotEmpty()) {
                            currentSongId?.let { LyricsCache.put(it, lyrics) }
                            onReceiveLyrics(lyrics)
                        }
                    }

                    else -> return@launch
                }

            } catch (e: Exception) {
                YLog.error(tag = TAG, msg = "Lyric parsing failed: ${e.message}")
            }
        }
    }

    private fun hookMediaSession() {
        "android.media.session.MediaSession".toClass()
            .resolve().apply {
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
                        val metadata = args[0] as? MediaMetadata ?: return@after
                        handleMetadataChange(metadata)
                    }
                }
            }
    }

    /**
     * 处理元数据变更
     */
    private fun handleMetadataChange(metadata: MediaMetadata) {
        val title = metadata.getString(MediaMetadata.METADATA_KEY_TITLE) ?: return
        val artist = metadata.getString(MediaMetadata.METADATA_KEY_ARTIST) ?: ""
        val album = metadata.getString(MediaMetadata.METADATA_KEY_ALBUM) ?: ""
        val duration = metadata.getLong(MediaMetadata.METADATA_KEY_DURATION)

        val meta = MetadataData(title, artist, album, duration)
        val songId = meta.generateId

        if (songId == currentSongId) return
        currentSongId = songId

        MetadataDataManager.put(meta)
        //YLog.info(tag = TAG, msg = "Track Changed: $title - $artist")

        // 尝试从缓存获取歌词
        LyricsCache.get(songId)?.let {
            sendLyrics(it)
        }
    }

    /**
     * 接收到解析完成的歌词
     */
    private fun onReceiveLyrics(lyrics: List<RichLyricLine>) {
        if (currentSongId.isNullOrBlank()) return
        sendLyrics(lyrics)
    }

    private fun sendLyrics(lyrics: List<RichLyricLine>) {
        val id = currentSongId ?: return
        val meta = MetadataDataManager.get(id) ?: return

        val finalDuration = if (meta.duration <= 0) {
            lyrics.lastOrNull()?.end ?: 0L
        } else meta.duration

        val song = Song(
            id = id,
            name = meta.title,
            artist = meta.artist,
            duration = finalDuration,
            lyrics = lyrics
        )

        emitSong(song)
    }

    /**
     * 最终提交歌曲变更，包含去重校验
     */
    private fun emitSong(song: Song) {
        if (lastEmittedSong == song) return
        lastEmittedSong = song
        provider?.player?.setSong(song)
        YLog.info(tag = TAG, msg = "Successfully pushed song to Provider: ${song.name}")
    }
}