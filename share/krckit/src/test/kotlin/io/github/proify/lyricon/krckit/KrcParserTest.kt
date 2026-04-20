/*
 * Copyright 2026 Proify, Tomakino
 * Licensed under the Apache License, Version 2.0
 * http://www.apache.org/licenses/LICENSE-2.0
 */

package io.github.proify.lyricon.krckit

import junit.framework.TestCase

class KrcParserTest : TestCase() {
    fun testParse() {
        val krcText = readResourceFile("極楽浄土.krc")
        val krcLyric = KrcParser.parse(krcText)

        krcLyric.richLyricLines.forEach {
            println(it)
        }
    }

    fun readResourceFile(fileName: String): String {
        val inputStream = javaClass.classLoader.getResourceAsStream(fileName)
            ?: throw IllegalArgumentException("File $fileName not found")
        return inputStream.bufferedReader().use { it.readText() }
    }
}