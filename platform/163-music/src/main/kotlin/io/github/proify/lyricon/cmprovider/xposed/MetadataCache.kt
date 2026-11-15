/*
 * Copyright 2026 Proify, Tomakino
 * Licensed under the Apache License, Version 2.0
 * http://www.apache.org/licenses/LICENSE-2.0
 */

package io.github.proify.lyricon.cmprovider.xposed

import android.media.MediaMetadata
import android.util.Log
import kotlinx.serialization.Serializable
import org.json.JSONObject
import java.net.URI
import java.net.URLDecoder
import java.nio.charset.StandardCharsets
import java.util.concurrent.ConcurrentHashMap

@Serializable
data class Metadata(
    val id: Long,
    val title: String?,
    val artist: String?,
    val duration: Long,
    val lyric: String? = null
)

object MediaMetadataCache {
    private val map = ConcurrentHashMap<Long, Metadata>()

    fun save(metadata: MediaMetadata): Metadata? {
        val id = MediaIdParser.parse(
            metadata.getString(MediaMetadata.METADATA_KEY_MEDIA_ID)
        ) ?: return null
        val title = metadata.getString(MediaMetadata.METADATA_KEY_TITLE)
        val artist = metadata.getString(MediaMetadata.METADATA_KEY_ARTIST)
        val duration = metadata.getLong(MediaMetadata.METADATA_KEY_DURATION)
        val lyricInfo = metadata.getString("lyricInfo")

        metadata.keySet().forEach {
            runCatching {
                Log.i(CloudMusic.TAG, "key:" + it + " value:" + metadata.getString(it))
            }
        }


        Log.i(CloudMusic.TAG, "lyricInfo:" + lyricInfo.orEmpty())
        val lyric = runCatching {
            if (lyricInfo != null) {
                val jo = JSONObject(lyricInfo)
                jo.getString("lyric")
            } else null
        }.getOrNull()

        val data = Metadata(
            id,
            title,
            artist,
            duration,
            lyric
        )
        map[id] = data
        return data
    }

    fun get(id: Long): Metadata? = map[id]
}

/** Extracts a media id only when all supported representations agree on one value. */
internal object MediaIdParser {
    private val numeric = Regex("\\d+")
    private val colonSuffix = Regex(".*:(\\d+)$")

    fun parse(value: String?): Long? {
        val input = value?.trim().takeUnless { it.isNullOrEmpty() } ?: return null
        val candidates = linkedSetOf<Long>()

        addCandidate(input, candidates)

        val withoutFragment = input.substringBefore('#')
        val query = withoutFragment.substringAfter('?', missingDelimiterValue = "")
        query.split('&').filter { it.isNotEmpty() }.forEach { parameter ->
            val separator = parameter.indexOf('=')
            val rawKey = if (separator >= 0) parameter.substring(0, separator) else parameter
            if (decode(rawKey).equals("id", ignoreCase = true)) {
                val rawValue = if (separator >= 0) parameter.substring(separator + 1) else ""
                addCandidate(decode(rawValue), candidates)
            }
        }

        val withoutQuery = withoutFragment.substringBefore('?')
        val uri = runCatching { URI(withoutQuery) }.getOrNull()
        val rawPath = uri?.rawPath ?: withoutQuery.takeUnless { it.contains("://") }.orEmpty()
        rawPath.split('/').forEach { segment -> addCandidate(decode(segment), candidates) }

        val colonSource = when {
            uri?.isOpaque == true -> withoutQuery
            rawPath.isNotBlank() -> rawPath.substringAfterLast('/')
            !withoutQuery.contains("://") -> withoutQuery
            else -> null
        }
        colonSource?.let { source ->
            colonSuffix.matchEntire(source)?.groupValues?.getOrNull(1)?.let {
                addCandidate(it, candidates)
            }
        }

        return candidates.singleOrNull()
    }

    private fun addCandidate(value: String, candidates: MutableSet<Long>) {
        if (!numeric.matches(value)) return
        value.toLongOrNull()?.takeIf { it > 0 }?.let(candidates::add)
    }

    private fun decode(value: String): String = runCatching {
        URLDecoder.decode(value, StandardCharsets.UTF_8.name())
    }.getOrDefault(value)
}
