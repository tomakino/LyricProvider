/*
 * Copyright 2026 Proify, Tomakino
 * Licensed under the Apache License, Version 2.0
 * http://www.apache.org/licenses/LICENSE-2.0
 */

package io.github.proify.amkit.parser

import io.github.proify.amkit.parser.model.LyricsAgent
import io.github.proify.amkit.parser.model.LyricsAudio
import io.github.proify.amkit.parser.model.LyricsDocument
import io.github.proify.amkit.parser.model.LyricsLine
import io.github.proify.amkit.parser.model.LyricsMetadata
import io.github.proify.amkit.parser.model.LyricsName
import io.github.proify.amkit.parser.model.LyricsSection
import io.github.proify.amkit.parser.model.LyricsText
import io.github.proify.amkit.parser.model.LyricsTiming
import org.w3c.dom.Document
import org.w3c.dom.Element
import kotlin.time.Duration

/** 把 DOM 读成 [LyricsDocument]。 */
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
        words = collector.words.trimmedSeparators(),
        background = collector.background.trimmedSeparators(),
        translations = collector.translations,
        transliterations = collector.transliterations,
    )
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

/** 首句歌词前的静音时长。 */
internal fun Element?.toLeadingSilence(): Duration? =
    this?.childElements(TtmlNamespaces.ITUNES, "iTunesMetadata")
        ?.firstOrNull()
        ?.plainAttribute("leadingSilence")
        ?.let { TtmlTimeCode.parseOrNull(it) }

/** 音频信息。 */
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

/** 把 head 侧条目并入行内条目，冲突时行内优先。 */
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
