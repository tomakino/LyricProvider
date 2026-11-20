/*
 * Copyright 2026 Proify, Tomakino
 * Licensed under the Apache License, Version 2.0
 * http://www.apache.org/licenses/LICENSE-2.0
 */

package io.github.proify.amkit.parser.model

/** 把文档渲染成分层文本。 */
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

private const val FIELD_WIDTH = 14

/** 空白的可视化符号（U+2420）。 */
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
