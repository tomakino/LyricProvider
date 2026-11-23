/*
 * Copyright 2026 Proify, Tomakino
 * Licensed under the Apache License, Version 2.0
 * http://www.apache.org/licenses/LICENSE-2.0
 */

package io.github.proify.amkit.parser

import io.github.proify.amkit.locale.SystemLyricsLocale
import io.github.proify.amkit.parser.model.LyricsAgent
import io.github.proify.amkit.parser.model.LyricsDocument
import io.github.proify.amkit.parser.model.LyricsLine
import io.github.proify.amkit.parser.model.LyricsWord
import io.github.proify.lyricon.lyric.model.LyricWord
import io.github.proify.lyricon.lyric.model.RichLyricLine
import io.github.proify.lyricon.lyric.model.Song

/**
 * [LyricsDocument] → [io.github.proify.lyricon.lyric.model.Song]。
 *
 * 本地化区分两类：同语言字形转换（`zh-Hant` → `zh-Hans`）替换主文本且不产生翻译；
 * 跨语言（`en` → `zh`）保留原文，译文放进 [io.github.proify.lyricon.lyric.model.RichLyricLine.translation]。
 * 多演唱者的左右分列复刻宿主 `PlayerLyricsViewModel` 的逐行推演。
 */

fun LyricsDocument.toSong(
    id: String? = null,
    name: String? = null,
    artist: String? = null,
    durationMs: Long = 0,
): Song {
    val document = this
    val targetLanguage = SystemLyricsLocale.languageTag()
    val rightAligned = rightAlignedIndices(document.lines, document.agents)
    // amkit 负责本地化视图（替换原文 / 对照译文 / 逐字兜底），这里只做模型转换
    val displays = document.displayLines(DisplayOptions(language = targetLanguage))

    val lines = document.lines.mapIndexed { index, line ->
        val display = displays[index]
        val begin = line.start?.inWholeMilliseconds ?: 0L
        val end = line.end?.inWholeMilliseconds
            ?: line.words.lastOrNull()?.end?.inWholeMilliseconds
            ?: begin

        RichLyricLine().apply {
            this.begin = begin
            this.end = end
            duration = (end - begin).coerceAtLeast(0L)
            text = display.text
            words = display.words.toTimedWords()
            secondary = display.background
            secondaryWords = display.backgroundWords.toTimedWords()
            translation = display.translation
            translationWords =
                display.translationWords?.takeIf { it.hasCompleteTiming() }?.toTimedWords()
            roma = line.transliterations.firstOrNull()?.text
            // 宿主 `isAlignedRight = true` 渲染在右，与 Apple 的 6（右）同向
            isAlignedRight = index in rightAligned
        }
    }.toMutableList()

    return Song(
        id = id,
        name = name,
        artist = artist,
        duration = if (durationMs <= 0) lines.lastOrNull()?.end ?: durationMs else durationMs,
        lyrics = lines,
    )
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

/** 词序列是否带完整的逐词时间。 */
private fun List<LyricsWord>.hasCompleteTiming(): Boolean =
    isNotEmpty() && all { it.start != null && it.end != null }

/** 带完整时间的词序列；时间不完整时返回空，交由调用方按行渲染。 */
private fun List<LyricsWord>.toTimedWords(): MutableList<LyricWord> =
    if (hasCompleteTiming()) mapTo(mutableListOf()) { it.toLyricWord() } else mutableListOf()

fun LyricsWord.toLyricWord(): LyricWord {
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