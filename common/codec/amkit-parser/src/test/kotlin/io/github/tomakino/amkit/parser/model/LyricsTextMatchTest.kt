/*
 * Copyright 2026 Proify, Tomakino
 * Licensed under the Apache License, Version 2.0
 * http://www.apache.org/licenses/LICENSE-2.0
 */

package io.github.tomakino.amkit.parser.model

import kotlin.test.Test
import kotlin.test.assertEquals
import kotlin.test.assertFalse
import kotlin.test.assertNull
import kotlin.test.assertTrue
import kotlin.time.Duration.Companion.seconds

class LyricsTextMatchTest {

    private fun text(language: String?, type: String? = null) =
        LyricsText(text = "x", language = language, type = type)

    @Test
    fun `精确匹配大小写不敏感`() {
        val candidates = listOf(text("zh-Hans"))
        assertEquals("zh-Hans", candidates.matchLanguage("ZH-HANS", fallback = false)?.language)
    }

    @Test
    fun `回退链逐级去掉子标签`() {
        val candidates = listOf(text("zh-Hans"))
        assertEquals("zh-Hans", candidates.matchLanguage("zh-Hans-CN", fallback = true)?.language)
        assertNull(candidates.matchLanguage("zh-Hans-CN", fallback = false))
    }

    @Test
    fun `language 为 null 取第一条`() {
        val candidates = listOf(text("en"), text("ja"))
        assertEquals("en", candidates.matchLanguage(null, fallback = false)?.language)
    }

    @Test
    fun `preferredType 优先`() {
        val candidates = listOf(text("zh-Hans", "replacement"), text("zh-Hans", "subtitle"))
        assertEquals("subtitle", candidates.matchLanguage("zh-Hans", false, "subtitle")?.type)
        assertEquals("replacement", candidates.matchLanguage("zh-Hans", false, null)?.type)
    }

    @Test
    fun `空列表返回 null`() {
        assertNull(emptyList<LyricsText>().matchLanguage("zh-Hans", fallback = true))
    }

    @Test
    fun `完整逐字时间判定`() {
        val timed = text("ja-Latn").copy(
            words = listOf(LyricsWord("a", 1.seconds, 2.seconds)),
        )
        val partial = text("ja-Latn").copy(
            words = listOf(LyricsWord("a", 1.seconds, 2.seconds), LyricsWord("b")),
        )
        val empty = text("ja-Latn")

        assertTrue(timed.hasCompleteTiming())
        assertFalse(partial.hasCompleteTiming())
        assertFalse(empty.hasCompleteTiming())
    }
}
