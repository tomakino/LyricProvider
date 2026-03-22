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
import android.media.MediaMetadata
import android.media.session.PlaybackState
import android.net.Uri
import android.os.Environment
import android.provider.MediaStore
import androidx.core.content.ContextCompat
import com.highcapable.kavaref.KavaRef.Companion.resolve
import com.highcapable.yukihookapi.hook.entity.YukiBaseHooker
import com.highcapable.yukihookapi.hook.log.YLog
import io.github.proify.lyricon.localprovider.model.ProviderLyrics
import io.github.proify.lyricon.localprovider.model.LyricsResult
import io.github.proify.lyricon.lyric.model.Song
import io.github.proify.lyricon.provider.LyriconFactory
import io.github.proify.lyricon.provider.LyriconProvider
import io.github.proify.lyricon.provider.ProviderLogo
import java.io.File

object LocalProvider : YukiBaseHooker(), DownloadCallback {
    private const val TAG = "LocalProvider"
    private const val ACTION_TRACK_CHANGED = "com.maxmpz.audioplayer.TRACK_CHANGED"

    private var provider: LyriconProvider? = null
    private var lastMediaSignature: String? = null
    private var trackReceiver: BroadcastReceiver? = null

    override fun onHook() {
        YLog.debug(tag = TAG, msg = "========== LocalProvider 已注入，进程名=$processName ==========")

        onAppLifecycle {
            onCreate {
                initProvider()
                setupPowerAmpReceiver()
            }
            onTerminate { release() }
        }

        hookMediaSession()
    }

    private fun initProvider() {
        val context = appContext ?: return

        val storageRoot = Environment.getExternalStorageDirectory()?.absolutePath ?: "/storage/emulated/0"
        val defaultDirs = listOf(
            "$storageRoot/Lyrics",
            "$storageRoot/Music",
            "$storageRoot/Download"
        )
        for (dir in defaultDirs) {
            val dirFile = File(dir)
            if (!dirFile.exists()) {
                dirFile.mkdirs()
                YLog.debug(tag = TAG, msg = "Created directory: $dir")
            }
            if (!PathManager.getPaths(context).contains(dir)) {
                PathManager.addPath(context, dir)
                YLog.debug(tag = TAG, msg = "Added directory: $dir")
            }
        }

        provider = LyriconFactory.createProvider(
            context = context,
            providerPackageName = Constants.PROVIDER_PACKAGE_NAME,
            playerPackageName = context.packageName,
            //logo = ProviderLogo.fromBase64(Constants.ICON),
            processName = processName
        ).apply { register() }
    }

    private fun setupPowerAmpReceiver() {
        val filter = IntentFilter(ACTION_TRACK_CHANGED)
        trackReceiver = object : BroadcastReceiver() {
            override fun onReceive(context: Context, intent: Intent) {
                val bundle = intent.extras ?: return
                val rawPath = bundle.getString("path") ?: return
                val uri = resolvePowerAmpUri(rawPath) ?: return
                val resolvedPath = uri.path ?: return

                val title = bundle.getString("title")
                val artist = bundle.getString("artist")
                val album = bundle.getString("album")
                val duration = bundle.getLong("durMs")

                YLog.debug(tag = TAG, msg = "PowerAmp broadcast: path=$resolvedPath, title=$title, artist=$artist")

                provider?.player?.setSong(Song(name = title, artist = artist))

                DownloadManager.setCurrentAudioPath(resolvedPath)
                DownloadManager.search(this@LocalProvider) {
                    trackName = title
                    artistName = artist
                    albumName = album
                    enableLocalSearch = true
                    audioFilePath = resolvedPath
                }
            }
        }.also {
            ContextCompat.registerReceiver(appContext!!, it, filter, ContextCompat.RECEIVER_EXPORTED)
        }
    }

