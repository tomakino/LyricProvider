/*
 * Copyright 2026 Proify, Tomakino
 * Licensed under the Apache License, Version 2.0
 * http://www.apache.org/licenses/LICENSE-2.0
 */

@file:Suppress("unused")

package io.github.proify.lyricon.qqmusicprovider.xposed

import android.app.Application
import android.media.session.PlaybackState
import android.util.Base64
import com.highcapable.kavaref.extension.classOf
import com.highcapable.yukihookapi.extension.kavaref.toClass
import com.highcapable.yukihookapi.extension.kavaref.toClassOrNull
import com.highcapable.yukihookapi.hook.entity.YukiBaseHooker
import com.highcapable.yukihookapi.hook.log.YLog
import io.github.proify.lrckit.LrcParser
import io.github.proify.lyricon.lyric.model.RichLyricLine
import io.github.proify.lyricon.lyric.model.Song
import io.github.proify.lyricon.provider.LyriconFactory
import io.github.proify.lyricon.provider.LyriconProvider
import io.github.proify.lyricon.provider.ProviderLogo
import io.github.proify.qrckit.decrypt.QrcDecrypter
import io.github.proify.qrckit.parser.QrcParser
import kotlinx.coroutines.CoroutineScope
import kotlinx.coroutines.Dispatchers
import kotlinx.coroutines.SupervisorJob
import kotlinx.coroutines.delay
import kotlinx.coroutines.launch
import kotlinx.coroutines.withContext
import org.json.JSONObject
import java.io.File
import java.util.concurrent.ConcurrentHashMap
import kotlin.time.Duration.Companion.milliseconds

/**
 * QQ 音乐简洁版（[Constants.MUSIC_PACKAGE_NAME]）歌词提供者。
 *
 * 歌词直接取 App 自己的缓存：`cache/QPlayEdge/lyric/<hash>` 是 4 字节头 +
 * UTF-16LE JSON，`song_lyric` 即明文 LRC。
 */
object QQMusic : YukiBaseHooker() {

    const val TAG = "QQMusicProvider"

    private const val LYRIC_DIR = "QPlayEdge/lyric"

    private val scope = CoroutineScope(Dispatchers.IO + SupervisorJob())

    private var provider: LyriconProvider? = null
    private var application: Application? = null

    @Volatile
    private var currentTitle: String? = null

    @Volatile
    private var currentArtist: String? = null

    @Volatile
    private var lastTrackKey: String? = null

    @Volatile
    private var pendingSnapshot: Map<String, Long>? = null

    @Volatile
    private var lastPushedKey: String? = null

    @Volatile
    private var pendingPauseJob: kotlinx.coroutines.Job? = null

    /** 缓存文件名（key 哈希）到曲目 ID 的映射。 */
    private val fileNameToSongId = ConcurrentHashMap<Int, String>()

    override fun onHook() {
        YLog.info(tag = TAG, msg = "attached: $packageName / $processName")

        registerAppLifecycle(true) {
            onCreate { application = this }
        }

        hookMediaSession()
        hookTrackChange()
        hookLyricRequest()
    }

    private fun ensureProvider(): LyriconProvider? {
        provider?.let { return it }

        val context = application ?: hostApplication ?: return null
        val created = LyriconFactory.createProvider(
            context = context,
            providerPackageName = Constants.PROVIDER_PACKAGE_NAME,
            playerPackageName = Constants.MUSIC_PACKAGE_NAME,
            logo = ProviderLogo.fromSvg(Constants.ICON),
            processName = processName
        )
        created.player.setDisplayTranslation(true)
        created.register()
        provider = created

        YLog.info(tag = TAG, msg = "provider registered in process=$processName")
        return created
    }

