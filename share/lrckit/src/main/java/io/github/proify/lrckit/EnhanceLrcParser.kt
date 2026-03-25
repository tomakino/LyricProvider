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
                val timeTags = timeMatch.value

                parseStandardLine(timeTags, content, roles).forEach { cur ->
                    mergeLines(lines, cur)
                }
            } else {
                // 非时间戳开头的行尝试作为元数据处理
                handleMeta(trimmed, meta, lines, roles)
            }
        }

        val offset = meta["offset"]?.toLongOrNull() ?: 0L
        val finalizedLines = finalize(lines, duration)

        return EnhanceLrcDocument(meta, finalizedLines).run {
            if (offset != 0L) applyOffset(offset) else this
        }
    }

    /**
     * 合并同时间戳行。若当前行与上一行开始时间一致，则将其设为上一行的 secondary 轨道。
     */
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
        // **** 关键修改：单词之间添加空格，使英文显示正常 ****
        val plainText = if (words.isNotEmpty()) {
            words.joinToString(" ") { it.text ?: "" }
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
                end = words.lastOrNull()?.end ?: ms,
                text = plainText,
                words = words.takeIf { it.isNotEmpty() },
                isAlignedRight = isRight
            )
        }.toList()
    }

    /**
     * 解析逐字时间。通过寻找连续的 <time> 标签并截取其中间的字符来实现。
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
    @Suppress("unused")
    private fun handleMeta(
        line: String,
        meta: MutableMap<String, String>,
        lines: MutableList<RichLyricLine>,
        roles: List<String>
    ) {
        META_REGEX.matchEntire(line)?.let { m ->
            val tag = m.groupValues[1].lowercase()
            val value = m.groupValues[2].trim()

            if (tag == "bg" && lines.isNotEmpty()) {
                val words = parseWords(value)
                lines.last().apply {
                    secondary =
                        if (words.isNotEmpty()) words.joinToString("") { it.text ?: "" } else value
                    secondaryWords = words.takeIf { it.isNotEmpty() }
                }
            } else {
                meta[tag] = value
            }
        }
    }

    /**
     * 完成解析后的后处理：排序时间轴，并根据下一行或歌曲总时长补全当前行的结束时间。
     */
    private fun finalize(lines: List<RichLyricLine>, totalDur: Long): List<RichLyricLine> {
        val sorted = lines.sortedBy { it.begin }
        sorted.forEachIndexed { i, cur ->
            if (cur.end <= cur.begin) {
                val nextBegin = sorted.getOrNull(i + 1)?.begin
                cur.end = nextBegin ?: if (totalDur > cur.begin) totalDur else cur.begin + 5000L
            }
            cur.duration = (cur.end - cur.begin).coerceAtLeast(0)
        }
        return sorted
    }
}
