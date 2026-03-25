/*
 * Copyright 2026 Proify, Tomakino
 * Licensed under the Apache License, Version 2.0
 * http://www.apache.org/licenses/LICENSE-2.0
 */
package io.github.proify.lyricon.localprovider.xposed

import io.github.proify.lyricon.localprovider.model.ProviderLyrics

interface DownloadCallback {
    fun onDownloadFinished(response: List<ProviderLyrics>)
    fun onDownloadFailed(e: Exception)
}