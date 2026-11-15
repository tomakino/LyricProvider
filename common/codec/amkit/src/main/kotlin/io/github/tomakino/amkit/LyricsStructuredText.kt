/*
 * Copyright 2026 Proify, Tomakino
 * Licensed under the Apache License, Version 2.0
 * http://www.apache.org/licenses/LICENSE-2.0
 */

@file:Suppress("unused")

package io.github.tomakino.amkit

import io.github.tomakino.amkit.model.LyricsAudio
import io.github.tomakino.amkit.model.LyricsDocument
import io.github.tomakino.amkit.model.LyricsLine
import io.github.tomakino.amkit.model.LyricsMetadata
import io.github.tomakino.amkit.model.LyricsSection
import io.github.tomakino.amkit.model.LyricsText
import io.github.tomakino.amkit.model.LyricsWord

/**
 * 把整份文档渲染成**分层的可读文本**（字段结构视图）。
 *
 * 与 [flatten] 一样是**模型之外的扩展**：领域模型只负责承载数据，
 * 渲染格式属于使用方视角，不该长在 `model` 包里。
 *
 * 默认的 `data class` `toString()` 会把整份歌词挤在一行里，歌词一多就没法读；
 * 这里按「文档 → 段落 → 行 → 词 / 背景 / 译文」分层缩进，每层带时间戳，
 * 词间分隔空格用 `␠` 显式标出。适合调试与人工核对：
 *
 * ```kotlin
 * println(document.toStructuredString())
 * ```
 *
 * 需要「像歌词一样排版 + 时间轴条」的视图用 `LyricsTextView`。
 *
 * ⚠️ 输出包含**完整歌词文本**，不要直接写进生产环境的外部日志。
 */
fun LyricsDocument.toStructuredString(): String = buildString {
    appendLine("LyricsDocument(")
    appendField("language", language)
    appendField("timing", timing)
    appendField("duration", duration)
    appendField("leadingSilence", leadingSilence)
    appendField("audio", audio?.describe())
    appendField("metadata", metadata.describe())
    appendField(
        "agents",
        agents.joinToString(separator = ", ") { agent ->
            val name = agent.firstDisplayName()?.let { ": $it" }.orEmpty()
            "${agent.id}(${agent.type ?: "?"}$name)"
        }.ifEmpty { "(无)" },
    )
    appendField("sections", sections.size)

    sections.forEach { section ->
        appendLine(section.describe())
        section.lines.forEach { line -> appendLine(line.describe()) }
    }

    append(")")
}

/** 输出时字段名左对齐到的宽度，让 `=` 排成一列。 */
private const val FIELD_WIDTH = 14

/** 词间分隔空格在输出里的可视化符号（U+2420 SYMBOL FOR SPACE），否则它看起来像是没输出。 */
private const val SPACE_MARKER = "␠"

private fun StringBuilder.appendField(name: String, value: Any?) {
    append("  ").append(name.padEnd(FIELD_WIDTH)).append(" = ")
    append(value ?: "(未声明)").append('\n')
}

private fun LyricsMetadata.describe(): String = buildString {
    append("title=").append(title ?: "null")
    append(", language=").append(language ?: "null")
    append(", songwriters=").append(songwriters)
    append(", meta=").append(meta)
}

/** `<audio>`：实测只有 `role=spatial`，`lyricOffset` 是歌词时间校准偏移。 */
private fun LyricsAudio.describe(): String =
    "role=${role ?: "null"}, lyricOffset=${lyricOffset ?: "null"}"

private fun LyricsSection.describe(): String = buildString {
    append("    [").append(songPart ?: "(无名)").append("] ")
    append(start ?: "-").append(" → ").append(end ?: "-")
    append("  (").append(lines.size).append(" 行)")
}

private fun LyricsLine.describe(): String = buildString {
    append("      ").append(key ?: "(无 key)")
    append("  [").append(start ?: "-").append(" → ").append(end ?: "-").append(']')
    agentId?.let { append("  agent=").append(it) }

    if (words.isNotEmpty()) {
        append("\n        words: ").append(words.describeAll())
    }
    if (background.isNotEmpty()) {
        append("\n        bg   : ").append(background.describeAll())
    }
    translations.forEach { append("\n        ").append(it.describe()) }
    transliterations.forEach { append("\n        ").append(it.describe()) }
}

private fun LyricsText.describe(): String = buildString {
    append(language ?: "?").append('/').append(type ?: "-")
    // 机器生成的译文/音译可靠性不同，输出里标出来
    if (autoCreated) append("[自动生成]")
    append(": ").append(text)
    if (words.isNotEmpty()) {
        append("\n          words: ").append(words.describeAll())
    }
    if (background.isNotEmpty()) {
        append("\n          bg   : ").append(background.describeAll())
    }
}

private fun List<LyricsWord>.describeAll(): String =
    joinToString(separator = " ") { it.describe() }

private fun LyricsWord.describe(): String = buildString {
    append(text.ifBlank { SPACE_MARKER })
    if (start != null || end != null) {
        append('[').append(start ?: "-").append('→').append(end ?: "-").append(']')
    }
}
