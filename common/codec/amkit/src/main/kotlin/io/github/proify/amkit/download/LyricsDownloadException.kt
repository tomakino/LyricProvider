/*
 * Copyright 2026 Tomakino
 * Licensed under the Apache License, Version 2.0
 * http://www.apache.org/licenses/LICENSE-2.0
 */

package io.github.proify.amkit.download

/** 下载失败时抛出。 */
class LyricsDownloadException(
    message: String,
    cause: Throwable? = null,
) : Exception(message, cause)
