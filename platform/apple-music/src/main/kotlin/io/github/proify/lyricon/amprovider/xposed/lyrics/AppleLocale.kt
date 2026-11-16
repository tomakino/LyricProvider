/*
 * Copyright 2026 Proify, Tomakino
 * Licensed under the Apache License, Version 2.0
 * http://www.apache.org/licenses/LICENSE-2.0
 */

package io.github.proify.lyricon.amprovider.xposed.lyrics

import java.util.Locale

/**
 * 复刻 Apple Music 的 `LocaleUtil.getSystemLyricsLanguage()`，
 * 用于 `l[lyrics]` 请求参数与译文语言匹配。
 *
 * 规则：`language-script`，中文取简繁变体，其他语言取国家码。
 */
internal object AppleLocale {

    fun lyricsLanguage(locale: Locale = Locale.getDefault()): String {
        var script: String? = locale.script
        if (script.isNullOrEmpty()) script = chineseScript(locale)
        val language = locale.language
        if (script.isNullOrEmpty()) script = locale.country
        return if (script.isNullOrEmpty()) language else "$language-$script"
    }

    /** 对应 `LocaleUtil.getChineseScript()`。 */
    private fun chineseScript(locale: Locale): String? = when {
        locale == Locale.SIMPLIFIED_CHINESE -> "Hans"
        locale == Locale.TRADITIONAL_CHINESE -> "Hant"
        locale.language == "zh" && locale.country in TRADITIONAL_REGIONS -> "Hant"
        else -> null
    }

    private val TRADITIONAL_REGIONS = setOf("HK", "MO")
}
