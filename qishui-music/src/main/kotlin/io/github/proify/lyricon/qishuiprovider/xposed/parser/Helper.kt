package io.github.proify.lyricon.qishuiprovider.xposed.parser

import io.github.proify.extensions.findClosest
import io.github.proify.lrckit.LrcParser
import io.github.proify.lyricon.lyric.model.LyricLine
import io.github.proify.lyricon.lyric.model.RichLyricLine
import io.github.proify.lyricon.lyric.model.extensions.normalize
import java.util.Locale

fun NetResponseCache.toRichLyric(): List<RichLyricLine> {
    val lines = parserTypeLyric(lyric?.type, lyric?.content)?.normalize()
    if (lines.isNullOrEmpty()) return emptyList()

    val langKey = lyric?.lang_translations?.keys?.let { getLangKeyForTranslations(it) }
    val translation = lyric?.lang_translations?.get(langKey.orEmpty())
    val translationLines = parserTypeLyric(translation?.type, translation?.content)?.normalize()

    return lines.map { line ->
        val translation = translationLines?.findClosest(line.begin, 50)?.text

        RichLyricLine(
            begin = line.begin,
            end = line.end,
            duration = line.duration,
            text = line.text,
            words = line.words,
            translation = translationLines?.findClosest(line.begin, 50)?.text
        )
    }
}

private fun parserTypeLyric(type: String?, lyric: String?): List<LyricLine>? {
    if (type.isNullOrBlank() || lyric.isNullOrBlank()) return null
    return when (type.lowercase()) {
        "krc" -> KtvLyricParser.parse(lyric)
        "lrc" -> LrcParser.parse(lyric).lines
        else -> null
    }
}

/**
 * 根据系统语言匹配 lang_translations 中的 key
 */
private fun getLangKeyForTranslations(availableKeys: Set<String>): String? {
    val locale = Locale.getDefault()
    val systemTag = buildString {
        append(locale.language.uppercase())
        if (locale.script.isNotEmpty()) append("-${locale.script.uppercase()}")
        if (locale.country.isNotEmpty()) append("-${locale.country.uppercase()}")
    }

    // 精确匹配
    availableKeys.firstOrNull { it.equals(systemTag, ignoreCase = true) }?.let { return it }

    // 中文特殊处理：简体 Hans / 繁体 Hant
    if (locale.language == "zh") {
        val fallbackHans = "ZH-HANS-${locale.country.uppercase()}"
        availableKeys.firstOrNull { it.equals(fallbackHans, ignoreCase = true) }?.let { return it }

        val fallbackHant = "ZH-HANT-${locale.country.uppercase()}"
        availableKeys.firstOrNull { it.equals(fallbackHant, ignoreCase = true) }?.let { return it }
    }

    // 模糊匹配语言部分
    return availableKeys.firstOrNull { it.startsWith(locale.language, ignoreCase = true) }
}