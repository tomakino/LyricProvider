/*
 * Copyright 2026 Tomakino
 * Licensed under the Apache License, Version 2.0
 * http://www.apache.org/licenses/LICENSE-2.0
 */

package io.github.tomakino.amkit.download

import kotlinx.coroutines.Dispatchers
import kotlinx.coroutines.currentCoroutineContext
import kotlinx.coroutines.ensureActive
import kotlinx.coroutines.withContext
import java.io.ByteArrayOutputStream
import java.io.IOException
import java.net.HttpURLConnection
import java.net.URI
import java.net.URLEncoder
import java.nio.charset.Charset

/**
 * Apple Music 歌词下载器。
 *
 * @param lyricsLanguage 译文语言
 * @param lyricsScript 书写系统
 * @param userAgent 客户端 User-Agent
 */
class AppleMusicLyricsDownloader(
    private val credentials: LyricsCredentials,
    private val lyricsLanguage: String = SystemLyricsLocale.languageTag(),
    private val lyricsScript: String = SystemLyricsLocale.scriptTag(),
    private val userAgent: String = AppleMusicUserAgent.default,
    baseUrl: String = DEFAULT_BASE_URL,
) {

    private val baseUrl: String = baseUrl.trimEnd('/')

    /**
     * 下载 TTML 原文。
     *
     * @param songId 曲目 ID
     * @param storefront 商店区域
     * @throws LyricsDownloadException 网络失败、非 2xx、响应不含 TTML
     */
    suspend fun download(songId: String, storefront: String): String = withContext(Dispatchers.IO) {
        require(songId.isNotBlank()) { "songId 不能为空" }
        require(storefront.isNotBlank()) { "storefront 不能为空" }

        val body = execute(buildLyricsUrl(songId, storefront))
        AmpTtmlExtractor.extractTtml(body)
            ?: throw LyricsDownloadException("响应中不含 TTML 歌词（songId=$songId）")
    }

    /** 拼装请求 URL。 */
    internal fun buildLyricsUrl(songId: String, storefront: String): String {
        val params = buildList {
            add("l[lyrics]" to lyricsLanguage)
            if (lyricsScript.isNotBlank()) {
                add("extend" to EXTEND_TTML_LOCALIZATIONS)
                add("l[script]" to lyricsScript)
            }
        }
        val query = params.joinToString("&") { (key, value) ->
            "$key=${URLEncoder.encode(value, Charsets.UTF_8.name())}"
        }
        return buildString {
            append(baseUrl)
            append("/v1/catalog/")
            append(encodePathSegment(storefront))
            append("/songs/")
            append(encodePathSegment(songId))
            append("/syllable-lyrics?")
            append(query)
        }
    }

    /** 发起请求并读取响应体；所有网络/协议失败统一转换为 [LyricsDownloadException]。 */
    private suspend fun execute(url: String): String {
        val connection = try {
            withContext(Dispatchers.IO) {
                URI(url).toURL().openConnection()
            } as HttpURLConnection
        } catch (e: IOException) {
            throw LyricsDownloadException("请求创建失败: ${e.message}", e)
        }

        try {
            connection.requestMethod = "GET"
            connection.connectTimeout = CONNECT_TIMEOUT_MS
            connection.readTimeout = READ_TIMEOUT_MS
            connection.instanceFollowRedirects = true
            connection.setRequestProperty("Authorization", "Bearer ${credentials.developerToken}")
            connection.setRequestProperty("Accept", "application/json")
            connection.setRequestProperty("User-Agent", userAgent)
            connection.setRequestProperty("Origin", ORIGIN)
            connection.setRequestProperty("Referer", "$ORIGIN/")
            credentials.userToken?.let { connection.setRequestProperty("Music-User-Token", it) }
            credentials.cookie?.let { connection.setRequestProperty("Cookie", it) }
            credentials.dsid?.let { connection.setRequestProperty("x-dsid", it) }

            val code = try {
                connection.responseCode
            } catch (e: IOException) {
                throw LyricsDownloadException("网络请求失败: ${e.message}", e)
            }
            if (code !in 200..299) {
                throw LyricsDownloadException("接口返回 HTTP $code")
            }
            return readBody(connection)
        } finally {
            connection.disconnect()
        }
    }

    /** 按上限分块读取响应体；分块传输时 `contentLength` 为 -1，必须边读边累计。 */
    private suspend fun readBody(connection: HttpURLConnection): String {
        if (connection.contentLengthLong > MAX_RESPONSE_BYTES) {
            throw oversizedBody()
        }

        val output = ByteArrayOutputStream()
        val chunk = ByteArray(READ_CHUNK_BYTES)
        try {
            connection.inputStream.use { stream ->
                while (true) {
                    currentCoroutineContext().ensureActive()
                    val read = stream.read(chunk)
                    if (read == -1) break
                    output.write(chunk, 0, read)
                    if (output.size().toLong() > MAX_RESPONSE_BYTES) {
                        throw oversizedBody()
                    }
                }
            }
        } catch (e: IOException) {
            throw LyricsDownloadException("读取响应体失败: ${e.message}", e)
        }
        return String(output.toByteArray(), resolveCharset(connection.contentType))
    }

    private fun oversizedBody(): LyricsDownloadException =
        LyricsDownloadException(
            "响应体超过上限 $MAX_RESPONSE_BYTES 字节（正常歌词约 32 KB），已拒绝读取",
        )

    companion object {
        /** Apple Music AMP 接口的基础地址。 */
        const val DEFAULT_BASE_URL = "https://amp-api.music.apple.com"

        /** Origin / Referer 使用的站点。非必需，保留以贴近客户端行为。 */
        const val ORIGIN = "https://music.apple.com"

        /** 译文与罗马音所需的 extend 取值。 */
        const val EXTEND_TTML_LOCALIZATIONS = "ttmlLocalizations"

        /**
         * 响应体大小上限（4 MiB）。
         *
         * 正常歌词响应约 32 KB，留出两个数量级余量即可；超出说明响应被改写或服务异常，
         * 与其把内容读进内存不如直接失败。
         */
        const val MAX_RESPONSE_BYTES = 4L * 1024 * 1024

        private const val CONNECT_TIMEOUT_MS = 10_000
        private const val READ_TIMEOUT_MS = 20_000
        private const val READ_CHUNK_BYTES = 8 * 1024
    }
}

/** 路径段编码。 */
private fun encodePathSegment(value: String): String =
    URLEncoder.encode(value, Charsets.UTF_8.name()).replace("+", "%20")

/** 从 `Content-Type` 提取字符集，缺省按 UTF-8。 */
private fun resolveCharset(contentType: String?): Charset =
    contentType
        ?.substringAfter("charset=", "")
        ?.substringBefore(';')
        ?.trim()
        ?.takeIf { it.isNotEmpty() }
        ?.let { runCatching { Charset.forName(it) }.getOrNull() }
        ?: Charsets.UTF_8
