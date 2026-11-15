/*
 * Copyright 2026 Proify, Tomakino
 * Licensed under the Apache License, Version 2.0
 * http://www.apache.org/licenses/LICENSE-2.0
 */

package io.github.proify.lyricon.paprovider.xposed

import io.github.proify.cloudlyric.ProviderLyrics

interface DownloadCallback {
    fun onDownloadFinished(metadata: TrackMetadata, response: List<ProviderLyrics>)
    fun onDownloadFailed(metadata: TrackMetadata, e: Exception)
}