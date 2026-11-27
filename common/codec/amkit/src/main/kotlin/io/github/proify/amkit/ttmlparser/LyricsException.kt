/*
 * Copyright 2026 Proify, Tomakino
 * Licensed under the Apache License, Version 2.0
 * http://www.apache.org/licenses/LICENSE-2.0
 */

package io.github.proify.amkit.ttmlparser

/** 解析失败时抛出。 */
class LyricsParseException(
    message: String,
    cause: Throwable? = null,
) : Exception(message, cause)
