/*
 * Copyright 2026 Proify, Tomakino
 * Licensed under the Apache License, Version 2.0
 * http://www.apache.org/licenses/LICENSE-2.0
 */

package io.github.proify.lyricon.amprovider.xposed.parser

import io.github.proify.lyricon.amprovider.xposed.model.LyricTiming

object LyricsTimingParser {

    /** 将原生时间信息（agent / begin / end / duration）填到 [timing] 中。 */
    fun parse(timing: LyricTiming, native: Any) {
        timing.agent = callMethod(native, "getAgent") as? String
        timing.begin = callMethod(native, "getBegin") as? Int ?: 0
        timing.end = callMethod(native, "getEnd") as? Int ?: 0
        timing.duration = callMethod(native, "getDuration") as? Int ?: 0
    }
}
