/*
 * Copyright 2026 Proify, Tomakino
 * Licensed under the Apache License, Version 2.0
 * http://www.apache.org/licenses/LICENSE-2.0
 */

package io.github.proify.extensions

import java.io.ByteArrayOutputStream
import java.util.zip.Deflater
import java.util.zip.Inflater

/**
 * ZLIB压缩字节数组
 */
fun ByteArray.deflate(): ByteArray {
    if (isEmpty()) return byteArrayOf()

    return Deflater().run {
        setInput(this@deflate)
        finish()

        ByteArrayOutputStream().use { output ->
            val buffer = ByteArray(4096)
            while (!finished()) {
                output.write(buffer, 0, deflate(buffer))
            }
            output.toByteArray()
        }.also { end() }
    }
}

/**
 * ZLIB解压字节数组
 */
fun ByteArray.inflate(): ByteArray {
    if (isEmpty()) return byteArrayOf()

    return Inflater().run {
        setInput(this@inflate)

        ByteArrayOutputStream().use { output ->
            val buffer = ByteArray(4096)
            while (!finished()) {
                val count = inflate(buffer)
                if (count == 0 && needsInput()) break
                output.write(buffer, 0, count)
            }
            output.toByteArray()
        }.also { end() }
    }
}