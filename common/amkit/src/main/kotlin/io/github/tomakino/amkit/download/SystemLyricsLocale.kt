/*
 * Copyright 2026 Proify, Tomakino
 * Licensed under the Apache License, Version 2.0
 * http://www.apache.org/licenses/LICENSE-2.0
 */

package io.github.tomakino.amkit.download

import io.github.tomakino.amkit.download.SystemLyricsLocale.LATIN_SCRIPT
import java.util.Locale

/**
 * 按**运行环境的系统区域**推导歌词接口的两个本地化参数。
 *
 * Apple 的歌词端点用它们描述使用方的语言环境：
 *  - `l[lyrics]` —— 要哪种语言的歌词本地化内容（译文）
 *  - `l[script]` —— 内容用哪种书写系统呈现；**非原生脚本会产生转写**
 *    （如日文传 `ja-Latn` 会额外得到罗马音音译，传 `ja-Jpan` 则只是原文）
 *
 * 这两个值此前在 [AppleMusicLyricsDownloader] 里硬编码为 `zh-Hans` / `zh-Hani`，
 * 那只是抓包环境的语言 —— 换成英文或日文区的调用方拿到的就是错的本地化。
 * 现在默认从 [Locale.getDefault] 推导，Android 上即用户设置的系统语言。
 *
 * 取值依据（`l[script]` 一列均在真实接口上实测过）：
 *
 * | 系统区域 | `l[lyrics]` | `l[script]` | 实测结果 |
 * |---|---|---|---|
 * | `zh-CN` | `zh-Hans` | `zh-Hani` | 繁体原文 + 简体改写译文 |
 * | `zh-TW` | `zh-Hant` | `zh-Hani` | 原文即繁体，同上 |
 * | `en-US` | `en` | `en-Latn` | 英文原文，无转写 |
 * | `ja-JP` | `ja` | `ja-Jpan` | 日文原文（汉字 + 假名），无转写 |
 * | 想要罗马音时 | `ja` | `ja-Latn` | 日文原文 + 罗马音音译 |
 *
 * 两个参数的方向刻意不同：
 *  - `l[lyrics]` 侧，中文**必须带 script**（`zh` 本身无法区分简繁），其余语言只给语言子标签；
 *  - `l[script]` 侧，中文统一用 `Hani`（汉字）—— `Hans` / `Hant` 是简繁**变体**，
 *    当作「书写系统」传入语义不符。
 */
object SystemLyricsLocale {

    /**
     * `l[lyrics]`：歌词本地化语言，如 `zh-Hans`、`zh-Hant`、`en`、`ja`。
     *
     * @param locale 默认取 [Locale.getDefault]，测试可显式传入。
     */
    fun languageTag(locale: Locale = Locale.getDefault()): String {
        val (language, script) = resolve(locale)
        return if (language == CHINESE) "$CHINESE-$script" else language
    }

    /**
     * `l[script]`：内容书写系统，如 `zh-Hani`、`en-Latn`、`ja-Jpan`。
     *
     * @param locale 默认取 [Locale.getDefault]，测试可显式传入。
     */
    fun scriptTag(locale: Locale = Locale.getDefault()): String {
        val (language, script) = resolve(locale)
        val resolvedScript = if (language == CHINESE) HAN_SCRIPT else script
        return "$language-$resolvedScript"
    }

    /**
     * 推导「语言 + 书写系统」。
     *
     * 本想靠 `Locale.Builder.setLocale` 的 likely-subtags 补全免掉映射表，但
     * **标准 JDK 没有公开的 likely-subtags API**（`addLikelySubtags` 只在 Android 的
     * ICU 上提供），实测它既不会为 `zh-TW` 补出 `Hant`，也不会为 `ja-JP` 补出 `Jpan`。
     * 所以这里维护一张显式表，只覆盖歌词场景会遇到的书写系统。
     */
    private fun resolve(locale: Locale): Pair<String, String> {
        val language = locale.language.ifEmpty { FALLBACK_LANGUAGE }
        val script = locale.script.ifEmpty {
            if (language == CHINESE) chineseScript(locale) else nativeScript(language)
        }
        return language to script
    }

    /** 中文的简繁：显式 script 优先，其次看区域（台港澳为繁体），最后回落简体。 */
    private fun chineseScript(locale: Locale): String = when {
        locale.script.equals(TRADITIONAL_HAN, ignoreCase = true) -> TRADITIONAL_HAN
        locale.script.equals(SIMPLIFIED_HAN, ignoreCase = true) -> SIMPLIFIED_HAN
        locale.country.uppercase() in TRADITIONAL_REGIONS -> TRADITIONAL_HAN
        else -> SIMPLIFIED_HAN
    }

    private fun nativeScript(language: String): String = NATIVE_SCRIPTS[language] ?: LATIN_SCRIPT

    /** 台湾 / 香港 / 澳门使用繁体。 */
    private val TRADITIONAL_REGIONS = setOf("TW", "HK", "MO")

    /**
     * 语言的**原生书写系统**（ISO 15924）。
     *
     * 只收录歌词场景会出现的语言，未收录的一律按 [LATIN_SCRIPT] 处理。
     * 这个回落方向很重要：拉丁字母覆盖面最广，而且不会像把日文错推成
     * `ja-Latn` 那样，**悄悄给日文用户塞上一整套罗马音音译**。
     */
    private val NATIVE_SCRIPTS = mapOf(
        // 已实测：zh-Hani 与 ja-Jpan 都只返回原文，不产生任何转写
        CHINESE to HAN_SCRIPT,
        "ja" to "Jpan",
        "ko" to "Kore",
        // 西里尔
        "ru" to "Cyrl", "uk" to "Cyrl", "bg" to "Cyrl", "sr" to "Cyrl",
        "mk" to "Cyrl", "be" to "Cyrl", "kk" to "Cyrl", "mn" to "Cyrl",
        // 阿拉伯 / 希伯来
        "ar" to "Arab", "fa" to "Arab", "ur" to "Arab", "ps" to "Arab",
        "he" to "Hebr",
        // 东南亚
        "th" to "Thai", "lo" to "Laoo", "km" to "Khmr", "my" to "Mymr",
        // 南亚
        "hi" to "Deva", "mr" to "Deva", "ne" to "Deva",
        "bn" to "Beng", "pa" to "Guru", "gu" to "Gujr",
        "ta" to "Taml", "te" to "Telu", "kn" to "Knda", "ml" to "Mlym",
        "si" to "Sinh",
        // 其它
        "el" to "Grek", "hy" to "Armn", "ka" to "Geor", "bo" to "Tibt",
        "am" to "Ethi",
    )

    private const val CHINESE = "zh"
    private const val HAN_SCRIPT = "Hani"
    private const val SIMPLIFIED_HAN = "Hans"
    private const val TRADITIONAL_HAN = "Hant"
    private const val LATIN_SCRIPT = "Latn"
    private const val FALLBACK_LANGUAGE = "en"
}
