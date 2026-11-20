/*
 * Copyright 2026 Proify, Tomakino
 * Licensed under the Apache License, Version 2.0
 * http://www.apache.org/licenses/LICENSE-2.0
 */

package io.github.proify.qrckit

import io.github.proify.qrckit.parser.QrcDownloader
import io.github.proify.qrckit.parser.QrcLyricResponse
import io.github.proify.qrckit.parser.model.ParsedLyric
import kotlin.test.Test

class QrcDownloaderTest {
    @Test
    fun testDownload() {
        val response: QrcLyricResponse = QrcDownloader.download("105603406")
        val data: ParsedLyric = response.parsedLyric

        //println(response.rawXml)

        data.richLyricLines.forEach {
            println(it)
        }
    }
}