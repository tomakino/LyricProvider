/*
 * Copyright 2026 Proify, Tomakino
 * Licensed under the Apache License, Version 2.0
 * http://www.apache.org/licenses/LICENSE-2.0
 */

package io.github.proify.lyricon.cmprovider.xposed

import io.github.proify.lyricon.yrckit.download.YrcDownloader
import kotlinx.coroutines.CoroutineScope
import kotlinx.coroutines.Dispatchers
import kotlinx.coroutines.SupervisorJob
import kotlinx.coroutines.launch
import java.util.concurrent.ConcurrentHashMap

object Downloader {
    private val downloadGate = DownloadGate()
    private val scope = CoroutineScope(Dispatchers.IO + SupervisorJob())

    fun download(id: Long, downloadCallback: DownloadCallback) {
        if (!downloadGate.tryStart(id)) return

        scope.launch {
            try {
                val response = YrcDownloader.fetchLyric(id)
                downloadCallback.onDownloadFinished(id, response)
            } catch (e: Exception) {
                downloadCallback.onDownloadFailed(id, e)
            } finally {
                downloadGate.finish(id)
            }
        }
    }
}

/** Atomically owns at most one in-flight download for each media id. */
internal class DownloadGate {
    private val activeIds = ConcurrentHashMap.newKeySet<Long>()

    fun tryStart(id: Long): Boolean = activeIds.add(id)

    fun finish(id: Long) {
        activeIds.remove(id)
    }
}
