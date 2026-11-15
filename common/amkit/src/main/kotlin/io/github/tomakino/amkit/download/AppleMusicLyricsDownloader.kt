/*
 * Copyright 2026 Tomakino
 * Licensed under the Apache License, Version 2.0
 * http://www.apache.org/licenses/LICENSE-2.0
 */

package io.github.tomakino.amkit.download

import io.github.tomakino.amkit.LyricsDownloadException
import io.github.tomakino.amkit.model.LyricsDocument
import io.github.tomakino.amkit.parser.TtmlLyricsParser
import kotlinx.coroutines.suspendCancellableCoroutine
import okhttp3.Call
import okhttp3.Callback
import okhttp3.HttpUrl
import okhttp3.HttpUrl.Companion.toHttpUrl
import okhttp3.OkHttpClient
import okhttp3.Request
import okhttp3.Response
import okhttp3.ResponseBody
import java.io.ByteArrayOutputStream
import java.io.IOException
import kotlin.coroutines.resume
import kotlin.coroutines.resumeWithException

/**
 * 基于 OkHttp 的 Apple Music 歌词下载器。
 *
 * 请求形态对照官方 Android 客户端抓包：
 * ```
 * GET {baseUrl}/v1/catalog/{storefront}/songs/{songId}/syllable-lyrics
 *     ?l[lyrics]={本地化语言}   例如 zh-Hans / en / ja
 *     &extend=ttmlLocalizations
 *     &l[script]={书写系统}     例如 zh-Hani / en-Latn / ja-Jpan
 * Headers: Authorization · Cookie · x-dsid · User-Agent
 * ```
 *
 * ### 三件套缺一不可（实测）
 *
 * | 缺少 | 结果 |
 * |---|---|
 * | `Cookie` | 404 |
 * | `x-dsid` | 404 |
 * | `User-Agent`（官方格式） | 404 |
 *
 * 三者缺失时**表现完全相同**（都是 404 且响应体为 `No related resources`），
 * 极易被误判成"曲目不存在"。而 `extend=ttmlLocalizations` 是另一类失败：
 * 接口照样返回 200，只是响应里没有 TTML。
 *
 * @param lyricsLanguage `l[lyrics]` 查询参数：歌词本地化语言（要哪种语言的译文）。
 *   默认按**系统区域**推导，见 [SystemLyricsLocale] —— 此前写死成抓包环境的
 *   `zh-Hans`，对其他区域的调用方是错的。
 * @param lyricsScript `l[script]` 查询参数：内容书写系统。默认同样来自系统区域。
 *   注意**非原生脚本会产生转写**：日文传 `ja-Latn` 会额外得到罗马音音译，
 *   传 `ja-Jpan` 则只是原文。
 * @param userAgent 必须为 Apple 客户端格式，否则接口一律 404。
 *   默认由 [AppleMusicUserAgent] 生成：格式合规，但不含任何真实设备标识。
 */
