/*
 * Copyright 2026 Proify, Tomakino
 * Licensed under the Apache License, Version 2.0
 * http://www.apache.org/licenses/LICENSE-2.0
 */

package io.github.proify.lyricon.amprovider.xposed.lyrics

import android.content.Context
import com.highcapable.yukihookapi.hook.log.YLog
import io.github.tomakino.amkit.download.AppleMusicLyricsDownloader
import io.github.tomakino.amkit.model.LyricsDocument
import io.github.tomakino.amkit.parser.TtmlLyricsParser
import okhttp3.OkHttpClient

/**
 * 基于 amkit 的歌词来源：直接调用 Apple Music 授权接口下载并解析 TTML。
 *
 * 与旧实现（构造假 Song 让 App 自己下载、再 hook 其解析结果）相比，
 * 这条链路不依赖 Apple Music 内部类结构，因此对 6.5.2 / 7.0.0 天然兼容。
 */
class AmkitLyricsSource(private val context: Context) {

    private val client: OkHttpClient by lazy { OkHttpClient.Builder().build() }
    private val parser = TtmlLyricsParser()

    /**
     * 下载指定曲目的歌词；任何失败（无凭据、网络、解析）都返回 null。
     *
     * @param adamId Apple Music 歌曲 ID。
     */
    suspend fun fetch(adamId: String): LyricsDocument? {
        if (adamId.isBlank()) return null

        val credentials = CredentialsCollector.credentials(context) ?: run {
            YLog.warn(tag = TAG, msg = "AmkitLyricsSource: 缺少凭证，跳过 $adamId")
            return null
        }
        val storefront = resolveStorefront()

        return runCatching {
            AppleMusicLyricsDownloader(
                httpClient = client,
                credentials = credentials,
                parser = parser
            ).download(songId = adamId, storefront = storefront)
        }.onFailure {
            YLog.error(
                tag = TAG,
                msg = "AmkitLyricsSource: 下载失败 $adamId (storefront=$storefront)",
                e = it
            )
        }.getOrNull()
    }

    /**
     * 商店区域。优先读取手动配置，其次从 cookie 中的地区推断，最后回退美国区。
     *
     * 区域必须与曲目所属区域一致，否则接口返回 404（与缺少凭证的表现相同）。
     */
    private fun resolveStorefront(): String {
        runCatching {
            context.getSharedPreferences(PREFS_NAME, Context.MODE_PRIVATE)
                .getString(KEY_STOREFRONT, null)
        }.getOrNull()?.takeIf { it.isNotBlank() }?.let { return it.lowercase() }
        return DEFAULT_STOREFRONT
    }

    private companion object {
        const val TAG = "AMProvider"
        const val PREFS_NAME = "lyricon_amkit_credentials"
        const val KEY_STOREFRONT = "storefront"
        const val DEFAULT_STOREFRONT = "us"
    }
}
