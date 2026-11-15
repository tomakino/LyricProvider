/*
 * Copyright 2026 Tomakino
 * Licensed under the Apache License, Version 2.0
 * http://www.apache.org/licenses/LICENSE-2.0
 */

package io.github.tomakino.amkit.model

import kotlin.time.Duration

/**
 * 歌词计时模式，对应 TTML 根元素上的 `itunes:timing`。
 *
 * 实测 Apple 会给出**三种**取值，另加一个兜底：
 *  - `Word` —— `syllable-lyrics` 逐字歌词
 *  - `Line` —— 只有行级时间（`/lyrics` 端点，或该曲只提供逐行歌词）
 *  - `None` —— **完全不带时间信息**的静态歌词：`<body>` 无 `dur`、`<div>` 无属性、
 *   连 `<p>` 都没有 `begin`。归到 [UNKNOWN] 会丢掉「明确无时间」这一语义，
 *    调用方就无法区分「这首歌没有同步歌词」与「遇到了没见过的取值」。
 */
enum class LyricsTiming {
    /** 逐字计时：`<p>` 表示行，行内带时间戳的 `<span>` 表示词。 */
    WORD,

    /** 逐行计时：只有 `<p>` 级别的时间戳，词级时间不可用。 */
    LINE,

    /** 无计时：整份歌词不含任何时间戳，只能静态展示。 */
    NONE,

    /** 属性缺失或取值无法识别。 */
    UNKNOWN;

    companion object {
        /** 大小写不敏感地把属性值映射为枚举，未知值一律归入 [UNKNOWN]。 */
        fun fromAttribute(rawValue: String?): LyricsTiming =
            when (rawValue?.trim()?.lowercase()) {
                "word" -> WORD
                "line" -> LINE
                "none" -> NONE
                else -> UNKNOWN
            }
    }
}

/**
 * `iTunesMetadata` 里的 `<audio>` 元素。
 *
 * 实测形态（43/385 首出现，每份文档至多一个）：
 * ```xml
 * <audio lyricOffset="0.045" role="spatial"/>
 * ```
 *
 * - [role] 目前只观测到 `spatial`（空间音频）
 * - [lyricOffset] 是**歌词时间的校准偏移**，实测取值 **-0.511 ~ 1.164 秒**（有正有负），
 *   播放器可用它把歌词与音频对齐
 */
data class LyricsAudio(
    /** `role`，实测只有 `spatial`。 */
    val role: String? = null,

    /** `lyricOffset`：歌词时间校准偏移。 */
    val lyricOffset: Duration? = null,
)

/**
 * 文档级元数据。
 *
 * [meta] 保留 `amll:meta` 的原始键值，**同一个 key 允许出现多次**
 * —— AMLL 社区格式中 `musicName`、`artists`、`album` 等会带多语言与多平台变体，
 * 因此值类型是 `List<String>` 而非 `String`。取首个值请用 [firstMeta]。
 *
 * 注意：实测 Apple 的 `syllable-lyrics` / `lyrics` 响应**不含 `amll:meta`**
 * （那是 AMLL 社区格式的扩展），所以对接 Apple 时这里通常为空。
 */
data class LyricsMetadata(
    val title: String? = null,
    val language: String? = null,
    val meta: Map<String, List<String>> = emptyMap(),
    val songwriters: List<String> = emptyList(),
) {
    /** 取某个 `amll:meta` key 的第一个值，不存在时返回 `null`。 */
    fun firstMeta(key: String): String? = meta[key]?.firstOrNull()
}

/**
 * `div` 段落，可携带 `itunes:songPart`（如 `Verse`、`Chorus`）。
 *
 * 段落**自带时间范围**：实测 Apple 的每个 `<div>` 都带 `begin` / `end`，
 * 可用于段落级跳转、进度条分段与高亮当前段落。
 */
data class LyricsSection(
    val songPart: String? = null,
    val start: Duration? = null,
    val end: Duration? = null,
    val lines: List<LyricsLine> = emptyList(),
)

/** 整份歌词文档，是解析结果的根对象。 */
data class LyricsDocument(
    val language: String? = null,
    val timing: LyricsTiming = LyricsTiming.UNKNOWN,
    val metadata: LyricsMetadata = LyricsMetadata(),
    val agents: List<LyricsAgent> = emptyList(),
    val sections: List<LyricsSection> = emptyList(),
    val duration: Duration? = null,

    /**
     * `iTunesMetadata@leadingSilence`：歌曲开头到**第一句歌词**之间的静音时长。
     *
     * Apple 用它表达前奏留白（实测取值如 `0.060`、`0.140`），播放器据此决定
     * 何时开始滚动歌词。部分曲目不提供该属性，此时为 `null`。
     */
    val leadingSilence: Duration? = null,

    /** `<iTunesMetadata><audio/></iTunesMetadata>`，携带空间音频标记与歌词时间偏移。 */
    val audio: LyricsAudio? = null,
) {
    /**
     * 所有段落中的行，按出现顺序摊平。
     *
     * 用 `by lazy` 缓存：这个属性在遍历歌词时会被反复访问，而 `flatMap`
     * 每次调用都会新建列表。缓存不会失效，因为 [LyricsDocument] 与
     * [LyricsSection] 都是不可变的。
     */
    val lines: List<LyricsLine> by lazy { sections.flatMap { it.lines } }

    /** 按 `itunes:key` 查询行；不存在时返回 `null`。 */
    fun lineByKey(key: String): LyricsLine? = lines.firstOrNull { it.key == key }
}
