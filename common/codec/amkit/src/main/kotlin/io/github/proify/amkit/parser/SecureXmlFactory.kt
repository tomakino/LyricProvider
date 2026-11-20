/*
 * Copyright 2026 Proify, Tomakino
 * Licensed under the Apache License, Version 2.0
 * http://www.apache.org/licenses/LICENSE-2.0
 */

@file:Suppress("HttpUrlsUsage")

package io.github.proify.amkit.parser

import javax.xml.parsers.DocumentBuilder
import javax.xml.parsers.DocumentBuilderFactory
import javax.xml.parsers.ParserConfigurationException

/** 禁用 DOCTYPE 与外部实体的 [DocumentBuilder] 工厂。 */
internal object SecureXmlFactory {

    /** 创建构造器。 */
    fun newDocumentBuilder(): DocumentBuilder {
        val factory = DocumentBuilderFactory.newInstance()
        // 必须开启命名空间感知，否则 ttm: / itunes: / amll: 前缀全部取不到
        factory.isNamespaceAware = true

        factory.setFeatureQuietly("http://apache.org/xml/features/disallow-doctype-decl", true)
        factory.setFeatureQuietly("http://xml.org/sax/features/external-general-entities", false)
        factory.setFeatureQuietly("http://xml.org/sax/features/external-parameter-entities", false)
        factory.setFeatureQuietly(
            "http://apache.org/xml/features/nonvalidating/load-external-dtd",
            false
        )

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

    /** 设置布尔特性，失败时忽略。 */
    private inline fun DocumentBuilderFactory.setFlagQuietly(block: DocumentBuilderFactory.() -> Unit) {
        try {
            block()
        } catch (_: UnsupportedOperationException) {
            // 该实现不支持此开关：忽略
        }
    }
}
