/*
 * Copyright 2026 Tomakino
 * Licensed under the Apache License, Version 2.0
 * http://www.apache.org/licenses/LICENSE-2.0
 */

package io.github.tomakino.amkit

/**
 * 解析 TTML 失败时抛出。
 *
 * 只在**结构性问题**（XML 非法、根元素缺失）时抛出；
 * 单个属性取值异常（例如时间码写错）不会中断整份文档的解析，
 * 而是按「降级为 null / 未知枚举」处理 —— 歌词文件由第三方贡献，
 * 局部脏数据不应导致整首歌词不可用。
 */
class LyricsParseException(
    message: String,
    cause: Throwable? = null,
) : Exception(message, cause)

/**
 * 下载歌词失败时抛出。
 *
 * 覆盖网络异常、非 2xx 响应，以及响应中不含 TTML 的情况。
 */
class LyricsDownloadException(
    message: String,
    cause: Throwable? = null,
) : Exception(message, cause)
