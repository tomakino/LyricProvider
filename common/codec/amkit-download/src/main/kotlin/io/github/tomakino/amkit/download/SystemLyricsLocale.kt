/*
 * Copyright 2026 Proify, Tomakino
 * Licensed under the Apache License, Version 2.0
 * http://www.apache.org/licenses/LICENSE-2.0
 */

package io.github.tomakino.amkit.download

import android.icu.lang.UScript
import java.util.Locale

/** 按系统区域推导本地化参数。 */
object SystemLyricsLocale {

    /** 译文语言。 */
    fun languageTag(locale: Locale = Locale.getDefault()): String {
        val script = locale.script.ifBlank {
            if (locale.language == CHINESE) chineseScript(locale) else locale.country
        }
        return "${locale.language}-$script"
    }

    /** 书写系统。 */
    fun scriptTag(locale: Locale = Locale.getDefault()): String =
        systemScripts(locale).joinToString(separator = ",") { "${locale.language}-$it" }

    /** 系统书写系统。 */
    private fun systemScripts(locale: Locale): List<String> {
        val codes = UScript.getCode(locale) ?: return emptyList()
        return buildList {
            if (locale == Locale.JAPAN) add(UScript.getShortName(LATIN))
            codes.forEach { add(UScript.getShortName(it)) }
        }
    }

    private fun chineseScript(locale: Locale): String = when {
        locale == Locale.SIMPLIFIED_CHINESE -> SIMPLIFIED_HAN
        locale == Locale.TRADITIONAL_CHINESE -> TRADITIONAL_HAN
        locale.country == "HK" -> TRADITIONAL_HAN
        locale.country == "MO" -> TRADITIONAL_HAN
        else -> ""
    }

    private const val CHINESE = "zh"
    private const val SIMPLIFIED_HAN = "Hans"
    private const val TRADITIONAL_HAN = "Hant"
    private const val LATIN = 54
}
