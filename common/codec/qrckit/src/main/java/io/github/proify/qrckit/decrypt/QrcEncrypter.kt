/*
 * Copyright 2026 Proify, Tomakino
 * Licensed under the Apache License, Version 2.0
 * http://www.apache.org/licenses/LICENSE-2.0
 */

package io.github.proify.qrckit.decrypt

object QrcEncrypter {

    /**
     * 加密 QRC 歌词。
     *
     * @param plain 明文 QRC 歌词
     * @return 大写十六进制密文；入参为空白时返回 null
     */
    fun encrypt(plain: String?): String? =
        if (plain.isNullOrBlank()) null
        else runCatching { QrcCodec.encrypt(plain) }.getOrNull()
}
