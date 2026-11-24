/*
 * Copyright 2026 Proify, Tomakino
 * Licensed under the Apache License, Version 2.0
 * http://www.apache.org/licenses/LICENSE-2.0
 */
package io.github.proify.lyricon.retromusicprovider.xposed

import android.content.Context
import android.media.MediaMetadata
import android.media.session.PlaybackState
import android.net.Uri
import com.highcapable.yukihookapi.extension.kavaref.toClass
import com.highcapable.yukihookapi.extension.kavaref.toClassOrNull
import com.highcapable.yukihookapi.hook.entity.YukiBaseHooker
import com.highcapable.yukihookapi.hook.log.YLog
import com.kyant.taglib.TagLib
import io.github.proify.extensions.android.Reflect
import io.github.proify.lrckit.EnhanceLrcParser
import io.github.proify.lyricon.lyric.model.RichLyricLine
import io.github.proify.lyricon.lyric.model.Song
import io.github.proify.lyricon.lyric.model.interfaces.ILyricLine
import io.github.proify.lyricon.provider.LyriconFactory
import io.github.proify.lyricon.provider.LyriconProvider
import io.github.proify.lyricon.provider.ProviderLogo
import java.io.File
import java.lang.reflect.Method

/**
 * Lyricon provider for Retro Music Player 7.0.x.
 */
object RetroMusic : YukiBaseHooker() {
    private const val TAG = Constants.LOG_TAG
    private const val MUSIC_SERVICE = "code.name.monkey.retromusic.service.MusicService"
    private const val RETRO_SONG = "code.name.monkey.retromusic.model.Song"

    private var provider: LyriconProvider? = null

    private var currentMediaID: Long? = null

    private var lastSong: Song? = null

    private var musicServiceInstance: Any? = null

    private val lyricTagRegex by lazy { Regex("(?i)\\bLYRICS\\b") }

    override fun onHook() {
        YLog.info(tag = TAG, msg = "Starting Retro Music provider")

        registerAppLifecycle(true) {
            onCreate { initProvider(this) }
        }

        hookMusicService()
        hookTrackChange()
        hookPlaybackState()
        hookMediaMetadata()
    }

    /**
     * 注册 Lyricon provider。
     * @param context 宿主应用上下文。
     */
    private fun initProvider(context: Context) {
        provider = LyriconFactory.createProvider(
            context = context,
            providerPackageName = Constants.PROVIDER_PACKAGE_NAME,
            playerPackageName = Constants.MUSIC_PACKAGE_NAME,
            logo = ProviderLogo.fromSvg(Constants.ICON)
        ).apply { register() }
        YLog.info(tag = TAG, msg = "Lyricon provider registered")
    }

    /**
     * 捕获 MusicService 实例。
     */
    private fun hookMusicService() {
        val serviceClass = MUSIC_SERVICE.toClassOrNull()

        if (serviceClass == null) {
            YLog.error(tag = TAG, msg = "MusicService class not found")
            return
        }

        val method = serviceClass.declaredMethods.firstOrNull {
            it.name == "onCreate" && it.parameterTypes.isEmpty()
        }

        if (method == null) {
            YLog.error(tag = TAG, msg = "MusicService.onCreate not found")
            return
        }

        method.hook {
            after {
                musicServiceInstance = instance
                YLog.debug(tag = TAG, msg = "MusicService instance captured")
            }
        }
    }

    /**
     * 监听播放状态并转发给 Lyricon。
     */
    private fun hookPlaybackState() {
        val method = "android.media.session.MediaSession".toClass().declaredMethods.firstOrNull {
            it.name == "setPlaybackState" &&
                it.parameterTypes.size == 1 &&
                it.parameterTypes[0] == PlaybackState::class.java
        }

        if (method == null) {
            YLog.error(tag = TAG, msg = "MediaSession.setPlaybackState not found")
            return
        }

        method.hook {
            after {
                val state = args[0] as? PlaybackState

                if (state == null) {
                    YLog.debug(tag = TAG, msg = "playback state argument is null")
                    return@after
                }

                provider?.player?.setPlaybackState(state)
            }
        }
    }

