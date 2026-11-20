/*
 * Copyright 2026 Proify, Tomakino
 * Licensed under the Apache License, Version 2.0
 * http://www.apache.org/licenses/LICENSE-2.0
 */

package io.github.proify.amkit.locale

import java.util.Locale

/**
 * 按系统区域推导歌词本地化参数。
 *
 * 覆盖三类区域：显式声明书写系统的、中文里按地区区分简繁的、以及直接以地区标识语言的。
 * 中文的简繁判定只认 CN / TW / HK / MO；其余中文地区在 [languageTag] 中回退到地区形式，
 * 在 [scriptTag] 中按简体处理。
 */
object SystemLyricsLocale {

    /**
     * 译文语言。
     *
     * 书写系统可确定时输出 `language-script`，否则输出 `language-region`。
     *
     * @param locale 区域，默认取 [Locale.getDefault]
     * @return 语言标签
     */
    fun languageTag(locale: Locale = Locale.getDefault()): String {
        val script = locale.script.ifBlank { chineseScript(locale) }
        return if (script.isNotBlank()) "${locale.language}-$script" else "${locale.language}-${locale.country}"
    }

    /**
     * 书写系统。
     *
     * @param locale 区域，默认取 [Locale.getDefault]
     * @return 逗号分隔的 `language-script` 列表，无信息时为空字符串
     */
    fun scriptTag(locale: Locale = Locale.getDefault()): String =
        systemScripts(locale).joinToString(separator = ",") { "${locale.language}-$it" }

    /** 系统书写系统，日语额外前置拉丁字母。 */
    private fun systemScripts(locale: Locale): List<String> {
        val script = locale.script.takeIf { it.isNotBlank() } ?: scriptOf(locale) ?: return emptyList()
        return if (locale == Locale.JAPAN) listOf(LATN, script) else listOf(script)
    }

    private fun scriptOf(locale: Locale): String? = when (locale.language) {
        CHINESE, CANTONESE -> if (locale.country in TRADITIONAL_REGIONS) TRADITIONAL_HAN else SIMPLIFIED_HAN
        else -> SCRIPTS[locale.language] ?: LATN
    }

    /** 中文书写系统，只识别 CN / TW / HK / MO。 */
    private fun chineseScript(locale: Locale): String = when {
        locale == Locale.SIMPLIFIED_CHINESE -> SIMPLIFIED_HAN
        locale == Locale.TRADITIONAL_CHINESE -> TRADITIONAL_HAN
        locale.country == "HK" -> TRADITIONAL_HAN
        locale.country == "MO" -> TRADITIONAL_HAN
        else -> ""
    }

    private const val CHINESE = "zh"
    private const val CANTONESE = "yue"
    private const val SIMPLIFIED_HAN = "Hans"
    private const val TRADITIONAL_HAN = "Hant"
    private const val LATN = "Latn"

    private val TRADITIONAL_REGIONS = setOf("TW", "HK", "MO")

    /** 语言到书写系统的对照，未列出的语言按拉丁字母处理。 */
    private val SCRIPTS: Map<String, String> = mapOf(
        "ja" to "Jpan",
        "ko" to "Kore",
        "ru" to "Cyrl",
        "uk" to "Cyrl",
        "be" to "Cyrl",
        "bg" to "Cyrl",
        "sr" to "Cyrl",
        "mk" to "Cyrl",
        "kk" to "Cyrl",
        "mn" to "Cyrl",
        "ky" to "Cyrl",
        "tg" to "Cyrl",
        "tt" to "Cyrl",
        "ba" to "Cyrl",
        "ar" to "Arab",
        "fa" to "Arab",
        "ur" to "Arab",
        "ps" to "Arab",
        "ku" to "Arab",
        "sd" to "Arab",
        "ug" to "Arab",
        "he" to "Hebr",
        "th" to "Thai",
        "hi" to "Deva",
        "mr" to "Deva",
        "ne" to "Deva",
        "el" to "Grek",
        "ka" to "Geor",
        "hy" to "Armn",
        "bo" to "Tibt",
        "am" to "Ethi",
        "ti" to "Ethi",
        "dv" to "Thaa",
        "bn" to "Beng",
        "gu" to "Gujr",
        "kn" to "Knda",
        "ml" to "Mlym",
        "pa" to "Guru",
        "si" to "Sinh",
        "ta" to "Taml",
        "te" to "Telu",
        "my" to "Mymr",
        "km" to "Khmr",
        "lo" to "Laoo",
    )
}
