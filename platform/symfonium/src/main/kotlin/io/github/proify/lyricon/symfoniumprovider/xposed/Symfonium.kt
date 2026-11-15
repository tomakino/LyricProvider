/*
 * Copyright 2026 Proify, Tomakino
 * Licensed under the Apache License, Version 2.0
 * http://www.apache.org/licenses/LICENSE-2.0
 */

package io.github.proify.lyricon.symfoniumprovider.xposed

import android.media.MediaMetadata
import android.media.session.PlaybackState
import android.net.Uri
import com.highcapable.kavaref.KavaRef.Companion.resolve
import com.highcapable.yukihookapi.hook.entity.YukiBaseHooker
import com.highcapable.yukihookapi.hook.log.YLog
import com.kyant.taglib.TagLib
import io.github.proify.lrckit.EnhanceLrcParser
import io.github.proify.lyricon.lyric.model.Song
import io.github.proify.lyricon.provider.LyriconFactory
import io.github.proify.lyricon.provider.LyriconProvider
import io.github.proify.lyricon.provider.ProviderLogo

class Symfonium : YukiBaseHooker() {
    companion object {
        const val TAG: String = "Symfonium"
    }

    private var lyriconProvider: LyriconProvider? = null
    private val lyricTagRegex by lazy { Regex("(?i)\\b(LYRICS)\\b") }

    private var currentMediaUri: Uri? = null
    private var currentMediaMetadata: MediaMetadata? = null
    private var currentSong: Song? = null

    override fun onHook() {
        YLog.debug(tag = TAG, msg = "进程: $packageName/$processName")

        onAppLifecycle {
            onCreate {
                initProvider()
            }
        }
        hookMediaSession()

        Uri::class.java.name.toClass()
            .resolve().apply {

                firstMethod {
                    name = "parse"
                    parameters(String::class.java)
                }.hook {
                    after {
                        val uri = args[0] as String
                        if (!uri.startsWith("content://media/external/audio/")) {
                            return@after
                        }

                        val result = this.result as Uri
                        if (currentMediaUri == result) {
                            //YLog.debug(tag = TAG, msg = "skip same uri: $uri")
                            return@after
                        }
                        YLog.debug(tag = TAG, msg = "load uri: $uri")
                        currentMediaUri = result

                        val lyric = fetchLyricFromTag(result)
                        setLyric(uri, lyric)
                    }
                }
            }

    }

    private fun setLyric(id: String, lyric: String?) {
        val document = EnhanceLrcParser.parse(lyric)

        val name = currentMediaMetadata?.getString(MediaMetadata.METADATA_KEY_TITLE)
        val artist = currentMediaMetadata?.getString(MediaMetadata.METADATA_KEY_ARTIST)

        setSong(
            Song(
                id = id,
                name = name,
                artist = artist,
                lyrics = document.lines
            )
        )
    }

    private fun setSong(song: Song) {
        if (currentSong == song) {
            YLog.debug(tag = TAG, msg = "skip same song: ${song.name}")
            return
        }
        currentSong = song
        lyriconProvider?.player?.setSong(song)
    }

    private fun fetchLyricFromTag(uri: Uri): String? = try {
        appContext?.contentResolver?.openFileDescriptor(uri, "r")?.use { pfd ->
            TagLib.getMetadata(pfd.dup().detachFd())?.let { metadata ->
                metadata.propertyMap.entries.firstOrNull { (key, _) ->
                    lyricTagRegex.matches(key)
                }?.value?.firstOrNull()
            }
        }
    } catch (e: Exception) {
        YLog.error(tag = TAG, msg = "Failed to fetch lyric tag from $uri", e = e)
        null
    }

    private fun initProvider() {
        val context = appContext ?: return
        lyriconProvider = LyriconFactory.createProvider(
            context = context,
            providerPackageName = Constants.PROVIDER_PACKAGE_NAME,
            playerPackageName = context.packageName,
            logo = ProviderLogo.fromSvg(Constants.ICON)
        ).apply { register() }
    }

    private fun hookMediaSession() {
        "android.media.session.MediaSession".toClass().resolve().apply {
            firstMethod {
                name = "setPlaybackState"
                parameters(PlaybackState::class.java)
            }.hook {
                after {
                    val state = args[0] as? PlaybackState
                    lyriconProvider?.player?.setPlaybackState(state)
                }
            }

            firstMethod {
                name = "setMetadata"
                parameters("android.media.MediaMetadata")
            }.hook {
                after {
                    val metadata = args[0] as? MediaMetadata ?: return@after
                    if (metadata == currentMediaMetadata) {
                        // YLog.debug(tag = TAG, msg = "skip same metadata")
                        return@after
                    }
                    val title = metadata.getString(MediaMetadata.METADATA_KEY_TITLE)
                    val artist = metadata.getString(MediaMetadata.METADATA_KEY_ARTIST)
                    YLog.debug(tag = TAG, msg = "set metadata: $title - $artist")
                    currentMediaMetadata = metadata
                }
            }
        }
    }
}