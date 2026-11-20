/*
 * Copyright 2026 Proify, Tomakino
 * Licensed under the Apache License, Version 2.0
 * http://www.apache.org/licenses/LICENSE-2.0
 */

package io.github.proify.amkit.parser

import kotlin.test.Test
import kotlin.test.assertFailsWith

class SecurityTest {

    @Test
    fun `拒绝 DOCTYPE 与外部实体`() {
        val xxe = """
            <?xml version="1.0"?>
            <!DOCTYPE tt [<!ENTITY xxe SYSTEM "file:///etc/passwd">]>
            <tt xmlns="http://www.w3.org/ns/ttml">
              <body><div><p><span>&xxe;</span></p></div></body>
            </tt>
        """.trimIndent()

        assertFailsWith<LyricsParseException> {
            TtmlLyricsParser().parse(xxe)
        }
    }
}