    private fun hookMediaSession() {
        val sessionClass = "android.media.session.MediaSession".toClass()

        val playbackState = sessionClass.declaredMethods.firstOrNull {
            it.name == "setPlaybackState" &&
                    it.parameterTypes.size == 1 &&
                    it.parameterTypes[0] == classOf<PlaybackState>()
        }

        if (playbackState == null) {
            YLog.error(tag = TAG, msg = "setPlaybackState not found")
            return
        }

        playbackState.hook {
            after {
                runCatching {
                    val state = args[0] as? PlaybackState ?: return@runCatching

                    if (state.state == PlaybackState.STATE_PLAYING) {
                        pendingPauseJob?.cancel()
                        pendingPauseJob = null

                        deliverPlaybackState(state)
                    } else {
                        pendingPauseJob?.cancel()
                        pendingPauseJob = scope.launch {
                            delay(400.milliseconds)

                            withContext(Dispatchers.Main) {
                                deliverPlaybackState(state)
                            }
                        }
                    }
                }.onFailure {
                    YLog.error(tag = TAG, msg = "setPlaybackState failed", e = it)
                }
            }
        }
    }

    private fun deliverPlaybackState(state: PlaybackState) {
        ensureProvider()?.player?.setPlaybackState(state)
    }

    private fun hookTrackChange() {
        val sessionClass = "cn.wenyu.bodian.HostMediaSession".toClassOrNull()

        if (sessionClass == null) {
            YLog.error(tag = TAG, msg = "HostMediaSession not found")
            return
        }

        val trackMethod = sessionClass.declaredMethods.firstOrNull {
            it.parameterTypes.size == 3 &&
                    it.parameterTypes[0] == classOf<Long>() &&
                    it.parameterTypes[1] == classOf<String>() &&
                    it.parameterTypes[2] == classOf<String>()
        }

        if (trackMethod == null) {
            YLog.error(tag = TAG, msg = "HostMediaSession track method not found")
            return
        }

        trackMethod.hook {
            before {
                pendingSnapshot = snapshotLyricDir()
            }
            after {
                val title = args[1] as? String ?: return@after
                val artist = args[2] as? String

                val key = title + "|" + (artist ?: "")

                if (key == lastTrackKey) return@after

                lastTrackKey = key
                currentTitle = title
                currentArtist = artist

                YLog.info(tag = TAG, msg = "track: $title / $artist")

                loadCachedLyric(pendingSnapshot)
            }
        }
        YLog.info(tag = TAG, msg = "track change hooked")
    }

    /**
     * Base64(hex) 形式的密文字段解密。
     */
    private fun decodeEncoded(value: String?): String? {
        if (value.isNullOrBlank()) return null

        return runCatching {
            val hex = String(Base64.decode(value, Base64.DEFAULT), Charsets.UTF_8)
            QrcDecrypter.decrypt(hex)
        }.getOrNull()
    }

    /**
     * 记录歌词请求的曲目 ID，供缓存文件名换算。
     */
    private fun hookLyricRequest() {
        val requestClass = "com.tencent.qqmusic.qplayer.openapi.network.song.GetSongLyricApiReq"
            .toClassOrNull()

        if (requestClass == null) {
            YLog.error(tag = TAG, msg = "GetSongLyricApiReq not found")
            return
        }

        val constructors = requestClass.declaredConstructors.filter {
            it.parameterTypes.size >= 3 &&
                    it.parameterTypes[0] == classOf<Long>() &&
                    it.parameterTypes[1] == classOf<String>()
        }

        if (constructors.isEmpty()) {
            YLog.error(tag = TAG, msg = "GetSongLyricApiReq constructor not found")
            return
        }

        constructors.forEach { constructor ->
            constructor.hook {
                after {
                    val songId = args[0] as? Long ?: return@after
                    val songMid = args[1] as? String ?: return@after

                    if (songId <= 0L) return@after

                    val key = songId.toString() + "_" + songMid

                    if (fileNameToSongId.size > 256) fileNameToSongId.clear()

                    fileNameToSongId[key.hashCode()] = songId.toString()
                }
            }
        }

        YLog.info(tag = TAG, msg = "lyric request hooked: " + constructors.size)
    }

    /**
     * 等待 App 写入当前曲目的歌词缓存后读取。
     */
    private fun snapshotLyricDir(): Map<String, Long>? {
        val dir = File(application?.cacheDir, LYRIC_DIR)

        if (!dir.isDirectory) {
            YLog.warn(tag = TAG, msg = "lyric dir missing: " + dir.absolutePath)
            return null
        }

        return dir.listFiles()
            ?.filter { it.isFile }
            ?.associate { it.name to it.lastModified() }
    }

