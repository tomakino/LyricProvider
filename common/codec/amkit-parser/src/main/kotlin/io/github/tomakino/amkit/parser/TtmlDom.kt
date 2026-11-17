/*
 * Copyright 2026 Tomakino
 * Licensed under the Apache License, Version 2.0
 * http://www.apache.org/licenses/LICENSE-2.0
 */

@file:Suppress("UsePropertyAccessSyntax")

package io.github.tomakino.amkit.parser

import org.w3c.dom.Element
import org.w3c.dom.Node
import org.w3c.dom.NodeList

/** DOM 辅助。 */

/** 取命名空间属性。 */
internal fun Element.nsAttribute(namespace: String, localName: String): String? =
    getAttributeNS(namespace, localName).takeIf { it.isNotEmpty() }

/** 取无命名空间属性（`begin` / `end` / `dur` / `key` 等）。 */
internal fun Element.plainAttribute(name: String): String? =
    getAttribute(name).takeIf { it.isNotEmpty() }

/** 按命名空间 + 本地名筛选直接子元素（不递归）。 */
internal fun Element.childElements(namespace: String, localName: String): List<Element> {
    val nodes: NodeList = getChildNodes()
    val result = ArrayList<Element>(nodes.length)
    for (index in 0 until nodes.length) {
        val node = nodes.item(index)
        if (node.nodeType != Node.ELEMENT_NODE) continue
        val element = node as Element
        if (element.localName != localName) continue
        if (element.namespaceURI != namespace) continue
        result += element
    }
    return result
}

/**
 * 取第一个匹配的直接子元素。
 *
 * 刻意**不复用 [childElements] 再取首项**：那会把整个子元素列表建出来，
 * 而调用点关心的只是「有没有、是哪一个」。
 */
internal fun Element.firstChildElement(namespace: String, localName: String): Element? {
    val nodes: NodeList = getChildNodes()
    for (index in 0 until nodes.length) {
        val node = nodes.item(index)
        if (node.nodeType != Node.ELEMENT_NODE) continue
        val element = node as Element
        if (element.localName == localName && element.namespaceURI == namespace) return element
    }
    return null
}
