/*
 * Copyright 2026 Proify, Tomakino
 * Licensed under the Apache License, Version 2.0
 * http://www.apache.org/licenses/LICENSE-2.0
 */

package io.github.proify.lrckit

import io.github.proify.lyricon.lyric.model.LyricWord
import io.github.proify.lyricon.lyric.model.RichLyricLine

/**
 * 增强型歌词解析器。
 * 兼容标准 LRC、逐字时间轴及多角色标签，并支持正文包含方括号 [] 的特殊文本。
 */
object EnhanceLrcParser {

    /** 严格匹配行首的一个或多个时间戳标签，如 [00:12.34][00:15.00] */
    private val TIME_PREFIX_REGEX = Regex("""^(\[(\d{1,3})[ :.](\d{2})(?:[ :.](\d{1,3}))?])+""")

    /** 匹配单个时间戳标签，用于从前缀字符串中提取具体数值 */
    private val TAG_REGEX = Regex("""\[(\d{1,3})[ :.](\d{2})(?:[ :.](\d{1,3}))?]""")

    /** 匹配逐字时间标签，如 <00:12.34> */
    private val WORD_REGEX = Regex("""<(\d{1,3})[ :.](\d{2})(?:[ :.](\d{1,3}))?>""")

    /** 识别角色前缀，支持 v1 (主唱) 或 bg (背景音) */
    private val PERSON_REGEX = Regex("""^(v\d+|bg):\s*(.+)$""", RegexOption.IGNORE_CASE)

    /** 匹配元数据标签，如 [ar:歌手名] */
    private val META_REGEX = Regex("""^\[(\w+)\s*:\s*(.*)]$""")

    /** 无下一行且无歌曲总时长时，给最后一行兜底的持续时长 */
    private const val FALLBACK_LINE_DURATION = 5000L

    /** 同一时间点的多段副轨道（和声/对唱）之间的分隔符 */
    private const val SECONDARY_SEPARATOR = "\n"

    fun parse(raw: String?, duration: Long = 0): EnhanceLrcDocument {
        if (raw.isNullOrBlank()) return EnhanceLrcDocument(emptyMap(), emptyList())

        val lines = mutableListOf<RichLyricLine>()
        val meta = mutableMapOf<String, String>()
        val roles = mutableListOf<String>()

        raw.lineSequence().forEach { rawLine ->
            val trimmed = rawLine.trim()
            if (!trimmed.startsWith("[")) return@forEach

            val timeMatch = TIME_PREFIX_REGEX.find(trimmed)
            if (timeMatch != null) {
                // 核心修复：通过截取时间戳匹配范围之后的字符串作为正文，避免正文内的 [] 被误识别
                val content = trimmed.substring(timeMatch.range.last + 1).trim()
                parseStandardLine(timeMatch.value, content, roles).forEach { cur ->
                    mergeLines(lines, cur)
                }
            } else {
                // 非时间戳开头的行尝试作为元数据处理
                handleMeta(trimmed, meta, lines)
            }
        }

        val offset = meta["offset"]?.toLongOrNull() ?: 0L
        val finalizedLines = finalize(lines, duration)

        return EnhanceLrcDocument(meta, finalizedLines).run {
            if (offset != 0L) applyOffset(offset) else this
        }
    }

    /**
     * 合并同时间戳行。若当前行与上一行开始时间一致，则将其追加为上一行的 secondary 副轨道。
     * 通过 copy 重建列表元素而非就地修改，避免与调用方共享同一个可变实例。
     */
    private fun mergeLines(lines: MutableList<RichLyricLine>, cur: RichLyricLine) {
        val last = lines.lastOrNull()
        if (last == null || last.begin != cur.begin) {
            lines.add(cur)
            return
        }

        lines[lines.lastIndex] = last.copy(
            secondary = appendText(last.secondary, cur.text),
            secondaryWords = appendWords(last.secondaryWords, cur.words),
            isAlignedRight = last.isAlignedRight || cur.isAlignedRight
        )
    }

    /**
     * 追加副轨道文本。多段和声按换行拼接，避免后段覆盖前段造成信息丢失。
     */
    private fun appendText(existing: String?, incoming: String?): String? {
        val parts = listOfNotNull(
            existing?.takeIf { it.isNotEmpty() },
            incoming?.takeIf { it.isNotEmpty() }
        )
        return parts.takeIf { it.isNotEmpty() }?.joinToString(SECONDARY_SEPARATOR)
    }

    /**
     * 追加副轨道的逐字数据。段与段之间插入一个换行分隔词，
     * 保证 secondaryWords 直接拼接后与 secondary 文本严格一致。
     * @param boundary 分隔词的时间点，取后一段首词时间，保证时间轴非降序。
     */
    private fun appendWords(
        existing: List<LyricWord>?,
        incoming: List<LyricWord>?
    ): List<LyricWord>? {
        if (existing.isNullOrEmpty()) return incoming
        if (incoming.isNullOrEmpty()) return existing

        val boundary = incoming.first().begin
        val separator = LyricWord(begin = boundary, end = boundary, text = SECONDARY_SEPARATOR)
        return existing + separator + incoming
    }