    /**
     * 监听切歌协程的创建，在控制层切歌时立即更新歌词。
     */
    private fun hookTrackChange() {
        val coroutineClass = "code.name.monkey.retromusic.service.MusicService\$playSongAt\$1".toClassOrNull()

        if (coroutineClass == null) {
            YLog.error(tag = TAG, msg = "playSongAt coroutine class not found")
            return
        }

        val constructor = coroutineClass.declaredConstructors.firstOrNull {
            it.parameterTypes.size == 3 && it.parameterTypes[1] == Int::class.javaPrimitiveType
        }

        if (constructor == null) {
            YLog.error(tag = TAG, msg = "playSongAt constructor not found")
            return
        }

        constructor.hook {
            before {
                val position = args[1] as? Int

                if (position == null) {
                    YLog.debug(tag = TAG, msg = "track change position is null")
                } else {
                    updateSongAt(position)
                }
            }
        }
    }

    /**
     * 按队列位置取曲目并更新歌词。
     * @param position 队列位置。
     */
    private fun updateSongAt(position: Int) {
        val service = musicServiceInstance

        if (service == null) {
            YLog.debug(tag = TAG, msg = "service instance not captured yet")
            return
        }

        val songClass = RETRO_SONG.toClassOrNull()

        if (songClass == null) {
            YLog.error(tag = TAG, msg = "Retro Song class not found")
            return
        }

        val getSongAt = findGetSongAt(service.javaClass, songClass)

        if (getSongAt == null) {
            YLog.error(tag = TAG, msg = "getSongAt method not found")
            return
        }

        val retroSong = runCatching { getSongAt.invoke(service, position) }.getOrNull()

        if (retroSong == null) {
            YLog.debug(tag = TAG, msg = "song unavailable at " + position)
            return
        }

        updateSongFromRetro(retroSong)
    }

    /**
     * 监听媒体元数据，作为切歌时的触发与兜底。
     */
    private fun hookMediaMetadata() {
        val method = "android.media.session.MediaSession".toClass().declaredMethods.firstOrNull {
            it.name == "setMetadata" &&
                it.parameterTypes.size == 1 &&
                it.parameterTypes[0] == MediaMetadata::class.java
        }

        if (method == null) {
            YLog.error(tag = TAG, msg = "MediaSession.setMetadata not found")
            return
        }

        method.hook {
            after {
                val metadata = args[0] as? MediaMetadata

                if (metadata == null) {
                    YLog.debug(tag = TAG, msg = "metadata argument is null")
                    return@after
                }

                if (currentMediaID != null) return@after

                fallbackToMediaMetadata(metadata)
            }
        }
    }


    /**
     * 按签名 `(int) -> Song` 定位取歌方法，避免依赖混淆后的方法名。
     * @param serviceClass MusicService 类型。
     * @param songClass 曲目类型。
     * @return 匹配的方法；未找到时为 null。
     */
    private fun findGetSongAt(serviceClass: Class<*>, songClass: Class<*>): Method? =
        serviceClass.declaredMethods.firstOrNull {
            it.returnType == songClass &&
                it.parameterTypes.size == 1 &&
                it.parameterTypes[0] == Int::class.javaPrimitiveType
        }

    /**
     * 把歌词行转换为富歌词行，不携带不可序列化的元数据。
     * @param lines 歌词行。
     * @return 富歌词行。
     */
    private fun toRichLines(lines: List<ILyricLine>): List<RichLyricLine> =
        lines.map { line ->
            RichLyricLine(
                begin = line.begin,
                end = line.end,
                duration = line.duration,
                text = line.text,
                words = line.words,
                isAlignedRight = line.isAlignedRight
            )
        }

