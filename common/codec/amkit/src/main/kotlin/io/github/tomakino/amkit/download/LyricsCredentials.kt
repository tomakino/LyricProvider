/*
 * Copyright 2026 Proify, Tomakino
 * Licensed under the Apache License, Version 2.0
 * http://www.apache.org/licenses/LICENSE-2.0
 */

package io.github.tomakino.amkit.download

/**
 * 调用 Apple Music 授权接口所需的凭证。
 *
 * 本库**不内置任何默认凭证，也不代为获取** —— 全部由调用方通过合法途径提供。
 *
 * 官方 Android 客户端的鉴权是三件套（对照抓包实测确认）：
 * - [developerToken] → `Authorization: Bearer <token>`
 * - [cookie] → 承载用户登录态。**缺失时接口返回 404 而非 401**，
 *   这是最容易被误判为"曲目不存在"的坑。
 * - [dsid] → `x-dsid` 请求头
 *
 * ⚠️ 切勿把真实凭证硬编码进源码或提交到版本库。
 */
data class LyricsCredentials(
    val developerToken: String,
    /** 用户会话 Cookie。缺少它时歌词端点会返回 404。 */
    val cookie: String? = null,
    /** 用户 dsid，对应 `x-dsid` 请求头。 */
    val dsid: String? = null,
    /** `Music-User-Token`。与 [cookie] 不是同一套机制，部分端点需要。 */
    val userToken: String? = null,
) {
    init {
        require(developerToken.isNotBlank()) { "developerToken 不能为空" }
    }

    /**
     * **刻意覆盖 `toString()`**。
     *
     * data class 自动生成的版本会把 `developerToken` 与 `cookie` 原样打印出来，
     * 于是 `println(credentials)` 或一行日志就泄露了整份凭证 —— 这个坑在调试时
     * 真实踩过一次（Gradle 打印含系统属性的命令行，凭证进了日志）。
     *
     * 这里只保留长度与首尾各两位：足够判断「配没配、是不是同一份」，
     * 又不构成泄露。
     */
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
