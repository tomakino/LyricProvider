/*
 * Copyright 2026 Proify, Tomakino
 * Licensed under the Apache License, Version 2.0
 * http://www.apache.org/licenses/LICENSE-2.0
 */

package io.github.tomakino.amkit.parser

import io.github.tomakino.amkit.LyricsParseException
import io.github.tomakino.amkit.model.LyricsAgent
import io.github.tomakino.amkit.model.LyricsAudio
import io.github.tomakino.amkit.model.LyricsDocument
import io.github.tomakino.amkit.model.LyricsLine
import io.github.tomakino.amkit.model.LyricsMetadata
import io.github.tomakino.amkit.model.LyricsName
import io.github.tomakino.amkit.model.LyricsSection
import io.github.tomakino.amkit.model.LyricsText
import io.github.tomakino.amkit.model.LyricsTiming
import io.github.tomakino.amkit.model.LyricsWord
import org.w3c.dom.Document
import org.w3c.dom.Element
import kotlin.time.Duration

/**
 * 把 DOM 读成领域模型。
 *
 * 这一层只做「DOM → [LyricsDocument]」的搬运，不含任何 XML 解析或安全配置
 * （那是 [TtmlLyricsParser] 与 [SecureXmlFactory] 的事），也不含词的收集规则
 * （那是 [SpanCollector] 的事）。
 *
 * 所有函数都是 `internal`：库外只能通过 [TtmlLyricsParser] 拿到结果。
 */

internal fun Document.toLyricsDocument(): LyricsDocument {
    val root = documentElement ?: throw LyricsParseException("TTML 缺少根元素")

    val metadataElement = root.firstChildElement(TtmlNamespaces.TTML, "head")
        ?.firstChildElement(TtmlNamespaces.TTML, "metadata")
    val bodyElement = root.firstChildElement(TtmlNamespaces.TTML, "body")

    val parsed = LyricsDocument(
        language = root.nsAttribute(TtmlNamespaces.XML, "lang"),
        timing = LyricsTiming.fromAttribute(root.nsAttribute(TtmlNamespaces.ITUNES, "timing")),
        metadata = metadataElement.toMetadata(),
        agents = metadataElement?.childElements(TtmlNamespaces.TTM, "agent")
            ?.map { it.toAgent() }
            .orEmpty(),
        sections = bodyElement?.childElements(TtmlNamespaces.TTML, "div")
            ?.map { it.toSection() }
            .orEmpty(),
        duration = TtmlTimeCode.parseOrNull(bodyElement?.plainAttribute("dur")),
        leadingSilence = metadataElement.toLeadingSilence(),
        audio = metadataElement.toAudio(),
    )

    val (translations, transliterations) = metadataElement.toSidecars()
    return parsed.mergeSidecars(translations, transliterations)
}

// ---------------------------------------------------------------------------
// Body 侧：段落与行
// ---------------------------------------------------------------------------

internal fun Element.toSection(): LyricsSection {
    // Apple 同时存在 itunes:song-part 与早期驼峰写法 itunes:songPart，两者都要认
    val songPart = nsAttribute(TtmlNamespaces.ITUNES, "song-part")
        ?: nsAttribute(TtmlNamespaces.ITUNES, "songPart")

    return LyricsSection(
        songPart = songPart,
        // 段落自带 begin/end：实测 Apple 的每个 <div> 都带，用于段落级跳转与高亮
        start = TtmlTimeCode.parseOrNull(plainAttribute("begin")),
        end = TtmlTimeCode.parseOrNull(plainAttribute("end")),
        lines = childElements(TtmlNamespaces.TTML, "p").map { it.toLine() },
    )
}

internal fun Element.toLine(): LyricsLine {
    val start = TtmlTimeCode.parseOrNull(plainAttribute("begin"))
    val end = TtmlTimeCode.parseOrNull(plainAttribute("end"))

    val collector = SpanCollector()
    collector.collect(this)

    return LyricsLine(
        key = nsAttribute(TtmlNamespaces.ITUNES, "key"),
        agentId = nsAttribute(TtmlNamespaces.TTM, "agent"),
        start = start,
        end = end,
        // 先去掉首尾分隔空格，再补行时间 —— 补完时间后分隔符就无法与实词区分了
        words = collector.words.trimmedSeparators().withLineTimeWhenTimeless(start, end),
        background = collector.background.trimmedSeparators(),
        translations = collector.translations,
        transliterations = collector.transliterations,
    )
}

/**
 * 逐行模式（`itunes:timing="Line"`）的 `<p>` 里没有任何带时间戳的 `<span>`，
 * 整行就是一段裸文本，词级时间自然也不存在。
 *
 * 此时把行时间补到词上，调用方（滚动、逐行高亮）就不必自己回落；
 * 反过来，只要行内出现过任意一个带时间的词，就说明词级时间由文档提供，这里不动。
 */
internal fun List<LyricsWord>.withLineTimeWhenTimeless(
    lineStart: Duration?,
    lineEnd: Duration?,
): List<LyricsWord> {
    if (isEmpty() || (lineStart == null && lineEnd == null)) return this
    if (any { it.start != null || it.end != null }) return this
    return map { it.copy(start = lineStart, end = lineEnd) }
}

// ---------------------------------------------------------------------------
// Head 侧：元数据、演唱者、音频段
// ---------------------------------------------------------------------------

