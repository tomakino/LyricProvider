/*
 * Copyright 2026 Tomakino
 * Licensed under the Apache License, Version 2.0
 * http://www.apache.org/licenses/LICENSE-2.0
 */

package io.github.tomakino.amkit.parser

import io.github.tomakino.amkit.parser.model.LyricsText
import io.github.tomakino.amkit.parser.model.LyricsWord
import org.w3c.dom.Element
import org.w3c.dom.Node

/** `automaticallyCreated`：标记该译文/音译由机器生成。 */
internal const val AUTO_CREATED_ATTRIBUTE = "automaticallyCreated"

/** 收集词、背景人声、译文与音译。 */
internal class SpanCollector {
    val words = mutableListOf<LyricsWord>()
    val background = mutableListOf<LyricsWord>()
    val translations = mutableListOf<LyricsText>()
    val transliterations = mutableListOf<LyricsText>()

    fun collect(parent: Element, intoBackground: Boolean = false) {
        val target = if (intoBackground) background else words

        // 必须遍历全部子节点（裸文本节点是词间分隔符）；用下标而非 for-in，
        // 因为 NodeList 的迭代器扩展在 Kotlin 中存在歧义解析。
        val nodes = parent.childNodes
        for (index in 0 until nodes.length) {
            val node = nodes.item(index) ?: continue
            when (node.nodeType) {
                Node.ELEMENT_NODE -> collectElement(node as Element, intoBackground)
                Node.TEXT_NODE, Node.CDATA_SECTION_NODE ->
                    node.nodeValue?.toSeparatorWord()?.let(target::add)
                // 注释、处理指令等其它节点与歌词内容无关，直接忽略
                else -> Unit
            }
        }
    }

    private fun collectElement(span: Element, intoBackground: Boolean) {
        if (span.namespaceURI != TtmlNamespaces.TTML || span.localName != "span") return

        val target = if (intoBackground) background else words
        when (span.nsAttribute(TtmlNamespaces.TTM, "role")) {
            TtmlRoles.BACKGROUND -> collect(span, intoBackground = true)
            TtmlRoles.TRANSLATION -> translations += span.toLyricsText()
            TtmlRoles.ROMAN -> transliterations += span.toLyricsText()
            // 未知 role 与无 role 的 span 一律按普通词处理，保证向前兼容；
            // 空 span 不产生词，否则会在词序列里留下空片段
            else -> {
                val word = span.toWord()
                if (word.text.isNotEmpty()) target += word
            }
        }
    }
}

/** 把元素解析成 [LyricsText]。 */
internal fun Element.toLyricsText(
    containerLanguage: String? = null,
    containerType: String? = null,
    containerAutoCreated: Boolean = false,
): LyricsText {
    val collector = SpanCollector()
    collector.collect(this)

    // 裁剪只做一次：它会新建列表，重复调用纯属浪费
    val words = collector.words.trimmedSeparators()

    return LyricsText(
        text = words.joinToString(separator = "") { it.text },
        language = containerLanguage ?: nsAttribute(TtmlNamespaces.XML, "lang"),
        type = containerType ?: plainAttribute("type"),
        // 该属性既可能标在 head 侧容器（<translation>）上，也可能标在行内条目上，
        // 两处都要认（Apple 使用前者）
        autoCreated = containerAutoCreated || plainAttribute(AUTO_CREATED_ATTRIBUTE) == "true",
        words = words,
        background = collector.background.trimmedSeparators(),
    )
}

internal fun Element.toWord(): LyricsWord = LyricsWord(
    text = textContent.orEmpty(),
    start = TtmlTimeCode.parseOrNull(plainAttribute("begin")),
    end = TtmlTimeCode.parseOrNull(plainAttribute("end")),
)

// ---------------------------------------------------------------------------
// 裸文本节点与分隔符
// ---------------------------------------------------------------------------

/** 把裸文本节点转成词间分隔符。 */
internal fun String.toSeparatorWord(): LyricsWord? {
    if (isEmpty()) return null

    // 纯空白且不含换行 —— 这正是 Apple 真实的词间分隔形式（`</span> <span>`）。
    // 绝不能在这里做首尾修剪：那会把整个分隔符折成空串，相邻词随即粘连。
    // 行首行尾的分隔符由词序列级别的 trimmedSeparators() 负责丢弃。
    if (isBlank()) {
        return if (contains('\n') || contains('\r')) null else LyricsWord(text = " ")
    }

    return LyricsWord(text = collapseWhitespace())
}

/** 把连续空白折叠成单个空格，并去掉首尾空白（等价于 XML 的空白归一化）。 */
internal fun String.collapseWhitespace(): String {
    val builder = StringBuilder(length)
    var pendingSpace = false

    for (character in this) {
        if (character.isWhitespace()) {
            // builder 为空时说明还处在行首：行首空白不构成内容
            pendingSpace = builder.isNotEmpty()
            continue
        }
        if (pendingSpace) builder.append(' ')
        pendingSpace = false
        builder.append(character)
    }
    return builder.toString()
}

/** 纯空白、无时间的词只是分隔符，不构成内容。 */
internal fun LyricsWord.isSeparator(): Boolean =
    text.isBlank() && start == null && end == null

/** 丢弃词序列首尾的分隔符（行首行尾的空格不属于歌词）。 */
internal fun List<LyricsWord>.trimmedSeparators(): List<LyricsWord> {
    var from = 0
    var to = size
    while (from < to && this[from].isSeparator()) from++
    while (to > from && this[to - 1].isSeparator()) to--
    return if (from == 0 && to == size) this else subList(from, to).toList()
}
