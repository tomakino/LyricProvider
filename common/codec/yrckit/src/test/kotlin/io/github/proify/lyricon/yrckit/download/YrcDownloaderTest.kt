/*
 * Copyright 2026 Proify, Tomakino
 * Licensed under the Apache License, Version 2.0
 * http://www.apache.org/licenses/LICENSE-2.0
 */

package io.github.proify.lyricon.yrckit.download

import io.github.proify.lrckit.EnhanceLrcParser
import kotlin.test.Test

class YrcDownloaderTest {

    @Test
    fun fetchLyric() {
        val r = YrcDownloader.fetchLyric(2728097231)
        println(r)
        val lines = EnhanceLrcParser.parse(r.tlyric?.lyric.orEmpty())
        for (line in lines.lines) {
            println(line)
        }
    }

}