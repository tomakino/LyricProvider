/*
 * Copyright 2026 Proify, Tomakino
 * Licensed under the Apache License, Version 2.0
 * http://www.apache.org/licenses/LICENSE-2.0
 */

package io.github.proify.lyricon.cloudprovider.xposed

import android.media.MediaMetadata
import android.media.session.PlaybackState
import com.highcapable.kavaref.KavaRef.Companion.resolve
import com.highcapable.yukihookapi.hook.entity.YukiBaseHooker
import com.highcapable.yukihookapi.hook.log.YLog
import io.github.proify.cloudlyric.LyricsResult
import io.github.proify.cloudlyric.ProviderLyrics
import io.github.proify.extensions.md5
import io.github.proify.lyricon.lyric.model.Song
import io.github.proify.lyricon.provider.LyriconFactory
import io.github.proify.lyricon.provider.LyriconProvider
import io.github.proify.lyricon.provider.ProviderLogo

/**
 * 从网络获取歌词
 *
 * 注意：如果被hook的app没有网络权限，那么将无法工作
 */
object CloudProvider : YukiBaseHooker(), DownloadCallback {
    private const val TAG: String = "CloudProvider"

    private var provider: LyriconProvider? = null
    private var lastMediaSignature: String? = null

    private var curMediaMetadata: MediaMetadata? = null

    override fun onHook() {
        YLog.debug(tag = TAG, msg = "进程: $processName")

        onAppLifecycle {
            onCreate {
                initProvider()
            }
        }

        hookMediaSession()
    }

    private fun initProvider() {
        val context = appContext ?: return
        provider = LyriconFactory.createProvider(
            context = context,
            providerPackageName = Constants.PROVIDER_PACKAGE_NAME,
            playerPackageName = context.packageName,
            logo = ProviderLogo.fromBase64(Constants.ICON),
            processName = processName
        ).apply {
            player.setDisplayTranslation(true)
            register()
        }
    }

    // ============== 新增：书名号内容提取方法开始 ==============
    /**
     * 提取歌曲名称：优先取书名号《》包裹的内容，没有则返回原标题
     * @param rawTitle 原始读取到的媒体标题
     * @return 处理后的歌曲名称
     */
    private fun extractSongTitle(rawTitle: String?): String? {
        if (rawTitle.isNullOrEmpty()) return rawTitle
        // 非贪婪匹配第一个中文书名号包裹的内容
        val titleRegex = Regex("《(.+?)》")
        val matchResult = titleRegex.find(rawTitle)
        return matchResult?.groupValues?.getOrNull(1)?.trim() ?: rawTitle.trim()
    }
    // ============== 新增：书名号内容提取方法结束 ==============

    private fun hookMediaSession() {
        "android.media.session.MediaSession".toClass().resolve().apply {
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

                    val id = metadata.getString(MediaMetadata.METADATA_KEY_MEDIA_ID)
                    // ============== 修改：原标题读取后增加处理逻辑开始 ==============
                    val rawTitle = metadata.getString(MediaMetadata.METADATA_KEY_TITLE)
                    // 优先提取书名号内容作为歌曲名
                    val title = extractSongTitle(rawTitle)
                    // ============== 修改：原标题读取后增加处理逻辑结束 ==============
                    val artist = metadata.getString(MediaMetadata.METADATA_KEY_ARTIST)
                    val album = metadata.getString(MediaMetadata.METADATA_KEY_ALBUM)

                    val signature = calculateSignature(id, title, artist, album)
                    if (signature == lastMediaSignature) {
                        YLog.debug(tag = TAG, msg = "Same metadata, skip")
                        return@after
                    }
                    lastMediaSignature = signature
                    curMediaMetadata = metadata

                    YLog.debug(
                        tag = TAG,
                        msg = "Metadata: id=$id, rawTitle=$rawTitle, processedTitle=$title, artist=$artist, album=$album"
                    )

                    provider?.player?.setSong(
                        Song(
                            name = title,
                            artist = artist,
                        )
                    )

                    DownloadManager.cancel()

                    YLog.debug(
                        tag = TAG,
                        msg = "Searching lyrics... trackName=$title, artist=$artist, album=$album"
                    )
                    DownloadManager.search(this@CloudProvider) {
                        trackName = title
                        artistName = artist
                        albumName = album
                        perProviderLimit = 5
                        maxTotalResults = 1
                    }
                }
            }
        }
    }

    private fun calculateSignature(vararg data: String?): String {
        return data.joinToString("").md5()
    }

    override fun onDownloadFinished(response: List<ProviderLyrics>) {
        if (response.isEmpty()) {
            YLog.debug(tag = TAG, msg = "No lyrics found")
            return
        }
        YLog.debug(tag = TAG, msg = "Download finished: $response")
        val song = response.firstOrNull()?.lyrics?.toSong()
        provider?.player?.setSong(song)
    }

    override fun onDownloadFailed(e: Exception) {
        YLog.error(tag = TAG, msg = "Download failed: ${e.message}")
    }

    private fun LyricsResult.toSong() = Song().apply {
        var duration = curMediaMetadata?.getLong(MediaMetadata.METADATA_KEY_DURATION) ?: 0
        if (duration == 0L) {
            duration = rich.lastOrNull()?.end ?: 0L
        }

        name = trackName
        artist = artistName
        lyrics = rich
        this.duration = duration
    }
}
