/*
 * Copyright 2026 Proify, Tomakino
 * Licensed under the Apache License, Version 2.0
 * http://www.apache.org/licenses/LICENSE-2.0
 */

@file:Suppress("unused")

package io.github.proify.lyricon.kgprovider.xposed.kugou

import android.util.Log
import io.github.proify.lyricon.lyric.model.RichLyricLine

object LyricsCache {
    
    private const val TAG = "LyricsCache"
    
    private val cache = mutableMapOf<String, CachedLyrics>()
    
    private data class CachedLyrics(
        val lyrics: List<RichLyricLine>,
        val songId: String
    )
    
    fun put(songId: String, lyrics: List<RichLyricLine>) {
        cache[songId] = CachedLyrics(lyrics, songId)
        Log.d(TAG, "Cached lyrics for: $songId (${lyrics.size} lines)")
    }
    
    fun get(songId: String): List<RichLyricLine>? {
        val cached = cache[songId]
        if (cached != null) {
            Log.d(TAG, "Cache hit for: $songId")
            return cached.lyrics
        }
        Log.d(TAG, "Cache miss for: $songId")
        return null
    }
    
    fun has(songId: String): Boolean = cache.containsKey(songId)
    
    fun remove(songId: String) {
        cache.remove(songId)
        Log.d(TAG, "Removed cache for: $songId")
    }
    
    fun clear() {
        cache.clear()
        Log.d(TAG, "Cache cleared")
    }
    
    fun size(): Int = cache.size
}
