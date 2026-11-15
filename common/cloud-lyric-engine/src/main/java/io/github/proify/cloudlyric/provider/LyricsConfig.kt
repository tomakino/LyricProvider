/*
 * Copyright 2026 Proify, Tomakino
 * Licensed under the Apache License, Version 2.0
 * http://www.apache.org/licenses/LICENSE-2.0
 */

@file:Suppress("unused")

package io.github.proify.cloudlyric.provider

import io.github.proify.cloudlyric.LyricsProvider
import io.github.proify.cloudlyric.provider.lrclib.LrcLibProvider
import io.github.proify.cloudlyric.provider.qq.QQMusicProvider

object LyricsConfig {
    val ALL_PROVIDERS: List<LyricsProvider> = listOf(
        QQMusicProvider(),
        LrcLibProvider()
    )
}