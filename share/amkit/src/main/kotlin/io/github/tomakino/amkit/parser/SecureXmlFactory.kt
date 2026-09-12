/*
 * Copyright 2026 Tomakino
 * Licensed under the Apache License, Version 2.0
 * http://www.apache.org/licenses/LICENSE-2.0
 */

@file:Suppress("HttpUrlsUsage")

package io.github.tomakino.amkit.parser

import javax.xml.parsers.DocumentBuilder
import javax.xml.parsers.DocumentBuilderFactory
import javax.xml.parsers.ParserConfigurationException

/**
 * 提供已做 XXE 加固的 [DocumentBuilder]。
 *
 * 歌词文件是**外部不可信输入**（可能来自第三方歌词库或任意下载源），
 * 若不关闭 DOCTYPE 与外部实体，恶意 TTML 就能读取本地文件或发起 SSRF：
 * ```xml
 * <!DOCTYPE tt [<!ENTITY xxe SYSTEM "file:///etc/passwd">]>
 * <tt><body><p>&xxe;</p></body></tt>
 * ```
 */
internal object SecureXmlFactory {

    /** 创建一个禁用了 DOCTYPE、外部实体与 XInclude 的构造器。 */
    fun newDocumentBuilder(): DocumentBuilder {
        val factory = DocumentBuilderFactory.newInstance()
        // 必须开启命名空间感知，否则 ttm: / itunes: / amll: 前缀全部取不到
        factory.isNamespaceAware = true

        factory.setFeatureQuietly("http://apache.org/xml/features/disallow-doctype-decl", true)
        factory.setFeatureQuietly("http://xml.org/sax/features/external-general-entities", false)
        factory.setFeatureQuietly("http://xml.org/sax/features/external-parameter-entities", false)
        factory.setFeatureQuietly("http://apache.org/xml/features/nonvalidating/load-external-dtd", false)

        factory.setFlagQuietly { isXIncludeAware = false }
        factory.setFlagQuietly { isExpandEntityReferences = false }

        return factory.newDocumentBuilder()
    }

    /**
     * 部分 XML 实现（例如 Android 上的 Expat 后端）并不支持全部安全特性，
     * 设置不支持的特性会抛 [ParserConfigurationException]。
     * 这里逐个容错，保证在不支持的环境中仍能解析，而不是直接崩溃。
     */
    private fun DocumentBuilderFactory.setFeatureQuietly(name: String, value: Boolean) {
        try {
            setFeature(name, value)
        } catch (_: ParserConfigurationException) {
            // 该实现不支持此特性：忽略，其余开关仍然生效
        }
    }

    /** 同上，用于可能抛 [UnsupportedOperationException] 的布尔开关。 */
    private inline fun DocumentBuilderFactory.setFlagQuietly(block: DocumentBuilderFactory.() -> Unit) {
        try {
            block()
        } catch (_: UnsupportedOperationException) {
            // 该实现不支持此开关：忽略
        }
    }
}
