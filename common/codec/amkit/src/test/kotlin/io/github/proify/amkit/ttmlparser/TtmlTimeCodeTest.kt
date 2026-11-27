/*
 * Copyright 2026 Proify, Tomakino
 * Licensed under the Apache License, Version 2.0
 * http://www.apache.org/licenses/LICENSE-2.0
 */

package io.github.proify.amkit.ttmlparser

import kotlin.test.Test
import kotlin.test.assertEquals
import kotlin.test.assertFailsWith
import kotlin.test.assertNull
import kotlin.time.Duration.Companion.milliseconds
import kotlin.time.Duration.Companion.seconds

class TtmlTimeCodeTest {

    @Test
    fun `无后缀秒值`() {
        assertEquals(15_525.milliseconds, TtmlTimeCode.parse("15.525"))
    }

    @Test
    fun `带秒后缀`() {
        assertEquals(12_300.milliseconds, TtmlTimeCode.parse("12.3s"))
        assertEquals(12.seconds, TtmlTimeCode.parse("12s"))
    }

    @Test
    fun `毫秒后缀`() {
        assertEquals(1_500.milliseconds, TtmlTimeCode.parse("1500ms"))
    }

    @Test
    fun `时钟格式`() {
        assertEquals(62_500.milliseconds, TtmlTimeCode.parse("01:02.5"))
        assertEquals(3_723_000.milliseconds, TtmlTimeCode.parse("01:02:03"))
    }

    @Test
    fun `非法值返回 null`() {
        assertNull(TtmlTimeCode.parseOrNull(null))
        assertNull(TtmlTimeCode.parseOrNull(""))
        assertNull(TtmlTimeCode.parseOrNull("  "))
        assertNull(TtmlTimeCode.parseOrNull("abc"))
        assertNull(TtmlTimeCode.parseOrNull("1500f"))
        assertNull(TtmlTimeCode.parseOrNull("100t"))
    }

    @Test
    fun `严格解析抛异常`() {
        assertFailsWith<IllegalArgumentException> { TtmlTimeCode.parse("") }
        assertFailsWith<IllegalArgumentException> { TtmlTimeCode.parse("a:b") }
        assertFailsWith<IllegalArgumentException> { TtmlTimeCode.parse("1:2:3:4") }
    }
}
