/*
 * Copyright 2026 Proify, Tomakino
 * Licensed under the Apache License, Version 2.0
 * http://www.apache.org/licenses/LICENSE-2.0
 */
package io.github.proify.lyricon.localprovider.xposed

import io.github.proify.lyricon.localprovider.model.ProviderLyrics
import io.github.proify.lyricon.localprovider.local.ExternalLrcProvider
import kotlinx.coroutines.CoroutineScope
import kotlinx.coroutines.Dispatchers
import kotlinx.coroutines.Job
import kotlinx.coroutines.SupervisorJob
import kotlinx.coroutines.launch

object DownloadManager {

    private val scope = CoroutineScope(Dispatchers.IO + SupervisorJob())
    private var currentTask: Job? = null

    @Volatile
    var currentAudioPath: String? = null
        private set

    fun setCurrentAudioPath(path: String?) {
        currentAudioPath = path
    }

    fun search(downloadCallback: DownloadCallback, block: SearchOptions.() -> Unit) {
        currentTask = scope.launch {
            val path = currentAudioPath
            val options = SearchOptions().apply(block)

            if (path != null) {
                val embeddedResult = EmbeddedLyricsProvider.searchByAudioFile(
                    audioFilePath = path,
                    trackName = options.trackName,
                    artistName = options.artistName,
                    albumName = options.albumName
                )
                if (embeddedResult != null) {
                    downloadCallback.onDownloadFinished(listOf(ProviderLyrics(EmbeddedProviderPlaceholder, embeddedResult)))
                    return@launch
                }

                val externalProvider = ExternalLrcProvider()
                val externalResult = externalProvider.searchByAudioFile(
                    audioFilePath = path,
                    trackName = options.trackName,
                    artistName = options.artistName,
                    albumName = options.albumName
                )
                if (externalResult != null) {
                    downloadCallback.onDownloadFinished(listOf(ProviderLyrics(externalProvider, externalResult)))
                    return@launch
                }
            }

            val externalProvider = ExternalLrcProvider()
            val metadataResult = externalProvider.searchByMetadata(
                trackName = options.trackName,
                artistName = options.artistName,
                albumName = options.albumName
            )
            if (metadataResult != null) {
                downloadCallback.onDownloadFinished(listOf(ProviderLyrics(externalProvider, metadataResult)))
                return@launch
            }

            downloadCallback.onDownloadFinished(emptyList())
        }
    }

    fun cancel() {
        currentTask?.cancel()
        currentTask = null
    }

    private object EmbeddedProviderPlaceholder : io.github.proify.lyricon.localprovider.model.LyricsProvider {
        override val id = "Embedded"
        override suspend fun search(
            query: String?,
            trackName: String?,
            artistName: String?,
            albumName: String?,
            limit: Int
        ): List<io.github.proify.lyricon.localprovider.model.LyricsResult> = emptyList()
    }
}

data class SearchOptions(
    var trackName: String? = null,
    var artistName: String? = null,
    var albumName: String? = null,
    var enableLocalSearch: Boolean = false,
    var audioFilePath: String? = null
)