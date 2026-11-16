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
import io.github.tomakino.amkit.model.LyricsLine
import io.github.tomakino.amkit.model.LyricsWord

/**
 * [LyricsDocument] → [Song]。
 *
 * 本地化区分两类：同语言字形转换（`zh-Hant` → `zh-Hans`）替换主文本且不产生翻译；
 * 跨语言（`en` → `zh`）保留原文，译文放进 [RichLyricLine.translation]。
 * 多演唱者的左右分列复刻宿主 `PlayerLyricsViewModel` 的逐行推演。
 */
object LyricsMapper {

    fun toSong(
        id: String,
        name: String?,
        artist: String?,
        durationMs: Long,
        document: LyricsDocument,
    ): Song {
        val targetLanguage = AppleLocale.lyricsLanguage()
        val sameLanguage = isSameLanguage(document.language, targetLanguage)
        val rightAligned = rightAlignedIndices(document.lines, document.agents)

        val lines = document.lines.mapIndexed { index, line ->
            val localized = line.translations.firstOrNull {
                it.language?.startsWith(targetLanguage, ignoreCase = true) == true
            }
            val roma = line.transliterations.firstOrNull()?.text
            val originalText = line.text
            val localizedText = localized?.text
            val asPrimary = sameLanguage && localizedText != null
            val begin = line.start?.inWholeMilliseconds ?: 0L
            val end = line.end?.inWholeMilliseconds
                ?: line.words.lastOrNull()?.end?.inWholeMilliseconds
                ?: begin

            RichLyricLine().apply {
                this.begin = begin
                this.end = end
                duration = (end - begin).coerceAtLeast(0L)
                text = if (asPrimary) localizedText else originalText
                words = if (asPrimary) {
                    alignWords(line.words, originalText, localizedText).map { it.toLyricWord() }
                } else {
                    line.words.map { it.toLyricWord() }
                }.toMutableList()
                secondary = line.backgroundText.ifBlank { null }
                secondaryWords = line.background.map { it.toLyricWord() }.toMutableList()
                translation = if (!sameLanguage) localizedText?.takeIf { it.isNotBlank() } else null
                translationWords = if (!sameLanguage) {
                    localized?.words?.map { it.toLyricWord() }
                } else {
                    null
                }
                this.roma = roma
                // 宿主 `isAlignedRight = true` 渲染在右，与 Apple 的 6（右）同向
                isAlignedRight = index in rightAligned
            }
        }.toMutableList()

        return Song(
            id = id,
            name = name,
            artist = artist,
            duration = durationMs,
            lyrics = lines,
        )
    }

    /** 只比较主语言子标签：`zh-Hant` 与 `zh-Hans` 属于同一语言。 */
    private fun isSameLanguage(source: String?, target: String): Boolean {
        val sourceLanguage = source?.substringBefore('-')?.lowercase() ?: return false
        return sourceLanguage == target.substringBefore('-').lowercase()
    }

    /**
     * 把逐字文本替换为译文中的对应字符。
     *
     * 比较与切分都忽略空白：中文原文通常不含空格，译文可能带空格。
     * 逐字符映射可以处理整行只有一个 span 的情况。
     */
    private fun alignWords(
        words: List<LyricsWord>,
        original: String,
        localized: String?,
    ): List<LyricsWord> {
        if (localized == null) return words

        val originalCompact = original.filterNot { it.isWhitespace() }
        val localizedCompact = localized.filterNot { it.isWhitespace() }
        if (originalCompact.length != localizedCompact.length) return words

        var offset = 0
        return words.map { word ->
            if (word.text.isBlank()) return@map word

            val builder = StringBuilder(word.text.length)
            var consumed = true
            for (ch in word.text) {
                if (ch.isWhitespace()) {
                    builder.append(ch)
                } else {
                    if (offset >= localizedCompact.length) {
                        consumed = false
                        break
                    }
                    builder.append(localizedCompact[offset])
                    offset++
                }
            }
            if (consumed) word.copy(text = builder.toString()) else word
        }
    }

    /**
     * 复刻宿主 `PlayerLyricsViewModel` 的逐行对齐推演（`x.s0`）。
     *
     * 首行由 agent 类型决定：`person` 右、`group` 左；同一 agent 连续沿用；
     * 换人时按前后行的文本空/非空状态决定是否换边，因此对唱歌词会随演唱者交替。
     * 没有出现右对齐时返回空集（宿主同样不启用分列）。
     */
    private fun rightAlignedIndices(
        lines: List<LyricsLine>,
        agents: List<LyricsAgent>,
    ): Set<Int> {
        val personCount = agents.count { it.type.equals("person", ignoreCase = true) }
        val otherCount = agents.count { it.type.equals("other", ignoreCase = true) }
        if (personCount + otherCount <= 1) return emptySet()

        val alignment = IntArray(lines.size) { ALIGN_DEFAULT }
        val agentIds = lines.map { it.agentId }
        var hasRight = false

        for (index in lines.indices) {
            val agentId = agentIds[index]
            if (agentId.isNullOrBlank()) continue

            alignment[index] = resolveAlignment(
                index = index,
                agentId = agentId,
                agents = agents,
                agentIds = agentIds,
                lines = lines,
                alignment = alignment,
            )
            if (alignment[index] == ALIGN_RIGHT) hasRight = true
        }

        if (!hasRight) return emptySet()
        return alignment.indices.filter { alignment[it] == ALIGN_RIGHT }.toSet()
    }

    private fun resolveAlignment(
        index: Int,
        agentId: String,
        agents: List<LyricsAgent>,
        agentIds: List<String?>,
        lines: List<LyricsLine>,
        alignment: IntArray,
    ): Int {
        val type = agents.firstOrNull { it.id == agentId }?.type

        // 宿主的 `lineId` 从 1 开始；回溯到 0 时按类型给初始边。
        // 探针实测：Person 首行算出的 textAlignment 是 5（宿主内部 5=左、6=右）。
        if (index < 0) {
            return when {
                type == null -> ALIGN_DEFAULT
                type.equals("person", ignoreCase = true) -> ALIGN_LEFT
                type.equals("group", ignoreCase = true) -> ALIGN_RIGHT
                else -> ALIGN_DEFAULT
            }
        }
        if (type.equals("group", ignoreCase = true)) return ALIGN_LEFT

        val previous = if (index > 0) alignment[index - 1] else ALIGN_DEFAULT
        if (previous == ALIGN_DEFAULT) {
            return resolveAlignment(index - 1, agentId, agents, agentIds, lines, alignment)
        }
        if (agentId == agentIds[index - 1]) return previous

        val currentBlank = lines[index].text.isBlank()
        val previousBlank = lines[index - 1].text.isBlank()
        return if (previous == ALIGN_LEFT) {
            if (previousBlank == currentBlank) ALIGN_RIGHT else ALIGN_LEFT
        } else {
            if (previousBlank != currentBlank) ALIGN_RIGHT else ALIGN_LEFT
        }
    }

    private fun LyricsWord.toLyricWord(): LyricWord {
        val begin = start?.inWholeMilliseconds ?: 0L
        val end = this.end?.inWholeMilliseconds ?: begin
        return LyricWord(
            text = text,
            begin = begin,
            duration = (end - begin).coerceAtLeast(0L),
            end = end,
        )
    }

    private const val ALIGN_DEFAULT = 1
    private const val ALIGN_LEFT = 5
    private const val ALIGN_RIGHT = 6
}
