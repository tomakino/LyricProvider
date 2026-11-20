/*
 * Copyright 2026 Proify, Tomakino
 * Licensed under the Apache License, Version 2.0
 * http://www.apache.org/licenses/LICENSE-2.0
 */

package io.github.proify.amkit.locale

import java.util.Locale
import kotlin.test.Test
import kotlin.test.assertEquals
import kotlin.test.assertTrue

class SystemLyricsLocaleTest {

    @Test
    fun `中文简繁`() {
        assertEquals("zh-Hans", SystemLyricsLocale.languageTag(Locale.forLanguageTag("zh-CN")))
        assertEquals("zh-Hant", SystemLyricsLocale.languageTag(Locale.forLanguageTag("zh-TW")))
        assertEquals("zh-Hant", SystemLyricsLocale.languageTag(Locale.forLanguageTag("zh-HK")))
        assertEquals("zh-Hant", SystemLyricsLocale.languageTag(Locale.forLanguageTag("zh-MO")))
    }

    @Test
    fun `中文其余地区回落`() {
        assertEquals("zh-SG", SystemLyricsLocale.languageTag(Locale.forLanguageTag("zh-SG")))
        assertEquals("zh-Hans", SystemLyricsLocale.scriptTag(Locale.forLanguageTag("zh-SG")))
    }

    @Test
    fun `日韩`() {
        assertEquals("ja-JP", SystemLyricsLocale.languageTag(Locale.forLanguageTag("ja-JP")))
        assertTrue(SystemLyricsLocale.scriptTag(Locale.forLanguageTag("ja-JP")).contains("ja-Latn"))
        assertEquals("ko-KR", SystemLyricsLocale.languageTag(Locale.forLanguageTag("ko-KR")))
        assertTrue(SystemLyricsLocale.scriptTag(Locale.forLanguageTag("ko-KR")).contains("ko-Kore"))
    }

    @Test
    fun `非拉丁语言`() {
        assertTrue(SystemLyricsLocale.scriptTag(Locale.forLanguageTag("ru-RU")).contains("ru-Cyrl"))
        assertTrue(SystemLyricsLocale.scriptTag(Locale.forLanguageTag("ar-SA")).contains("ar-Arab"))
        assertTrue(SystemLyricsLocale.scriptTag(Locale.forLanguageTag("he-IL")).contains("he-Hebr"))
        assertTrue(SystemLyricsLocale.scriptTag(Locale.forLanguageTag("th-TH")).contains("th-Thai"))
        assertTrue(SystemLyricsLocale.scriptTag(Locale.forLanguageTag("hi-IN")).contains("hi-Deva"))
        assertTrue(SystemLyricsLocale.scriptTag(Locale.forLanguageTag("el-GR")).contains("el-Grek"))
    }

    @Test
    fun `拉丁语言默认`() {
        assertEquals("en-US", SystemLyricsLocale.languageTag(Locale.forLanguageTag("en-US")))
        assertTrue(SystemLyricsLocale.scriptTag(Locale.forLanguageTag("en-US")).contains("en-Latn"))
        assertTrue(SystemLyricsLocale.scriptTag(Locale.forLanguageTag("fr-FR")).contains("fr-Latn"))
        assertTrue(SystemLyricsLocale.scriptTag(Locale.forLanguageTag("vi-VN")).contains("vi-Latn"))
    }

    @Test
    fun `扩展后的语言`() {
        assertTrue(SystemLyricsLocale.scriptTag(Locale.forLanguageTag("ka-GE")).contains("ka-Geor"))
        assertTrue(SystemLyricsLocale.scriptTag(Locale.forLanguageTag("hy-AM")).contains("hy-Armn"))
        assertTrue(SystemLyricsLocale.scriptTag(Locale.forLanguageTag("bo-CN")).contains("bo-Tibt"))
        assertTrue(SystemLyricsLocale.scriptTag(Locale.forLanguageTag("am-ET")).contains("am-Ethi"))
    }
}
