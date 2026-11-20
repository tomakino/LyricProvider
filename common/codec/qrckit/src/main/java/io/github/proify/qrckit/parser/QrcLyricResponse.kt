/*
 * Copyright 2026 Proify, Tomakino
 * Licensed under the Apache License, Version 2.0
 * http://www.apache.org/licenses/LICENSE-2.0
 */

@file:Suppress("SpellCheckingInspection")

package io.github.proify.qrckit.parser

import io.github.proify.qrckit.decrypt.QrcDecrypter
import io.github.proify.qrckit.parser.model.ParsedLyric
import java.util.regex.Pattern

/**
 * QQ 音乐歌词响应。
 * @property musicId 歌曲 ID。
 * @property rawXml 原始响应 XML。
 */
data class QrcLyricResponse(
    val musicId: String,
    val rawXml: String
) {

    /** 解密后的主歌词、翻译与罗马音文本。 */
    val parsedLyric: ParsedLyric by lazy {
        val lyrics = extractCData(rawXml, "content")
        val trans = extractCData(rawXml, "contentts")
        val roma = extractCData(rawXml, "contentroma")

        val decryptedLyrics = QrcDecrypter.decrypt(lyrics)
        val decryptedTrans = QrcDecrypter.decrypt(trans)
        val decryptedRoma = QrcDecrypter.decrypt(roma)

        ParsedLyric(
            decryptedLyrics,
            decryptedTrans,
            decryptedRoma
        )
    }

    /**
     * 提取指定标签的 CDATA 内容。
     * @param xml 响应 XML。
     * @param tagName 标签名。
     * @return 去首尾空白的 CDATA 内容；缺失返回 null。
     */
    private fun extractCData(xml: String, tagName: String): String? {
        try {
            val pattern = "<$tagName[^>]*>.*?<!\\[CDATA\\[(.*?)]]>"
            val regex = Pattern.compile(pattern, Pattern.DOTALL)
            val matcher = regex.matcher(xml)

            return if (matcher.find()) matcher.group(1).trim() else null
        } catch (e: Exception) {
            e.printStackTrace()
            return null
        }
    }
}
