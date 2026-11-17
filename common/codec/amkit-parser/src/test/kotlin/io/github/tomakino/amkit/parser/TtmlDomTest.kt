/*
 * Copyright 2026 Proify, Tomakino
 * Licensed under the Apache License, Version 2.0
 * http://www.apache.org/licenses/LICENSE-2.0
 */

package io.github.tomakino.amkit.parser

import org.w3c.dom.Element
import javax.xml.parsers.DocumentBuilderFactory
import kotlin.test.Test
import kotlin.test.assertEquals
import kotlin.test.assertNull

class TtmlDomTest {

    private fun root(xml: String): Element =
        DocumentBuilderFactory.newInstance()
            .apply { isNamespaceAware = true }
            .newDocumentBuilder()
            .parse(xml.byteInputStream())
            .documentElement

    @Test
    fun `命名空间属性`() {
        val element = root("""<tt xmlns="http://www.w3.org/ns/ttml" xmlns:t="urn:t" t:a="1"/>""")
        assertEquals("1", element.nsAttribute("urn:t", "a"))
        assertNull(element.nsAttribute("urn:t", "absent"))
    }

    @Test
    fun `普通属性`() {
        val element = root("""<tt xmlns="http://www.w3.org/ns/ttml" begin="1.0"/>""")
        assertEquals("1.0", element.plainAttribute("begin"))
        assertNull(element.plainAttribute("end"))
    }

    @Test
    fun `按命名空间筛选子元素`() {
        val element = root(
            """<tt xmlns="http://www.w3.org/ns/ttml" xmlns:x="urn:x"><a/><x:a/></tt>""",
        )
        assertEquals(1, element.childElements("http://www.w3.org/ns/ttml", "a").size)
        assertEquals(1, element.childElements("urn:x", "a").size)
        assertEquals(0, element.childElements("urn:other", "a").size)
    }

    @Test
    fun `取第一个匹配的子元素`() {
        val element = root(
            """<tt xmlns="http://www.w3.org/ns/ttml" xmlns:x="urn:x"><a id="1"/><a id="2"/></tt>""",
        )
        assertEquals("1", element.firstChildElement("http://www.w3.org/ns/ttml", "a")?.plainAttribute("id"))
        assertNull(element.firstChildElement("urn:missing", "a"))
    }
}
