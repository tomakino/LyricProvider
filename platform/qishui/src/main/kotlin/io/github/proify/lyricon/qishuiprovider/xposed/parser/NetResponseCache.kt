@file:Suppress("PropertyName")

package io.github.proify.lyricon.qishuiprovider.xposed.parser

import kotlinx.serialization.Serializable

@Serializable
class NetResponseCache(
    val lyric: Lyric? = null,
) {

    @Serializable
    class Lyric(
        val type: String? = null,
        val content: String? = null,
        val lang_translations: Map<String, Translation>? = null
    )

    @Serializable
    class Translation(
        val content: String? = null,
        val type: String? = null
    )
}