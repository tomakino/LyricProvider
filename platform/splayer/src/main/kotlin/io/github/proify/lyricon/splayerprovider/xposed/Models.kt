/*
 * Copyright 2026 Proify, Tomakino
 * Licensed under the Apache License, Version 2.0
 * http://www.apache.org/licenses/LICENSE-2.0
 */

package io.github.proify.lyricon.splayerprovider.xposed

import kotlinx.serialization.SerialName
import kotlinx.serialization.Serializable

@Serializable
data class LyricLine(
    val words: List<Word> = emptyList(),
    val translatedLyric: String = "",
    val romanLyric: String = "",
    @SerialName("isBG") val isBG: Boolean = false,
    @SerialName("isDuet") val isDuet: Boolean = false,
    val startTime: Long = 0L,
    val endTime: Long = 0L,
)

@Serializable
data class Word(
    val startTime: Long = 0L,
    val endTime: Long = 0L,
    val word: String = "",
    val romanWord: String = "",
)