/*
 * Copyright 2026 Proify, Tomakino
 * Licensed under the Apache License, Version 2.0
 * http://www.apache.org/licenses/LICENSE-2.0
 */

@file:Suppress("unused")

package io.github.proify.lyricon.paprovider.xposed

import android.annotation.SuppressLint
import io.github.proify.cloudlyric.CloudLyrics
import io.github.proify.cloudlyric.SearchOptions
import io.github.proify.cloudlyric.provider.lrclib.LrcLibProvider
import io.github.proify.cloudlyric.provider.qq.QQMusicProvider
import io.github.proify.extensions.isChinese
import kotlinx.coroutines.CoroutineScope
import kotlinx.coroutines.Dispatchers
import kotlinx.coroutines.SupervisorJob
import kotlinx.coroutines.launch
import java.util.Locale

object Downloader {

    private val scope = CoroutineScope(Dispatchers.IO + SupervisorJob())

    @SuppressLint("ConstantLocale")
    val cloudLyrics = CloudLyrics(
        if (Locale.getDefault().isChinese()) {
            listOf(
                QQMusicProvider()
            )
        } else {
            listOf(
                LrcLibProvider()
            )
        }
    )

    fun search(
        metadata: TrackMetadata,
        downloadCallback: DownloadCallback,
        block: SearchOptions.() -> Unit
    ) {
        scope.launch {
            try {
                val response = cloudLyrics.search(block)
                downloadCallback.onDownloadFinished(metadata, response)
            } catch (e: Exception) {
                downloadCallback.onDownloadFailed(metadata, e)
            }
        }
    }
}