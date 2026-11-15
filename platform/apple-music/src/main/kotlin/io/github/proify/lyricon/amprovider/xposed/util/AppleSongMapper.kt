/*
 * Copyright 2026 Proify, Tomakino
 * Licensed under the Apache License, Version 2.0
 * http://www.apache.org/licenses/LICENSE-2.0
 */

package io.github.proify.lyricon.amprovider.xposed.util

import io.github.proify.lyricon.amprovider.xposed.model.AppleSong
import io.github.proify.lyricon.amprovider.xposed.model.LyricAgent
import io.github.proify.lyricon.amprovider.xposed.model.LyricLine
import io.github.proify.lyricon.lyric.model.LyricWord
import io.github.proify.lyricon.lyric.model.RichLyricLine
import io.github.proify.lyricon.lyric.model.Song
import io.github.proify.lyricon.amprovider.xposed.model.LyricWord as AppleLyricWord

fun AppleSong.toSong(): Song = AppleSongMapper.map(this)

object AppleSongMapper {

    fun map(song: AppleSong): Song = Song(
        id = song.adamId,
        name = song.name,
        artist = song.artist,
        duration = song.duration.toLong(),
        lyrics = mapLyrics(song.lyrics, song.agents)
    )

    private fun mapLyrics(
        appleLyrics: List<LyricLine>,
        agents: List<LyricAgent>
    ): MutableList<RichLyricLine> {
        val agentDirection = computeAgentDirections(agents)

        return appleLyrics.map { appleLine ->
            RichLyricLine().apply {
                begin = appleLine.begin.toLong()
                end = appleLine.end.toLong()
                duration = appleLine.duration.toLong()

                text = appleLine.htmlLineText
                words = appleLine.words.map { it.toLyricWord() }.toMutableList()

                secondary = appleLine.htmlBackgroundVocalsLineText
                secondaryWords = appleLine.backgroundWords.map { it.toLyricWord() }.toMutableList()

                translation = appleLine.htmlTranslationLineText
                isAlignedRight = agentDirection[appleLine.agent] == LyricDirection.RIGHT
            }
        }.toMutableList()
    }

    private fun AppleLyricWord.toLyricWord(): LyricWord = LyricWord(
        text = text,
        begin = begin.toLong(),
        duration = duration.toLong(),
        end = end.toLong()
    )

    /**
     * 计算 Agent ID 与歌词对齐方向的映射。
     * 规则：取前两个类型为 PERSON 的 Agent，第一个为左（默认），第二个为右。
     */
    private fun computeAgentDirections(agents: List<LyricAgent>?): Map<String, LyricDirection> {
        if (agents.isNullOrEmpty()) return emptyMap()

        val personAgents = agents.filter {
            LyricAgent.typeOf(it.type) == LyricAgent.Type.PERSON
        }
        // 人数不足两个时无需区分左右
        if (personAgents.size < 2) return emptyMap()

        return buildMap {
            personAgents[0].id?.let { put(it, LyricDirection.DEFAULT) }
            personAgents[1].id?.let { put(it, LyricDirection.RIGHT) }
        }
    }

    private enum class LyricDirection {
        DEFAULT, RIGHT
    }
}
