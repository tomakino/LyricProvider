/*
 * Copyright 2026 Proify, Tomakino
 * Licensed under the Apache License, Version 2.0
 * http://www.apache.org/licenses/LICENSE-2.0
 */

package io.github.proify.cloudlyric

data class ProviderLyrics(
    val provider: LyricsProvider,
    val lyrics: LyricsResult
) {

    override fun toString(): String {
        return "${provider.id}, $lyrics"
    }
}