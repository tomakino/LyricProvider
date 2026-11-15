/*
 * Copyright 2026 Proify, Tomakino
 * Licensed under the Apache License, Version 2.0
 * http://www.apache.org/licenses/LICENSE-2.0
 */

package io.github.proify.lyricon.amprovider.xposed.lyrics

import io.github.proify.lyricon.lyric.model.LyricWord
import io.github.proify.lyricon.lyric.model.RichLyricLine
import io.github.proify.lyricon.lyric.model.Song
import io.github.tomakino.amkit.model.LyricsAgent
import io.github.tomakino.amkit.model.LyricsDocument
import io.github.tomakino.amkit.model.LyricsWord

/**
 * 把 amkit 的 [LyricsDocument] 映射为 Lyricon 的 [Song]。
 *
 * 映射规则与旧的原生对象解析保持一致：
 * - 行时间取 `<p>` 的 begin/end
 * - 词序列同时映射主唱与背景人声
 * - 翻译取第一份译文文本
 * - 多演唱者时前两个 `person` agent 分别左/右对齐
 */
object LyricsMapper {

    fun toSong(
        id: String,
        name: String?,
        artist: String?,
        durationMs: Long,
        document: LyricsDocument
    ): Song {
        val rightAgentIds = rightAlignedAgentIds(document.agents)
        val lines = document.lines.map { line ->
            val begin = line.start?.inWholeMilliseconds ?: 0L
            val end = line.end?.inWholeMilliseconds ?: begin

            RichLyricLine().apply {
                this.begin = begin
                this.end = end
                duration = (end - begin).coerceAtLeast(0L)
                text = line.text
                words = line.words.map { it.toLyricWord() }.toMutableList()
                secondary = line.backgroundText.ifBlank { null }
                secondaryWords = line.background.map { it.toLyricWord() }.toMutableList()
                translation = line.translations.firstOrNull()?.text
                isAlignedRight = line.agentId != null && line.agentId in rightAgentIds
            }
        }.toMutableList()

        return Song(
            id = id,
            name = name,
            artist = artist,
            duration = durationMs,
            lyrics = lines
        )
    }

    /** 第一个 person 为默认（左），第二个为右。 */
    private fun rightAlignedAgentIds(agents: List<LyricsAgent>): Set<String> {
        val persons = agents.filter { it.type.equals("person", ignoreCase = true) }
        return if (persons.size >= 2) setOf(persons[1].id) else emptySet()
    }

    private fun LyricsWord.toLyricWord(): LyricWord {
        val begin = start?.inWholeMilliseconds ?: 0L
        val end = this.end?.inWholeMilliseconds ?: begin
        return LyricWord(
            text = text,
            begin = begin,
            duration = (end - begin).coerceAtLeast(0L),
            end = end
        )
    }
}
