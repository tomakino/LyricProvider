/*
 * Copyright 2026 Proify, Tomakino
 * Licensed under the Apache License, Version 2.0
 * http://www.apache.org/licenses/LICENSE-2.0
 */

package io.github.proify.lyricon.localprovider.lrckit

import io.github.proify.lyricon.lyric.model.LyricWord
import io.github.proify.lyricon.lyric.model.RichLyricLine

/**
 * 增强型歌词解析器。
 * 支持逐字时间轴、多角色区分、bg语义化合并及内联时间戳格式（如[00:00.715]世[00:00.924]界）。
 */
object EnhanceLrcParser {

    private val LINE_REGEX = Regex("""^(?:\[(\d{1,3})[ :.](\d{2})(?:[ :.](\d{1,3}))?])+(.*)$""")
    private val TAG_REGEX = Regex("""\[(\d{1,3})[ :.](\d{2})(?:[ :.](\d{1,3}))?]""")
    private val WORD_REGEX = Regex("""<(\d{1,3})[ :.](\d{2})(?:[ :.](\d{1,3}))?>""")
    private val PERSON_REGEX = Regex("""^(v\d+|bg):\s*(.+)$""", RegexOption.IGNORE_CASE)
    private val META_REGEX = Regex("""^\[(\w+)\s*:\s*([^]]*)]$""")

    fun parse(raw: String?, duration: Long = 0): EnhanceLrcDocument {
        if (raw.isNullOrBlank()) return EnhanceLrcDocument(emptyMap(), emptyList())

        val lines = mutableListOf<RichLyricLine>()
        val meta = mutableMapOf<String, String>()
        val roles = mutableListOf<String>()

        raw.lineSequence().forEach { rawLine ->
            val trimmed = rawLine.trim()
            if (!trimmed.startsWith("[")) return@forEach

            val match = LINE_REGEX.matchEntire(trimmed)
            if (match != null) {
                val content = match.groupValues.last().trim()
                parseStandardLine(trimmed, content, roles).forEach { cur ->
                    mergeLines(lines, cur)
                }
            } else {
                handleMeta(trimmed, meta, lines)
            }
        }
        return EnhanceLrcDocument(meta, finalize(lines, duration))
    }

    private fun mergeLines(lines: MutableList<RichLyricLine>, cur: RichLyricLine) {
        val last = lines.lastOrNull()
        if (last != null && last.begin == cur.begin) {
            last.secondary = cur.text
            last.secondaryWords = cur.words
            if (cur.isAlignedRight) last.isAlignedRight = true
        } else {
            lines.add(cur)
        }
    }

    private fun parseStandardLine(
        raw: String,
        content: String,
        roles: MutableList<String>
    ): List<RichLyricLine> {
        var person: String? = null
        var text = content

        PERSON_REGEX.matchEntire(content)?.let {
            person = it.groupValues[1].lowercase()
            text = it.groupValues[2]
            if (roles.isEmpty() && person != "bg") roles.add(person)
        }

        var words = parseWords(text)
        var begin = 0L
        var end = 0L

        if (words.isEmpty()) {
            // 尝试内联时间戳格式
            val tags = TAG_REGEX.findAll(raw).toList()
            if (tags.size > 1) {
                val newWords = mutableListOf<LyricWord>()
                for (i in tags.indices) {
                    val start = toMs(tags[i].groupValues[1], tags[i].groupValues[2], tags[i].groupValues.getOrNull(3))
                    val nextStart = if (i + 1 < tags.size) {
                        toMs(tags[i+1].groupValues[1], tags[i+1].groupValues[2], tags[i+1].groupValues.getOrNull(3))
                    } else {
                        start + 500
                    }
                    val startIdx = tags[i].range.last + 1
                    val endIdx = if (i + 1 < tags.size) tags[i+1].range.first else raw.length
                    val wordText = raw.substring(startIdx, endIdx).trim()
                    if (wordText.isNotEmpty()) {
                        newWords.add(
                            LyricWord(
                                begin = start,
                                end = nextStart,
                                duration = (nextStart - start).coerceAtLeast(0),
                                text = wordText
                            )
                        )
                    }
                }
                if (newWords.isNotEmpty()) {
                    words = newWords
                    text = words.joinToString("") { it.text.orEmpty() }
                }
            }
        }

        val plainText = words.takeIf { it.isNotEmpty() }?.joinToString("") { it.text ?: "" } ?: text
        val isRight = person == "bg" || (person != null && roles.isNotEmpty() && person != roles.first())

        if (words.isNotEmpty()) {
            begin = words.first().begin
            end = words.last().end
        } else {
            val firstTag = TAG_REGEX.find(raw)
            begin = firstTag?.let { toMs(it.groupValues[1], it.groupValues[2], it.groupValues.getOrNull(3)) } ?: 0L
            end = begin
        }

        return listOf(
            RichLyricLine(
                begin = begin,
                end = end,
                text = plainText,
                words = words.takeIf { it.isNotEmpty() },
                isAlignedRight = isRight
            )
        )
    }

    private fun parseWords(content: String): List<LyricWord> {
        val matches = WORD_REGEX.findAll(content).toList()
        return matches.mapIndexed { i, m ->
            val start = toMs(m.groupValues[1], m.groupValues[2], m.groupValues.getOrNull(3))
            val text = content.substring(
                m.range.last + 1,
                matches.getOrNull(i + 1)?.range?.first ?: content.length
            )
            LyricWord(begin = start, text = text).apply {
                matches.getOrNull(i + 1)?.let { next ->
                    end = toMs(
                        next.groupValues[1],
                        next.groupValues[2],
                        next.groupValues.getOrNull(3)
                    )
                    duration = (end - begin).coerceAtLeast(0)
                }
            }
        }
    }

    private fun toMs(mStr: String, sStr: String, fStr: String?): Long {
        val m = mStr.toLongOrNull() ?: 0L
        val s = sStr.toLongOrNull() ?: 0L
        val ms = when (fStr?.length) {
            1 -> fStr.toLong() * 100
            2 -> fStr.toLong() * 10
            3 -> fStr.toLong()
            else -> 0L
        }
        return m * 60000 + s * 1000 + ms
    }

    private fun handleMeta(
        line: String,
        meta: MutableMap<String, String>,
        lines: List<RichLyricLine>
    ) {
        META_REGEX.matchEntire(line)?.let { m ->
            val tag = m.groupValues[1].lowercase()
            val value = m.groupValues[2].trim()
            if (tag == "bg" && lines.isNotEmpty()) {
                val words = parseWords(value)
                lines.last().apply {
                    secondary = words.takeIf { it.isNotEmpty() }?.joinToString("") { it.text ?: "" } ?: value
                    secondaryWords = words.takeIf { it.isNotEmpty() }
                }
            } else meta[tag] = value
        }
    }

    private fun finalize(lines: List<RichLyricLine>, totalDur: Long): List<RichLyricLine> {
        val sorted = lines.sortedBy { it.begin }
        sorted.forEachIndexed { i, cur ->
            if (cur.end <= cur.begin) {
                val next = sorted.getOrNull(i + 1)?.begin
                cur.end = next ?: if (totalDur > cur.begin) totalDur else cur.begin + 5000L
            }
            cur.duration = cur.end - cur.begin
        }
        return sorted
    }
}