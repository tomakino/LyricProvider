/*
 * Copyright 2026 Proify, Tomakino
 * Licensed under the Apache License, Version 2.0
 * http://www.apache.org/licenses/LICENSE-2.0
 */

package io.github.proify.lyricon.amprovider.xposed.lyrics

import io.github.proify.lyricon.amprovider.xposed.util.DebugLog
import io.github.tomakino.amkit.download.AppleMusicLyricsDownloader
import io.github.tomakino.amkit.parser.model.LyricsDocument
import io.github.tomakino.amkit.parser.TtmlLyricsParser

/** amkit 直连歌词来源：`amkit-download` 取原文，`amkit-parser` 解析。 */
class AmkitLyricsSource {

    private val parser = TtmlLyricsParser()

    suspend fun fetch(adamId: String): LyricsDocument? {
        val credentials = CredentialsStore.credentials() ?: run {
            DebugLog.error("LyricsSource", "凭据未就绪")
            return null
        }
        val storefront = CredentialsStore.storefront()

        return runCatching {
            val ttml = AppleMusicLyricsDownloader(credentials = credentials)
                .download(adamId, storefront)
            parser.parse(ttml)
        }.onFailure {
            DebugLog.error("LyricsSource", "下载失败 $adamId storefront=$storefront", it)
        }.getOrNull()
    }
}
