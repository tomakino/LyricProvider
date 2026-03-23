/*
 * Copyright 2026 Proify, Tomakino
 * Licensed under the Apache License, Version 2.0
 * http://www.apache.org/licenses/LICENSE-2.0
 */

package io.github.proify.lyricon.localprovider.xposed

import android.content.BroadcastReceiver
import android.content.Context
import android.content.Intent
import android.content.IntentFilter
import android.media.session.PlaybackState
import android.net.Uri
import androidx.core.content.ContextCompat
import com.highcapable.kavaref.KavaRef.Companion.resolve
import com.highcapable.yukihookapi.hook.entity.YukiBaseHooker
import com.highcapable.yukihookapi.hook.log.YLog
import com.kyant.taglib.TagLib
import io.github.proify.lrckit.EnhanceLrcParser
import io.github.proify.lyricon.localprovider.xposed.util.SafUriResolver
import io.github.proify.lyricon.lyric.model.Song
import io.github.proify.lyricon.provider.LyriconFactory
import io.github.proify.lyricon.provider.LyriconProvider
import io.github.proify.lyricon.provider.ProviderLogo

object PowerAmp : YukiBaseHooker() {
    private const val TAG = "PowerAmp"
    private const val ACTION_TRACK_CHANGED = "com.maxmpz.audioplayer.TRACK_CHANGED"

    private val lyricTagRegex by lazy { Regex("(?i)\\b(LYRICS|LYRICS\\d*|USLT)\\b") }

    private var provider: LyriconProvider? = null
    private var trackReceiver: BroadcastReceiver? = null
    private var currentSongId: String? = null

    override fun onHook() {
        onAppLifecycle {
            onCreate {
                initLyriconProvider(this)
                setupBroadcastReceiver(this)
                hookMediaSession()
            }
            onTerminate { release() }
        }
    }

    private fun initLyriconProvider(context: Context) {
        provider = LyriconFactory.createProvider(
            context = context,
            providerPackageName = Constants.PROVIDER_PACKAGE_NAME,
            playerPackageName = context.packageName,
            logo = null // 使用已有图标，不重复传递
        ).apply {
            register()
        }
        YLog.info(tag = TAG, msg = "Lyricon Provider registered")
    }

    private fun setupBroadcastReceiver(context: Context) {
        val filter = IntentFilter(ACTION_TRACK_CHANGED)
        trackReceiver = object : BroadcastReceiver() {
            override fun onReceive(context: Context, intent: Intent) {
                if (intent.action == ACTION_TRACK_CHANGED) {
                    handleTrackChange(intent)
                }
            }
        }.also {
            ContextCompat.registerReceiver(context, it, filter, ContextCompat.RECEIVER_EXPORTED)
        }
        YLog.info(tag = TAG, msg = "Broadcast receiver registered")
    }

    private fun hookMediaSession() {
        "android.media.session.MediaSession".toClass().resolve().apply {
            firstMethod {
                name = "setPlaybackState"
                parameters(PlaybackState::class.java)
            }.hook {
                after {
                    val state = args[0] as? PlaybackState ?: return@after
                    provider?.player?.setPlaybackState(state)
                }
            }
        }
        YLog.info(tag = TAG, msg = "MediaSession hooked")
    }

    private fun handleTrackChange(intent: Intent) {
        val bundle = intent.extras ?: return

        val id = bundle.getLong("id", -1).toString()
        val title = bundle.getString("title") ?: return
        val artist = bundle.getString("artist")
        val album = bundle.getString("album")
        val duration = bundle.getLong("durMs")
        val path = bundle.getString("path") ?: return

        if (id == currentSongId) return
        currentSongId = id

        YLog.debug(tag = TAG, msg = "Track changed: $title - $artist")

        provider?.player?.setSong(Song(name = title, artist = artist))

        val uri = resolveAudioUri(path)
        if (uri != null) {
            val lyrics = fetchEmbeddedLyrics(uri)
            if (!lyrics.isNullOrEmpty()) {
                val song = Song(
                    id = id,
                    name = title,
                    artist = artist,
                    duration = duration,
                    lyrics = lyrics
                )
                provider?.player?.setSong(song)
                YLog.info(tag = TAG, msg = "Embedded lyrics loaded for: $title")
                return
            }
        }

        YLog.debug(tag = TAG, msg = "No embedded lyrics found for: $title")
    }

    private fun resolveAudioUri(path: String): Uri? {
        val formattedPath = formatSafPath(path) ?: return null
        return SafUriResolver.resolveToUri(appContext!!, formattedPath)
    }

    private fun formatSafPath(path: String): String? {
        val input = path.trimStart()
        if (input.isEmpty() || input.startsWith("/")) return null

        val separatorIndex = input.indexOf('/')
        if (separatorIndex == -1) return null

        val volumeId = input.take(separatorIndex)
        val relativePath = input.substring(separatorIndex + 1)

        return if (volumeId.isNotEmpty()) "$volumeId:$relativePath" else null
    }

    private fun fetchEmbeddedLyrics(uri: Uri): List<io.github.proify.lyricon.lyric.model.RichLyricLine>? {
        return try {
            appContext?.contentResolver?.openFileDescriptor(uri, "r")?.use { pfd ->
                TagLib.getMetadata(pfd.dup().detachFd())?.let { metadata ->
                    metadata.propertyMap.entries.firstOrNull { (key, _) ->
                        lyricTagRegex.matches(key)
                    }?.value?.firstOrNull()?.let { rawLyric ->
                        val doc = EnhanceLrcParser.parse(rawLyric)
                        doc.lines.filter { !it.text.isNullOrBlank() }
                    }
                }
            }
        } catch (e: Exception) {
            YLog.error(tag = TAG, msg = "Failed to fetch lyrics", e = e)
            null
        }
    }

    private fun release() {
        trackReceiver?.let { appContext?.unregisterReceiver(it) }
        trackReceiver = null
        provider?.unregister()
        provider = null
        YLog.info(tag = TAG, msg = "PowerAmp provider released")
    }
}