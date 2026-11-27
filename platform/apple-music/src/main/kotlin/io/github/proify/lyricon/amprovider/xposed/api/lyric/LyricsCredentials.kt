/*
 * Copyright 2026 Proify, Tomakino
 * Licensed under the Apache License, Version 2.0
 * http://www.apache.org/licenses/LICENSE-2.0
 */

package io.github.proify.lyricon.amprovider.xposed.api.lyric

/** Apple Music 授权凭证。 */
data class LyricsCredentials(
    val developerToken: String,

    val cookie: String? = null,

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
    if (length <= 8) "<$length chars>" else "${take(2)}…${takeLast(2)}（$length chars）"
