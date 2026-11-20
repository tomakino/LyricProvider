/*
 * Copyright 2026 Proify, Tomakino
 * Licensed under the Apache License, Version 2.0
 * http://www.apache.org/licenses/LICENSE-2.0
 */

package io.github.proify.amkit.parser.model

import kotlin.time.Duration

/** 歌词计时模式。 */
enum class LyricsTiming {
    /** 逐字计时。 */
    WORD,

    /** 逐行计时。 */
    LINE,

    /** 无计时。 */
    NONE,

    /** 未知。 */
    UNKNOWN;

    companion object {
        /** 把属性值映射为计时模式，未知值归入 [UNKNOWN]。 */
        fun fromAttribute(rawValue: String?): LyricsTiming =
            when (rawValue?.trim()?.lowercase()) {
                "word" -> WORD
                "line" -> LINE
                "none" -> NONE
                else -> UNKNOWN
            }
    }
}

/** 音频信息。 */
data class LyricsAudio(
    /** 播放角色。 */
    val role: String? = null,

    /** 歌词时间校准偏移。 */
    val lyricOffset: Duration? = null,
)

/** 文档元数据。 */
data class LyricsMetadata(
    val title: String? = null,
    val language: String? = null,

    /** 扩展键值。 */
    val meta: Map<String, List<String>> = emptyMap(),

    val songwriters: List<String> = emptyList(),
) {
    /** 取某个键的第一个值。 */
    fun firstMeta(key: String): String? = meta[key]?.firstOrNull()
}

/** 段落。 */
data class LyricsSection(
    /** 段落名。 */
    val songPart: String? = null,

    val start: Duration? = null,
    val end: Duration? = null,
    val lines: List<LyricsLine> = emptyList(),
)

/** 整份歌词文档。 */
data class LyricsDocument(
    val language: String? = null,
    val timing: LyricsTiming = LyricsTiming.UNKNOWN,
    val metadata: LyricsMetadata = LyricsMetadata(),
    val agents: List<LyricsAgent> = emptyList(),
    val sections: List<LyricsSection> = emptyList(),
    val duration: Duration? = null,

    /** 首句歌词前的静音时长。 */
    val leadingSilence: Duration? = null,

    val audio: LyricsAudio? = null,
) {
    /** 所有行，按出现顺序。 */
    val lines: List<LyricsLine> by lazy { sections.flatMap { it.lines } }

    /** 按行 key 查询。 */
    fun lineByKey(key: String): LyricsLine? = lines.firstOrNull { it.key == key }

    /**
     * 取指定语言的译文。
     *
     * @param language 语言标签
     * @param fallback 是否允许逐级回退
     * @return 首条匹配的译文
     */
    fun translationFor(language: String, fallback: Boolean = false): LyricsText? =
        lines.firstNotNullOfOrNull { line -> line.translations.matchLanguage(language, fallback) }

    /**
     * 取指定语言的音译。
     *
     * @param language 语言标签
     * @param fallback 是否允许逐级回退
     * @return 首条匹配的音译
     */
    fun transliterationFor(language: String, fallback: Boolean = false): LyricsText? =
        lines.firstNotNullOfOrNull { line -> line.transliterations.matchLanguage(language, fallback) }

    /** 是否包含指定语言的译文。 */
    fun hasTranslation(language: String, fallback: Boolean = false): Boolean =
        translationFor(language, fallback) != null

    /** 出现过的译文语言。 */
    val translationLanguages: List<String>
        get() = lines.flatMap { line -> line.translations.mapNotNull { it.language } }.distinct()

    /** 出现过的音译语言。 */
    val transliterationLanguages: List<String>
        get() = lines.flatMap { line -> line.transliterations.mapNotNull { it.language } }.distinct()
}
