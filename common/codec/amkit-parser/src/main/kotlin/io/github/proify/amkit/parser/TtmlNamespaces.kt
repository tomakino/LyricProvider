/*
 * Copyright 2026 Tomakino
 * Licensed under the Apache License, Version 2.0
 * http://www.apache.org/licenses/LICENSE-2.0
 */

package io.github.proify.amkit.parser

/** Apple 风格 TTML 涉及的命名空间。 */
internal object TtmlNamespaces {
    /** TTML 主命名空间。 */
    const val TTML = "http://www.w3.org/ns/ttml"

    /** 元数据命名空间。 */
    const val TTM = "http://www.w3.org/ns/ttml#metadata"

    /** Apple 私有命名空间。 */
    const val ITUNES = "http://music.apple.com/lyric-ttml-internal"

    /** AMLL 扩展命名空间。 */
    const val AMLL = "http://www.example.com/ns/amll"

    /** XML 内置命名空间。 */
    const val XML = "http://www.w3.org/XML/1998/namespace"
}

/** `ttm:role` 的已知取值。 */
internal object TtmlRoles {
    /** 译文。 */
    const val TRANSLATION = "x-translation"

    /** 音译。 */
    const val ROMAN = "x-roman"

    /** 背景人声。 */
    const val BACKGROUND = "x-bg"
}
