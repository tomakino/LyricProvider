/*
 * Copyright 2026 Proify, Tomakino
 * Licensed under the Apache License, Version 2.0
 * http://www.apache.org/licenses/LICENSE-2.0
 */

package io.github.proify.amkit.download

/** Apple Music 授权凭证。 */
data class LyricsCredentials(
    val developerToken: String,

    /** 用户会话 Cookie；缺失时接口返回 404 而非 401。 */
    val cookie: String? = null,

    /** 用户 dsid，对应 `x-dsid` 请求头。 */
    val dsid: String? = null,

    /** `Music-User-Token`，与 [cookie] 不是同一套机制。 */
    val userToken: String? = null,
) {
    init {
        require(developerToken.isNotBlank()) { "developerToken 不能为空" }
    }

    /** 脱敏输出。 */
    override fun toString(): String =
        "LyricsCredentials(" +
                "developerToken=${developerToken.masked()}, " +
                "cookie=${cookie?.masked()}, " +
                "dsid=${dsid?.masked()}, " +
                "userToken=${userToken?.masked()})"
}

/** 打码：只暴露长度与首尾两位，短值直接只给长度。 */
private fun String.masked(): String =
    if (length <= 8) "<$length 字符>" else "${take(2)}…${takeLast(2)}（$length 字符）"