    private fun loadCachedLyric(snapshot: Map<String, Long>?) {
        val dir = File(application?.cacheDir, LYRIC_DIR)

        if (!dir.isDirectory) return

        val before = snapshot ?: emptyMap()

        scope.launch {
            var target: File? = null
            var attempt = 0

            while (attempt < 20 && target == null) {
                delay(200.milliseconds)

                target = dir.listFiles()
                    ?.filter { it.isFile && it.length() > 0 }
                    ?.filter { before[it.name] != it.lastModified() }
                    ?.maxByOrNull { it.lastModified() }

                attempt++
            }

            val file = target
                ?: dir.listFiles()
                    ?.filter { it.isFile && it.length() > 0 }
                    ?.maxByOrNull { it.lastModified() }

            if (file == null) {
                YLog.warn(tag = TAG, msg = "no lyric file for $currentTitle")
                return@launch
            }

            val bytes = runCatching { file.readBytes() }.getOrNull() ?: return@launch
            if (bytes.size <= 4) return@launch

            val json = runCatching {
                String(bytes, 4, bytes.size - 4, Charsets.UTF_16LE)
            }.getOrNull() ?: return@launch

            val payload = runCatching { JSONObject(json) }.getOrNull() ?: return@launch

            val qrcPlain = decodeEncoded(payload.optString("song_qrc_enc_lyric"))

            val translationLines = runCatching {
                val raw = decodeEncoded(payload.optString("song_translate_enc_lyric"))
                    ?: return@runCatching emptyList()

                LrcParser.parse(raw, 0L).lines
                    .ifEmpty { QrcParser.parseLyric(raw).second }
                    .filter { !it.text.isNullOrBlank() }
            }.getOrNull().orEmpty()

            val qrcLines = runCatching {
                qrcPlain?.let { QrcParser.parseLyric(it).second }
            }.getOrNull()?.takeIf { it.isNotEmpty() }

            val lines = if (qrcLines != null) {
                qrcLines.map { line ->
                    RichLyricLine(
                        begin = line.begin,
                        end = line.end,
                        duration = line.duration,
                        text = line.text,
                        words = line.words
                    )
                }
            } else {
                val raw =
                    payload.optString("song_lyric").takeIf { it.isNotBlank() } ?: return@launch

                runCatching {
                    LrcParser.parse(raw, 0L).lines
                        .filter { !it.text.isNullOrBlank() }
                        .map { line ->
                            RichLyricLine(
                                begin = line.begin,
                                end = line.end,
                                duration = line.duration,
                                text = line.text
                            )
                        }
                }.getOrNull().orEmpty()
            }

            if (lines.isEmpty()) {
                YLog.warn(tag = TAG, msg = "empty lyric in " + file.name)
                return@launch
            }

            val title = currentTitle

            val mergedLines = lines.map { line ->
                val translation = translationLines
                    .minByOrNull { kotlin.math.abs(it.begin - line.begin) }
                    ?.takeIf { kotlin.math.abs(it.begin - line.begin) <= 500 }
                    ?.text
                    .takeUnless {
                        it?.trim() == "//"
                    }

                RichLyricLine(
                    begin = line.begin,
                    end = line.end,
                    duration = line.duration,
                    text = line.text,
                    words = line.words,
                    translation = translation
                )
            }

            val realId = file.name.toIntOrNull()?.let { fileNameToSongId[it] }

            val song = Song(
                id = realId ?: file.name,
                name = title,
                artist = currentArtist,
                duration = mergedLines.lastOrNull()?.end ?: 0L,
                lyrics = mergedLines
            )

            val pushKey = song.id + "|" + mergedLines.size + "|" +
                    (mergedLines.firstOrNull()?.text ?: "")

            if (pushKey == lastPushedKey) {
                YLog.debug(tag = TAG, msg = "skip duplicate push: " + pushKey)
                return@launch
            }

            lastPushedKey = pushKey

            withContext(Dispatchers.Main) {
                ensureProvider()?.player?.setSong(song)
            }

            YLog.info(
                tag = TAG,
                msg = "lyrics pushed: " + title + " (" + mergedLines.size + " lines, wordwise=" +
                        mergedLines.any { !it.words.isNullOrEmpty() } + ", translation=" +
                        mergedLines.any { !it.translation.isNullOrBlank() } + "), id=" +
                        (realId ?: file.name) + ", file=" + file.name
            )
        }
    }
}
