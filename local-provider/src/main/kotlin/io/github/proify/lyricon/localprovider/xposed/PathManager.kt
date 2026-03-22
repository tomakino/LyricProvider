/*
 * Copyright 2026 Proify, Tomakino
 * Licensed under the Apache License, Version 2.0
 * http://www.apache.org/licenses/LICENSE-2.0
 */

package io.github.proify.lyricon.localprovider.xposed

import android.content.Context
import android.content.SharedPreferences
import kotlinx.serialization.Serializable
import kotlinx.serialization.json.Json
import kotlinx.serialization.encodeToString
import kotlinx.serialization.decodeFromString

@Serializable
data class PathList(val paths: List<String> = emptyList())

object PathManager {
    private const val PREFS_NAME = "lyric_paths"
    private const val KEY_PATHS = "paths"
    private val json = Json { ignoreUnknownKeys = true }

    fun getPaths(context: Context): List<String> {
        val prefs = context.getSharedPreferences(PREFS_NAME, Context.MODE_PRIVATE)
        val jsonStr = prefs.getString(KEY_PATHS, null) ?: return emptyList()
        return try {
            json.decodeFromString<PathList>(jsonStr).paths
        } catch (e: Exception) {
            emptyList()
        }
    }

    fun addPath(context: Context, path: String) {
        val current = getPaths(context).toMutableList()
        if (current.contains(path)) return
        current.add(path)
        savePaths(context, current)
    }

    fun removePath(context: Context, path: String) {
        val current = getPaths(context).toMutableList()
        current.remove(path)
        savePaths(context, current)
    }

    private fun savePaths(context: Context, paths: List<String>) {
        val jsonStr = json.encodeToString(PathList(paths))
        context.getSharedPreferences(PREFS_NAME, Context.MODE_PRIVATE)
            .edit()
            .putString(KEY_PATHS, jsonStr)
            .apply()
    }
}