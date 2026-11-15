/*
 * Copyright 2026 Proify, Tomakino
 * Licensed under the Apache License, Version 2.0
 * http://www.apache.org/licenses/LICENSE-2.0
 */

package io.github.proify.qrckit.decrypt

import java.io.ByteArrayOutputStream
import java.util.zip.DeflaterOutputStream

/**
 * QRC 歌词加密，是 [QrcDecrypter] 的逆运算。
 *
 * 链路与解密严格对称：
 * UTF-8 文本 -> zlib 压缩 -> 零填充到 8 字节整数倍 -> Triple-DES(EDE) 加密 -> 大写十六进制字符串。
 *
 * 两点与原实现保持一致，缺一不可：
 * 1. 压缩必须用 [DeflaterOutputStream] 的默认参数，才能逐字节复现 QQ 音乐下发的 zlib 流；
 * 2. 尾块用 [零填充] 而不是 PKCS#5，解密侧依赖 zlib 流自身的结束标记，多余尾字节会被忽略。
 */
object QrcEncrypter {

    private val encryptSchedule = Array(3) { Array(16) { ByteArray(6) } }.also {
        DESHelper.tripleDESKeySetup(QrcDecrypter.QQ_KEY, it, DESHelper.ENCRYPT)
    }

    /**
     * 加密 QRC 歌词。
     *
     * @param plain 明文 QRC 歌词（UTF-8，通常是 `<QrcInfos>` XML）
     * @return 大写十六进制密文；入参为空白时返回 null
     */
    fun encrypt(plain: String?): String? {
        if (plain.isNullOrBlank()) return null

        return runCatching {
            val compressed = compress(plain.toByteArray(Charsets.UTF_8))
            // 零填充到 8 字节块边界；已对齐时 copyOf 不会补任何字节
            val aligned = compressed.copyOf((compressed.size + 7) / 8 * 8)

            val encrypted = ByteArray(aligned.size)
            val inputBlock = ByteArray(8)
            val temp = ByteArray(8)

            for (i in aligned.indices step 8) {
                System.arraycopy(aligned, i, inputBlock, 0, 8)
                DESHelper.tripleDESCrypt(inputBlock, temp, encryptSchedule)
                System.arraycopy(temp, 0, encrypted, i, 8)
            }

            encrypted.joinToString("") { "%02X".format(it.toInt() and 0xFF) }
        }.onFailure {
            it.printStackTrace()
        }.getOrNull()
    }

    private fun compress(data: ByteArray): ByteArray {
        val buffer = ByteArrayOutputStream()
        DeflaterOutputStream(buffer).use { it.write(data) }
        return buffer.toByteArray()
    }
}