    /**
     * 从 Retro 曲目读取信息并下发歌词。
     * @param retroSong Retro Song 实例。
     * @return 成功下发歌词时为 true。
     */
    private fun updateSongFromRetro(retroSong: Any): Boolean {
        try {
            val id = getLong(retroSong, "getId")

            if (id == 0L) {
                YLog.debug(tag = TAG, msg = "song id unavailable")
                return false
            }

            if (id == currentMediaID) {
                YLog.info(tag = TAG, msg = "same song id=" + id + ", skip")
                return true
            }

            currentMediaID = id

            val title = call(retroSong, "getTitle")
            val artist = call(retroSong, "getArtistName")
            val duration = getLong(retroSong, "getDuration")
            val data = call(retroSong, "getData")

            if (data.isNullOrBlank()) {
                YLog.debug(tag = TAG, msg = "song data path missing, id=" + id)
                return false
            }

            val uri = if (data.startsWith("content://")) Uri.parse(data) else Uri.fromFile(File(data))
            val rawLyrics = fetchLyricFromTag(uri)

            if (rawLyrics.isNullOrBlank()) {
                YLog.debug(tag = TAG, msg = "no embedded lyrics, id=" + id)
                return false
            }

            val lines = EnhanceLrcParser.parse(rawLyrics, duration).lines
                .filter { !it.text.isNullOrBlank() }

            if (lines.isEmpty()) {
                YLog.info(tag = TAG, msg = "lyric tag parsed empty, id=" + id)
                return false
            }

            setSong(
                Song(
                    id = id.toString(),
                    name = title,
                    artist = artist,
                    duration = duration,
                    lyrics = toRichLines(lines)
                )
            )

            YLog.info(
                tag = TAG,
                msg = "lyrics loaded: " + title + " - " + artist + " (" + lines.size + " lines)"
            )

            return true
        } catch (e: Throwable) {
            YLog.error(tag = TAG, msg = "Failed to update Retro Music song", e = e)
        }
        return false
    }

    /**
     * 以媒体元数据兜底，仅下发歌名与歌手。
     * @param metadata 当前媒体元数据。
     */
    private fun fallbackToMediaMetadata(metadata: MediaMetadata) {
        val title = metadata.getString(MediaMetadata.METADATA_KEY_TITLE)
        val artist = metadata.getString(MediaMetadata.METADATA_KEY_ARTIST) ?: "Unknown Artist"

        if (title.isNullOrBlank()) {
            YLog.debug(tag = TAG, msg = "metadata title missing, skip fallback")
            return
        }

        YLog.debug(tag = TAG, msg = "fallback metadata: " + title)
        setSong(Song(name = title, artist = artist))
    }

    /**
     * 下发歌词，重复内容不重复发送。
     * @param song 待下发曲目。
     */
    private fun setSong(song: Song) {
        if (lastSong == song) {
            YLog.debug(tag = TAG, msg = "same song, skip dispatch")
            return
        }

        lastSong = song
        provider?.player?.setSong(song)
        YLog.debug(tag = TAG, msg = "push: " + song.name)
    }

    /**
     * 读取音频文件中的内嵌歌词标签。
     * @param uri 音频文件 URI。
     * @return 歌词文本；读取失败或没有标签时为 null。
     */
    private fun fetchLyricFromTag(uri: Uri): String? {
        val resolver = hostApplication?.contentResolver

        if (resolver == null) {
            YLog.error(tag = TAG, msg = "application context unavailable")
            return null
        }

        return try {
            resolver.openFileDescriptor(uri, "r")?.use { pfd ->
                val metadata = TagLib.getMetadata(pfd.dup().detachFd())

                if (metadata == null) {
                    YLog.debug(tag = TAG, msg = "no metadata in " + uri)
                    return@use null
                }

                val value = metadata.propertyMap.entries
                    .firstOrNull { (key, _) -> lyricTagRegex.matches(key) }
                    ?.value
                    ?.firstOrNull()

                if (value.isNullOrBlank()) {
                    YLog.debug(tag = TAG, msg = "no lyrics tag in " + uri)
                }

                value
            }
        } catch (e: Exception) {
            YLog.error(tag = TAG, msg = "read lyric tag failed: " + uri, e = e)
            null
        }
    }

    /**
     * 反射调用无参方法并转为字符串。
     * @param obj 目标对象。
     * @param getter 方法名。
     * @return 调用结果；失败时为 null。
     */
    private fun call(obj: Any, getter: String): String? =
        Reflect.callOrNull(obj, getter)?.toString()

    /**
     * 反射调用无参方法并转为数值。
     * @param obj 目标对象。
     * @param getter 方法名。
     * @return 调用结果；失败时为 0。
     */
    private fun getLong(obj: Any, getter: String): Long =
        (Reflect.callOrNull(obj, getter) as? Number)?.toLong() ?: 0L
}
