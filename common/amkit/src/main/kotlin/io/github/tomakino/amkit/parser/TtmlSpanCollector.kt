/*
 * Copyright 2026 Tomakino
 * Licensed under the Apache License, Version 2.0
 * http://www.apache.org/licenses/LICENSE-2.0
 */

package io.github.tomakino.amkit.parser

import io.github.tomakino.amkit.model.LyricsText
import io.github.tomakino.amkit.model.LyricsWord
import org.w3c.dom.Element
import org.w3c.dom.Node

/** `automaticallyCreated`：标记该译文/音译由机器生成。 */
internal const val AUTO_CREATED_ATTRIBUTE = "automaticallyCreated"

/**
 * 沿 `<p>` 或 head 侧 `<text for="...">` 收集内容。
 *
 * 遍历的是**全部子节点**，而不是只遍历元素 —— 这一点很关键：Apple 用裸文本节点
 * 充当词间分隔（`<span>I</span> <span>stay</span>`），只遍历元素会把空格丢掉，
 * 于是 `I stay out too late` 被拼成 `Istayouttoolate`。
 *
 * 背景人声（`x-bg`）组是**嵌套**的，且组内既可能有词、也可能有翻译/音译，
 * 因此递归时只需携带「当前是否位于背景组内」这一个状态：词按该状态归入
 * [words] 或 [background]；而翻译/音译与嵌套层级无关，统一归到行级别
 * —— 它们在语义上都属于这一行。
 */
internal class SpanCollector {
    val words = mutableListOf<LyricsWord>()
    val background = mutableListOf<LyricsWord>()
    val translations = mutableListOf<LyricsText>()
    val transliterations = mutableListOf<LyricsText>()

    fun collect(parent: Element, intoBackground: Boolean = false) {
        val target = if (intoBackground) background else words

        // 按 DOM 顺序遍历：词序由文档顺序决定，不能只挑元素节点。
        // 用下标而不是 for-in —— NodeList 的迭代器扩展在 Kotlin 中存在歧义解析。
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
            // 未知 role 与无 role 的 span 一律按普通词处理，保证向前兼容
            else -> target += span.toWord()
        }
    }
}

/**
 * 把承载文本的元素（行内 `x-translation` / `x-roman` span，或 head 侧
 * `<text for>`）解析成 [LyricsText]。
 *
 * 内部同样走 [SpanCollector]：译文里那些**逐词时间戳**与 `x-bg` 背景组
 * 都会被保留下来。早先直接用 `textContent` 会把它们拍平 —— 文本还在，
 * 但与主歌词逐词对齐的时间轴就没了，逐字双语对照也就做不成了。
 *
 * @param containerLanguage / @param containerType / @param containerAutoCreated
 *   head 侧容器（`<translation>`）上的声明。`<text>` 自身不带这些属性，
 *   因此由容器传入；行内 span 走默认值，从元素自己身上取。
 */
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
        // 两处都要认 —— 实测 Apple 用的是前者
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

/**
 * 把裸文本节点转成词间分隔符；不该保留时返回 `null`。
 *
 * 空白处理规则：
 *  - 连续空白折叠成单个空格，并**去掉首尾空白**，避免文档缩进混进歌词；
 *  - **纯空白且原文含换行**的文本节点视为排版产物直接丢弃 —— Apple 的响应是
 *    紧凑单行，出现换行说明这份 TTML 被人缩进排版过（社区样本很常见），
 *    此时的换行与缩进不是歌词内容。
 *
 * 反过来说，Apple 真实数据里的词间分隔正是「只有一个空格、不含换行」的文本节点
 * （`<span>I</span> <span>stay</span>`），必须原样保留，否则英文歌词会被粘成一团。
 */
internal fun String.toSeparatorWord(): LyricsWord? {
    if (isEmpty()) return null

    // 纯空白且不含换行 —— 这正是 Apple 真实的词间分隔形式（`</span> <span>`）。
    // 绝不能在这里做首尾修剪：那会把整个分隔符折成空串，英文歌词随即粘成一团。
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
