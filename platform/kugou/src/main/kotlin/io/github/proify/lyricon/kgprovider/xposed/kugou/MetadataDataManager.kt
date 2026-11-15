/*
 * Copyright 2026 Proify, Tomakino
 * Licensed under the Apache License, Version 2.0
 * http://www.apache.org/licenses/LICENSE-2.0
 */

@file:Suppress("unused")

package io.github.proify.lyricon.kgprovider.xposed.kugou

object MetadataDataManager {
    private val metadataDataMap = mutableMapOf<String, MetadataData>()

    fun get(songId: String): MetadataData? {
        return metadataDataMap[songId]
    }

    fun put(metadataData: MetadataData) {
        metadataDataMap[metadataData.generateId] = metadataData
    }

    fun remove(songId: String) {
        metadataDataMap.remove(songId)
    }

    fun clear() {
        metadataDataMap.clear()
    }
}