/*
 * Copyright 2026 Proify, Tomakino
 * Licensed under the Apache License, Version 2.0
 * http://www.apache.org/licenses/LICENSE-2.0
 */

@file:Suppress("DestructuringDeclaration")

package io.github.proify.lyricon.amprovider.xposed.credentials

import android.annotation.SuppressLint
import android.content.Context
import android.content.pm.PackageInfo
import android.os.Build
import com.highcapable.yukihookapi.hook.log.YLog
import io.github.proify.extensions.android.content.pm.versionCodeCompat
import io.github.proify.lyricon.amprovider.xposed.api.lyric.LyricsCredentials
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
@SuppressLint("StaticFieldLeak")
object CredentialsStore {

    private const val COOKIES_FILE = "CookieStoreTable.json"
    private const val BAG_FILE = "BagConfig.json"

    private const val APPLE_DOMAIN = "apple.com"
    private const val DSID_COOKIE = "X-Dsid"
    private const val DEFAULT_STOREFRONT = "us"
    private const val COOKIE_SEPARATOR = "; "

    private val json = Json {
        ignoreUnknownKeys = true
        isLenient = true
    }

    private var context: Context? = null

    @Volatile
    private var storefrontCache: String? = null

    @Volatile
    private var userAgentCache: String? = null

    @Volatile
    private var packageInfoCache: PackageInfo? = null

    private val developerTokenReader: DeveloperTokenReader by lazy {
        DeveloperTokenReaders.create(versionCode())
    }

    fun initialize(appContext: Context) {
        if (context != null) return
        context = appContext.applicationContext
    }

    fun storefront(): String {
        storefrontCache?.let { return it }
        val resolved = runCatching {
            val bag = json.decodeFromString<BagConfig>(
                File(requireContext().filesDir, BAG_FILE).readText()
            )
            bag.countryCode
                ?.takeIf { it.length == 2 && it.all(Char::isLetter) }
                ?.lowercase()
        }.getOrNull() ?: DEFAULT_STOREFRONT
        storefrontCache = resolved
        return resolved
    }

    fun userAgent(): String? {
        userAgentCache?.let { return it }

        val pkg = packageInfo() ?: return null
        val versionName = pkg.versionName?.takeIf { it.isNotBlank() } ?: return null
        val release = Build.VERSION.RELEASE?.takeIf { it.isNotBlank() } ?: return null

        val ua = buildString {
            append("Music/").append(versionName)
            append(" Android/").append(release)
            Build.MODEL?.takeIf { it.isNotBlank() }
                ?.let { append(" model/").append(it) }
            (Build.BRAND ?: Build.MANUFACTURER)?.takeIf { it.isNotBlank() }
                ?.let { append(" brand/").append(it) }
            append(" build/").append(pkg.versionCodeCompat)
            append(" (dt:66)")
        }
        userAgentCache = ua
        return ua
    }

    fun credentials(): LyricsCredentials? {
        val ctx = context ?: return null

        val token = developerTokenReader.read(ctx) ?: run {
            YLog.error(tag = "Credentials", msg = "developerToken 读取失败")
            return null
        }

        val cookies = readCookies(ctx)
        if (cookies.isEmpty()) {
            YLog.error(tag = "Credentials", msg = "Cookie 为空")
            return null
        }

        val cookieHeader = cookies.entries.joinToString(COOKIE_SEPARATOR) {
            "${it.key}=${it.value}"
        }

        return LyricsCredentials(
            developerToken = token,
            cookie = cookieHeader,
            dsid = cookies[DSID_COOKIE],
            userToken = null,
        ).also {
            YLog.debug(
                tag = "Credentials",
                msg = "ready token=${token.length}字符 cookie=${cookies.size}项 dsid=*** " +
                        "storefront=${storefront()}"
            )
        }
    }

    private fun packageInfo(): PackageInfo? =
        packageInfoCache ?: runCatching {
            val ctx = requireContext()
            ctx.packageManager.getPackageInfo(ctx.packageName, 0)
        }.getOrNull()?.also { packageInfoCache = it }

    private fun versionCode(): Long = packageInfo()?.versionCodeCompat ?: 0

    private fun readCookies(context: Context): Map<String, String> = runCatching {
        val table = json.decodeFromString<CookieTable>(
            File(context.filesDir, COOKIES_FILE).readText()
        )
        buildMap {
            for (cookie in table.cookies) {
                val name = cookie.name?.takeIf { it.isNotBlank() } ?: continue
                if (!isAppleDomain(cookie.domain)) continue
                put(name, cookie.value ?: "")
            }
        }
    }.getOrElse {
        YLog.error(tag = "Credentials", msg = "读取 Cookie 失败", e = it)
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