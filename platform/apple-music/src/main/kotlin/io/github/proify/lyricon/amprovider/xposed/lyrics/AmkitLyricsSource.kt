/*
 * Copyright 2026 Proify, Tomakino
 * Licensed under the Apache License, Version 2.0
 * http://www.apache.org/licenses/LICENSE-2.0
 */

package io.github.proify.lyricon.amprovider.xposed.lyrics

import io.github.proify.lyricon.amprovider.xposed.util.DebugLog
import io.github.tomakino.amkit.download.AppleMusicLyricsDownloader
import io.github.tomakino.amkit.download.SystemLyricsLocale
import io.github.tomakino.amkit.model.LyricsDocument
import io.github.tomakino.amkit.parser.TtmlLyricsParser
import okhttp3.OkHttpClient

/** amkit 直连歌词来源：下载并解析 TTML。 */
class AmkitLyricsSource {

    private val client: OkHttpClient by lazy { OkHttpClient.Builder().build() }
    private val parser = TtmlLyricsParser()

    suspend fun fetch(adamId: String): LyricsDocument? {
        val credentials = CredentialsStore.credentials() ?: run {
            DebugLog.error("LyricsSource", "凭据未就绪")
            return null
        }
        val storefront = CredentialsStore.storefront()

        return runCatching {
            AppleMusicLyricsDownloader(
                httpClient = client,
                credentials = credentials,
                parser = parser,
                lyricsLanguage = AppleLocale.lyricsLanguage(),
                lyricsScript = SystemLyricsLocale.scriptTag(),
            ).download(songId = adamId, storefront = storefront)
        }.onFailure {
            DebugLog.error("LyricsSource", "下载失败 $adamId storefront=$storefront", it)
        }.getOrNull()
    }
}
