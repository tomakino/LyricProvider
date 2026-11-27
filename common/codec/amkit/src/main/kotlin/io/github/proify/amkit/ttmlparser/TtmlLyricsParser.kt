/*
 * Copyright 2026 Proify, Tomakino
 * Licensed under the Apache License, Version 2.0
 * http://www.apache.org/licenses/LICENSE-2.0
 */

package io.github.proify.amkit.ttmlparser

import io.github.proify.amkit.ttmlparser.model.LyricsDocument
import org.xml.sax.InputSource
import org.xml.sax.SAXException
import java.io.IOException
import java.io.InputStream
import java.io.Reader
import java.io.StringReader

/** UTF-8 BOM。 */
private const val BOM = "\uFEFF"

/** TTML 歌词解析器。 */
class TtmlLyricsParser {

    /** 解析 TTML 文本。 */
    fun parse(ttml: String): LyricsDocument {
        val normalized = ttml.removePrefix(BOM)
        if (normalized.isBlank()) throw LyricsParseException("TTML 文本不能为空")
        return parseInternal(InputSource(StringReader(normalized)))
    }

    /** 解析 TTML 字节流。调用方负责关闭流。 */
    fun parse(inputStream: InputStream): LyricsDocument = parseInternal(InputSource(inputStream))

    /** 解析 TTML 字符流。调用方负责关闭流。 */
    fun parse(reader: Reader): LyricsDocument {
        val normalized = reader.readText().removePrefix(BOM)
        if (normalized.isBlank()) throw LyricsParseException("TTML 内容不能为空")
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
