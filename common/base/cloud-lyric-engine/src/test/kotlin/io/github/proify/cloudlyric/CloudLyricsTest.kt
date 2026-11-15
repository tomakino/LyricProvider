/*
 * Copyright 2026 Proify, Tomakino
 * Licensed under the Apache License, Version 2.0
 * http://www.apache.org/licenses/LICENSE-2.0
 */

package io.github.proify.cloudlyric

import io.github.proify.cloudlyric.provider.qq.QQMusicProvider
import kotlinx.coroutines.runBlocking
import kotlin.test.Test

class CloudLyricsTest {

    @Test
    fun search(): Unit = runBlocking {
        val cloudLyrics = CloudLyrics(listOf(QQMusicProvider()))

        val start = System.currentTimeMillis()
        val result = cloudLyrics.search {
            trackName = "简单爱"
            artistName = "周杰伦"
            albumName = "范特西"
            perProviderLimit = 5
            maxTotalResults = 1
        }
        println("Time: ${System.currentTimeMillis() - start}ms")

        println("Result: ${result.size}")
        result.forEach {
            println(it)
        }
    }
}