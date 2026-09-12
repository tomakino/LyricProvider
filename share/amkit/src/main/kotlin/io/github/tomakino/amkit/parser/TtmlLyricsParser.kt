/*
 * Copyright 2026 Tomakino
 * Licensed under the Apache License, Version 2.0
 * http://www.apache.org/licenses/LICENSE-2.0
 */

package io.github.tomakino.amkit.parser

import io.github.tomakino.amkit.LyricsParseException
import io.github.tomakino.amkit.model.LyricsDocument
import org.xml.sax.InputSource
import org.xml.sax.SAXException
import java.io.IOException
import java.io.InputStream
import java.io.Reader
import java.io.StringReader

/** UTF-8 BOM。解析前必须剥离，否则 XML 解析器会报「前言中不允许有内容」。 */
private const val BOM = "\uFEFF"

/**
 * Apple 风格 TTML 歌词解析器。
 *
 * 本类**无状态且线程安全**，可以安全地作为单例复用；每次解析都会新建独立的
 * [javax.xml.parsers.DocumentBuilder]，不共享任何可变状态。
 *
 * 容错策略：
 *  - 结构性错误（参数为空、XML 非法、缺少根元素）→ 抛 [LyricsParseException]
 *  - 局部脏数据（时间码写错、未知 `ttm:role`）→ 降级处理，不中断整份文档
 *
 * 用法：
 * ```kotlin
 * val document = TtmlLyricsParser().parse(ttmlText)
 * println(document.lines.first().text)
 * ```
 *
 * 实现按职责拆在几个文件里，本类只保留入口：
 *  - [SecureXmlFactory] —— 带 XXE 加固的 `DocumentBuilder`
 *  - [TtmlDocumentReader] —— DOM 到领域模型的搬运
 *  - [SpanCollector] —— `<p>` / `<text for>` 子树的内容收集与空白规则
 *  - [TtmlDom] —— DOM 访问辅助
 */
class TtmlLyricsParser {

    /** 解析 TTML 文本。会先剥离可能存在的 UTF-8 BOM。 */
    fun parse(ttml: String): LyricsDocument {
        val normalized = ttml.removePrefix(BOM)
        require(normalized.isNotBlank()) { "TTML 文本不能为空" }
        return parseInternal(InputSource(StringReader(normalized)))
    }

    /**
     * 解析 TTML 字节流。**调用方负责关闭流**。
     *
     * 字节流无需手动剥离 BOM —— XML 解析器能自行识别 UTF-8 / UTF-16 的字节序标记。
     */
    fun parse(inputStream: InputStream): LyricsDocument = parseInternal(InputSource(inputStream))

    /**
     * 解析 TTML 字符流。**调用方负责关闭流**。
     *
     * 字符流中的 BOM 已被解码成 `U+FEFF`，解析器会把它当成「前言内容」直接报错，
     * 所以必须先读入全文再剥离。歌词文件通常只有几十 KB，代价可接受；
     * 若真要处理大文件，请改用 [parse] 的字节流重载以保持流式。
     */
    fun parse(reader: Reader): LyricsDocument {
        val normalized = reader.readText().removePrefix(BOM)
        require(normalized.isNotBlank()) { "TTML 内容不能为空" }
        return parseInternal(InputSource(StringReader(normalized)))
    }

    private fun parseInternal(source: InputSource): LyricsDocument {
        val document = try {
            SecureXmlFactory.newDocumentBuilder().parse(source)
        } catch (e: SAXException) {
            throw LyricsParseException("TTML 语法错误: ${e.message}", e)
        } catch (e: IOException) {
            throw LyricsParseException("TTML 读取失败: ${e.message}", e)
        }
        return document.toLyricsDocument()
    }
}
