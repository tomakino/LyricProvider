/*
 * Copyright 2026 Proify, Tomakino
 * Licensed under the Apache License, Version 2.0
 * http://www.apache.org/licenses/LICENSE-2.0
 */

package io.github.proify.amkit.ttmlparser

import io.github.proify.amkit.ttmlparser.model.LyricsText

/** 译文/音译条目。 */
internal data class LyricsTranslationSidecar(
    /** 行 key 到文本。 */
    val entries: Map<String, LyricsText> = emptyMap(),
)