    /**
     * 解析具体的一行歌词内容。
     * @param timeTags 仅包含行首时间戳的字符串。
     * @param content 歌词正文内容。
     */
    private fun parseStandardLine(
        timeTags: String,
        content: String,
        roles: MutableList<String>
    ): List<RichLyricLine> {
        var person: String? = null
        var text = content

        PERSON_REGEX.matchEntire(content)?.let {
            person = it.groupValues[1].lowercase()
            text = it.groupValues[2]
            // 第一个出现的非 bg 角色被定义为主角色
            if (roles.isEmpty() && person != "bg") roles.add(person)
        }

        val words = parseWords(text)
        // 逐字文本必须与 words 直接拼接结果一致（与 RichLyricLine.normalize 及 QRC/YRC/KRC 保持一致），
        // 插入任何分隔符都会破坏中文/日文逐字歌词的显示
        val plainText = if (words.isNotEmpty()) {
            words.joinToString("") { it.text.orEmpty() }
        } else {
            text
        }

        // 背景音或非首位角色默认右对齐
        val isRight =
            person == "bg" || (person != null && roles.isNotEmpty() && person != roles.first())

        return TAG_REGEX.findAll(timeTags).map { m ->
            val ms = toMs(m.groupValues[1], m.groupValues[2], m.groupValues.getOrNull(3))
            RichLyricLine(
                begin = words.firstOrNull()?.begin ?: ms,
                // 行的结束时间统一交给 finalize 推导，此处只记录该时间戳本身
                end = ms,
                text = plainText,
                words = words.takeIf { it.isNotEmpty() },
                isAlignedRight = isRight
            )
        }.toList()
    }

    /**
     * 解析逐字时间。通过寻找连续的 <time> 标签并截取其中间的字符来实现。
     * 注意：最后一个字只有开始时间（无后续标签），其结束时间由 finalize 对齐到行结束时间。
     */
    private fun parseWords(content: String): List<LyricWord> {
        val matches = WORD_REGEX.findAll(content).toList()
        if (matches.isEmpty()) return emptyList()

        return matches.mapIndexed { i, m ->
            val start = toMs(m.groupValues[1], m.groupValues[2], m.groupValues.getOrNull(3))
            val nextMatch = matches.getOrNull(i + 1)
            // 截取当前标签结束到下一个标签开始之间的文字
            val text =
                content.substring(m.range.last + 1, nextMatch?.range?.first ?: content.length)

            LyricWord(begin = start, text = text).apply {
                nextMatch?.let { next ->
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

    /**
     * 转换时间标签为毫秒。根据毫秒位字符串长度自动调整倍率（1位x100, 2位x10, 3位x1）。
     */
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

    /**
     * 处理元数据。特殊逻辑：若 tag 为 bg，则尝试将其内容作为上一行歌词的副轨道合并。
     */
    private fun handleMeta(
        line: String,
        meta: MutableMap<String, String>,
        lines: MutableList<RichLyricLine>
    ) {
        META_REGEX.matchEntire(line)?.let { m ->
            val tag = m.groupValues[1].lowercase()
            val value = m.groupValues[2].trim()

            if (tag == "bg" && lines.isNotEmpty()) {
                val words = parseWords(value)
                val last = lines.last()
                val resolvedText =
                    if (words.isNotEmpty()) words.joinToString("") { it.text.orEmpty() } else value

                lines[lines.lastIndex] = last.copy(
                    secondary = appendText(last.secondary, resolvedText),
                    secondaryWords = appendWords(
                        last.secondaryWords,
                        words.takeIf { it.isNotEmpty() })
                )
            } else {
                meta[tag] = value
            }
        }
    }

    /**
     * 完成解析后的后处理：排序时间轴，推导每行结束时间，并把逐字末词对齐到行结束时间。
     * 全过程通过 copy 产出新对象，不就地修改传入的行对象。
     */
    private fun finalize(lines: List<RichLyricLine>, totalDur: Long): List<RichLyricLine> {
        val sorted = lines.sortedBy { it.begin }

        // 相同 begin 视为同一时间点：预计算每个时间点之后的下一个更晚时间点
        val distinctBegins = sorted.map { it.begin }.distinct()
        val successor = HashMap<Long, Long>(distinctBegins.size)
        distinctBegins.forEachIndexed { index, begin ->
            distinctBegins.getOrNull(index + 1)?.let { successor[begin] = it }
        }

        return sorted.map { line ->
            val lineEnd = resolveLineEnd(line, successor[line.begin], totalDur)
            line.copy(
                end = lineEnd,
                duration = (lineEnd - line.begin).coerceAtLeast(0L),
                words = alignLastWordTo(line.words, lineEnd)
            )
        }
    }

    /**
     * 行结束时间优先级：已有的有效结束时间 > 下一个更晚的时间点 > 歌曲总时长 > 兜底时长。
     */
    private fun resolveLineEnd(
        line: RichLyricLine,
        nextDistinctBegin: Long?,
        totalDur: Long
    ): Long = when {
        line.end > line.begin -> line.end
        nextDistinctBegin != null -> nextDistinctBegin
        totalDur > line.begin -> totalDur
        else -> line.begin + FALLBACK_LINE_DURATION
    }

    /**
     * 逐字时间轴的最后一个字只有开始时间、没有结束时间，
     * 统一对齐到行结束时间，使卡拉 OK 末字同样具备有效时长（与 KRC 的收尾处理保持一致）。
     */
    private fun alignLastWordTo(words: List<LyricWord>?, lineEnd: Long): List<LyricWord>? {
        val last = words?.lastOrNull() ?: return words
        if (last.end >= lineEnd) return words

        // 数据源畸形（逐字时间晚于行结束）时至少保证 end 不早于 begin，避免产生非法区间
        val alignedEnd = maxOf(lineEnd, last.begin)
        val aligned =
            last.copy(end = alignedEnd, duration = (alignedEnd - last.begin).coerceAtLeast(0L))
        return words.dropLast(1) + aligned
    }
}
