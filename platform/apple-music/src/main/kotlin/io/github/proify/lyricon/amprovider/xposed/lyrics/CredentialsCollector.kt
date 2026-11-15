/*
 * Copyright 2026 Proify, Tomakino
 * Licensed under the Apache License, Version 2.0
 * http://www.apache.org/licenses/LICENSE-2.0
 */

package io.github.proify.lyricon.amprovider.xposed.lyrics

import android.content.Context
import android.webkit.CookieManager
import com.highcapable.yukihookapi.hook.log.YLog
import io.github.tomakino.amkit.download.LyricsCredentials
import java.io.File

/**
 * 为 amkit 收集调用 Apple Music 授权接口所需的凭证。
 *
 * 由于不再依赖 Apple Music 内部的歌词下载流程，这里必须在宿主进程内自行取得：
 * - **developerToken**：MusicKit 的 JWT（`eyJ...`），优先读取偏好，其次扫描 App 的 shared_prefs
 * - **cookie / dsid**：宿主 WebView 登录态，直接从 [CookieManager] 读取
 * - **userToken**：MusicKit 用户 token，扫描 shared_prefs
 *
 * 所有来源都失败时返回 null，调用方应降级而不是崩溃。
 */
object CredentialsCollector {

    /** 偏好中允许手动补充的 developerToken（兜底，调试用）。 */
    private const val PREFS_NAME = "lyricon_amkit_credentials"
    private const val KEY_DEVELOPER_TOKEN = "developer_token"
    private const val KEY_USER_TOKEN = "user_token"

    private const val WEB_ORIGIN = "https://music.apple.com"
    private const val API_ORIGIN = "https://amp-api.music.apple.com"

    private val JWT_PATTERN = Regex("""eyJ[A-Za-z0-9_-]{8,}\.[A-Za-z0-9_-]{8,}\.[A-Za-z0-9_-]{8,}""")

    @Volatile
    private var cached: LyricsCredentials? = null

    /** 取一份可用凭证；不可用时返回 null（不抛异常）。 */
    fun credentials(context: Context): LyricsCredentials? {
        cached?.let { return it }

        val cookie = readCookie()
        val dsid = parseDsid(cookie)
        val developerToken = findDeveloperToken(context) ?: run {
            YLog.warn(
                tag = "AMProvider",
                msg = "CredentialsCollector: 未找到 developerToken（可在 $PREFS_NAME 的 $KEY_DEVELOPER_TOKEN 手动配置）"
            )
            return null
        }
        val userToken = findUserToken(context)

        return LyricsCredentials(
            developerToken = developerToken,
            cookie = cookie,
            dsid = dsid,
            userToken = userToken
        ).also { creds ->
            cached = creds
            YLog.debug(
                tag = "AMProvider",
                msg = "CredentialsCollector: prepared developerToken=${developerToken.take(12)}... " +
                        "cookie=${cookie != null} dsid=${dsid != null} userToken=${userToken != null}"
            )
        }
    }

    /** 登录态变化后清空缓存。 */
    fun invalidate() {
        cached = null
    }

    private fun readCookie(): String? = runCatching {
        val manager = CookieManager.getInstance()
        manager.getCookie(WEB_ORIGIN) ?: manager.getCookie(API_ORIGIN)
    }.getOrNull()

    private fun parseDsid(cookie: String?): String? =
        cookie?.let { Regex("""(?:^|;\s*)dsid=([^;]+)""").find(it)?.groupValues?.getOrNull(1) }

    /** 手动配置 > shared_prefs 中疑似 JWT 的最长字符串。 */
    private fun findDeveloperToken(context: Context): String? {
        runCatching {
            context.getSharedPreferences(PREFS_NAME, Context.MODE_PRIVATE)
                .getString(KEY_DEVELOPER_TOKEN, null)
                ?.takeIf { it.isNotBlank() }
        }.getOrNull()?.let { return it }

        return scanSharedPreferences(context, JWT_PATTERN) { it.length >= 100 }
    }

    private fun findUserToken(context: Context): String? {
        runCatching {
            context.getSharedPreferences(PREFS_NAME, Context.MODE_PRIVATE)
                .getString(KEY_USER_TOKEN, null)
                ?.takeIf { it.isNotBlank() }
        }.getOrNull()?.let { return it }

        return scanSharedPreferences(context, Regex("""(?i)music[_-]?user[_-]?token[^\"<>]*[\"\\s:]+([A-Za-z0-9._-]{20,})""")) {
            it.length >= 20
        }
    }

    /**
     * 在 App 的 shared_prefs 中扫描匹配值。
     *
     * 读取的是宿主进程自己的数据目录，属于本模块作用域内的合法读取；
     * 任何失败都静默跳过。
     */
    private fun scanSharedPreferences(
        context: Context,
        pattern: Regex,
        accept: (String) -> Boolean
    ): String? {
        val dir = runCatching { File(context.filesDir.parentFile, "shared_prefs") }.getOrNull() ?: return null
        if (!dir.isDirectory) return null

        val candidates = ArrayList<String>()
        dir.listFiles()?.forEach { file ->
            runCatching {
                val xml = file.readText()
                pattern.findAll(xml).forEach { match ->
                    val value = match.groupValues.last()
                    if (value.isNotBlank() && accept(value)) candidates += value
                }
            }
        }
        return candidates.maxByOrNull { it.length }
    }
}
