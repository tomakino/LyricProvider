/*
 * Copyright 2026 Proify, Tomakino
 * Licensed under the Apache License, Version 2.0
 * http://www.apache.org/licenses/LICENSE-2.0
 */

package io.github.proify.lyricon.amprovider.xposed.lyrics

import android.content.Context
import io.github.proify.lyricon.amprovider.xposed.credentials.DeveloperTokenReader
import io.github.proify.lyricon.amprovider.xposed.credentials.DeveloperTokenReaders
import io.github.proify.lyricon.amprovider.xposed.util.DebugLog
import io.github.tomakino.amkit.download.LyricsCredentials
import kotlinx.serialization.Serializable
import kotlinx.serialization.json.Json
import java.io.File

/**
 * amkit 直连所需凭据。
 *
 * 三个来源（均在宿主进程内可直接读取）：
 * - developerToken：按版本选择 [DeveloperTokenReader]
 * - Cookie：`CookieStoreTable.json` 中 domain 为 `apple.com` 的全部条目
 * - dsid / storefront：`X-Dsid` cookie 与 `BagConfig.json` 的 `countryCode`
 */
object CredentialsStore {

    private const val COOKIES_FILE = "CookieStoreTable.json"
    private const val BAG_FILE = "BagConfig.json"

    private const val APPLE_DOMAIN = "apple.com"
    private const val DSID_COOKIE = "X-Dsid"
    private const val DEFAULT_STOREFRONT = "us"

    private const val TOKEN_EXPIRY_MARGIN_MS = 5 * 60 * 1000L

    private val json = Json { ignoreUnknownKeys = true; isLenient = true }

    @Volatile
    private var context: Context? = null

    @Volatile
    private var cached: LyricsCredentials? = null

    @Volatile
    private var storefront: String? = null

    private val reader: DeveloperTokenReader by lazy {
        DeveloperTokenReaders.create(versionCode())
    }

    fun initialize(appContext: Context) {
        if (context != null) return
        context = appContext
    }

    fun storefront(): String {
        storefront?.let { return it }
        val resolved = runCatching {
            val bag = json.decodeFromString<BagConfig>(
                File(requireContext().filesDir, BAG_FILE).readText()
            )
            bag.countryCode?.takeIf { it.length == 2 }?.lowercase()
        }.getOrNull() ?: DEFAULT_STOREFRONT
        storefront = resolved
        return resolved
    }

    fun credentials(): LyricsCredentials? {
        // 缓存的 token 到期后必须重新读取，否则进程长时间运行会全部 404
        cached?.let {
            if (!isTokenExpired(it.developerToken)) return it
            DebugLog.log("Credentials", "developerToken 已过期，重新读取")
            cached = null
        }
        val ctx = context ?: return null

        val token = reader.read(ctx) ?: run {
            DebugLog.error("Credentials", "developerToken 读取失败")
            return null
        }
        DebugLog.log("Credentials", "token=$token")
        val cookies = readCookies(ctx)
        if (cookies.isEmpty()) {
            DebugLog.error("Credentials", "Cookie 为空")
            return null
        }

        val dsid = cookies[DSID_COOKIE]
        val cookieHeader = cookies.entries.joinToString("; ") { "${it.key}=${it.value}" }

        return LyricsCredentials(
            developerToken = token,
            cookie = cookieHeader,
            dsid = dsid,
            userToken = null,
        ).also {
            cached = it
            DebugLog.log(
                "Credentials",
                "ready token=${token.length}字节 cookie=${cookies.size}项 dsid=$dsid " +
                        "storefront=${storefront()}"
            )
        }
    }

    private fun versionCode(): Int = runCatching {
        val ctx = requireContext()
        ctx.packageManager.getPackageInfo(ctx.packageName, 0).versionCode
    }.getOrDefault(0)

    /** JWT 的 `exp` 早于当前时间 + 5 分钟即视为过期。 */
    private fun isTokenExpired(token: String): Boolean = runCatching {
        val payload = token.split('.').getOrNull(1) ?: return false
        val json = String(
            android.util.Base64.decode(payload, android.util.Base64.URL_SAFE or android.util.Base64.NO_PADDING),
            Charsets.UTF_8,
        )
        val exp = Regex(""""exp"\s*:\s*(\d+)""").find(json)?.groupValues?.getOrNull(1)?.toLong()
            ?: return false
        exp * 1000L <= System.currentTimeMillis() + TOKEN_EXPIRY_MARGIN_MS
    }.getOrDefault(false)

    private fun readCookies(context: Context): Map<String, String> = runCatching {
        val table = json.decodeFromString<CookieTable>(
            File(context.filesDir, COOKIES_FILE).readText()
        )
        table.cookies
            .filter { isAppleDomain(it.domain) && !it.name.isNullOrBlank() }
            .associate { it.name!! to (it.value ?: "") }
    }.getOrElse {
        DebugLog.error("Credentials", "读取 Cookie 失败", it)
        emptyMap()
    }

    private fun requireContext(): Context = context ?: error("CredentialsStore 未初始化")

    private fun isAppleDomain(domain: String?): Boolean =
        domain == APPLE_DOMAIN || domain == ".$APPLE_DOMAIN"

    @Serializable
    private data class CookieTable(val cookies: List<Cookie> = emptyList())

    @Serializable
    private data class Cookie(
        val domain: String? = null,
        val name: String? = null,
        val value: String? = null,
    )

    @Serializable
    private data class BagConfig(val countryCode: String? = null)
}