    private fun resolvePowerAmpUri(standardId: String): Uri? {
        if (standardId.isBlank() || !standardId.contains(":")) return null
        val inputVolume = standardId.substringBefore(":")
        val contentResolver = appContext!!.contentResolver
        val persistedPermissions = contentResolver.persistedUriPermissions
        for (permission in persistedPermissions) {
            if (!permission.isReadPermission) continue
            val treeUri = permission.uri
            if ("com.android.externalstorage.documents" != treeUri.authority) continue
            val treeDocumentId = android.provider.DocumentsContract.getTreeDocumentId(treeUri) ?: continue
            val treeVolume = treeDocumentId.substringBefore(":")
            if (inputVolume.equals(treeVolume, ignoreCase = true) && standardId.startsWith(treeDocumentId)) {
                return android.provider.DocumentsContract.buildDocumentUriUsingTree(treeUri, standardId)
            }
        }
        return null
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

            firstMethod {
                name = "setMetadata"
                parameters("android.media.MediaMetadata")
            }.hook {
                after {
                    val metadata = args[0] as? MediaMetadata ?: return@after
                    handleMetadata(metadata)
                }
            }
        }
    }

    private fun handleMetadata(metadata: MediaMetadata) {
        val id = metadata.getString(MediaMetadata.METADATA_KEY_MEDIA_ID)
        val title = metadata.getString(MediaMetadata.METADATA_KEY_TITLE)
        val artist = metadata.getString(MediaMetadata.METADATA_KEY_ARTIST)
        val album = metadata.getString(MediaMetadata.METADATA_KEY_ALBUM)

        val mediaUriString = metadata.getString(MediaMetadata.METADATA_KEY_MEDIA_URI)
        val filePath = metadata.getString("filePath")
        var resolvedPath = when {
            !mediaUriString.isNullOrBlank() -> {
                val uri = Uri.parse(mediaUriString)
                if (uri.scheme == "file") uri.path
                else if (uri.scheme == "content") getPathFromContentUri(uri)
                else null
            }
            !filePath.isNullOrBlank() -> filePath
            else -> null
        }

        if (resolvedPath == null) {
            resolvedPath = getPathFromMediaStore(title, artist, album)
        }

        YLog.debug(tag = TAG, msg = "音频路径解析: mediaUri=$mediaUriString, filePath=$filePath, resolved=$resolvedPath")

        DownloadManager.setCurrentAudioPath(resolvedPath)

        val signature = calculateSignature(id, title, artist, album)
        if (signature == lastMediaSignature) return
        lastMediaSignature = signature

        provider?.player?.setSong(Song(name = title, artist = artist))

        DownloadManager.cancel()
        DownloadManager.search(this@LocalProvider) {
            trackName = title
            artistName = artist
            albumName = album
            enableLocalSearch = true
            audioFilePath = resolvedPath
        }
    }

    private fun getPathFromContentUri(uri: Uri): String? {
        val projection = arrayOf(MediaStore.Audio.Media.DATA)
        appContext?.contentResolver?.query(uri, projection, null, null, null)?.use { cursor ->
            if (cursor.moveToFirst()) {
                val columnIndex = cursor.getColumnIndexOrThrow(MediaStore.Audio.Media.DATA)
                return cursor.getString(columnIndex)
            }
        }
        return null
    }

    private fun getPathFromMediaStore(title: String?, artist: String?, album: String?): String? {
        if (title.isNullOrBlank()) return null
        val projection = arrayOf(MediaStore.Audio.Media.DATA)
        val selection = StringBuilder()
        val selectionArgs = mutableListOf<String>()
        selection.append("${MediaStore.Audio.Media.TITLE} = ?")
        selectionArgs.add(title)
        if (!artist.isNullOrBlank()) {
            selection.append(" AND ${MediaStore.Audio.Media.ARTIST} = ?")
            selectionArgs.add(artist)
        }
        if (!album.isNullOrBlank()) {
            selection.append(" AND ${MediaStore.Audio.Media.ALBUM} = ?")
            selectionArgs.add(album)
        }
        appContext?.contentResolver?.query(
            MediaStore.Audio.Media.EXTERNAL_CONTENT_URI,
            projection,
            selection.toString(),
            selectionArgs.toTypedArray(),
            null
        )?.use { cursor ->
            if (cursor.moveToFirst()) {
                val columnIndex = cursor.getColumnIndexOrThrow(MediaStore.Audio.Media.DATA)
                return cursor.getString(columnIndex)
            }
        }
        return null
    }

    private fun calculateSignature(vararg data: String?): String {
        return data.joinToString("") { it?.hashCode()?.toString() ?: "0" }.hashCode().toString()
    }

    override fun onDownloadFinished(response: List<ProviderLyrics>) {
        val song = response.firstOrNull()?.lyrics?.toSong()
        provider?.player?.setSong(song)
    }

    override fun onDownloadFailed(e: Exception) {
        YLog.error(tag = TAG, msg = "下载失败: ${e.message}")
    }

    private fun LyricsResult.toSong() = Song().apply {
        name = trackName
        artist = artistName
        lyrics = rich
        duration = rich.lastOrNull()?.end ?: 0L
    }

    private fun release() {
        trackReceiver?.let { appContext?.unregisterReceiver(it) }
        trackReceiver = null
    }
}