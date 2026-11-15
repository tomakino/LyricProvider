/*
 * Copyright 2026 Proify, Tomakino
 * Licensed under the Apache License, Version 2.0
 * http://www.apache.org/licenses/LICENSE-2.0
 */

package io.github.proify.lrckit

import io.github.proify.lyricon.lyric.model.LyricLine
import java.util.regex.Pattern

/**
 * LRC 解析器。
 * 针对网易云等平台非标格式优化，修复了歌词文本中包含中括号时导致的截断异常。
 */
object LrcParser {

    /** 仅用于快速判断行是否包含合法时间标签 */
    private val LINE_VALIDATOR =
        Pattern.compile("""^\[\d{1,3}[ :.]\d{2}(?:[ :.]\d{1,3})?].*""")

    /** 用于精确提取时间数值 */
    private val TAG_PATTERN =
        Pattern.compile("""\[(\d{1,3})[ :.](\d{2})(?:[ :.](\d{1,3}))?]""")

    /** 无下一行且无歌曲总时长时，给最后一行兜底的持续时长 */
    private const val FALLBACK_LINE_DURATION = 5000L

    /**
     * 解析原始歌词。
     * @param raw 原始文本。
     * @param duration 音频总时长，用于修正末行结束时间。
     */
    fun parse(raw: String?, duration: Long = 0): LrcDocument {
        if (raw.isNullOrBlank()) return LrcDocument()

        val entries = mutableListOf<LyricLine>()
        val meta = mutableMapOf<String, String>()

        raw.lineSequence().forEach { line ->
            val trimmed = line.trim()
            if (trimmed.isEmpty() || !trimmed.startsWith("[")) return@forEach

            if (LINE_VALIDATOR.matcher(trimmed).matches()) {
                val tagMatcher = TAG_PATTERN.matcher(trimmed)
                val tempTimes = mutableListOf<Long>()
                var lastTagEnd = 0

                // 只有处于行首且连续的时间标签才被视为时间轴
                while (tagMatcher.find()) {
                    if (tagMatcher.start() != lastTagEnd) {
                        break
                    }
                    tempTimes.add(
                        toMs(
                            tagMatcher.group(1),
                            tagMatcher.group(2),
                            tagMatcher.group(3)
                        )
                    )
                    lastTagEnd = tagMatcher.end()
                }

                // 剩余部分即为歌词正文，即使其中包含 [文本] 也不会被截断
                val content = trimmed.substring(lastTagEnd).trim()

                tempTimes.forEach { time ->
                    entries.add(LyricLine(begin = time, text = content))
                }
            } else {
                parseMeta(trimmed, meta)
            }
        }
        return finalize(entries, meta, duration)
    }

    /**
     * 时间转毫秒。强制识别为 [分:秒:毫秒]，自适应毫秒位数。
     */
    private fun toMs(mStr: String, sStr: String, fStr: String?): Long {
        val m = mStr.toLongOrNull() ?: 0L
        val s = sStr.toLongOrNull() ?: 0L
        val ms = when (fStr?.length) {
            1 -> fStr.toLong() * 100 // [ss:5] -> 500ms
            2 -> fStr.toLong() * 10  // [ss:50] -> 500ms
            3 -> fStr.toLong()       // [ss:500] -> 500ms
            else -> 0L
        }
        return m * 60000 + s * 1000 + ms
    }

    private fun parseMeta(line: String, meta: MutableMap<String, String>) {
        val colon = line.indexOf(":")
        if (colon > 1 && line.endsWith("]")) {
            val key = line.substring(1, colon).trim()
            val value = line.substring(colon + 1, line.length - 1).trim()
            meta[key] = value
        }
    }

    private fun finalize(list: List<LyricLine>, meta: Map<String, String>, dur: Long): LrcDocument {
        val sorted = list.sortedBy { it.begin }

        // 相同 begin 的行属于同一时间点：结束时间统一取"下一个更晚的时间点"，
        // 否则同一时间戳的重复行会得到 0 时长
        val distinctBegins = sorted.map { it.begin }.distinct()
        val successor = HashMap<Long, Long>(distinctBegins.size)
        distinctBegins.forEachIndexed { index, begin ->
            distinctBegins.getOrNull(index + 1)?.let { successor[begin] = it }
        }

        val lines = sorted.map { cur ->
            val end = successor[cur.begin]
                ?: if (dur > cur.begin) dur else cur.begin + FALLBACK_LINE_DURATION
            cur.copy(end = end, duration = end - cur.begin)
        }

        val offset = meta["offset"]?.toLongOrNull() ?: 0L
        return LrcDocument(meta, lines).run {
            if (offset != 0L) applyOffset(offset) else this
        }
    }
}
