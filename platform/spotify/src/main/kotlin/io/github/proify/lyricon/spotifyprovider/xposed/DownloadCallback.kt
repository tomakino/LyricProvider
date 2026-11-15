/*
 * Copyright 2026 Proify, Tomakino
 * Licensed under the Apache License, Version 2.0
 * http://www.apache.org/licenses/LICENSE-2.0
 */

package io.github.proify.lyricon.spotifyprovider.xposed

interface DownloadCallback {
    fun onDownloadFinished(id: String, response: ByteArray)
    fun onDownloadFailed(id: String, e: Exception)
}
