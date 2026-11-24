/*
 * Copyright 2026 Proify, Tomakino
 * Licensed under the Apache License, Version 2.0
 * http://www.apache.org/licenses/LICENSE-2.0
 */

package io.github.proify.lyricon.qishuikit.model

import io.github.proify.extensions.findClosest
import io.github.proify.lrckit.LrcParser
import io.github.proify.lyricon.lyric.model.LyricLine
import io.github.proify.lyricon.lyric.model.RichLyricLine
import io.github.proify.lyricon.lyric.model.Song
import io.github.proify.lyricon.qishuikit.parser.KrcLyricParser
import kotlinx.serialization.SerialName
import kotlinx.serialization.Serializable
import java.util.Locale

/**
 * 汽水曲目歌词。
 * @property id 曲目 ID。
 * @property name 歌名。
 * @property artist 歌手，多个以 `, ` 连接。
 * @property duration 时长，毫秒。
 * @property type 歌词格式，`krc` 或 `lrc`。
 * @property content 歌词原文。
 * @property langTranslations 带格式标记的翻译。
 * @property translations 纯 LRC 文本的翻译。
 */
@Serializable
data class QishuiLyric(
    val id: String,
    val name: String? = null,
    val artist: String? = null,
    val duration: Long = 0,
    val type: String? = null,
    val content: String? = null,
    @SerialName("lang_translations") val langTranslations: Map<String, QishuiTranslation>? = null,
    val translations: Map<String, String>? = null
) {

    /**
     * 转换为播放器歌词。
     * @return 含元数据与富歌词行的曲目对象；没有可解析的歌词行时为 null。
     */
    fun toSong(): Song? {
        val lines = parseMain() ?: return null
        if (lines.isEmpty()) return null

        val translations = parseTranslation()

        val rich = lines.map { line ->
            RichLyricLine(
                begin = line.begin,
                end = line.end,
                duration = line.duration,
                text = line.text,
                words = line.words,
                translation = translations.findClosest(line.begin, 50)
                    ?.text
                    ?.takeUnless { it.trim() == "//" }
            )
        }

        return Song(
            id = id,
            name = name.orEmpty(),
            artist = artist.orEmpty(),
            duration = duration,
            lyrics = rich
        )
    }

    /**
     * 解析主歌词。
     * @return 主歌词行；格式未知或没有有效行时为 null。
     */
    private fun parseMain(): List<LyricLine>? = when (type?.lowercase()) {
        "lrc" -> LrcParser.parse(content, duration).lines
        else -> KrcLyricParser.parse(content)
    }

    /**
     * 解析翻译歌词。
     * @return 翻译行；没有可用翻译时为空列表。
     */
    private fun parseTranslation(): List<LyricLine> {
        pickTranslation()?.let { translation ->
            val text = translation.content?.takeIf { it.isNotBlank() } ?: return@let
            val lines = when (translation.type?.lowercase()) {
                "krc" -> KrcLyricParser.parse(text)
                "lrc" -> LrcParser.parse(text, duration).lines
                else -> emptyList()
            }
            if (lines.isNotEmpty()) return lines
        }

        val plainText = pickPlainTranslation() ?: return emptyList()
        return LrcParser.parse(plainText, duration).lines
    }

    /**
     * 选择纯文本翻译。
     * @return 与当前系统语言匹配的翻译文本；没有可用项时为 null。
     */
    private fun pickPlainTranslation(): String? {
        val map = translations?.filterValues { it.isNotBlank() } ?: return null
        if (map.isEmpty()) return null
        if (map.size == 1) return map.values.first()

        val preferred = Locale.getDefault().language

        return map.entries
            .firstOrNull { normalizeLanguage(it.key) == preferred }
            ?.value
            ?: map.values.first()
    }

    /**
     * 把语言标签归一化为语言代码。
     * @param key 原始语言标签。
     * @return 归一化后的语言代码。
     */
    private fun normalizeLanguage(key: String): String {
        val base = key.lowercase().substringBefore('-')
        return when (base) {
            "cn", "zh" -> "zh"
            "jp" -> "ja"
            "kr" -> "ko"
            else -> base
        }
    }

    /**
     * 选择带格式标记的翻译。
     * @return 与当前系统语言匹配的翻译；没有可用项时为 null。
     */
    private fun pickTranslation(): QishuiTranslation? {
        val translations = langTranslations ?: return null
        val key = pickLanguageKey(translations.keys) ?: return null
        return translations[key]
    }

    /**
     * 按系统语言选择语言标签。
     * @param keys 可用语言标签。
     * @return 匹配的语言标签；没有匹配项时为 null。
     */
    private fun pickLanguageKey(keys: Set<String>): String? {
        if (keys.isEmpty()) return null
        if (keys.size == 1) return keys.first()

        val locale = Locale.getDefault()
        val language = locale.language

        keys.firstOrNull { it.equals(language, ignoreCase = true) }?.let { return it }

        val systemTag = buildString {
            append(language.uppercase())
            if (locale.script.isNotEmpty()) append("-").append(locale.script.uppercase())
            if (locale.country.isNotEmpty()) append("-").append(locale.country.uppercase())
        }
        keys.firstOrNull { it.equals(systemTag, ignoreCase = true) }?.let { return it }

        if (language == "zh") {
            val country = locale.country.uppercase()
            keys.firstOrNull { it.equals("ZH-HANS-$country", ignoreCase = true) }?.let { return it }
            keys.firstOrNull { it.equals("ZH-HANT-$country", ignoreCase = true) }?.let { return it }
        }

        return keys.firstOrNull { it.startsWith(language, ignoreCase = true) }
    }
}
