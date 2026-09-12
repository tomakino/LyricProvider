/*
 * Copyright 2026 Tomakino
 * Licensed under the Apache License, Version 2.0
 * http://www.apache.org/licenses/LICENSE-2.0
 */

package io.github.tomakino.amkit.download

import io.github.tomakino.amkit.model.LyricsDocument

/**
 * 歌词下载器抽象。
 *
 * 解析与传输是两件独立的事：本接口只描述「拿到并解析歌词」，
 * 具体传输方式（OkHttp、自建代理、离线实现）由实现类决定，
 * 便于替换与测试（依赖倒置）。
 */
interface LyricsDownloader {

    /**
     * 下载并解析某首歌的歌词。
     *
     * @param songId Apple Music 歌曲 ID（Adam ID）。
     * @param storefront 商店区域代码，例如 `us`、`cn`、`jp`。
     *
     *   **必须与曲目所属区域一致**：曲目不属于该区域时接口会返回 404，
     *   而该 404 与「缺少凭证」时的报错完全相同（都是
     *   `No related resources found`），极易被误判为凭证问题。
     *
     * @throws io.github.tomakino.amkit.LyricsDownloadException
     *   网络失败、HTTP 非 2xx、响应结构不符，或响应中不含 TTML。
     * @throws io.github.tomakino.amkit.LyricsParseException
     *   取到的 TTML 无法解析。
     */
    suspend fun download(songId: String, storefront: String = DEFAULT_STOREFRONT): LyricsDocument

    companion object {
        /** 默认商店区域（美国区，曲库最全）。 */
        const val DEFAULT_STOREFRONT = "us"
    }
}
