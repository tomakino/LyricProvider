/*
 * Copyright 2026 Proify, Tomakino
 * Licensed under the Apache License, Version 2.0
 * http://www.apache.org/licenses/LICENSE-2.0
 */

package io.github.proify.amkit.parser

/** 显示选项。 */
data class DisplayOptions(
    /** 译文语言。 */
    val language: String? = null,

    /** 语言匹配失败时是否逐级回退。 */
    val languageFallback: Boolean = true,

    /** 音译语言。 */
    val transliterationLanguage: String? = null,

    /** 同语言有多条译文时优先的类型。 */
    val translationType: String? = null,

    /** 替换型译文是否替换原文。 */
    val applyReplacement: Boolean = true,

    /** 无逐字时间的译文是否按字符对齐。 */
    val alignUntimedTranslation: Boolean = true,
)