internal fun Element?.toMetadata(): LyricsMetadata {
    if (this == null) return LyricsMetadata()

    // amll:meta 同一个 key 可以出现多次（多语言 / 多平台变体），因此按 key 分组
    val meta = childElements(TtmlNamespaces.AMLL, "meta")
        .mapNotNull { element ->
            val key = element.plainAttribute("key") ?: return@mapNotNull null
            val value = element.plainAttribute("value") ?: return@mapNotNull null
            key to value
        }
        .groupBy({ it.first }, { it.second })

    val songwriters = childElements(TtmlNamespaces.ITUNES, "iTunesMetadata")
        .flatMap { it.childElements(TtmlNamespaces.ITUNES, "songwriters") }
        .flatMap { it.childElements(TtmlNamespaces.ITUNES, "songwriter") }
        .map { it.textContent.orEmpty() }

    return LyricsMetadata(
        title = childElements(TtmlNamespaces.TTM, "title").firstOrNull()?.textContent,
        language = nsAttribute(TtmlNamespaces.XML, "lang"),
        meta = meta,
        songwriters = songwriters,
    )
}

internal fun Element.toAgent(): LyricsAgent = LyricsAgent(
    id = nsAttribute(TtmlNamespaces.XML, "id").orEmpty(),
    type = plainAttribute("type"),
    // type 必须一起取：full / family / given / alias 是不同性质的姓名
    names = childElements(TtmlNamespaces.TTM, "name").map {
        LyricsName(text = it.textContent.orEmpty(), type = it.plainAttribute("type"))
    },
)

/**
 * `iTunesMetadata@leadingSilence`：首句歌词之前的静音时长。
 *
 * Apple 用它表达前奏留白（实测 `0.060` / `0.140`），播放器据此决定何时开始
 * 滚动歌词。多数曲目不提供该属性，缺失时返回 `null`。
 */
internal fun Element?.toLeadingSilence(): Duration? =
    this?.childElements(TtmlNamespaces.ITUNES, "iTunesMetadata")
        ?.firstOrNull()
        ?.plainAttribute("leadingSilence")
        ?.let { TtmlTimeCode.parseOrNull(it) }

/**
 * `<iTunesMetadata><audio role="spatial" lyricOffset="0.045"/></iTunesMetadata>`。
 *
 * 实测 43/385 首出现，每份文档至多一个。`lyricOffset` 是**歌词时间的校准偏移**
 * （取值 -0.511 ~ 1.164 秒，有正有负），播放器可用它把歌词与音频对齐；
 * `role` 目前只观测到 `spatial`。
 */
internal fun Element?.toAudio(): LyricsAudio? =
    this?.childElements(TtmlNamespaces.ITUNES, "iTunesMetadata")
        ?.firstOrNull()
        ?.childElements(TtmlNamespaces.ITUNES, "audio")
        ?.firstOrNull()
        ?.let { element ->
            LyricsAudio(
                role = element.plainAttribute("role"),
                lyricOffset = TtmlTimeCode.parseOrNull(element.plainAttribute("lyricOffset")),
            )
        }

// ---------------------------------------------------------------------------
// Head 侧 Sidecar 与合并
// ---------------------------------------------------------------------------

internal fun Element?.toSidecars(): Pair<List<LyricsTranslationSidecar>, List<LyricsTranslationSidecar>> {
    if (this == null) return emptyList<LyricsTranslationSidecar>() to emptyList()

    val iTunesMetadata = childElements(TtmlNamespaces.ITUNES, "iTunesMetadata")
    return iTunesMetadata.collectSidecars("translations", "translation") to
            iTunesMetadata.collectSidecars("transliterations", "transliteration")
}

internal fun List<Element>.collectSidecars(
    containerName: String,
    itemName: String,
): List<LyricsTranslationSidecar> =
    flatMap { it.childElements(TtmlNamespaces.ITUNES, containerName) }
        .flatMap { it.childElements(TtmlNamespaces.ITUNES, itemName) }
        .map { container ->
            // xml:lang / type / automaticallyCreated 都标注在容器（<translation>）上，
            // 条目（<text>）自身没有这些属性
            val language = container.nsAttribute(TtmlNamespaces.XML, "lang")
            val type = container.plainAttribute("type")
            val autoCreated = container.plainAttribute(AUTO_CREATED_ATTRIBUTE) == "true"

            LyricsTranslationSidecar(
                entries = container.childElements(TtmlNamespaces.ITUNES, "text")
                    .mapNotNull { text ->
                        text.plainAttribute("for")?.let { key ->
                            key to text.toLyricsText(
                                containerLanguage = language,
                                containerType = type,
                                containerAutoCreated = autoCreated,
                            )
                        }
                    }
                    .toMap(),
            )
        }

internal fun LyricsDocument.mergeSidecars(
    translations: List<LyricsTranslationSidecar>,
    transliterations: List<LyricsTranslationSidecar>,
): LyricsDocument {
    if (translations.isEmpty() && transliterations.isEmpty()) return this

    val mergedSections = sections.map { section ->
        section.copy(
            lines = section.lines.map { line ->
                line.copy(
                    translations = line.translations.mergeWith(translations, line.key),
                    transliterations = line.transliterations.mergeWith(transliterations, line.key),
                )
            },
        )
    }
    return copy(sections = mergedSections)
}

/**
 * 把 Head 侧条目并入行内条目。
 *
 * 冲突时**行内优先**：同一语言或同一文本的 Sidecar 条目会被丢弃，
 * 避免同一行出现两条内容相同、来源不同的翻译。
 */
internal fun List<LyricsText>.mergeWith(
    sidecars: List<LyricsTranslationSidecar>,
    lineKey: String?,
): List<LyricsText> {
    if (lineKey == null) return this

    val fromHead = sidecars.mapNotNull { it.entries[lineKey] }
    if (fromHead.isEmpty()) return this

    val inlineLanguages = mapNotNull { it.language }.toSet()
    val inlineTexts = map { it.text }.toSet()

    val deduplicated = fromHead.filterNot { candidate ->
        candidate.text in inlineTexts ||
                (candidate.language != null && candidate.language in inlineLanguages)
    }
    return this + deduplicated
}
