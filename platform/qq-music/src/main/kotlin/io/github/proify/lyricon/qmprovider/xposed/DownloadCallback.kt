/*
 * Copyright 2026 Proify, Tomakino
 * Licensed under the Apache License, Version 2.0
 * http://www.apache.org/licenses/LICENSE-2.0
 */

package io.github.proify.lyricon.qmprovider.xposed

import io.github.proify.qrckit.LyricResponse

interface DownloadCallback {
    fun onDownloadFinished(response: LyricResponse)
    fun onDownloadFailed(id: String, e: Exception)
}