class AppleMusicLyricsDownloader(
    private val httpClient: OkHttpClient,
    private val credentials: LyricsCredentials,
    private val parser: TtmlLyricsParser = TtmlLyricsParser(),
    private val lyricsLanguage: String = SystemLyricsLocale.languageTag(),
    private val lyricsScript: String = SystemLyricsLocale.scriptTag(),
    private val userAgent: String = AppleMusicUserAgent.default,
    baseUrl: String = DEFAULT_BASE_URL,
) : LyricsDownloader {

    private val baseHttpUrl: HttpUrl = baseUrl.trimEnd('/').toHttpUrl()

    override suspend fun download(songId: String, storefront: String): LyricsDocument {
        require(songId.isNotBlank()) { "songId 不能为空" }
        require(storefront.isNotBlank()) { "storefront 不能为空" }

        return parser.parse(fetchTtml(songId, storefront))
    }

    /**
     * 取回 TTML 原文。
     *
     * 声明为 `internal` 而非 `private`：扫描/探针工具需要「拿到原文再自己解析」，
     * 以便把原文落盘、并统计解析覆盖率。正常调用请用 [download]。
     */
    internal suspend fun fetchTtml(songId: String, storefront: String): String {
        val request = Request.Builder()
            .url(buildLyricsUrl(songId, storefront))
            .header("Authorization", "Bearer ${credentials.developerToken}")
            .header("Accept", "application/json")
            .header("User-Agent", userAgent)
            .header("Origin", ORIGIN)
            .header("Referer", "$ORIGIN/")
            .apply {
                credentials.userToken?.let { header("Music-User-Token", it) }
                credentials.cookie?.let { header("Cookie", it) }
                credentials.dsid?.let { header("x-dsid", it) }
            }
            .get()
            .build()

        val body = httpClient.newCall(request).awaitBody()
        return AmpTtmlExtractor.extractTtml(body)
            ?: throw LyricsDownloadException("响应中不含 TTML 歌词（songId=$songId）")
    }

    /**
     * 拼装歌词请求 URL。
     *
     * `extend=ttmlLocalizations` 是**决定性参数**：缺它时接口照样返回 200，
     * 但响应是瘦身版、不含 TTML —— 表现为"请求能通却解析不到歌词"。
     * 实测：带它 32579 字节，不带只有 14188 字节。
     *
     * 声明为 `internal` 而非 `private`：回归测试需要直接断言拼装结果。
     * 路径段、`l[lyrics]`、`l[script]`、`extend` 缺任何一个都会静默降级，
     * 而失败表现全是「请求能通但拿不到歌词」，必须在别处钉死。
     */
    internal fun buildLyricsUrl(songId: String, storefront: String): HttpUrl =
        baseHttpUrl.newBuilder()
            .addPathSegments("v1/catalog")
            .addPathSegment(storefront)
            .addPathSegment("songs")
            .addPathSegment(songId)
            .addPathSegment("syllable-lyrics")
            .addQueryParameter("l[lyrics]", lyricsLanguage)
            .addQueryParameter("extend", EXTEND_TTML_LOCALIZATIONS)
            .addQueryParameter("l[script]", lyricsScript)
            .build()

    /** 把 OkHttp 回调桥接为挂起函数，并把失败统一转换为 [LyricsDownloadException]。 */
    private suspend fun Call.awaitBody(): String = suspendCancellableCoroutine { continuation ->
        continuation.invokeOnCancellation { cancel() }

        enqueue(object : Callback {
            override fun onFailure(call: Call, e: IOException) {
                continuation.resumeWithException(
                    LyricsDownloadException("网络请求失败: ${e.message}", e),
                )
            }

            override fun onResponse(call: Call, response: Response) {
                response.use { safeResponse ->
                    if (!safeResponse.isSuccessful) {
                        continuation.resumeWithException(
                            LyricsDownloadException("接口返回 HTTP ${safeResponse.code}"),
                        )
                        return
                    }

                    // 读体本身也会抛 IOException，不接住的话异常会逃出回调、无人处理
                    val body = try {
                        safeResponse.body.readTextWithin(MAX_RESPONSE_BYTES)
                    } catch (e: IOException) {
                        continuation.resumeWithException(
                            LyricsDownloadException("读取响应体失败: ${e.message}", e),
                        )
                        return
                    }

                    if (body == null) {
                        continuation.resumeWithException(
                            LyricsDownloadException(
                                "响应体超过上限 $MAX_RESPONSE_BYTES 字节（实测正常歌词约 32 KB），已拒绝读取",
                            ),
                        )
                        return
                    }

                    continuation.resume(body)
                }
            }
        })
    }

    companion object {
        /** Apple Music AMP 接口的基础地址。 */
        const val DEFAULT_BASE_URL = "https://amp-api.music.apple.com"

        /** Origin / Referer 使用的站点。实测非必需，保留以贴近客户端行为。 */
        const val ORIGIN = "https://music.apple.com"

        /** 请求 TTML 歌词所必需的 extend 取值。 */
        const val EXTEND_TTML_LOCALIZATIONS = "ttmlLocalizations"

        /**
         * 响应体大小上限（4 MiB）。
         *
         * 正常歌词响应约 32 KB，留出两个数量级余量即可；超出说明响应被改写或服务异常，
         * 与其把内容读进内存不如直接失败。
         */
        const val MAX_RESPONSE_BYTES = 4L * 1024 * 1024
    }
}

/** 读取响应体时的分块大小。 */
private const val READ_CHUNK_BYTES = 8 * 1024

/**
 * 按上限读取响应体文本；超限返回 `null`。
 *
 * 只检查 `contentLength()` 是不够的：**分块传输时它返回 -1**，检查会被整个跳过，
 * 紧接着的 `string()` 就把任意大小的内容读进了内存。这里边读边累计，
 * 一旦越过上限立刻放弃（最多多读一个分块）。
 */
private fun ResponseBody.readTextWithin(maxBytes: Long): String? {
    if (contentLength() > maxBytes) return null

    val output = ByteArrayOutputStream()
    val chunk = ByteArray(READ_CHUNK_BYTES)
    byteStream().use { stream ->
        while (true) {
            val read = stream.read(chunk)
            if (read == -1) break
            output.write(chunk, 0, read)
            if (output.size().toLong() > maxBytes) return null
        }
    }
    // OkHttp 4 把 ResponseBody.charset() 收成了 private，只能从 Content-Type 上取
    val charset = contentType()?.charset() ?: Charsets.UTF_8
    return output.toByteArray().toString(charset)
}
