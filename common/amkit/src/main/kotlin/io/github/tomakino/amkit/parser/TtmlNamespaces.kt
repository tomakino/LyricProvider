/*
 * Copyright 2026 Tomakino
 * Licensed under the Apache License, Version 2.0
 * http://www.apache.org/licenses/LICENSE-2.0
 */

package io.github.tomakino.amkit.parser

/**
 * Apple 风格 TTML 涉及的命名空间。
 *
 * 这些常量集中在一处，避免在解析代码里散落字符串字面量
 * （写错命名空间会导致元素静默查不到，是最难排查的一类 bug）。
 */
internal object TtmlNamespaces {
    /** TTML 主命名空间，也是 `<tt>`、`<head>`、`<body>`、`<div>`、`<p>`、`<span>` 的命名空间。 */
    const val TTML = "http://www.w3.org/ns/ttml"

    /** TTML 元数据命名空间，包含 `agent` / `name` / `role` 等。 */
    const val TTM = "http://www.w3.org/ns/ttml#metadata"

    /** Apple 私有命名空间：`timing`、`key`、`song-part`、`iTunesMetadata`、`translations`、`audio` 等都在这里。 */
    const val ITUNES = "http://music.apple.com/lyric-ttml-internal"

    /**
     * AMLL 扩展命名空间：`meta` 等。
     *
     * 这是**社区格式**的扩展，Apple 的真实响应里不包含它（385 首实测为 0），
     * 保留它是为了能直接吃下 AMLL TTML DB 那类样本。
     */
    const val AMLL = "http://www.example.com/ns/amll"

    /** XML 内置命名空间，`xml:lang`、`xml:id` 使用它。 */
    const val XML = "http://www.w3.org/XML/1998/namespace"
}

/**
 * `ttm:role` 的已知取值。
 *
 * 未知取值不会抛错，会被当作普通词处理（见 [SpanCollector]）。
 */
internal object TtmlRoles {
    /** 翻译。 */
    const val TRANSLATION = "x-translation"

    /** 音译 / 罗马音。 */
    const val ROMAN = "x-roman"

    /** 背景人声组，内部可再嵌套词、翻译与音译。 */
    const val BACKGROUND = "x-bg"
}
