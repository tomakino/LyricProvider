/*
 * Copyright 2026 Proify, Tomakino
 * Licensed under the Apache License, Version 2.0
 * http://www.apache.org/licenses/LICENSE-2.0
 */

package io.github.proify.qrckit.decrypt

object QrcDecrypter {

    /**
     * 解密 QRC 歌词数据。
     *
     * @param encrypted 待解密的 QRC 歌词数据，16 进制字符串
     * @return 解密后的歌词数据或者null
     */
    fun decrypt(encrypted: String?): String? =
        if (encrypted.isNullOrBlank()) null
        else runCatching { QrcCodec.decrypt(encrypted) }.getOrNull()

}