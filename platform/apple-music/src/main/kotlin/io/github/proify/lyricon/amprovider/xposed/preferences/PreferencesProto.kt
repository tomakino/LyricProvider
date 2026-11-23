/*
 * Copyright 2026 Proify, Tomakino
 * Licensed under the Apache License, Version 2.0
 * http://www.apache.org/licenses/LICENSE-2.0
 */

package io.github.proify.lyricon.amprovider.xposed.preferences

import java.io.File

/** 最小 Preferences DataStore 读取器（PreferencesProto.PreferenceMap）。 */
internal object PreferencesProto {

    fun readBoolean(file: File, key: String): Boolean? {
        val bytes = runCatching { file.readBytes() }.getOrNull() ?: return null
        var offset = 0

        while (offset < bytes.size) {
            val tag = readVarint(bytes, offset) ?: return null
            offset = tag.second
            val field = tag.first ushr 3
            val wire = (tag.first and 0x07).toInt()

            if (field == 1L && wire == 2) {
                val length = readVarint(bytes, offset) ?: return null
                offset = length.second
                val end = offset + length.first.toInt()
                if (end > bytes.size) return null

                val entry = readEntry(bytes, offset, end)
                if (entry?.first == key) return entry.second
                offset = end
            } else {
                offset = skip(bytes, offset, wire) ?: return null
            }
        }
        return null
    }

    private fun readEntry(bytes: ByteArray, start: Int, end: Int): Pair<String, Boolean?>? {
        var offset = start
        var key: String? = null
        var value: Boolean? = null

        while (offset < end) {
            val tag = readVarint(bytes, offset) ?: return null
            offset = tag.second
            val field = tag.first ushr 3
            val wire = (tag.first and 0x07).toInt()

            when (field) {
                1L if wire == 2 -> {
                    val length = readVarint(bytes, offset) ?: return null
                    offset = length.second
                    val valueEnd = offset + length.first.toInt()
                    if (valueEnd > end) return null
                    key = String(bytes, offset, valueEnd - offset, Charsets.UTF_8)
                    offset = valueEnd
                }

                2L if wire == 2 -> {
                    val length = readVarint(bytes, offset) ?: return null
                    offset = length.second
                    val valueEnd = offset + length.first.toInt()
                    if (valueEnd > end) return null
                    value = readValue(bytes, offset, valueEnd)
                    offset = valueEnd
                }

                else -> offset = skip(bytes, offset, wire) ?: return null
            }
        }
        return if (key == null) null else key to value
    }

    private fun readValue(bytes: ByteArray, start: Int, end: Int): Boolean? {
        var offset = start

        while (offset < end) {
            val tag = readVarint(bytes, offset) ?: return null
            offset = tag.second
            val field = tag.first ushr 3
            val wire = (tag.first and 0x07).toInt()

            if (field == 1L && wire == 0) {
                val value = readVarint(bytes, offset) ?: return null
                return value.first != 0L
            }
            offset = skip(bytes, offset, wire) ?: return null
        }
        return null
    }

    private fun readVarint(bytes: ByteArray, start: Int): Pair<Long, Int>? {
        var result = 0L
        var shift = 0
        var offset = start

        while (offset < bytes.size && shift < 64) {
            val byte = bytes[offset].toInt() and 0xFF
            result = result or ((byte and 0x7F).toLong() shl shift)
            offset++
            if (byte and 0x80 == 0) return result to offset
            shift += 7
        }
        return null
    }

    private fun skip(bytes: ByteArray, start: Int, wire: Int): Int? = when (wire) {
        0 -> readVarint(bytes, start)?.second
        1 -> (start + 8).takeIf { it <= bytes.size }
        2 -> {
            val length = readVarint(bytes, start) ?: return null
            (length.second + length.first.toInt()).takeIf { it <= bytes.size }
        }

        5 -> (start + 4).takeIf { it <= bytes.size }
        else -> null
    }
}
