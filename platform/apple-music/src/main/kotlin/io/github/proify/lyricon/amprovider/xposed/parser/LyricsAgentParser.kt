/*
 * Copyright 2026 Proify, Tomakino
 * Licensed under the Apache License, Version 2.0
 * http://www.apache.org/licenses/LICENSE-2.0
 */

package io.github.proify.lyricon.amprovider.xposed.parser

import io.github.proify.lyricon.amprovider.xposed.model.LyricAgent

object LyricsAgentParser {

    /** 解析原生 Agent Vector 为 [LyricAgent] 列表。 */
    fun parse(any: Any): MutableList<LyricAgent> = parseNativeVector(any) { parseAgent(it) }

    private fun parseAgent(native: Any): LyricAgent = LyricAgent().apply {
        nameTypes = callMethod(native, "getNameTypes_") as? IntArray ?: intArrayOf()
        type = callMethod(native, "getType_") as? Long ?: 0L
        id = callMethod(native, "getId") as? String

        nameTypeNames = LyricAgent.nameTypesToNames(nameTypes)
        typeName = LyricAgent.typeOf(type)?.name
    }
}
