/*
 * Copyright 2026 Proify, Tomakino
 * Licensed under the Apache License, Version 2.0
 * http://www.apache.org/licenses/LICENSE-2.0
 */

package io.github.proify.lyricon.amprovider.xposed.lyrics

import android.content.Context
import io.github.proify.lyricon.amprovider.xposed.util.DebugLog
import io.github.tomakino.amkit.download.LyricsCredentials
import kotlinx.serialization.Serializable
import kotlinx.serialization.json.Json
import java.io.File
import java.nio.ByteBuffer
import java.security.KeyStore
import javax.crypto.Cipher
import javax.crypto.SecretKey
import javax.crypto.spec.GCMParameterSpec

/**
 * amkit 直连所需凭据。
 *
 * 三个来源（均在宿主进程内可直接读取）：
 * - developerToken：`datastore/encrypted_tokens.preferences_pb` 的
 *   `key_api_token_musicAndroid`，用 AndroidKeyStore 的 `music_token_store_key` 解密
 * - Cookie：`files/CookieStoreTable.json` 中 domain 为 `apple.com` 的全部条目
 * - dsid / storefront：`X-Dsid` cookie 与 `files/BagConfig.json` 的 `countryCode`
 *
 * 实测：只带 X-Dsid 等少数 cookie 会 404，必须带上 `mz_at_ssl` / `mz_at0` 等认证 cookie；
 * `Music-User-Token` 非必需。
 */
object CredentialsStore {

    private const val TOKENS_FILE = "datastore/encrypted_tokens.preferences_pb"
    private const val COOKIES_FILE = "CookieStoreTable.json"
    private const val BAG_FILE = "BagConfig.json"

    private const val TOKEN_STORE_KEY = "music_token_store_key"
    private const val API_TOKEN_KEY = "key_api_token_musicAndroid"
    private const val PREF_CIPHER = "__cipher"
    private const val PREF_IV = "__iv"

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

        val token = readDeveloperToken(ctx) ?: run {
            DebugLog.error("Credentials", "developerToken 解析失败")
            return null
        }
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

    private fun readDeveloperToken(context: Context): String? = runCatching {
        val key = loadKeyStoreKey()
        val entries = parseDataStore(File(context.filesDir, TOKENS_FILE).readBytes())
        val cipher = entries["$API_TOKEN_KEY$PREF_CIPHER"] ?: return null
        val iv = entries["$API_TOKEN_KEY$PREF_IV"] ?: return null

        val c = Cipher.getInstance("AES/GCM/NoPadding")
        c.init(
            Cipher.DECRYPT_MODE,
            key,
            GCMParameterSpec(128, android.util.Base64.decode(iv, 0)),
        )
        String(c.doFinal(android.util.Base64.decode(cipher, 0)), Charsets.UTF_8)
    }.getOrElse {
        DebugLog.error("Credentials", "解密 developerToken 失败", it)
        null
    }

    private fun loadKeyStoreKey(): SecretKey {
        val keyStore = KeyStore.getInstance("AndroidKeyStore").apply { load(null) }
        val entry = keyStore.getEntry(TOKEN_STORE_KEY, null)
        if (entry is KeyStore.SecretKeyEntry) return entry.secretKey
        error("missing $TOKEN_STORE_KEY")
    }

    private fun readCookies(context: Context): Map<String, String> = runCatching {
        val table = json.decodeFromString<CookieTable>(
            File(context.filesDir, COOKIES_FILE).readText()
        )
        table.cookies
            .filter { it.domain == APPLE_DOMAIN && !it.name.isNullOrBlank() }
            .associate { it.name!! to (it.value ?: "") }
    }.getOrElse {
        DebugLog.error("Credentials", "读取 Cookie 失败", it)
        emptyMap()
    }

    private fun requireContext(): Context = context ?: error("CredentialsStore 未初始化")

    /** 解析 DataStore protobuf：entry{ key:1:string, value:2:message{ 5:string } }。 */
    private fun parseDataStore(bytes: ByteArray): Map<String, String> {
        val result = mutableMapOf<String, String>()
        val buffer = ByteBuffer.wrap(bytes)

        while (buffer.hasRemaining()) {
            val tag = readVarint(buffer)
            val wire = (tag and 0x7).toInt()
            if (wire != WIRE_LENGTH) {
                skip(buffer, wire)
                continue
            }
            val chunk = readBytes(buffer)
            if ((tag ushr 3).toInt() != 1) continue

            val entry = ByteBuffer.wrap(chunk)
            var name: String? = null
            var value: String? = null
            while (entry.hasRemaining()) {
                val entryTag = readVarint(entry)
                val entryWire = (entryTag and 0x7).toInt()
                if (entryWire != WIRE_LENGTH) {
                    skip(entry, entryWire)
                    continue
                }
                val part = readBytes(entry)
                when ((entryTag ushr 3).toInt()) {
                    1 -> name = String(part, Charsets.UTF_8)
                    2 -> value = parseValue(part)
                }
            }
            if (name != null && value != null) result[name] = value
        }
        return result
    }

    private fun parseValue(bytes: ByteArray): String? {
        val buffer = ByteBuffer.wrap(bytes)
        while (buffer.hasRemaining()) {
            val tag = readVarint(buffer)
            val wire = (tag and 0x7).toInt()
            if (wire != WIRE_LENGTH) {
                skip(buffer, wire)
                continue
            }
            val part = readBytes(buffer)
            if ((tag ushr 3).toInt() == 5) return String(part, Charsets.UTF_8)
        }
        return null
    }

    private fun readVarint(buffer: ByteBuffer): Long {
        var value = 0L
        var shift = 0
        while (buffer.hasRemaining() && shift < 64) {
            val byte = buffer.get().toInt()
            value = value or ((byte and 0x7f).toLong() shl shift)
            if (byte and 0x80 == 0) break
            shift += 7
        }
        return value
    }

    private fun readBytes(buffer: ByteBuffer): ByteArray {
        val length = readVarint(buffer).toInt()
        return ByteArray(length).also { buffer.get(it) }
    }

    private fun skip(buffer: ByteBuffer, wire: Int) {
        when (wire) {
            0 -> readVarint(buffer)
            1 -> buffer.position(buffer.position() + 8)
            5 -> buffer.position(buffer.position() + 4)
            else -> error("unsupported wire $wire")
        }
    }

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

    private const val WIRE_LENGTH = 2
}
